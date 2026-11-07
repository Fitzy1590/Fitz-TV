import { cached, HOUR, MIN } from "./cache";
import { fetchJson } from "./http";
import type { ScoringProfile, Sport } from "../shared/types";
import { config } from "./config";
import { DEMO_STATE, demoLog, demoProjection, demoPlayers } from "./demo";

/** Sleeper's stats/projections service (unauthenticated, used by sleeper.com). */
const STATS = "https://api.sleeper.com";

export type StatLine = Record<string, number>;

export const positionQs = (sport: Sport) =>
  sport === "nfl" ? ["QB", "RB", "WR", "TE", "K", "DEF"].map((p) => `&position[]=${p}`).join("") : "";

export interface BulkRow {
  player_id: string;
  team?: string;
  opponent?: string;
  stats: StatLine;
}

/** Actual stats for every player in a week. */
export function weekStats(sport: Sport, season: string, week: number): Promise<BulkRow[]> {
  return cached(`wstats:${sport}:${season}:${week}`, 30 * MIN, () =>
    fetchJson<BulkRow[]>(`${STATS}/stats/${sport}/${season}/${week}?season_type=regular${positionQs(sport)}`).catch(() => []),
  );
}

export interface NflState {
  season: string;
  week: number;
  seasonType: string;
}

export function sportState(sport: Sport): Promise<NflState> {
  if (config.demo) return Promise.resolve(sport === "nfl" ? DEMO_STATE : { ...DEMO_STATE, week: 3 });
  return cached(`state:${sport}`, 10 * MIN, async () => {
    const s = await fetchJson<{ season: string; week: number; display_week?: number; season_type: string }>(
      `https://api.sleeper.app/v1/state/${sport}`,
    );
    return { season: s.season, week: Math.max(1, s.display_week ?? s.week ?? 1), seasonType: s.season_type };
  });
}

/** Fantasy points for a stat line under a scoring profile. */
export function pointsFor(stats: StatLine | undefined, profile: ScoringProfile, pos?: string): number | null {
  if (!stats) return null;
  const base =
    profile.base === "PPR" ? stats.pts_ppr : profile.base === "HALF" ? stats.pts_half_ppr : stats.pts_std;
  if (base === undefined || base === null) return null;
  const tep = pos === "TE" ? profile.tePremium * (stats.rec ?? 0) : 0;
  return Math.round((base + tep) * 100) / 100;
}

/** Weekly projections for every player, keyed by Sleeper id. */
export function weekProjections(sport: Sport, season: string, week: number): Promise<Map<string, StatLine>> {
  if (config.demo) {
    return Promise.resolve(new Map(Object.keys(demoPlayers(sport)).map((id) => [id, demoProjection(id, week) ?? {}])));
  }
  return cached(`proj:${sport}:${season}:${week}`, 30 * MIN, async () => {
    const rows = await fetchJson<{ player_id: string; stats: StatLine }[]>(
      `${STATS}/projections/${sport}/${season}/${week}?season_type=regular${positionQs(sport)}`,
    ).catch(() => []);
    return new Map(rows.map((r) => [r.player_id, r.stats ?? {}]));
  });
}

export interface GameLogRow {
  week: number;
  opponent: string | null;
  stats: StatLine;
  proj: StatLine | null;
}

/** Per-week stats + projections for one player in one season. */
export function playerSeasonLog(sport: Sport, playerId: string, season: string): Promise<GameLogRow[]> {
  if (config.demo) return Promise.resolve(demoLog(sport, playerId, season));
  return cached(`log:${sport}:${playerId}:${season}`, 15 * MIN, async () => {
    type Weekly = Record<string, { stats?: StatLine; opponent?: string; week?: number } | null>;
    const [stats, proj] = await Promise.all([
      fetchJson<Weekly>(`${STATS}/stats/${sport}/player/${playerId}?season_type=regular&season=${season}&grouping=week`).catch(() => ({}) as Weekly),
      fetchJson<Weekly>(`${STATS}/projections/${sport}/player/${playerId}?season_type=regular&season=${season}&grouping=week`).catch(() => ({}) as Weekly),
    ]);
    const weeks = new Set([...Object.keys(stats ?? {}), ...Object.keys(proj ?? {})].map(Number).filter(Number.isFinite));
    return [...weeks]
      .sort((a, b) => a - b)
      .map((w) => ({
        week: w,
        opponent: stats?.[w]?.opponent ?? proj?.[w]?.opponent ?? null,
        stats: stats?.[w]?.stats ?? {},
        proj: proj?.[w]?.stats ?? null,
      }));
  });
}

