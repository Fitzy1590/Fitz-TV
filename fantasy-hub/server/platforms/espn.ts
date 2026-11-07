import { cached, invalidate, MIN } from "../cache";
import { fetchJson, UpstreamError } from "../http";
import { getVault, updateVault } from "../store";
import { enricher } from "./enrich";
import { AdapterError, type Adapter } from "./types";
import type { LeagueSummary, LineupChange, Player, Roster, ScoringProfile, Sport, TeamSummary } from "../../shared/types";

// ESPN's fantasy API is unofficial (it is what fantasy.espn.com itself calls).
const READS = "https://lm-api-reads.fantasy.espn.com/apis/v3/games";
const WRITES = "https://lm-api-writes.fantasy.espn.com/apis/v3/games";
const GAME: Record<Sport, string> = { nfl: "ffl", nba: "fba" };

const NFL_SLOTS: Record<number, string> = {
  0: "QB", 2: "RB", 3: "WRRB", 4: "WR", 5: "REC", 6: "TE", 7: "SUPER_FLEX", 8: "DL", 9: "DL", 10: "LB", 11: "DL",
  12: "DB", 13: "DB", 14: "DB", 15: "IDP_FLEX", 16: "DEF", 17: "K", 20: "BN", 21: "IR", 23: "FLEX",
};
const NBA_SLOTS: Record<number, string> = {
  0: "PG", 1: "SG", 2: "SF", 3: "PF", 4: "C", 5: "G", 6: "F", 7: "SG/SF", 8: "G/F", 9: "PF/C", 10: "F/C", 11: "UTIL", 12: "BN", 13: "IR",
};
const NFL_ORDER = [0, 2, 4, 6, 3, 5, 23, 7, 16, 17, 8, 9, 11, 10, 12, 13, 14, 15];
const NBA_ORDER = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
const NFL_POS: Record<number, string> = { 1: "QB", 2: "RB", 3: "WR", 4: "TE", 5: "K", 16: "DEF" };
const NBA_POS: Record<number, string> = { 1: "PG", 2: "SG", 3: "SF", 4: "PF", 5: "C" };

export const NFL_TEAMS: Record<number, string> = {
  1: "ATL", 2: "BUF", 3: "CHI", 4: "CIN", 5: "CLE", 6: "DAL", 7: "DEN", 8: "DET", 9: "GB", 10: "TEN", 11: "IND", 12: "KC",
  13: "LV", 14: "LAR", 15: "MIA", 16: "MIN", 17: "NE", 18: "NO", 19: "NYG", 20: "NYJ", 21: "PHI", 22: "ARI", 23: "PIT",
  24: "LAC", 25: "SF", 26: "SEA", 27: "TB", 28: "WAS", 29: "CAR", 30: "JAX", 33: "BAL", 34: "HOU",
};
const NBA_TEAMS: Record<number, string> = {
  1: "ATL", 2: "BOS", 3: "NOP", 4: "CHI", 5: "CLE", 6: "DAL", 7: "DEN", 8: "DET", 9: "GSW", 10: "HOU", 11: "IND", 12: "LAC",
  13: "LAL", 14: "MIA", 15: "MIL", 16: "MIN", 17: "BKN", 18: "NYK", 19: "ORL", 20: "PHI", 21: "PHX", 22: "POR", 23: "SAC",
  24: "SAS", 25: "OKC", 26: "UTA", 27: "WAS", 28: "TOR", 29: "MEM", 30: "CHA",
};
const INJURY: Record<string, string | null> = {
  ACTIVE: null, NORMAL: null, QUESTIONABLE: "Q", DOUBTFUL: "D", OUT: "OUT", INJURY_RESERVE: "IR", SUSPENSION: "SUS", DAY_TO_DAY: "DTD",
};

