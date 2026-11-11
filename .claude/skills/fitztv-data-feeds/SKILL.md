---
name: fitztv-data-feeds
description: The zero-key public feeds Fitz TV uses (ESPN summary, TheSportsDB free key, WorldTimeAPI) and how they feed the stats drawer, Momentum tab, clutch buffering and time zone. Use when changing live stats, win probability, shooting splits, team crests, kick-off time zones or ClutchLoadControl.
---

# Zero-key feeds

All adapters live in `core/src/main/java/com/fitztv/data/adapters/`; use `Http.getString(url, "FitzTV")`. Never log or send user data.

## ESPN (`EspnLive`)
- `https://site.api.espn.com/apis/site/v2/sports/<espnPath>/summary?event=<id>`; game ids are `espn:<id>`, path is `Game.espnPath` (e.g. `basketball/nba`).
- Used fields: `header.competitions[0].status` (`displayClock`, `period`, `type.name/state`), `competitors[].score`, `winprobability[].homeWinPercentage`, `boxscore.teams[].statistics` (`fieldGoalPct`, `threePointFieldGoalPct`, `freeThrowPct` and the made-attempted pairs), `plays[].type.text` ("Full Timeout", "End Period").
- Soccer clocks look like `90'+3'` (use `matchMinute`); basketball `4:12`.
- Call `EspnLive.latest(game)` (8 s cache shared by the drawer and the buffer logic); it blocks, so use IO. `parse()` is pure; test it with a trimmed JSON fixture (`EspnLiveTest`).
- Preseason or finished games have no live data: expect `state != "in"`, empty `momentum`.

## TheSportsDB (`SportsDbArt`)
- Free key `123`, call `Http.throttleSportsDb()` before every request (about 30 per minute). Endpoints: `searchteams.php?t=` (`strBadge` transparent crest), `lookupleague.php?id=` (`strBadge/strLogo/strBanner`), `lookuptv.php?id=<idEvent>` (broadcaster `strLogo`). `strTvRights` is league-level text, not art.

## WorldTimeAPI (`LocalKickoff`)
- `worldtimeapi.org/api/ip` returns `timezone`; blocked in the cloud sandbox (403), so it can only be checked on a device. `current()` never blocks and falls back to the device zone.
- Default app zone stays Adelaide; `AppSettings.timeZone` blank means "follow my location".

## Clutch buffering
- `Situation` -> `ClutchPolicy.next` -> `ClutchLoadControl`: clutch = 4th quarter+/overtime with margin <= 8 (exit > 10), or soccer from minute 75 with margin <= 1 (exit > 2); break = halftime / timeout / end of period. `PlaybackController.watchSituation` feeds it every 10 s; Settings › Close-game buffering turns it off.

## Drawer (TV) and Momentum tab (phone)
- TV: `StatsDrawer`, opened by ▶ in `PlayerActivity.dispatchKeyEvent` when `drawerAvailable()`; Back (`closeDrawer`) reopens the controls with focus on Play. Streams list: Menu key or the "Streams" control button.
- Phone: `MomentumPanel` as a tab in `MobilePlayerActivity`'s side panel.
