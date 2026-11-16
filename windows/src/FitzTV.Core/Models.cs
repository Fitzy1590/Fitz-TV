namespace FitzTV.Core;

public sealed record Channel(string Id, string Name, string Url, string? Logo = null, string? Group = null,
    string? TvgId = null, string? UserAgent = null, string? Referrer = null);

public sealed record Programme(string ChannelId, string Title, DateTimeOffset Start, DateTimeOffset Stop);

public enum GameState { Scheduled, Live, Final }

public sealed record Game(string Id, string League, string Title, string Home, string Away,
    DateTimeOffset KickoffUtc, GameState State, int? HomeScore, int? AwayScore,
    string? Clock, int? Period, string? HomeLogo, string? AwayLogo, string? HomeColor, string? AwayColor);
