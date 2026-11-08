---
paths:
  - "app/src/main/java/**/*.kt"
---

# TV UI rules (10-foot)

- Components: Jetpack Compose TV 1.0.0 — `androidx.tv.material3.Card`, `Button`, `Surface`
  (`ClickableSurfaceDefaults` for colours, border, scale, glow) inside the standard
  `androidx.compose.foundation.lazy.LazyRow` / `LazyColumn`. Do not use `TvLazyRow` /
  `TvLazyColumn` (deprecated). `androidx.compose.material3` only where tv-material has no
  equivalent (text fields).
- Focus feedback: every interactive element shows focus with scale ≥ 1.03× or an accent
  border ≥ 2dp (`NeonSurface` does both: 1.03×, 2dp `Neon.Purple`). No glow.
- Initial focus: each screen/dialog requests focus on its first meaningful element via
  `FocusRequester` (`LaunchedEffect { runCatching { requester.requestFocus() } }`).
- Lists: parent `LazyRow` / `LazyColumn` of focusables gets `Modifier.focusRestorer()` so
  returning to a row restores the last focused item; no focus traps (↑ reaches top nav, Back exits).
- Overscan-safe: 48dp horizontal page padding; text ≥ 12sp; tabular figures for scores/times.
