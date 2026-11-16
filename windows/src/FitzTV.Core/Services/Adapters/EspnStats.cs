using System.Text.Json;

namespace FitzTV.Core.Services.Adapters;

public sealed record StandingRow(int Rank, string Team, string Record, double Pct);
public sealed record StandingsTable(string Name, List<StandingRow> Rows);

/// <summary>
/// ESPN league standings and per-game win probability. Standings shape follows the Android StandingsClient
/// (apis/v2/.../standings). The win-probability field names come from memory of the summary JSON and are unconfirmed:
/// a missing or renamed field yields an empty list, never an exception.
/// </summary>
public sealed class EspnStats(HttpClient http)
{
    public async Task<List<StandingsTable>> GetStandingsAsync(string sport, string league, CancellationToken ct = default) =>
        ParseStandings(await http.GetStringAsync($"https://site.api.espn.com/apis/v2/sports/{sport}/{league}/standings", ct));

    public async Task<List<double>> GetWinProbabilityAsync(string sport, string league, string eventId, CancellationToken ct = default) =>
        ParseWinProbability(await http.GetStringAsync($"https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/summary?event={eventId}", ct));

    public static List<StandingsTable> ParseStandings(string json)
    {
        var tables = new List<StandingsTable>();
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        var groups = root.TryGetProperty("children", out var ch) && ch.ValueKind == JsonValueKind.Array && ch.GetArrayLength() > 0
            ? ch.EnumerateArray().ToList() : [root];
        foreach (var g in groups)
        {
            if (!g.TryGetProperty("standings", out var s) || !s.TryGetProperty("entries", out var entries)
                || entries.ValueKind != JsonValueKind.Array) continue;
            var rows = new List<(string Team, string Record, double Pct, double Points, double Wins)>();
            foreach (var e in entries.EnumerateArray())
            {
                var t = e.TryGetProperty("team", out var tt) ? tt : default;
                var team = Str(t, "shortDisplayName") ?? Str(t, "displayName") ?? "";
                var stats = new Dictionary<string, JsonElement>();
                if (e.TryGetProperty("stats", out var sa) && sa.ValueKind == JsonValueKind.Array)
                    foreach (var st in sa.EnumerateArray())
                        if (Str(st, "name") is { } n) stats[n] = st;
                double V(string n) => stats.TryGetValue(n, out var x) && x.TryGetProperty("value", out var v) && v.ValueKind == JsonValueKind.Number ? v.GetDouble() : double.NaN;
                var w = V("wins"); var l = V("losses"); var pts = V("points");
                var pct = V("winPercent");
                if (double.IsNaN(pct)) pct = !double.IsNaN(w) && !double.IsNaN(l) && w + l > 0 ? w / (w + l) : 0;
                var record = !double.IsNaN(pts) ? $"{pts:0} pts" : !double.IsNaN(w) && !double.IsNaN(l) ? $"{w:0}-{l:0}" : "";
                if (team.Length > 0) rows.Add((team, record, pct, double.IsNaN(pts) ? double.NegativeInfinity : pts, double.IsNaN(w) ? 0 : w));
            }
            if (rows.Count == 0) continue;
            var sorted = rows.OrderByDescending(r => r.Points).ThenByDescending(r => r.Pct).ThenByDescending(r => r.Wins).ToList();
            tables.Add(new StandingsTable(Str(g, "name") ?? "", sorted.Select((r, i) => new StandingRow(i + 1, r.Team, r.Record, r.Pct)).ToList()));
        }
        return tables;
    }

    /// <summary>Home win probability (0..1) after each play, oldest first.</summary>
    public static List<double> ParseWinProbability(string json)
    {
        var list = new List<double>();
        using var doc = JsonDocument.Parse(json);
        if (doc.RootElement.TryGetProperty("winprobability", out var wp) && wp.ValueKind == JsonValueKind.Array)
            foreach (var p in wp.EnumerateArray())
                if (p.TryGetProperty("homeWinPercentage", out var h) && h.ValueKind == JsonValueKind.Number)
                    list.Add(Math.Clamp(h.GetDouble(), 0, 1));
        return list;
    }

    private static string? Str(JsonElement e, string prop) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(prop, out var v) && v.ValueKind == JsonValueKind.String
            ? v.GetString() : null;
}
