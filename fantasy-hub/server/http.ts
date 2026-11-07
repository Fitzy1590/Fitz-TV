export class UpstreamError extends Error {
  constructor(message: string, public status: number, public body?: string) {
    super(message);
  }
}

export async function fetchText(url: string, init: RequestInit = {}, timeoutMs = 15_000): Promise<string> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      ...init,
      signal: ctrl.signal,
      headers: { "user-agent": "FitzFantasyHub/0.1", accept: "application/json, text/html;q=0.9", ...(init.headers ?? {}) },
    });
    const text = await res.text();
    if (!res.ok) throw new UpstreamError(`${new URL(url).host} responded ${res.status}`, res.status, text.slice(0, 500));
    return text;
  } catch (e) {
    if (e instanceof UpstreamError) throw e;
    throw new UpstreamError(`Could not reach ${new URL(url).host}: ${(e as Error).message}`, 502);
  } finally {
    clearTimeout(t);
  }
}

export async function fetchJson<T>(url: string, init: RequestInit = {}, timeoutMs?: number): Promise<T> {
  const text = await fetchText(url, init, timeoutMs);
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new UpstreamError(`${new URL(url).host} returned invalid JSON`, 502, text.slice(0, 200));
  }
}
