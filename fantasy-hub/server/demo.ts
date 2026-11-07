// Sample data so the hub can be explored without linking any accounts (DEMO=1).
import type { Adapter } from "./platforms/types";
import { enricher } from "./platforms/enrich";
import type { LeagueSummary, Roster, ScoringProfile, Sport, TeamSummary } from "../shared/types";
import type { StatLine, GameLogRow } from "./stats";

type Raw = [id: string, name: string, pos: string, team: string, ppg: number, age: number, exp: number, injury?: string];

const NFL: Raw[] = [
  ["4984", "Josh Allen", "QB", "BUF", 23.8, 30.5, 8],
  ["4881", "Lamar Jackson", "QB", "BAL", 23.1, 29.8, 8],
  ["6904", "Jalen Hurts", "QB", "PHI", 21.4, 28.3, 6],
  ["4046", "Patrick Mahomes", "QB", "KC", 20.2, 31.1, 9],
  ["d-penix", "Michael Penix Jr.", "QB", "ATL", 16.4, 26.5, 2],
  ["d-stroud", "C.J. Stroud", "QB", "HOU", 17.1, 25.1, 3],
  ["9509", "Bijan Robinson", "RB", "ATL", 19.2, 24.7, 3],
  ["9221", "Jahmyr Gibbs", "RB", "DET", 19.0, 24.6, 3],
  ["4034", "Christian McCaffrey", "RB", "SF", 17.9, 30.4, 9, "Q"],
  ["6813", "Jonathan Taylor", "RB", "IND", 17.4, 27.8, 6],
  ["3198", "Derrick Henry", "RB", "BAL", 16.0, 32.8, 10],
  ["4866", "Saquon Barkley", "RB", "PHI", 16.8, 29.7, 8],
  ["d-achane", "De'Von Achane", "RB", "MIA", 15.9, 25.0, 3],
  ["d-sermon", "Trey Sermon", "RB", "ATL", 3.1, 27.7, 5, "IR"],
  ["d-hampton", "Omarion Hampton", "RB", "LAC", 12.4, 23.4, 1],
  ["7564", "Ja'Marr Chase", "WR", "CIN", 20.1, 26.6, 5],
  ["6794", "Justin Jefferson", "WR", "MIN", 18.7, 27.3, 6],
  ["6786", "CeeDee Lamb", "WR", "DAL", 17.9, 27.6, 6],
  ["9493", "Puka Nacua", "WR", "LAR", 18.2, 25.4, 3],
  ["7547", "Amon-Ra St. Brown", "WR", "DET", 17.6, 26.9, 5],
  ["d-london", "Drake London", "WR", "ATL", 15.8, 25.3, 4],
  ["3321", "Tyreek Hill", "WR", "MIA", 13.1, 32.7, 10, "OUT"],
  ["2133", "Davante Adams", "WR", "LAR", 12.9, 33.9, 12],
  ["d-wilson", "Garrett Wilson", "WR", "NYJ", 14.6, 26.2, 4],
  ["d-zacch", "Olamide Zaccheaus", "WR", "ATL", 6.2, 29.3, 7],
  ["d-dotson", "Jahan Dotson", "WR", "ATL", 5.9, 26.6, 4],
  ["1466", "Travis Kelce", "TE", "KC", 10.8, 37.1, 13],
  ["7553", "Kyle Pitts Sr.", "TE", "ATL", 10.1, 26.1, 5],
  ["d-mcbride", "Trey McBride", "TE", "ARI", 13.4, 26.9, 4],
  ["d-laporta", "Sam LaPorta", "TE", "DET", 11.2, 25.8, 3],
  ["d-folk", "Nick Folk", "K", "ATL", 8.1, 42.0, 17],
  ["d-aubrey", "Brandon Aubrey", "K", "DAL", 9.6, 31.5, 3],
  ["BAL", "Baltimore Ravens", "DEF", "BAL", 8.2, 0, 0],
  ["PIT", "Pittsburgh Steelers", "DEF", "PIT", 7.9, 0, 0],
];

