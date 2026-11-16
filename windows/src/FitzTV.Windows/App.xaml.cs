using FitzTV.Windows.Engine;
using Microsoft.UI.Xaml;

namespace FitzTV.Windows;

public partial class App : Application
{
    private Window? _window;
    public App() => InitializeComponent();
    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        NtTimerResolution.Lock();          // 0.5 ms system timer
        OptiScalerProxyManager.Prepare();  // dxgi.dll + ini before the first D3D11 device
        _window = new MainWindow();
        _window.Closed += (_, _) => NtTimerResolution.Release();
        _window.Activate();
    }
}
