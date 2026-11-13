using System.Text.Json;

namespace FitzTV.Core.Services.IPTV;

/// <summary>Xtream Codes player_api client. Credentials stay on the device and are never logged.</summary>
public sealed class XtreamClient(HttpClient http, string server, string user, string pass)
{
    private readonly string _server = server.TrimEnd('/');
    private string Api(string action) =>
        $"{_server}/player_api.php?username={Uri.EscapeDataString(user)}&password={Uri.EscapeDataString(pass)}&action={action}";

    public string XmltvUrl =>
        $"{_server}/xmltv.php?username={Uri.EscapeDataString(user)}&password={Uri.EscapeDataString(pass)}";

    public string StreamUrl(string streamId, string ext = "m3u8") =>
        $"{_server}/live/{Uri.EscapeDataString(user)}/{Uri.EscapeDataString(pass)}/{streamId}.{ext}";

    public async Task<List<Channel>> GetLiveChannelsAsync(CancellationToken ct = default)
    {
        var cats = ParseCategories(await http.GetStringAsync(Api("get_live_categories"), ct));
        return ParseStreams(await http.GetStringAsync(Api("get_live_streams"), ct), cats);
    }

    public Dictionary<string, string> ParseCategories(string json)
    {
        var map = new Dictionary<string, string>();
        using var doc = JsonDocument.Parse(json);
        if (doc.RootElement.ValueKind != JsonValueKind.Array) return map;
        foreach (var e in doc.RootElement.EnumerateArray())
        {
            var id = Str(e, "category_id");
            var name = Str(e, "category_name");
            if (id != null && name != null) map[id] = name;
        }
        return map;
    }

    public List<Channel> ParseStreams(string json, IReadOnlyDictionary<string, string> categories)
    {
        var list = new List<Channel>();
        using var doc = JsonDocument.Parse(json);
        if (doc.RootElement.ValueKind != JsonValueKind.Array) return list;
        foreach (var e in doc.RootElement.EnumerateArray())
        {
            var id = Str(e, "stream_id");
            var name = Str(e, "name");
            if (id == null || name == null) continue;
            var cat = Str(e, "category_id");
            list.Add(new Channel(id, name, StreamUrl(id), Str(e, "stream_icon"),
                cat != null && categories.TryGetValue(cat, out var g) ? g : null, Str(e, "epg_channel_id")));
        }
        return list;
    }

    // Xtream providers return ids as numbers or strings; accept both.
    private static string? Str(JsonElement e, string prop)
    {
        if (!e.TryGetProperty(prop, out var v)) return null;
        return v.ValueKind switch
        {
            JsonValueKind.String => v.GetString() is { Length: > 0 } s ? s : null,
            JsonValueKind.Number => v.GetRawText(),
            _ => null
        };
    }
}
