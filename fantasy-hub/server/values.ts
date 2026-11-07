import { cached, HOUR } from "./cache";
import { config } from "./config";
import { fetchText } from "./http";
import { matchByName, normalizeTeam, playerDb } from "./players";

/** Dynasty trade values from KeepTradeCut (crowd-sourced), including rookie draft picks. */
export interface DynastyValue {
  id: string; // Sleeper id, or `pick:<name>` for draft picks
  name: string;
  pos: string; // QB RB WR TE PICK
  team: string | null;
  age: number | null;
  sf: number;
  oneQb: number;
}

interface KtcPlayer {
  playerName: string;
  position: string;
  team?: string;
  age?: number;
  rookie?: boolean;
  oneQBValues?: { value?: number };
  superflexValues?: { value?: number };
  value?: number;
}

function extract(html: string): KtcPlayer[] | null {
  // KTC moved the data from `var playersArray = [...]` to a JSON script tag in Sept 2026; support both.
  const tag = html.match(/<script[^>]*id=["']ktc-players["'][^>]*>([\s\S]*?)<\/script>/);
  if (tag) {
    try {
      const arr = JSON.parse(tag[1].trim());
      if (Array.isArray(arr)) return arr;
    } catch {
      /* fall through */
    }
  }
  const inline = html.match(/var\s+playersArray\s*=\s*(\[[\s\S]*?\]);/);
  return inline ? JSON.parse(inline[1]) : null;
}

export function dynastyValues(): Promise<DynastyValue[]> {
  if (config.demo) return demoValues();
  return cached("ktc", 12 * HOUR, async () => {
    const db = await playerDb("nfl");
    const seen = new Set<string>();
    const out: DynastyValue[] = [];
    for (let page = 0; page < 10; page++) {
      const html = await fetchText(`https://keeptradecut.com/dynasty-rankings?page=${page}&filters=QB|WR|RB|TE|RDP&format=0`, {
        headers: { referer: "https://keeptradecut.com/", "user-agent": "Mozilla/5.0 (FitzFantasyHub)" },
      }).catch((e) => {
        if (page === 0) throw e;
        return "";
      });
      const players = html ? extract(html) : null;
      if (!players?.length) break;
      let added = 0;
      for (const p of players) {
        const isPick = p.position === "RDP";
        const team = p.team && p.team !== "FA" && p.team !== "RDP" ? normalizeTeam(p.team) : null;
        const match = isPick ? null : matchByName(db, p.playerName, team, p.position);
        const id = isPick ? `pick:${p.playerName}` : match?.id ?? `ktc:${p.playerName}`;
        if (seen.has(id)) continue;
        seen.add(id);
        added++;
        out.push({
          id,
          name: p.playerName,
          pos: isPick ? "PICK" : p.position,
          team,
          age: p.age ?? null,
          sf: p.superflexValues?.value ?? p.value ?? 0,
          oneQb: p.oneQBValues?.value ?? p.value ?? 0,
        });
      }
      if (!added) break;
    }
    if (!out.length) throw new Error("KeepTradeCut returned no values");
    return out.sort((a, b) => b.sf - a.sf);
  });
}

async function demoValues(): Promise<DynastyValue[]> {
  const { demoDynastyValues } = await import("./demo");
  return demoDynastyValues();
}
