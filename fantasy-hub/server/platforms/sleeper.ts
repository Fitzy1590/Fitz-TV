import { cached, invalidate, MIN } from "../cache";
import { fetchJson, UpstreamError } from "../http";
import { sportState } from "../stats";
import { getVault, updateVault } from "../store";
import { enricher } from "./enrich";
import { AdapterError, type Adapter } from "./types";
import type { LeagueSummary, LineupChange, Matchup, Roster, ScoringProfile, Sport, TeamSummary } from "../../shared/types";

const API = "https://api.sleeper.app/v1";
const GQL = "https://sleeper.com/graphql";

interface SLeague {
  league_id: string;
  name: string;
  avatar: string | null;
  season: string;
  total_rosters: number;
  roster_positions: string[];
  scoring_settings: Record<string, number>;
  settings: { type?: number; playoff_week_start?: number };
  status: string;
}
interface SRoster {
  roster_id: number;
  owner_id: string | null;
  co_owners?: string[] | null;
  players: string[] | null;
  starters: string[] | null;
  reserve: string[] | null;
  taxi: string[] | null;
  settings: { wins: number; losses: number; ties: number; fpts?: number; fpts_decimal?: number };
}
interface SUser {
  user_id: string;
  display_name: string;
  avatar: string | null;
  metadata?: { team_name?: string; avatar?: string };
}
interface SMatchup {
  roster_id: number;
  matchup_id: number | null;
  starters: string[];
  players: string[];
  points: number;
  players_points?: Record<string, number>;
}

const SLOT_MAP: Record<string, string> = { REC_FLEX: "REC", WRRB_FLEX: "WRRB", BN: "BN" };
const slotOf = (s: string) => SLOT_MAP[s] ?? s;

const avatarUrl = (id: string | null | undefined) => (id ? `https://sleepercdn.com/avatars/thumbs/${id}` : null);

export function sleeperScoring(l: Pick<SLeague, "scoring_settings" | "roster_positions">): ScoringProfile {
  const rec = l.scoring_settings?.rec ?? 0;
  const base = rec >= 0.75 ? "PPR" : rec >= 0.25 ? "HALF" : "STD";
  const qbSlots = l.roster_positions.filter((s) => s === "QB").length;
  return {
    base,
    superflex: l.roster_positions.includes("SUPER_FLEX") || qbSlots >= 2,
    tePremium: l.scoring_settings?.bonus_rec_te ?? 0,
  };
}

function rosters(leagueId: string) {
  return cached(`sl:rosters:${leagueId}`, MIN, () => fetchJson<SRoster[]>(`${API}/league/${leagueId}/rosters`));
}
function users(leagueId: string) {
  return cached(`sl:users:${leagueId}`, 30 * MIN, () => fetchJson<SUser[]>(`${API}/league/${leagueId}/users`));
}
function matchups(leagueId: string, week: number) {
  return cached(`sl:matchups:${leagueId}:${week}`, MIN, () =>
    fetchJson<SMatchup[]>(`${API}/league/${leagueId}/matchups/${week}`).catch(() => [] as SMatchup[]),
  );
}

function teamSummary(r: SRoster, us: SUser[]): TeamSummary {
  const u = us.find((x) => x.user_id === r.owner_id);
  return {
    id: String(r.roster_id),
    name: u?.metadata?.team_name || u?.display_name || `Team ${r.roster_id}`,
    owner: u?.display_name,
    avatar: u?.metadata?.avatar ?? avatarUrl(u?.avatar),
    wins: r.settings?.wins ?? 0,
    losses: r.settings?.losses ?? 0,
    ties: r.settings?.ties ?? 0,
    pointsFor: (r.settings?.fpts ?? 0) + (r.settings?.fpts_decimal ?? 0) / 100,
  };
}

function isMine(r: SRoster, userId: string) {
  return r.owner_id === userId || (r.co_owners ?? []).includes(userId);
}

