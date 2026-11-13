using System.Runtime.InteropServices;

namespace FitzTV.Windows.Services.Media;

/// <summary>
/// Native child window that libmpv renders into via its <c>wid</c> option. WinUI 3 has no HWND per control,
/// so the video pane is a child of the main window positioned over a XAML placeholder. A child HWND always
/// draws above XAML, so overlays (stats drawer, quick bar) take their own layout space instead of floating over video.
/// </summary>
internal sealed class ChildWindow : IDisposable
{
    private const uint WS_CHILD = 0x40000000, WS_VISIBLE = 0x10000000, WS_CLIPSIBLINGS = 0x04000000;
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern nint CreateWindowEx(uint exStyle, string cls, string? name, uint style,
        int x, int y, int w, int h, nint parent, nint menu, nint instance, nint param);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool MoveWindow(nint h, int x, int y, int w, int hgt, bool repaint);
    [DllImport("user32.dll")] private static extern bool ShowWindow(nint h, int cmd);
    [DllImport("user32.dll")] private static extern bool DestroyWindow(nint h);

    public nint Handle { get; }

    public ChildWindow(nint parent)
    {
        // "Static" is a built-in class, so no window class registration is needed.
        Handle = CreateWindowEx(0, "Static", null, WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS, 0, 0, 1, 1, parent, 0, 0, 0);
        if (Handle == 0) throw new InvalidOperationException("Could not create the video window.");
    }

    public void Place(int x, int y, int w, int h) => MoveWindow(Handle, x, y, Math.Max(w, 1), Math.Max(h, 1), true);
    public void SetVisible(bool visible) => ShowWindow(Handle, visible ? 5 : 0);
    public void Dispose() => DestroyWindow(Handle);
}
