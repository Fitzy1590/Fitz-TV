using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using FitzTV.Core;
using FitzTV.Core.Services.Adapters;
using FitzTV.Core.Services.IPTV;
using FitzTV.Windows.Services;

namespace FitzTV.Windows.ViewModels;

public sealed partial class MainViewModel : ObservableObject
{
    private static readonly (string Sport, string League, string Name)[] Leagues =
    [
        ("basketball", "nba", "NBA"), ("football", "nfl", "NFL"), ("hockey", "nhl", "NHL"),
        ("baseball", "mlb", "MLB"), ("soccer", "eng.1", "Premier League"), ("soccer", "usa.1", "MLS")
    ];

    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private List<Channel> _all = [];
    private Dictionary<string, List<Programme>> _epg = new(StringComparer.OrdinalIgnoreCase);

    public ObservableCollection<Channel> Visible { get; } = [];
    public ObservableCollection<GameRow> Games { get; } = [];
    public ObservableCollection<GameRow> LiveGames { get; } = [];

    [ObservableProperty] private string _search = "";
    [ObservableProperty] private string _status = "";
    [ObservableProperty] private string _nowNext = "";
    [ObservableProperty] private bool _hasChannels;
    [ObservableProperty] private bool _isTheater;
    [ObservableProperty] private bool _isDrawerOpen;
    [ObservableProperty] private bool _isOverlayOpen;
    [ObservableProperty] private bool _showFixtures;
    [ObservableProperty] private bool _showSetup;
    [ObservableProperty] private Channel? _selected;

    public bool ShowChannelList => !ShowFixtures;
    partial void OnShowFixturesChanged(bool value) => OnPropertyChanged(nameof(ShowChannelList));
    partial void OnSearchChanged(string value) => ApplyFilter();
    partial void OnSelectedChanged(Channel? value) => UpdateNowNext();

    public async Task LoadSourceAsync(SourceSettings s)
    {
        Status = "Loading channels…";
        try
        {
            if (s.M3uUrl.Length > 0)
                _all = M3uParser.Parse(await _http.GetStringAsync(s.M3uUrl));
            else
            {
                var x = new XtreamClient(_http, s.XtreamServer, s.XtreamUser, SettingsStore.Unprotect(s.XtreamPassProtected));
                _all = await x.GetLiveChannelsAsync();
                _ = LoadEpgAsync(x.XmltvUrl);
            }
            ApplyFilter();
            HasChannels = _all.Count > 0;
            Status = _all.Count == 0 ? "The playlist has no channels." : "";
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or UriFormatException or InvalidOperationException)
        {
            // Never include the exception text: Xtream URLs carry the account credentials.
            Status = "Could not load the playlist. Check the address and login.";
        }
    }

    private async Task LoadEpgAsync(string url)
    {
        try
        {
            await using var stream = await _http.GetStreamAsync(url);
            _epg = await Task.Run(() => XmltvParser.Parse(stream, DateTimeOffset.UtcNow.AddHours(-1)));
            UpdateNowNext();
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or System.Xml.XmlException) { }
    }

    public async Task RefreshGamesAsync()
    {
        var espn = new EspnClient(_http);
        var all = new List<Game>();
        foreach (var (sport, league, name) in Leagues)
        {
            try { all.AddRange(await espn.GetScoreboardAsync(sport, league, name)); }
            catch (Exception e) when (e is HttpRequestException or TaskCanceledException or System.Text.Json.JsonException) { }
        }
        var now = DateTimeOffset.UtcNow;
        var rows = all.OrderBy(g => g.State == GameState.Live ? 0 : g.State == GameState.Scheduled ? 1 : 2)
                      .ThenBy(g => g.KickoffUtc).Select(g => new GameRow(g, now)).ToList();
        Games.Clear(); LiveGames.Clear();
        foreach (var r in rows) { Games.Add(r); if (r.IsLive) LiveGames.Add(r); }
    }

    /// <summary>The selected channel first, then other channels with the same name as failover candidates.</summary>
    public List<Channel> CandidatesFor(Channel c) =>
        [c, .. _all.Where(o => o != c && string.Equals(o.Name.Trim(), c.Name.Trim(), StringComparison.OrdinalIgnoreCase))];

    public Channel? Step(int delta)
    {
        if (Visible.Count == 0) return null;
        var i = Selected == null ? -1 : Visible.IndexOf(Selected);
        return Visible[(i + delta + Visible.Count) % Visible.Count];
    }

    public void UpdateNowNext()
    {
        if (Selected?.TvgId is not { } id) { NowNext = ""; return; }
        var (now, next) = XmltvParser.NowNext(_epg.GetValueOrDefault(id), DateTimeOffset.UtcNow);
        NowNext = string.Join("   ", new[] { now != null ? $"Now: {now.Title}" : null, next != null ? $"Next: {next.Title}" : null }
            .Where(x => x != null));
    }

    private void ApplyFilter()
    {
        var q = Search.Trim();
        var items = q.Length == 0 ? _all : _all.Where(c =>
            c.Name.Contains(q, StringComparison.OrdinalIgnoreCase) || (c.Group?.Contains(q, StringComparison.OrdinalIgnoreCase) ?? false));
        Visible.Clear();
        foreach (var c in items) Visible.Add(c);
    }
}
