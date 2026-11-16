using FitzTV.Windows.Engine;
using Microsoft.UI.Xaml;

namespace FitzTV.Windows;

public partial class App : Application
{
    private Window? _window;
    public static Window? Main { get; private set; }

    public App() => InitializeComponent();

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        NtTimerResolution.Lock();          // 0.5 ms system timer before anything renders
        OptiScalerProxyManager.Prepare();  // dxgi.dll + ini must exist before D3D11 loads
        _window = Main = new MainWindow();
        _window.Closed += (_, _) => NtTimerResolution.Release();
        _window.Activate();
    }
}
