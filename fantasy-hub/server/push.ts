import webpush from "web-push";
import { config } from "./config";
import { getVault, updateVault } from "./store";
import { lineupWarnings } from "../shared/lineup";
import type { LeagueSummary, Roster } from "../shared/types";

export interface PushSub {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

function vapid() {
  let v = getVault().push;
  if (!v?.publicKey) {
    const keys = webpush.generateVAPIDKeys();
    updateVault((x) => (x.push = { publicKey: keys.publicKey, privateKey: keys.privateKey, subs: x.push?.subs ?? [], sent: x.push?.sent ?? [] }));
    v = getVault().push!;
  }
  webpush.setVapidDetails(config.publicUrl.startsWith("https") ? config.publicUrl : "mailto:fantasy-hub@localhost", v.publicKey, v.privateKey);
  return v;
}

export const pushPublicKey = () => vapid().publicKey;

export function addSubscription(sub: PushSub) {
  vapid();
  updateVault((v) => {
    v.push!.subs = [...v.push!.subs.filter((s) => s.endpoint !== sub.endpoint), sub];
  });
}

export function removeSubscription(endpoint: string) {
  updateVault((v) => v.push && (v.push.subs = v.push.subs.filter((s) => s.endpoint !== endpoint)));
}

export async function sendPush(title: string, body: string, url = "/") {
  const v = vapid();
  const dead: string[] = [];
  await Promise.all(
    v.subs.map((s) =>
      webpush.sendNotification(s, JSON.stringify({ title, body, url })).catch((e: { statusCode?: number }) => {
        if (e.statusCode === 404 || e.statusCode === 410) dead.push(s.endpoint);
      }),
    ),
  );
  if (dead.length) updateVault((x) => x.push && (x.push.subs = x.push.subs.filter((s) => !dead.includes(s.endpoint))));
  return v.subs.length - dead.length;
}

/**
 * Background lineup watcher: every 20 minutes, check every league's current lineup and
 * notify once per new problem (starter OUT / on bye / empty slot).
 */
export function startLineupWatcher(load: () => Promise<{ league: LeagueSummary; roster: () => Promise<Roster> }[]>) {
  const tick = async () => {
    if (!getVault().push?.subs.length) return;
    try {
      const leagues = await load();
      for (const { league, roster } of leagues) {
        const r = await roster().catch(() => null);
        if (!r) continue;
        for (const w of lineupWarnings(r.starters)) {
          const key = `${league.key}|${r.week}|${w}`;
          if (getVault().push?.sent.includes(key)) continue;
          await sendPush(`Lineup alert · ${league.name}`, w, `/league/${encodeURIComponent(league.key)}`);
          updateVault((v) => v.push && (v.push.sent = [...v.push.sent, key].slice(-500)));
        }
      }
    } catch (e) {
      console.warn("[push] watcher failed:", (e as Error).message);
    }
  };
  setTimeout(tick, 30_000).unref?.();
  setInterval(tick, 20 * 60_000).unref?.();
}
