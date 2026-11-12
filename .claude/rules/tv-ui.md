---
paths: ["app/src/main/java/**/*.kt"]
---

# TV UI rules (10-foot)

- Components: Jetpack Compose TV 1.0.0 — `androidx.tv.material3.*` (`Card`, `Button`, `Surface`)
  inside the standard `androidx.compose.foundation.lazy.LazyRow` / `LazyColumn`. Do not use
  `TvLazyRow` / `TvLazyColumn` (deprecated). `androidx.compose.material3` only where tv-material
  has no equivalent (text fields).
- Focus feedback: every focusable element shows distinct focus with scale ≥ 1.03× or an accent
  border ≥ 2dp (`NeonSurface` does both). Focus is driven via `FocusRequester`. No glow.
- D-pad memory: wrap channel/category lists (rows/columns of focusables) in
  `Modifier.focusRestorer()` so returning restores the last focused item and scroll position;
  no focus traps (↑ reaches top nav, Back exits).
- Initial focus: each screen/dialog requests focus on its first meaningful element
  (`LaunchedEffect { runCatching { requester.requestFocus() } }`).
- Instant navigation: no transitions or animated scrolling. Use `EnterTransition.None`,
  `ExitTransition.None`, `snap()`, `scrollToItem`. Focus shifts immediately. See `SPEC.md`.
- Overscan-safe: 48dp horizontal page padding; text ≥ 12sp; tabular figures for scores/times.
