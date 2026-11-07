import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, type League as L } from "../api";
import { useLeague, useRoster } from "../hooks";
import { LineupEditor } from "../components/LineupEditor";
import { PlatformBadge, Skeleton, scoringLabel } from "../components/bits";
import { IconBack } from "../components/Icons";
import { ordinal } from "./Home";
import type { ScoringProfile } from "../../shared/types";

type Tab = "team" | "matchup" | "standings" | "settings";

export function League() {
  const { key = "" } = useParams();
  const { league, isLoading } = useLeague(decodeURIComponent(key));
  const [tab, setTab] = useState<Tab>("team");
  const [week, setWeek] = useState<number | null>(null);

  if (!league) {
    return (
      <div className="page" style={{ paddingTop: 24 }}>
        {isLoading ? <Skeleton h={80} n={5} /> : <div className="empty">League not found. <Link to="/">Back</Link></div>}
      </div>
    );
  }
  const wk = week ?? league.currentWeek;
  return (
    <>
      <header className="topbar">
        <Link to="/" className="iconbtn" aria-label="Back" style={{ color: "var(--text)" }}><IconBack /></Link>
        <div className="grow">
          <div className="row" style={{ gap: 6 }}>
            <PlatformBadge platform={league.platform} />
            <span className="small muted">{league.sport === "nfl" ? scoringLabel(league.scoring) : "Basketball"}</span>
          </div>
          <h1 className="ellipsis" style={{ fontSize: 22 }}>{league.name}</h1>
        </div>
      </header>
      <div className="page">
        <div className="tabs" style={{ marginTop: 0 }}>
          {(["team", "matchup", "standings", "settings"] as Tab[]).map((t) => (
            <button key={t} className={tab === t ? "on" : ""} onClick={() => setTab(t)}>{t}</button>
          ))}
        </div>
        {(tab === "team" || tab === "matchup") && (
          <div className="row" style={{ marginBottom: 12 }}>
            <button className="chip" onClick={() => setWeek(Math.max(1, wk - 1))} aria-label="Previous week">‹</button>
            <div className="grow" style={{ textAlign: "center", fontWeight: 800 }}>
              {league.platform === "espn" && league.sport === "nba" ? `Scoring period ${wk}` : `Week ${wk}`}
              {wk === league.currentWeek && <span className="muted small"> · current</span>}
            </div>
            <button className="chip" onClick={() => setWeek(wk + 1)} aria-label="Next week">›</button>
          </div>
        )}
        {tab === "team" && <TeamTab league={league} week={wk} />}
        {tab === "matchup" && <MatchupTab league={league} week={wk} />}
        {tab === "standings" && <StandingsTab league={league} />}
        {tab === "settings" && <SettingsTab league={league} />}
      </div>
    </>
  );
}

function TeamTab({ league, week }: { league: L; week: number }) {
  const r = useRoster(league.key, week === league.currentWeek ? undefined : week);
  if (r.error) return <div className="banner err">{(r.error as Error).message}</div>;
  if (!r.data) return <Skeleton h={60} n={9} />;
  return <LineupEditor league={league} roster={r.data} week={week} />;
}

function MatchupTab({ league, week }: { league: L; week: number }) {
  const q = useQuery({ queryKey: ["matchup", league.key, week], queryFn: () => api.matchup(league.key, week), staleTime: 30_000 });
  if (q.error) return <div className="banner err">{(q.error as Error).message}</div>;
  if (q.isLoading) return <Skeleton h={140} />;
  const m = q.data;
  if (!m) return <div className="empty">No matchup this week.</div>;
  const side = (s: NonNullable<typeof m.opponent>, mine: boolean) => (
    <div style={{ textAlign: mine ? "left" : "right", flex: 1, minWidth: 0 }}>
      <div className="ellipsis" style={{ fontWeight: 800 }}>{s.team.name}</div>
      <div className="small muted">{s.team.wins}-{s.team.losses}</div>
      <div className="score num" style={{ fontSize: 40 }}>{s.points.toFixed(2)}</div>
      {s.projected ? <div className="small muted">proj {s.projected.toFixed(1)}</div> : null}
    </div>
  );
  return (
    <div className="card">
      <div className="row" style={{ alignItems: "flex-start" }}>
        {side(m.me, true)}
        <div className="muted caps" style={{ paddingTop: 30 }}>vs</div>
        {m.opponent ? side(m.opponent, false) : <div className="grow muted" style={{ textAlign: "right" }}>Bye</div>}
      </div>
      {m.opponent && (
        <div className="meter" style={{ marginTop: 14 }}>
          <i style={{ width: `${(100 * m.me.points) / Math.max(1, m.me.points + m.opponent.points)}%`, background: "var(--accent)" }} />
          <i style={{ flex: 1, background: "var(--danger)", opacity: 0.6 }} />
        </div>
      )}
    </div>
  );
}

