# Fitz TV Windows: Progress

Last updated: 2026-11-13 · Phase 5 partly done. Next: on Windows run `dotnet build`, fix XAML/C# errors, then the libmpv spike.

## Votes (recorded)
- [x] 1A Multi-pane Fluent grid + Theater toggle
- [x] 2A libmpv D3D11
- [x] 3A Right slide-over stats drawer
- [x] 4B Quick overlay bar only (EPG grid dropped)
- [x] 5A ESPN + TheSportsDB + local TimeZoneInfo
- [x] Repo: `windows/` subfolder · Framework: WinUI 3

## Done
- [x] Phase 1 research and vote
- [x] Phase 2 config: `CLAUDE.md`, `.claude/settings.json`, `.claude/agents/reviewer.md`, `.claude/rules/windows-ui.md`
- [x] Phase 3 `SPEC.md`

## Backlog (Phase 5)
- [x] 1. Solution scaffold: `FitzTV.Core`, `FitzTV.Windows`, `FitzTV.Tests`, CI `.github/workflows/windows.yml`
- [x] 2. M3U parser + tests
- [x] 3. Xtream client + tests
- [x] 4. XMLTV now/next parser + tests
- [~] 5. libmpv host written (child HWND, wid). **Spike not run**: needs Windows + libmpv-2.dll
- [x] 6. Playback controller (start timeout, stall watchdog, failover) + tests
- [x] 7. ESPN adapter, TheSportsDB badge parser, image cache, kickoff conversion + tests (ESPN sample is hand-written)
- [~] 8. Shell: Mica, docked list, Theater toggle. Written, **never compiled**
- [~] 9. Stats drawer (live games list). Written, never compiled
- [~] 10. Quick overlay. Written, never compiled
- [ ] 11. Reviewer subagent pass against SPEC.md
- [ ] 12. Wire crests into UI via ImageCache; game-to-channel matching; per-team stats; splitter; auto-collapse

## Verification criteria
- `dotnet test tests/FitzTV.Tests`: 16/16 pass on Linux (done). `dotnet build` of the full solution passes on Windows (not yet).
- No `*ThemeTransition` in XAML; all navigation uses `SuppressNavigationTransitionInfo`.
- Focus ring visible on every control by keyboard and gamepad.
- Spike result for item 5 recorded here before any playback work continues.
- ESPN field names confirmed with a real response and SPEC.md updated.

## Notes
- The Stop hook runs `dotnet build`; it needs the .NET SDK, so it will fail on machines without it
  (such as this Linux session). Run Claude from `windows/` on Windows for the hook to behave.
- Hooks in `windows/.claude/` load only when Claude is started from `windows/`.
