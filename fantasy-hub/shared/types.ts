// Platform-neutral model shared by the server adapters and the client.

export type Sport = "nfl" | "nba";
export type Platform = "sleeper" | "espn" | "yahoo";
export type BaseScoring = "STD" | "HALF" | "PPR";

/** Points system used to pull rankings for a football league. */
export interface ScoringProfile {
  base: BaseScoring;
  superflex: boolean;
  /** Extra points per TE reception (0 = no TE premium). */
  tePremium: number;
}

export const DEFAULT_SCORING: ScoringProfile = { base: "PPR", superflex: false, tePremium: 0 };

/**
 * Canonical lineup slots. Every adapter translates its own slot codes into these.
 * Football: QB RB WR TE K DEF FLEX (RB/WR/TE) WRRB (RB/WR) REC (WR/TE) SUPER_FLEX (QB/RB/WR/TE) IDP_FLEX DL LB DB
 * Basketball: PG SG SF PF C G F UTIL
 * Shared: BN IR TAXI
 */
export type Slot = string;

export const SLOT_ELIGIBILITY: Record<string, string[]> = {
  QB: ["QB"],
  RB: ["RB"],
  WR: ["WR"],
  TE: ["TE"],
  K: ["K"],
  DEF: ["DEF"],
  FLEX: ["RB", "WR", "TE"],
  WRRB: ["RB", "WR"],
  REC: ["WR", "TE"],
  SUPER_FLEX: ["QB", "RB", "WR", "TE"],
  DL: ["DL", "DE", "DT"],
  LB: ["LB"],
  DB: ["DB", "CB", "S"],
  IDP_FLEX: ["DL", "DE", "DT", "LB", "DB", "CB", "S"],
  PG: ["PG"],
  SG: ["SG"],
  SF: ["SF"],
  PF: ["PF"],
  C: ["C"],
  G: ["PG", "SG"],
  F: ["SF", "PF"],
  UTIL: ["PG", "SG", "SF", "PF", "C"],
  "SG/SF": ["SG", "SF"],
  "G/F": ["PG", "SG", "SF", "PF"],
  "PF/C": ["PF", "C"],
  "F/C": ["SF", "PF", "C"],
};

export const BENCH_SLOTS = new Set(["BN", "IR", "TAXI"]);

export interface Player {
  /** Canonical id: the Sleeper player id when known (drives headshots), else `${platform}:${id}`. */
  id: string;
  /** The id the source platform uses for this player. */
  platformId: string;
  name: string;
  /** Positions this player is eligible for (first is primary). */
  positions: string[];
  team: string | null;
  injury?: string | null;
  /** Projected points for the current week, using the league's scoring when the platform gives it. */
  proj?: number | null;
  /** Points scored so far this week. */
  points?: number | null;
  /** True when the player's game has started and the platform won't let them move. */
  locked?: boolean;
  /** Opponent this week, e.g. "@TB" or "CIN". */
  opponent?: string | null;
  bye?: boolean;
  /** Slots the platform says this player may fill (authoritative when present). */
  eligibleSlots?: string[];
}

export interface RosterSpot {
  slot: Slot;
  player: Player | null;
}

export interface Roster {
  leagueKey: string;
  week: number;
  /** Starting slots in display order (no BN/IR). */
  starters: RosterSpot[];
  bench: Player[];
  reserve: Player[];
  taxi: Player[];
  /** True when the platform adapter can push lineup changes for this league. */
  canEdit: boolean;
  editNote?: string;
}

export interface TeamSummary {
  id: string;
  name: string;
  owner?: string;
  avatar?: string | null;
  wins: number;
  losses: number;
  ties: number;
  pointsFor: number;
}

export interface LeagueSummary {
  key: string; // `${platform}:${leagueId}`
  platform: Platform;
  sport: Sport;
  leagueId: string;
  name: string;
  season: string;
  avatar?: string | null;
  size: number;
  slots: Slot[];
  /** Points system detected from league settings. */
  detectedScoring: ScoringProfile;
  myTeam: TeamSummary | null;
  /** Rank of my team in the standings (1 = first). */
  standing?: number | null;
  dynasty?: boolean;
  currentWeek: number;
}

export interface Matchup {
  week: number;
  me: { team: TeamSummary; points: number; projected?: number | null };
  opponent: { team: TeamSummary; points: number; projected?: number | null } | null;
}

/** A requested lineup: slot index (in Roster.starters order) -> player id (platformId), or null for empty. */
export interface LineupChange {
  week: number;
  starters: (string | null)[];
}

export interface RankingRow {
  /** Canonical (Sleeper) id when matched. */
  id: string | null;
  name: string;
  team: string | null;
  pos: string;
  rank: number;
  posRank: string | null;
  tier?: number | null;
  best?: number | null;
  worst?: number | null;
  avg?: number | null;
  std?: number | null;
  /** Projected points under the requested scoring profile (TE premium applied). */
  proj?: number | null;
  opponent?: string | null;
  startSit?: string | null;
}

export type RankingType = "weekly" | "ros" | "draft" | "dynasty";

export interface RankingSet {
  source: "fantasypros" | "footballers";
  type: RankingType;
  scoring: ScoringProfile;
  week: number | null;
  updated: string;
  rows: RankingRow[];
  note?: string;
}

export interface Account {
  platform: Platform | "fantasypros" | "footballers";
  label: string;
  connected: boolean;
  detail?: string;
}