async function gql<T>(op: string, query: string, variables: Record<string, unknown>, token?: string): Promise<T> {
  const headers: Record<string, string> = {
    "content-type": "application/json",
    accept: "application/json",
    origin: "https://sleeper.com",
    referer: "https://sleeper.com/",
    "x-sleeper-graphql-op": op,
  };
  if (token) headers.authorization = token;
  const res = await fetch(GQL, { method: "POST", headers, body: JSON.stringify({ operationName: op, query, variables }) });
  const body = (await res.json().catch(() => ({}))) as { data?: Record<string, unknown>; errors?: { message: string }[] };
  if (res.status === 401 || body.errors?.some((e) => /token|unauthor/i.test(e.message))) {
    throw new AdapterError("Sleeper session expired. Sign in to Sleeper again from Accounts.", 401);
  }
  if (!res.ok || body.errors?.length) throw new AdapterError(`Sleeper: ${body.errors?.[0]?.message ?? `HTTP ${res.status}`}`, 502);
  return body.data?.[op] as T;
}

/** Exchange username/password for a Sleeper session token. The password is not stored. */
export async function sleeperLogin(who: string, password: string) {
  const user = await gql<{ user_id: string; username: string; token: string | null; verification: unknown } | null>(
    "login",
    `query login($who: String!, $password: String) { login(email_or_phone_or_username: $who, password: $password) { user_id username display_name token verification } }`,
    { who, password },
  );
  if (!user?.token) {
    throw new AdapterError(
      user?.verification
        ? "Sleeper wants extra verification. Paste a session token instead (see Accounts help)."
        : "Sleeper didn't accept that username/password.",
      401,
    );
  }
  return { userId: String(user.user_id), username: user.username, token: user.token };
}

/** Link by username only (read-only until a token is added). */
export async function sleeperLink(username: string, token?: string) {
  const u = await fetchJson<{ user_id: string; username: string } | null>(`${API}/user/${encodeURIComponent(username)}`);
  if (!u?.user_id) throw new AdapterError("No Sleeper user with that username", 404);
  updateVault((v) => (v.sleeper = { username: u.username, userId: u.user_id, token: token || v.sleeper?.token }));
  invalidate("sl:");
  return u;
}

