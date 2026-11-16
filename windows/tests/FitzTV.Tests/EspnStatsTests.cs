using FitzTV.Core.Services.Adapters;

namespace FitzTV.Tests;

public class EspnStatsTests
{
    // Hand-written samples following the Android parser's shape, not captured responses.
    const string Standings = """
        {"children":[{"name":"East","standings":{"entries":[
          {"team":{"shortDisplayName":"Knicks"},"stats":[{"name":"wins","value":20},{"name":"losses","value":10},{"name":"winPercent","value":0.667}]},
          {"team":{"shortDisplayName":"Celtics"},"stats":[{"name":"wins","value":25},{"name":"losses","value":5},{"name":"winPercent","value":0.833}]},
          {"team":{"displayName":"No Stats"}}]}},
         {"name":"Empty","standings":{"entries":[]}}]}
        """;

    [Fact]
    public void Standings_sorted_by_win_percent()
    {
        var t = Assert.Single(EspnStats.ParseStandings(Standings));
        Assert.Equal("East", t.Name);
        Assert.Equal(new[] { "Celtics", "Knicks", "No Stats" }, t.Rows.Select(r => r.Team).ToArray());
        Assert.Equal("25-5", t.Rows[0].Record);
        Assert.Equal(1, t.Rows[0].Rank);
    }

    [Fact]
    public void Soccer_sorted_by_points()
    {
        const string j = """
          {"standings":{"entries":[
          {"team":{"displayName":"B"},"stats":[{"name":"points","value":10},{"name":"wins","value":3},{"name":"losses","value":4}]},
          {"team":{"displayName":"A"},"stats":[{"name":"points","value":30},{"name":"wins","value":9},{"name":"losses","value":1}]}]}}
          """;
        var t = Assert.Single(EspnStats.ParseStandings(j));
        Assert.Equal("A", t.Rows[0].Team);
        Assert.Equal("30 pts", t.Rows[0].Record);
    }

    [Fact]
    public void Win_probability_parsed_and_clamped() =>
        Assert.Equal(new[] { 0.5, 0.62, 1.0 }, EspnStats.ParseWinProbability("""{"winprobability":[{"homeWinPercentage":0.5},{"homeWinPercentage":0.62},{"homeWinPercentage":1.4},{"x":1}]}""").ToArray());

    [Fact]
    public void Missing_win_probability_is_empty() => Assert.Empty(EspnStats.ParseWinProbability("{}"));
}
