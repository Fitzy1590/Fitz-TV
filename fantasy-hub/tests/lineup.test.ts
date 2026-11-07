import { describe, expect, it } from "vitest";
import { canPlay, lineupWarnings, optimizeLineup } from "../shared/lineup";
import type { Player, RosterSpot } from "../shared/types";

const p = (id: string, pos: string, proj: number, extra: Partial<Player> = {}): Player => ({ id, platformId: id, name: id, positions: [pos], team: "ATL", proj, ...extra });

describe("canPlay", () => {
  it("handles flex slots", () => {
    expect(canPlay(p("a", "QB", 0), "SUPER_FLEX")).toBe(true);
    expect(canPlay(p("a", "QB", 0), "FLEX")).toBe(false);
    expect(canPlay(p("a", "TE", 0), "REC")).toBe(true);
    expect(canPlay(p("a", "TE", 0), "WRRB")).toBe(false);
  });
  it("prefers platform eligibility when given", () => {
    expect(canPlay(p("a", "PG", 0, { eligibleSlots: ["PG", "G", "UTIL"] }), "SG")).toBe(false);
    expect(canPlay(p("a", "PG", 0, { eligibleSlots: ["PG", "G", "UTIL"] }), "G")).toBe(true);
  });
});

describe("optimizeLineup", () => {
  const slots = ["QB", "RB", "RB", "WR", "TE", "FLEX", "SUPER_FLEX"];
  const empty = (): RosterSpot[] => slots.map((slot) => ({ slot, player: null }));
  const val = (x: Player) => x.proj ?? 0;

  it("fills fixed slots before flexes and puts the 2nd QB in superflex", () => {
    const pool = [p("qb1", "QB", 24), p("qb2", "QB", 18), p("rb1", "RB", 20), p("rb2", "RB", 15), p("rb3", "RB", 12), p("wr1", "WR", 19), p("wr2", "WR", 11), p("te1", "TE", 9)];
    const out = optimizeLineup(empty(), pool, val);
    expect(out.map((s) => s.player?.id)).toEqual(["qb1", "rb1", "rb2", "wr1", "te1", "rb3", "qb2"]);
  });

  it("keeps locked starters and skips players who are out or on bye", () => {
    const start = empty();
    start[1].player = p("lockedRb", "RB", 2, { locked: true });
    const pool = [p("qb", "QB", 20), p("rbOut", "RB", 30, { injury: "OUT" }), p("rbBye", "RB", 25, { bye: true }), p("rb", "RB", 10), p("wr", "WR", 12), p("te", "TE", 8)];
    const out = optimizeLineup(start, pool, val);
    expect(out[1].player?.id).toBe("lockedRb");
    expect(out.some((s) => s.player?.id === "rbOut" || s.player?.id === "rbBye")).toBe(false);
  });

  it("flags lineup problems", () => {
    const spots: RosterSpot[] = [{ slot: "QB", player: null }, { slot: "RB", player: p("x", "RB", 1, { injury: "OUT" }) }];
    expect(lineupWarnings(spots)).toEqual(["Empty QB slot", "x is OUT"]);
  });
});

describe("keepSlots", () => {
  it("doesn't shuffle a starter between RB and FLEX for no reason", () => {
    const slots = ["RB", "RB", "FLEX"];
    const before: RosterSpot[] = [
      { slot: "RB", player: p("a", "RB", 10) },
      { slot: "RB", player: p("b", "RB", 9) },
      { slot: "FLEX", player: p("c", "RB", 20) },
    ];
    const out = optimizeLineup(before, [], (x) => x.proj ?? 0);
    expect(out.map((s) => s.player?.id)).toEqual(["a", "b", "c"]);
    expect(slots).toHaveLength(3);
  });
});
