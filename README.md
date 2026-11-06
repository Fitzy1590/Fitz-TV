# Fitz TV

A sports-only IPTV app for Android TV, built for the **NVIDIA Shield**, with a neon purple interface.

## Features

- **Sources:** Xtream Codes logins and M3U playlist URLs. You can add several. Only sports channels are kept; everything else is filtered out.
- **Sport switcher:** NBA, NFL, Soccer, Cricket, AFL, Rugby/NRL, NHL, MLB, UFC/Boxing, Motorsport, Tennis and Golf. Sports you don't watch can be hidden.
- **TV guide:** fixtures for yesterday, today and the next 6 days, shown in your **local time**. It refreshes once a day (about 5am) and whenever the app opens on a new day. Live scores refresh every 90 seconds while the guide is on screen.
- **Game → channel matching:** choose a game and Fitz TV finds the channels showing it. It checks event channels that name the teams, your provider's EPG, the official broadcaster list and league channels. Long-press a game to pick the channel yourself.
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
| ◀ | Games panel: live now and the next 12 hours. Pick one to jump to it |
| ▶ (or Menu) | Channel list for the current game or sport |
| OK | Show or hide the info bar (resolution, fps, engine, HW decode) |
| ⏮ / Last channel | Go back to the previous channel |
| ⏯ | Force a reconnect |

## Building

GitHub Actions builds a signed APK on every push. Download **FitzTV-apk** from the workflow run and sideload it onto the Shield (for example with *Send Files to TV* or `adb install`).

To build locally with the Android SDK: `./gradlew assembleRelease`.
