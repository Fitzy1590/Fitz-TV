using FitzTV.Windows.Engine;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace FitzTV.Windows.Views;

public sealed partial class DashboardPage : Page
{
    // Placeholder list until the Xtream/M3U source is ported from :core.
    private static readonly string[] Samples = { };

    public DashboardPage()
    {
        InitializeComponent();
        Channels.ItemsSource = Samples;
        Status.Text = $"Timer 0.5 ms: {(NtTimerResolution.Active ? "on" : "off")} · " +
                      $"OptiScaler: {(OptiScalerProxyManager.Available ? "ready" : "dxgi.dll not bundled")} · " +
                      $"libmpv: {(LibMpv.IsInstalled ? "ready" : "missing")}";
    }

    private void OnChannel(object sender, SelectionChangedEventArgs e)
    {
        if (Channels.SelectedItem is string url)
        {
            Player.Play(url);
            _ = StreamPrewarm.WarmAsync(Samples.Where(s => s != url));
        }
    }

    private void OnVideoOptions(object sender, RoutedEventArgs e)
    {
        if (Preset is null || FrameGen is null) return;
        OptiScalerProxyManager.Apply((UpscalePreset)Preset.SelectedIndex, FrameGen.IsOn);
        Player.SetInterpolation(FrameGen.IsOn);
    }
}
