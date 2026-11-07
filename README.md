# Fitz TV

A sports-only IPTV app for Android TV, built for the **NVIDIA Shield**, with a neon purple interface.

## Features

- **Sources:** Xtream Codes logins and M3U playlist URLs. You can add several. Only sports channels are kept; everything else is filtered out. Use **Set up from phone (QR)** in Settings to scan a code and type the details on your phone. That page is served by the Shield on your home network only.
- **Clean names:** provider clutter like `US| NBA 01: Rockets @ Lakers 7:30PM ET [FHD]` is shown as `Rockets vs Lakers`.
- **Internationals:** international cricket (Tests, ODIs, T20Is, ICC events), soccer friendlies, World Cup qualifiers, Asian Cup, World Cup and FIBA basketball appear in their sport tabs and the guide like any league (Nations League and European qualifiers when live).
- **Live now:** the first row on All and on every sport tab. It shows every live game across the top leagues: NBA, NBL, NFL, BBL, AFL, EPL, A-League, LaLiga, Serie A, Bundesliga, Ligue 1, MLS, Champions/Europa League, NHL, MLB, NRL and UFC. Leagues outside your guide only appear here while live, or when one of your teams is playing. New games appear within a couple of minutes of starting.
- **Team logos:** collected from every schedule and team list. Any that are still missing are looked up by name on TheSportsDB, then saved on the device.
- **Team theme:** the background rotates hourly between your teams' photos (from TheSportsDB), dimmed under a neon tint. It's a single cached image, so it doesn't slow anything down. You can turn it off in Settings.
- **Favourite teams:** choose from the full team list of every supported league, with search and league filters. a ★ My teams panel on the home screen and in the player, showing each team's live score or next game. It starts with the Houston Rockets, Manchester United and the Green Bay Packers; edit the list in Settings.
- **Live stats:** press OK while watching a game for a side panel with the score by period, team stats, leaders and latest plays. It updates every 15 seconds.
- **Sport switcher:** NBA, NBL, NFL, BBL, AFL and Soccer (Premier League and cups, for Man United). Others (NRL, NHL, MLB, UFC, F1, tennis, golf) can be switched on in Settings.
- **TV guide:** fixtures for yesterday, today and the next 6 days, shown in your **local time**. It refreshes once a day (about 5am) and whenever the app opens on a new day. Live scores refresh every 90 seconds while the guide is on screen.
- **Themes:** Settings › Colour theme: Midnight (default), Graphite, Stadium, Sunset or the original Neon Purple. Settings options open a list to pick from.
- **Measured quality:** while you're on the menus (never during playback), Fitz TV reads a couple of seconds of the streams you're most likely to watch and records their real resolution, frame rate and interlacing. 1080i counts as 50/60 fields a second. Labels show measured values, or "~720p" when only estimated from the channel name.
- **Best-stream selection:** choose a game and Fitz TV collects every matching stream across all your sources. Streams are grouped by how sure it is they show that game, then ranked by resolution and frame rate. Those come from HLS playlist tags, what the app saw last time the stream played, and quality tags in the name. The top stream plays first; if it fails, the next one is tried automatically. Long-press a game to see the ranked list.
- **Stream format:** Auto (default) uses HLS when your Xtream provider allows it, otherwise MPEG-TS. You can force either in Settings, and a failing stream automatically tries the other format.
- **Playback improvements:**
  - A faster decoder mode turned on for the Shield (Android doesn't use it by default on Android 11), for smoother 50/60 fps sport.
  - Proper audio focus on the phone (pauses for calls).
  - Optional +3/6/9 dB audio boost for quiet streams.
- **Two player engines:** ExoPlayer (Media3) with the FFmpeg audio extension is the main engine. libVLC is used automatically when a stream won't play in ExoPlayer.
- **Anti-buffering / anti-freeze:**
  - Large live buffer, fast start, and instant reconnects at the live edge.
  - A watchdog detects frozen video (picture stops but no buffering event) and long stalls.
  - Repeated stalls step quality down: first a lower HLS rendition, then a lower-quality copy of the same channel (FHD → HD → SD), or the same channel from another source.
  - After a stable period it climbs back to the best quality.
- **Shield AI upscaling:** video is hardware-decoded straight onto a `SurfaceView`, the path the Shield's AI upscaler works on. Turn it on in *Settings › Device Preferences › Display & Sound › AI upscaling* (AI-Enhanced, High).
- **Refresh-rate matching:** switches the TV to 50/60 Hz to suit the stream, for smooth sport motion.
- **Privacy:** no analytics, crash reporting or logging (log calls are stripped from release builds). The only background task is the daily guide refresh.

## Replays, pause and rewind

- **Replays tab:** finished games from the past week, plus your provider's sport on-demand library. Choose a game to watch the **full game from catch-up** on the channel that showed it, ranked by stream quality, or a matching replay video.
- **Highlights:** select any finished game to choose between:
  - **Full replay** (catch-up / on-demand library);
  - **Highlights**, **Extended highlights** and, for NBA/NFL, **Condensed game** on YouTube;
  - **ESPN clips**, which play inside the app.
  With an optional free YouTube Data API key (Settings › Highlights) the exact video opens in the YouTube app; without one, YouTube opens on a search for that game and date.
- **Quick channels (always shown):** Fox Footy and Fox Sports News on Home, Fox Footy on AFL, **NBA TV** and the **Houston Rockets' channel** (Space City Home Network) on NBA, **NFL Network** and **NFL RedZone** on NFL. Each plays the best copy in your sources, with the others as backups.
- **Pause / ±3 seconds / play from start / go live** work on any stream that can seek:
  - provider catch-up (Xtream *TV archive*, M3U `catchup=`): full rewind, and play from start of the game;
  - HLS streams with a rewind window;
  - replays.
  Plain MPEG-TS live streams without catch-up can only pause.
- **My teams:** select a team to jump straight into its live game; otherwise you see its next 7 days and recent results, with replay links.
- **Live stats pages:** Summary, **Players** (NBA/NBL: points, rebounds, assists, steals, blocks, FG made, FG missed, +/-, sorted by points) and **Line-ups** (soccer formation drawn on a pitch, plus the bench).
- **Guide refresh:** runs in the background every time the app opens or comes back to the foreground (at most every 10 minutes). Each league updates on screen as soon as it arrives, so you can pick a game at any time.
- **Time zone:** fixed to Adelaide (ACST/ACDT) for every time shown.

## Multiview, recording and alerts

- **Multiview (1–4 games, full screen):** from the player's controls (⊞), or *Multiview* in any game's options.
  - **TV:** arrows pick a window and the sound follows; OK opens the window menu: move (arrows swap windows), make big, change game, next stream, add a game, full screen, remove.
  - Each window needs one connection to your provider. If your account allows fewer streams than windows, multiview says so.
  - **Phone:** tap a window for its sound; long-press for the same menu.
  - Each window has its own anti-buffering and failover.
- **Recording (DVR):** ⏺ in the player records the game until its expected finish; *Record* on an upcoming game schedules it. Recordings are saved on the device (about 3–4 GB per hour of HD) and appear in **Replays › My recordings** (you can watch while it's still recording). Each recording uses one connection to your IPTV provider.
- **Reminders & alerts:**
  - 🔔 *Remind me* on upcoming games.
  - A pop-up when your teams start, with optional auto-switch.
  - Banners when your team scores or a game is close in its final minutes.
  - The pop-up or banner offers *Watch*.
  - Alerts only run while the app is open; on the phone they also appear as notifications.

## Guide grid, search, sync, screensaver, home row

- **TV grid guide:** Guide › *TV grid* shows the classic channel × time grid from your provider's EPG. On now = watch; finished = catch-up from the start (if the channel has it); upcoming = hold OK / long-press to record.
- **Search:** 🔍 on the TV, or the magnifier on the phone. Covers teams (hold or long-press to add to My teams), games, channels and replays.
- **Sync Shield ↔ phone:**
  - **Phone → TV:** phone Settings › *Copy settings for TV*, then on the Shield Settings › *Sync* › scan the QR and paste into *Sync code*.
  - **TV → phone:** scan the Shield's QR › *Copy this TV's settings* › Copy, then phone Settings › *Import from clipboard*.
  - Sources and favourites are merged, not replaced.
- **Live-scores screensaver (Shield):** Settings › Device Preferences › Screen saver › **Fitz TV live scores**. A neon clock, live games (your teams first) and your teams' next games; refreshes every minute and drifts to avoid burn-in.
- **Android TV home row (Shield):** a *Fitz TV – My teams & live* row on the home screen with your teams' live and upcoming games plus the big live games; select one to start watching. If it doesn't appear, use *Customize channels* on the home screen.

## Remote controls (player)

| Button | Action |
| --- | --- |
| ▲ / ▼ (or CH+/CH−) | Previous / next channel |
| ◀ | My teams, plus all games live now or in the next 12 hours |
| ▶ (or Menu) | Ranked streams for the current game (best first) |
| OK | Playback controls: Start • ⟲3s • Pause • 3s⟳ • Go live • Stats |
| ⏯ / ⏪ / ⏩ | Pause/play, back/forward 3 seconds (hold to repeat) |
| ◀ / ▶ while stats are open | Switch stats page (Summary / Players / Line-ups) |
| Info | Toggle the see-through live stats panel |
| ⏮ / Last channel | Go back to the previous channel |
| ⏯ | Force a reconnect |

## Phone app (Galaxy Z Fold 6 and other Android phones)

The `mobile` module is a touch version of the same app, installed alongside the TV app as **com.fitztv.mobile**. It shares all the sources, guide, stream ranking, player engines and anti-buffering logic with the TV app through the `core` module.

- **Adapts to the screen:** bottom navigation on the cover screen; a side rail and multi-column grids when unfolded or in landscape.
- **Rotation and folding:** the video follows the phone's rotation, even with the auto-rotate lock on. Rotating, folding or unfolding never restarts the stream; the video view is moved, not rebuilt.
- **Player layouts:**
  - Portrait: video on top, with Live stats / Streams / Games tabs underneath.
  - Unfolded portrait: stats and the ranked stream list side by side.
  - Landscape: fullscreen video with a slide-in panel.
  - **Flex mode** (Fold half-open, like a laptop): video on the top half, stats and streams on the bottom half.
- **Gestures:** tap the video to show controls, swipe left/right to switch streams. Picture-in-picture starts automatically when you leave the app.
- Setup is done directly in the app; there's no QR code step.

## In-app updates

Every push builds both apps and publishes a **GitHub Release** (`build-N`) with `FitzTV-TV-build-N.apk` and `FitzTV-Mobile-build-N.apk`. On startup the apps check for a newer build and offer **Update now**. You can also check from Settings › App updates.

One-time setup:

1. **Signing secret (required for updates).** In GitHub, go to repo **Settings › Secrets and variables › Actions › New repository secret**, name it `FITZTV_SIGNING_PASSWORD` and pick any long password. CI rebuilds the same signing key from it on every run (`signing/KeyGen.java`), so updates install over the previous version. The key itself is never stored. Don't lose or change the password, or updates will stop installing.
2. **Reinstall once.** Builds made before the secret was added used a throwaway key. Uninstall the old app once and install the first build made after the secret is in place.
3. **GitHub token (only while the repo is private).** github.com › Settings › Developer settings › Fine-grained tokens. Give it access to Fitz-TV only, with Contents: Read-only. Paste it into the app under Settings › App updates › GitHub token. Not needed if the repo is public.
4. The first time you update, Android asks you to allow Fitz TV to install apps.

## Building

GitHub Actions builds both APKs on every push. Download **FitzTV-apks** from the workflow run:

- `app-release.apk`: the Android TV / Shield app. Sideload it with *Send Files to TV* or `adb install`.
- `mobile-release.apk`: the phone app. Open it on the phone and allow installs from your browser or file manager.

To build locally with the Android SDK: `./gradlew assembleRelease` (`:app` = TV, `:mobile` = phone).
