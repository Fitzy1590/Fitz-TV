import { useQueries, useQuery } from "@tanstack/react-query";
import { get, set } from "idb-keyval";
import { useEffect, useState } from "react";
import { api, type League, type PlayerInfo } from "./api";

/** localStorage snapshot so screens paint instantly with the last known data. */
function snapshot<T>(key: string): T | undefined {
  try {
    const raw = localStorage.getItem(`snap:${key}`);
    return raw ? (JSON.parse(raw) as T) : undefined;
  } catch {
    return undefined;
  }
}
function save(key: string, value: unknown) {
  try {
    localStorage.setItem(`snap:${key}`, JSON.stringify(value));
  } catch {
    /* storage full or blocked */
  }
}

export function useLeagues() {
  const q = useQuery({
    queryKey: ["leagues"],
    queryFn: async () => {
      const ls = await api.leagues();
      save("leagues", ls);
      return ls;
    },
    placeholderData: () => snapshot<League[]>("leagues"),
    staleTime: 60_000,
  });
  return q;
}

export function useLeague(key: string | undefined) {
  const q = useLeagues();
  return { ...q, league: q.data?.find((l) => l.key === key) };
}

export function useRoster(key: string | undefined, week?: number) {
  return useQuery({
    queryKey: ["roster", key, week ?? "current"],
    queryFn: async () => {
      const r = await api.roster(key!, week);
      if (!week) save(`roster:${key}`, r);
      return r;
    },
    enabled: !!key,
    placeholderData: () => (week ? undefined : snapshot(`roster:${key}`)),
    staleTime: 30_000,
  });
}

/** Player index, cached in IndexedDB and refreshed in the background once a day. */
const memo = new Map<string, Promise<PlayerInfo[]>>();
export function loadPlayers(sport: string): Promise<PlayerInfo[]> {
  let p = memo.get(sport);
  if (!p) {
    p = (async () => {
      const cachedIdx = await get<{ at: number; players: PlayerInfo[] }>(`players:${sport}`).catch(() => undefined);
      const fresh = async () => {
        const r = await api.players(sport);
        await set(`players:${sport}`, { at: Date.now(), players: r.players }).catch(() => undefined);
        return r.players;
      };
      if (cachedIdx?.players?.length) {
        if (Date.now() - cachedIdx.at > 86_400_000) fresh().catch(() => undefined);
        return cachedIdx.players;
      }
      return fresh();
    })();
    memo.set(sport, p);
    p.catch(() => memo.delete(sport));
  }
  return p;
}

export function usePlayers(sport: string) {
  const [players, setPlayers] = useState<PlayerInfo[] | null>(null);
  useEffect(() => {
    let live = true;
    loadPlayers(sport).then((p) => live && setPlayers(p)).catch(() => live && setPlayers([]));
    return () => {
      live = false;
    };
  }, [sport]);
  return players;
}

/** Defense-vs-position ranks (32 = allows the most points = best matchup). */
export function useDefense(enabled = true) {
  return useQuery({ queryKey: ["defense"], queryFn: api.defense, staleTime: 6 * 3600_000, enabled, retry: false });
}

/** Current rosters for a set of leagues (shares cache with the lineup screens). */
export function useRosters(leagues: League[]) {
  return useQueries({
    queries: leagues.map((l) => ({ queryKey: ["roster", l.key, "current"], queryFn: () => api.roster(l.key), staleTime: 60_000, retry: 1 })),
  });
}

export function matchupTone(rank: number | undefined): "good" | "ok" | "bad" | null {
  if (!rank) return null;
  return rank >= 22 ? "good" : rank >= 11 ? "ok" : "bad";
}