export const sleeper: Adapter = {
  platform: "sleeper",
  connected: () => !!getVault().sleeper?.userId,

  async leagues() {
    const acct = getVault().sleeper;
    if (!acct) return [];
    const sports: Sport[] = ["nfl", "nba"];
    const lists = await Promise.all(
      sports.map(async (sport) => {
        const state = await sportState(sport).catch(() => null);
        if (!state) return [];
        const ls = await cached(`sl:leagues:${acct.userId}:${sport}:${state.season}`, 5 * MIN, () =>
          fetchJson<SLeague[]>(`${API}/user/${acct.userId}/leagues/${sport}/${state.season}`).catch(() => [] as SLeague[]),
        );
        return Promise.all(
          ls.map(async (l): Promise<LeagueSummary> => {
            const [rs, us] = await Promise.all([rosters(l.league_id).catch(() => []), users(l.league_id).catch(() => [])]);
            const mine = rs.find((r) => isMine(r, acct.userId));
            const sorted = [...rs].sort((a, b) => b.settings.wins - a.settings.wins || (b.settings.fpts ?? 0) - (a.settings.fpts ?? 0));
            return {
              key: `sleeper:${l.league_id}`,
              platform: "sleeper",
              sport,
              leagueId: l.league_id,
              name: l.name,
              season: l.season,
              avatar: avatarUrl(l.avatar),
              size: l.total_rosters,
              slots: l.roster_positions.map(slotOf),
              detectedScoring: sleeperScoring(l),
              myTeam: mine ? teamSummary(mine, us) : null,
              standing: mine ? sorted.indexOf(mine) + 1 : null,
              dynasty: l.settings?.type === 2,
              currentWeek: state.week,
            };
          }),
        );
      }),
    );
    return lists.flat();
  },

  async roster(league, week) {
    const acct = getVault().sleeper!;
    const [rs, ms, enrich] = await Promise.all([rosters(league.leagueId), matchups(league.leagueId, week), enricher(league, week)]);
    const mine = rs.find((r) => isMine(r, acct.userId));
    if (!mine) throw new AdapterError("You don't have a team in this league", 404);
    // Weekly matchup starters are authoritative; roster.starters are the season defaults.
    const m = ms.find((x) => x.roster_id === mine.roster_id);
    const starterIds = m?.starters?.length ? m.starters : mine.starters ?? [];
    const pts = m?.players_points ?? {};
    const toPlayer = (id: string) => enrich.fromCanonical(id, id, { points: pts[id] ?? null });
    const startSlots = league.slots.filter((s) => s !== "BN");
    const reserve = new Set(mine.reserve ?? []);
    const taxi = new Set(mine.taxi ?? []);
    const starting = new Set(starterIds);
    return {
      leagueKey: league.key,
      week,
      starters: startSlots.map((slot, i) => ({
        slot,
        player: starterIds[i] && starterIds[i] !== "0" ? toPlayer(starterIds[i]) : null,
      })),
      bench: (mine.players ?? []).filter((id) => !starting.has(id) && !reserve.has(id) && !taxi.has(id)).map(toPlayer),
      reserve: [...reserve].map(toPlayer),
      taxi: [...taxi].map(toPlayer),
      canEdit: !!acct.token,
      editNote: acct.token ? undefined : "Sign in to Sleeper in Accounts to change lineups.",
    };
  },

  async matchup(league, week) {
    const acct = getVault().sleeper!;
    const [rs, us, ms] = await Promise.all([rosters(league.leagueId), users(league.leagueId), matchups(league.leagueId, week)]);
    const mine = rs.find((r) => isMine(r, acct.userId));
    const me = ms.find((x) => x.roster_id === mine?.roster_id);
    if (!mine || !me) return null;
    const opp = ms.find((x) => x.matchup_id === me.matchup_id && x.roster_id !== me.roster_id);
    const oppRoster = rs.find((r) => r.roster_id === opp?.roster_id);
    return {
      week,
      me: { team: teamSummary(mine, us), points: me.points ?? 0 },
      opponent: opp && oppRoster ? { team: teamSummary(oppRoster, us), points: opp.points ?? 0 } : null,
    } satisfies Matchup;
  },

  async standings(league) {
    const [rs, us] = await Promise.all([rosters(league.leagueId), users(league.leagueId)]);
    return rs.map((r) => teamSummary(r, us)).sort((a, b) => b.wins - a.wins || b.pointsFor - a.pointsFor);
  },

  async setLineup(league, change: LineupChange): Promise<Roster> {
    const acct = getVault().sleeper;
    if (!acct?.token) throw new AdapterError("Sign in to Sleeper in Accounts to change lineups.", 401);
    const rs = await fetchJson<SRoster[]>(`${API}/league/${league.leagueId}/rosters`);
    const mine = rs.find((r) => isMine(r, acct.userId));
    if (!mine) throw new AdapterError("You don't have a team in this league", 404);
    const owned = new Set(mine.players ?? []);
    const starters = change.starters.map((id) => id ?? "0");
    for (const id of starters) if (id !== "0" && !owned.has(id)) throw new AdapterError(`Player ${id} is not on your roster`);

    try {
      await gql(
        "update_matchup_leg",
        `mutation update_matchup_leg($league_id: Snowflake!, $roster_id: Int!, $round: Int!, $leg: Int!, $starters: [String]) { update_matchup_leg(league_id: $league_id, roster_id: $roster_id, round: $round, leg: $leg, starters: $starters) { league_id roster_id round leg starters } }`,
        { league_id: league.leagueId, roster_id: mine.roster_id, round: change.week, leg: change.week, starters },
        acct.token,
      );
    } catch (e) {
      if (e instanceof AdapterError && e.status === 401) updateVault((v) => v.sleeper && (v.sleeper.token = undefined));
      throw e;
    }
    invalidate(`sl:rosters:${league.leagueId}`);
    invalidate(`sl:matchups:${league.leagueId}:${change.week}`);
    // The public API can lag a write; return the requested lineup on top of the fresh roster.
    const roster = await sleeper.roster(league, change.week);
    const all = [...roster.starters.map((s) => s.player), ...roster.bench, ...roster.reserve, ...roster.taxi].filter((p) => !!p);
    const byId = new Map(all.map((p) => [p!.platformId, p!]));
    roster.starters = roster.starters.map((s, i) => ({ slot: s.slot, player: change.starters[i] ? byId.get(change.starters[i]!) ?? null : null }));
    const starting = new Set(change.starters);
    roster.bench = all.filter((p) => !starting.has(p!.platformId) && !roster.reserve.includes(p!) && !roster.taxi.includes(p!)) as typeof roster.bench;
    return roster;
  },

  async ownership(league) {
    const [rs, us] = await Promise.all([rosters(league.leagueId), users(league.leagueId)]);
    const out = new Map<string, string>();
    for (const r of rs) {
      const name = teamSummary(r, us).name;
      for (const p of r.players ?? []) out.set(p, name);
    }
    return out;
  },
};

export { UpstreamError };
