import { cached, HOUR } from "../cache";
import { config } from "../config";
import { fetchJson, fetchText } from "../http";
import { matchByName, normalizeTeam, playerDb } from "../players";
import { pointsFor, sportState, weekProjections } from "../stats";
import { getVault } from "../store";
import { DEMO_STATE, demoRankingRows } from "../demo";
import type { RankingRow, RankingSet, RankingType, ScoringProfile } from "../../shared/types";

/** One row of FantasyPros ECR data (same shape from the API and the pages' embedded `ecrData`). */
interface FpPlayer {
  player_name: string;
  player_team_id?: string;
  player_position_id: string;
  rank_ecr: number;
  pos_rank?: string;
  tier?: number;
  rank_min?: string | number;
  rank_max?: string | number;
  rank_ave?: string | number;
  rank_std?: string | number;
  r2p_pts?: string | number;
  player_opponent?: string;
  start_sit_grade?: string;
}

const SCORING_PARAM = { STD: "STD", HALF: "HALF", PPR: "PPR" } as const;
const PAGE_PREFIX = { STD: "", HALF: "half-point-ppr-", PPR: "ppr-" } as const;

function apiKey() {
  return getVault().fantasyPros?.apiKey || config.fantasyProsKey;
}

/** FantasyPros partner API (needs a key). */
async function viaApi(type: RankingType, base: ScoringProfile["base"], position: string, week: number, season: string): Promise<FpPlayer[]> {
  const t = type === "weekly" ? "weekly" : type === "ros" ? "ros" : type === "dynasty" ? "dynasty" : "draft";
  const qs = new URLSearchParams({ type: t, scoring: SCORING_PARAM[base], position, ...(type === "weekly" ? { week: String(week) } : {}) });
  const r = await fetchJson<{ players: FpPlayer[] }>(`https://api.fantasypros.com/public/v2/json/nfl/${season}/consensus-rankings?${qs}`, {
    headers: { "x-api-key": apiKey() },
  });
  return r.players ?? [];
}

/** Public rankings pages embed the same data as `var ecrData = {...};`. */
async function viaPage(type: RankingType, base: ScoringProfile["base"], position: string): Promise<FpPlayer[]> {
  const pos = position.toLowerCase();
  const prefix = pos === "qb" || pos === "k" || pos === "dst" ? "" : PAGE_PREFIX[base];
  const slug =
    type === "weekly"
      ? `${prefix}${pos}`
      : type === "ros"
        ? `ros-${prefix}${pos}`
        : type === "dynasty"
          ? `dynasty-${pos === "op" ? "superflex" : pos === "flex" ? "overall" : pos}`
          : `${prefix}${pos === "flex" ? "cheatsheets" : pos === "op" ? "superflex-cheatsheets" : pos + "-cheatsheets"}`;
  const html = await fetchText(`https://www.fantasypros.com/nfl/rankings/${slug}.php`);
  const m = html.match(/var\s+ecrData\s*=\s*(\{[\s\S]*?\});\s*\n/);
  if (!m) throw new Error(`FantasyPros page ${slug} had no ranking data`);
  return (JSON.parse(m[1]) as { players: FpPlayer[] }).players ?? [];
}

async function fetchPosition(type: RankingType, base: ScoringProfile["base"], position: string, week: number, season: string) {
  return cached(`fp:${type}:${base}:${position}:${week}:${season}`, 2 * HOUR, async () => {
    if (apiKey()) {
      try {
        return await viaApi(type, base, position, week, season);
      } catch (e) {
        console.warn("[fantasypros] API failed, falling back to pages:", (e as Error).message);
      }
    }
    return viaPage(type, base, position);
  });
}

const num = (x: unknown) => (x === undefined || x === null || x === "" ? null : Number(x));

/**
 * Rankings for a points system.
 *
 * - STD / HALF / PPR come straight from FantasyPros ECR.
 * - Superflex uses FantasyPros' OP (QB/RB/WR/TE) list when available; otherwise QBs are merged
 *   into the FLEX list by projected points.
 * - TE premium re-orders the overall list using projections with the TE bonus added
 *   (FantasyPros doesn't publish TE-premium weekly ECR); TE position ranks are unchanged.
 */
export async function fantasyProsRankings(type: RankingType, scoring: ScoringProfile, position: string, weekIn?: number): Promise<RankingSet> {
  const state = await sportState("nfl");
  const week = weekIn ?? state.week;
  if (config.demo) {
    return { source: "fantasypros", type, scoring, week: type === "weekly" ? week : null, updated: new Date().toISOString(), rows: demoRankingRows("nfl", scoring, position, week ?? DEMO_STATE.week), note: "Demo rankings" };
  }
  const [db, proj] = await Promise.all([playerDb("nfl"), weekProjections("nfl", state.season, week).catch(() => new Map())]);
  const overall = position === "ALL" || position === "FLEX" || position === "OP";
  let note: string | undefined;

  let players: FpPlayer[];
  if (overall && scoring.superflex) {
    try {
      players = await fetchPosition(type, scoring.base, "OP", week, state.season);
    } catch {
      const [qb, flex] = await Promise.all([fetchPosition(type, scoring.base, "QB", week, state.season), fetchPosition(type, scoring.base, "FLEX", week, state.season)]);
      players = [...qb, ...flex];
      note = "Superflex order built from QB + FLEX rankings by projected points.";
    }
  } else {
    players = await fetchPosition(type, scoring.base, overall ? "FLEX" : position === "DEF" ? "DST" : position, week, state.season);
  }

  let rows: RankingRow[] = players.map((p) => {
    const pos = p.player_position_id === "DST" ? "DEF" : p.player_position_id;
    const team = p.player_team_id ? normalizeTeam(p.player_team_id) : null;
    const match = pos === "DEF" ? (team ? db.byId.get(team) ?? null : null) : matchByName(db, p.player_name, team, pos);
    const projPts = match ? pointsFor(proj.get(match.id), scoring, pos) : null;
    return {
      id: match?.id ?? null,
      name: p.player_name,
      team,
      pos,
      rank: p.rank_ecr,
      posRank: p.pos_rank ?? null,
      tier: p.tier ?? null,
      best: num(p.rank_min),
      worst: num(p.rank_max),
      avg: num(p.rank_ave),
      std: num(p.rank_std),
      proj: projPts ?? num(p.r2p_pts),
      opponent: p.player_opponent ?? null,
      startSit: p.start_sit_grade ?? null,
    };
  });

  const merged = note !== undefined;
  const tep = scoring.tePremium > 0 && overall && type !== "dynasty";
  if (merged || tep) {
    rows.sort((a, b) => (b.proj ?? -1) - (a.proj ?? -1) || a.rank - b.rank);
    rows = rows.map((r, i) => ({ ...r, rank: i + 1 }));
    if (tep) note = [note, `TE premium (+${scoring.tePremium}/rec) applied to projections and the overall order.`].filter(Boolean).join(" ");
  } else {
    rows.sort((a, b) => a.rank - b.rank);
  }

  return { source: "fantasypros", type, scoring, week: type === "weekly" ? week : null, updated: new Date().toISOString(), rows, note };
}
