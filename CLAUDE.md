# Fitz TV

Sports-only IPTV client. Android TV app (`:app`), phone app (`:mobile`), Windows app (`:desktop`), shared logic (`:core`).
Every user-facing change ships in all three apps.

## Build
- `./gradlew assembleDebug` — both APKs (debug-signed)
- `./gradlew testDebugUnitTest` — JVM unit tests (`core/src/test`)
- `./gradlew lintDebug` — must pass (Stop hook runs it)
- `./gradlew -PdesktopOnly=true :desktop:test :desktop:run` — Windows app without an Android SDK (also runs the `core/src/test` tests on the JVM); `:desktop:packageMsi` needs Windows + WiX (CI does it). Android CI passes `-PandroidOnly=true`.
- Needs an Android SDK: `$ANDROID_HOME` or `sdk.dir` in `local.properties` (untracked).
- CI (`.github/workflows/build.yml`): unit tests + `assembleRelease` on every push; publishes a
  GitHub release with `FitzTV-TV.apk` / `FitzTV-Mobile.apk`, then the `windows` job attaches
  `FitzTV-Windows.msi` (libVLC bundled) to the same release (in-app updater + permanent link).

## Architecture
- `core/src/main/java/com/fitztv/sources` — Xtream (`XtreamClient`), M3U (`M3uParser`), XMLTV (`XmltvParser`)
- `core/src/main/java/com/fitztv/data` — parsing results and state: `Repository` (channels, games,
  EPG, refresh passes), `GameMatcher` (game→channel + verdicts), `StreamProbe`/`TsSniffer`
  (quality), `LiveInference`, `ChannelHints`, `Settings`
- `core/src/main/java/com/fitztv/sports` — schedules (ESPN, TheSportsDB), live stats, leagues
- `core/src/main/java/com/fitztv/player` — streaming: `PlaybackController` (watchdog, failover,
  upgrade), `ExoEngine` (Media3), `VlcEngine` (fallback), `Multiview`, `PlaybackQueue`
- `desktop/src/main/kotlin/com/fitztv/desktop` — Windows app (Compose Desktop, Material 3, mouse + keyboard). It compiles
  `:core` sources directly (Gradle `syncCore`, minus the Android-only files listed in `desktop/build.gradle.kts`), so
  `:core` logic is shared, not copied. Android-only pieces have desktop replacements under the same package names
  (`FitzApp`, `VlcEngine`/`ExoEngine` on vlcj, `PlaybackQueue`, `Multiview`, `Alerts`, `Updater`, `Recorder`), and
  `desktop/src/main/kotlin/android/**` holds tiny stand-ins (`Context`, `Intent`, `Uri`, `Xml`, …) so shared files compile
  unchanged. Changing a shared `:core` file that uses a new Android API means adding the stand-in (or moving it behind
  an interface). Data lives in `%APPDATA%\FitzTV`.
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
- Progress and verification checks: `FEATURE_PROGRESS.md`.
