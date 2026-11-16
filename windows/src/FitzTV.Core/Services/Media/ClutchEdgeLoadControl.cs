namespace FitzTV.Core.Services.Media;

/// <summary>Picks a read-ahead in seconds: short for close late games, long in breaks, normal otherwise.</summary>
public static class ClutchEdgeLoadControl
{
    public const double Normal = 4.0, Clutch = 1.5, Break = 12.0;

    public static double BufferSeconds(Game? g)
    {
        if (g is not { State: GameState.Live }) return Normal;
        if (g.Clock is { } c && (c.Contains("Half", StringComparison.OrdinalIgnoreCase) || c.Contains("End", StringComparison.OrdinalIgnoreCase)))
            return Break;
        var delta = Math.Abs((g.HomeScore ?? 0) - (g.AwayScore ?? 0));
        return g.Period >= 4 && delta <= 8 ? Clutch : Normal;
    }
}
