import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, type League } from "../api";
import { useLeagues } from "../hooks";
import { Opp, PlayerHead, Skeleton, scoringLabel } from "../components/bits";
import { usePlayerSheet } from "../components/PlayerSheet";
import { SubHeader } from "./Tools";
import { ordinal } from "./Home";

/** League picker shared by the per-league tools (remembers the choice in the URL). */
function useLeaguePick(filter: (l: League) => boolean = () => true) {
  const { data } = useLeagues();
  const leagues = (data ?? []).filter((l) => !l.hidden && filter(l));
  const [params, setParams] = useSearchParams();
  const key = params.get("league") ?? leagues[0]?.key ?? "";
  const league = leagues.find((l) => l.key === key) ?? leagues[0];
  const picker = (
    <select value={league?.key ?? ""} onChange={(e) => setParams({ league: e.target.value }, { replace: true })} aria-label="League" style={{ marginBottom: 12 }}>
      {leagues.map((l) => <option key={l.key} value={l.key}>{l.name} · {l.platform.toUpperCase()}</option>)}
    </select>
  );
  return { league, picker, loaded: !!data };
}

const nflOnly = (l: League) => l.sport === "nfl";

// ---------------------------------------------------------------------------
export function Waivers() {
  const { league, picker, loaded } = useLeaguePick(nflOnly);
  const [pos, setPos] = useState("ALL");
  const openPlayer = usePlayerSheet();
  const q = useQuery({ queryKey: ["waivers", league?.key, pos], queryFn: () => api.waivers(league!.key, pos), enabled: !!league, staleTime: 15 * 60_000 });
  return (
    <>
      <SubHeader title="Waiver targets" />
      <div className="page">
        {picker}
        {loaded && !league && <div className="empty">Link a football league first.</div>}
        <div className="chips" style={{ marginBottom: 10 }}>
          {["ALL", "QB", "RB", "WR", "TE", "K", "DEF"].map((p) => <button key={p} className={`chip ${pos === p ? "on" : ""}`} onClick={() => setPos(p)}>{p === "ALL" ? "All" : p}</button>)}
        </div>
        {league && <div className="small muted" style={{ marginBottom: 6 }}>Unrostered players by {q.data?.source ?? "FantasyPros ROS"} · {scoringLabel(league.scoring)}</div>}
        {q.isLoading && <Skeleton h={54} n={8} />}
        {q.error && <div className="banner err">{(q.error as Error).message}</div>}
        {q.data?.rows.map((r) => (
          <button key={r.name} className="rank-row" onClick={() => r.id && openPlayer({ id: r.id, sport: "nfl", name: r.name })}>
            <span className="rk num">{r.rank}</span>
            <PlayerHead id={r.id ?? r.name} name={r.name} size={40} />
            <div className="grow">
              <div style={{ fontWeight: 700 }} className="ellipsis">{r.name}</div>
              <div className="small muted"><span className={`postext ${r.pos}`} style={{ fontWeight: 800 }}>{r.posRank ?? r.pos}</span> · {r.team ?? "FA"} {r.opponent && <>· <Opp opp={r.opponent} pos={r.pos} /></>}</div>
            </div>
            {r.proj != null && <div style={{ textAlign: "right" }}><b className="num">{r.proj.toFixed(1)}</b><div className="small muted">wk proj</div></div>}
          </button>
        ))}
        {q.data && !q.data.rows.length && <div className="empty">No ranked free agents found.</div>}
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
export function Power() {
  const { league, picker } = useLeaguePick(nflOnly);
  const q = useQuery({ queryKey: ["power", league?.key], queryFn: () => api.power(league!.key), enabled: !!league, staleTime: 30 * 60_000 });
  const max = Math.max(1, ...(q.data?.rows.map((r) => r.starters) ?? [1]));
  return (
    <>
      <SubHeader title="Power rankings" />
      <div className="page">
        {picker}
        {q.data && <div className="small muted" style={{ marginBottom: 8 }}>Roster strength from {q.data.source}{league?.dynasty ? "" : ", blended with record"}.</div>}
        {q.isLoading && <Skeleton h={70} n={8} />}
        {q.error && <div className="banner err">{(q.error as Error).message}</div>}
        {q.data?.rows.map((r, i) => (
          <div key={r.team} className="card" style={{ marginBottom: 8, border: r.mine ? "1.5px solid var(--accent)" : undefined }}>
            <div className="row">
              <span className="score num" style={{ width: 44 }}>{ordinal(i + 1)}</span>
              <div className="grow" style={{ minWidth: 0 }}>
                <b className="ellipsis" style={{ display: "block" }}>{r.team}</b>
                <div className="small muted ellipsis">{r.record && `${r.record} · `}{r.best.map((b) => b.name).join(", ")}</div>
              </div>
              <span className="prob">{r.score}</span>
            </div>
            <div className="bar" style={{ marginTop: 8 }}><i style={{ width: `${(100 * r.starters) / max}%` }} /></div>
            <div className="row small muted" style={{ marginTop: 6, gap: 12 }}>
              {["QB", "RB", "WR", "TE"].map((p) => <span key={p}><span className={`postext ${p}`} style={{ fontWeight: 800 }}>{p}</span> {Math.round((r.byPos[p] ?? 0) / 100) / 10}k</span>)}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
export function TradeFinder() {
  const { league, picker } = useLeaguePick(nflOnly);
  const q = useQuery({ queryKey: ["trade-finder", league?.key], queryFn: () => api.tradeFinder(league!.key), enabled: !!league, staleTime: 30 * 60_000 });
  const openPlayer = usePlayerSheet();
  return (
    <>
      <SubHeader title="Trade finder" />
      <div className="page">
        {picker}
        {q.data && (
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="row small">
              <span className="grow"><span className="muted">You need: </span><b>{q.data.needs.join(", ") || "nothing major"}</b></span>
              <span><span className="muted">Surplus: </span><b>{q.data.surplus.join(", ") || "none"}</b></span>
            </div>
            <div className="small muted" style={{ marginTop: 6 }}>Values: {q.data.source}</div>
          </div>
        )}
        {q.isLoading && <Skeleton h={110} n={4} />}
        {q.error && <div className="banner err">{(q.error as Error).message}</div>}
        {q.data && !q.data.ideas.length && <div className="empty">No balanced trades found right now. Your roster may already be well built, or other teams are thin where you need help.</div>}
        {q.data?.ideas.map((t, i) => (
          <div key={i} className="card" style={{ marginBottom: 10 }}>
            <div className="row" style={{ marginBottom: 8 }}>
              <b className="grow ellipsis">with {t.partner}</b>
              <span className={`pill ${Math.abs(t.fairness) <= 5 ? "good" : ""}`}>{t.fairness >= 0 ? "+" : ""}{t.fairness}% value</span>
            </div>
            <div className="trade-cols">
              {[{ label: "You give", list: t.give }, { label: "You get", list: t.get }].map((side) => (
                <div key={side.label}>
                  <div className="caps muted small" style={{ marginBottom: 6 }}>{side.label}</div>
                  {side.list.map((p) => (
                    <button key={p.id} className="row" style={{ padding: "3px 0", width: "100%", textAlign: "left" }} onClick={() => openPlayer({ id: p.id, sport: "nfl", name: p.name })}>
                      <PlayerHead id={p.id} name={p.name} size={30} />
                      <span className="grow ellipsis small" style={{ fontWeight: 700 }}>{p.name}</span>
                      <span className={`postext ${p.pos} small`} style={{ fontWeight: 800 }}>{p.pos}</span>
                    </button>
                  ))}
                </div>
              ))}
            </div>
            <div className="small muted" style={{ marginTop: 8 }}>{t.why}</div>
          </div>
        ))}
      </div>
    </>
  );
}
