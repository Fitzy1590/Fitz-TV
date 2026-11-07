import { BENCH_SLOTS, SLOT_ELIGIBILITY, type Player, type RosterSpot } from "./types";

export function canPlay(player: Pick<Player, "positions" | "eligibleSlots">, slot: string): boolean {
  if (BENCH_SLOTS.has(slot)) return true;
  if (player.eligibleSlots?.length) return player.eligibleSlots.includes(slot);
  const allowed = SLOT_ELIGIBILITY[slot] ?? [slot];
  return player.positions.some((p) => allowed.includes(p));
}

/** How many positions a slot accepts. Fewer = more restrictive, filled first. */
function breadth(slot: string): number {
  return (SLOT_ELIGIBILITY[slot] ?? [slot]).length;
}

/**
 * Best lineup for a set of slots.
 *
 * Players are taken in descending value; each goes into the most restrictive open
 * slot they can fill. Locked starters (game already kicked off) stay where they are,
 * and locked bench players are never moved in. For the slot layouts fantasy leagues
 * use (fixed positions plus nested flexes) this greedy order is optimal.
 */
export function optimizeLineup(
  starters: RosterSpot[],
  pool: Player[],
  value: (p: Player) => number,
): RosterSpot[] {
  const result: RosterSpot[] = starters.map((s) => ({ slot: s.slot, player: null }));
  const used = new Set<string>();

  starters.forEach((s, i) => {
    if (s.player?.locked) {
      result[i].player = s.player;
      used.add(s.player.id);
    }
  });

  const candidates = [...starters.map((s) => s.player).filter((p): p is Player => !!p), ...pool]
    .filter((p, i, arr) => arr.findIndex((q) => q.id === p.id) === i)
    .filter((p) => !used.has(p.id) && !p.locked && !isOut(p))
    .sort((a, b) => value(b) - value(a));

  for (const p of candidates) {
    let best = -1;
    for (let i = 0; i < result.length; i++) {
      if (result[i].player || !canPlay(p, result[i].slot)) continue;
      if (best === -1 || breadth(result[i].slot) < breadth(result[best].slot)) best = i;
    }
    if (best !== -1) {
      result[best].player = p;
      used.add(p.id);
    }
  }
  return keepSlots(starters, result);
}

/**
 * Same starters, fewer moves: if a player who was already starting landed in a different
 * slot, swap them back to their original slot when both players stay eligible. Stops the
 * optimizer from shuffling RB/FLEX for no gain.
 */
export function keepSlots(before: RosterSpot[], after: RosterSpot[]): RosterSpot[] {
  const out = after.map((s) => ({ ...s }));
  for (let pass = 0; pass < 3; pass++) {
    let changed = false;
    for (let j = 0; j < before.length; j++) {
      const orig = before[j].player;
      if (!orig || out[j].player?.id === orig.id) continue;
      const i = out.findIndex((s) => s.player?.id === orig.id);
      if (i === -1) continue;
      const other = out[j].player;
      if (canPlay(orig, out[j].slot) && (!other || canPlay(other, out[i].slot)) && !other?.locked) {
        out[j].player = orig;
        out[i].player = other;
        changed = true;
      }
    }
    if (!changed) break;
  }
  return out;
}

export function isOut(p: Pick<Player, "injury" | "bye">): boolean {
  if (p.bye) return true;
  const s = (p.injury ?? "").toUpperCase();
  return s === "OUT" || s === "IR" || s === "O" || s === "SUS" || s === "PUP" || s === "NA" || s === "COV";
}

/** Problems worth flagging on a lineup: empty slots, starters who are out or on bye. */
export function lineupWarnings(starters: RosterSpot[]): string[] {
  const out: string[] = [];
  for (const s of starters) {
    if (!s.player) out.push(`Empty ${s.slot} slot`);
    else if (s.player.bye) out.push(`${s.player.name} is on bye`);
    else if (isOut(s.player)) out.push(`${s.player.name} is ${s.player.injury}`);
  }
  return out;
}
