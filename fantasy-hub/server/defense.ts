import { cached, HOUR } from "./cache";
import { config } from "./config";
import { playerDb } from "./players";
import { nflSchedule, sportState, weekStats } from "./stats";

/**
 * Matchup strength: fantasy points (PPR) each defense has allowed per game to each position,
 * ranked 1-32 where 32 = allows the most (best matchup for your player).
 */
export type DefenseRanks = Record<string, Record<string, { allowed: number; rank: number }>>;

const POS = ["QB", "RB", "WR", "TE", "K"];

export function defenseRanks(): Promise<DefenseRanks> {
  if (config.demo) return Promise.resolve(demoRanks());
  return cached("defense:nfl", 6 * HOUR, async () => {
    const [state, db] = await Promise.all([sportState("nfl"), playerDb("nfl")]);
    const games = await nflSchedule(state.season);
    const oppOf = new Map<string, string>();
    for (const g of games) {
      oppOf.set(`${g.week}:${g.home}`, g.away);
      oppOf.set(`${g.week}:${g.away}`, g.home);
    }
    const totals = new Map<string, { pts: number; games: Set<number> }>(); // `${def}:${pos}`
    const weeks = Array.from({ length: Math.max(0, state.week - 1) }, (_, i) => i + 1);
    const all = await Promise.all(weeks.map((w) => weekStats("nfl", state.season, w).then((rows) => ({ w, rows }))));
    for (const { w, rows } of all) {
      for (const r of rows) {
        const info = db.byId.get(r.player_id);
        const pos = info?.pos;
        if (!pos || !POS.includes(pos)) continue;
        const team = r.team ?? info?.team;
        const def = r.opponent?.replace("@", "") ?? (team ? oppOf.get(`${w}:${team}`) : undefined);
        if (!def || !r.stats?.pts_ppr) continue;
        const k = `${def}:${pos}`;
        const t = totals.get(k) ?? { pts: 0, games: new Set<number>() };
        t.pts += r.stats.pts_ppr;
        t.games.add(w);
        totals.set(k, t);
      }
    }
    return rank(totals);
  });
}

function rank(totals: Map<string, { pts: number; games: Set<number> }>): DefenseRanks {
  const out: DefenseRanks = {};
  for (const pos of POS) {
    const rows = [...totals.entries()]
      .filter(([k]) => k.endsWith(`:${pos}`))
      .map(([k, v]) => ({ def: k.split(":")[0], allowed: v.pts / Math.max(1, v.games.size) }))
      .sort((a, b) => a.allowed - b.allowed);
    rows.forEach((r, i) => {
      out[r.def] ??= {};
      out[r.def][pos] = { allowed: Math.round(r.allowed * 10) / 10, rank: i + 1 };
    });
  }
  return out;
}

function demoRanks(): DefenseRanks {
  const teams = ["ARI", "ATL", "BAL", "BUF", "CAR", "CHI", "CIN", "CLE", "DAL", "DEN", "DET", "GB", "HOU", "IND", "JAX", "KC", "LAC", "LAR", "LV", "MIA", "MIN", "NE", "NO", "NYG", "NYJ", "PHI", "PIT", "SEA", "SF", "TB", "TEN", "WAS"];
  const totals = new Map<string, { pts: number; games: Set<number> }>();
  teams.forEach((t, i) => POS.forEach((p, j) => totals.set(`${t}:${p}`, { pts: ((i * 7 + j * 13) % 32) + 10, games: new Set([1]) })));
  return rank(totals);
}
