import { cached, MIN } from "./cache";
import { config } from "./config";
import { fetchJson } from "./http";
import { playerDb } from "./players";
import { fantasyProsRankings } from "./rankings/fantasypros";
import { sportState } from "./stats";
import { getVault } from "./store";
import { dynastyValues } from "./values";
import { AdapterError } from "./platforms/types";
import { scoringFor } from "./platforms/enrich";
import type { LeagueSummary, ScoringProfile } from "../shared/types";

// Draft assistant for Sleeper drafts (league drafts and mocks) via Sleeper's public API.
const API = "https://api.sleeper.app/v1";

interface SDraft {
  draft_id: string;
  league_id: string | null;
  status: string; // pre_draft | drafting | paused | complete
  type: string; // snake | linear | auction
  season: string;
  start_time: number | null;
  settings: { teams: number; rounds: number; reversal_round?: number; player_type?: number };
  draft_order: Record<string, number> | null;
  metadata?: { name?: string; scoring_type?: string; description?: string };
}
interface SPick {
  pick_no: number;
  round: number;
  draft_slot: number;
  player_id: string;
  picked_by: string;
  metadata?: { first_name?: string; last_name?: string; position?: string; team?: string };
}

export interface DraftSummary {
  draftId: string;
  name: string;
  status: string;
  type: string;
  start: number | null;
}

export async function listDrafts(leagues: LeagueSummary[]): Promise<DraftSummary[]> {
  if (config.demo) return [{ draftId: "demo-draft", name: "DFFL #2 SuperFlex rookie draft", status: "drafting", type: "snake", start: Date.now() }];
  const acct = getVault().sleeper;
  if (!acct) return [];
  const state = await sportState("nfl");
  const fromLeagues = await Promise.all(
    leagues.filter((l) => l.platform === "sleeper").map((l) =>
      fetchJson<SDraft[]>(`${API}/league/${l.leagueId}/drafts`).then((ds) => ds.map((d) => ({ d, name: `${l.name} ${d.settings.player_type === 1 ? "rookie draft" : "draft"}` }))).catch(() => []),
    ),
  );
  const mine = await fetchJson<SDraft[]>(`${API}/user/${acct.userId}/drafts/nfl/${state.season}`).catch(() => [] as SDraft[]);
  const all = [...fromLeagues.flat(), ...mine.map((d) => ({ d, name: d.metadata?.name || "Mock draft" }))];
  const seen = new Set<string>();
  return all
    .filter(({ d }) => !seen.has(d.draft_id) && !!seen.add(d.draft_id))
    .map(({ d, name }) => ({ draftId: d.draft_id, name, status: d.status, type: d.type, start: d.start_time }))
    .sort((a, b) => (a.status === "drafting" ? -1 : 0) - (b.status === "drafting" ? -1 : 0) || (b.start ?? 0) - (a.start ?? 0));
}

/** Which draft slot picks at overall pick n (1-based). */
export function slotForPick(n: number, teams: number, type: string, reversalRound = 0): number {
  const round = Math.ceil(n / teams);
  const idx = (n - 1) % teams;
  if (type !== "snake") return idx + 1;
  // Third-round reversal flips the snake direction from that round on.
  const flipped = reversalRound > 0 && round >= reversalRound;
  const forward = (round % 2 === 1) !== flipped;
  return forward ? idx + 1 : teams - idx;
}

