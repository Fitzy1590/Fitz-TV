package com.fitztv

import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameMatcher
import com.fitztv.sports.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {
    private fun ch(id: String, name: String, sport: Sport = Sport.NBA) =
        Channel(id = id, sourceId = "s", name = name, group = "US| NBA", logo = null, streamUrl = "http://x/$id", epgId = null, sport = sport, quality = 3, variantKey = id)

    private fun game(id: String, away: String, awayShort: String, awayAbbr: String, home: String, homeShort: String, homeAbbr: String) =
        Game(id = id, sport = Sport.NBA, league = "NBA", title = "$away at $home", home = home, away = away, homeShort = homeShort, awayShort = awayShort,
            startUtcMillis = System.currentTimeMillis(), state = "in", statusText = "Q2", homeAbbr = homeAbbr, awayAbbr = awayAbbr)

    private val nets = game("g1", "Brooklyn Nets", "Nets", "BKN", "Dallas Mavericks", "Mavericks", "DAL")
    private val jazz = game("g2", "Utah Jazz", "Jazz", "UTAH", "Minnesota Timberwolves", "Timberwolves", "MIN")
    private val knicks = game("g3", "New York Knicks", "Knicks", "NY", "Boston Celtics", "Celtics", "BOS")

    @Test fun abbreviationsCitiesAndShortForms() {
        val chans = listOf(ch("a", "NBA 05: BKN @ DAL"), ch("b", "Brooklyn vs Dallas | NBA League Pass"), ch("c", "Nets vs Mavs (HD)"), ch("d", "Sky Sports Main Event", Sport.GENERAL))
        val m = GameMatcher(chans, emptyMap()) { listOf(nets, jazz, knicks) }
        val found = m.scoredFor(nets).map { it.first.id }.toSet()
        assertTrue("found $found", found.containsAll(listOf("a", "b", "c")))
        assertTrue(!found.contains("d"))
    }

    @Test fun oneTeamAgainstSomeoneElseIsADifferentGame() {
        val other = ch("k", "Knicks (NYK) x Timberwolves (MIN) starts 11:00")
        val m = GameMatcher(listOf(other), emptyMap()) { listOf(nets, jazz, knicks) }
        assertEquals(GameMatcher.Verdict.CONTRADICTED, m.verdict(other, jazz, System.currentTimeMillis()))
        assertTrue(m.scoredFor(jazz).none { it.second >= 60 })
    }

    @Test fun emptyEventSlotsAreRuledOut() {
        val m = GameMatcher(emptyList(), emptyMap()) { listOf(nets) }
        assertTrue(m.isPlaceholder("NO Event Streaming - | Exclusive | US: NBA PASS PPV 05"))
        assertEquals(GameMatcher.Verdict.CONTRADICTED, m.verdict(ch("p", "NO Event Streaming - | Exclusive | US: NBA PASS PPV 05"), nets, System.currentTimeMillis()))
        assertTrue(!m.isPlaceholder("Nets vs Mavericks | NBA League Pass"))
        assertTrue(!m.isPlaceholder("Snow Events Live"))
    }
}
