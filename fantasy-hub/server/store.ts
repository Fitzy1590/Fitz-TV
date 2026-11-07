import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { config } from "./config";
import type { ScoringProfile } from "../shared/types";

/** Everything the hub remembers. Saved to disk encrypted with APP_SECRET (AES-256-GCM). */
export interface Vault {
  sleeper?: { username: string; userId: string; token?: string };
  espn?: { s2: string; swid: string; leagues: { id: string; sport: "nfl" | "nba"; season?: string }[] };
  yahoo?: { accessToken: string; refreshToken: string; expiresAt: number };
  footballers?: { cookie?: string; imports: Record<string, FootballersImport> };
  fantasyPros?: { apiKey?: string };
  /** Points system override per league key. */
  scoring: Record<string, ScoringProfile>;
  hiddenLeagues: string[];
  push?: { publicKey: string; privateKey: string; subs: { endpoint: string; keys: { p256dh: string; auth: string } }[]; sent: string[] };
}

export interface FootballersImport {
  type: "weekly" | "ros" | "draft" | "dynasty";
  scoring: string;
  week: number | null;
  importedAt: string;
  rows: { name: string; pos: string; team: string | null; rank: number; tier: number | null }[];
}

const file = join(config.dataDir, "vault.bin");
const key = createHash("sha256").update(`vault:${config.appSecret}`).digest();
let vault: Vault | null = null;

function load(): Vault {
  if (vault) return vault;
  vault = { scoring: {}, hiddenLeagues: [] };
  if (existsSync(file)) {
    try {
      const buf = readFileSync(file);
      const iv = buf.subarray(0, 12);
      const tag = buf.subarray(12, 28);
      const decipher = createDecipheriv("aes-256-gcm", key, iv);
      decipher.setAuthTag(tag);
      const json = Buffer.concat([decipher.update(buf.subarray(28)), decipher.final()]).toString("utf8");
      vault = { ...vault, ...(JSON.parse(json) as Vault) };
    } catch (e) {
      console.error("[store] could not decrypt vault (APP_SECRET changed?)", (e as Error).message);
    }
  }
  return vault;
}

export function getVault(): Vault {
  return load();
}

export function updateVault(mut: (v: Vault) => void): Vault {
  const v = load();
  mut(v);
  mkdirSync(config.dataDir, { recursive: true });
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const body = Buffer.concat([cipher.update(JSON.stringify(v), "utf8"), cipher.final()]);
  const tmp = `${file}.tmp`;
  writeFileSync(tmp, Buffer.concat([iv, cipher.getAuthTag(), body]), { mode: 0o600 });
  renameSync(tmp, file);
  return v;
}
