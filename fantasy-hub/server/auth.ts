import { createHmac, timingSafeEqual } from "node:crypto";
import type { MiddlewareHandler } from "hono";
import { getCookie, setCookie, deleteCookie } from "hono/cookie";
import { config } from "./config";

const COOKIE = "fh_session";
const MAX_AGE = 60 * 60 * 24 * 90; // 90 days

function sign(payload: string): string {
  return createHmac("sha256", config.appSecret).update(payload).digest("base64url");
}

export function issueSession(c: Parameters<MiddlewareHandler>[0]): void {
  const exp = Math.floor(Date.now() / 1000) + MAX_AGE;
  const payload = `v1.${exp}`;
  setCookie(c, COOKIE, `${payload}.${sign(payload)}`, {
    httpOnly: true,
    sameSite: "Lax",
    secure: config.publicUrl.startsWith("https"),
    path: "/",
    maxAge: MAX_AGE,
  });
}

export function clearSession(c: Parameters<MiddlewareHandler>[0]): void {
  deleteCookie(c, COOKIE, { path: "/" });
}

export function isAuthed(c: Parameters<MiddlewareHandler>[0]): boolean {
  if (!config.appPassword) return true; // no password configured: local single-user mode
  const raw = getCookie(c, COOKIE);
  if (!raw) return false;
  const i = raw.lastIndexOf(".");
  const payload = raw.slice(0, i);
  const sig = raw.slice(i + 1);
  const expected = sign(payload);
  if (sig.length !== expected.length || !timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return false;
  const exp = Number(payload.split(".")[1]);
  return exp > Date.now() / 1000;
}

export function checkPassword(given: string): boolean {
  const a = Buffer.from(sign(`pw:${given}`));
  const b = Buffer.from(sign(`pw:${config.appPassword}`));
  return a.length === b.length && timingSafeEqual(a, b);
}

export const requireAuth: MiddlewareHandler = async (c, next) => {
  if (!isAuthed(c)) return c.json({ error: "Not signed in" }, 401);
  await next();
};