interface EStat { scoringPeriodId: number; statSourceId: number; statSplitTypeId: number; appliedTotal?: number; stats?: Record<string, number> }
interface EEntry {
  playerId: number;
  lineupSlotId: number;
  playerPoolEntry?: {
    lineupLocked?: boolean;
    player: { id: number; fullName: string; defaultPositionId: number; eligibleSlots: number[]; proTeamId: number; injuryStatus?: string; stats?: EStat[] };
  };
}
interface ETeam {
  id: number;
  name?: string;
  location?: string;
  nickname?: string;
  abbrev?: string;
  logo?: string;
  owners?: string[];
  primaryOwner?: string;
  record?: { overall?: { wins: number; losses: number; ties: number; pointsFor: number } };
  roster?: { entries: EEntry[] };
}
interface ESide { teamId: number; totalPoints: number; totalPointsLive?: number; totalProjectedPointsLive?: number }
interface ELeague {
  id: number;
  seasonId: number;
  scoringPeriodId: number;
  status?: { currentMatchupPeriod?: number; latestScoringPeriod?: number };
  settings: {
    name: string;
    size?: number;
    rosterSettings: { lineupSlotCounts: Record<string, number> };
    scoringSettings?: { scoringItems?: { statId: number; points: number; pointsOverrides?: Record<string, number> }[] };
    draftSettings?: { keeperCount?: number };
  };
  teams: ETeam[];
  schedule?: { matchupPeriodId: number; home?: ESide; away?: ESide }[];
}

function creds() {
  const e = getVault().espn;
  if (!e) throw new AdapterError("ESPN isn't linked", 401);
  return e;
}

function cookie() {
  const e = creds();
  return { cookie: `espn_s2=${e.s2}; SWID=${e.swid}` };
}

function leagueUrl(base: string, sport: Sport, season: string, id: string) {
  return `${base}/${GAME[sport]}/seasons/${season}/segments/0/leagues/${id}`;
}

async function readLeague(sport: Sport, season: string, id: string, views: string[], period?: number) {
  const qs = views.map((v) => `view=${v}`).join("&") + (period ? `&scoringPeriodId=${period}` : "");
  return cached(`espn:${id}:${season}:${views.join(",")}:${period ?? ""}`, MIN, async () => {
    try {
      return await fetchJson<ELeague>(`${leagueUrl(READS, sport, season, id)}?${qs}`, { headers: cookie() });
    } catch (e) {
      if (e instanceof UpstreamError && (e.status === 401 || e.status === 403)) {
        throw new AdapterError("ESPN rejected the saved cookies (espn_s2 / SWID). Re-link ESPN in Accounts.", 401);
      }
      throw e;
    }
  });
}

const teamName = (t: ETeam) => t.name || `${t.location ?? ""} ${t.nickname ?? ""}`.trim() || t.abbrev || `Team ${t.id}`;
const summary = (t: ETeam): TeamSummary => ({
  id: String(t.id),
  name: teamName(t),
  avatar: t.logo ?? null,
  wins: t.record?.overall?.wins ?? 0,
  losses: t.record?.overall?.losses ?? 0,
  ties: t.record?.overall?.ties ?? 0,
  pointsFor: Math.round((t.record?.overall?.pointsFor ?? 0) * 100) / 100,
});

const sameSwid = (a?: string, b?: string) => !!a && !!b && a.replace(/[{}]/g, "").toUpperCase() === b.replace(/[{}]/g, "").toUpperCase();
const myTeamOf = (l: ELeague) => l.teams.find((t) => (t.owners ?? []).some((o) => sameSwid(o, creds().swid)) || sameSwid(t.primaryOwner, creds().swid));

export function espnScoring(l: ELeague): ScoringProfile {
  const items = l.settings.scoringSettings?.scoringItems ?? [];
  const rec = items.find((i) => i.statId === 53);
  const recPts = rec?.points ?? 0;
  const counts = l.settings.rosterSettings.lineupSlotCounts;
  const teRec = rec?.pointsOverrides?.["6"];
  return {
    base: recPts >= 0.75 ? "PPR" : recPts >= 0.25 ? "HALF" : "STD",
    superflex: (counts["7"] ?? 0) > 0 || (counts["0"] ?? 0) >= 2,
    tePremium: teRec !== undefined ? Math.max(0, teRec - recPts) : 0,
  };
}

