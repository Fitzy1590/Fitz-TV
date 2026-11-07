import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, type PlayerProfile, type StatLine } from "../api";
import { IconChevron, IconClose, IconShirt } from "./Icons";
import { Injury, PlatformBadge, PlayerHead, PosBadge, Skeleton, teamLogoUrl } from "./bits";

interface Target { id: string; sport: string; name?: string }
const Ctx = createContext<(t: Target) => void>(() => undefined);
export const usePlayerSheet = () => useContext(Ctx);

export function PlayerSheetProvider({ children }: { children: ReactNode }) {
  const [target, setTarget] = useState<Target | null>(null);
  const open = useCallback((t: Target) => {
    setTarget(t);
    history.pushState({ sheet: true }, "");
  }, []);
  useEffect(() => {
    const onPop = () => setTarget(null);
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);
  useEffect(() => {
    document.body.style.overflow = target ? "hidden" : "";
  }, [target]);
  return (
    <Ctx.Provider value={open}>
      {children}
      {target && <PlayerSheet target={target} onClose={() => history.back()} onOpen={setTarget} />}
    </Ctx.Provider>
  );
}

type Tab = "summary" | "log" | "team" | "history";

function PlayerSheet({ target, onClose, onOpen }: { target: Target; onClose: () => void; onOpen: (t: Target) => void }) {
  const [tab, setTab] = useState<Tab>("summary");
  const [season, setSeason] = useState<string | undefined>();
  const q = useQuery({ queryKey: ["player", target.sport, target.id, season ?? ""], queryFn: () => api.player(target.sport, target.id, season), staleTime: 5 * 60_000 });
  const p = q.data;
  const info = p?.info;
  const name = info?.name ?? target.name ?? "";
  const [first, ...rest] = name.split(" ");
  useEffect(() => setTab("summary"), [target.id]);

  return (
    <>
      <div className="sheet-backdrop" onClick={onClose} />
      <div className="sheet" role="dialog" aria-label={name}>
        <div className="hero">
          {info?.team && <img className="teamlogo" src={teamLogoUrl(target.sport, info.team)!} alt="" onError={(e) => (e.currentTarget.style.display = "none")} />}
          <div className="row" style={{ alignItems: "flex-end", gap: 14 }}>
            <div className="shot">
              <PlayerHead id={target.id} name={name} sport={target.sport} size={150} />
            </div>
            <div className="grow" style={{ paddingBottom: 12 }}>
              <div style={{ fontSize: 18, fontWeight: 700, opacity: 0.9 }}>{first}</div>
              <h2>{rest.join(" ") || first}</h2>
              <div className="bio">
                <span>AGE</span><span>HEIGHT</span><span>WEIGHT</span><span>EXP</span>
                <b>{info?.age ?? "–"}</b><b>{info?.height ?? "–"}</b><b>{info?.weight?.replace(" lbs", "") ?? "–"}</b><b>{info?.exp ?? "–"}</b>
              </div>
            </div>
          </div>
          <div className="teamline">
            <div className="grow">
              <div style={{ fontWeight: 800, fontSize: 17 }}>{info?.team ?? "Free agent"}</div>
              <div className="small">
                <span className={`postext ${info?.pos}`} style={{ fontWeight: 800 }}>{info?.pos}</span>
                {info?.number != null && <> · #{info.number}</>}
                {info?.college && <span className="muted"> · {info.college}</span>} <Injury status={info?.injury} />
              </div>
            </div>
          </div>
        </div>

        <div className="page" style={{ paddingTop: 4 }}>
          {p && <Availability p={p} />}
          <div className="tabs" style={{ top: 0 }}>
            {(["summary", "log", "team", "history"] as Tab[]).map((t) => (
              <button key={t} className={tab === t ? "on" : ""} onClick={() => setTab(t)}>
                {t === "log" ? "Game log" : t}
              </button>
            ))}
          </div>
          {q.isLoading && <Skeleton h={90} n={4} />}
          {q.error && <div className="banner err">{(q.error as Error).message}</div>}
          {p && tab === "summary" && <Summary p={p} sport={target.sport} />}
          {p && tab === "log" && <GameLog p={p} sport={target.sport} onSeason={setSeason} />}
          {p && tab === "team" && <Depth p={p} sport={target.sport} onOpen={onOpen} />}
          {p && tab === "history" && <History p={p} sport={target.sport} />}
        </div>
      </div>
      <button className="close-fab" onClick={onClose} aria-label="Close">
        <IconClose />
      </button>
    </>
  );
}

function Availability({ p }: { p: PlayerProfile }) {
  const [open, setOpen] = useState(false);
  if (!p.availability.length) return null;
  const free = p.availability.filter((a) => a.known && !a.owner).length;
  const mine = p.availability.filter((a) => a.mine).length;
  return (
    <div style={{ borderBottom: "1px solid var(--line)", margin: "0 -16px", padding: "0 16px" }}>
      <button className="row" style={{ width: "100%", padding: "14px 0" }} onClick={() => setOpen(!open)}>
        <IconShirt />
        <span className="caps grow" style={{ textAlign: "left", fontSize: 14 }}>Availability in your leagues</span>
        <span className="pill">{mine ? `${mine} mine` : ""}{mine && free ? " · " : ""}{free ? `${free} free` : ""}{!mine && !free ? "rostered" : ""}</span>
        <IconChevron open={open} />
      </button>
      {open && (
        <div style={{ paddingBottom: 12 }}>
          {p.availability.map((a) => (
            <div key={a.leagueKey} className="row" style={{ padding: "7px 0" }}>
              <PlatformBadge platform={a.platform} />
              <span className="grow ellipsis">{a.league}</span>
              {!a.known ? <span className="muted small">unknown</span> : a.mine ? <span className="pill good">On your team</span> : a.owner ? <span className="muted small ellipsis" style={{ maxWidth: 150 }}>{a.owner}</span> : <span className="pill warn">Free agent</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ Summary
const avg = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0);

function Summary({ p, sport }: { p: PlayerProfile; sport: string }) {
  const played = p.log.filter((r) => Object.keys(r.stats).length && (r.stats.gp ?? 1) > 0);
  const ppr = avg(played.map((r) => r.stats.pts_ppr ?? 0));
  const half = avg(played.map((r) => r.stats.pts_half_ppr ?? 0));
  const std = avg(played.map((r) => r.stats.pts_std ?? 0));
  const thisSeason = p.career.find((c) => c.season === p.season);
  const posRank = thisSeason?.stats.pos_rank_ppr ?? null;
  const owned = p.availability.filter((a) => a.owner).length;
  const upcoming = p.log.filter((r) => r.week >= p.currentWeek && r.opponent).slice(0, 1)[0];
  const projCards = p.log.filter((r) => r.proj || Object.keys(r.stats).length).slice(Math.max(0, p.currentWeek - 2), p.currentWeek + 5);

  return (
    <div>
      <div className="statgrid" style={{ marginTop: 6 }}>
        <div><div className="lbl">POS RANK</div><b>{posRank ? `#${posRank}` : "–"}</b><div className="small muted caps">{p.info?.pos}</div></div>
        <div><div className="lbl">ROSTERED</div><b>{p.availability.length ? `${owned}/${p.availability.length}` : "–"}</b><div className="small muted caps">your leagues</div></div>
        <div>
          <div className="lbl">FPTS/GAME</div>
          {sport === "nfl" ? (
            <><b>{ppr.toFixed(2)}</b><div className="small muted caps">PPR · {half.toFixed(1)} half · {std.toFixed(1)} std</div></>
          ) : (
            <><b>{std.toFixed(1)}</b><div className="small muted caps">per game</div></>
          )}
        </div>
      </div>

      {upcoming && (
        <>
          <div className="section-title caps">Upcoming game</div>
          <div className="card row">
            <span className="pill">WEEK {upcoming.week}</span>
            <div className="grow" style={{ fontWeight: 700 }}>{p.info?.name.split(" ").slice(-1)[0]} {upcoming.opponent?.startsWith("@") ? "at" : "vs"} {upcoming.opponent?.replace("@", "")}</div>
            {upcoming.proj && <div className="num"><b style={{ fontSize: 20 }}>{(upcoming.proj.pts_ppr ?? upcoming.proj.pts_std ?? 0).toFixed(1)}</b> <span className="muted small">proj</span></div>}
          </div>
        </>
      )}

      {projCards.length > 0 && (
        <>
          <div className="section-title caps">Projections</div>
          <div className="chips" style={{ gap: 10 }}>
            {projCards.map((r) => {
              const final = Object.keys(r.stats).length ? r.stats.pts_ppr ?? r.stats.pts_std : null;
              const proj = r.proj?.pts_ppr ?? r.proj?.pts_std;
              return (
                <div key={r.week} className="card" style={{ flex: "none", minWidth: 140, padding: 12 }}>
                  <div className="small" style={{ fontWeight: 800, marginBottom: 8 }}>WK {r.week} {r.opponent ?? "BYE"}</div>
                  <div className="row small"><span className="muted caps grow">Proj</span><b className="num" style={{ fontSize: 18 }}>{proj != null ? proj.toFixed(2) : "–"}</b></div>
                  <div className="row small" style={{ marginTop: 6 }}><span className="muted caps grow">Final</span><b className="num" style={{ fontSize: 18 }}>{final != null ? final.toFixed(2) : "–"}</b></div>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ Game log
type Col = { key: string; label: string; get: (s: StatLine) => number | undefined; fmt?: (n: number) => string; grade?: (n: number) => "good" | "ok" | "bad" | null };
const g = (good: number, ok: number) => (n: number) => (n >= good ? "good" : n >= ok ? "ok" : "bad");
const gLow = (good: number, ok: number) => (n: number) => (n <= good ? "good" : n <= ok ? "ok" : "bad");
const dec1 = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/0$/, ""));

function columns(pos: string, sport: string): { group: string; cols: Col[] }[] {
  if (sport === "nba") {
    return [
      { group: "Fantasy", cols: [{ key: "fp", label: "FPTS", get: (s) => s.pts_std ?? s.pts_ppr, fmt: dec1 }] },
      {
        group: "Box score",
        cols: [
          { key: "min", label: "MIN", get: (s) => s.min },
          { key: "pts", label: "PTS", get: (s) => s.pts, grade: g(25, 15) },
          { key: "reb", label: "REB", get: (s) => s.reb, grade: g(9, 5) },
          { key: "ast", label: "AST", get: (s) => s.ast, grade: g(7, 4) },
          { key: "stl", label: "STL", get: (s) => s.stl },
          { key: "blk", label: "BLK", get: (s) => s.blk },
          { key: "3pm", label: "3PM", get: (s) => s.fg3m },
        ],
      },
    ];
  }
  const fantasy: Col[] = [
    { key: "fp", label: "FPTS", get: (s) => s.pts_ppr, fmt: dec1 },
    { key: "snp", label: "SNP%", get: (s) => (s.off_snp && s.tm_off_snp ? Math.round((100 * s.off_snp) / s.tm_off_snp) : undefined), grade: g(70, 50) },
    { key: "rk", label: "RANK", get: (s) => s.pos_rank_ppr, grade: gLow(12, 24) },
  ];
  const rush: Col[] = [
    { key: "ra", label: "ATT", get: (s) => s.rush_att, grade: pos === "RB" ? g(15, 10) : undefined },
    { key: "ry", label: "YD", get: (s) => s.rush_yd, grade: pos === "RB" ? g(80, 50) : undefined },
    { key: "ypc", label: "YPC", get: (s) => (s.rush_att ? s.rush_yd / s.rush_att : undefined), fmt: (n) => n.toFixed(2).replace(/\.?0+$/, ""), grade: pos === "RB" ? g(4.5, 3.8) : undefined },
    { key: "rtd", label: "TD", get: (s) => s.rush_td ?? (s.rush_att ? 0 : undefined), grade: pos === "RB" ? g(1, 1) : undefined },
  ];
  const rec: Col[] = [
    { key: "tg", label: "TAR", get: (s) => s.rec_tgt, grade: g(6, 4) },
    { key: "rc", label: "REC", get: (s) => s.rec, grade: g(5, 3) },
    { key: "rcy", label: "YD", get: (s) => s.rec_yd, grade: g(70, 40) },
    { key: "rctd", label: "TD", get: (s) => s.rec_td ?? (s.rec_tgt ? 0 : undefined), grade: g(1, 1) },
  ];
  const pass: Col[] = [
    { key: "cmp", label: "CMP", get: (s) => s.pass_cmp },
    { key: "pa", label: "ATT", get: (s) => s.pass_att },
    { key: "py", label: "YD", get: (s) => s.pass_yd, grade: g(275, 220) },
    { key: "ptd", label: "TD", get: (s) => s.pass_td, grade: g(2, 1) },
    { key: "int", label: "INT", get: (s) => s.pass_int, grade: gLow(0, 1) },
  ];
  if (pos === "QB") return [{ group: "Fantasy", cols: fantasy }, { group: "Passing", cols: pass }, { group: "Rushing", cols: rush }];
  if (pos === "RB") return [{ group: "Fantasy", cols: fantasy }, { group: "Rushing", cols: rush }, { group: "Receiving", cols: rec }];
  if (pos === "WR" || pos === "TE") return [{ group: "Fantasy", cols: fantasy }, { group: "Receiving", cols: rec }, { group: "Rushing", cols: rush }];
  return [{ group: "Fantasy", cols: fantasy }];
}

function GameLog({ p, sport, onSeason }: { p: PlayerProfile; sport: string; onSeason: (s: string) => void }) {
  const groups = useMemo(() => columns(p.info?.pos ?? "", sport), [p.info?.pos, sport]);
  const others = p.career.map((c) => c.season).filter((s) => s !== p.season);
  return (
    <div>
      <div className="caps muted" style={{ margin: "4px 0 8px", fontSize: 14 }}>{p.season}</div>
      <div className="tablewrap">
        <table className="stats">
          <thead>
            <tr>
              <th colSpan={2} />
              {groups.map((gr) => (<th key={gr.group} colSpan={gr.cols.length} className="group caps">{gr.group}</th>))}
            </tr>
            <tr>
              <th>WK</th><th>OPP</th>
              {groups.flatMap((gr) => gr.cols.map((c) => <th key={gr.group + c.key}>{c.label}</th>))}
            </tr>
          </thead>
          <tbody>
            {p.log.map((r) => {
              const has = Object.keys(r.stats).length > 0;
              const bye = !r.opponent;
              return (
                <tr key={r.week}>
                  <td className="lead num">{r.week}</td>
                  <td className="lead" style={{ color: bye ? "var(--text)" : r.opponent?.startsWith("@") ? "var(--te)" : "var(--good)" }}>{r.opponent ?? "BYE"}</td>
                  {groups.flatMap((gr) =>
                    gr.cols.map((c) => {
                      if (bye) return <td key={gr.group + c.key} className="bye" />;
                      const v = has ? c.get(r.stats) : undefined;
                      if (v === undefined || v === null || Number.isNaN(v)) return <td key={gr.group + c.key} className="c">-</td>;
                      const cls = c.key === "fp" ? "pts" : c.grade?.(v) ?? "c";
                      return <td key={gr.group + c.key} className={`num ${cls}`}>{c.fmt ? c.fmt(v) : v}</td>;
                    }),
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {others.length > 0 && (
        <div style={{ marginTop: 20, display: "grid", gap: 14 }}>
          {others.map((s) => (
            <button key={s} className="caps" style={{ textAlign: "left", color: "var(--muted)", fontSize: 15 }} onClick={() => onSeason(s)}>{s} (view)</button>
          ))}
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ Team / depth chart
const DEPTH_ORDER = ["QB", "RB", "WR", "TE", "K", "PG", "SG", "SF", "PF", "C"];
function Depth({ p, sport, onOpen }: { p: PlayerProfile; sport: string; onOpen: (t: Target) => void }) {
  const byPos = new Map<string, PlayerProfile["depth"]>();
  for (const d of p.depth) {
    const key = d.pos === "LWR" || d.pos === "RWR" || d.pos === "SWR" ? `WR${d.pos === "LWR" ? 1 : d.pos === "RWR" ? 2 : 3}` : d.pos;
    byPos.set(key, [...(byPos.get(key) ?? []), d].sort((a, b) => a.order - b.order));
  }
  const keys = [...byPos.keys()].sort((a, b) => (DEPTH_ORDER.indexOf(a.replace(/\d/, "")) + 1 || 99) - (DEPTH_ORDER.indexOf(b.replace(/\d/, "")) + 1 || 99) || a.localeCompare(b));
  if (!keys.length) return <div className="empty">No depth chart available.</div>;
  return (
    <div>
      <div className="section-title caps" style={{ marginTop: 6 }}>Depth chart · {p.info?.team}</div>
      <div className="depth">
        <div className="h">POS</div><div className="h">STARTER</div><div className="h">2ND</div><div className="h">3RD</div>
        {keys.map((k) => (
          <DepthRow key={k} pos={k} players={byPos.get(k)!} sport={sport} onOpen={onOpen} current={p.info?.id} />
        ))}
      </div>
    </div>
  );
}
function DepthRow({ pos, players, sport, onOpen, current }: { pos: string; players: PlayerProfile["depth"]; sport: string; onOpen: (t: Target) => void; current?: string }) {
  return (
    <>
      <div className={`p postext ${pos.replace(/\d/, "")}`}>{pos}</div>
      {[1, 2, 3].map((o) => {
        const d = players.find((x) => x.order === o);
        if (!d) return <div key={o} />;
        const [first, ...rest] = d.name.split(" ");
        return (
          <button key={o} onClick={() => d.id !== current && onOpen({ id: d.id, sport, name: d.name })} style={{ display: "block", padding: 10, borderBottom: "1px solid var(--line)" }}>
            <div className="first ellipsis">{first}</div>
            <div style={{ fontWeight: 700, fontSize: 15, color: d.id === current ? "var(--accent)" : undefined }}>{rest.join(" ") || first}</div>
            {d.age && <div className="first">{d.age} y/o</div>}
            {d.injury && <div className="small" style={{ color: "var(--danger)", textAlign: "right", fontWeight: 700 }}>{d.injury}</div>}
          </button>
        );
      })}
    </>
  );
}

// ------------------------------------------------------------------ History
function History({ p, sport }: { p: PlayerProfile; sport: string }) {
  if (!p.career.length) return <div className="empty">No career stats yet.</div>;
  const gp = (s: StatLine) => s.gp ?? s.gms_active ?? 0;
  const gpGrade = g(14, 10);
  return (
    <div>
      <div className="section-title caps" style={{ marginTop: 6 }}>Career</div>
      <div className="tablewrap">
        <table className="stats">
          <thead>
            <tr><th /><th>GM</th><th>FPTS</th>{sport === "nfl" ? <><th>PPR RK</th><th>PPG</th><th>RUSH ATT</th><th>RUSH YD</th><th>REC</th><th>REC YD</th><th>TD</th></> : <><th>PTS</th><th>REB</th><th>AST</th></>}</tr>
          </thead>
          <tbody>
            {p.career.map((c) => {
              const s = c.stats;
              const fp = sport === "nfl" ? s.pts_ppr ?? 0 : s.pts_std ?? 0;
              const td = (s.rush_td ?? 0) + (s.rec_td ?? 0) + (s.pass_td ?? 0);
              return (
                <tr key={c.season}>
                  <td className="lead num">{c.season} <span className="muted small">{c.team ?? ""}</span></td>
                  <td className={`num ${gpGrade(gp(s))}`}>{gp(s) || "-"}</td>
                  <td className="num pts">{fp.toFixed(1)}</td>
                  {sport === "nfl" ? (
                    <>
                      <td className={`num ${s.pos_rank_ppr ? gLow(6, 18)(s.pos_rank_ppr) : "c"}`}>{s.pos_rank_ppr ?? "-"}</td>
                      <td className="num c">{gp(s) ? (fp / gp(s)).toFixed(1) : "-"}</td>
                      <td className="num c">{s.rush_att ?? "-"}</td>
                      <td className="num c">{s.rush_yd ?? "-"}</td>
                      <td className="num c">{s.rec ?? "-"}</td>
                      <td className="num c">{s.rec_yd ?? "-"}</td>
                      <td className="num c">{td || "-"}</td>
                    </>
                  ) : (
                    <>
                      <td className="num c">{s.pts ?? "-"}</td>
                      <td className="num c">{s.reb ?? "-"}</td>
                      <td className="num c">{s.ast ?? "-"}</td>
                    </>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="small muted" style={{ marginTop: 12 }}>
        <PosBadge slot={p.info?.pos ?? "BN"} /> Career numbers come from Sleeper's stats feed.
      </div>
    </div>
  );
}
