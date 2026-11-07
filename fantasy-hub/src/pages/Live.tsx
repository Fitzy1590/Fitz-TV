import { useQueries } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, type League } from "../api";
import { useLeagues, useRosters } from "../hooks";
import { PlatformBadge, Skeleton } from "../components/bits";
import type { Matchup, Roster } from "../../shared/types";

/** Normal CDF (Abramowitz-Stegun). */
function phi(x: number) {
  const t = 1 / (1 + 0.2316419 * Math.abs(x));
  const d = 0.3989423 * Math.exp((-x * x) / 2);
  const p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
  return x > 0 ? 1 - p : p;
}

/** Win probability from live projections; uncertainty shrinks as more of the projection is already banked. */
export function winProbability(m: Matchup, myProjFallback: number | null): number | null {
  if (!m.opponent) return null;
  const myProj = m.me.projected ?? myProjFallback;
  const oppProj = m.opponent.projected ?? null;
  if (myProj == null || oppProj == null) return null;
  const remaining = Math.max(0, myProj - m.me.points) + Math.max(0, oppProj - m.opponent.points);
  const total = Math.max(1, myProj + oppProj);
  const sigma = 0.5 + 0.22 * total * Math.sqrt(remaining / total) / Math.SQRT2;
  return phi((myProj - oppProj) / sigma);
}

function rosterProj(r: Roster | undefined) {
  if (!r) return null;
  return r.starters.reduce((s, x) => s + (x.player ? (x.player.locked ? x.player.points ?? 0 : x.player.proj ?? 0) : 0), 0);
}

export function Live() {
  const { data } = useLeagues();
  const leagues = (data ?? []).filter((l) => !l.hidden);
  const matchups = useQueries({
    queries: leagues.map((l) => ({ queryKey: ["matchup", l.key, "current"], queryFn: () => api.matchup(l.key), refetchInterval: 30_000, staleTime: 15_000 })),
  });
  const rosters = useRosters(leagues);
  const rows = leagues.map((l, i) => ({ l, m: matchups[i].data, loading: matchups[i].isLoading, r: rosters[i].data }));
  const probs = rows.map(({ m, r }) => (m ? winProbability(m, rosterProj(r)) : null));
  const expectedWins = probs.reduce<number>((s, p) => s + (p ?? 0), 0);

  return (
    <>
      <header className="topbar">
        <h1 className="grow">Live</h1>
        <span className="small muted">auto-refresh 30s</span>
      </header>
      <div className="page">
        {leagues.length > 0 && (
          <div className="card row" style={{ marginBottom: 12 }}>
            <div className="grow">
              <div className="caps muted">Projected record this week</div>
              <div className="score">{expectedWins.toFixed(1)} – {(probs.filter((p) => p != null).length - expectedWins).toFixed(1)}</div>
            </div>
            <div className="small muted" style={{ textAlign: "right" }}>{rows.filter((x) => x.m?.opponent && x.m.me.points > x.m.opponent.points).length} winning now</div>
          </div>
        )}
        {!data && <Skeleton h={120} n={3} />}
        {rows.map(({ l, m, loading }, i) => <LiveCard key={l.key} league={l} m={m} loading={loading} prob={probs[i]} />)}
      </div>
    </>
  );
}

function LiveCard({ league, m, loading, prob }: { league: League; m: Matchup | null | undefined; loading: boolean; prob: number | null }) {
  if (loading) return <Skeleton h={120} />;
  return (
    <Link to={`/league/${encodeURIComponent(league.key)}`} className="card live-card" style={{ display: "block", color: "inherit", marginBottom: 10 }}>
      <div className="row" style={{ marginBottom: 8 }}>
        <PlatformBadge platform={league.platform} />
        <b className="grow ellipsis">{league.name}</b>
        {prob != null && <span className="prob" style={{ color: prob >= 0.5 ? "var(--good)" : "var(--danger)" }}>{Math.round(prob * 100)}%</span>}
      </div>
      {!m || !m.opponent ? (
        <div className="muted small">{m ? "Bye week" : "No matchup data"}</div>
      ) : (
        <>
          <div className="row">
            <div className="grow" style={{ minWidth: 0 }}>
              <div className="ellipsis small">{m.me.team.name}</div>
              <div className="score num" style={{ color: m.me.points >= m.opponent.points ? "var(--good)" : undefined }}>{m.me.points.toFixed(2)}</div>
              {m.me.projected != null && <div className="small muted">proj {m.me.projected.toFixed(1)}</div>}
            </div>
            <div className="grow" style={{ minWidth: 0, textAlign: "right" }}>
              <div className="ellipsis small">{m.opponent.team.name}</div>
              <div className="score num" style={{ color: m.opponent.points > m.me.points ? "var(--danger)" : undefined }}>{m.opponent.points.toFixed(2)}</div>
              {m.opponent.projected != null && <div className="small muted">proj {m.opponent.projected.toFixed(1)}</div>}
            </div>
          </div>
          {prob != null && (
            <div className="meter" style={{ marginTop: 10 }}>
              <i style={{ width: `${prob * 100}%`, background: "var(--good)" }} />
              <i style={{ flex: 1, background: "var(--danger)", opacity: 0.55 }} />
            </div>
          )}
        </>
      )}
    </Link>
  );
}