const NBA: Raw[] = [
  ["n-jokic", "Nikola Jokic", "C", "DEN", 62.1, 31.7, 11],
  ["n-sga", "Shai Gilgeous-Alexander", "PG", "OKC", 55.4, 27.4, 8],
  ["n-luka", "Luka Doncic", "PG", "LAL", 57.3, 27.7, 8],
  ["n-wemby", "Victor Wembanyama", "C", "SAS", 54.8, 22.8, 3],
  ["n-tatum", "Jayson Tatum", "SF", "BOS", 46.2, 28.6, 9, "OUT"],
  ["n-ant", "Anthony Edwards", "SG", "MIN", 44.7, 25.2, 6],
  ["n-giannis", "Giannis Antetokounmpo", "PF", "MIL", 56.0, 31.9, 13],
  ["n-haliburton", "Tyrese Haliburton", "PG", "IND", 40.3, 26.6, 7, "IR"],
  ["n-bam", "Bam Adebayo", "C", "MIA", 39.9, 29.3, 9],
  ["n-banchero", "Paolo Banchero", "PF", "ORL", 42.6, 23.9, 4],
  ["n-booker", "Devin Booker", "SG", "PHX", 41.8, 30.0, 11],
  ["n-herro", "Tyler Herro", "SG", "MIA", 36.1, 26.8, 7],
  ["n-mitchell", "Donovan Mitchell", "SG", "CLE", 41.0, 30.1, 9],
];

const raw = (sport: Sport) => (sport === "nfl" ? NFL : NBA);

export function demoPlayers(sport: Sport) {
  const out: Record<string, object> = {};
  const nbaPos: Record<string, string[]> = { PG: ["PG", "SG"], SG: ["SG", "SF"], SF: ["SF", "PF"], PF: ["PF", "C"], C: ["C"] };
  raw(sport).forEach(([id, name, pos, team, , age, exp, injury], i) => {
    const [first, ...rest] = name.split(" ");
    out[id] = {
      player_id: id,
      full_name: name,
      first_name: first,
      last_name: rest.join(" "),
      position: pos,
      fantasy_positions: sport === "nba" ? nbaPos[pos] : [pos],
      team,
      age: age || null,
      height: pos === "DEF" ? null : String(70 + (i % 8)),
      weight: pos === "DEF" ? null : String(195 + ((i * 7) % 60)),
      years_exp: exp,
      number: pos === "DEF" ? null : (i * 3) % 99,
      injury_status: injury ?? null,
      depth_chart_position: pos,
      depth_chart_order: 1,
      search_rank: i + 1,
      active: true,
    };
  });
  return out;
}

export const DEMO_STATE = { season: "2026", week: 9, seasonType: "regular" };

const ppg = new Map([...NFL, ...NBA].map((r) => [r[0], r[4]]));
function seeded(id: string, n: number) {
  // FNV-1a + xorshift mix so neighbouring weeks get unrelated values.
  let h = 2166136261;
  for (const c of `${id}:${n}`) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  h ^= h >>> 15;
  h = Math.imul(h, 2246822507);
  h ^= h >>> 13;
  return ((h >>> 0) % 1000) / 1000;
}

export function demoProjection(id: string, week: number): StatLine | undefined {
  const base = ppg.get(id);
  if (base === undefined) return undefined;
  const v = base * (0.85 + seeded(id, week) * 0.3);
  const rec = ["TE", "WR"].includes(NFL.find((r) => r[0] === id)?.[2] ?? "") ? v / 3 : 1.5;
  return { pts_ppr: round(v), pts_half_ppr: round(v - rec / 2), pts_std: round(v - rec), rec: round(rec) };
}

const round = (x: number) => Math.round(x * 100) / 100;

export function demoLog(sport: Sport, id: string, season: string): GameLogRow[] {
  const r = raw(sport).find((x) => x[0] === id);
  if (!r) return [];
  const pos = r[2];
  const opps = ["@PIT", "CAR", "@GB", "@NO", "BAL", "CHI", "SF", "@TB", "CIN", "KC", "BYE", "@MIN", "DET", "@CLE", "@WAS", "TB", "NO", "@CAR"];
  const played = season === DEMO_STATE.season ? DEMO_STATE.week - 1 : 17;
  return opps.map((opp, i) => {
    const week = i + 1;
    if (opp === "BYE") return { week, opponent: null, stats: {}, proj: null };
    const proj = demoProjection(id, week + Number(season)) ?? null;
    if (week > played) return { week, opponent: opp, stats: {}, proj };
    const k = seeded(id, week + Number(season) * 7);
    const pts = r[4] * (0.35 + k * 1.4);
    const stats: StatLine = { pts_ppr: round(pts), pts_half_ppr: round(pts * 0.93), pts_std: round(pts * 0.86), off_snp: Math.round(45 + k * 35), tm_off_snp: 70, pos_rank_ppr: Math.max(1, Math.round(40 - k * 38)) };
    if (pos === "QB") Object.assign(stats, { pass_att: Math.round(28 + k * 14), pass_cmp: Math.round(19 + k * 10), pass_yd: Math.round(190 + k * 160), pass_td: Math.round(k * 3.4), pass_int: k < 0.25 ? 1 : 0, rush_att: Math.round(3 + k * 6), rush_yd: Math.round(10 + k * 40), rush_td: k > 0.85 ? 1 : 0 });
    if (pos === "RB") Object.assign(stats, { rush_att: Math.round(9 + k * 20), rush_yd: Math.round(40 + k * 150), rush_td: Math.round(k * 2.3), rec_tgt: Math.round(1 + k * 8), rec: Math.round(k * 7), rec_yd: Math.round(k * 60), rec_td: k > 0.9 ? 1 : 0 });
    if (pos === "WR" || pos === "TE") Object.assign(stats, { rec_tgt: Math.round(3 + k * 10), rec: Math.round(2 + k * 8), rec_yd: Math.round(20 + k * 120), rec_td: Math.round(k * 1.8) });
    if (sport === "nba") Object.assign(stats, { pts: Math.round(pts / 2), reb: Math.round(3 + k * 10), ast: Math.round(2 + k * 8), stl: Math.round(k * 3), blk: Math.round(k * 3), fg3m: Math.round(k * 5), min: Math.round(28 + k * 10) });
    stats.rush_ypa = stats.rush_att ? round((stats.rush_yd ?? 0) / stats.rush_att) : 0;
    return { week, opponent: opp, stats, proj };
  });
}

