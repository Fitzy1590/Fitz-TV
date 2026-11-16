# Fitz TV Windows spec

Vote (2026-11-16): **[x] Option A** OptiScaler + LibMPV D3D11 + 0.5 ms timer + 0 ms Fluent UI + slide-over stats · [ ] Option B plain LibMPV.
Namespace `FitzTV.Windows` · WinUI 3 unpackaged, x64, net8.0 · project `FitzTV.Windows/`.

## 1. OptiScaler proxy (`Engine/OptiScalerProxyManager.cs`)
- We bundle OptiScaler's `dxgi.dll` under `native/OptiScaler/` (not downloaded at runtime). On launch it is copied next to the exe,
  before the first D3D11 device exists, so Windows loads it in place of the system `dxgi.dll`.
- `OptiScaler.ini` is rewritten from the in-player controls: upscaler FSR 3.1, preset ratio (Quality 1.5, Balanced 1.7, Performance 2.0),
  frame generation on/off. Changes apply on next player start.
- **Unverified:** OptiScaler targets game swapchains. Whether it receives usable motion/depth data from a video swapchain is untested,
  and the ini key names need checking against the bundled OptiScaler version. Fallback that always works: mpv's own
  `interpolation` + `video-sync=display-resample` (the Frame generation toggle drives this too).
- Boundary: never ship OptiScaler without its licence; no network access from this class.

## 2. LibMPV engine (`Engine/LibMpv.cs`, `Views/LibMpvPlayerView`)
- `vo=gpu-next`, `gpu-api=d3d11`, `hwdec=d3d11va` (AV1, HEVC, H.264, VP9), output into a native child HWND laid over the XAML control
  (so XAML cannot draw on top of the video; the drawer closes the video overlay only by sitting in the same window area, see Known gaps).
- Shader cache persisted in `%LocalAppData%\FitzTV\shaders`.
- Clutch Edge (`ClutchEdgeLoadControl`): read-ahead 1.5 s when live, period >= 4 and score delta <= 8; 12 s in intermissions; 4 s otherwise.
  Fed by `Sports/EspnScores` (delta, period, halftime/end-of-period).
- Pre-fetch: `demuxer-max-bytes=150MiB`; `StreamPrewarm` sends HEAD requests to the next likely channels to keep sockets warm.

## 3. Timer (`Engine/NtTimerResolution.cs`)
`NtSetTimerResolution(5000, true, out _)` (5000 x 100 ns = 0.5 ms) at launch, released on window close.

## 4. UI
- `NavigationView` + `Frame.Navigate(..., new SuppressNavigationTransitionInfo())`: no page animation.
- Right Arrow opens a 30%-width drawer with ESPN scoreboard; Left Arrow closes.

## Known gaps
- Channel source (Xtream/M3U/EPG) not yet ported from `:core`; channel list is empty.
- Drawer lacks standings and win-probability chart. EPG dock is not built.
- Child-HWND video draws above XAML (airspace), so the drawer will be covered by video until the player is moved to a swapchain panel.
- Shader pre-compile warm-up beyond the persistent cache is not done.
