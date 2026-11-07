import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { api, type PlayerInfo } from "../api";
import { useDefense, useLeagues, usePlayers, useRosters } from "../hooks";
import { Injury, Opp, PlayerHead, scoringLabel } from "../components/bits";
import { SubHeader } from "./Tools";

/** Compare 2-4 players on weekly ECR, projection, matchup and injury status. */
export function StartSit() {
  const { data } = useLeagues();
  const nfl = (data ?? []).filter((l) => l.sport === "nfl");
  const [leagueKey, setLeagueKey] = useState("");
  const league = nfl.find((l) => l.key === leagueKey);
  const players = usePlayers("nfl");
  const [picked, setPicked] = useState<PlayerInfo[]>([]);
  const [q, setQ] = useState("");
  const defense = useDefense();
  const rosters = useRosters(league ? [league] : []);
  const myIds = new Set(rosters[0]?.data ? [...rosters[0].data.starters.map((s) => s.player?.id), ...rosters[0].data.bench.map((p) => p.id)] : []);

  const ecr = useQuery({
    queryKey: ["rankings", "fantasypros", "weekly", league?.key ?? "ppr", "ALL-startsit"],
    queryFn: () => api.rankings(league ? { league: league.key, type: "weekly", pos: "ALL" } : { type: "weekly", pos: "ALL", scoring: "PPR" }),
    staleTime: 30 * 60_000,
  });
  const ecrK = useQuery({ queryKey: ["rankings", "ss-k-def", league?.key], queryFn: async () => [...(await api.rankings({ type: "weekly", pos: "K" })).rows, ...(await api.rankings({ type: "weekly", pos: "DEF" })).rows], staleTime: 30 * 60_000 });
  const row = (id: string) => ecr.data?.rows.find((r) => r.id === id) ?? ecrK.data?.find((r) => r.id === id);

  const results = useMemo(() => {
    if (!players || !q.trim()) return league && !q ? (players ?? []).filter((p) => myIds.has(p.id) && !picked.some((x) => x.id === p.id)).slice(0, 12) : [];
    const t = q.toLowerCase();
    return players.filter((p) => p.name.toLowerCase().includes(t) && !picked.some((x) => x.id === p.id)).slice(0, 8);
  }, [players, q, picked, league?.key, rosters[0]?.dataUpdatedAt]);

  const scored = picked.map((p) => {
    const r = row(p.id);
    const opp = r?.opponent ?? null;
    const dr = opp ? defense.data?.[opp.replace("@", "")]?.[p.pos]?.rank : undefined;
    return { p, r, opp, dr, score: (r ? 1000 - r.rank : 0) + (dr ?? 16) * 0.5 - (p.injury ? 40 : 0) };
  });
  const best = scored.length > 1 ? [...scored].sort((a, b) => b.score - a.score)[0]?.p.id : null;
  const [focus, setFocus] = useState(false);

  return (
    <>
      <SubHeader title="Start / Sit" />
      <div className="page">
        <select value={leagueKey} onChange={(e) => setLeagueKey(e.target.value)} style={{ marginBottom: 10 }} aria-label="League">
          <option value="">PPR (no league)</option>
          {nfl.map((l) => <option key={l.key} value={l.key}>{l.name} · {scoringLabel(l.scoring)}</option>)}
        </select>
        <div style={{ position: "relative", marginBottom: 12 }}>
          <input placeholder={picked.length >= 4 ? "Up to 4 players" : league ? "Your roster, or search any player" : "Search player"} value={q} disabled={picked.length >= 4} onChange={(e) => setQ(e.target.value)} onFocus={() => setFocus(true)} onBlur={() => setTimeout(() => setFocus(false), 150)} />
          {focus && results.length > 0 && (
            <div className="search-results">
              {results.map((p) => (
                <button key={p.id} onMouseDown={(e) => e.preventDefault()} onClick={() => { setPicked([...picked, p]); setQ(""); }}>
                  <PlayerHead id={p.id} name={p.name} size={32} />
                  <span className="grow ellipsis">{p.name}</span>
                  <span className={`postext ${p.pos} small`} style={{ fontWeight: 800 }}>{p.pos} · {p.team ?? "FA"}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        {!picked.length && <div className="empty">Add 2–4 players to compare.</div>}
        <div className="ss-grid">
          {scored.map(({ p, r, opp, dr }) => (
            <div key={p.id} className={`ss-card ${best === p.id ? "best" : ""}`}>
              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <button className="muted small" onClick={() => setPicked(picked.filter((x) => x.id !== p.id))} aria-label={`Remove ${p.name}`}>✕</button>
              </div>
              <PlayerHead id={p.id} name={p.name} size={64} />
              <div style={{ fontWeight: 800, marginTop: 6 }} className="ellipsis">{p.name}</div>
              <div className="small muted">{p.pos} · {p.team ?? "FA"} <Injury status={p.injury} /></div>
              {best === p.id && <div className="pill good" style={{ display: "inline-block", marginTop: 6 }}>START</div>}
              <div className="k">ECR</div><div className="v">{r ? `#${r.rank}` : "–"}</div>
              <div className="small muted">{r?.posRank ?? ""}</div>
              <div className="k">PROJ</div><div className="v">{r?.proj != null ? r.proj.toFixed(1) : "–"}</div>
              <div className="k">MATCHUP</div><div className="v" style={{ fontSize: 18 }}>{opp ? <Opp opp={opp} pos={p.pos} /> : "–"}</div>
              {dr && <div className="small muted">{dr >= 22 ? "Soft" : dr >= 11 ? "Neutral" : "Tough"} defense</div>}
              {r?.best != null && <><div className="k">EXPERT RANGE</div><div className="small">{r.best}–{r.worst}</div></>}
            </div>
          ))}
        </div>
        <div className="legend" style={{ marginTop: 14 }}>
          <span><i style={{ background: "var(--good)" }} />Soft matchup</span>
          <span><i style={{ background: "var(--warn)" }} />Neutral</span>
          <span><i style={{ background: "var(--danger)" }} />Tough</span>
        </div>
      </div>
    </>
  );
}
