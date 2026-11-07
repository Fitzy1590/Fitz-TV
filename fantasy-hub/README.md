# Fitz Fantasy Hub

One app for every fantasy football and basketball team you have on Sleeper, ESPN and Yahoo. It syncs your leagues and lets you set lineups that save back to each platform. It also pulls FantasyPros rankings in the points system each league uses, imports Fantasy Footballers rankings, and includes a trade analyzer and Sleeper-style player profiles with headshots.

It's an installable web app (PWA) with a small Node server. The server is needed because ESPN, Yahoo and FantasyPros don't allow browser apps to call them directly, and it keeps your platform credentials off the phone.

## Quick start

```bash
cd fantasy-hub
npm install
cp .env.example .env        # set APP_PASSWORD and APP_SECRET
npm run dev                 # API on :8787, app on http://localhost:5173
```

Try it without any accounts: `DEMO=1 npm run dev`.

Production: `npm run build && npm start`, or use the `Dockerfile` (mount a volume at `/data`). On Android, open the site in Chrome and choose **Add to Home screen**.

## What syncs and how

| Platform | Read leagues/rosters | Change lineups | How you link it |
|---|---|---|---|
| Sleeper | Public API | Sleeper's private GraphQL (`update_matchup_leg`), the same call sleeper.com makes | Username, plus password (only exchanged for a session token, never stored) or a pasted session token |
| ESPN (football + basketball) | `lm-api-reads` (unofficial) | `lm-api-writes` transactions endpoint (unofficial) | `espn_s2` + `SWID` cookies. Leagues are found automatically, or you can enter IDs |
| Yahoo (football + basketball) | Official Fantasy Sports API | Official `PUT /team/{key}/roster` | OAuth sign-in. Needs a Yahoo developer app (see `.env.example`) |

Sleeper's lineup call and everything on ESPN are undocumented and can change without warning. If a save fails, the platform's error message is shown in the app.

Basketball lineups: ESPN uses the platform's scoring period (one day), and Yahoo sets today's date.

## Rankings

- **FantasyPros ECR**: weekly, rest-of-season, dynasty and draft. Choose Standard, Half PPR, Full PPR, Superflex or TE Premium, or select a league to use its own settings. Settings are detected from each league and can be overridden per league (League → Settings).
  - Superflex uses FantasyPros' OP list. If that's missing, the app merges QB and FLEX rankings by projected points.
  - TE premium re-orders the overall list using projections with the TE bonus added, because FantasyPros doesn't publish TE-premium weekly ECR.
  - Works without a key by reading the public ranking pages. A FantasyPros API key is faster and more reliable.
- **Fantasy Footballers**: the Ultimate dashboard has no public API. Paste or upload the rankings table (Accounts → Fantasy Footballers) and it feeds the Rankings screen and the trade analyzer.

## Player data

Headshots, bios, depth charts, game logs and career stats come from Sleeper's player database and stats feed. ESPN and Yahoo players are matched to Sleeper IDs through the `espn_id` and `yahoo_id` fields in Sleeper's player data.

## Security

- The whole hub sits behind `APP_PASSWORD`, with a signed, HTTP-only session cookie.
- Platform tokens and cookies are stored in `data/vault.bin`, encrypted with AES-256-GCM using a key derived from `APP_SECRET`.
- `espn_s2` is a full ESPN session. Treat the server like a password manager.

## Scripts

`npm run dev` · `npm run build` · `npm start` · `npm test` · `npm run typecheck`
