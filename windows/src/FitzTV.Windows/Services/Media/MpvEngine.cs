using FitzTV.Core.Services.Media;

namespace FitzTV.Windows.Services.Media;

/// <summary>libmpv player with D3D11 hardware decoding (see SPEC.md section 1). Dispose on window teardown.</summary>
public sealed class MpvEngine : IPlayerEngine, IDisposable
{
    private readonly ChildWindow _window;
    private nint _ctx;

    public MpvEngine(nint parentHwnd)
    {
        _window = new ChildWindow(parentHwnd);
        _ctx = MpvInterop.mpv_create();
        if (_ctx == 0) throw new InvalidOperationException("libmpv-2.dll not found or failed to start.");
        Opt("wid", _window.Handle.ToString());
        Opt("vo", "gpu-next"); Opt("gpu-api", "d3d11"); Opt("hwdec", "d3d11va");
        Opt("cache", "yes"); Opt("demuxer-max-bytes", "64MiB"); Opt("demuxer-readahead-secs", "10");
        Opt("network-timeout", "15");
        Opt("stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_delay_max=4");
        Opt("input-default-bindings", "no"); Opt("input-vo-keyboard", "no"); Opt("osc", "no");
        if (MpvInterop.mpv_initialize(_ctx) < 0) throw new InvalidOperationException("mpv_initialize failed.");
    }

    public double? TimePos =>
        _ctx != 0 && MpvInterop.mpv_get_property(_ctx, "time-pos", MpvInterop.FormatDouble, out var v) >= 0 ? v : null;

    public void Play(string url, string? userAgent, string? referrer)
    {
        if (_ctx == 0) return;
        MpvInterop.mpv_set_property_string(_ctx, "user-agent", userAgent ?? "FitzTV/1.0");
        MpvInterop.mpv_set_property_string(_ctx, "referrer", referrer ?? "");
        MpvInterop.Command(_ctx, "loadfile", url, "replace");
    }

    public void Stop() { if (_ctx != 0) MpvInterop.Command(_ctx, "stop"); }
    public void SetPaused(bool paused) { if (_ctx != 0) MpvInterop.mpv_set_property_string(_ctx, "pause", paused ? "yes" : "no"); }
    public void SetMuted(bool muted) { if (_ctx != 0) MpvInterop.mpv_set_property_string(_ctx, "mute", muted ? "yes" : "no"); }
    public void Place(int x, int y, int w, int h) => _window.Place(x, y, w, h);
    public void SetVisible(bool v) => _window.SetVisible(v);

    private void Opt(string name, string value) => MpvInterop.mpv_set_option_string(_ctx, name, value);

    public void Dispose()
    {
        if (_ctx != 0) { MpvInterop.mpv_terminate_destroy(_ctx); _ctx = 0; }
        _window.Dispose();
    }
}
