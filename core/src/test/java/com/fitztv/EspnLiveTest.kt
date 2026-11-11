package com.fitztv

import com.fitztv.data.adapters.EspnLive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnLiveTest {
    // Trimmed from a real ESPN NBA summary: live Q4, halftime-free, last play a timeout.
    private val live = """
    {"header":{"competitions":[{"status":{"displayClock":"4:12","period":4,"type":{"name":"STATUS_IN_PROGRESS","state":"in","shortDetail":"4:12 - 4th Quarter"}},
      "competitors":[{"homeAway":"home","score":"101","team":{"abbreviation":"NY"}},{"homeAway":"away","score":"96","team":{"abbreviation":"MIL"}}]}]},
     "winprobability":[{"homeWinPercentage":0.5},{"homeWinPercentage":0.62},{"homeWinPercentage":0.71}],
     "boxscore":{"teams":[
       {"homeAway":"away","team":{"abbreviation":"MIL"},"statistics":[
         {"name":"fieldGoalsMade-fieldGoalsAttempted","displayValue":"30-79"},{"name":"fieldGoalPct","displayValue":"38"},
         {"name":"threePointFieldGoalsMade-threePointFieldGoalsAttempted","displayValue":"16-44"},{"name":"threePointFieldGoalPct","displayValue":"36"},
         {"name":"freeThrowsMade-freeThrowsAttempted","displayValue":"9-10"},{"name":"freeThrowPct","displayValue":"90"}]},
       {"homeAway":"home","team":{"abbreviation":"NY"},"statistics":[
         {"name":"fieldGoalsMade-fieldGoalsAttempted","displayValue":"40-80"},{"name":"fieldGoalPct","displayValue":"50"},
         {"name":"threePointFieldGoalsMade-threePointFieldGoalsAttempted","displayValue":"12-30"},{"name":"threePointFieldGoalPct","displayValue":"40"},
         {"name":"freeThrowsMade-freeThrowsAttempted","displayValue":"9-12"},{"name":"freeThrowPct","displayValue":"75"}]}]},
     "plays":[{"type":{"text":"Jump Shot"}},{"type":{"text":"Full Timeout"}}]}
    """.trimIndent()

    @Test fun parsesLiveGame() {
        val s = EspnLive.parse(Json.parseToJsonElement(live))
        assertNotNull(s)
        s!!
        assertTrue(s.live)
        assertEquals(4, s.period)
        assertEquals(252, s.clockSeconds)
        assertEquals("NY", s.homeName); assertEquals("MIL", s.awayName)
        assertEquals(101, s.homeScore); assertEquals(96, s.awayScore)
        assertEquals(0.71, s.homeWinProb!!, 1e-9)
        assertEquals(3, s.momentum.size)
        assertEquals(listOf("FG", "3PT", "FT"), s.shooting.map { it.label })
        assertEquals("30-79", s.shooting[0].away); assertEquals("50%", s.shooting[0].homePct)
        assertTrue("a timeout is a break", s.inBreak)
        val sit = s.situation()
        assertEquals(5, sit.scoreDelta)
        assertTrue(sit.live && sit.inBreak)
    }

    @Test fun missingFeedsDegradeGracefully() {
        val s = EspnLive.parse(Json.parseToJsonElement("""{"header":{"competitions":[{"status":{"type":{"state":"pre"}},"competitors":[]}]}}"""))!!
        assertFalse(s.live)
        assertEquals(null, s.homeWinProb)
        assertTrue(s.shooting.isEmpty())
        assertEquals(null, EspnLive.parse(Json.parseToJsonElement("{}")))
    }

    @Test fun clockParsing() {
        assertEquals(252, EspnLive.clockToSeconds("4:12"))
        assertEquals(38, EspnLive.clockToSeconds("38.5"))
        assertEquals(0, EspnLive.clockToSeconds("0.0"))
        assertEquals(0, EspnLive.clockToSeconds(""))
    }
}