// ---------------------------------------------------------------------------
// Demo leagues
// ---------------------------------------------------------------------------
interface DemoLeague { summary: LeagueSummary; roster: string[]; starters: (string | null)[]; ir: string[]; others: Record<string, string[]> }

const team = (id: string, name: string, w: number, l: number, pf: number): TeamSummary => ({ id, name, wins: w, losses: l, ties: 0, pointsFor: pf });
const sf: ScoringProfile = { base: "PPR", superflex: true, tePremium: 0 };

const leagues: DemoLeague[] = [
  {
    summary: {
      key: "sleeper:demo-dffl2", platform: "sleeper", sport: "nfl", leagueId: "demo-dffl2", name: "DFFL #2 SuperFlex", season: "2026", size: 12,
      slots: ["QB", "RB", "RB", "WR", "WR", "TE", "FLEX", "SUPER_FLEX", "BN", "BN", "BN", "BN", "BN"],
      detectedScoring: sf, myTeam: team("1", "Fitzy's Falcons", 6, 2, 1012.4), standing: 2, dynasty: true, currentWeek: 9,
    },
    roster: ["4984", "d-penix", "9509", "4034", "7564", "d-london", "7553", "6813", "d-zacch", "d-sermon", "d-dotson"],
    starters: ["4984", "9509", "4034", "7564", "d-london", "7553", "6813", "d-penix"],
    ir: ["d-sermon"],
    others: { "Dropbear FC": ["9221", "6794", "4881"], "Gridiron Gurus": ["6786", "9493", "1466"] },
  },
  {
    summary: {
      key: "espn:demo-office", platform: "espn", sport: "nfl", leagueId: "demo-office", name: "Office League", season: "2026", size: 10,
      slots: ["QB", "RB", "RB", "WR", "WR", "TE", "FLEX", "DEF", "K", "BN", "BN", "BN", "BN", "BN", "BN"],
      detectedScoring: { base: "HALF", superflex: false, tePremium: 0 }, myTeam: team("3", "Bijan Mustard", 5, 3, 904.1), standing: 4, currentWeek: 9,
    },
    roster: ["4881", "9509", "3198", "6794", "3321", "d-mcbride", "d-achane", "PIT", "d-aubrey", "6904", "2133", "d-hampton"],
    starters: ["4881", "9509", "3198", "6794", "3321", "d-mcbride", "d-achane", "PIT", "d-aubrey"],
    ir: [],
    others: { "Team Taco": ["4984", "7564", "1466"] },
  },
  {
    summary: {
      key: "yahoo:demo-tep", platform: "yahoo", sport: "nfl", leagueId: "demo-tep", name: "TE Premium Dynasty", season: "2026", size: 12,
      slots: ["QB", "RB", "RB", "WR", "WR", "WR", "TE", "FLEX", "FLEX", "BN", "BN", "BN", "BN"],
      detectedScoring: { base: "PPR", superflex: false, tePremium: 0.5 }, myTeam: team("7", "Kittle Me This", 4, 4, 958.0), standing: 7, dynasty: true, currentWeek: 9,
    },
    roster: ["6904", "4866", "d-hampton", "9493", "7547", "d-wilson", "d-laporta", "1466", "9221", "d-stroud", "d-zacch"],
    starters: ["6904", "4866", "d-hampton", "9493", "7547", "d-wilson", "d-laporta", "1466", "9221"],
    ir: [],
    others: { "Dynasty Dorks": ["9509", "6786", "4046"] },
  },
  {
    summary: {
      key: "sleeper:demo-hoops", platform: "sleeper", sport: "nba", leagueId: "demo-hoops", name: "Hardwood Heroes", season: "2026", size: 10,
      slots: ["PG", "SG", "G", "SF", "PF", "F", "C", "UTIL", "BN", "BN", "BN"],
      detectedScoring: { base: "STD", superflex: false, tePremium: 0 }, myTeam: team("2", "Fitz Buckets", 3, 1, 1840.5), standing: 1, currentWeek: 3,
    },
    roster: ["n-sga", "n-ant", "n-mitchell", "n-tatum", "n-banchero", "n-giannis", "n-jokic", "n-wemby", "n-herro", "n-booker", "n-haliburton"],
    starters: ["n-sga", "n-ant", "n-mitchell", "n-tatum", "n-banchero", "n-giannis", "n-jokic", "n-wemby"],
    ir: ["n-haliburton"],
    others: { "Splash Bros": ["n-luka", "n-bam"] },
  },
];

