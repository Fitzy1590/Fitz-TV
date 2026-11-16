using System.Text.Json;

namespace FitzTV.Windows.Sports;

public sealed record LiveGame(string Away, string Home, int AwayScore, int HomeScore, int Period, string Clock, bool Live, bool Intermission)
{
    public int Delta => Math.Abs(AwayScore - HomeScore);
}

public static class EspnScores
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(8) };

    public static async Task<List<LiveGame>> FetchAsync(string sport = "basketball", string league = "nba")
    {
        var list = new List<LiveGame>();
        var url = $"https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard";
        using var doc = JsonDocument.Parse(await Http.GetStringAsync(url));
        foreach (var ev in doc.RootElement.GetProperty("events").EnumerateArray())
        {
            var comp = ev.GetProperty("competitions")[0];
            var st = comp.GetProperty("status");
            var type = st.GetProperty("type");
            string state = type.GetProperty("state").GetString() ?? "";
            string name = type.GetProperty("name").GetString() ?? "";
            string away = "", home = ""; int aS = 0, hS = 0;
            foreach (var c in comp.GetProperty("competitors").EnumerateArray())
            {
                var abbr = c.GetProperty("team").GetProperty("abbreviation").GetString() ?? "";
                int.TryParse(c.GetProperty("score").GetString(), out var s);
                if (c.GetProperty("homeAway").GetString() == "home") { home = abbr; hS = s; } else { away = abbr; aS = s; }
            }
            list.Add(new LiveGame(away, home, aS, hS, st.GetProperty("period").GetInt32(),
                st.GetProperty("displayClock").GetString() ?? "", state == "in", name == "STATUS_HALFTIME" || name == "STATUS_END_PERIOD"));
        }
        return list;
    }
}
