using System.Globalization;

namespace FitzTV.Core.Services.Adapters;

/// <summary>Kickoff time conversion with the OS time zone database (no network call).</summary>
public static class Kickoff
{
    public static DateTimeOffset ToLocal(DateTimeOffset utc, TimeZoneInfo? zone = null) =>
        TimeZoneInfo.ConvertTime(utc, zone ?? TimeZoneInfo.Local);

    public static string Format(DateTimeOffset utc, DateTimeOffset now, TimeZoneInfo? zone = null, CultureInfo? culture = null)
    {
        zone ??= TimeZoneInfo.Local; culture ??= CultureInfo.CurrentCulture;
        var k = ToLocal(utc, zone); var n = ToLocal(now, zone);
        var time = k.ToString("t", culture);
        if (k.Date == n.Date) return time;
        if (k.Date == n.Date.AddDays(1)) return $"Tomorrow {time}";
        return $"{k.ToString("ddd d MMM", culture)} {time}";
    }
}