function StandingsTab({ league }: { league: L }) {
  const q = useQuery({ queryKey: ["standings", league.key], queryFn: () => api.standings(league.key), staleTime: 5 * 60_000 });
  if (q.error) return <div className="banner err">{(q.error as Error).message}</div>;
  if (!q.data) return <Skeleton h={44} n={10} />;
  return (
    <div className="card" style={{ padding: 6 }}>
      {q.data.map((t, i) => (
        <div key={t.id} className="row" style={{ padding: "10px 8px", borderBottom: i < q.data.length - 1 ? "1px solid var(--line)" : undefined, background: t.name === league.myTeam?.name ? "color-mix(in srgb, var(--accent) 10%, transparent)" : undefined, borderRadius: 8 }}>
          <span className="num muted" style={{ width: 34 }}>{ordinal(i + 1)}</span>
          <span className="grow ellipsis" style={{ fontWeight: 700 }}>{t.name}</span>
          <span className="num" style={{ width: 56, textAlign: "right" }}>{t.wins}-{t.losses}{t.ties ? `-${t.ties}` : ""}</span>
          <span className="num muted" style={{ width: 70, textAlign: "right" }}>{t.pointsFor.toFixed(1)}</span>
        </div>
      ))}
    </div>
  );
}

function SettingsTab({ league }: { league: L }) {
  const qc = useQueryClient();
  const [s, setS] = useState<ScoringProfile>(league.scoring);
  const [saved, setSaved] = useState(false);
  const save = async (next: ScoringProfile | null) => {
    await api.setScoring(league.key, next);
    await qc.invalidateQueries({ queryKey: ["leagues"] });
    qc.invalidateQueries({ queryKey: ["rankings"] });
    if (!next) setS(league.detectedScoring);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };
  return (
    <div>
      {league.sport === "nfl" && (
        <div className="card">
          <h3 style={{ fontSize: 16 }}>Rankings points system</h3>
          <p className="small muted">Used for FantasyPros rankings, projections and the lineup optimizer in this league. Detected from the league: <b>{scoringLabel(league.detectedScoring)}</b>.</p>
          <div className="chips" style={{ margin: "12px 0" }}>
            {(["STD", "HALF", "PPR"] as const).map((b) => (
              <button key={b} className={`chip ${s.base === b ? "on" : ""}`} onClick={() => setS({ ...s, base: b })}>{b === "STD" ? "Standard" : b === "HALF" ? "Half PPR" : "Full PPR"}</button>
            ))}
          </div>
          <div className="chips" style={{ marginBottom: 12 }}>
            <button className={`chip ${s.superflex ? "on" : ""}`} onClick={() => setS({ ...s, superflex: !s.superflex })}>Superflex</button>
            <button className={`chip ${s.tePremium > 0 ? "on" : ""}`} onClick={() => setS({ ...s, tePremium: s.tePremium > 0 ? 0 : 0.5 })}>TE Premium</button>
          </div>
          {s.tePremium > 0 && (
            <div className="field">
              <label>Extra points per TE reception</label>
              <select value={s.tePremium} onChange={(e) => setS({ ...s, tePremium: Number(e.target.value) })}>
                {[0.25, 0.5, 0.75, 1, 1.5].map((v) => <option key={v} value={v}>+{v}</option>)}
              </select>
            </div>
          )}
          <div className="row">
            <button className="btn primary" onClick={() => save(s)}>Save</button>
            <button className="btn ghost" onClick={() => save(null)}>Use detected</button>
            {saved && <span className="small" style={{ color: "var(--good)" }}>Saved</span>}
          </div>
        </div>
      )}
      <div className="card">
        <h3 style={{ fontSize: 16 }}>League</h3>
        <p className="small muted">{league.size} teams · season {league.season} · {league.slots.filter((x) => x !== "BN").join(", ")}</p>
        <button
          className="btn ghost"
          onClick={async () => {
            await api.setHidden(league.key, !league.hidden);
            qc.invalidateQueries({ queryKey: ["leagues"] });
          }}
        >
          {league.hidden ? "Show on My Teams" : "Hide from My Teams"}
        </button>
      </div>
    </div>
  );
}
