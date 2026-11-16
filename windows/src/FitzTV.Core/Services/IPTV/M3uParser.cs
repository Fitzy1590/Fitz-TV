using System.Text.RegularExpressions;

namespace FitzTV.Core.Services.IPTV;

/// <summary>Parses extended M3U playlists (#EXTINF, #EXTVLCOPT user-agent / referrer).</summary>
public static partial class M3uParser
{
    [GeneratedRegex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")]
    private static partial Regex AttrRegex();

    public static List<Channel> Parse(string text)
    {
        var list = new List<Channel>();
        string? name = null, logo = null, group = null, tvgId = null, ua = null, referrer = null;
        foreach (var raw in text.Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0) continue;
            if (line.StartsWith("#EXTINF", StringComparison.OrdinalIgnoreCase))
            {
                (name, logo, group, tvgId) = ParseInfo(line);
                ua = referrer = null;
            }
            else if (line.StartsWith("#EXTVLCOPT:", StringComparison.OrdinalIgnoreCase))
            {
                var opt = line[11..];
                var eq = opt.IndexOf('=');
                if (eq < 0) continue;
                var key = opt[..eq].Trim().ToLowerInvariant();
                var val = opt[(eq + 1)..].Trim();
                if (key == "http-user-agent") ua = val;
                else if (key == "http-referrer") referrer = val;
            }
            else if (line[0] == '#') continue;
            else if (name != null)
            {
                list.Add(new Channel(tvgId is { Length: > 0 } ? tvgId : $"{list.Count}", name, line,
                    logo, group, tvgId, ua, referrer));
                name = logo = group = tvgId = ua = referrer = null;
            }
        }
        return list;
    }

    private static (string name, string? logo, string? group, string? tvgId) ParseInfo(string line)
    {
        string? logo = null, group = null, tvgId = null;
        foreach (Match m in AttrRegex().Matches(line))
        {
            var v = m.Groups[2].Value;
            switch (m.Groups[1].Value.ToLowerInvariant())
            {
                case "tvg-logo": logo = v; break;
                case "group-title": group = v; break;
                case "tvg-id": tvgId = v; break;
            }
        }
        // Title is after the last comma that is outside a quoted attribute value.
        var inQuote = false; var cut = -1;
        for (var i = 0; i < line.Length; i++)
        {
            if (line[i] == '"') inQuote = !inQuote;
            else if (line[i] == ',' && !inQuote) cut = i;
        }
        var name = cut >= 0 ? line[(cut + 1)..].Trim() : "";
        return (name.Length > 0 ? name : "Unnamed", logo, group, tvgId);
    }
}