/** Season totals for a player across several seasons. */
export function playerCareer(sport: Sport, playerId: string, seasons: string[]) {
  if (config.demo) {
    return Promise.resolve(
      seasons
        .map((season) => {
          const log = demoLog(sport, playerId, season).filter((r) => Object.keys(r.stats).length);
          if (!log.length) return null;
          const stats: StatLine = { gp: log.length };
          for (const r of log) for (const [k, v] of Object.entries(r.stats)) if (k !== "pos_rank_ppr") stats[k] = Math.round(((stats[k] ?? 0) + v) * 100) / 100;
          stats.pos_rank_ppr = Math.max(1, Math.round(12 - Number(season.slice(-1))));
          return { season, team: null as string | null, stats };
        })
        .filter((x): x is NonNullable<typeof x> => !!x),
    );
  }
  return cached(`career:${sport}:${playerId}:${seasons.join(",")}`, 6 * HOUR, async () => {
    const out = await Promise.all(
      seasons.map(async (season) => {
        const r = await fetchJson<{ stats?: StatLine; team?: string } | null>(
          `${STATS}/stats/${sport}/player/${playerId}?season_type=regular&season=${season}`,
        ).catch(() => null);
        return r?.stats && Object.keys(r.stats).length ? { season, team: r.team ?? null, stats: r.stats } : null;
      }),
    );
    return out.filter((x): x is NonNullable<typeof x> => !!x);
  });
}

export interface Game {
  week: number;
  home: string;
  away: string;
  date: string | null;
  status: string;
}

/** NFL regular-season schedule. */
export function nflSchedule(season: string): Promise<Game[]> {
  if (config.demo) {
    const pairs = ["ATL-CIN", "BUF-MIA", "BAL-PIT", "PHI-DAL", "KC-DEN", "SF-SEA", "IND-HOU", "DET-GB", "MIN-CHI", "LAR-ARI", "NYJ-NE", "LAC-LV", "TB-NO", "WAS-NYG", "CAR-TEN", "CLE-JAX"];
    return Promise.resolve(
      [8, 9, 10].flatMap((week) => pairs.map((p) => {
        const [a, b] = p.split("-");
        return { week, home: week % 2 ? a : b, away: week % 2 ? b : a, date: null, status: week < 9 ? "complete" : "pre_game" };
      })),
    );
  }
  return cached(`sched:${season}`, 10 * MIN, async () => {
    const rows = await fetchJson<{ week: number; home: string; away: string; date?: string; start_time?: number; status?: string }[]>(
      `${STATS}/schedule/nfl/regular/${season}`,
    ).catch(() => []);
    return rows.map((g) => ({
      week: g.week,
      home: g.home,
      away: g.away,
      date: g.start_time ? new Date(g.start_time).toISOString() : g.date ?? null,
      status: g.status ?? "pre_game",
    }));
  });
}

/** Opponent / lock / bye info for a team in a week. */
export async function gameContext(sport: Sport, season: string, week: number) {
  const map = new Map<string, { opponent: string; locked: boolean; date: string | null }>();
  if (sport !== "nfl") return { map, hasSchedule: false };
  const games = (await nflSchedule(season)).filter((g) => g.week === week);
  for (const g of games) {
    const started = g.status === "in_game" || g.status === "complete";
    map.set(g.home, { opponent: g.away, locked: started, date: g.date });
    map.set(g.away, { opponent: `@${g.home}`, locked: started, date: g.date });
  }
  return { map, hasSchedule: games.length > 0 };
}
