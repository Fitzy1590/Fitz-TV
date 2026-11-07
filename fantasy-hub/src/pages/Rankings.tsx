import { useQuery, useQueries } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { api } from "../api";
import { useLeagues } from "../hooks";
import { PlayerHead, Skeleton, scoringLabel } from "../components/bits";
import { usePlayerSheet } from "../components/PlayerSheet";
import type { RankingRow, RankingType, ScoringProfile } from "../../shared/types";

const POSITIONS = ["ALL", "QB", "RB", "WR", "TE", "K", "DEF"];
const PRESETS: { id: string; label: string; s: ScoringProfile }[] = [
  { id: "std", label: "Standard", s: { base: "STD", superflex: false, tePremium: 0 } },
  { id: "half", label: "Half PPR", s: { base: "HALF", superflex: false, tePremium: 0 } },
  { id: "ppr", label: "Full PPR", s: { base: "PPR", superflex: false, tePremium: 0 } },
  { id: "sf", label: "Superflex", s: { base: "PPR", superflex: true, tePremium: 0 } },
  { id: "tep", label: "TE Premium", s: { base: "PPR", superflex: false, tePremium: 0.5 } },
];

export function Rankings() {
  const { data: leagues } = useLeagues();
  const nfl = (leagues ?? []).filter((l) => l.sport === "nfl");
  const [source, setSource] = useState<"fantasypros" | "footballers">("fantasypros");
  const [type, setType] = useState<RankingType>("weekly");
  const [scope, setScope] = useState<string>("ppr"); // preset id or league key
  const [pos, setPos] = useState("ALL");
  const [query, setQuery] = useState("");
  const openPlayer = usePlayerSheet();

  const league = nfl.find((l) => l.key === scope);
  const preset = PRESETS.find((p) => p.id === scope);
  const scoring = league?.scoring ?? preset?.s ?? PRESETS[2].s;

  const q = useQuery({
    queryKey: ["rankings", source, type, scope, pos],
    queryFn: () =>
      api.rankings({
        source,
        type,
        pos,
        ...(league ? { league: league.key } : { scoring: scoring.base, superflex: scoring.superflex ? 1 : 0, tep: scoring.tePremium }),
      }),
    staleTime: 30 * 60_000,
    retry: false,
  });

  // Which of my players are in this list (dot + "mine" filter).
  const rosters = useQueries({ queries: nfl.map((l) => ({ queryKey: ["roster", l.key, "current"], queryFn: () => api.roster(l.key), staleTime: 60_000 })) });
  const mine = useMemo(() => {
    const m = new Map<string, number>();
    for (const r of rosters) {
      if (!r.data) continue;
      for (const p of [...r.data.starters.map((s) => s.player), ...r.data.bench, ...r.data.reserve]) if (p) m.set(p.id, (m.get(p.id) ?? 0) + 1);
    }
    return m;
  }, [rosters]);
  const [onlyMine, setOnlyMine] = useState(false);

  const rows = (q.data?.rows ?? []).filter((r) => (!onlyMine || (r.id && mine.has(r.id))) && (!query || r.name.toLowerCase().includes(query.toLowerCase())));

  return (
    <>
      <header className="topbar">
        <h1 className="grow">Rankings</h1>
      </header>
      <div className="page">
        <div className="chips">
          <button className={`chip ${source === "fantasypros" ? "on" : ""}`} onClick={() => setSource("fantasypros")}>FantasyPros ECR</button>
          <button className={`chip ${source === "footballers" ? "on" : ""}`} onClick={() => setSource("footballers")}>Fantasy Footballers</button>
        </div>
        <div className="chips" style={{ marginTop: 8 }}>
          {(["weekly", "ros", "dynasty", "draft"] as RankingType[]).map((t) => (
            <button key={t} className={`chip ${type === t ? "on" : ""}`} onClick={() => setType(t)}>{t === "ros" ? "Rest of season" : t[0].toUpperCase() + t.slice(1)}</button>
          ))}
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          <select value={scope} onChange={(e) => setScope(e.target.value)} aria-label="Points system">
            <optgroup label="Points system">
              {PRESETS.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
            </optgroup>
            {nfl.length > 0 && (
              <optgroup label="Use a league's settings">
                {nfl.map((l) => <option key={l.key} value={l.key}>{l.name} ({scoringLabel(l.scoring)})</option>)}
              </optgroup>
            )}
          </select>
        </div>
        <div className="chips" style={{ marginTop: 10 }}>
          {POSITIONS.map((p) => (
            <button key={p} className={`chip ${pos === p ? "on" : ""}`} onClick={() => setPos(p)}>{p === "ALL" ? (scoring.superflex ? "Overall (SF)" : "Overall") : p}</button>
          ))}
          <button className={`chip ${onlyMine ? "on" : ""}`} onClick={() => setOnlyMine(!onlyMine)}>My players</button>
        </div>
        <input style={{ marginTop: 10 }} placeholder="Search players" value={query} onChange={(e) => setQuery(e.target.value)} />

        {q.data?.note && <div className="banner info" style={{ marginTop: 10 }}>{q.data.note}</div>}
        {q.data && (
          <div className="small muted" style={{ marginTop: 8 }}>
            {scoringLabel(q.data.scoring)}{q.data.week ? ` · Week ${q.data.week}` : ""} · updated {new Date(q.data.updated).toLocaleString()}
          </div>
        )}
        {q.isLoading && <div style={{ marginTop: 12 }}><Skeleton h={52} n={10} /></div>}
        {q.error && (
          <div className="banner err" style={{ marginTop: 12 }}>
            {(q.error as Error).message}
            {source === "footballers" && <> — import them from <a href="/accounts">Accounts</a>.</>}
          </div>
        )}
        <div style={{ marginTop: 6 }}>
          {rows.map((r, i) => (
            <RankRow key={`${r.name}-${r.rank}`} r={r} prevTier={rows[i - 1]?.tier} mine={r.id ? mine.get(r.id) ?? 0 : 0} onOpen={() => r.id && openPlayer({ id: r.id, sport: "nfl", name: r.name })} />
          ))}
        </div>
      </div>
    </>
  );
}

function RankRow({ r, prevTier, mine, onOpen }: { r: RankingRow; prevTier?: number | null; mine: number; onOpen: () => void }) {
  return (
    <>
      {r.tier != null && r.tier !== prevTier && <div className="tier">TIER {r.tier}</div>}
      <button className="rank-row" onClick={onOpen}>
        <span className="rk num">{r.rank}</span>
        <PlayerHead id={r.id ?? r.name} name={r.name} size={40} />
        <div className="grow">
          <div style={{ fontWeight: 700 }} className="ellipsis">
            {r.name} {mine > 0 && <span className="mine-dot" title={`On ${mine} of your teams`} />}
          </div>
          <div className="small muted">
            <span className={`postext ${r.pos}`} style={{ fontWeight: 800 }}>{r.posRank ?? r.pos}</span> · {r.team ?? "FA"}
            {r.opponent ? ` · ${r.opponent}` : ""}
            {r.best != null && r.worst != null ? ` · ${r.best}–${r.worst}` : ""}
          </div>
        </div>
        {r.proj != null && (
          <div style={{ textAlign: "right" }}>
            <b className="num">{r.proj.toFixed(1)}</b>
            <div className="small muted">proj</div>
          </div>
        )}
      </button>
    </>
  );
}
