import { serve } from "@hono/node-server";
import { serveStatic } from "@hono/node-server/serve-static";
import { Hono } from "hono";
import { compress } from "hono/compress";
import { readFileSync, existsSync } from "node:fs";
import { config } from "./config";
import { checkPassword, clearSession, isAuthed, issueSession, requireAuth } from "./auth";
import { cached, invalidate, MIN } from "./cache";
import { UpstreamError } from "./http";
import { playerDb, setPlayerDb } from "./players";
import { getVault, updateVault } from "./store";
import { playerCareer, playerSeasonLog, sportState } from "./stats";
import { AdapterError, type Adapter } from "./platforms/types";
import { scoringFor } from "./platforms/enrich";
import { sleeper, sleeperLink, sleeperLogin } from "./platforms/sleeper";
import { espn, espnLink } from "./platforms/espn";
import { yahoo, yahooAuthUrl, yahooCallback } from "./platforms/yahoo";
import { fantasyProsRankings } from "./rankings/fantasypros";
import { footballersRankings, saveImport, scoringLabel } from "./rankings/footballers";
import { analyzeTrade, rankingLookup } from "./rankings/trade";
import { dynastyValues } from "./values";
import { latestNews, newsFor } from "./news";
import { defenseRanks } from "./defense";
import { powerRankings, tradeFinder, waiverTargets } from "./leaguetools";
import { draftBoard, listDrafts } from "./drafts";
import { addSubscription, pushPublicKey, removeSubscription, sendPush, startLineupWatcher, type PushSub } from "./push";
import { demoAdapter, demoPlayers } from "./demo";
import type { Account, LeagueSummary, LineupChange, RankingType, ScoringProfile, Sport } from "../shared/types";

if (config.demo) {
  setPlayerDb("nfl", demoPlayers("nfl") as never);
  setPlayerDb("nba", demoPlayers("nba") as never);
}

const adapters: Record<string, Adapter> = { sleeper, espn, yahoo };
const adapterFor = (l: LeagueSummary): Adapter => (config.demo ? demoAdapter : adapters[l.platform]);

async function allLeagues(): Promise<LeagueSummary[]> {
  return cached("leagues:all", 2 * MIN, async () => {
    if (config.demo) return demoAdapter.leagues();
    const results = await Promise.allSettled(Object.values(adapters).filter((a) => a.connected()).map((a) => a.leagues()));
    results.forEach((r) => r.status === "rejected" && console.warn("[leagues]", (r.reason as Error).message));
    return results.flatMap((r) => (r.status === "fulfilled" ? r.value : []));
  });
}

async function leagueByKey(key: string): Promise<LeagueSummary> {
  const l = (await allLeagues()).find((x) => x.key === key);
  if (!l) throw new AdapterError("League not found (try refreshing your leagues)", 404);
  return l;
}

const app = new Hono();
app.use("*", compress());

app.onError((err, c) => {
  const status = err instanceof AdapterError || err instanceof UpstreamError ? err.status : 500;
  if (status >= 500) console.error(err);
  const code = status >= 400 && status < 600 ? status : 502;
  return c.json({ error: err.message }, code as 400);
});

// ---------------------------------------------------------------------------
// Session (the hub's own login)
// ---------------------------------------------------------------------------
app.get("/api/session", (c) => c.json({ authed: isAuthed(c), passwordRequired: !!config.appPassword, demo: config.demo }));
app.post("/api/login", async (c) => {
  const { password } = await c.req.json<{ password: string }>();
  if (!config.appPassword || checkPassword(password ?? "")) {
    issueSession(c);
    return c.json({ ok: true });
  }
  await new Promise((r) => setTimeout(r, 600)); // slow down guessing
  return c.json({ error: "Wrong password" }, 401);
});
app.post("/api/logout", (c) => {
  clearSession(c);
  return c.json({ ok: true });
});

const api = new Hono();
api.use("*", requireAuth);

