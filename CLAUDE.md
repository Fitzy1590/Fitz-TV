# Fitz TV

Sports-only IPTV client. Android TV app (`:app`), phone app (`:mobile`), shared logic (`:core`).
Every user-facing change ships in both apps.

## Build
- `./gradlew assembleDebug` — both APKs (debug-signed)
- `./gradlew testDebugUnitTest` — JVM unit tests (`core/src/test`)
- `./gradlew lintDebug` — must pass (Stop hook runs it)
- Needs an Android SDK: `$ANDROID_HOME` or `sdk.dir` in `local.properties` (untracked).
- CI (`.github/workflows/build.yml`): unit tests + `assembleRelease` on every push; publishes a
  GitHub release with `FitzTV-TV.apk` / `FitzTV-Mobile.apk` (in-app updater + permanent link).

## Architecture
- `core/src/main/java/com/fitztv/sources` — Xtream (`XtreamClient`), M3U (`M3uParser`), XMLTV (`XmltvParser`)
- `core/src/main/java/com/fitztv/data` — parsing results and state: `Repository` (channels, games,
  EPG, refresh passes), `GameMatcher` (game→channel + verdicts), `StreamProbe`/`TsSniffer`
  (quality), `LiveInference`, `ChannelHints`, `Settings`
- `core/src/main/java/com/fitztv/sports` — schedules (ESPN, TheSportsDB), live stats, leagues
- `core/src/main/java/com/fitztv/player` — streaming: `PlaybackController` (watchdog, failover,
  upgrade), `ExoEngine` (Media3), `VlcEngine` (fallback), `Multiview`, `PlaybackQueue`
- `app/src/main/java/com/fitztv/ui` — TV views (Compose for TV); `app/…/player` — TV player activities
- `mobile/src/main/java/com/fitztv/mobile/ui` — phone views (Material 3)

## Rules
- TV UI: Jetpack Compose TV 1.0.0 — `androidx.tv.material3.*` (`Card`, `Button`, `Surface`) with
  the standard `LazyRow` / `LazyColumn`. No `TvLazyRow` (deprecated tv-foundation).
- Focus feedback on every interactive element: scale ≥ 1.03× or accent border ≥ 2dp.
- Apply `Modifier.focusRestorer()` on parent list containers (rows/columns of focusables).
- Collect StateFlows lifecycle-aware: `repeatOnLifecycle` (views) / `collectAsStateWithLifecycle` (Compose).
- Never retain player instances across configuration changes.
- No analytics or logging; credentials stay on the device. Never commit keystores (release
  signing is generated in CI from the `FITZTV_SIGNING_PASSWORD` secret).
- Visual direction: style D "stadium poster" (Kayo-like) — hero carousel, 16:9 matchup posters
  (team-colour diagonal + logos, or TheSportsDB fixture thumbnail) with title/league underneath,
  LIVE/time badges, Fixtures day strip with Join live / From start. One theme accent (theme
  picker), line icons, no decorative glows. `.claude/skills/unslop` for cleanup passes.
- Instant navigation: no UI transitions, slides, fades, crossfades or animated scrolling
  (`EnterTransition.None` / `ExitTransition.None`, `snap()`); focus shifts instantly on D-pad.
- Layout (voted 2026-11-12): Option C only, game-first Live now + verdict strip. See `SPEC.md`.
  Run `.claude/agents/reviewer.md` (read-only) after TV UI edits.
- Progress and verification checks: `FEATURE_PROGRESS.md`.
