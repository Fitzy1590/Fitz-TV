import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { config } from "./config";
import { fetchJson } from "./http";
import type { Sport } from "../shared/types";

/** Raw Sleeper player record (only the fields we use). */
interface SleeperRaw {
  player_id: string;
  full_name?: string;
  first_name?: string;
  last_name?: string;
  position?: string;
  fantasy_positions?: string[] | null;
  team?: string | null;
  age?: number | null;
  height?: string | null;
  weight?: string | null;
  years_exp?: number | null;
  number?: number | null;
  injury_status?: string | null;
  status?: string | null;
  espn_id?: number | string | null;
  yahoo_id?: number | string | null;
  depth_chart_position?: string | null;
  depth_chart_order?: number | null;
  college?: string | null;
  birth_date?: string | null;
  search_rank?: number | null;
  active?: boolean;
}

/** Slim player record served to the client (and used for matching). */
export interface PlayerInfo {
  id: string;
  name: string;
  first: string;
  last: string;
  pos: string;
  positions: string[];
  team: string | null;
  age: number | null;
  height: string | null;
  weight: string | null;
  exp: number | null;
  number: number | null;
  injury: string | null;
  status: string | null;
  depthPos: string | null;
  depthOrder: number | null;
  college: string | null;
  birthDate: string | null;
  searchRank: number | null;
  espnId: string | null;
}

export interface PlayerDb {
  sport: Sport;
  updated: number;
  byId: Map<string, PlayerInfo>;
  byEspn: Map<string, string>;
  byYahoo: Map<string, string>;
  byName: Map<string, string[]>;
  list: PlayerInfo[];
}

const DAY = 24 * 60 * 60 * 1000;
const dbs = new Map<Sport, Promise<PlayerDb>>();

export function normalizeName(name: string): string {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/\b(jr|sr|ii|iii|iv|v)\b\.?/g, "")
    .replace(/[^a-z]/g, "");
}

function slim(r: SleeperRaw): PlayerInfo {
  const h = r.height ? Number(r.height) : NaN;
  const pos = r.position === "DEF" ? "DEF" : r.position ?? "";
  return {
    id: r.player_id,
    name: r.full_name ?? `${r.first_name ?? ""} ${r.last_name ?? ""}`.trim(),
    first: r.first_name ?? "",
    last: r.last_name ?? "",
    pos,
    positions: r.fantasy_positions?.length ? r.fantasy_positions : [pos],
    team: r.team ?? null,
    age: r.age ?? null,
    height: Number.isFinite(h) ? `${Math.floor(h / 12)}'${h % 12}"` : r.height ?? null,
    weight: r.weight ? `${r.weight} lbs` : null,
    exp: r.years_exp ?? null,
    number: r.number ?? null,
    injury: r.injury_status ?? null,
    status: r.status ?? null,
    depthPos: r.depth_chart_position ?? null,
    depthOrder: r.depth_chart_order ?? null,
    college: r.college ?? null,
    birthDate: r.birth_date ?? null,
    searchRank: r.search_rank ?? null,
    espnId: r.espn_id ? String(r.espn_id) : null,
  };
}

function build(sport: Sport, raw: Record<string, SleeperRaw>, updated: number): PlayerDb {
  const db: PlayerDb = { sport, updated, byId: new Map(), byEspn: new Map(), byYahoo: new Map(), byName: new Map(), list: [] };
  for (const r of Object.values(raw)) {
    // Skip long-retired players with no team and no fantasy position; keeps the index small.
    if (!r.player_id || (!r.team && r.active === false && !r.fantasy_positions)) continue;
    const p = slim(r);
    if (!p.name) continue;
    db.byId.set(p.id, p);
    db.list.push(p);
    if (r.espn_id) db.byEspn.set(String(r.espn_id), p.id);
    if (r.yahoo_id) db.byYahoo.set(String(r.yahoo_id), p.id);
    const n = normalizeName(p.name);
    db.byName.set(n, [...(db.byName.get(n) ?? []), p.id]);
  }
  db.list.sort((a, b) => (a.searchRank ?? 1e9) - (b.searchRank ?? 1e9));
  return db;
}

async function loadDb(sport: Sport): Promise<PlayerDb> {
  const file = join(config.dataDir, `players-${sport}.json`);
  if (existsSync(file) && Date.now() - statSync(file).mtimeMs < DAY) {
    return build(sport, JSON.parse(readFileSync(file, "utf8")), statSync(file).mtimeMs);
  }
  try {
    const raw = await fetchJson<Record<string, SleeperRaw>>(`https://api.sleeper.app/v1/players/${sport}`, {}, 60_000);
    mkdirSync(config.dataDir, { recursive: true });
    writeFileSync(file, JSON.stringify(raw));
    return build(sport, raw, Date.now());
  } catch (e) {
    // Fall back to a stale copy rather than failing.
    if (existsSync(file)) return build(sport, JSON.parse(readFileSync(file, "utf8")), statSync(file).mtimeMs);
    throw e;
  }
}

export function playerDb(sport: Sport): Promise<PlayerDb> {
  let p = dbs.get(sport);
  if (!p) {
    p = loadDb(sport);
    dbs.set(sport, p);
    p.catch(() => dbs.delete(sport));
    // Refresh once a day in the background.
    setTimeout(() => dbs.delete(sport), DAY).unref?.();
  }
  return p;
}

/** Install a player db directly (demo mode and tests). */
export function setPlayerDb(sport: Sport, raw: Record<string, SleeperRaw>): void {
  dbs.set(sport, Promise.resolve(build(sport, raw, Date.now())));
}

/** Find the canonical player for a name (+ optional team/position to break ties). */
export function matchByName(db: PlayerDb, name: string, team?: string | null, pos?: string | null): PlayerInfo | null {
  const ids = db.byName.get(normalizeName(name)) ?? [];
  if (!ids.length) return null;
  const cands = ids.map((id) => db.byId.get(id)!).filter(Boolean);
  const t = team ? normalizeTeam(team) : null;
  return (
    cands.find((p) => (!t || p.team === t) && (!pos || p.positions.includes(pos))) ??
    cands.find((p) => !t || p.team === t) ??
    cands.find((p) => !pos || p.positions.includes(pos)) ??
    cands[0]
  );
}

const TEAM_FIX: Record<string, string> = { JAC: "JAX", WSH: "WAS", LA: "LAR", LV: "LV", OAK: "LV", SD: "LAC", STL: "LAR", GB: "GB", KC: "KC", NE: "NE", NO: "NO", SF: "SF", TB: "TB" };
export function normalizeTeam(team: string): string {
  const t = team.toUpperCase();
  return TEAM_FIX[t] ?? t;
}
