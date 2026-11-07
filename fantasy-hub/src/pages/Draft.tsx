import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api";
import { PlayerHead, Skeleton } from "../components/bits";
import { usePlayerSheet } from "../components/PlayerSheet";
import { SubHeader } from "./Tools";

export function Draft() {
  const [params, setParams] = useSearchParams();
  const id = params.get("id");
  const drafts = useQuery({ queryKey: ["drafts"], queryFn: api.drafts, staleTime: 60_000 });
  const [manual, setManual] = useState("");

  if (id) return <Board id={id} onBack={() => setParams({})} />;
  return (
    <>
      <SubHeader title="Draft assistant" />
      <div className="page">
        <p className="small muted" style={{ marginTop: 0 }}>Live best-available board for Sleeper drafts (league drafts and mocks). Dynasty and rookie drafts use KeepTradeCut values; redraft uses FantasyPros draft ECR.</p>
        {drafts.isLoading && <Skeleton h={60} n={3} />}
        {drafts.data?.map((d) => (
          <button key={d.draftId} className="card row" style={{ width: "100%", textAlign: "left", marginBottom: 8 }} onClick={() => setParams({ id: d.draftId })}>
            <div className="grow">
              <b>{d.name}</b>
              <div className="small muted">{d.type} · {d.start ? new Date(d.start).toLocaleDateString() : "not scheduled"}</div>
            </div>
            <span className={`pill ${d.status === "drafting" ? "good" : ""}`}>{d.status.replace("_", " ")}</span>
          </button>
        ))}
        {drafts.data && !drafts.data.length && <div className="empty">No Sleeper drafts found.</div>}
        <form className="row" style={{ marginTop: 14 }} onSubmit={(e) => { e.preventDefault(); if (manual.trim()) setParams({ id: manual.trim() }); }}>
          <input placeholder="Or paste a Sleeper draft ID" value={manual} onChange={(e) => setManual(e.target.value)} />
          <button className="btn primary">Open</button>
        </form>
      </div>
    </>
  );
}

function Board({ id, onBack }: { id: string; onBack: () => void }) {
  const q = useQuery({ queryKey: ["draft", id], queryFn: () => api.draft(id), refetchInterval: (query) => (query.state.data?.status === "drafting" ? 5000 : 60_000) });
  const [pos, setPos] = useState("ALL");
  const openPlayer = usePlayerSheet();
  const d = q.data;
  const avail = (d?.available ?? []).filter((p) => pos === "ALL" || p.pos === pos);
  const counts = (d?.myTeam ?? []).reduce<Record<string, number>>((m, p) => ((m[p.pos] = (m[p.pos] ?? 0) + 1), m), {});

  return (
    <>
      <header className="topbar">
        <button className="iconbtn" onClick={onBack} aria-label="Back">‹</button>
        <h1 className="grow" style={{ fontSize: 22 }}>Draft board</h1>
        {d?.status === "drafting" && <span className="pill good">LIVE</span>}
      </header>
      <div className="page">
        {q.isLoading && <Skeleton h={90} n={4} />}
        {q.error && <div className="banner err">{(q.error as Error).message}</div>}
        {d && (
          <>
            <div className="card row" style={{ marginBottom: 10 }}>
              <div className="grow">
                <div className="caps muted">Pick {d.current} · Round {Math.ceil(d.current / d.teams)}</div>
                <div className="score">{d.onClock === d.mySlot ? "You're on the clock!" : d.picksUntilMine != null ? `${d.picksUntilMine} pick${d.picksUntilMine === 1 ? "" : "s"} until you` : "Draft complete"}</div>
              </div>
              {d.nextMine && <div style={{ textAlign: "right" }}><div className="small muted">Your next</div><b className="num">#{d.nextMine}</b></div>}
            </div>

            {d.myTeam.length > 0 && (
              <div className="card" style={{ marginBottom: 10 }}>
                <div className="caps muted small" style={{ marginBottom: 6 }}>Your picks · {Object.entries(counts).map(([p, n]) => `${n} ${p}`).join(" · ")}</div>
                <div className="chips">{d.myTeam.map((p) => <span key={p.id} className="pill"><span className={`postext ${p.pos}`}>{p.pos}</span> {p.name}</span>)}</div>
              </div>
            )}

            {d.recent.length > 0 && (
              <div className="chips" style={{ marginBottom: 10 }}>
                {d.recent.map((p) => <span key={p.pick} className="pill">{p.round}.{String(((p.pick - 1) % d.teams) + 1).padStart(2, "0")} {p.name}</span>)}
              </div>
            )}

            <div className="chips" style={{ marginBottom: 8 }}>
              {["ALL", "QB", "RB", "WR", "TE"].map((p) => <button key={p} className={`chip ${pos === p ? "on" : ""}`} onClick={() => setPos(p)}>{p === "ALL" ? "Best available" : p}</button>)}
            </div>
            <div className="small muted" style={{ marginBottom: 4 }}>{d.source}</div>
            {avail.map((p, i) => (
              <div key={p.id}>
                {p.tier != null && p.tier !== avail[i - 1]?.tier && <div className="tier">TIER {p.tier}</div>}
                <button className="rank-row" onClick={() => openPlayer({ id: p.id, sport: "nfl", name: p.name })}>
                  <span className="rk num">{p.rank}</span>
                  <PlayerHead id={p.id} name={p.name} size={40} />
                  <div className="grow">
                    <div style={{ fontWeight: 700 }} className="ellipsis">{p.name}</div>
                    <div className="small muted"><span className={`postext ${p.pos}`} style={{ fontWeight: 800 }}>{p.pos}</span> · {p.team ?? "FA"}</div>
                  </div>
                  {p.value != null && <b className="num">{p.value.toLocaleString()}</b>}
                </button>
              </div>
            ))}
          </>
        )}
      </div>
    </>
  );
}
