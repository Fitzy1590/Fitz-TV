# Feature progress

Audit: 2026-11-09, build 45. Modules: `:core` (data/player), `:app` (TV), `:mobile` (phone).
Status key: `[x]` done and verified in CI · `[~]` done, needs device verification · `[ ]` pending.

## 1. Implemented

### Playlist parsing (`core/…/sources`, `core/…/data`)
- [x] Xtream Codes `player_api` live streams, sports filter, HLS/TS format choice (`XtreamClient`)
  - Verify: `./gradlew :core:testReleaseUnitTest` passes; adding an Xtream source in Settings lists channels.
- [x] M3U parsing with catch-up attributes (`M3uParser`, `Catchup`)
  - Verify: an M3U URL loads channels; catch-up channels open archive playback.
- [x] XMLTV guide, filtered to sports channel ids (`XmltvParser`)
  - Verify: the channel guide grid shows listings for channels that have an `epgId`.
- [x] Game→channel matching using names and the guide at kick-off, with verdicts (`GameMatcher`)
  - Verify: unit test `SameTeamTest` passes; a live game's stream list marks streams "Confirmed".

### Streaming (`core/…/player`)
- [x] Media3 ExoPlayer: hardware decoders first, async queueing, OkHttp, tuned load control (`ExoEngine`)
- [x] VLC fallback engine (`VlcEngine`); Auto engine switching
- [x] `PlaybackController`: watchdog, step-down, failover through ranked streams, HLS↔TS swap
- [x] Stream ranking: SD last, priority categories, language, resolution, fps, measured quality
- [~] Seamless upgrade to a sharper stream; channel memory (`ChannelHints`); tunnelled playback (experimental)
  - Verify: `./gradlew assembleRelease` passes; on the Shield, "Stream n/N" plus the resolution pill
    match the source; a failover happens within 10 s of a dead stream.

### TV UI (`app/…/ui`, `androidx.tv.material3`)
- [x] Screens: Home, Guide (games and channel grid), Replays, Channels, Search, Settings
- [x] Dialogs: stream picker, favourites, priority categories, QR setup, replays, updates
- [x] Player overlay: transport controls, Live now panel (◀), streams panel (▶), live stats, multiview
- [x] Live-scores screensaver (DreamService); Android TV home row
  - Verify: `./gradlew :app:compileReleaseKotlin` passes; every screen reachable by D-pad from Home.

## 2. Active work (built, needs device verification)
- [~] Kayo-style home: hero carousel, matchup posters, live channels, league logos (TV + phone)
  - Verify: hero advances every 8 s, stops on focus; ◀ ▶ at the button ends change game; posters show team colours/logos or the fixture thumbnail.
- [~] Fixtures screen + league hubs (ladder from ESPN standings)
  - Verify: day strip changes day; Join live / From start / Full replay work; ladder sorted by points / win %.
- [~] Player: full-width controls panel with scorebug; audio sync (per channel, ±20 ms steps)
  - Verify: offset persists after reopening the channel; no effect while tunnelled playback is on (expected).
- [~] Stream ranking: highest resolution first; abbreviations/cities/short names; other-opponent rule-out
  - Verify: `MatcherTest`, `QualityTagTest` pass; stream 1 is the highest tagged/measured resolution.
- [~] Fold two-pane Fixtures (≥700 dp)
  - Verify: unfolded, selecting a card fills the right pane; folded, single list.
- [~] Box score: phone stat columns slide left/right under a fixed player column (SofaScore style); TV shows key columns only
  - Verify: on the phone, swipe a box score sideways; header and rows move together and names read in full.
- [~] Home polish: channel logos on a light plate, team logos/flags on a soft plate, no-teams fixtures show league + title; TV hero focus scrolls list to top
  - Verify: no cropped/stretched channel logos; scroll down then back up on the Shield and the hero is fully visible.
- [~] Style: Esports teal default (Violet night selectable), Saira Condensed + Saira bundled (OFL, `third_party/`), uppercase condensed titles
  - Verify: Settings › Colour theme lists Esports teal first; scores, titles and section heads use the condensed face; existing amber users move to teal once.