// ---------------------------------------------------------------------------
// Accounts
// ---------------------------------------------------------------------------
api.get("/accounts", (c) => {
  const v = getVault();
  const fbImports = Object.values(v.footballers?.imports ?? {});
  const accounts: Account[] = [
    { platform: "sleeper", label: "Sleeper", connected: !!v.sleeper, detail: v.sleeper ? `${v.sleeper.username}${v.sleeper.token ? " · lineup changes on" : " · read-only (sign in to edit lineups)"}` : undefined },
    { platform: "espn", label: "ESPN Fantasy", connected: !!v.espn, detail: v.espn ? `${v.espn.leagues.length} league(s)` : undefined },
    { platform: "yahoo", label: "Yahoo Fantasy", connected: !!v.yahoo, detail: config.yahooClientId ? undefined : "Server needs YAHOO_CLIENT_ID / SECRET" },
    { platform: "fantasypros", label: "FantasyPros", connected: true, detail: v.fantasyPros?.apiKey || config.fantasyProsKey ? "API key saved" : "Public rankings pages (add an API key for faster, more reliable data)" },
    { platform: "footballers", label: "Fantasy Footballers", connected: fbImports.length > 0, detail: fbImports.length ? `${fbImports.length} ranking import(s)` : undefined },
  ];
  return c.json({ accounts, demo: config.demo });
});

api.post("/accounts/sleeper", async (c) => {
  const b = await c.req.json<{ username: string; password?: string; token?: string }>();
  if (!b.username?.trim()) throw new AdapterError("Enter your Sleeper username");
  let token = b.token?.trim() || undefined;
  if (b.password) token = (await sleeperLogin(b.username.trim(), b.password)).token;
  await sleeperLink(b.username.trim(), token);
  invalidate("leagues:");
  return c.json({ ok: true });
});

api.post("/accounts/espn", async (c) => {
  const b = await c.req.json<{ s2: string; swid: string; leagues?: { id: string; sport: Sport }[] }>();
  if (!b.s2 || !b.swid) throw new AdapterError("Both espn_s2 and SWID are needed");
  const leagues = await espnLink(b.s2, b.swid, (b.leagues ?? []).filter((l) => /^\d+$/.test(l.id)));
  invalidate("leagues:");
  return c.json({ ok: true, leagues });
});

api.get("/yahoo/connect", (c) => c.redirect(yahooAuthUrl()));
api.get("/yahoo/callback", async (c) => {
  const code = c.req.query("code");
  const state = c.req.query("state");
  if (!code || !state) return c.redirect("/accounts?yahoo=error");
  await yahooCallback(code, state);
  invalidate("leagues:");
  return c.redirect("/accounts?yahoo=ok");
});

api.post("/accounts/fantasypros", async (c) => {
  const { apiKey } = await c.req.json<{ apiKey: string }>();
  updateVault((v) => (v.fantasyPros = { apiKey: apiKey?.trim() || undefined }));
  invalidate("fp:");
  return c.json({ ok: true });
});

api.post("/accounts/footballers/import", async (c) => {
  const b = await c.req.json<{ type: RankingType; scoring: ScoringProfile; week: number | null; text: string }>();
  const week = b.type === "weekly" ? b.week || (await sportState("nfl")).week : null;
  const imp = saveImport(b.type, scoringLabel(b.scoring), week, b.text);
  return c.json({ ok: true, count: imp.rows.length });
});

api.delete("/accounts/:platform", (c) => {
  const p = c.req.param("platform");
  updateVault((v) => {
    if (p === "sleeper") delete v.sleeper;
    if (p === "espn") delete v.espn;
    if (p === "yahoo") delete v.yahoo;
    if (p === "fantasypros") delete v.fantasyPros;
    if (p === "footballers") delete v.footballers;
  });
  invalidate("");
  return c.json({ ok: true });
});

// ---------------------------------------------------------------------------
// Leagues
// ---------------------------------------------------------------------------
const withScoring = (l: LeagueSummary) => ({ ...l, scoring: scoringFor(l), hidden: getVault().hiddenLeagues.includes(l.key) });

api.get("/leagues", async (c) => {
  if (c.req.query("refresh")) invalidate("");
  return c.json((await allLeagues()).map(withScoring));
});

api.put("/leagues/:key/scoring", async (c) => {
  const key = c.req.param("key");
  const s = await c.req.json<ScoringProfile | null>();
  updateVault((v) => {
    if (s) v.scoring[key] = { base: s.base, superflex: !!s.superflex, tePremium: Number(s.tePremium) || 0 };
    else delete v.scoring[key];
  });
  return c.json(withScoring(await leagueByKey(key)));
});

api.put("/leagues/:key/hidden", async (c) => {
  const key = c.req.param("key");
  const { hidden } = await c.req.json<{ hidden: boolean }>();
  updateVault((v) => (v.hiddenLeagues = hidden ? [...new Set([...v.hiddenLeagues, key])] : v.hiddenLeagues.filter((k) => k !== key)));
  return c.json({ ok: true });
});

const weekOf = (c: { req: { query: (k: string) => string | undefined } }, l: LeagueSummary) => Number(c.req.query("week")) || l.currentWeek;

