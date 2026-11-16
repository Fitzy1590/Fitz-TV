namespace FitzTV.Windows.Engine;

public readonly record struct GameState(int Period, int ScoreDelta, bool Intermission, bool IsLive);

/// <summary>Picks a read-ahead (seconds): short for close late games, long in breaks.</summary>
public static class ClutchEdgeLoadControl
{
    public const double Normal = 4.0, Clutch = 1.5, Break = 12.0;

    public static double BufferSeconds(GameState g)
    {
        if (!g.IsLive) return Normal;
        if (g.Intermission) return Break;
        return g.Period >= 4 && g.ScoreDelta <= 8 ? Clutch : Normal;
    }
}
