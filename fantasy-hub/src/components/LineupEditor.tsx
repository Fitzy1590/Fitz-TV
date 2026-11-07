import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type League } from "../api";
import { canPlay, lineupWarnings, optimizeLineup } from "../../shared/lineup";
import type { Player, Roster, RosterSpot } from "../../shared/types";
import { Injury, PlayerHead, PosBadge } from "./bits";
import { IconBolt, IconWarn } from "./Icons";
import { usePlayerSheet } from "./PlayerSheet";

type Sel = { kind: "starter"; index: number } | { kind: "bench"; id: string } | null;

export function LineupEditor({ league, roster, week }: { league: League; roster: Roster; week: number }) {
  const qc = useQueryClient();
  const openPlayer = usePlayerSheet();
  const [starters, setStarters] = useState<RosterSpot[]>(roster.starters);
  const [sel, setSel] = useState<Sel>(null);
  const [optBy, setOptBy] = useState<"ecr" | "proj">("ecr");
  const [msg, setMsg] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    setStarters(roster.starters);
    setSel(null);
  }, [roster]);

  const pool = useMemo(() => {
    const all = [...roster.starters.map((s) => s.player), ...roster.bench].filter((p): p is Player => !!p);
    return all.filter((p, i) => all.findIndex((q) => q.id === p.id) === i);
  }, [roster]);
  const startingIds = new Set(starters.map((s) => s.player?.id).filter(Boolean));
  const bench = pool.filter((p) => !startingIds.has(p.id));
  const dirty = starters.some((s, i) => s.player?.id !== roster.starters[i]?.player?.id);
  const warnings = lineupWarnings(starters);
  const projTotal = starters.reduce((sum, s) => sum + (s.player?.proj ?? 0), 0);

  // FantasyPros weekly ECR for the optimizer (league's own points system).
  const ecr = useQuery({
    queryKey: ["rankings", "fantasypros", "weekly", league.key, week],
    queryFn: () => api.rankings({ league: league.key, type: "weekly", pos: "ALL", week }),
    enabled: league.sport === "nfl",
    staleTime: 30 * 60_000,
    retry: false,
  });

  const save = useMutation({
    mutationFn: () => api.saveLineup(league.key, { week, starters: starters.map((s) => s.player?.platformId ?? null) }),
    onSuccess: (r) => {
      qc.setQueryData(["roster", league.key, week === league.currentWeek ? "current" : week], r);
      qc.invalidateQueries({ queryKey: ["roster", league.key] });
      setMsg({ kind: "ok", text: `Lineup saved to ${league.platform.toUpperCase()}` });
    },
    onError: (e: Error) => setMsg({ kind: "err", text: e.message }),
  });

  useEffect(() => {
    if (!msg) return;
    const t = setTimeout(() => setMsg(null), 4000);
    return () => clearTimeout(t);
  }, [msg]);

  const selectedPlayer = sel?.kind === "starter" ? starters[sel.index]?.player : sel?.kind === "bench" ? bench.find((p) => p.id === sel.id) : null;
  const selectedSlot = sel?.kind === "starter" ? starters[sel.index].slot : null;

  /** Can the current selection be swapped with this target? */
  function canSwapWith(t: Exclude<Sel, null>): boolean {
    if (!sel || !roster.canEdit) return false;
    if (sel.kind === "starter" && t.kind === "starter") {
      if (sel.index === t.index) return false;
      const a = starters[sel.index], b = starters[t.index];
      if (b.player?.locked) return false;
      return (!a.player || canPlay(a.player, b.slot)) && (!b.player || canPlay(b.player, a.slot));
    }
    if (sel.kind === "starter" && t.kind === "bench") {
      const p = bench.find((x) => x.id === t.id);
      return !!p && !p.locked && canPlay(p, starters[sel.index].slot);
    }
    if (sel.kind === "bench" && t.kind === "starter") {
      const s = starters[t.index];
      return !!selectedPlayer && !s.player?.locked && canPlay(selectedPlayer, s.slot);
    }
    return false;
  }

  function tap(t: Exclude<Sel, null>, p: Player | null) {
    if (!roster.canEdit) {
      if (p) openPlayer({ id: p.id, sport: league.sport, name: p.name });
      return;
    }
    if (sel && canSwapWith(t)) {
      const next = starters.map((s) => ({ ...s }));
      if (sel.kind === "starter" && t.kind === "starter") {
        const tmp = next[sel.index].player;
        next[sel.index].player = next[t.index].player;
        next[t.index].player = tmp;
      } else if (sel.kind === "starter" && t.kind === "bench") {
        next[sel.index].player = bench.find((x) => x.id === t.id)!;
      } else if (sel.kind === "bench" && t.kind === "starter") {
        next[t.index].player = selectedPlayer!;
      }
      setStarters(next);
      setSel(null);
      return;
    }
    const same = sel && t.kind === sel.kind && (t.kind === "starter" ? sel.kind === "starter" && sel.index === t.index : sel.kind === "bench" && sel.id === t.id);
    if (same || p?.locked) return setSel(null);
    setSel(t);
  }

  function optimize() {
    const rank = new Map((ecr.data?.rows ?? []).filter((r) => r.id).map((r) => [r.id!, r.rank]));
    const value = (p: Player) => (optBy === "ecr" && rank.has(p.id) ? 10_000 - rank.get(p.id)! : p.proj ?? 0);
    setStarters(optimizeLineup(starters, bench, value));
    setSel(null);
  }

  const rowState = (t: Exclude<Sel, null>, p: Player | null) => {
    const isSel = sel && ((t.kind === "starter" && sel.kind === "starter" && sel.index === t.index) || (t.kind === "bench" && sel.kind === "bench" && sel.id === t.id));
    if (isSel) return "selected";
    if (sel) return canSwapWith(t) ? "target" : "dim";
    return p?.locked ? "locked" : "";
  };

  return (
    <div>
      <div className="row" style={{ marginBottom: 10 }}>
        <div className="grow">
          <div className="caps muted">Projected</div>
          <div className="score num">{projTotal.toFixed(1)}</div>
        </div>
        {roster.canEdit && (
          <>
            {league.sport === "nfl" && (
              <select value={optBy} onChange={(e) => setOptBy(e.target.value as "ecr" | "proj")} style={{ width: "auto", padding: "8px 10px", borderRadius: 999 }} aria-label="Optimize by">
                <option value="ecr">FantasyPros ECR</option>
                <option value="proj">Projections</option>
              </select>
            )}
            <button className="btn primary" onClick={optimize}><IconBolt /> Optimize</button>
          </>
        )}
      </div>

      {roster.editNote && <div className="banner info" style={{ marginBottom: 10 }}>{roster.editNote}</div>}
      {warnings.length > 0 && (
        <div className="banner warn" style={{ marginBottom: 10 }}>
          <IconWarn />
          <div>{warnings.join(" · ")}</div>
        </div>
      )}
      {selectedPlayer && <div className="small muted" style={{ marginBottom: 8 }}>Moving <b style={{ color: "var(--text)" }}>{selectedPlayer.name}</b>{selectedSlot ? ` from ${selectedSlot}` : ""}: tap a highlighted spot.</div>}

      <div className="lineup">
        {starters.map((s, i) => (
          <SlotRow key={i} slot={s.slot} p={s.player} sport={league.sport} state={rowState({ kind: "starter", index: i }, s.player)} onTap={() => tap({ kind: "starter", index: i }, s.player)} onInfo={openPlayer} />
        ))}
      </div>

      <div className="section-title caps">Bench</div>
      <div className="lineup">
        {bench.map((p) => (
          <SlotRow key={p.id} slot="BN" p={p} sport={league.sport} state={rowState({ kind: "bench", id: p.id }, p)} onTap={() => tap({ kind: "bench", id: p.id }, p)} onInfo={openPlayer} />
        ))}
        {!bench.length && <div className="muted small">Nobody on the bench.</div>}
      </div>

      {(roster.reserve.length > 0 || roster.taxi.length > 0) && (
        <>
          <div className="section-title caps">Reserve / Taxi</div>
          <div className="lineup">
            {roster.reserve.map((p) => <SlotRow key={p.id} slot="IR" p={p} sport={league.sport} state="" onTap={() => openPlayer({ id: p.id, sport: league.sport, name: p.name })} onInfo={openPlayer} />)}
            {roster.taxi.map((p) => <SlotRow key={p.id} slot="TAXI" p={p} sport={league.sport} state="" onTap={() => openPlayer({ id: p.id, sport: league.sport, name: p.name })} onInfo={openPlayer} />)}
          </div>
        </>
      )}

      {(dirty || msg) && (
        <div className="savebar">
          <div>
            {msg ? (
              <div className="grow" style={{ color: msg.kind === "ok" ? "var(--good)" : "var(--danger)", fontWeight: 700, padding: "4px 6px" }}>{msg.text}</div>
            ) : (
              <>
                <div className="grow small"><b>Unsaved lineup</b><div className="muted">{warnings.length ? `${warnings.length} issue(s)` : "Looks good"}</div></div>
                <button className="btn ghost" onClick={() => setStarters(roster.starters)} disabled={save.isPending}>Reset</button>
                <button className="btn primary" onClick={() => save.mutate()} disabled={save.isPending}>{save.isPending ? "Saving…" : `Save to ${league.platform === "sleeper" ? "Sleeper" : league.platform === "espn" ? "ESPN" : "Yahoo"}`}</button>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function SlotRow({ slot, p, sport, state, onTap, onInfo }: { slot: string; p: Player | null; sport: string; state: string; onTap: () => void; onInfo: (t: { id: string; sport: string; name?: string }) => void }) {
  return (
    <button className={`slot-row ${state}`} onClick={onTap}>
      <PosBadge slot={slot} />
      {p ? (
        <>
          <span onClick={(e) => { e.stopPropagation(); onInfo({ id: p.id, sport, name: p.name }); }} role="button" aria-label={`${p.name} profile`}>
            <PlayerHead id={p.id} name={p.name} sport={sport} size={42} />
          </span>
          <div className="grow">
            <div className="name ellipsis">{p.name}</div>
            <div className="meta">
              <span className={`postext ${p.positions[0]}`} style={{ fontWeight: 700 }}>{p.positions.join("/")}</span>
              <span>· {p.team ?? "FA"}</span>
              {p.bye ? <span className="inj">BYE</span> : p.opponent && <span>· {p.opponent}</span>}
              <Injury status={p.injury} />
            </div>
          </div>
          <div className="proj num">
            <b>{p.points != null ? p.points.toFixed(1) : p.proj != null ? p.proj.toFixed(1) : "–"}</b>
            <span className="small muted">{p.points != null ? "pts" : "proj"}</span>
          </div>
        </>
      ) : (
        <div className="grow muted">Empty</div>
      )}
    </button>
  );
}
