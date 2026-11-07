import { useState } from "react";
import { api } from "../api";

export function Login({ onDone }: { onDone: () => void }) {
  const [pw, setPw] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <div style={{ minHeight: "100%", display: "grid", placeItems: "center", padding: 24 }}>
      <form
        className="card"
        style={{ width: "100%", maxWidth: 360, padding: 24 }}
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setErr(null);
          try {
            await api.login(pw);
            onDone();
          } catch (x) {
            setErr((x as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <h1 style={{ fontFamily: "var(--display)", fontSize: 32 }}>Fitz Fantasy</h1>
        <p className="muted" style={{ marginTop: 4 }}>All your leagues, one lineup screen.</p>
        <div className="field" style={{ marginTop: 18 }}>
          <label htmlFor="pw">Password</label>
          <input id="pw" type="password" autoComplete="current-password" value={pw} onChange={(e) => setPw(e.target.value)} autoFocus />
        </div>
        {err && <div className="banner err" style={{ marginBottom: 12 }}>{err}</div>}
        <button className="btn primary" style={{ width: "100%" }} disabled={busy || !pw}>{busy ? "Signing in…" : "Sign in"}</button>
      </form>
    </div>
  );
}
