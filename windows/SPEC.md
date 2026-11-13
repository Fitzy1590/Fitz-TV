# Fitz TV Windows: Spec

Status legend: **[V]** verified, **[U]** unverified, must be checked before relying on it.

## 0. Approved decisions (vote)
- [x] 1A Multi-pane Fluent grid, docked channel list, Theater toggle (`F`)
- [x] 2A libmpv, D3D11 hardware decode
- [x] 3A Right slide-over stats drawer (Right Arrow / D-pad Right)
- [x] 4B Quick overlay bar only (no EPG grid)
- [x] 5A ESPN JSON + TheSportsDB crests + local `TimeZoneInfo` (WorldTimeAPI dropped)
- [x] Location: `windows/` subfolder. Framework: WinUI 3.

Consequence of 4B: XMLTV is still parsed, but only to show now/next on the overlay and the channel list.

## 1. Streaming and hardware
**Host.** WinUI 3 has no HWND for mpv's `wid`. Spike first: render libmpv into a `SwapChainPanel` using the
D3D11 route (custom device + swap chain from libmpv) **[U]**; the only known implementations are the
mpv-uwp / mpv-winui forks **[U]**. Fallback if the spike fails: host a Win32 child HWND via a
`Microsoft.UI` `ContentIsland`/native window and pass it as `wid`. Wrapper: try `LibMpv.NET`
(existence and maintenance **[U]**), else a thin P/Invoke layer over `libmpv-2.dll` (LGPL, ship next to the exe).

**mpv options** (documented mpv options):
```
hwdec=d3d11va          vo=gpu-next (fallback: gpu)    gpu-api=d3d11
cache=yes              demuxer-max-bytes=64MiB        demuxer-readahead-secs=10
network-timeout=15     user-agent=<playlist UA or "FitzTV/1.0">
stream-lavf-o=reconnect=1,reconnect_streamed=1,reconnect_delay_max=4
```
Multithread-protect the shared D3D11 device. The swap chain exists only after playback starts.

**Playback controller** (port of Android `PlaybackController`): watchdog on stalled `time-pos`,
failover to the next candidate stream, quality upgrade attempt. Dispose the mpv handle on window teardown.

**Playlist pipeline.** M3U/M3U8 playlist parser (`#EXTINF`, `tvg-id`, `tvg-logo`, `group-title`, UA/referrer
options), Xtream (`player_api.php` actions `get_live_streams`, `get_live_categories`; stream URL
`{host}/live/{user}/{pass}/{id}.m3u8` or `.ts`) and XMLTV. Stream playback itself, including HLS, is mpv's job;
we do not parse segment playlists. Parse off the UI thread, stream the XML, cache to local disk.

## 2. Zero-key data
**ESPN scoreboard [U, the fetch returned 403; recorded from memory, verify with curl]**
`GET https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard`
(for example `basketball/nba`, `soccer/eng.1`, `football/nfl`, `hockey/nhl`). Unofficial, may change; no key.
Fields used: `events[].id`, `date` (UTC ISO), `name`, `shortName`, `status.type.state` (`pre|in|post`),
`status.displayClock`, `status.period`, `competitions[0].competitors[]` with `homeAway`, `score`,
`team.displayName`, `team.abbreviation`, `team.logo`, `team.color`. Poll live games every 15 s, others every 5 min.
Every field optional; a missing field hides that UI element, never crashes.

**TheSportsDB [V docs]** Free key `123`, 30 requests/min, 429 when exceeded. v1 `searchteams.php?t=` is limited
to "Arsenal" and `lookupteam.php?id=` to one result on the free tier; v2 needs a premium key (not used).
Crest field believed `strBadge` **[U]**; image suffix `/small` `/medium` `/tiny` gives smaller files.
Use: crests and fixture thumbnails only. Primary crest is ESPN `team.logo` when present; TheSportsDB is the
fallback. Cache every image on disk by URL hash; never fetch the same crest twice.

**Kickoff time.** No network call: `DateTimeOffset.Parse(iso).ToLocalTime()`, or
`TimeZoneInfo.ConvertTimeFromUtc(utc, TimeZoneInfo.Local)`. Format with the user's culture.

## 3. Windows UI
- **Shell:** one window, `MicaBackdrop`, left docked channel/category list (min 280 px, splitter), right video
  pane. Top tabs: Sports, Leagues, Fixtures. Collapse the list below 900 px width.
- **Theater (`F`):** hides list and tabs; only video and the OSD remain. `Esc` exits.
- **Stats drawer:** right-edge panel, about 34% width, over the video. Right Arrow / D-pad Right opens, Left
  or `Esc` closes. Content: status, clock, score, per-team stats. Visibility toggles instantly (no animation).
- **Quick overlay (Ctrl+K or G):** bottom bar with channel chips and Now/Next for the selected channel.
  Left/Right moves between channels, Enter switches, Esc closes. Also acts as the command palette for games.
- **Input:** Space pause, M mute, F Theater, Up/Down channel, Ctrl+K palette. Xbox: D-pad focus, A activate,
  B back, D-pad Right on video opens stats.
- **Motion:** none. No `*ThemeTransition`; `SuppressNavigationTransitionInfo` for every navigation.
- **Focus:** 2 px visible ring on every interactive control; `XYFocusKeyboardNavigation="Enabled"`.
- **Style:** system accent, Segoe Fluent Icons, theme brushes. No gradients, no emoji icons, no decorative glow.

## 3a. As built (deviations from the sections above)
- **Video host:** implemented the fallback route: libmpv renders into a native child HWND (`wid`) placed over the
  `VideoHost` XAML element. The SwapChainPanel route was not attempted. A child HWND always draws above XAML, so the
  stats drawer and quick overlay take their own layout space (right column, bottom row) instead of floating over video.
- **Tabs:** Channels and Fixtures only (Leagues dropped). Fixtures lists ESPN games across NBA, NFL, NHL, MLB, EPL, MLS.
- **Stats drawer:** lists live games with score, period and clock. Per-team stats and matching a game to the playing
  channel are not built.
- **Crests:** not shown in the UI yet. `ImageCache` and `TheSportsDbClient` exist and are tested for parsing only.
- **List pane:** fixed 340 px, no splitter, no auto-collapse below 900 px.

## 4. Open risks
1. libmpv in WinUI 3 (swap chain route unconfirmed). Spike this first.
2. ESPN endpoints unofficial and unverified here.
3. `LibMpv.NET` maintenance unknown.
4. `FitzTV.Windows` (WinUI) has never been compiled: the XAML compiler is a Windows exe. `FitzTV.Core` and tests build and pass on Linux.
