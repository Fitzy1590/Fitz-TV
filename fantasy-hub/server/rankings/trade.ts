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

export type ValueLookup = (id: string) => { rank: number | null; value: number };

/** Value lookup from a ranking set (rank -> value curve). */
export function rankingLookup(set: RankingSet): ValueLookup {
  const rankOf = new Map(set.rows.filter((r) => r.id).map((r) => [r.id!, r.rank]));
  return (id) => {
    const rank = rankOf.get(id) ?? null;
    return { rank, value: rankValue(rank) };
  };
}

export function analyzeTrade(lookup: ValueLookup, give: string[], get: string[], names: (id: string) => string, source: string): TradeResult {
  const side = (ids: string[]) => ids.map((id) => ({ id, name: names(id), ...lookup(id) })).sort((a, b) => b.value - a.value);
  const g = side(give);
  const r = side(get);
  const total = (xs: { value: number }[]) => xs.reduce((s, x) => s + x.value, 0);
  const giveTotal = total(g);
  // Roster-spot tax: receiving more players than you send means cutting someone.
  const tax = Math.round(0.04 * Math.max(giveTotal, total(r)) / Math.max(1, Math.max(g.length, r.length)));
  const extra = r.length - g.length;
  const getTotal = total(r) - Math.max(0, extra) * tax + Math.max(0, -extra) * tax;
  const edge = getTotal - giveTotal;
  const pct = giveTotal ? edge / giveTotal : 0;
  const verdict =
    !g.length || !r.length ? "Add players to both sides" : pct > 0.25 ? "Smash accept" : pct > 0.08 ? "You win this trade" : pct > -0.08 ? "Fair trade" : pct > -0.25 ? "Slight overpay" : "Decline";
  return { give: g, get: r, giveTotal, getTotal, edge, verdict, source };
}
