using FitzTV.Core;
using FitzTV.Core.Services.Media;

namespace FitzTV.Tests;

public class ClutchEdgeTests
{
    private static Game G(int home, int away, int period, string clock = "5:00", GameState s = GameState.Live) =>
        new("1", "NBA", "t", "H", "A", DateTimeOffset.UtcNow, s, home, away, clock, period, null, null, null, null);

    [Fact] public void CloseFourthQuarterShortensBuffer() => Assert.Equal(ClutchEdgeLoadControl.Clutch, ClutchEdgeLoadControl.BufferSeconds(G(108, 104, 4)));
    [Fact] public void BlowoutKeepsNormal() => Assert.Equal(ClutchEdgeLoadControl.Normal, ClutchEdgeLoadControl.BufferSeconds(G(120, 90, 4)));
    [Fact] public void EarlyPeriodKeepsNormal() => Assert.Equal(ClutchEdgeLoadControl.Normal, ClutchEdgeLoadControl.BufferSeconds(G(50, 50, 2)));
    [Fact] public void HalftimeExpandsBuffer() => Assert.Equal(ClutchEdgeLoadControl.Break, ClutchEdgeLoadControl.BufferSeconds(G(50, 50, 2, "Halftime")));
    [Fact] public void NoGameIsNormal() => Assert.Equal(ClutchEdgeLoadControl.Normal, ClutchEdgeLoadControl.BufferSeconds(null));
    [Fact] public void FinalIsNormal() => Assert.Equal(ClutchEdgeLoadControl.Normal, ClutchEdgeLoadControl.BufferSeconds(G(100, 99, 4, "0:00", GameState.Final)));
}
