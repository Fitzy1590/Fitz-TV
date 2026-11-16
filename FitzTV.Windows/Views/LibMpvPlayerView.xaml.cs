using System.Runtime.InteropServices;
using FitzTV.Windows.Engine;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using WinRT.Interop;

namespace FitzTV.Windows.Views;

/// <summary>
/// Hosts libmpv's D3D11 (gpu-next) output in a native child window laid over this control.
/// </summary>
public sealed partial class LibMpvPlayerView : UserControl
{
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr CreateWindowExW(int ex, string cls, string? name, uint style, int x, int y, int w, int h, IntPtr parent, IntPtr menu, IntPtr inst, IntPtr param);
    [DllImport("user32.dll")] private static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hgt, bool repaint);
    [DllImport("user32.dll")] private static extern bool DestroyWindow(IntPtr h);
    private const uint WS_CHILD = 0x40000000, WS_VISIBLE = 0x10000000, WS_CLIPSIBLINGS = 0x04000000;

    private IntPtr _child;
    private LibMpv? _mpv;

    public LibMpvPlayerView()
    {
        InitializeComponent();
        SizeChanged += (_, _) => Reposition();
        LayoutUpdated += (_, _) => Reposition();
        Unloaded += (_, _) => Stop();
    }

    public void Play(string url)
    {
        if (!LibMpv.IsInstalled) { Hint.Text = "libmpv-2.dll not found next to the app"; return; }
        if (_mpv is null)
        {
            var main = WindowNative.GetWindowHandle(App.Main!);
            _child = CreateWindowExW(0, "STATIC", null, WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS, 0, 0, 16, 16, main, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
            _mpv = new LibMpv();
            _mpv.Start(_child);
            Reposition();
        }
        Hint.Visibility = Visibility.Collapsed;
        _mpv.Load(url);
    }

    public void SetInterpolation(bool on) => _mpv?.SetInterpolation(on);
    public void SetBuffer(double seconds) => _mpv?.SetBuffer(seconds);

    private void Reposition()
    {
        if (_child == IntPtr.Zero || XamlRoot is null) return;
        var scale = XamlRoot.RasterizationScale;
        var p = TransformToVisual(null).TransformPoint(new Windows.Foundation.Point(0, 0));
        MoveWindow(_child, (int)(p.X * scale), (int)(p.Y * scale), (int)(ActualWidth * scale), (int)(ActualHeight * scale), true);
    }

    private void Stop()
    {
        _mpv?.Dispose(); _mpv = null;
        if (_child != IntPtr.Zero) { DestroyWindow(_child); _child = IntPtr.Zero; }
    }
}
