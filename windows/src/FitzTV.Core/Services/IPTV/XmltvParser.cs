using System.Globalization;
using System.Xml;

namespace FitzTV.Core.Services.IPTV;

/// <summary>Streaming XMLTV reader. Keeps only programmes that have not ended by <c>notBefore</c>.</summary>
public static class XmltvParser
{
    public static Dictionary<string, List<Programme>> Parse(Stream xml, DateTimeOffset notBefore)
    {
        var result = new Dictionary<string, List<Programme>>(StringComparer.OrdinalIgnoreCase);
        var settings = new XmlReaderSettings { DtdProcessing = DtdProcessing.Ignore, XmlResolver = null };
        using var r = XmlReader.Create(xml, settings);
        while (r.Read())
        {
            if (r.NodeType != XmlNodeType.Element || r.Name != "programme") continue;
            var channel = r.GetAttribute("channel");
            var start = ParseTime(r.GetAttribute("start"));
            var stop = ParseTime(r.GetAttribute("stop"));
            string? title = null;
            using (var sub = r.ReadSubtree())
            {
                sub.Read();
                while (sub.Read())
                    if (sub.NodeType == XmlNodeType.Element && sub.Name == "title" && title == null)
                        title = sub.ReadElementContentAsString();
            }
            if (channel == null || start == null || stop == null || string.IsNullOrWhiteSpace(title)) continue;
            if (stop <= notBefore) continue;
            if (!result.TryGetValue(channel, out var list)) result[channel] = list = new();
            list.Add(new Programme(channel, title.Trim(), start.Value, stop.Value));
        }
        foreach (var l in result.Values) l.Sort((a, b) => a.Start.CompareTo(b.Start));
        return result;
    }

    public static (Programme? Now, Programme? Next) NowNext(IReadOnlyList<Programme>? list, DateTimeOffset now)
    {
        if (list == null) return (null, null);
        Programme? cur = null, next = null;
        foreach (var p in list)
        {
            if (p.Start <= now && now < p.Stop) cur = p;
            else if (p.Start > now) { next = p; break; }
        }
        return (cur, next);
    }

    /// <summary>XMLTV time: yyyyMMddHHmmss with optional " +hhmm" offset (UTC when absent).</summary>
    public static DateTimeOffset? ParseTime(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return null;
        s = s.Trim();
        var offset = TimeSpan.Zero;
        var space = s.IndexOf(' ');
        if (space > 0)
        {
            var tz = s[(space + 1)..].Trim();
            s = s[..space];
            if (tz.Length == 5 && (tz[0] == '+' || tz[0] == '-')
                && int.TryParse(tz[1..3], out var h) && int.TryParse(tz[3..], out var m))
                offset = new TimeSpan(h, m, 0) * (tz[0] == '-' ? -1 : 1);
        }
        if (s.Length == 12) s += "00";
        return DateTime.TryParseExact(s, "yyyyMMddHHmmss", CultureInfo.InvariantCulture, DateTimeStyles.None, out var dt)
            ? new DateTimeOffset(dt, offset) : null;
    }
}
