import { randomBytes } from "node:crypto";
import { cached, invalidate, MIN } from "../cache";
import { config } from "../config";
import { fetchJson, fetchText, UpstreamError } from "../http";
import { normalizeTeam } from "../players";
import { getVault, updateVault } from "../store";
import { enricher } from "./enrich";
import { AdapterError, type Adapter } from "./types";
import type { LeagueSummary, LineupChange, Player, Roster, ScoringProfile, Sport, TeamSummary } from "../../shared/types";

// Yahoo Fantasy Sports API (official, OAuth 2). Docs: https://developer.yahoo.com/fantasysports/guide/
const API = "https://fantasysports.yahooapis.com/fantasy/v2";
const AUTH = "https://api.login.yahoo.com/oauth2";
const redirectUri = () => `${config.publicUrl}/api/yahoo/callback`;

const SLOT: Record<string, string> = {
  "W/R/T": "FLEX", "W/R": "WRRB", "W/T": "REC", "Q/W/R/T": "SUPER_FLEX", OP: "SUPER_FLEX", DEF: "DEF", K: "K",
  BN: "BN", IR: "IR", "IR+": "IR", IL: "IR", "IL+": "IR", NA: "IR", Util: "UTIL", UTIL: "UTIL", D: "IDP_FLEX",
};
const canonicalSlot = (y: string) => SLOT[y] ?? y;
const INACTIVE = new Set(["BN", "IR", "IR+", "IL", "IL+", "NA"]);

// ---------------------------------------------------------------------------
// OAuth
// ---------------------------------------------------------------------------
const pendingStates = new Map<string, number>();

export function yahooAuthUrl(): string {
  if (!config.yahooClientId) throw new AdapterError("Set YAHOO_CLIENT_ID and YAHOO_CLIENT_SECRET on the server first", 400);
  const state = randomBytes(16).toString("hex");
  pendingStates.set(state, Date.now());
  const qs = new URLSearchParams({ client_id: config.yahooClientId, redirect_uri: redirectUri(), response_type: "code", state, language: "en-us" });
  return `${AUTH}/request_auth?${qs}`;
}

