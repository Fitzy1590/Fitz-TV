# Fitz TV Windows: Progress

Last updated: 2026-11-13 · Phase: 4 (checkpoint). Next: Phase 5 on a Windows machine.

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

## Backlog (Phase 5, in order)
- [ ] 1. Scaffold WinUI 3 solution `FitzTV.Windows` + `Tests/` (xUnit)
- [ ] 2. M3U parser + tests (fixtures)
- [ ] 3. Xtream client + tests
- [ ] 4. XMLTV now/next parser + tests
- [ ] 5. libmpv spike: SwapChainPanel host plays one test stream, hwdec=d3d11va confirmed
- [ ] 6. Playback controller (watchdog, failover)
- [ ] 7. ESPN adapter + TheSportsDB crest cache + kickoff conversion + tests
- [ ] 8. Shell: Mica, docked list, Theater toggle
- [ ] 9. Stats drawer (Right / D-pad Right)
- [ ] 10. Quick overlay (Ctrl+K / G)
- [ ] 11. Reviewer subagent pass against SPEC.md

## Verification criteria
- `dotnet build` and `dotnet test` pass on Windows.
- No `*ThemeTransition` in XAML; all navigation uses `SuppressNavigationTransitionInfo`.
- Focus ring visible on every control by keyboard and gamepad.
- Spike result for item 5 recorded here before any playback work continues.
- ESPN field names confirmed with a real response and SPEC.md updated.

## Notes
- The Stop hook runs `dotnet build`; it needs the .NET SDK, so it will fail on machines without it
  (such as this Linux session). Run Claude from `windows/` on Windows for the hook to behave.
- Hooks in `windows/.claude/` load only when Claude is started from `windows/`.
