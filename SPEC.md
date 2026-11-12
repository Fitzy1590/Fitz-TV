# Fitz TV UI spec

Reviewer (`.claude/agents/reviewer.md`) audits changed TV composables against this file.

## Layout vote (2026-11-12)
- [ ] Option A (channel grid + live preview)
- [ ] Option B (slide-over guide + score overlay)
- [x] Option C (game-first Live now + verdict strip)

Only C is enabled. A and B are out of scope; do not build them.

## Option C requirements
1. Home: game-first rows. "Live now" then "Starting soon"; poster = team-colour diagonal, score, clock, LIVE badge. OK joins the best matched channel (`GameMatcher`).
2. Verdict strip (player overlay + game page): best stream verdict ("Confirmed · 1080p"), numbered next-best chips (Stream 1..N), ◀ ▶ switches stream, one press fails over. Data from `GameMatcher` verdicts and `PlaybackController` ranking; no new matching logic.
3. Existing panels stay: ◀ Live now, ▶ streams, ▲▼ zap, score cards.

## Instant navigation (applies to all TV UI)
- No enter/exit transitions, no slide/fade/crossfade, no animated scroll, no infinite/pulse animations.
  Use `EnterTransition.None` / `ExitTransition.None`, `snap()`, `scrollToItem` / `scrollTo`.
- Focus moves instantly on D-pad. Focus feedback stays: scale ≥ 1.03× or accent border ≥ 2dp.
- `Modifier.focusRestorer()` on every list container; `FocusRequester` for initial focus.

## Violations fixed 2026-11-12 (animations replaced with None/instant scroll)
- `PlayerActivity.kt:472,520,567,606` AnimatedVisibility fade/slide
- `ScoreAnimation.kt:76,116` slide-in, infinite flame pulse
- `TeamBackdrop.kt:55` Crossfade 1200 ms
- `HomeScreen.kt:135` `animateScrollToItem`; `LiveStatsPanel.kt:110` `animateScrollTo`
