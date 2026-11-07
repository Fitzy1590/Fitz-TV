import type { RankingSet } from "../../shared/types";

export interface TradeSide {
  ids: string[];
}

export interface TradeResult {
  give: { id: string; name: string; rank: number | null; value: number }[];
  get: { id: string; name: string; rank: number | null; value: number }[];
  giveTotal: number;
  getTotal: number;
  /** Positive = you win the trade. */
  edge: number;
  verdict: string;
  source: string;
}

/**
 * Rank -> trade value. Exponential decay so elite players are worth more than the sum of
 * several mid-tier ones (2-for-1 trades shouldn't win on volume alone).
 */
export function rankValue(rank: number | null, poolSize = 300): number {
  if (!rank || rank > poolSize) return 1;
  return Math.round(1000 * Math.exp(-rank / 38));
}

export function analyzeTrade(set: RankingSet, give: string[], get: string[], names: (id: string) => string): TradeResult {
  const rankOf = new Map(set.rows.filter((r) => r.id).map((r) => [r.id!, r.rank]));
  const side = (ids: string[]) =>
    ids
      .map((id) => {
        const rank = rankOf.get(id) ?? null;
        return { id, name: names(id), rank, value: rankValue(rank) };
      })
      .sort((a, b) => b.value - a.value);
  const g = side(give);
  const r = side(get);
  // Roster-spot tax: each extra player received beyond what you send costs a little (you must cut someone).
  const extra = r.length - g.length;
  const total = (xs: { value: number }[]) => xs.reduce((s, x) => s + x.value, 0);
  const giveTotal = total(g);
  const getTotal = total(r) - Math.max(0, extra) * 40 + Math.max(0, -extra) * 40;
  const edge = getTotal - giveTotal;
  const pct = giveTotal ? edge / giveTotal : 0;
  const verdict =
    !g.length || !r.length ? "Add players to both sides" : pct > 0.25 ? "Smash accept" : pct > 0.08 ? "You win this trade" : pct > -0.08 ? "Fair trade" : pct > -0.25 ? "Slight overpay" : "Decline";
  return { give: g, get: r, giveTotal, getTotal, edge, verdict, source: `${set.source === "footballers" ? "Fantasy Footballers" : "FantasyPros"} ${set.type.toUpperCase()}` };
}