function starterSlotIds(sport: Sport, counts: Record<string, number>): number[] {
  const order = sport === "nfl" ? NFL_ORDER : NBA_ORDER;
  return order.flatMap((id) => Array(counts[String(id)] ?? 0).fill(id));
}

const slotName = (sport: Sport, id: number) => (sport === "nfl" ? NFL_SLOTS : NBA_SLOTS)[id] ?? `S${id}`;
const benchId = (sport: Sport) => (sport === "nfl" ? 20 : 12);
const irId = (sport: Sport) => (sport === "nfl" ? 21 : 13);

/** Discover the leagues on an ESPN account (ESPN's fan profile API). */
async function discover(): Promise<{ id: string; sport: Sport; season: string }[]> {
  const { swid } = creds();
  return cached(`espn:discover:${swid}`, 30 * MIN, async () => {
    type Fan = { preferences?: { metaData?: { entry?: { gameId?: number; seasonId?: number; groups?: { groupId: number }[] } } }[] };
    const fan = await fetchJson<Fan>(
      `https://fan.api.espn.com/apis/v2/fans/${encodeURIComponent(swid)}?displayEvents=true&displayNow=true&context=fantasy&source=espncom-fantasy-lm&lang=en&section=espn&region=us`,
      { headers: cookie() },
    ).catch(() => ({}) as Fan);
    const out: { id: string; sport: Sport; season: string }[] = [];
    for (const p of fan.preferences ?? []) {
      const e = p.metaData?.entry;
      const sport: Sport | null = e?.gameId === 1 ? "nfl" : e?.gameId === 3 ? "nba" : null;
      if (!e || !sport) continue;
      for (const g of e.groups ?? []) out.push({ id: String(g.groupId), sport, season: String(e.seasonId ?? "") });
    }
    return out;
  });
}

export async function espnLink(s2: string, swid: string, manual: { id: string; sport: Sport }[]) {
  const clean = swid.trim().startsWith("{") ? swid.trim() : `{${swid.trim()}}`;
  updateVault((v) => (v.espn = { s2: decodeURIComponent(s2.trim()), swid: clean, leagues: manual }));
  invalidate("espn:");
  const found = await discover().catch(() => []);
  const all = [...manual, ...found.filter((f) => !manual.some((m) => m.id === f.id))];
  updateVault((v) => v.espn && (v.espn.leagues = all));
  return all;
}

function toPlayer(sport: Sport, e: EEntry, period: number, enrich: Awaited<ReturnType<typeof enricher>>): Player {
  const pl = e.playerPoolEntry!.player;
  const teams = sport === "nfl" ? NFL_TEAMS : NBA_TEAMS;
  const team = teams[pl.proTeamId] ?? null;
  const pos = (sport === "nfl" ? NFL_POS : NBA_POS)[pl.defaultPositionId] ?? "";
  // ESPN D/ST ids are negative; Sleeper uses the team abbreviation as the DEF id.
  const canonical = pos === "DEF" && team ? team : enrich.db.byEspn.get(String(pl.id)) ?? `espn:${pl.id}`;
  const stat = (src: number) => pl.stats?.find((s) => s.scoringPeriodId === period && s.statSourceId === src)?.appliedTotal;
  const eligible = pl.eligibleSlots.map((id) => slotName(sport, id)).filter((s) => !s.startsWith("S"));
  return enrich.fromCanonical(canonical, String(pl.id), {
    name: pl.fullName,
    positions: pos ? [pos] : [],
    team,
    injury: INJURY[pl.injuryStatus ?? "ACTIVE"] ?? pl.injuryStatus ?? null,
    proj: stat(1) ?? undefined,
    points: stat(0) ?? null,
    locked: e.playerPoolEntry?.lineupLocked || undefined,
    eligibleSlots: [...new Set(eligible)],
  });
}

