using System.Text.Json;

namespace FitzTV.Core.Services.Adapters;

/// <summary>
/// ESPN public scoreboard (unofficial, no key). Field names are from memory of the public JSON and
/// must be confirmed against a real response; every field is optional so a change degrades, not crashes.
/// </summary>
public sealed class EspnClient(HttpClient http)
{
    public static string ScoreboardUrl(string sport, string league) =>
        $"https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard";

    public async Task<List<Game>> GetScoreboardAsync(string sport, string league, string leagueName, CancellationToken ct = default) =>
        Parse(await http.GetStringAsync(ScoreboardUrl(sport, league), ct), leagueName);

    public static List<Game> Parse(string json, string leagueName)
    {
        var games = new List<Game>();
        using var doc = JsonDocument.Parse(json);
        if (!doc.RootElement.TryGetProperty("events", out var events) || events.ValueKind != JsonValueKind.Array) return games;
        foreach (var ev in events.EnumerateArray())
        {
            var id = Str(ev, "id");
            if (id == null || !DateTimeOffset.TryParse(Str(ev, "date"), null, System.Globalization.DateTimeStyles.AssumeUniversal, out var kickoff)) continue;
            JsonElement home = default, away = default;
            if (ev.TryGetProperty("competitions", out var comps) && comps.GetArrayLength() > 0
                && comps[0].TryGetProperty("competitors", out var cs))
                foreach (var c in cs.EnumerateArray())
                {
                    if (Str(c, "homeAway") == "home") home = c; else if (Str(c, "homeAway") == "away") away = c;
                }
            var status = ev.TryGetProperty("status", out var st) ? st : default;
            var state = status.ValueKind == JsonValueKind.Object && status.TryGetProperty("type", out var ty)
                ? Str(ty, "state") : null;
            var gs = state switch { "in" => GameState.Live, "post" => GameState.Final, _ => GameState.Scheduled };
            games.Add(new Game(id, leagueName, Str(ev, "shortName") ?? Str(ev, "name") ?? id,
                Team(home, "displayName") ?? "Home", Team(away, "displayName") ?? "Away", kickoff.ToUniversalTime(), gs,
                Score(home), Score(away),
                status.ValueKind == JsonValueKind.Object ? Str(status, "displayClock") : null,
                status.ValueKind == JsonValueKind.Object && status.TryGetProperty("period", out var p) && p.TryGetInt32(out var pi) ? pi : null,
                Team(home, "logo"), Team(away, "logo"), Team(home, "color"), Team(away, "color")));
        }
        return games;
    }

    private static string? Team(JsonElement competitor, string prop) =>
        competitor.ValueKind == JsonValueKind.Object && competitor.TryGetProperty("team", out var t) ? Str(t, prop) : null;

    private static int? Score(JsonElement competitor) =>
        competitor.ValueKind == JsonValueKind.Object && int.TryParse(Str(competitor, "score"), out var s) ? s : null;

    private static string? Str(JsonElement e, string prop) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(prop, out var v)
            ? v.ValueKind switch { JsonValueKind.String => v.GetString(), JsonValueKind.Number => v.GetRawText(), _ => null }
            : null;
}