- [~] Channel logos from tv-logo/tv-logos by name (provider logo as fallback); game previews are always the team-colour split
  - Verify: Live channels tiles show clean logos (e.g. SKY ESPN 2); no fixture banner images; similar team colours (Pacers v Mavericks) stay distinct.
- [~] Layout votes applied: featured trio on Home, dense Fixtures rows, game page with tabs (Streams / Box score / Replays), both apps
  - Verify: Home shows one big game plus two smaller; a Fixtures row opens the game page; tabs switch without closing; Back closes.
- [~] Replay library: list, newest game first, chosen sport only, clean titles with dates; replay lookup no longer hangs or matches other teams
  - Verify: Replays screen lists dated rows in reverse order; Soccer shows no American football; Aston Villa v Man Utd lookup finishes within seconds.
- [~] Live now row on Home (both apps); remembered stream first (HD+); live cards pre-rank streams on highlight (TV)
  - Verify: Live now sits under the featured trio; highlighting a live card for a moment makes select-to-watch start without the "Finding the best stream" pause; moving across cards stays smooth.
- [~] Category learning: watch time per league teaches which provider category ranks first (after resolution, priority categories); 30-day half-life
  - Verify: watch NBA games for a few sessions on one category; its streams then lead among same-resolution streams.
- [~] Auto update on launch: downloads the new build and opens Android's installer; 3 tries, then skipped quietly (Settings › App updates toggle)
  - Verify: with a newer release out, launching shows a download progress box, then the system install confirmation; with the network off the app just opens normally.
- [~] TV picture-in-picture: only from the PiP button in the controls (Home no longer starts it); Mute / Pause actions; the corner window takes no remote keys
  - Verify: allow Fitz TV in Settings › Apps › Special app access › Picture-in-picture; play a game, press Home, open YouTube: the game stays in the corner; closing the corner window stops the stream.
- [~] Up-vote streams: Prefer button in the player (both apps) marks a working HD stream; it ranks first among HD streams and teaches the league's category; Settings lists / clears them
  - Verify: press Prefer on a 1080p stream; next game on that league lists it first; Settings › Preferred channels shows it.
- [~] Multiview: menu order (Add another game, Move, Change game, then the rest); same loudness boost as single view; windows without sound decode no audio
  - Verify: volume matches single view; a non-active window reconnecting no longer changes the loudness.
- [~] Self-healing streams: 3 reconnects in 2.5 min escalate (other User-Agent, buffer 1.5x/2x/3x, longer re-buffer wait); a working fix is remembered per channel; Settings switch
  - Verify: a flaky stream shows "Unstable stream – steadier connection"; the same channel starts steadier next time.
