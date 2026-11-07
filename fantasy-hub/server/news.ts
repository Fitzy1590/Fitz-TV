import { cached, MIN } from "./cache";
import { config } from "./config";
import { fetchJson } from "./http";
import { playerDb } from "./players";
import type { Sport } from "../shared/types";

export interface NewsItem {
  id: string;
  headline: string;
  description: string | null;
  published: string;
  url: string | null;
  image: string | null;
  /** Canonical (Sleeper) ids of players mentioned. */
  players: { id: string; name: string }[];
}

interface EspnArticle {
  id: number;
  headline: string;
  description?: string | null;
  published?: string | null;
  lastModified: string;
  links?: { web?: { href?: string | null } | null } | null;
  images?: { url?: string }[];
  categories?: { type: string; athleteId?: number; description?: string | null }[];
}

const PATH: Record<Sport, string> = { nfl: "football/nfl", nba: "basketball/nba" };

/** ESPN's public news feed, with athletes mapped to Sleeper ids. */
export function latestNews(sport: Sport): Promise<NewsItem[]> {
  if (config.demo) return Promise.resolve(demoNews());
  return cached(`news:${sport}`, 10 * MIN, async () => {
    const [db, r] = await Promise.all([
      playerDb(sport),
      fetchJson<{ articles: EspnArticle[] }>(`https://site.api.espn.com/apis/site/v2/sports/${PATH[sport]}/news?limit=100`).catch(() => ({ articles: [] })),
    ]);
    return (r.articles ?? []).map((a) => ({
      id: String(a.id),
      headline: a.headline,
      description: a.description ?? null,
      published: a.published ?? a.lastModified,
      url: a.links?.web?.href ?? null,
      image: a.images?.[0]?.url ?? null,
      players: (a.categories ?? [])
        .filter((c) => c.type === "athlete" && c.athleteId)
        .map((c) => ({ id: db.byEspn.get(String(c.athleteId)) ?? `espn:${c.athleteId}`, name: c.description ?? "" })),
    }));
  });
}

export async function newsFor(sport: Sport, ids: Set<string>): Promise<NewsItem[]> {
  return (await latestNews(sport)).filter((n) => n.players.some((p) => ids.has(p.id)));
}

function demoNews(): NewsItem[] {
  const now = Date.now();
  return [
    { id: "d1", headline: "Bijan Robinson handles full workload in practice", description: "The Falcons' lead back logged a full session Thursday and is set for a heavy role against Cincinnati.", published: new Date(now - 3600e3).toISOString(), url: null, image: null, players: [{ id: "9509", name: "Bijan Robinson" }] },
    { id: "d2", headline: "Christian McCaffrey questionable with Achilles soreness", description: "McCaffrey was limited all week; the 49ers expect a game-time decision.", published: new Date(now - 7200e3).toISOString(), url: null, image: null, players: [{ id: "4034", name: "Christian McCaffrey" }] },
    { id: "d3", headline: "Tyreek Hill ruled out for Week 9", description: "Hill will miss his second straight game with a wrist injury.", published: new Date(now - 10800e3).toISOString(), url: null, image: null, players: [{ id: "3321", name: "Tyreek Hill" }] },
    { id: "d4", headline: "Michael Penix Jr. throws for 300 in Falcons' win", description: "Penix connected with Drake London eight times for 112 yards.", published: new Date(now - 86400e3).toISOString(), url: null, image: null, players: [{ id: "d-penix", name: "Michael Penix Jr." }, { id: "d-london", name: "Drake London" }] },
  ];
}
