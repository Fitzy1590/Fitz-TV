using System.Text;
using FitzTV.Core;
using FitzTV.Core.Services.IPTV;

namespace FitzTV.Tests;

public class M3uParserTests
{
    const string Sample = """
        #EXTM3U
        #EXTINF:-1 tvg-id="espn.us" tvg-logo="http://x/espn.png" group-title="US, Sports",ESPN HD
        #EXTVLCOPT:http-user-agent=Custom/1.0
        #EXTVLCOPT:http-referrer=http://ref/
        http://host/live/1.m3u8
        #EXTINF:-1,No Attrs
        http://host/live/2.ts
        #EXTINF:-1 tvg-id="" group-title="Soccer",
        http://host/live/3.ts
        """;

    [Fact]
    public void Parses_attributes_title_and_options()
    {
        var c = M3uParser.Parse(Sample);
        Assert.Equal(3, c.Count);
        Assert.Equal("ESPN HD", c[0].Name);
        Assert.Equal("espn.us", c[0].TvgId);
        Assert.Equal("US, Sports", c[0].Group);   // comma inside quotes is not the title separator
        Assert.Equal("Custom/1.0", c[0].UserAgent);
        Assert.Equal("http://ref/", c[0].Referrer);
        Assert.Equal("http://host/live/1.m3u8", c[0].Url);
    }

    [Fact]
    public void Missing_title_and_attrs_get_defaults_and_options_do_not_leak()
    {
        var c = M3uParser.Parse(Sample);
        Assert.Equal("No Attrs", c[1].Name);
        Assert.Null(c[1].UserAgent);
        Assert.Equal("Unnamed", c[2].Name);
        Assert.Equal("2", c[2].Id);   // no tvg-id: falls back to list index
    }

    [Fact]
    public void Handles_crlf_and_ignores_stray_urls()
    {
        var c = M3uParser.Parse("#EXTM3U\r\nhttp://orphan\r\n#EXTINF:-1,A\r\nhttp://a\r\n");
        Assert.Single(c);
        Assert.Equal("http://a", c[0].Url);
    }
}

public class XtreamClientTests
{
    static XtreamClient Client() => new(new HttpClient(), "http://srv:8080/", "u s", "p");

    [Fact]
    public void Builds_urls_with_escaped_credentials()
    {
        Assert.Equal("http://srv:8080/live/u%20s/p/42.m3u8", Client().StreamUrl("42"));
        Assert.Contains("xmltv.php?username=u%20s&password=p", Client().XmltvUrl);
    }

    [Fact]
    public void Parses_streams_with_numeric_or_string_ids()
    {
        var c = Client();
        var cats = c.ParseCategories("""[{"category_id":"5","category_name":"Sports"},{"category_id":6,"category_name":"News"}]""");
        var ch = c.ParseStreams("""
            [{"stream_id":42,"name":"ESPN","stream_icon":"i.png","category_id":"5","epg_channel_id":"espn.us"},
             {"stream_id":"43","name":"CNN","category_id":6},
             {"name":"no id"}]
            """, cats);
        Assert.Equal(2, ch.Count);
        Assert.Equal("Sports", ch[0].Group);
        Assert.Equal("espn.us", ch[0].TvgId);
        Assert.Equal("News", ch[1].Group);
        Assert.Equal("http://srv:8080/live/u%20s/p/43.m3u8", ch[1].Url);
    }
}

public class XmltvParserTests
{
    const string Xml = """
        <?xml version="1.0"?>
        <tv>
          <programme start="20261113190000 +0000" stop="20261113200000 +0000" channel="espn.us"><title>Lakers vs Celtics</title></programme>
          <programme start="20261113200000 +0000" stop="20261113210000 +0000" channel="espn.us"><title lang="en">SportsCenter</title></programme>
          <programme start="20261113100000 +0000" stop="20261113110000 +0000" channel="espn.us"><title>Old</title></programme>
          <programme start="20261113190000 -0500" stop="20261113200000 -0500" channel="b"><title>Offset</title></programme>
          <programme start="bad" stop="20261113200000 +0000" channel="c"><title>Bad</title></programme>
        </tv>
        """;

    static Dictionary<string, List<Programme>> Load(DateTimeOffset notBefore) =>
        XmltvParser.Parse(new MemoryStream(Encoding.UTF8.GetBytes(Xml)), notBefore);

    [Fact]
    public void Drops_ended_and_invalid_programmes_and_sorts()
    {
        var d = Load(new DateTimeOffset(2026, 11, 13, 12, 0, 0, TimeSpan.Zero));
        Assert.Equal(2, d["espn.us"].Count);
        Assert.False(d.ContainsKey("c"));
        Assert.Equal("Lakers vs Celtics", d["espn.us"][0].Title);
    }

    [Fact]
    public void Applies_time_offsets()
    {
        var d = Load(DateTimeOffset.MinValue);
        Assert.Equal(new DateTimeOffset(2026, 11, 14, 0, 0, 0, TimeSpan.Zero), d["b"][0].Start.ToUniversalTime());
    }

    [Fact]
    public void NowNext_picks_current_and_following()
    {
        var d = Load(DateTimeOffset.MinValue);
        var (now, next) = XmltvParser.NowNext(d["espn.us"], new DateTimeOffset(2026, 11, 13, 19, 30, 0, TimeSpan.Zero));
        Assert.Equal("Lakers vs Celtics", now!.Title);
        Assert.Equal("SportsCenter", next!.Title);
        Assert.Equal((null, null), XmltvParser.NowNext(null, DateTimeOffset.UtcNow));
    }
}
