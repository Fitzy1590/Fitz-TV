using System.Globalization;
using FitzTV.Core;
using FitzTV.Core.Services.Adapters;
using FitzTV.Core.Services.Media;

namespace FitzTV.Tests;

public class EspnClientTests
{
    // Hand-written sample following the remembered ESPN shape. Not a captured response.
    const string Json = """
        {"events":[
         {"id":"401","date":"2026-11-13T00:30Z","shortName":"BOS @ LAL",
          "status":{"displayClock":"4:12","period":3,"type":{"state":"in"}},
          "competitions":[{"competitors":[
            {"homeAway":"home","score":"104","team":{"displayName":"Los Angeles Lakers","logo":"l.png","color":"552583"}},
            {"homeAway":"away","score":"98","team":{"displayName":"Boston Celtics","logo":"b.png","color":"007a33"}}]}]},
         {"id":"402","date":"2026-11-14T02:00Z","name":"Future Game","competitions":[]},
         {"date":"2026-11-14T02:00Z"}
        ]}
        """;

    [Fact]
    public void Parses_live_game()
    {
        var g = EspnClient.Parse(Json, "NBA");
        Assert.Equal(2, g.Count);
        Assert.Equal(GameState.Live, g[0].State);
        Assert.Equal((104, 98), (g[0].HomeScore, g[0].AwayScore));
        Assert.Equal("4:12", g[0].Clock);
        Assert.Equal(3, g[0].Period);
        Assert.Equal("Los Angeles Lakers", g[0].Home);
        Assert.Equal("l.png", g[0].HomeLogo);
        Assert.Equal(new DateTimeOffset(2026, 11, 13, 0, 30, 0, TimeSpan.Zero), g[0].KickoffUtc);
    }

    [Fact]
    public void Missing_fields_degrade_instead_of_throwing()
    {
        var g = EspnClient.Parse(Json, "NBA")[1];
        Assert.Equal(GameState.Scheduled, g.State);
        Assert.Null(g.HomeScore);
        Assert.Equal("Home", g.Home);
        Assert.Empty(EspnClient.Parse("{}", "NBA"));
    }

    [Fact]
    public void Builds_scoreboard_url() =>
        Assert.Equal("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard",
            EspnClient.ScoreboardUrl("basketball", "nba"));
}

public class TheSportsDbTests
{
    [Fact]
    public void Parses_badge_and_handles_empty()
    {
        Assert.Equal("https://x/b.png", TheSportsDbClient.ParseBadge("""{"teams":[{"strBadge":"https://x/b.png"}]}"""));
        Assert.Null(TheSportsDbClient.ParseBadge("""{"teams":null}"""));
        Assert.Null(TheSportsDbClient.ParseBadge("""{"teams":[{}]}"""));
    }
}

public class KickoffTests
{
    [Fact]
    public void Converts_utc_to_zone_and_formats_relative_day()
    {
        var zone = TimeZoneInfo.FindSystemTimeZoneById("America/Los_Angeles");
        var utc = new DateTimeOffset(2026, 11, 14, 3, 30, 0, TimeSpan.Zero); // 19:30 PST on the 13th
        Assert.Equal(19, Kickoff.ToLocal(utc, zone).Hour);
        var now = new DateTimeOffset(2026, 11, 13, 18, 0, 0, TimeSpan.Zero);
        Assert.Equal("7:30 PM", Kickoff.Format(utc, now, zone, CultureInfo.GetCultureInfo("en-US")).Replace('\u202F', ' ')); // ICU uses a narrow no-break space
        Assert.StartsWith("Tomorrow", Kickoff.Format(utc.AddDays(1), now, zone, CultureInfo.GetCultureInfo("en-US")));
    }
}

public class PlaybackControllerTests
{
    sealed class Fake : IPlayerEngine
    {
        public List<string> Played = new(); public double? TimePos { get; set; }
        public void Play(string url, string? ua, string? r) { Played.Add(url); TimePos = null; }
        public void Stop() => Played.Add("stop");
    }

    static Channel Ch(string n) => new(n, n, "http://" + n);
    static readonly DateTimeOffset T0 = new(2026, 11, 13, 12, 0, 0, TimeSpan.Zero);

    [Fact]
    public void Fails_over_when_stream_never_starts()
    {
        var e = new Fake(); var c = new PlaybackController(e);
        c.Start([Ch("a"), Ch("b")], T0);
        c.Tick(T0.AddSeconds(10));
        Assert.Equal(["http://a"], e.Played);
        c.Tick(T0.AddSeconds(16));
        Assert.Equal(["http://a", "http://b"], e.Played);
        Assert.Equal("b", c.Current!.Id);
    }

    [Fact]
    public void Fails_over_when_playback_stalls_but_not_while_progressing()
    {
        var e = new Fake(); var c = new PlaybackController(e);
        c.Start([Ch("a"), Ch("b")], T0);
        e.TimePos = 1; c.Tick(T0.AddSeconds(1));
        e.TimePos = 5; c.Tick(T0.AddSeconds(5));
        c.Tick(T0.AddSeconds(10));                       // 5 s since progress: still ok
        Assert.Single(e.Played);
        c.Tick(T0.AddSeconds(14));                       // 9 s since progress: stalled
        Assert.Equal("http://b", e.Played[^1]);
    }

    [Fact]
    public void Marks_failed_after_last_candidate_and_handles_empty()
    {
        var e = new Fake(); var c = new PlaybackController(e);
        c.Start([Ch("a")], T0);
        c.Tick(T0.AddSeconds(20));
        Assert.True(c.Failed);
        c.Start([], T0);
        Assert.True(c.Failed);
    }
}
