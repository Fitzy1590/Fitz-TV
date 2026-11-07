import type { LeagueSummary, LineupChange, Matchup, Platform, Roster, TeamSummary } from "../../shared/types";

export interface Adapter {
  platform: Platform;
  connected(): boolean;
  leagues(): Promise<LeagueSummary[]>;
  roster(league: LeagueSummary, week: number): Promise<Roster>;
  matchup(league: LeagueSummary, week: number): Promise<Matchup | null>;
  standings(league: LeagueSummary): Promise<TeamSummary[]>;
  /** Push a lineup to the platform. Returns the roster as the platform now reports it. */
  setLineup(league: LeagueSummary, change: LineupChange): Promise<Roster>;
  /** canonical player id -> fantasy team name, for every rostered player in the league. */
  ownership(league: LeagueSummary): Promise<Map<string, string>>;
}

export class AdapterError extends Error {
  constructor(message: string, public status = 400) {
    super(message);
  }
}
