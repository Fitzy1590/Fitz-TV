import type { Account, LeagueSummary, LineupChange, Matchup, RankingSet, Roster, ScoringProfile, TeamSummary } from "../shared/types";

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

async function req<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`/api${path}`, {
    credentials: "same-origin",
    ...init,
    headers: init.body ? { "content-type": "application/json", ...(init.headers ?? {}) } : init.headers,
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new ApiError((body as { error?: string }).error ?? `Request failed (${res.status})`, res.status);
  return body as T;
}

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export type League = LeagueSummary & { scoring: ScoringProfile; hidden: boolean };

export interface PlayerInfo {
  id: string;
  name: string;
  first: string;
  last: string;
  pos: string;
  positions: string[];
  team: string | null;
  age: number | null;
  height: string | null;
  weight: string | null;
  exp: number | null;
  number: number | null;
  injury: string | null;
  status: string | null;
  depthPos: string | null;
  depthOrder: number | null;
  college: string | null;
  birthDate: string | null;
  searchRank: number | null;
}

export type StatLine = Record<string, number>;
export interface PlayerProfile {
  info: PlayerInfo | null;
  season: string;
  currentWeek: number;
  log: { week: number; opponent: string | null; stats: StatLine; proj: StatLine | null }[];
  career: { season: string; team: string | null; stats: StatLine }[];
  availability: { leagueKey: string; league: string; platform: string; owner: string | null; mine: boolean; known: boolean }[];
  depth: { id: string; name: string; pos: string; order: number; age: number | null; injury: string | null }[];
}

export interface TradeResult {
  give: { id: string; name: string; rank: number | null; value: number }[];
  get: { id: string; name: string; rank: number | null; value: number }[];
  giveTotal: number;
  getTotal: number;
  edge: number;
  verdict: string;
  source: string;
}

export const api = {
  session: () => req<{ authed: boolean; passwordRequired: boolean; demo: boolean }>("/session"),
  login: (password: string) => req("/login", json("POST", { password })),
  logout: () => req("/logout", { method: "POST" }),

  accounts: () => req<{ accounts: Account[]; demo: boolean }>("/accounts"),
  linkSleeper: (b: { username: string; password?: string; token?: string }) => req("/accounts/sleeper", json("POST", b)),
  linkEspn: (b: { s2: string; swid: string; leagues: { id: string; sport: "nfl" | "nba" }[] }) => req<{ leagues: unknown[] }>("/accounts/espn", json("POST", b)),
  linkFantasyPros: (apiKey: string) => req("/accounts/fantasypros", json("POST", { apiKey })),
  importFootballers: (b: { type: string; scoring: ScoringProfile; week: number | null; text: string }) => req<{ count: number }>("/accounts/footballers/import", json("POST", b)),
  unlink: (platform: string) => req(`/accounts/${platform}`, { method: "DELETE" }),

  leagues: (refresh = false) => req<League[]>(`/leagues${refresh ? "?refresh=1" : ""}`),
  setScoring: (key: string, s: ScoringProfile | null) => req<League>(`/leagues/${encodeURIComponent(key)}/scoring`, json("PUT", s)),
  setHidden: (key: string, hidden: boolean) => req(`/leagues/${encodeURIComponent(key)}/hidden`, json("PUT", { hidden })),
  roster: (key: string, week?: number) => req<Roster>(`/leagues/${encodeURIComponent(key)}/roster${week ? `?week=${week}` : ""}`),
  saveLineup: (key: string, change: LineupChange) => req<Roster>(`/leagues/${encodeURIComponent(key)}/lineup`, json("PUT", change)),
  matchup: (key: string, week?: number) => req<Matchup | null>(`/leagues/${encodeURIComponent(key)}/matchup${week ? `?week=${week}` : ""}`),
  standings: (key: string) => req<TeamSummary[]>(`/leagues/${encodeURIComponent(key)}/standings`),

  players: (sport: string) => req<{ updated: number; players: PlayerInfo[] }>(`/players/${sport}`),
  player: (sport: string, id: string, season?: string) => req<PlayerProfile>(`/players/${sport}/${encodeURIComponent(id)}${season ? `?season=${season}` : ""}`),

  rankings: (q: Record<string, string | number | undefined>) => {
    const qs = new URLSearchParams(Object.entries(q).filter(([, v]) => v !== undefined && v !== "").map(([k, v]) => [k, String(v)]));
    return req<RankingSet>(`/rankings?${qs}`);
  },
  trade: (b: { give: string[]; get: string[]; league?: string; source?: string; scoring?: ScoringProfile; type?: string }) => req<TradeResult>("/trade", json("POST", b)),
};
