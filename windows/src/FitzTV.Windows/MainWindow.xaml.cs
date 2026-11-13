using FitzTV.Core;
using FitzTV.Core.Services.Media;
using FitzTV.Windows.Services;
using FitzTV.Windows.Services.Media;
using FitzTV.Windows.ViewModels;
using Microsoft.UI.Input;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Windows.System;
using Windows.UI.Core;

namespace FitzTV.Windows;

public sealed partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; } = new();
    private MpvEngine? _engine;
    private PlaybackController? _controller;
    private bool _paused, _muted;
    private int _ticks;
    private readonly Microsoft.UI.Dispatching.DispatcherQueueTimer _timer;

    public MainWindow()
    {
        InitializeComponent();
        try
        {
            _engine = new MpvEngine(WinRT.Interop.WindowNative.GetWindowHandle(this));
            _controller = new PlaybackController(_engine);
        }
        catch (Exception e) when (e is DllNotFoundException or InvalidOperationException or BadImageFormatException)
        {
            ViewModel.Status = "libmpv-2.dll was not found. Put it next to FitzTV.Windows.exe.";
        }
        VideoHost.LayoutUpdated += (_, _) => PlaceVideo();
        Closed += (_, _) => { _timer.Stop(); _engine?.Dispose(); _engine = null; };

        _timer = DispatcherQueue.CreateTimer();
        _timer.Interval = TimeSpan.FromSeconds(1);
        _timer.Tick += async (_, _) =>
        {
            _controller?.Tick(DateTimeOffset.UtcNow);
            _ticks++;
            if (_ticks % 20 == 0) ViewModel.UpdateNowNext();
            var fast = ViewModel.IsDrawerOpen || ViewModel.ShowFixtures;
            if (_ticks % (fast ? 30 : 300) == 0) await ViewModel.RefreshGamesAsync();
        };
        _timer.Start();

        var settings = SettingsStore.Load();
        if (settings.HasSource) _ = ViewModel.LoadSourceAsync(settings);
        else ViewModel.ShowSetup = true;
        _ = ViewModel.RefreshGamesAsync();
    }

    public static Visibility Vis(bool value, bool invert = false) => value != invert ? Visibility.Visible : Visibility.Collapsed;

    private void PlaceVideo()
    {
        if (_engine == null || VideoHost.XamlRoot == null) return;
        var p = VideoHost.TransformToVisual(null).TransformPoint(new global::Windows.Foundation.Point(0, 0));
        var s = VideoHost.XamlRoot.RasterizationScale;
        _engine.Place((int)(p.X * s), (int)(p.Y * s), (int)(VideoHost.ActualWidth * s), (int)(VideoHost.ActualHeight * s));
    }

    private void Play(Channel c)
    {
        ViewModel.Selected = c;
        _paused = false;
        _controller?.Start(ViewModel.CandidatesFor(c), DateTimeOffset.UtcNow);
        if (_controller != null) ViewModel.Status = "";
    }

    private void Channel_ItemClick(object sender, ItemClickEventArgs e) => Play((Channel)e.ClickedItem);

    private void Overlay_ItemClick(object sender, ItemClickEventArgs e)
    {
        Play((Channel)e.ClickedItem);
        ViewModel.IsOverlayOpen = false;
    }

    private void TabChannels_Click(object sender, RoutedEventArgs e) { ViewModel.ShowFixtures = false; TabChannels.IsChecked = true; TabFixtures.IsChecked = false; ViewModel.ShowSetup = false; }
    private async void TabFixtures_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.ShowFixtures = true; TabFixtures.IsChecked = true; TabChannels.IsChecked = false; ViewModel.ShowSetup = false;
        await ViewModel.RefreshGamesAsync();
    }

    private void Source_Click(object sender, RoutedEventArgs e) => ViewModel.ShowSetup = !ViewModel.ShowSetup;

    private async void SaveSource_Click(object sender, RoutedEventArgs e)
    {
        var s = new SourceSettings
        {
            M3uUrl = M3uBox.Text.Trim(), XtreamServer = ServerBox.Text.Trim(), XtreamUser = UserBox.Text.Trim(),
            XtreamPassProtected = SettingsStore.Protect(PassBox.Password)
        };
        PassBox.Password = "";
        if (!s.HasSource) { ViewModel.Status = "Enter an M3U URL or Xtream server and username."; return; }
        SettingsStore.Save(s);
        ViewModel.ShowSetup = false;
        await ViewModel.LoadSourceAsync(s);
    }

    private void Root_PreviewKeyDown(object sender, KeyRoutedEventArgs e)
    {
        var focused = FocusManager.GetFocusedElement(Root.XamlRoot);
        if (focused is TextBox or PasswordBox) return;
        var ctrl = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Control).HasFlag(CoreVirtualKeyStates.Down);
        var inOverlay = ViewModel.IsOverlayOpen && focused is DependencyObject d && IsInside(d, OverlayList);

        switch (e.Key)
        {
            case VirtualKey.Space: _paused = !_paused; _engine?.SetPaused(_paused); break;
            case VirtualKey.F: ViewModel.IsTheater = !ViewModel.IsTheater; break;
            case VirtualKey.M: _muted = !_muted; _engine?.SetMuted(_muted); break;
            case VirtualKey.G: ToggleOverlay(); break;
            case VirtualKey.K when ctrl: ToggleOverlay(); break;
            case VirtualKey.Escape:
            case VirtualKey.GamepadB:
                if (ViewModel.IsOverlayOpen) ViewModel.IsOverlayOpen = false;
                else if (ViewModel.IsDrawerOpen) ViewModel.IsDrawerOpen = false;
                else if (ViewModel.IsTheater) ViewModel.IsTheater = false;
                else return;
                break;
            case VirtualKey.Right or VirtualKey.GamepadDPadRight when !inOverlay && !ViewModel.IsDrawerOpen
                    && (ViewModel.IsTheater || focused is null || IsInside(focused as DependencyObject, ChannelList)):
                ViewModel.IsDrawerOpen = true; _ = ViewModel.RefreshGamesAsync(); break;
            case VirtualKey.Left or VirtualKey.GamepadDPadLeft when ViewModel.IsDrawerOpen && !inOverlay:
                ViewModel.IsDrawerOpen = false; break;
            case VirtualKey.Up when ViewModel.IsTheater && !ViewModel.IsOverlayOpen:
                if (ViewModel.Step(-1) is { } up) Play(up); break;
            case VirtualKey.Down when ViewModel.IsTheater && !ViewModel.IsOverlayOpen:
                if (ViewModel.Step(1) is { } down) Play(down); break;
            default: return;
        }
        e.Handled = true;
    }

    private void ToggleOverlay()
    {
        ViewModel.IsOverlayOpen = !ViewModel.IsOverlayOpen;
        if (!ViewModel.IsOverlayOpen) return;
        if (ViewModel.Selected != null) OverlayList.SelectedItem = ViewModel.Selected;
        DispatcherQueue.TryEnqueue(() => OverlayList.Focus(FocusState.Keyboard));
    }

    private static bool IsInside(DependencyObject? node, DependencyObject container)
    {
        for (; node != null; node = Microsoft.UI.Xaml.Media.VisualTreeHelper.GetParent(node))
            if (node == container) return true;
        return false;
    }
}
