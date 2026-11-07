import { matchByName, normalizeTeam, playerDb } from "../players";
import { getVault, updateVault, type FootballersImport } from "../store";
import type { RankingSet, RankingType, ScoringProfile } from "../../shared/types";

/**
 * The Fantasy Footballers' Ultimate dashboard has no public API. Rankings get into the hub by
 * pasting/uploading the table (copy from the dashboard, or a CSV export). The parser accepts
 * CSV, TSV or copied table text with or without a header row.
 */
export function parseRankingTable(text: string): FootballersImport["rows"] {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (!lines.length) return [];
  const split = (l: string) =>
    l.includes("\t") ? l.split("\t") : l.includes(",") ? l.match(/("([^"]|"")*"|[^,]*)(,|$)/g)!.map((c) => c.replace(/,$/, "").replace(/^"|"$/g, "").replace(/""/g, '"')) : l.split(/\s{2,}/);
  const head = split(lines[0]).map((h) => h.toLowerCase().trim());
  const col = (...names: string[]) => head.findIndex((h) => names.some((n) => h === n || h.startsWith(n)));
  const hasHeader = col("player", "name") !== -1;
  const rows: FootballersImport["rows"] = [];
  let tier: number | null = null;

  if (hasHeader) {
    const ci = { rank: col("rank", "rk", "#", "ovr"), name: col("player", "name"), pos: col("pos", "position"), team: col("team", "tm"), tier: col("tier") };
    for (const l of lines.slice(1)) {
      const c = split(l);
      const name = c[ci.name]?.trim();
      if (!name) continue;
      const posRaw = ci.pos >= 0 ? c[ci.pos] : "";
      rows.push({
        rank: ci.rank >= 0 ? parseInt(c[ci.rank], 10) || rows.length + 1 : rows.length + 1,
        name,
        pos: (posRaw?.match(/[A-Z]+/i)?.[0] ?? "").toUpperCase().replace("DST", "DEF"),
        team: ci.team >= 0 && c[ci.team] ? normalizeTeam(c[ci.team].trim()) : null,
        tier: ci.tier >= 0 ? parseInt(c[ci.tier], 10) || null : null,
      });
    }
    return rows;
  }

  // Free text, e.g. "Tier 1" lines followed by "1. Bijan Robinson RB ATL" / "1 Bijan Robinson (ATL - RB)".
  for (const l of lines) {
    const t = l.match(/^tier\s*(\d+)/i);
    if (t) {
      tier = Number(t[1]);
      continue;
    }
    const m = l.match(/^(\d+)[.)]?\s+(.+?)\s*(?:\(|\s)(?:([A-Z]{2,3})\s*[-–,/]\s*)?(QB|RB|WR|TE|K|DST|DEF)\d*\s*(?:[-–,/]?\s*([A-Z]{2,3}))?\)?\s*$/i);
    if (!m) continue;
    rows.push({ rank: Number(m[1]), name: m[2].replace(/[,(]+$/, "").trim(), pos: m[4].toUpperCase().replace("DST", "DEF"), team: (m[3] ?? m[5]) ? normalizeTeam(m[3] ?? m[5]) : null, tier });
  }
  return rows;
}

export const importKey = (type: RankingType, scoring: string, week: number | null) => `${type}:${scoring}:${type === "weekly" ? week ?? "" : ""}`;

export function saveImport(type: RankingType, scoring: string, week: number | null, text: string): FootballersImport {
  const rows = parseRankingTable(text);
  if (!rows.length) throw new Error("Couldn't find any ranked players in that text");
  const imp: FootballersImport = { type, scoring, week, importedAt: new Date().toISOString(), rows };
  updateVault((v) => {
    v.footballers ??= { imports: {} };
    v.footballers.imports[importKey(type, scoring, week)] = imp;
  });
  return imp;
}

const scoringLabel = (s: ScoringProfile) => `${s.base}${s.superflex ? "-SF" : ""}`;

/** Latest import that fits the request (exact scoring first, then any scoring of that type). */
export async function footballersRankings(type: RankingType, scoring: ScoringProfile, week: number | null): Promise<RankingSet | null> {
  const imports = getVault().footballers?.imports ?? {};
  const all = Object.values(imports).filter((i) => i.type === type && (type !== "weekly" || i.week === week));
  const imp = all.find((i) => i.scoring === scoringLabel(scoring)) ?? all.sort((a, b) => b.importedAt.localeCompare(a.importedAt))[0];
  if (!imp) return null;
  const db = await playerDb("nfl");
  return {
    source: "footballers",
    type,
    scoring,
    week: imp.week,
    updated: imp.importedAt,
    note: imp.scoring !== scoringLabel(scoring) ? `Showing your ${imp.scoring} import.` : undefined,
    rows: imp.rows.map((r) => {
      const match = r.pos === "DEF" && r.team ? db.byId.get(r.team) ?? null : matchByName(db, r.name, r.team, r.pos || null);
      return { id: match?.id ?? null, name: r.name, team: r.team ?? match?.team ?? null, pos: r.pos || match?.pos || "", rank: r.rank, posRank: null, tier: r.tier };
    }),
  };
}

export { scoringLabel };
