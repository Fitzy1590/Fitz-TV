import { optimizeLineup } from "../shared/lineup";
import type { LeagueSummary, Player, RankingRow } from "../shared/types";
import { scoringFor } from "./platforms/enrich";
import type { Adapter } from "./platforms/types";
import { AdapterError } from "./platforms/types";
import { playerDb } from "./players";
import { fantasyProsRankings } from "./rankings/fantasypros";
import { rankValue } from "./rankings/trade";
import { dynastyValues } from "./values";

const SKILL = ["QB", "RB", "WR", "TE"];

/** Value per player for a league: KTC for dynasty leagues, FantasyPros ROS otherwise. */
export async function leagueValues(league: LeagueSummary): Promise<{ value: Map<string, number>; source: string }> {
  const scoring = scoringFor(league);
  if (league.dynasty) {
    try {
      const ktc = await dynastyValues();
      return { value: new Map(ktc.map((v) => [v.id, scoring.superflex ? v.sf : v.oneQb])), source: `KeepTradeCut ${scoring.superflex ? "superflex" : "1QB"}` };
    } catch {
      /* fall back to ROS */
    }
  }
  const ros = await fantasyProsRankings("ros", scoring, "ALL");
  // Scale to KTC-like numbers so both sources read the same in the UI.
  return { value: new Map(ros.rows.filter((r) => r.id).map((r) => [r.id!, rankValue(r.rank) * 10])), source: "FantasyPros ROS" };
}

/** Every team's roster (canonical ids), from the adapter's ownership map. */
export async function teamRosters(adapter: Adapter, league: LeagueSummary): Promise<Map<string, string[]>> {
  const own = await adapter.ownership(league);
  const out = new Map<string, string[]>();
  for (const [id, team] of own) out.set(team, [...(out.get(team) ?? []), id]);
  return out;
}

async function asPlayers(league: LeagueSummary, ids: string[]): Promise<Player[]> {
  const db = await playerDb(league.sport);
  return ids.map((id) => {
    const i = db.byId.get(id);
    return { id, platformId: id, name: i?.name ?? id, positions: i?.positions ?? [], team: i?.team ?? null, injury: null };
  });
}

// ---------------------------------------------------------------------------
// Waiver targets
// ---------------------------------------------------------------------------
export async function waiverTargets(adapter: Adapter, league: LeagueSummary, pos: string): Promise<{ rows: RankingRow[]; source: string }> {
  if (league.sport !== "nfl") throw new AdapterError("Waiver targets use FantasyPros football rankings, so they're football-only for now");
  const scoring = scoringFor(league);
  const [own, ros, weekly] = await Promise.all([
    adapter.ownership(league),
    fantasyProsRankings("ros", scoring, pos),
    fantasyProsRankings("weekly", scoring, pos).catch(() => null),
  ]);
  const proj = new Map((weekly?.rows ?? []).filter((r) => r.id).map((r) => [r.id!, r.proj ?? null]));
  const rows = ros.rows
    .filter((r) => r.id && !own.has(r.id))
    .slice(0, 30)
    .map((r) => ({ ...r, proj: proj.get(r.id!) ?? r.proj ?? null }));
  return { rows, source: "FantasyPros rest-of-season ECR" };
}

// ---------------------------------------------------------------------------
// Power rankings
// ---------------------------------------------------------------------------
export interface PowerRow {
  team: string;
  mine: boolean;
  record: string;
  starters: number;
  bench: number;
  score: number;
  byPos: Record<string, number>;
  best: { id: string; name: string; value: number }[];
}

export async function powerRankings(adapter: Adapter, league: LeagueSummary): Promise<{ rows: PowerRow[]; source: string }> {
  const [rosters, { value, source }, standings] = await Promise.all([teamRosters(adapter, league), leagueValues(league), adapter.standings(league)]);
  const slots = league.slots.filter((s) => s !== "BN" && s !== "IR");
  const v = (p: Player) => value.get(p.id) ?? 0;
  const rows: PowerRow[] = [];
  for (const [team, ids] of rosters) {
    const players = await asPlayers(league, ids);
    const lineup = optimizeLineup(slots.map((slot) => ({ slot, player: null })), players, v);
    const starting = new Set(lineup.map((s) => s.player?.id));
    const starters = lineup.reduce((s, x) => s + (x.player ? v(x.player) : 0), 0);
    const bench = players.filter((p) => !starting.has(p.id)).map(v).sort((a, b) => b - a).slice(0, 6).reduce((a, b) => a + b, 0);
    const byPos: Record<string, number> = {};
    for (const p of players) if (SKILL.includes(p.positions[0])) byPos[p.positions[0]] = (byPos[p.positions[0]] ?? 0) + v(p);
    const st = standings.find((s) => s.name === team);
    rows.push({
      team,
      mine: team === league.myTeam?.name,
      record: st ? `${st.wins}-${st.losses}${st.ties ? `-${st.ties}` : ""}` : "",
      starters: Math.round(starters),
      bench: Math.round(bench),
      score: 0,
      byPos,
      best: players.map((p) => ({ id: p.id, name: p.name, value: v(p) })).sort((a, b) => b.value - a.value).slice(0, 3),
    });
  }
  const maxRoster = Math.max(1, ...rows.map((r) => r.starters + 0.35 * r.bench));
  const games = (r: PowerRow) => r.record.split("-").map(Number).reduce((a, b) => a + (b || 0), 0);
  for (const r of rows) {
    const [w = 0, , t = 0] = r.record.split("-").map(Number);
    const winPct = games(r) ? (w + t / 2) / games(r) : 0.5;
    // Dynasty: roster strength is what matters. Redraft: blend in results.
    const rosterScore = (r.starters + 0.35 * r.bench) / maxRoster;
    r.score = Math.round(100 * (league.dynasty ? 0.85 * rosterScore + 0.15 * winPct : 0.65 * rosterScore + 0.35 * winPct));
  }
  rows.sort((a, b) => b.score - a.score);
  return { rows, source };
}