api.get("/leagues/:key/roster", async (c) => {
  const l = await leagueByKey(c.req.param("key"));
  return c.json(await adapterFor(l).roster(l, weekOf(c, l)));
});

api.put("/leagues/:key/lineup", async (c) => {
  const l = await leagueByKey(c.req.param("key"));
  const change = await c.req.json<LineupChange>();
  if (!Array.isArray(change.starters) || !change.week) throw new AdapterError("Bad lineup request");
  return c.json(await adapterFor(l).setLineup(l, change));
});

api.get("/leagues/:key/matchup", async (c) => {
  const l = await leagueByKey(c.req.param("key"));
  return c.json(await adapterFor(l).matchup(l, weekOf(c, l)));
});

api.get("/leagues/:key/standings", async (c) => {
  const l = await leagueByKey(c.req.param("key"));
  return c.json(await adapterFor(l).standings(l));
});

// ---------------------------------------------------------------------------
// Players
// ---------------------------------------------------------------------------
const sportParam = (s: string): Sport => (s === "nba" ? "nba" : "nfl");

api.get("/players/:sport", async (c) => {
  const db = await playerDb(sportParam(c.req.param("sport")));
  c.header("cache-control", "private, max-age=3600");
  return c.json({ updated: db.updated, players: db.list.filter((p) => p.team || p.searchRank !== null) });
});

api.get("/players/:sport/:id", async (c) => {
  const sport = sportParam(c.req.param("sport"));
  const id = c.req.param("id");
  const [db, state] = await Promise.all([playerDb(sport), sportState(sport)]);
  const info = db.byId.get(id) ?? null;
  const season = c.req.query("season") ?? state.season;
  const firstSeason = Math.max(Number(state.season) - Math.max(0, info?.exp ?? 3), Number(state.season) - 6);
  const seasons = Array.from({ length: Number(state.season) - firstSeason + 1 }, (_, i) => String(Number(state.season) - i));

  const leagues = (await allLeagues()).filter((l) => l.sport === sport);
  const [log, career, availability, news] = await Promise.all([
    playerSeasonLog(sport, id, season),
    playerCareer(sport, id, seasons),
    Promise.all(
      leagues.map(async (l) => {
        const owners = await adapterFor(l).ownership(l).catch(() => null);
        const owner = owners?.get(id) ?? null;
        return { leagueKey: l.key, league: l.name, platform: l.platform, owner, mine: !!owner && owner === l.myTeam?.name, known: !!owners };
      }),
    ),
    newsFor(sport, new Set([id])).catch(() => []),
  ]);
  const depth = info?.team
    ? db.list
        .filter((p) => p.team === info.team && p.depthPos && p.depthOrder && p.depthOrder <= 3)
        .map((p) => ({ id: p.id, name: p.name, pos: p.depthPos!, order: p.depthOrder!, age: p.age, injury: p.injury }))
    : [];
  return c.json({ info, season, currentWeek: state.week, log, career, availability, depth, news });
});

// ---------------------------------------------------------------------------
// Rankings + trade analyzer
// ---------------------------------------------------------------------------
async function scoringFromQuery(q: (k: string) => string | undefined): Promise<ScoringProfile> {
  const league = q("league");
  if (league) return scoringFor(await leagueByKey(league));
  const base = (q("scoring") ?? "PPR").toUpperCase();
  return {
    base: base === "STD" || base === "HALF" ? base : "PPR",
    superflex: q("superflex") === "1",
    tePremium: Number(q("tep") ?? 0) || 0,
  };
}

api.get("/rankings", async (c) => {
  const q = (k: string) => c.req.query(k);
  const scoring = await scoringFromQuery(q);
  const type = (q("type") ?? "weekly") as RankingType;
  const week = Number(q("week")) || undefined;
  const pos = (q("pos") ?? "ALL").toUpperCase();
  if (q("source") === "footballers") {
    const set = await footballersRankings(type, scoring, week ?? (await sportState("nfl")).week);
    if (!set) return c.json({ error: "No Fantasy Footballers rankings imported for this view yet" }, 404);
    if (pos !== "ALL") set.rows = set.rows.filter((r) => (pos === "FLEX" ? ["RB", "WR", "TE"].includes(r.pos) : r.pos === pos));
    return c.json(set);
  }
  return c.json(await fantasyProsRankings(type, scoring, pos, week));
});

