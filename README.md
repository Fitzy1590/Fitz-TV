# Fitz TV

A sports-only IPTV app for Android TV, built for the **NVIDIA Shield**, with a neon purple interface.

## Features

- **Sources:** Xtream Codes logins and M3U playlist URLs. You can add several. Only sports channels are kept; everything else is filtered out. Use **Set up from phone (QR)** in Settings to scan a code and type the details on your phone. That page is served by the Shield on your home network only.
- **Clean names:** provider clutter like `US| NBA 01: Rockets @ Lakers 7:30PM ET [FHD]` is shown as `Rockets vs Lakers`.
- **Favourite teams:** a ★ My teams panel on the home screen and in the player, showing each team's live score or next game. It starts with the Houston Rockets, Manchester United and the Green Bay Packers; edit the list in Settings.
- **Live stats:** press OK while watching a game for a side panel with the score by period, team stats, leaders and latest plays. It updates every 15 seconds.
- **Sport switcher:** NBA, NBL, NFL, BBL, AFL and Soccer (Premier League and cups, for Man United). Others (NRL, NHL, MLB, UFC, F1, tennis, golf) can be switched on in Settings.
- **TV guide:** fixtures for yesterday, today and the next 6 days, shown in your **local time**. It refreshes once a day (about 5am) and whenever the app opens on a new day. Live scores refresh every 90 seconds while the guide is on screen.
- **Best-stream selection:** choose a game and Fitz TV collects every matching stream across all your sources. Streams are grouped by how sure it is they show that game, then ranked by resolution and frame rate. Those come from HLS playlist tags, what the app saw last time the stream played, and quality tags in the name. The top stream plays first; if it fails, the next one is tried automatically. Long-press a game to see the ranked list.
- **Stream format:** Auto (default) uses HLS when your Xtream provider allows it, otherwise MPEG-TS. You can force either in Settings, and a failing stream automatically tries the other format.
- **Two player engines:** ExoPlayer (Media3) with the FFmpeg audio extension is the main engine. libVLC is used automatically when a stream won't play in ExoPlayer.
- **Anti-buffering / anti-freeze:**
  - Large live buffer, fast start, and instant reconnects at the live edge.
  - A watchdog detects frozen video (picture stops but no buffering event) and long stalls.
  - Repeated stalls step quality down: first a lower HLS rendition, then a lower-quality copy of the same channel (FHD → HD → SD), or the same channel from another source.
  - After a stable period it climbs back to the best quality.
- **Shield AI upscaling:** video is hardware-decoded straight onto a `SurfaceView`, the path the Shield's AI upscaler works on. Turn it on in *Settings › Device Preferences › Display & Sound › AI upscaling* (AI-Enhanced, High).
- **Refresh-rate matching:** switches the TV to 50/60 Hz to suit the stream, for smooth sport motion.
- **Privacy:** no analytics, crash reporting or logging (log calls are stripped from release builds). The only background task is the daily guide refresh.

## Remote controls (player)

| Button | Action |
| --- | --- |
| ▲ / ▼ (or CH+/CH−) | Previous / next channel |
| ◀ | My teams, plus all games live now or in the next 12 hours |
| ▶ (or Menu) | Ranked streams for the current game (best first) |
| OK | Live stats panel (when watching a game) or the info bar |
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

## Building

GitHub Actions builds both APKs on every push. Download **FitzTV-apks** from the workflow run:

- `app-release.apk`: the Android TV / Shield app. Sideload it with *Send Files to TV* or `adb install`.
- `mobile-release.apk`: the phone app. Open it on the phone and allow installs from your browser or file manager.

To build locally with the Android SDK: `./gradlew assembleRelease` (`:app` = TV, `:mobile` = phone).
