import { useQueries } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { api, type League } from "../api";
import { lineupWarnings } from "../../shared/lineup";
import { useLeagues } from "../hooks";
import { PlatformBadge, Skeleton, scoringLabel } from "../components/bits";
import { IconRefresh, IconWarn } from "../components/Icons";
import { useQueryClient } from "@tanstack/react-query";


type Filter = "all" | "nfl" | "nba";

export function Home() {
  const { data: leagues, isLoading, error, isFetching } = useLeagues();
  const [filter, setFilter] = useState<Filter>("all");
  const qc = useQueryClient();
  const visible = (leagues ?? []).filter((l) => !l.hidden && (filter === "all" || l.sport === filter));

  const rosters = useQueries({
    queries: visible.map((l) => ({ queryKey: ["roster", l.key, "current"], queryFn: () => api.roster(l.key), staleTime: 60_000, retry: 1 })),
  });
  const matchups = useQueries({
    queries: visible.map((l) => ({ queryKey: ["matchup", l.key, "current"], queryFn: () => api.matchup(l.key), staleTime: 60_000, retry: 0 })),
  });
  const issues = visible.map((l, i) => ({ league: l, warnings: rosters[i].data ? lineupWarnings(rosters[i].data!.starters) : [] }));
  const totalIssues = issues.reduce((n, x) => n + x.warnings.length, 0);

  return (
    <>
      <header className="topbar">
        <h1 className="grow">My Teams</h1>
        <button
          className="iconbtn"
          aria-label="Refresh"
          onClick={async () => {
            await api.leagues(true);
            qc.invalidateQueries();
          }}
          style={{ animation: isFetching ? "spin 1s linear infinite" : undefined }}
        >
          <IconRefresh />
        </button>
      </header>
      <div className="page">
        <div className="chips" style={{ marginBottom: 14 }}>
          {(["all", "nfl", "nba"] as Filter[]).map((f) => (
            <button key={f} className={`chip ${filter === f ? "on" : ""}`} onClick={() => setFilter(f)}>
              {f === "all" ? "All" : f === "nfl" ? "🏈 Football" : "🏀 Basketball"}
            </button>
          ))}
        </div>

        {error && !leagues && <div className="banner err">{(error as Error).message}</div>}
        {isLoading && !leagues && <Skeleton h={78} n={4} />}
        {leagues && !leagues.length && (
          <div className="empty">
            <p>No leagues yet.</p>
            <Link className="btn primary" to="/accounts">Link Sleeper, ESPN or Yahoo</Link>
          </div>
        )}

        {visible.length > 0 && (
          <div className={`banner ${totalIssues ? "warn" : "info"}`} style={{ marginBottom: 12 }}>
            {totalIssues ? <IconWarn /> : <span>✅</span>}
            <div>
              {totalIssues
                ? `${totalIssues} lineup issue${totalIssues > 1 ? "s" : ""} across ${issues.filter((x) => x.warnings.length).length} league(s)`
                : rosters.every((r) => r.data)
                  ? "Every lineup is set"
                  : "Checking lineups…"}
            </div>
          </div>
        )}

        {visible.map((l, i) => (
          <TeamCard key={l.key} league={l} warnings={issues[i].warnings} matchup={matchups[i].data} />
        ))}
      </div>
    </>
  );
}

function TeamCard({ league: l, warnings, matchup }: { league: League; warnings: string[]; matchup: Awaited<ReturnType<typeof api.matchup>> | undefined }) {
  const t = l.myTeam;
  return (
    <Link to={`/league/${encodeURIComponent(l.key)}`} className="card team-card" style={{ color: "inherit", display: "flex", marginBottom: 10 }}>
      <div className="avatar">{l.avatar ? <img src={l.avatar} alt="" loading="lazy" /> : l.sport === "nba" ? "🏀" : "🏈"}</div>
      <div className="grow">
        <div className="row" style={{ gap: 6 }}>
          <PlatformBadge platform={l.platform} />
          <b className="ellipsis">{l.name}</b>
        </div>
        <div className="small muted ellipsis" style={{ marginTop: 3 }}>
          {t ? `${t.name} · ${t.wins}-${t.losses}${t.ties ? `-${t.ties}` : ""}` : "No team"}
          {l.standing ? ` · ${ordinal(l.standing)}` : ""}
        </div>
        <div className="row" style={{ gap: 6, marginTop: 6 }}>
          {l.sport === "nfl" && <span className="pill">{scoringLabel(l.scoring)}</span>}
          {l.dynasty && <span className="pill">Dynasty</span>}
          {warnings.length > 0 && <span className="pill warn">{warnings.length} issue{warnings.length > 1 ? "s" : ""}</span>}
        </div>
      </div>
      {matchup?.opponent && (
        <div style={{ textAlign: "right" }}>
          <div className="score num" style={{ color: matchup.me.points >= matchup.opponent.points ? "var(--good)" : "var(--text)" }}>{matchup.me.points.toFixed(1)}</div>
          <div className="small muted num">{matchup.opponent.points.toFixed(1)}</div>
          <div className="small muted ellipsis" style={{ maxWidth: 90 }}>vs {matchup.opponent.team.name}</div>
        </div>
      )}
    </Link>
  );
}

export function ordinal(n: number): string {
  const teen = n % 100 >= 11 && n % 100 <= 13;
  const suffix = teen ? "th" : ({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th";
  return `${n}${suffix}`;
}
