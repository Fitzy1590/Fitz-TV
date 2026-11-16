using System.Runtime.InteropServices;

namespace FitzTV.Windows.Engine;

/// <summary>Thin libmpv wrapper: D3D11 gpu-next output into a child HWND with hardware decoding.</summary>
public sealed class LibMpv : IDisposable
{
    private const string Lib = "libmpv-2.dll";
    [DllImport(Lib)] private static extern IntPtr mpv_create();
    [DllImport(Lib)] private static extern int mpv_initialize(IntPtr ctx);
    [DllImport(Lib)] private static extern void mpv_terminate_destroy(IntPtr ctx);
    [DllImport(Lib)] private static extern int mpv_set_option_string(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string n, [MarshalAs(UnmanagedType.LPUTF8Str)] string v);
    [DllImport(Lib)] private static extern int mpv_set_property_string(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string n, [MarshalAs(UnmanagedType.LPUTF8Str)] string v);
    [DllImport(Lib)] private static extern int mpv_command_string(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string cmd);

    public static bool IsInstalled => File.Exists(Path.Combine(AppContext.BaseDirectory, Lib));

    private IntPtr _ctx;

    public void Start(IntPtr hwnd)
    {
        var cache = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FitzTV", "shaders");
        Directory.CreateDirectory(cache);
        _ctx = mpv_create();
        Opt("wid", hwnd.ToInt64().ToString());
        Opt("vo", "gpu-next");
        Opt("gpu-api", "d3d11");
        Opt("gpu-context", "d3d11");
        Opt("hwdec", "d3d11va");            // AV1 / HEVC / H.264 / VP9 on the GPU
        Opt("gpu-shader-cache-dir", cache); // compiled shaders persist across launches
        Opt("video-sync", "display-resample");
        Opt("interpolation", "yes");
        Opt("tscale", "oversample");
        Opt("cache", "yes");
        Opt("demuxer-max-bytes", "150MiB"); // RAM pre-fetch for fast channel switching
        Opt("demuxer-readahead-secs", ClutchEdgeLoadControl.Normal.ToString("0.0"));
        Opt("input-default-bindings", "no");
        mpv_initialize(_ctx);
    }

    public void Load(string url) => mpv_command_string(_ctx, $"loadfile \"{url.Replace("\"", "%22")}\" replace");
    public void SetBuffer(double seconds) => Prop("demuxer-readahead-secs", seconds.ToString("0.0"));
    public void SetInterpolation(bool on) => Prop("interpolation", on ? "yes" : "no");

    private void Opt(string n, string v) => mpv_set_option_string(_ctx, n, v);
    private void Prop(string n, string v) { if (_ctx != IntPtr.Zero) mpv_set_property_string(_ctx, n, v); }

    public void Dispose()
    {
        if (_ctx == IntPtr.Zero) return;
        mpv_terminate_destroy(_ctx);
        _ctx = IntPtr.Zero;
    }
}