async function buildRoster(league: LeagueSummary, period: number): Promise<{ roster: Roster; entries: EEntry[]; slotIds: number[] }> {
  const [l, enrich] = await Promise.all([
    readLeague(league.sport, league.season, league.leagueId, ["mTeam", "mRoster", "mSettings"], period),
    enricher(league, period),
  ]);
  const me = myTeamOf(l);
  if (!me) throw new AdapterError("Couldn't find your team in this ESPN league", 404);
  const entries = me.roster?.entries ?? [];
  const slotIds = starterSlotIds(league.sport, l.settings.rosterSettings.lineupSlotCounts);
  const pool = entries.map((e) => ({ e, p: toPlayer(league.sport, e, period, enrich) }));
  const taken = new Set<EEntry>();
  const starters = slotIds.map((sid) => {
    const hit = pool.find((x) => x.e.lineupSlotId === sid && !taken.has(x.e));
    if (hit) taken.add(hit.e);
    return { slot: slotName(league.sport, sid), player: hit?.p ?? null };
  });
  return {
    entries,
    slotIds,
    roster: {
      leagueKey: league.key,
      week: period,
      starters,
      bench: pool.filter((x) => !taken.has(x.e) && x.e.lineupSlotId !== irId(league.sport)).map((x) => x.p),
      reserve: pool.filter((x) => x.e.lineupSlotId === irId(league.sport)).map((x) => x.p),
      taxi: [],
      canEdit: true,
    },
  };
}

