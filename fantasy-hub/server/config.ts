import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

// Minimal .env loader (avoids a dependency).
const envFile = resolve(process.cwd(), ".env");
if (existsSync(envFile)) {
  for (const line of readFileSync(envFile, "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^["']|["']$/g, "");
  }
}

const env = process.env;
export const config = {
  port: Number(env.PORT ?? 8787),
  publicUrl: (env.PUBLIC_URL ?? "http://localhost:5173").replace(/\/$/, ""),
  appPassword: env.APP_PASSWORD ?? "",
  appSecret: env.APP_SECRET ?? "",
  dataDir: resolve(process.cwd(), env.DATA_DIR ?? "data"),
  yahooClientId: env.YAHOO_CLIENT_ID ?? "",
  yahooClientSecret: env.YAHOO_CLIENT_SECRET ?? "",
  fantasyProsKey: env.FANTASYPROS_API_KEY ?? "",
  demo: env.DEMO === "1",
  production: env.NODE_ENV === "production",
};

if (!config.appSecret || config.appSecret.length < 16) {
  if (config.production) throw new Error("APP_SECRET must be set (at least 16 characters) in production");
  config.appSecret = "dev-only-secret-change-me-please";
  console.warn("[config] APP_SECRET not set; using an insecure development secret");
}
