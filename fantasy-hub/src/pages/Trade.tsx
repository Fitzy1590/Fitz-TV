import { useMutation, useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { api, type PlayerInfo } from "../api";
import { useLeagues, usePlayers } from "../hooks";
import { PlayerHead, scoringLabel } from "../components/bits";
import { usePlayerSheet } from "../components/PlayerSheet";

export function Trade() {
  const { data: leagues } = useLeagues();
  const nfl = (leagues ?? []).filter((l) => l.sport === "nfl");
  const [leagueKey, setLeagueKey] = useState<string>("");
  const [source, setSource] = useState<"fantasypros" | "footballers" | "ktc">("fantasypros");
  const [give, setGive] = useState<PlayerInfo[]>([]);
  const [get, setGet] = useState<PlayerInfo[]>([]);
  const league = nfl.find((l) => l.key === leagueKey);
  const basePlayers = usePlayers("nfl");
  // Draft picks (KeepTradeCut) are tradeable in dynasty mode.
  const values = useQuery({ queryKey: ["values"], queryFn: api.values, enabled: source === "ktc" || !!league?.dynasty, staleTime: 6 * 3600_000, retry: false });
  const players = useMemo(() => {
    if (!basePlayers) return null;
    const picks = (values.data ?? []).filter((v) => v.pos === "PICK").map((v) => ({ ...basePlayers[0], id: v.id, name: v.name, first: "", last: v.name, pos: "PICK", positions: ["PICK"], team: null, age: null, searchRank: null }) as PlayerInfo);
    return [...basePlayers, ...picks];
  }, [basePlayers, values.data]);
  const openPlayer = usePlayerSheet();

  // When a league is chosen, "You give" searches your roster first.
  const roster = useQuery({ queryKey: ["roster", leagueKey, "current"], queryFn: () => api.roster(leagueKey), enabled: !!leagueKey });
  const myIds = useMemo(
    () => new Set(roster.data ? [...roster.data.starters.map((s) => s.player?.id), ...roster.data.bench.map((p) => p.id), ...roster.data.reserve.map((p) => p.id)].filter(Boolean) as string[] : []),
    [roster.data],
  );

  const run = useMutation({
    mutationFn: () => api.trade({ give: give.map((p) => p.id), get: get.map((p) => p.id), league: leagueKey || undefined, source }),
  });
  const r = run.data;

  return (
    <>
      <header className="topbar">
        <h1 className="grow">Trade Analyzer</h1>
      </header>
      <div className="page">
        <div className="row">
          <select value={leagueKey} onChange={(e) => setLeagueKey(e.target.value)} aria-label="League">
            <option value="">Any league (PPR, redraft)</option>
            {nfl.map((l) => <option key={l.key} value={l.key}>{l.name} · {scoringLabel(l.scoring)}{l.dynasty ? " · dynasty" : ""}</option>)}
          </select>
        </div>
        <div className="chips" style={{ marginTop: 8 }}>
          <button className={`chip ${source === "fantasypros" ? "on" : ""}`} onClick={() => setSource("fantasypros")}>FantasyPros values</button>
          <button className={`chip ${source === "footballers" ? "on" : ""}`} onClick={() => setSource("footballers")}>Footballers ROS</button>
          <button className={`chip ${source === "ktc" ? "on" : ""}`} onClick={() => setSource("ktc")}>Dynasty (KTC + picks)</button>
        </div>

        <div className="trade-cols" style={{ marginTop: 14 }}>
          <Side title="You give" list={give} setList={setGive} players={players} prefer={myIds} onOpen={(p) => openPlayer({ id: p.id, sport: "nfl", name: p.name })} />
          <Side title="You get" list={get} setList={setGet} players={players} exclude={myIds} onOpen={(p) => openPlayer({ id: p.id, sport: "nfl", name: p.name })} />
        </div>

        <button className="btn primary" style={{ width: "100%", marginTop: 14 }} disabled={!give.length || !get.length || run.isPending} onClick={() => run.mutate()}>
          {run.isPending ? "Analyzing…" : "Analyze trade"}
        </button>
        {run.error && <div className="banner err" style={{ marginTop: 10 }}>{(run.error as Error).message}</div>}

        {r && (
          <div className="card" style={{ marginTop: 14 }}>
            <div className="row">
              <div className="grow">
                <div className="caps muted">Verdict</div>
                <div className="score" style={{ color: verdictColor(r.verdict) }}>{r.verdict}</div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div className="num" style={{ fontWeight: 800 }}>{r.edge >= 0 ? "+" : ""}{r.edge}</div>
                <div className="small muted">value edge</div>
              </div>
            </div>
            <div className="meter" style={{ marginTop: 12 }}>
              <i style={{ width: `${(100 * r.giveTotal) / Math.max(1, r.giveTotal + r.getTotal)}%`, background: "var(--danger)", opacity: 0.75 }} />
              <i style={{ flex: 1, background: "var(--good)" }} />
            </div>
            <div className="row small muted" style={{ marginTop: 6 }}>
              <span className="grow">Give {r.giveTotal}</span>
              <span>Get {r.getTotal}</span>
            </div>
            <div className="trade-cols" style={{ marginTop: 12 }}>
              {[r.give, r.get].map((side, i) => (
                <div key={i}>
                  {side.map((p) => (
                    <div key={p.id} className="row small" style={{ padding: "4px 0" }}>
                      <span className="grow ellipsis">{p.name}</span>
                      <span className="muted num">{p.rank ? `#${p.rank}` : "unranked"}</span>
                      <b className="num">{p.value}</b>
                    </div>
                  ))}
                </div>
              ))}
            </div>
            <div className="small muted" style={{ marginTop: 10 }}>Values from {r.source}{league ? ` using ${scoringLabel(league.scoring)}` : ""}. Receiving extra players costs a roster spot, so volume alone doesn't win a trade.</div>
          </div>
        )}
      </div>
    </>
  );
}

function Side({ title, list, setList, players, prefer, exclude, onOpen }: { title: string; list: PlayerInfo[]; setList: (p: PlayerInfo[]) => void; players: PlayerInfo[] | null; prefer?: Set<string>; exclude?: Set<string>; onOpen: (p: PlayerInfo) => void }) {
  const [q, setQ] = useState("");
  const results = useMemo(() => {
    if (!players) return [];
    const term = q.toLowerCase().trim();
    let pool = players.filter((p) => ["QB", "RB", "WR", "TE", "PICK"].includes(p.pos) && !list.some((x) => x.id === p.id) && !exclude?.has(p.id));
    if (term) pool = pool.filter((p) => p.name.toLowerCase().includes(term));
    else if (prefer?.size) pool = pool.filter((p) => prefer.has(p.id));
    else return [];
    return pool.slice(0, 8);
  }, [players, q, list, prefer, exclude]);
  const [focus, setFocus] = useState(false);

  return (
    <div>
      <div className="caps muted" style={{ marginBottom: 8 }}>{title}</div>
      <div style={{ position: "relative" }}>
        <input placeholder={prefer?.size ? "Your roster or search" : "Search player"} value={q} onChange={(e) => setQ(e.target.value)} onFocus={() => setFocus(true)} onBlur={() => setTimeout(() => setFocus(false), 150)} />
        {focus && results.length > 0 && (
          <div className="search-results">
            {results.map((p) => (
              <button key={p.id} onMouseDown={(e) => e.preventDefault()} onClick={() => { setList([...list, p]); setQ(""); }}>
                <PlayerHead id={p.id} name={p.name} size={32} />
                <span className="grow ellipsis">{p.name}</span>
                <span className={`postext ${p.pos} small`} style={{ fontWeight: 800 }}>{p.pos}</span>
              </button>
            ))}
          </div>
        )}
      </div>
      <div style={{ marginTop: 8, display: "grid", gap: 6 }}>
        {list.map((p) => (
          <div key={p.id} className="card row" style={{ padding: 8 }}>
            <button onClick={() => onOpen(p)}><PlayerHead id={p.id} name={p.name} size={34} /></button>
            <div className="grow">
              <div className="ellipsis small" style={{ fontWeight: 700 }}>{p.name}</div>
              <div className="small muted">{p.pos} · {p.team ?? "FA"}</div>
            </div>
            <button className="muted" aria-label={`Remove ${p.name}`} onClick={() => setList(list.filter((x) => x.id !== p.id))}>✕</button>
          </div>
        ))}
      </div>
    </div>
  );
}

function verdictColor(v: string) {
  if (/smash|win/i.test(v)) return "var(--good)";
  if (/fair/i.test(v)) return "var(--accent)";
  if (/overpay/i.test(v)) return "var(--warn)";
  return "var(--danger)";
}
