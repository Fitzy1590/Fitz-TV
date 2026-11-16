using System.Text;

namespace FitzTV.Windows.Engine;

public enum UpscalePreset { Quality, Balanced, Performance }

/// <summary>
/// Manages the OptiScaler dxgi.dll proxy. The DLL is shipped by us in native/OptiScaler/ and must sit
/// next to the exe before the first D3D11 device is created. Settings take effect on next player start.
/// </summary>
public static class OptiScalerProxyManager
{
    private static string Dir => AppContext.BaseDirectory;
    private static string ProxyPath => Path.Combine(Dir, "dxgi.dll");
    private static string IniPath => Path.Combine(Dir, "OptiScaler.ini");

    public static UpscalePreset Preset { get; set; } = UpscalePreset.Quality;
    public static bool FrameGeneration { get; set; } = true;
    public static bool Available => File.Exists(Path.Combine(Dir, "OptiScaler", "dxgi.dll")) || File.Exists(ProxyPath);

    private static double Ratio => Preset switch
    {
        UpscalePreset.Quality => 1.5,
        UpscalePreset.Balanced => 1.7,
        _ => 2.0,
    };

    /// <summary>Copy the bundled proxy into place (if present) and write its config.</summary>
    public static void Prepare()
    {
        var bundled = Path.Combine(Dir, "OptiScaler", "dxgi.dll");
        if (File.Exists(bundled) && !File.Exists(ProxyPath)) File.Copy(bundled, ProxyPath);
        if (Available) WriteIni();
    }

    public static void Apply(UpscalePreset preset, bool frameGen)
    {
        Preset = preset;
        FrameGeneration = frameGen;
        if (Available) WriteIni();
    }

    private static void WriteIni()
    {
        var sb = new StringBuilder();
        // Section and key names checked against OptiScaler's own OptiScaler.ini (v0.9.x).
        var r = Ratio.ToString("0.0", System.Globalization.CultureInfo.InvariantCulture);
        sb.AppendLine("[Upscalers]");
        sb.AppendLine("Dx11Upscaler=fsr31");
        sb.AppendLine("[QualityOverrides]");
        sb.AppendLine("QualityRatioOverrideEnabled=true");
        sb.AppendLine($"QualityRatioQuality={r}");
        sb.AppendLine($"QualityRatioBalanced={r}");
        sb.AppendLine($"QualityRatioPerformance={r}");
        sb.AppendLine("[FrameGen]");
        sb.AppendLine($"Enabled={FrameGeneration.ToString().ToLowerInvariant()}");
        if (FrameGeneration)
        {
            sb.AppendLine("FGInput=upscaler");
            sb.AppendLine("FGOutput=fsrfg");
        }
        File.WriteAllText(IniPath, sb.ToString());
    }
}
