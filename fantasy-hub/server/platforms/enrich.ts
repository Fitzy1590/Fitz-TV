import { playerDb } from "../players";
import { gameContext, pointsFor, sportState, weekProjections } from "../stats";
import type { LeagueSummary, Player, ScoringProfile } from "../../shared/types";
import { getVault } from "../store";

export function scoringFor(league: LeagueSummary): ScoringProfile {
  return getVault().scoring[league.key] ?? league.detectedScoring;
}

/**
 * Builds the context needed to turn platform players into hub players:
 * canonical info (name/positions/team/injury), projections, opponent and game locks.
 */
export async function enricher(league: LeagueSummary, week: number) {
  const [db, state] = await Promise.all([playerDb(league.sport), sportState(league.sport)]);
  const season = league.season || state.season;
  const [proj, games] = await Promise.all([
    weekProjections(league.sport, season, week).catch(() => new Map()),
    gameContext(league.sport, season, week).catch(() => ({ map: new Map(), hasSchedule: false })),
  ]);
  const scoring = scoringFor(league);

  return {
    db,
    /** Hub player from a canonical (Sleeper) id. */
    fromCanonical(id: string, platformId: string, extra: Partial<Player> = {}): Player {
      const info = db.byId.get(id);
      const team = extra.team ?? info?.team ?? null;
      const g = team ? games.map.get(team) : undefined;
      const pos = info?.positions[0] ?? extra.positions?.[0];
      return {
        id,
        platformId,
        name: info?.name ?? extra.name ?? "Unknown player",
        positions: extra.positions?.length ? extra.positions : info?.positions ?? [],
        team,
        injury: extra.injury !== undefined ? extra.injury : info?.injury ?? null,
        proj: extra.proj ?? pointsFor(proj.get(id), scoring, pos),
        points: extra.points ?? null,
        locked: extra.locked ?? g?.locked ?? false,
        opponent: g?.opponent ?? null,
        bye: league.sport === "nfl" && games.hasSchedule && !!team && !g,
      };
    },
  };
}
