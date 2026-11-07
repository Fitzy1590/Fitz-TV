import { matchupTone, useDefense } from "../hooks";
import { useState } from "react";

const CDN = "https://sleepercdn.com";

export function headshotUrl(sport: string, id: string): string | null {
  if (/^[A-Z]{2,3}$/.test(id)) return `${CDN}/images/team_logos/${sport}/${id.toLowerCase()}.png`;
  if (!/^\d+$/.test(id)) return null; // not a Sleeper id (unmatched ESPN/Yahoo player)
  return `${CDN}/content/${sport}/players/thumb/${id}.jpg`;
}

export const teamLogoUrl = (sport: string, team: string | null | undefined) => (team ? `${CDN}/images/team_logos/${sport}/${team.toLowerCase()}.png` : null);

function initials(name: string) {
  const parts = name.replace(/\b(Jr|Sr|II|III|IV)\.?$/i, "").trim().split(/\s+/);
  return ((parts[0]?.[0] ?? "") + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
}

export function PlayerHead({ id, name, sport = "nfl", size = 44 }: { id: string; name: string; sport?: string; size?: number }) {
  const [failed, setFailed] = useState(false);
  const url = headshotUrl(sport, id);
  const isDef = /^[A-Z]{2,3}$/.test(id);
  return (
    <span className={`head${isDef ? " def" : ""}`} style={{ width: size, height: size, fontSize: size * 0.34 }}>
      {url && !failed ? <img src={url} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} /> : initials(name)}
    </span>
  );
}

const SLOT_LABEL: Record<string, string> = { SUPER_FLEX: "SF", WRRB: "W/R", REC: "W/T", IDP_FLEX: "IDP", UTIL: "UTIL" };
export function PosBadge({ slot }: { slot: string }) {
  const cls = slot.replace("/", "");
  return <span className={`pos ${cls}`}>{SLOT_LABEL[slot] ?? slot}</span>;
}

export function PlatformBadge({ platform }: { platform: string }) {
  return <span className={`platform ${platform}`}>{platform.toUpperCase()}</span>;
}

export function Injury({ status }: { status?: string | null }) {
  if (!status) return null;
  const s = status.toUpperCase();
  const short = s === "QUESTIONABLE" ? "Q" : s === "DOUBTFUL" ? "D" : s === "OUT" ? "OUT" : s === "INJURED RESERVE" ? "IR" : s.length > 4 ? s.slice(0, 3) : s;
  return <span className={`inj ${short}`}>{short}</span>;
}

export function scoringLabel(s: { base: string; superflex: boolean; tePremium: number }) {
  return [s.base === "HALF" ? "Half PPR" : s.base === "STD" ? "Standard" : "PPR", s.superflex && "Superflex", s.tePremium > 0 && `TEP +${s.tePremium}`].filter(Boolean).join(" · ");
}

export function Skeleton({ h = 64, n = 1 }: { h?: number; n?: number }) {
  return (
    <>
      {Array.from({ length: n }, (_, i) => (
        <div key={i} className="skeleton" style={{ height: h, marginBottom: 8 }} />
      ))}
    </>
  );
}

/** Opponent with a defense-vs-position colour (green = soft matchup). */
export function Opp({ opp, pos }: { opp: string | null | undefined; pos: string | undefined }) {
  const d = useDefense(!!opp);
  if (!opp) return null;
  const def = opp.replace("@", "");
  const r = pos ? d.data?.[def]?.[pos] : undefined;
  const tone = matchupTone(r?.rank);
  return (
    <span className={`mchip ${tone ?? ""}`} title={r ? `${def} allows ${r.allowed} PPR pts/game to ${pos}s: ${ordinalShort(r.rank)} most of 32 defenses` : undefined}>
      {opp}
      {r && <sup>{ordinalShort(r.rank)}</sup>}
    </span>
  );
}

function ordinalShort(n: number) {
  const t = n % 100 >= 11 && n % 100 <= 13;
  return `${n}${t ? "th" : ({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`;
}