async function tokenRequest(params: Record<string, string>) {
  const basic = Buffer.from(`${config.yahooClientId}:${config.yahooClientSecret}`).toString("base64");
  const t = await fetchJson<{ access_token: string; refresh_token: string; expires_in: number }>(`${AUTH}/get_token`, {
    method: "POST",
    headers: { authorization: `Basic ${basic}`, "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ redirect_uri: redirectUri(), ...params }).toString(),
  });
  updateVault((v) => (v.yahoo = { accessToken: t.access_token, refreshToken: t.refresh_token ?? v.yahoo?.refreshToken, expiresAt: Date.now() + (t.expires_in - 60) * 1000 }));
}

export async function yahooCallback(code: string, state: string) {
  const at = pendingStates.get(state);
  pendingStates.delete(state);
  if (!at || Date.now() - at > 10 * MIN) throw new AdapterError("Yahoo sign-in expired, try again", 400);
  await tokenRequest({ grant_type: "authorization_code", code });
  invalidate("y:");
}

async function token(): Promise<string> {
  const y = getVault().yahoo;
  if (!y) throw new AdapterError("Yahoo isn't linked", 401);
  if (Date.now() > y.expiresAt) await tokenRequest({ grant_type: "refresh_token", refresh_token: y.refreshToken });
  return getVault().yahoo!.accessToken;
}

async function yget<T = any>(path: string): Promise<T> {
  const sep = path.includes("?") ? "&" : "?";
  try {
    return await fetchJson<T>(`${API}${path}${sep}format=json`, { headers: { authorization: `Bearer ${await token()}` } });
  } catch (e) {
    if (e instanceof UpstreamError && e.status === 401) {
      updateVault((v) => v.yahoo && (v.yahoo.expiresAt = 0));
      return fetchJson<T>(`${API}${path}${sep}format=json`, { headers: { authorization: `Bearer ${await token()}` } });
    }
    throw e;
  }
}

// ---------------------------------------------------------------------------
// Yahoo's JSON is XML translated literally: arrays of single-key objects and
// {"0": {...}, "count": n} maps. These helpers flatten it.
// ---------------------------------------------------------------------------
/** Merge an array of objects (and nested arrays of objects) into one object. */
export function merge(arr: unknown): Record<string, any> {
  const out: Record<string, any> = {};
  const walk = (x: unknown) => {
    if (Array.isArray(x)) x.forEach(walk);
    else if (x && typeof x === "object") Object.assign(out, x);
  };
  walk(arr);
  return out;
}
/** Values of a {"0": .., "1": .., "count": n} map. */
export function list<T = any>(m: unknown): T[] {
  if (!m || typeof m !== "object") return [];
  return Object.entries(m as Record<string, T>)
    .filter(([k]) => /^\d+$/.test(k))
    .map(([, v]) => v);
}

interface YTeamRef { teamKey: string; leagueKey: string }

async function myTeams(): Promise<YTeamRef[]> {
  return cached("y:teams", 10 * MIN, async () => {
    const r = await yget(`/users;use_login=1/games;game_keys=nfl,nba/teams`);
    const user = r.fantasy_content.users["0"].user;
    const games = list(merge(user).games);
    return games.flatMap((g: any) =>
      list(merge(g.game).teams).map((t: any) => {
        const team = merge(t.team);
        const teamKey: string = team.team_key;
        return { teamKey, leagueKey: teamKey.split(".t.")[0] };
      }),
    );
  });
}

interface YSettings { slots: string[]; scoring: ScoringProfile; sport: Sport; name: string; season: string; size: number; week: number; logo: string | null; keeper: boolean }

async function leagueSettings(leagueKey: string): Promise<YSettings> {
  return cached(`y:settings:${leagueKey}`, 30 * MIN, async () => {
    const r = await yget(`/league/${leagueKey}/settings`);
    const [meta, rest] = r.fantasy_content.league;
    const settings = merge(rest.settings);
    const slots: string[] = (settings.roster_positions ?? []).flatMap((p: any) => Array(Number(p.roster_position.count)).fill(p.roster_position.position));
    const mods: { stat: { stat_id: number; value: string } }[] = settings.stat_modifiers?.stats ?? [];
    const rec = Number(mods.find((m) => Number(m.stat.stat_id) === 11)?.stat.value ?? 0);
    const sport: Sport = meta.game_code === "nba" ? "nba" : "nfl";
    return {
      slots,
      sport,
      scoring: {
        base: rec >= 0.75 ? "PPR" : rec >= 0.25 ? "HALF" : "STD",
        superflex: slots.includes("Q/W/R/T") || slots.includes("OP") || slots.filter((s) => s === "QB").length >= 2,
        tePremium: 0,
      },
      name: meta.name,
      season: String(meta.season),
      size: Number(meta.num_teams),
      week: Number(meta.current_week ?? 1),
      logo: meta.logo_url || null,
      keeper: meta.is_keeper === 1 || settings.uses_keeper === 1,
    };
  });
}

function teamFrom(t: any): TeamSummary {
  const [info, ...rest] = t;
  const m = merge(info);
  const extra = merge(rest);
  const totals = extra.team_standings?.outcome_totals ?? {};
  return {
    id: m.team_key,
    name: m.name,
    owner: m.managers?.[0]?.manager?.nickname,
    avatar: m.team_logos?.[0]?.team_logo?.url ?? null,
    wins: Number(totals.wins ?? 0),
    losses: Number(totals.losses ?? 0),
    ties: Number(totals.ties ?? 0),
    pointsFor: Number(extra.team_standings?.points_for ?? extra.team_points?.total ?? 0),
  };
}

async function standingsOf(leagueKey: string): Promise<TeamSummary[]> {
  return cached(`y:standings:${leagueKey}`, 5 * MIN, async () => {
    const r = await yget(`/league/${leagueKey}/standings`);
    const teams = list(merge(r.fantasy_content.league[1].standings).teams);
    return teams.map((t: any) => teamFrom(t.team));
  });
}

function easternDate(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/New_York" }).format(new Date());
}

const coverage = (sport: Sport, week: number) => (sport === "nba" ? `date=${easternDate()}` : `week=${week}`);

interface YRosterRow { key: string; yahooId: string; slot: string; player: Player; editable: boolean }

async function rosterRows(league: LeagueSummary, teamKey: string, week: number): Promise<YRosterRow[]> {
  const [r, enrich] = await Promise.all([yget(`/team/${teamKey}/roster;${coverage(league.sport, week)}`), enricher(league, week)]);
  const roster = merge(r.fantasy_content.team[1].roster);
  const players = list(merge(roster["0"] ? roster["0"] : roster).players ?? roster.players);
  return players.map((p: any) => {
    const [infoArr, ...rest] = p.player;
    const info = merge(infoArr);
    const extra = merge(rest);
    const sel = merge(extra.selected_position).position as string;
    const team = info.editorial_team_abbr ? normalizeTeam(info.editorial_team_abbr) : null;
    const pos = String(info.display_position ?? "").split(",")[0];
    const eligible: string[] = (info.eligible_positions ?? []).map((e: any) => canonicalSlot(e.position));
    const canonical = pos === "DEF" && team ? team : enrich.db.byYahoo.get(String(info.player_id)) ?? `yahoo:${info.player_id}`;
    return {
      key: info.player_key,
      yahooId: String(info.player_id),
      slot: sel,
      editable: extra.is_editable === undefined ? true : Number(extra.is_editable) === 1,
      player: enrich.fromCanonical(canonical, info.player_key, {
        name: info.name?.full,
        team,
        positions: String(info.display_position ?? "").split(","),
        injury: info.status || null,
        eligibleSlots: [...new Set(eligible.filter((s) => !INACTIVE.has(s)))],
        locked: extra.is_editable !== undefined ? Number(extra.is_editable) === 0 : undefined,
      }),
    };
  });
}

function assemble(league: LeagueSummary, yslots: string[], rows: YRosterRow[], week: number): Roster {
  const startSlots = yslots.filter((s) => !INACTIVE.has(s));
  const taken = new Set<YRosterRow>();
  const starters = startSlots.map((ys) => {
    const hit = rows.find((r) => r.slot === ys && !taken.has(r));
    if (hit) taken.add(hit);
    return { slot: canonicalSlot(ys), player: hit?.player ?? null };
  });
  return {
    leagueKey: league.key,
    week,
    starters,
    bench: rows.filter((r) => !taken.has(r) && r.slot === "BN").map((r) => r.player),
    reserve: rows.filter((r) => !taken.has(r) && r.slot !== "BN").map((r) => r.player),
    taxi: [],
    canEdit: true,
    editNote: league.sport === "nba" ? "Yahoo basketball lineups are set for today's date." : undefined,
  };
}

async function teamKeyFor(league: LeagueSummary): Promise<string> {
  const t = (await myTeams()).find((x) => x.leagueKey === league.leagueId);
  if (!t) throw new AdapterError("Couldn't find your team in this Yahoo league", 404);
  return t.teamKey;
}

function xmlEscape(s: string) {
  return s.replace(/[<>&'"]/g, (c) => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", "'": "&apos;", '"': "&quot;" })[c]!);
}

export const yahoo: Adapter = {
  platform: "yahoo",
  connected: () => !!getVault().yahoo?.refreshToken,

  async leagues() {
    if (!getVault().yahoo) return [];
    const teams = await myTeams();
    const out = await Promise.allSettled(
      teams.map(async (t): Promise<LeagueSummary> => {
        const [s, st] = await Promise.all([leagueSettings(t.leagueKey), standingsOf(t.leagueKey).catch((): TeamSummary[] => [])]);
        const mine = st.find((x) => x.id === t.teamKey) ?? null;
        return {
          key: `yahoo:${t.leagueKey}`,
          platform: "yahoo",
          sport: s.sport,
          leagueId: t.leagueKey,
          name: s.name,
          season: s.season,
          avatar: s.logo,
          size: s.size,
          slots: s.slots.map(canonicalSlot),
          detectedScoring: s.scoring,
          myTeam: mine,
          standing: mine ? st.indexOf(mine) + 1 : null,
          dynasty: s.keeper,
          currentWeek: s.week,
        };
      }),
    );
    return out.flatMap((r) => (r.status === "fulfilled" ? [r.value] : []));
  },

  async roster(league, week) {
    const [teamKey, s] = await Promise.all([teamKeyFor(league), leagueSettings(league.leagueId)]);
    return assemble(league, s.slots, await rosterRows(league, teamKey, week), week);
  },

  async matchup(league, week) {
    if (league.sport !== "nfl") return null;
    const teamKey = await teamKeyFor(league);
    const r = await yget(`/team/${teamKey}/matchups;weeks=${week}`);
    const ms = list(merge(r.fantasy_content.team[1].matchups));
    const m = ms[0] && merge(ms[0].matchup);
    if (!m) return null;
    const teams = list(m["0"]?.teams ?? m.teams).map((t: any) => ({ summary: teamFrom(t.team), points: Number(merge(t.team.slice(1)).team_points?.total ?? 0), proj: Number(merge(t.team.slice(1)).team_projected_points?.total ?? 0) }));
    const me = teams.find((t) => t.summary.id === teamKey);
    const opp = teams.find((t) => t.summary.id !== teamKey);
    if (!me) return null;
    return { week, me: { team: me.summary, points: me.points, projected: me.proj }, opponent: opp ? { team: opp.summary, points: opp.points, projected: opp.proj } : null };
  },

  standings: (league) => standingsOf(league.leagueId),

  async setLineup(league, change: LineupChange) {
    const [teamKey, s] = await Promise.all([teamKeyFor(league), leagueSettings(league.leagueId)]);
    const rows = await rosterRows(league, teamKey, change.week);
    const startSlots = s.slots.filter((x) => !INACTIVE.has(x));
    const target = new Map<string, string>(); // player_key -> yahoo position
    change.starters.forEach((key, i) => key && target.set(key, startSlots[i]));
    for (const key of target.keys()) if (!rows.some((r) => r.key === key)) throw new AdapterError(`Player ${key} is not on your Yahoo roster`);

    const moves = rows
      .map((r) => ({ r, to: target.get(r.key) ?? (INACTIVE.has(r.slot) ? r.slot : "BN") }))
      .filter(({ r, to }) => to !== r.slot);
    const locked = moves.filter(({ r }) => !r.editable);
    if (locked.length) throw new AdapterError(`${locked.map(({ r }) => r.player.name).join(", ")} can't be moved right now (game started)`);
    if (moves.length) {
      const cov = league.sport === "nba" ? `<coverage_type>date</coverage_type><date>${easternDate()}</date>` : `<coverage_type>week</coverage_type><week>${change.week}</week>`;
      const xml = `<?xml version="1.0"?><fantasy_content><roster>${cov}<players>${moves
        .map(({ r, to }) => `<player><player_key>${xmlEscape(r.key)}</player_key><position>${xmlEscape(to)}</position></player>`)
        .join("")}</players></roster></fantasy_content>`;
      try {
        await fetchText(`${API}/team/${teamKey}/roster`, {
          method: "PUT",
          headers: { authorization: `Bearer ${await token()}`, "content-type": "application/xml" },
          body: xml,
        });
      } catch (e) {
        if (e instanceof UpstreamError) {
          const desc = e.body?.match(/<description>([^<]+)<\/description>/)?.[1];
          throw new AdapterError(`Yahoo refused the lineup: ${desc ?? e.message}`, e.status);
        }
        throw e;
      }
    }
    return assemble(league, s.slots, await rosterRows(league, teamKey, change.week), change.week);
  },

  async ownership(league) {
    const out = new Map<string, string>();
    const r = await cached(`y:allrosters:${league.leagueId}`, 5 * MIN, () => yget(`/league/${league.leagueId}/teams/roster`));
    const { playerDb } = await import("../players");
    const db = await playerDb(league.sport);
    for (const t of list(merge(r.fantasy_content.league[1]).teams)) {
      const [info, rest] = [merge((t as any).team[0]), merge((t as any).team.slice(1))];
      const roster = merge(rest.roster);
      for (const p of list(merge(roster["0"] ?? roster).players)) {
        const pi = merge((p as any).player[0]);
        const team = pi.editorial_team_abbr ? normalizeTeam(pi.editorial_team_abbr) : null;
        const id = pi.display_position === "DEF" && team ? team : db.byYahoo.get(String(pi.player_id)) ?? `yahoo:${pi.player_id}`;
        out.set(id, info.name);
      }
    }
    return out;
  },
};
