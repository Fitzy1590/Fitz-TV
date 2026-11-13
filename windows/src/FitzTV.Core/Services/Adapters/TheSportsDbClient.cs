using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace FitzTV.Core.Services.Adapters;

/// <summary>
/// TheSportsDB free key (123): 30 requests/min, and v1 team search is limited on the free tier.
/// Used only as a crest fallback; the badge field name (strBadge) is unverified.
/// </summary>
public sealed class TheSportsDbClient(HttpClient http, string key = "123")
{
    private readonly SemaphoreSlim _gate = new(1, 1);
    private readonly Queue<DateTime> _sent = new();

    public async Task<string?> FindBadgeUrlAsync(string teamName, CancellationToken ct = default)
    {
        await ThrottleAsync(ct);
        var url = $"https://www.thesportsdb.com/api/v1/json/{key}/searchteams.php?t={Uri.EscapeDataString(teamName)}";
        using var resp = await http.GetAsync(url, ct);
        if (!resp.IsSuccessStatusCode) return null; // includes 429
        return ParseBadge(await resp.Content.ReadAsStringAsync(ct));
    }

    public static string? ParseBadge(string json)
    {
        using var doc = JsonDocument.Parse(json);
        if (!doc.RootElement.TryGetProperty("teams", out var teams) || teams.ValueKind != JsonValueKind.Array
            || teams.GetArrayLength() == 0) return null;
        return teams[0].TryGetProperty("strBadge", out var b) && b.ValueKind == JsonValueKind.String ? b.GetString() : null;
    }

    private async Task ThrottleAsync(CancellationToken ct)
    {
        await _gate.WaitAsync(ct);
        try
        {
            while (_sent.Count > 0 && DateTime.UtcNow - _sent.Peek() > TimeSpan.FromMinutes(1)) _sent.Dequeue();
            if (_sent.Count >= 30) await Task.Delay(TimeSpan.FromMinutes(1) - (DateTime.UtcNow - _sent.Peek()), ct);
            _sent.Enqueue(DateTime.UtcNow);
        }
        finally { _gate.Release(); }
    }
}

/// <summary>On-disk image cache keyed by URL hash. Never downloads the same URL twice.</summary>
public sealed class ImageCache(HttpClient http, string directory)
{
    public async Task<string?> GetAsync(string url, CancellationToken ct = default)
    {
        Directory.CreateDirectory(directory);
        var path = Path.Combine(directory, Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(url)))[..32]);
        if (File.Exists(path)) return path;
        try
        {
            var bytes = await http.GetByteArrayAsync(url, ct);
            var tmp = path + ".tmp";
            await File.WriteAllBytesAsync(tmp, bytes, ct);
            File.Move(tmp, path, overwrite: true);
            return path;
        }
        catch (HttpRequestException) { return null; }
    }
}