const find = (l: LeagueSummary) => leagues.find((x) => x.summary.key === l.key)!;

async function rosterOf(l: LeagueSummary, week: number): Promise<Roster> {
  const d = find(l);
  const enrich = await enricher(l, week);
  const p = (id: string) => enrich.fromCanonical(id, id, { points: week < l.currentWeek ? Math.round((ppg.get(id) ?? 0) * 10) / 10 : null });
  const starting = new Set(d.starters.filter(Boolean));
  const ir = new Set(d.ir);
  return {
    leagueKey: l.key,
    week,
    starters: l.slots.filter((s) => s !== "BN").map((slot, i) => ({ slot, player: d.starters[i] ? p(d.starters[i]!) : null })),
    bench: d.roster.filter((id) => !starting.has(id) && !ir.has(id)).map(p),
    reserve: d.ir.map(p),
    taxi: [],
    canEdit: true,
    editNote: "Demo league: changes are saved locally only.",
  };
}

export const demoAdapter: Adapter = {
  platform: "sleeper",
  connected: () => true,
  leagues: async () => leagues.map((l) => l.summary),
  roster: rosterOf,
  async matchup(l, week) {
    const me = l.myTeam!;
    return { week, me: { team: me, points: 61.4, projected: 118.2 }, opponent: { team: team("9", Object.keys(find(l).others)[0], 5, 3, 980), points: 48.9, projected: 112.6 } };
  },
  async standings(l) {
    const names = [l.myTeam!.name, ...Object.keys(find(l).others), "Waiver Wire Warriors", "Sunday Scaries", "Tua Legit", "Hurts So Good"];
    return names.slice(0, l.size).map((n, i) => team(String(i), n, 8 - i, i, 1100 - i * 37)).sort((a, b) => b.wins - a.wins);
  },
  async setLineup(l, change) {
    const d = find(l);
    for (const id of change.starters) if (id && !d.roster.includes(id)) throw new Error("Player is not on your roster");
    d.starters = change.starters;
    return rosterOf(l, change.week);
  },
  async ownership(l) {
    const d = find(l);
    const out = new Map<string, string>();
    d.roster.forEach((id) => out.set(id, l.myTeam!.name));
    for (const [name, ids] of Object.entries(d.others)) ids.forEach((id) => out.set(id, name));
    return out;
  },
};

/** Demo ECR: order by projected points. */
export function demoRankingRows(sport: Sport, scoring: ScoringProfile, position: string, week: number) {
  const all = raw(sport)
    .filter(([, , pos]) => position === "ALL" || position === "FLEX" || position === "OP" ? (scoring.superflex || position === "OP" ? ["QB", "RB", "WR", "TE"] : ["RB", "WR", "TE"]).includes(pos) : pos === position)
    .map(([id, name, pos, team]) => {
      const proj = demoProjection(id, week)!;
      const base = scoring.base === "PPR" ? proj.pts_ppr : scoring.base === "HALF" ? proj.pts_half_ppr : proj.pts_std;
      return { id, name, pos, team, proj: round(base + (pos === "TE" ? scoring.tePremium * proj.rec : 0)) };
    })
    .sort((a, b) => b.proj - a.proj);
  const posCount: Record<string, number> = {};
  return all.map((r, i) => ({ ...r, rank: i + 1, posRank: `${r.pos}${(posCount[r.pos] = (posCount[r.pos] ?? 0) + 1)}`, tier: Math.floor(i / 6) + 1, best: Math.max(1, i - 2), worst: i + 5, avg: i + 1.4, std: 1.2, opponent: null, startSit: null }));
}
