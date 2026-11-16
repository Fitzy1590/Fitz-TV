using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace FitzTV.Windows.Services;

/// <summary>Playlist source, saved under %LOCALAPPDATA%\FitzTV. The Xtream password is DPAPI-protected. Nothing leaves the device.</summary>
public sealed class SourceSettings
{
    public string M3uUrl { get; set; } = "";
    public string XtreamServer { get; set; } = "";
    public string XtreamUser { get; set; } = "";
    public string XtreamPassProtected { get; set; } = "";
    public bool HasSource => M3uUrl.Length > 0 || (XtreamServer.Length > 0 && XtreamUser.Length > 0);
}

public static class SettingsStore
{
    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FitzTV", "source.json");

    public static SourceSettings Load()
    {
        try { return JsonSerializer.Deserialize<SourceSettings>(File.ReadAllText(FilePath)) ?? new(); }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException) { return new(); }
    }

    public static void Save(SourceSettings s)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(s));
    }

    public static string Protect(string plain) => plain.Length == 0 ? "" :
        Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(plain), null, DataProtectionScope.CurrentUser));

    public static string Unprotect(string stored)
    {
        if (stored.Length == 0) return "";
        try { return Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(stored), null, DataProtectionScope.CurrentUser)); }
        catch (CryptographicException) { return ""; }
    }
}
