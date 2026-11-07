import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, type League } from "../api";
import { useLeagues, useRosters } from "../hooks";
import { optimizeLineup } from "../../shared/lineup";
import type { Player, Roster, RosterSpot } from "../../shared/types";
import { Injury, PlatformBadge, PlayerHead, PosBadge, Skeleton } from "../components/bits";
import { IconBack, IconBolt } from "../components/Icons";
import { NewsRow } from "../components/PlayerSheet";
import { usePlayerSheet } from "../components/PlayerSheet";

const TOOLS = [
  { to: "/tools/optimize", ico: "⚡", name: "Optimize all", desc: "Best lineup in every league, one tap" },
  { to: "/tools/start-sit", ico: "⚖️", name: "Start / Sit", desc: "Compare players head to head" },
  { to: "/trade", ico: "🔁", name: "Trade analyzer", desc: "FantasyPros, Footballers or KTC values" },
  { to: "/tools/trade-finder", ico: "🧭", name: "Trade finder", desc: "Fair deals that fix your weak spots" },
  { to: "/tools/waivers", ico: "📈", name: "Waiver targets", desc: "Best free agents in each league" },
  { to: "/tools/power", ico: "🏆", name: "Power rankings", desc: "Who's really the best team" },
  { to: "/tools/exposure", ico: "🧮", name: "Exposure", desc: "Players you own the most" },
  { to: "/tools/draft", ico: "📋", name: "Draft assistant", desc: "Live Sleeper draft board" },
  { to: "/tools/news", ico: "📰", name: "My news", desc: "Latest on your players" },
];

export function ToolsHome() {
  return (
    <>
      <header className="topbar"><h1 className="grow">Tools</h1></header>
      <div className="page">
        <div className="tool-grid">
          {TOOLS.map((t) => (
            <Link key={t.to} to={t.to} className="tool">
              <span className="ico">{t.ico}</span>
              <b>{t.name}</b>
              <span>{t.desc}</span>
            </Link>
          ))}
        </div>
      </div>
    </>
  );
}

export function SubHeader({ title, back = "/tools" }: { title: string; back?: string }) {
  return (
    <header className="topbar">
      <Link to={back} className="iconbtn" aria-label="Back" style={{ color: "var(--text)" }}><IconBack /></Link>
      <h1 className="grow ellipsis" style={{ fontSize: 22 }}>{title}</h1>
    </header>
  );
}

// ---------------------------------------------------------------------------
// Optimize all
// ---------------------------------------------------------------------------
interface Plan { league: League; roster: Roster; next: RosterSpot[]; changes: { slot: string; out: Player | null; in: Player | null }[]; gain: number }

