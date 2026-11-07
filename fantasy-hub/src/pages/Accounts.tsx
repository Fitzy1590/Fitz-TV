import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api";
import { THEMES, useTheme } from "../theme";
import { IconCheck, IconChevron } from "../components/Icons";
import type { Account, RankingType } from "../../shared/types";

export function Accounts({ onLogout, passwordRequired }: { onLogout: () => void; passwordRequired: boolean }) {
  const q = useQuery({ queryKey: ["accounts"], queryFn: api.accounts });
  const [theme, setTheme] = useTheme();
  const [params] = useSearchParams();
  const byId = (id: string) => q.data?.accounts.find((a) => a.platform === id);

  return (
    <>
      <header className="topbar">
        <h1 className="grow">Accounts</h1>
      </header>
      <div className="page">
        {params.get("yahoo") === "ok" && <div className="banner info" style={{ marginBottom: 10 }}>Yahoo linked ✅</div>}
        {params.get("yahoo") === "error" && <div className="banner err" style={{ marginBottom: 10 }}>Yahoo sign-in didn't complete.</div>}
        {q.data?.demo && <div className="banner warn" style={{ marginBottom: 10 }}>Demo mode: the server is showing sample leagues. Set DEMO=0 to use your real accounts.</div>}

        <div className="section-title caps" style={{ marginTop: 6 }}>Fantasy platforms</div>
        <Panel account={byId("sleeper")} fallback="Sleeper">
          <SleeperForm />
        </Panel>
        <Panel account={byId("espn")} fallback="ESPN Fantasy">
          <EspnForm />
        </Panel>
        <Panel account={byId("yahoo")} fallback="Yahoo Fantasy">
          <p className="small muted">Yahoo uses its official sign-in. You'll be sent to Yahoo and back.</p>
          <a className="btn primary" href="/api/yahoo/connect">Connect Yahoo</a>
        </Panel>

        <div className="section-title caps">Rankings & tools</div>
        <Panel account={byId("fantasypros")} fallback="FantasyPros">
          <FantasyProsForm />
        </Panel>
        <Panel account={byId("footballers")} fallback="Fantasy Footballers">
          <FootballersForm />
        </Panel>

        <div className="section-title caps">Theme</div>
        <div className="theme-grid">
          {THEMES.map((t) => (
            <button key={t.id} className={`theme-tile ${theme === t.id ? "on" : ""}`} onClick={() => setTheme(t.id)}>
              <div className="sw" style={{ background: t.swatch[0], border: "1px solid var(--line)" }}>
                <i style={{ background: t.swatch[1] }} />
              </div>
              <b className="small">{t.name}</b>
            </button>
          ))}
        </div>

        {passwordRequired && (
          <button className="btn ghost danger" style={{ width: "100%", marginTop: 24 }} onClick={async () => { await api.logout(); onLogout(); }}>
            Sign out of the hub
          </button>
        )}
      </div>
    </>
  );
}

function Panel({ account, fallback, children }: { account?: Account; fallback: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  return (
    <div className="card" style={{ marginBottom: 10 }}>
      <button className="row" style={{ width: "100%", textAlign: "left" }} onClick={() => setOpen(!open)}>
        <div className="grow">
          <b>{account?.label ?? fallback}</b>
          <div className="small muted">{account?.detail ?? (account?.connected ? "Connected" : "Not connected")}</div>
        </div>
        {account?.connected && <span className="pill good"><IconCheck /></span>}
        <IconChevron open={open} />
      </button>
      {open && (
        <div style={{ marginTop: 14 }}>
          {children}
          {account?.connected && account.platform !== "fantasypros" && (
            <button
              className="btn ghost danger"
              style={{ marginTop: 10 }}
              onClick={async () => {
                await api.unlink(account.platform);
                qc.invalidateQueries();
              }}
            >
              Unlink
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function useSubmit<T>(fn: (v: T) => Promise<unknown>) {
  const qc = useQueryClient();
  const [state, setState] = useState<{ busy: boolean; msg: string | null; err: boolean }>({ busy: false, msg: null, err: false });
  const submit = async (v: T, okMsg = "Saved") => {
    setState({ busy: true, msg: null, err: false });
    try {
      await fn(v);
      setState({ busy: false, msg: okMsg, err: false });
      qc.invalidateQueries();
    } catch (e) {
      setState({ busy: false, msg: (e as Error).message, err: true });
    }
  };
  const note = state.msg ? <div className={`banner ${state.err ? "err" : "info"}`} style={{ marginTop: 10 }}>{state.msg}</div> : null;
  return { ...state, submit, note };
}

function SleeperForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState("");
  const [adv, setAdv] = useState(false);
  const s = useSubmit(api.linkSleeper);
  return (
    <form onSubmit={(e) => { e.preventDefault(); s.submit({ username, password: password || undefined, token: token || undefined }, "Sleeper linked. Your leagues are syncing."); }}>
      <div className="field"><label>Username</label><input value={username} onChange={(e) => setUsername(e.target.value)} autoCapitalize="none" placeholder="fitzy1590" /></div>
      <div className="field">
        <label>Password (only needed to change lineups — it isn't stored)</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" />
      </div>
      <button type="button" className="small" style={{ color: "var(--accent)", marginBottom: 10 }} onClick={() => setAdv(!adv)}>Signed up with Google/Apple? Use a session token</button>
      {adv && (
        <div className="field">
          <label>Session token</label>
          <textarea rows={3} value={token} onChange={(e) => setToken(e.target.value)} placeholder="eyJ…" />
          <span className="small muted">On sleeper.com: open DevTools → Network → any “graphql” request → copy the <code>authorization</code> request header.</span>
        </div>
      )}
      <button className="btn primary" disabled={s.busy || !username}>{s.busy ? "Linking…" : "Link Sleeper"}</button>
      {s.note}
    </form>
  );
}

function EspnForm() {
  const [s2, setS2] = useState("");
  const [swid, setSwid] = useState("");
  const [ids, setIds] = useState("");
  const s = useSubmit(api.linkEspn);
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        const leagues = ids.split(/[\s,]+/).filter(Boolean).map((x) => {
          const [id, sport] = x.split(":");
          return { id, sport: (sport === "nba" ? "nba" : "nfl") as "nfl" | "nba" };
        });
        s.submit({ s2, swid, leagues }, "ESPN linked. Your leagues are syncing.");
      }}
    >
      <p className="small muted">
        ESPN has no official sign-in for apps. Sign in at fantasy.espn.com on a computer, then in DevTools → Application → Cookies copy <code>espn_s2</code> and <code>SWID</code>. They're saved encrypted on your server.
      </p>
      <div className="field"><label>espn_s2</label><textarea rows={3} value={s2} onChange={(e) => setS2(e.target.value)} /></div>
      <div className="field"><label>SWID</label><input value={swid} onChange={(e) => setSwid(e.target.value)} placeholder="{XXXXXXXX-XXXX-…}" /></div>
      <div className="field">
        <label>League IDs (optional — found automatically when possible)</label>
        <input value={ids} onChange={(e) => setIds(e.target.value)} placeholder="12345678, 87654321:nba" />
      </div>
      <button className="btn primary" disabled={s.busy || !s2 || !swid}>{s.busy ? "Linking…" : "Link ESPN"}</button>
      {s.note}
    </form>
  );
}

function FantasyProsForm() {
  const [key, setKey] = useState("");
  const s = useSubmit(api.linkFantasyPros);
  return (
    <form onSubmit={(e) => { e.preventDefault(); s.submit(key); }}>
      <p className="small muted">Weekly, rest-of-season, dynasty and draft ECR work without a key (read from FantasyPros' public rankings pages). A FantasyPros API key makes it faster and more reliable.</p>
      <div className="field"><label>API key (optional)</label><input value={key} onChange={(e) => setKey(e.target.value)} autoComplete="off" /></div>
      <button className="btn primary" disabled={s.busy}>Save</button>
      {s.note}
    </form>
  );
}

function FootballersForm() {
  const [type, setType] = useState<RankingType>("weekly");
  const [base, setBase] = useState<"STD" | "HALF" | "PPR">("HALF");
  const [sf, setSf] = useState(false);
  const [week, setWeek] = useState("");
  const [text, setText] = useState("");
  const s = useSubmit(api.importFootballers);
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        s.submit({ type, scoring: { base, superflex: sf, tePremium: 0 }, week: Number(week) || null, text }, "Rankings imported.");
      }}
    >
      <p className="small muted">
        The Ultimate dashboard has no public API, so bring rankings in by copying the rankings table from the dashboard (select the rows, copy) or uploading its CSV. They then show up under Rankings and power the trade analyzer.
      </p>
      <div className="chips" style={{ marginBottom: 10 }}>
        {(["weekly", "ros", "dynasty", "draft"] as RankingType[]).map((t) => (
          <button type="button" key={t} className={`chip ${type === t ? "on" : ""}`} onClick={() => setType(t)}>{t === "ros" ? "ROS" : t}</button>
        ))}
      </div>
      <div className="chips" style={{ marginBottom: 10 }}>
        {(["STD", "HALF", "PPR"] as const).map((b) => (
          <button type="button" key={b} className={`chip ${base === b ? "on" : ""}`} onClick={() => setBase(b)}>{b}</button>
        ))}
        <button type="button" className={`chip ${sf ? "on" : ""}`} onClick={() => setSf(!sf)}>Superflex</button>
      </div>
      {type === "weekly" && <div className="field"><label>Week</label><input inputMode="numeric" value={week} onChange={(e) => setWeek(e.target.value)} placeholder="current" /></div>}
      <div className="field">
        <label>Paste rankings or choose a CSV</label>
        <textarea rows={6} value={text} onChange={(e) => setText(e.target.value)} placeholder={"Rank,Player,Pos,Team,Tier\n1,Bijan Robinson,RB,ATL,1"} />
        <input type="file" accept=".csv,.tsv,.txt" onChange={async (e) => { const f = e.target.files?.[0]; if (f) setText(await f.text()); }} />
      </div>
      <button className="btn primary" disabled={s.busy || !text.trim()}>Import</button>
      {s.note}
    </form>
  );
}