export const espn: Adapter = {
  platform: "espn",
  connected: () => !!getVault().espn?.s2,

  async leagues() {
    const acct = getVault().espn;
    if (!acct) return [];
    const now = new Date();
    const ids = acct.leagues.length ? acct.leagues : await discover().catch(() => []);
    const results = await Promise.allSettled(
      ids.map(async ({ id, sport, season }) => {
        // NBA seasons are named for the year they end in.
        const s = season || String(sport === "nba" && now.getMonth() >= 8 ? now.getFullYear() + 1 : now.getFullYear());
        const l = await readLeague(sport, s, id, ["mTeam", "mSettings"]);
        const me = myTeamOf(l);
        const sorted = [...l.teams].sort((a, b) => (b.record?.overall?.wins ?? 0) - (a.record?.overall?.wins ?? 0));
        const slots = [
          ...starterSlotIds(sport, l.settings.rosterSettings.lineupSlotCounts).map((sid) => slotName(sport, sid)),
          ...Array(l.settings.rosterSettings.lineupSlotCounts[String(benchId(sport))] ?? 0).fill("BN"),
        ];
        return {
          key: `espn:${id}`,
          platform: "espn",
          sport,
          leagueId: id,
          name: l.settings.name,
          season: String(l.seasonId),
          avatar: null,
          size: l.settings.size ?? l.teams.length,
          slots,
          detectedScoring: espnScoring(l),
          myTeam: me ? summary(me) : null,
          standing: me ? sorted.indexOf(me) + 1 : null,
          dynasty: (l.settings.draftSettings?.keeperCount ?? 0) > 0,
          currentWeek: l.scoringPeriodId ?? l.status?.latestScoringPeriod ?? 1,
        } satisfies LeagueSummary;
      }),
    );
    return results.flatMap((r) => (r.status === "fulfilled" ? [r.value] : []));
  },

  async roster(league, week) {
    return (await buildRoster(league, week)).roster;
  },

  async matchup(league, week) {
    const l = await readLeague(league.sport, league.season, league.leagueId, ["mTeam", "mMatchupScore", "mScoreboard"], week);
    const me = myTeamOf(l);
    if (!me) return null;
    const period = league.sport === "nfl" ? week : l.status?.currentMatchupPeriod ?? week;
    const m = l.schedule?.find((s) => s.matchupPeriodId === period && (s.home?.teamId === me.id || s.away?.teamId === me.id));
    if (!m) return null;
    const mine = m.home?.teamId === me.id ? m.home! : m.away!;
    const theirs = m.home?.teamId === me.id ? m.away : m.home;
    const opp = theirs ? l.teams.find((t) => t.id === theirs.teamId) : null;
    return {
      week,
      me: { team: summary(me), points: mine.totalPointsLive ?? mine.totalPoints ?? 0, projected: mine.totalProjectedPointsLive ?? null },
      opponent: opp && theirs ? { team: summary(opp), points: theirs.totalPointsLive ?? theirs.totalPoints ?? 0, projected: theirs.totalProjectedPointsLive ?? null } : null,
    };
  },

  async standings(league) {
    const l = await readLeague(league.sport, league.season, league.leagueId, ["mTeam"]);
    return l.teams.map(summary).sort((a, b) => b.wins - a.wins || b.pointsFor - a.pointsFor);
  },

  async setLineup(league, change: LineupChange) {
    const { roster, entries, slotIds } = await buildRoster(league, change.week);
    const l = await readLeague(league.sport, league.season, league.leagueId, ["mTeam"]);
    const me = myTeamOf(l)!;
    const wanted = new Map<string, number>(); // espn player id -> target slot id
    change.starters.forEach((pid, i) => pid && wanted.set(pid, slotIds[i]));

    const items: { playerId: number; type: "LINEUP"; fromLineupSlotId: number; toLineupSlotId: number }[] = [];
    for (const e of entries) {
      const pid = String(e.playerId);
      const from = e.lineupSlotId;
      const isStarterNow = from !== benchId(league.sport) && from !== irId(league.sport);
      const to = wanted.get(pid) ?? (isStarterNow ? benchId(league.sport) : from);
      if (to !== from) items.push({ playerId: e.playerId, type: "LINEUP", fromLineupSlotId: from, toLineupSlotId: to });
    }
    for (const pid of wanted.keys()) {
      if (!entries.some((e) => String(e.playerId) === pid)) throw new AdapterError(`Player ${pid} is not on your ESPN roster`);
    }
    const locked = [...roster.starters.map((s) => s.player), ...roster.bench].filter((p) => p?.locked && items.some((i) => String(i.playerId) === p.platformId));
    if (locked.length) throw new AdapterError(`${locked.map((p) => p!.name).join(", ")} ${locked.length > 1 ? "are" : "is"} locked for this period`);
    if (!items.length) return roster;

    const body = {
      isLeagueManager: false,
      teamId: me.id,
      type: "ROSTER",
      memberId: creds().swid,
      scoringPeriodId: change.week,
      executionType: "EXECUTE",
      items,
    };
    try {
      await fetchJson(`${leagueUrl(WRITES, league.sport, league.season, league.leagueId)}/transactions/`, {
        method: "POST",
        headers: { ...cookie(), "content-type": "application/json", "x-fantasy-platform": "espn-fantasy-web", "x-fantasy-source": "kona" },
        body: JSON.stringify(body),
      });
    } catch (e) {
      if (e instanceof UpstreamError) {
        let msg = e.body ?? e.message;
        try {
          msg = JSON.parse(e.body ?? "").messages?.join(" ") || msg;
        } catch { /* keep raw */ }
        throw new AdapterError(`ESPN refused the lineup: ${msg}`, e.status);
      }
      throw e;
    }
    invalidate(`espn:${league.leagueId}:`);
    return (await buildRoster(league, change.week)).roster;
  },

  async ownership(league) {
    const l = await readLeague(league.sport, league.season, league.leagueId, ["mTeam", "mRoster"]);
    const out = new Map<string, string>();
    const { playerDb } = await import("../players");
    const db = await playerDb(league.sport);
    for (const t of l.teams) {
      for (const e of t.roster?.entries ?? []) {
        const pl = e.playerPoolEntry?.player;
        if (!pl) continue;
        const team = (league.sport === "nfl" ? NFL_TEAMS : NBA_TEAMS)[pl.proTeamId];
        const id = pl.defaultPositionId === 16 && team ? team : db.byEspn.get(String(pl.id)) ?? `espn:${pl.id}`;
        out.set(id, teamName(t));
      }
    }
    return out;
  },
};