export function OptimizeAll() {
  const qc = useQueryClient();
  const { data } = useLeagues();
  const leagues = (data ?? []).filter((l) => !l.hidden);
  const rosters = useRosters(leagues);
  const ecr = useQueries({
    queries: leagues.map((l) => ({
      queryKey: ["rankings", "fantasypros", "weekly", l.key, l.currentWeek],
      queryFn: () => api.rankings({ league: l.key, type: "weekly", pos: "ALL", week: l.currentWeek }),
      enabled: l.sport === "nfl",
      staleTime: 30 * 60_000,
      retry: false,
    })),
  });
  const [status, setStatus] = useState<Record<string, "saving" | "saved" | string>>({});

  const plans: Plan[] = useMemo(
    () =>
      leagues.flatMap((league, i) => {
        const roster = rosters[i].data;
        if (!roster?.canEdit) return [];
        const rank = new Map((ecr[i].data?.rows ?? []).filter((r) => r.id).map((r) => [r.id!, r.rank]));
        const value = (p: Player) => (rank.has(p.id) ? 10_000 - rank.get(p.id)! : p.proj ?? 0);
        const bench = roster.bench;
        const next = optimizeLineup(roster.starters, bench, value);
        const changes = next
          .map((s, j) => ({ slot: s.slot, out: roster.starters[j].player, in: s.player }))
          .filter((c) => c.out?.id !== c.in?.id);
        const proj = (xs: RosterSpot[]) => xs.reduce((s, x) => s + (x.player?.proj ?? 0), 0);
        return changes.length ? [{ league, roster, next, changes, gain: proj(next) - proj(roster.starters) }] : [];
      }),
    [leagues.map((l) => l.key).join(), rosters.map((r) => r.dataUpdatedAt).join(), ecr.map((r) => r.dataUpdatedAt).join()],
  );
  const loading = rosters.some((r) => r.isLoading);

  const save = async (p: Plan) => {
    setStatus((s) => ({ ...s, [p.league.key]: "saving" }));
    try {
      await api.saveLineup(p.league.key, { week: p.roster.week, starters: p.next.map((s) => s.player?.platformId ?? null) });
      setStatus((s) => ({ ...s, [p.league.key]: "saved" }));
      qc.invalidateQueries({ queryKey: ["roster", p.league.key] });
    } catch (e) {
      setStatus((s) => ({ ...s, [p.league.key]: (e as Error).message }));
    }
  };
  const pending = plans.filter((p) => status[p.league.key] !== "saved");

  return (
    <>
      <SubHeader title="Optimize all lineups" />
      <div className="page">
        <p className="small muted" style={{ marginTop: 0 }}>Uses FantasyPros weekly ECR in each league's points system (projections for basketball). Players who are out, on bye or already locked aren't moved in.</p>
        {loading && <Skeleton h={90} n={3} />}
        {!loading && !plans.length && <div className="empty">✅ Every lineup is already optimal.</div>}
        {pending.length > 1 && (
          <button className="btn primary" style={{ width: "100%", marginBottom: 12 }} onClick={() => pending.forEach(save)}>
            <IconBolt /> Save all {pending.length} lineups
          </button>
        )}
        {plans.map((p) => {
          const st = status[p.league.key];
          return (
            <div key={p.league.key} className="card" style={{ marginBottom: 10 }}>
              <div className="row" style={{ marginBottom: 8 }}>
                <PlatformBadge platform={p.league.platform} />
                <b className="grow ellipsis">{p.league.name}</b>
                {p.gain > 0.05 && <span className="pill good">+{p.gain.toFixed(1)} proj</span>}
              </div>
              {p.changes.map((c, i) => (
                <div key={i} className="row small" style={{ padding: "4px 0" }}>
                  <PosBadge slot={c.slot} />
                  <span className="diff-out ellipsis">{c.out?.name ?? "Empty"}</span>
                  <span className="muted">→</span>
                  <span className="diff-in ellipsis grow">{c.in?.name ?? "Empty"}</span>
                </div>
              ))}
              <div className="row" style={{ marginTop: 8 }}>
                <span className="grow small" style={{ color: st && st !== "saving" && st !== "saved" ? "var(--danger)" : "var(--good)" }}>{st === "saved" ? "Saved ✓" : st && st !== "saving" ? st : ""}</span>
                <button className="btn primary" disabled={st === "saving" || st === "saved"} onClick={() => save(p)}>{st === "saving" ? "Saving…" : "Save"}</button>
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Exposure
// ---------------------------------------------------------------------------
export function Exposure() {
  const { data } = useLeagues();
  const [sport, setSport] = useState<"nfl" | "nba">("nfl");
  const leagues = (data ?? []).filter((l) => !l.hidden && l.sport === sport);
  const rosters = useRosters(leagues);
  const openPlayer = usePlayerSheet();
  const rows = useMemo(() => {
    const m = new Map<string, { p: Player; leagues: string[]; starting: number }>();
    rosters.forEach((r, i) => {
      if (!r.data) return;
      const starters = new Set(r.data.starters.map((s) => s.player?.id));
      for (const p of [...r.data.starters.map((s) => s.player), ...r.data.bench, ...r.data.reserve, ...r.data.taxi]) {
        if (!p) continue;
        const e = m.get(p.id) ?? { p, leagues: [], starting: 0 };
        if (!e.leagues.includes(leagues[i].name)) e.leagues.push(leagues[i].name);
        if (starters.has(p.id)) e.starting++;
        m.set(p.id, e);
      }
    });
    return [...m.values()].sort((a, b) => b.leagues.length - a.leagues.length || b.starting - a.starting);
  }, [rosters.map((r) => r.dataUpdatedAt).join(), sport]);
  const byTeam = useMemo(() => {
    const t = new Map<string, number>();
    rows.forEach((r) => r.p.team && t.set(r.p.team, (t.get(r.p.team) ?? 0) + r.leagues.length));
    return [...t.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);
  }, [rows]);

  return (
    <>
      <SubHeader title="Player exposure" />
      <div className="page">
        <div className="chips" style={{ marginBottom: 12 }}>
          <button className={`chip ${sport === "nfl" ? "on" : ""}`} onClick={() => setSport("nfl")}>🏈 Football</button>
          <button className={`chip ${sport === "nba" ? "on" : ""}`} onClick={() => setSport("nba")}>🏀 Basketball</button>
        </div>
        {byTeam.length > 0 && (
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="caps muted" style={{ marginBottom: 8 }}>Most exposed teams (bye-week risk)</div>
            <div className="chips">{byTeam.map(([t, n]) => <span key={t} className="pill">{t} · {n}</span>)}</div>
          </div>
        )}
        {rosters.some((r) => r.isLoading) && <Skeleton h={56} n={6} />}
        {rows.map(({ p, leagues: ls, starting }) => (
          <button key={p.id} className="rank-row" onClick={() => openPlayer({ id: p.id, sport, name: p.name })}>
            <PlayerHead id={p.id} name={p.name} sport={sport} size={40} />
            <div className="grow">
              <div style={{ fontWeight: 700 }} className="ellipsis">{p.name} <Injury status={p.injury} /></div>
              <div className="small muted ellipsis">{p.positions[0]} · {p.team ?? "FA"} · {ls.join(", ")}</div>
            </div>
            <div style={{ textAlign: "right", minWidth: 70 }}>
              <b className="num">{ls.length}/{leagues.length}</b>
              <div className="small muted">{starting} starting</div>
              <div className="bar" style={{ marginTop: 4 }}><i style={{ width: `${(100 * ls.length) / Math.max(1, leagues.length)}%` }} /></div>
            </div>
          </button>
        ))}
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// News
// ---------------------------------------------------------------------------
export function News() {
  const { data } = useLeagues();
  const [sport, setSport] = useState<"nfl" | "nba">("nfl");
  const [all, setAll] = useState(false);
  const leagues = (data ?? []).filter((l) => l.sport === sport);
  const rosters = useRosters(leagues);
  const ids = useMemo(() => {
    const s = new Set<string>();
    rosters.forEach((r) => r.data && [...r.data.starters.map((x) => x.player), ...r.data.bench, ...r.data.reserve].forEach((p) => p && s.add(p.id)));
    return [...s].sort();
  }, [rosters.map((r) => r.dataUpdatedAt).join()]);
  const q = useQuery({ queryKey: ["news", sport, all ? "all" : ids.join(",")], queryFn: () => api.news(sport, all ? undefined : ids), enabled: all || ids.length > 0, staleTime: 5 * 60_000, refetchInterval: 10 * 60_000 });
  return (
    <>
      <SubHeader title="News" />
      <div className="page">
        <div className="chips" style={{ marginBottom: 8 }}>
          <button className={`chip ${sport === "nfl" ? "on" : ""}`} onClick={() => setSport("nfl")}>🏈 Football</button>
          <button className={`chip ${sport === "nba" ? "on" : ""}`} onClick={() => setSport("nba")}>🏀 Basketball</button>
          <button className={`chip ${!all ? "on" : ""}`} onClick={() => setAll(!all)}>My players only</button>
        </div>
        {q.isLoading && <Skeleton h={70} n={5} />}
        {q.data && !q.data.length && <div className="empty">No recent news on your players.</div>}
        {q.data?.map((n) => <NewsRow key={n.id} n={n} />)}
      </div>
    </>
  );
}