- [~] Stream fixing (voted): Wi-Fi performance lock; connection-limit guard (multiview warning from the plan's max_connections, no background guide refresh while playing, "provider refusing" detection); IPv4 + fresh connections at step 2+; remembered format/engine per channel; failover checks the next streams in parallel; stall-by-time-of-week memory; Settings › Test connection
  - Verify: multiview with more windows than the plan allows shows the warning; a channel that needed VLC / the other format starts that way next time; Test connection prints speed and a buffer suggestion.
- [~] Score cards (voted): a card on every scoring play with the scorer, points and current stats; flame + ON FIRE when hot (3 makes in a row or 10+ pts in 4 min); delay setting; NBA/NBL, NFL, soccer; only the window with sound in multiview
  - Verify: during a live NBA game a card appears bottom-left after each basket (after your delay); Settings › Score cards toggles it and changes the delay.
- [x] Release clean-up: numbered APKs dropped (build 45+)
  - Verify: release has 2 APK assets; Check for updates works.

## 2b. Active UI tasks (2026-11-12 modernize; spec in `SPEC.md`)
Layout vote: [ ] A channel grid + preview · [ ] B slide-over guide + score overlay · [x] C game-first Live now + verdict strip
- [~] Remove animations (done 2026-11-12, compile not yet verified: Gradle 429 on Maven in sandbox): `PlayerActivity.kt:472,520,567,606`, `ScoreAnimation.kt:76,116`, `TeamBackdrop.kt:55`, `HomeScreen.kt:135`, `LiveStatsPanel.kt:110`
  - Verify: `grep -rnE "AnimatedVisibility|Crossfade|animateScroll|infiniteTransition|slideIn|fadeIn" app/src` returns nothing; panels appear instantly.
- [x] Fixtures ladder row focus: already has a 2dp accent border, so it meets the rule (no change)
- [~] Verdict strip: TV player info bar (verdict pill + stream chips 1..8, current highlighted, ▲▼ switch); phone player verdict pill (`core/.../VerdictLabel.kt`). Not compiled (offline Gradle). Game page: Streams tab rows show "n · verdict" (both apps).
  - Verify: during a game stream the info bar shows Confirmed / Unverified / Other event and chips; ▲▼ moves the highlight.
- [x] Home: Live now row, then "Coming up" (starting soon + later today) already exist; no change needed

- [~] Phone instant navigation: player side panel, controls, score card, flame and team backdrop use no animation (2026-11-12)
  - Verify: `grep -rnE "fadeIn|slideIn|Crossfade|animate[A-Z]" mobile/src app/src` returns nothing; panels open instantly.
- [~] Black screen: no picture 6 s after playback starts (8 s for 4K) fails over to the next stream; that stream is ranked last for the rest of the app session (`StreamProbe.markBlackScreen`)
  - Verify: play a game whose top stream shows black; within about 6 s it switches, and reopening the game lists that stream last. Black frames that do render are not detected.

## 3. Backlog

### D-pad focus (`FocusRequester`)
- [~] `Modifier.focusRestorer()` on Home / Fixtures rows (`focusRestorerCompat`); remaining lists pending
  - Verify: leave a row and come back; the same card has focus.
- [~] Initial focus on every screen and dialog: LeagueHub and the game page Stats tab now request focus; League grid has `focusRestorer`
  - Verify: opening each screen/dialog without input shows a focused element.
- [ ] Focus-trap audit: sport chips ↔ rows ↔ My teams rail ↔ top nav
  - Verify: from any element, ↑ reaches the top nav and Back returns, with no dead ends.

### 10-foot layouts (`androidx.tv.material3`)
- [x] `material3` on TV is limited to text fields and the progress spinner (no tv-material equivalent in 1.0.0)
  - Verify: `grep -r "androidx.compose.material3" app/src` returns only text fields.
- [~] Overscan-safe padding: 48dp horizontal already everywhere; top bar and alert banner now 27dp from the top
  - Verify: no clipping on a TV with overscan on; screenshot at 1920×1080.
- [~] Minimum text size 12sp done (all <12sp raised; box-score columns widened); focus feedback = scale ≥1.03× or ≥2dp border on all interactive elements
  - Verify: `grep -rn "fontSize = 1[01].sp" app/src` returns nothing; manual pass over every screen.

### Channel guide grid navigation
- [ ] D-pad moves by programme cell; ◀ at the left edge pages earlier, ▶ at the right edge pages later
- [ ] Jump to now (key or button); visible current-time line; the focused row stays in view
  - Verify: navigate 50 rows × 6 h without losing focus; frame time <16 ms (Layout Inspector / `gfxinfo`).

### Channel switching
- [x] ▲▼ / CH± zap within the queue (`PlayerActivity.zap`), ◀ Live now, ▶ streams
- [ ] Number-key direct entry; last-channel (Back-Back) toggle
- [ ] Zap latency: first frame <1.5 s on a warm connection
  - Verify: measure over 10 switches on the Shield with logcat timestamps (`onRenderedFirstFrame`).

## Verification baseline (every change)
- [x] `./gradlew testDebugUnitTest` locally; `:core:testReleaseUnitTest` in CI before building
- [x] `./gradlew assembleRelease` (CI)
- [x] `./gradlew lintDebug` — passes (baseline 2026-11-09); enforced by the Stop hook in `.claude/settings.json`
- [ ] UI tests: none yet (no `app/src/androidTest`); add Compose UI tests for D-pad flows