api.post("/trade", async (c) => {
  const b = await c.req.json<{ give: string[]; get: string[]; league?: string; source?: "fantasypros" | "footballers" | "ktc"; scoring?: ScoringProfile; type?: RankingType }>();
  const league = b.league ? await leagueByKey(b.league) : null;
  const scoring = league ? scoringFor(league) : b.scoring ?? { base: "PPR", superflex: false, tePremium: 0 };
  const db = await playerDb("nfl");
  const ids = [...b.give, ...b.get];
  const useKtc = b.source === "ktc" || ids.some((id) => id.startsWith("pick:"));
  if (useKtc) {
    const vals = await dynastyValues();
    const byId = new Map(vals.map((v, i) => [v.id, { v, rank: i + 1 }]));
    const name = (id: string) => byId.get(id)?.v.name ?? db.byId.get(id)?.name ?? id.replace(/^pick:/, "");
    const lookup = (id: string) => {
      const hit = byId.get(id);
      return { rank: hit?.rank ?? null, value: hit ? (scoring.superflex ? hit.v.sf : hit.v.oneQb) : 0 };
    };
    return c.json(analyzeTrade(lookup, b.give, b.get, name, `KeepTradeCut ${scoring.superflex ? "superflex" : "1QB"} dynasty values`));
  }
  const type: RankingType = b.type ?? (league?.dynasty ? "dynasty" : "ros");
  let set = b.source === "footballers" ? await footballersRankings(type, scoring, null) : null;
  if (!set) set = await fantasyProsRankings(type, scoring, "ALL").catch(() => fantasyProsRankings("ros", scoring, "ALL"));
  const label = `${set.source === "footballers" ? "Fantasy Footballers" : "FantasyPros"} ${set.type.toUpperCase()}`;
  return c.json(analyzeTrade(rankingLookup(set), b.give, b.get, (id) => db.byId.get(id)?.name ?? id, label));
});

api.get("/values", async (c) => c.json(await dynastyValues()));

// ---------------------------------------------------------------------------
// League tools
// ---------------------------------------------------------------------------
api.get("/leagues/:key/waivers", async (c) => {
  const l = await leagueByKey(c.req.param("key"));
  return c.json(await waiverTargets(adapterFor(l), l, (c.req.query("pos") ?? "ALL").toUpperCase()));
});
api.get("/leagues/:key/power", async (c) => {
  const l = await leagueByKey(c.req.param("key"));
  return c.json(await powerRankings(adapterFor(l), l));
});
api.get("/leagues/:key/trade-finder", async (c) => {
  const l = await leagueByKey(c.req.param("key"));
  return c.json(await tradeFinder(adapterFor(l), l));
});

api.get("/defense", async (c) => c.json(await defenseRanks()));

api.get("/news", async (c) => {
  const sport = sportParam(c.req.query("sport") ?? "nfl");
  const ids = c.req.query("ids");
  return c.json(ids ? await newsFor(sport, new Set(ids.split(","))) : await latestNews(sport));
});

api.get("/drafts", async (c) => c.json(await listDrafts(await allLeagues())));
api.get("/drafts/:id", async (c) => c.json(await draftBoard(c.req.param("id"), await allLeagues())));

// ---------------------------------------------------------------------------
// Push notifications
// ---------------------------------------------------------------------------
api.get("/push/key", (c) => c.json({ key: pushPublicKey() }));
api.post("/push/subscribe", async (c) => {
  addSubscription(await c.req.json<PushSub>());
  return c.json({ ok: true });
});
api.post("/push/unsubscribe", async (c) => {
  removeSubscription((await c.req.json<{ endpoint: string }>()).endpoint);
  return c.json({ ok: true });
});
api.post("/push/test", async (c) => c.json({ sent: await sendPush("Fitz Fantasy", "Notifications are working 🏈") }));

startLineupWatcher(async () =>
  (await allLeagues()).filter((l) => !getVault().hiddenLeagues.includes(l.key)).map((l) => ({ league: l, roster: () => adapterFor(l).roster(l, l.currentWeek) })),
);

app.route("/api", api);

// ---------------------------------------------------------------------------
// Static client (production)
// ---------------------------------------------------------------------------
if (existsSync("dist/index.html")) {
  app.use("/assets/*", async (c, next) => {
    await next();
    c.header("cache-control", "public, max-age=31536000, immutable");
  });
  app.use("*", serveStatic({ root: "./dist" }));
  const index = readFileSync("dist/index.html", "utf8");
  app.get("*", (c) => c.html(index));
}

serve({ fetch: app.fetch, port: config.port }, (i) => console.log(`Fantasy hub API on http://localhost:${i.port}${config.demo ? " (demo mode)" : ""}`));

export { app };
