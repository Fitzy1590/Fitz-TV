/** Tiny in-memory TTL cache with in-flight de-duplication. */
const store = new Map<string, { at: number; ttl: number; value: Promise<unknown> }>();

export function cached<T>(key: string, ttlMs: number, load: () => Promise<T>): Promise<T> {
  const hit = store.get(key);
  if (hit && Date.now() - hit.at < hit.ttl) return hit.value as Promise<T>;
  const value = load();
  store.set(key, { at: Date.now(), ttl: ttlMs, value });
  value.catch(() => store.delete(key));
  return value;
}

export function invalidate(prefix: string): void {
  for (const k of store.keys()) if (k.startsWith(prefix)) store.delete(k);
}

export const MIN = 60_000;
export const HOUR = 60 * MIN;