// ---------------------------------------------------------------------------
// Trade finder
// ---------------------------------------------------------------------------
export interface TradeIdea {
  partner: string;
  give: { id: string; name: string; pos: string; value: number }[];
  get: { id: string; name: string; pos: string; value: number }[];
  why: string;
  fairness: number; // get - give, as % of give
}

export async function tradeFinder(adapter: Adapter, league: LeagueSummary): Promise<{ ideas: TradeIdea[]; source: string; needs: string[]; surplus: string[] }> {
  if (league.sport !== "nfl") throw new AdapterError("Trade finder is football-only for now");
  const [rosters, { value, source }, db] = await Promise.all([teamRosters(adapter, league), leagueValues(league), playerDb("nfl")]);
  const myName = league.myTeam?.name;
  if (!myName || !rosters.has(myName)) throw new AdapterError("Couldn't find your roster in this league");
  const sf = scoringFor(league).superflex;
  const depth: Record<string, number> = { QB: sf ? 2 : 1, RB: 3, WR: 4, TE: 1 };

  const info = (id: string) => ({ id, name: db.byId.get(id)?.name ?? id, pos: db.byId.get(id)?.pos ?? "", value: Math.round(value.get(id) ?? 0) });
  const byPos = (ids: string[], pos: string) => ids.map(info).filter((p) => p.pos === pos).sort((a, b) => b.value - a.value);
  const strength = (ids: string[], pos: string) => byPos(ids, pos).slice(0, depth[pos]).reduce((s, p) => s + p.value, 0);

  // League-relative strength per position (0 = weakest team, 1 = strongest).
  const teams = [...rosters.keys()];
  const rel = (team: string, pos: string) => {
    const vals = teams.map((t) => strength(rosters.get(t)!, pos)).sort((a, b) => a - b);
    return vals.indexOf(strength(rosters.get(team)!, pos)) / Math.max(1, vals.length - 1);
  };
  const mine = rosters.get(myName)!;
  // Weak spots relative to the league; if none stand out, target the weakest position anyway.
  const ranked = [...SKILL].sort((a, b) => rel(myName, a) - rel(myName, b));
  const weak = ranked.filter((p) => rel(myName, p) < 0.45);
  const needs = weak.length ? weak : ranked.slice(0, 1);
  const surplus = SKILL.filter((p) => rel(myName, p) >= 0.55 && byPos(mine, p).length > depth[p]);

  const ideas: TradeIdea[] = [];
  for (const partner of teams.filter((t) => t !== myName)) {
    const theirs = rosters.get(partner)!;
    const theirNeeds = SKILL.filter((p) => rel(partner, p) < 0.5);
    for (const needPos of needs) {
      if (rel(partner, needPos) <= rel(myName, needPos)) continue; // they can't spare it
      const targets = byPos(theirs, needPos).filter((p) => p.value > 0).slice(depth[needPos] > 1 ? 1 : 0, depth[needPos] + 2);
      const offers = surplus.filter((p) => p !== needPos && (theirNeeds.includes(p) || rel(partner, p) < rel(myName, p)));
      for (const offerPos of offers) {
        const pieces = byPos(mine, offerPos).slice(1); // keep my best at the position
        for (const target of targets) {
          // 1-for-1
          for (const give of pieces) {
            const diff = (target.value - give.value) / Math.max(1, give.value);
            // Accept deals from a modest overpay (-20%) to a slight win (+12%): realistic to get accepted.
            if (diff >= -0.2 && diff <= 0.12) ideas.push({ partner, give: [give], get: [target], fairness: Math.round(diff * 100), why: `You need ${needPos}; ${partner} needs ${offerPos}` });
          }
          // 2-for-1 consolidation
          for (let i = 0; i < pieces.length; i++) {
            for (let j = i + 1; j < pieces.length; j++) {
              const sum = pieces[i].value + pieces[j].value;
              const diff = (target.value - sum * 0.8) / Math.max(1, sum * 0.8); // 2-for-1 discount
              if (Math.abs(diff) <= 0.1 && target.value > pieces[i].value) ideas.push({ partner, give: [pieces[i], pieces[j]], get: [target], fairness: Math.round(diff * 100), why: `Consolidate ${offerPos} depth into a ${needPos} upgrade` });
            }
          }
        }
      }
    }
  }
  const seen = new Set<string>();
  const unique = ideas
    .sort((a, b) => b.get[0].value - a.get[0].value || Math.abs(a.fairness) - Math.abs(b.fairness))
    .filter((i) => {
      const k = `${i.give.map((g) => g.id).join("+")}>${i.get[0].id}`;
      return !seen.has(k) && !!seen.add(k);
    });
  return { ideas: unique.slice(0, 12), source, needs, surplus };
}
