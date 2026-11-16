using CommunityToolkit.Mvvm.ComponentModel;
using FitzTV.Core;
using FitzTV.Core.Services.Adapters;
using Microsoft.UI.Xaml.Media.Imaging;

namespace FitzTV.Windows.ViewModels;

public sealed partial class GameRow(Game g, DateTimeOffset now) : ObservableObject
{
    public Game Game => g;
    public bool IsLive => g.State == GameState.Live;
    public string League => g.League;
    public string Title => $"{g.Away} at {g.Home}";
    public string Score => g.HomeScore is { } h && g.AwayScore is { } a ? $"{a} – {h}" : "";
    public string Status => g.State switch
    {
        GameState.Live => string.Join(" · ", new[] { g.Period is { } p ? $"P{p}" : null, g.Clock }.Where(x => !string.IsNullOrEmpty(x))),
        GameState.Final => "Final",
        _ => Kickoff.Format(g.KickoffUtc, now)
    };

    [ObservableProperty] private BitmapImage? _homeCrest;
    [ObservableProperty] private BitmapImage? _awayCrest;
}
