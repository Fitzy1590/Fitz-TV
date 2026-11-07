import { describe, expect, it } from "vitest";
import { parseRankingTable } from "../server/rankings/footballers";
import { analyzeTrade, rankValue } from "../server/rankings/trade";
import { merge, list } from "../server/platforms/yahoo";
import { sleeperScoring } from "../server/platforms/sleeper";
import { pointsFor } from "../server/stats";
import type { RankingSet } from "../shared/types";

describe("Footballers import parser", () => {
  it("reads CSV with a header", () => {
    const rows = parseRankingTable("Rank,Player,Pos,Team,Tier\n1,Bijan Robinson,RB1,ATL,1\n2,\"Chase, Ja'Marr\",WR,CIN,1");
    expect(rows[0]).toEqual({ rank: 1, name: "Bijan Robinson", pos: "RB", team: "ATL", tier: 1 });
    expect(rows[1].name).toBe("Chase, Ja'Marr");
  });
  it("reads tab-separated copy/paste", () => {
    const rows = parseRankingTable("RK\tPlayer\tTeam\tPos\n3\tPuka Nacua\tLAR\tWR");
    expect(rows).toEqual([{ rank: 3, name: "Puka Nacua", pos: "WR", team: "LAR", tier: null }]);
  });
  it("reads free text with tiers", () => {
    const rows = parseRankingTable("Tier 1\n1. Bijan Robinson RB ATL\n2. Jahmyr Gibbs (DET - RB)\nTier 2\n3. Ja'Marr Chase WR CIN");
    expect(rows.map((r) => [r.rank, r.name, r.pos, r.team, r.tier])).toEqual([
      [1, "Bijan Robinson", "RB", "ATL", 1],
      [2, "Jahmyr Gibbs", "RB", "DET", 1],
      [3, "Ja'Marr Chase", "WR", "CIN", 2],
    ]);
  });
});

describe("trade analyzer", () => {
  const set: RankingSet = { source: "fantasypros", type: "ros", scoring: { base: "PPR", superflex: false, tePremium: 0 }, week: null, updated: "", rows: [1, 2, 30, 40].map((rank) => ({ id: `p${rank}`, name: `P${rank}`, team: null, pos: "RB", rank, posRank: null })) };
  it("values elite players above two mid-tier ones", () => {
    const r = analyzeTrade(set, ["p1"], ["p30", "p40"], (id) => id);
    expect(r.edge).toBeLessThan(0);
  });
  it("is monotonic", () => {
    expect(rankValue(1)).toBeGreaterThan(rankValue(10));
    expect(rankValue(null)).toBe(1);
  });
});

describe("platform helpers", () => {
  it("flattens Yahoo JSON", () => {
    expect(merge([[{ a: 1 }, { b: 2 }], { c: 3 }])).toEqual({ a: 1, b: 2, c: 3 });
    expect(list({ "0": "x", "1": "y", count: 2 })).toEqual(["x", "y"]);
  });
  it("detects Sleeper scoring", () => {
    expect(sleeperScoring({ scoring_settings: { rec: 0.5, bonus_rec_te: 0.5 }, roster_positions: ["QB", "RB", "SUPER_FLEX", "BN"] })).toEqual({ base: "HALF", superflex: true, tePremium: 0.5 });
    expect(sleeperScoring({ scoring_settings: {}, roster_positions: ["QB", "QB", "BN"] })).toEqual({ base: "STD", superflex: true, tePremium: 0 });
  });
  it("applies TE premium to points", () => {
    expect(pointsFor({ pts_ppr: 10, rec: 6 }, { base: "PPR", superflex: false, tePremium: 0.5 }, "TE")).toBe(13);
    expect(pointsFor({ pts_ppr: 10, rec: 6 }, { base: "PPR", superflex: false, tePremium: 0.5 }, "WR")).toBe(10);
  });
});
