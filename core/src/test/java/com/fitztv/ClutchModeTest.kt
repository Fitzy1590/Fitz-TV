package com.fitztv

import com.fitztv.data.adapters.Situation
import com.fitztv.player.BufferMode
import com.fitztv.player.ClutchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClutchModeTest {
    private fun live(period: Int, delta: Int, brk: Boolean = false) = Situation(true, period, 300, delta, brk)

    @Test fun closeFourthQuarterIsClutch() {
        assertEquals(BufferMode.CLUTCH, ClutchPolicy.next(BufferMode.NORMAL, live(4, 8)))
        assertEquals(BufferMode.CLUTCH, ClutchPolicy.next(BufferMode.NORMAL, live(5, 3)))   // overtime
    }

    @Test fun notClutchEarlyOrWhenFarApart() {
        assertEquals(BufferMode.NORMAL, ClutchPolicy.next(BufferMode.NORMAL, live(3, 2)))
        assertEquals(BufferMode.NORMAL, ClutchPolicy.next(BufferMode.NORMAL, live(4, 9)))
        assertEquals(BufferMode.NORMAL, ClutchPolicy.next(BufferMode.NORMAL, Situation.NONE))
    }

    @Test fun clutchHasHysteresis() {
        assertEquals(BufferMode.CLUTCH, ClutchPolicy.next(BufferMode.CLUTCH, live(4, 10)))
        assertEquals(BufferMode.NORMAL, ClutchPolicy.next(BufferMode.CLUTCH, live(4, 11)))
    }

    @Test fun breaksWinOverClutch() {
        assertEquals(BufferMode.BREAK, ClutchPolicy.next(BufferMode.CLUTCH, live(4, 2, brk = true)))
        assertEquals(BufferMode.BREAK, ClutchPolicy.next(BufferMode.NORMAL, live(2, 20, brk = true)))
    }

    @Test fun finishedGameIsNormal() {
        assertEquals(BufferMode.NORMAL, ClutchPolicy.next(BufferMode.BREAK, Situation(false, 4, 0, 5, true)))
    }

    @Test fun profilesOrderedAndSafe() {
        val n = ClutchPolicy.profile(BufferMode.NORMAL, 20_000, 40_000, 1_500, 4_000)
        val c = ClutchPolicy.profile(BufferMode.CLUTCH, 20_000, 40_000, 1_500, 4_000)
        val b = ClutchPolicy.profile(BufferMode.BREAK, 20_000, 40_000, 1_500, 4_000)
        assertTrue(c.maxUs < n.minUs && c.minUs >= 3_000_000 && c.startUs <= n.startUs)
        assertTrue(b.minUs >= n.maxUs && b.maxUs > n.maxUs && b.maxUs <= 120_000_000)
        // A stream that needed self-heal escalation keeps a proportionally higher floor in clutch mode.
        val steady = ClutchPolicy.profile(BufferMode.CLUTCH, 20_000, 40_000, 1_500, 4_000, safety = 3.0)
        assertTrue(steady.minUs == 9_000_000L && steady.rebufferUs == 4_000_000L)
    }
}

class SoccerClutchTest {
    private fun soccer(period: Int, minute: Int, delta: Int, brk: Boolean = false) =
        Situation(true, period, minute * 60, delta, brk, soccer = true)

    @Test fun lateCloseMatchIsClutch() {
        assertEquals(BufferMode.CLUTCH, ClutchPolicy.next(BufferMode.NORMAL, soccer(2, 78, 1)))
        assertEquals(BufferMode.CLUTCH, ClutchPolicy.next(BufferMode.NORMAL, soccer(2, 93, 0)))   // stoppage time
    }

    @Test fun earlyOrLopsidedIsNormal() {
        assertEquals(BufferMode.NORMAL, ClutchPolicy.next(BufferMode.NORMAL, soccer(2, 60, 0)))
        assertEquals(BufferMode.NORMAL, ClutchPolicy.next(BufferMode.NORMAL, soccer(2, 85, 2)))
        assertEquals(BufferMode.CLUTCH, ClutchPolicy.next(BufferMode.CLUTCH, soccer(2, 85, 2)))   // hysteresis
        assertEquals(BufferMode.BREAK, ClutchPolicy.next(BufferMode.NORMAL, soccer(1, 45, 0, brk = true)))
    }

    @Test fun matchMinuteParsing() {
        assertEquals(67, com.fitztv.data.adapters.EspnLive.matchMinute("67'"))
        assertEquals(93, com.fitztv.data.adapters.EspnLive.matchMinute("90'+3'"))
        assertEquals(45, com.fitztv.data.adapters.EspnLive.matchMinute("45:00"))
        assertEquals(0, com.fitztv.data.adapters.EspnLive.matchMinute(""))
    }
}
