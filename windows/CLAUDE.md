# Fitz TV Windows

Sports-only IPTV client for Windows PCs (`FitzTV.Windows`, `com.fitzy.fitztv.windows`).
Port of the Android TV app in the repo root; this folder is self-contained. Spec: `SPEC.md`.
State and backlog: `FEATURE_PROGRESS.md`. Run Claude from `windows/` so these settings load.

## Stack
C# .NET, WinUI 3 (Windows App SDK), MVVM. Playback: libmpv, D3D11 hw decode.
Data: ESPN scoreboard JSON, TheSportsDB (free key), local `TimeZoneInfo`. No other keys.

## Build
- `dotnet test tests/FitzTV.Tests` — works anywhere with .NET 8
- `dotnet build` / `dotnet run --project src/FitzTV.Windows` — Windows only (WinUI XAML compiler)

## Layout
- `src/FitzTV.Windows/` — WinUI 3 app: `Views/`, `ViewModels/`, `Services/Media/` (libmpv engine, native video window)
- `src/FitzTV.Core/` — net8.0, no Windows deps: `Services/IPTV/` (M3U, Xtream, XMLTV),
  `Services/Adapters/` (ESPN, TheSportsDB, kickoff), `Services/Media/PlaybackController` (watchdog, failover)
- `tests/FitzTV.Tests/` — xUnit, fixtures only, no network. Runs on Linux and Windows.

## Approved design (see SPEC.md)
Multi-pane Fluent grid: docked channel list + video; `F` toggles Theater. Right Arrow / D-pad Right
opens the stats drawer. Guide is a quick overlay bar only (Ctrl+K / G), no EPG grid.

## UI rules
- Fluent, Windows 11: Mica backdrop, native system accent (`SystemAccentColor`), no purple gradients,
  no plain gray boxes, no emoji as icons (Segoe Fluent Icons).
- Zero animation: no `EntranceThemeTransition` / `NavigationThemeTransition`; navigate with
  `SuppressNavigationTransitionInfo`. Channel and tab switches are instant.
- Every interactive element has a visible focus ring (keyboard and Xbox controller).
- Keyboard and gamepad parity: every action reachable without a mouse.
- Details in `.claude/rules/windows-ui.md` (loads for XAML and Views).

## Rules
- Parse once, off the UI thread; UI binds to ViewModels only.
- Never retain the mpv handle across window teardown; dispose it.
- No analytics or logging of credentials. Playlist URLs and credentials stay on the device.
- Never commit keystores, `.env`, or `appsettings.Development.json`.

## Karpathy guardrails
1. Minimal delta: change 5 lines, not 500. Never reformat untouched code.
2. No unasked abstractions.
3. No silent assumptions: inspect existing files and APIs first.
4. Read before touch: read the target C#/XAML and dependencies before editing.

## Token discipline
Delegate repo-wide exploration to subagents. Keep replies short. At ~80% context, write state to
`FEATURE_PROGRESS.md` and tell the user to run `/clear`.