export async function draftBoard(draftId: string, leagues: LeagueSummary[]) {
  const [draft, picks] = config.demo ? demoDraft() : await Promise.all([
    cached(`draft:${draftId}`, 0.1 * MIN, () => fetchJson<SDraft>(`${API}/draft/${draftId}`)),
    cached(`picks:${draftId}`, 0.05 * MIN, () => fetchJson<SPick[]>(`${API}/draft/${draftId}/picks`)),
  ]);
  if (!draft) throw new AdapterError("Draft not found", 404);
  const userId = config.demo ? "me" : getVault().sleeper?.userId;
  const mySlot = userId && draft.draft_order ? draft.draft_order[userId] ?? null : null;
  const teams = draft.settings.teams;
  const total = teams * draft.settings.rounds;
  const current = picks.length + 1;
  const myPicks = mySlot ? Array.from({ length: total }, (_, i) => i + 1).filter((n) => slotForPick(n, teams, draft.type, draft.settings.reversal_round) === mySlot) : [];
  const nextMine = myPicks.find((n) => n >= current) ?? null;

  const league = leagues.find((l) => l.platform === "sleeper" && l.leagueId === draft.league_id) ?? null;
  const rookieOnly = draft.settings.player_type === 1;
  const dynasty = league?.dynasty || rookieOnly;
  const scoring: ScoringProfile = league ? scoringFor(league) : { base: draft.metadata?.scoring_type?.includes("half") ? "HALF" : draft.metadata?.scoring_type?.includes("std") ? "STD" : "PPR", superflex: !!draft.metadata?.scoring_type?.includes("2qb") || !!draft.metadata?.scoring_type?.includes("sf"), tePremium: 0 };
  const db = await playerDb("nfl");
  const taken = new Set(picks.map((p) => p.player_id));

  let board: { id: string; name: string; pos: string; team: string | null; rank: number; tier: number | null; value?: number }[];
  let source: string;
  if (dynasty) {
    const vals = await dynastyValues();
    const pool = vals.filter((v) => v.pos !== "PICK" && (!rookieOnly || (db.byId.get(v.id)?.exp ?? 1) === 0));
    board = pool.map((v, i) => ({ id: v.id, name: v.name, pos: v.pos, team: v.team, rank: i + 1, tier: null, value: scoring.superflex ? v.sf : v.oneQb }));
    board.sort((a, b) => (b.value ?? 0) - (a.value ?? 0)).forEach((b, i) => (b.rank = i + 1));
    source = `KeepTradeCut ${scoring.superflex ? "superflex" : "1QB"}${rookieOnly ? " rookies" : ""}`;
  } else {
    const set = await fantasyProsRankings("draft", scoring, "ALL");
    board = set.rows.filter((r) => r.id).map((r) => ({ id: r.id!, name: r.name, pos: r.pos, team: r.team, rank: r.rank, tier: r.tier ?? null }));
    source = "FantasyPros draft ECR";
  }
  const myTeam = mySlot ? picks.filter((p) => p.draft_slot === mySlot).map((p) => ({ id: p.player_id, name: db.byId.get(p.player_id)?.name ?? `${p.metadata?.first_name ?? ""} ${p.metadata?.last_name ?? ""}`.trim(), pos: db.byId.get(p.player_id)?.pos ?? p.metadata?.position ?? "", round: p.round })) : [];

  return {
    draftId,
    status: draft.status,
    type: draft.type,
    teams,
    rounds: draft.settings.rounds,
    current: Math.min(current, total),
    onClock: current <= total ? slotForPick(current, teams, draft.type, draft.settings.reversal_round) : null,
    mySlot,
    myPicks,
    nextMine,
    picksUntilMine: nextMine ? nextMine - current : null,
    recent: picks.slice(-8).reverse().map((p) => ({ pick: p.pick_no, round: p.round, slot: p.draft_slot, id: p.player_id, name: db.byId.get(p.player_id)?.name ?? `${p.metadata?.first_name ?? ""} ${p.metadata?.last_name ?? ""}`.trim(), pos: db.byId.get(p.player_id)?.pos ?? p.metadata?.position ?? "" })),
    myTeam,
    available: board.filter((b) => !taken.has(b.id)).slice(0, 80),
    source,
  };
}

let demoPickCount = 9;
function demoDraft(): [SDraft, SPick[]] {
  const draft: SDraft = { draft_id: "demo-draft", league_id: "demo-dffl2", status: "drafting", type: "snake", season: "2026", start_time: Date.now(), settings: { teams: 12, rounds: 4, player_type: 0 }, draft_order: { me: 5 }, metadata: { name: "Demo" } };
  const order = ["9509", "4984", "9221", "7564", "4881", "6794", "9493", "6786", "6904", "4046", "7547", "4866", "6813", "3198", "d-achane"];
  demoPickCount = Math.min(order.length, demoPickCount + (Math.random() < 0.3 ? 1 : 0));
  const picks = order.slice(0, demoPickCount).map((id, i) => ({ pick_no: i + 1, round: Math.ceil((i + 1) / 12), draft_slot: slotForPick(i + 1, 12, "snake"), player_id: id, picked_by: "x" }));
  return [draft, picks];
}
