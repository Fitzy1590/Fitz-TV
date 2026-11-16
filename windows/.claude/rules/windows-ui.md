---
paths: ["**/*.xaml", "**/Views/**/*.cs"]
---
# Windows UI rules
- Backdrop: `MicaBackdrop` on the window; panes use `LayerFillColorDefaultBrush` / theme resources.
- Color: system accent (`AccentFillColorDefaultBrush`). No custom gradients, no hard-coded gray fills.
- Icons: `FontIcon` with Segoe Fluent Icons. No emoji.
- Grid: docked channel list (min 280 px, resizable splitter) + video pane; collapse the list below 900 px.
  Theater mode hides everything except video and the OSD.
- Focus: every control shows `FocusVisualPrimaryBrush` ring (2 px). Set `XYFocusKeyboardNavigation="Enabled"`
  and `UseSystemFocusVisuals="True"`. Xbox controller: D-pad moves focus, A activates, B goes back,
  D-pad Right on the video opens the stats drawer.
- Motion: none. No `*ThemeTransition`, no storyboards for navigation. Navigate with
  `Frame.Navigate(type, param, new SuppressNavigationTransitionInfo())`. Set drawer visibility directly.
- Lists: `ItemsRepeater`/`ListView` with virtualization; never build 1000+ channel items eagerly.
