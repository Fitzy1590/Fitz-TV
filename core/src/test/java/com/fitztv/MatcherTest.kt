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

    @Test fun replayTitlesRejectOtherTeamsAndOldFixtures() {
        val villa = Game(id = "v", sport = Sport.SOCCER, league = "Premier League", title = "Manchester United at Aston Villa", home = "Aston Villa", away = "Manchester United",
            homeShort = "Aston Villa", awayShort = "Man United", startUtcMillis = java.time.LocalDate.of(2026, 11, 9).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            state = "post", statusText = "FT", homeAbbr = "AVL", awayAbbr = "MAN")
        val others = listOf("Manchester City", "Aston Villa", "Manchester United", "Chelsea")
        assertTrue(com.fitztv.data.ReplayTitles.matches("SOC - Aston Villa vs Manchester United", villa, others))
        assertTrue(!com.fitztv.data.ReplayTitles.matches("SOC - Aston Villa vs Manchester City .25", villa, others))
        assertTrue(!com.fitztv.data.ReplayTitles.matches("SOC - Manchester City - Aston Villa 03.04.2024", villa, others))
        assertTrue(!com.fitztv.data.ReplayTitles.matches("SOC - Aston Villa - Manchester United 06.10.2024", villa, others))
        assertTrue(com.fitztv.data.ReplayTitles.matches("SOC - Aston Villa - Manchester United 09.11.2026", villa, others))
    }

    @Test fun channelLogosMatchByName() {
        val idx = com.fitztv.data.LogoIndex(listOf("australia/espn-2-au", "australia/espn-2-hd-au", "united-states/espn-us", "australia/fox-sports-news-au", "united-kingdom/sky-sports-racing-uk", "mexico/tudn-mx"))
        assertTrue(idx.urlFor("SKY ESPN 2 RAW")!!.endsWith("australia/espn-2-au.png"))
        assertTrue(idx.urlFor("AU: ESPN 2 HD")!!.endsWith("australia/espn-2-au.png"))
        assertTrue(idx.urlFor("FOX Sports News")!!.endsWith("fox-sports-news-au.png"))
        assertTrue(idx.urlFor("SKY Sports Racing")!!.endsWith("sky-sports-racing-uk.png"))
        assertEquals(null, idx.urlFor("Totally Unknown Channel"))
    }

    @Test fun replayLibraryTitlesAndOrder() {
        assertEquals("Wisconsin Badgers at 4 Notre Dame Fighting Irish", com.fitztv.sports.Text.vodTitle("SOC - Wisconsin Badgers at 4 Notre Dame Fighting Irish 06.09.2026"))
        assertEquals("Aston Villa vs Manchester City", com.fitztv.sports.Text.vodTitle("SOC - Aston Villa vs Manchester City .25"))
        fun v(id: String, t: String, sport: Sport = Sport.SOCCER, added: Long = 0) = com.fitztv.data.VodItem(id, t, "SOCCER FOOTBALL", sport, "http://x/$id", null, added)
        val lib = com.fitztv.data.VodLibrary.of(listOf(v("a", "SOC - A vs B 06.10.2024"), v("b", "SOC - C vs D 21.12.2024"), v("c", "NFL - E at F 01.01.2025", Sport.NFL), v("d", "SOC - G vs H 03.04.2024")), Sport.SOCCER)
        assertEquals(listOf("b", "a", "d"), lib.map { it.id })
        assertEquals("21 Dec 2024", com.fitztv.data.VodLibrary.dateLabel(lib[0]))
    }

    @Test fun replayFitSeparatesGameFromEarlierMeetings() {
        val villa = Game(id = "v", sport = Sport.SOCCER, league = "Premier League", title = "Manchester United at Aston Villa", home = "Aston Villa", away = "Manchester United",
            homeShort = "Aston Villa", awayShort = "Man United", startUtcMillis = java.time.LocalDate.of(2026, 11, 9).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            state = "post", statusText = "FT", homeAbbr = "AVL", awayAbbr = "MAN")
        val fg = com.fitztv.data.ReplayTitles.ForGame(villa, listOf("Manchester City", "Chelsea"))
        assertEquals(2, fg.fit("SOC - Aston Villa - Manchester United 09.11.2026"))
        assertEquals(1, fg.fit("SOC - Aston Villa - Manchester United 06.10.2024"))
        assertEquals(0, fg.fit("SOC - Aston Villa vs Manchester City .25"))
    }

    @Test fun categoryLearnerFollowsWatchTimeAndFades() {
        val cl = com.fitztv.data.CategoryLearner()
        val day = 86_400_000L
        val t0 = 1_000_000_000_000L
        val sc = listOf("lg:NBA", "sp:NBA")
        cl.add(sc, "s|us nba", 130 * 60.0, t0)
        cl.add(sc, "s|uk sky", 6 * 60.0, t0)
        assertEquals(4, cl.level(sc, "s|us nba", t0))
        assertEquals(1, cl.level(sc, "s|uk sky", t0))
        assertEquals(0, cl.level(sc, "s|other", t0))
        assertEquals(2, cl.level(sc, "s|us nba", t0 + 60 * day))
        assertEquals(0, cl.level(sc, "s|uk sky", t0 + 60 * day))
        assertEquals(4, cl.level(listOf("lg:NBA Cup", "sp:NBA"), "s|us nba", t0))
    }

    @Test fun multiviewSlotStartsOnPlayingStream() {
        fun ch(id: String) = Channel(id, "s", id, "g", null, "http://x/$id", null, Sport.NBA, 2, id)
        val list = listOf(ch("a"), ch("b"), ch("c"))
        val slot = com.fitztv.player.Multiview.slotFor(null, list, 2)!!
        assertEquals(listOf("c", "a", "b"), slot.channels.map { it.id })
    }

    @Test fun matchConfidenceTiers() {
        val c = GameMatcher.Verdict.CONFIRMED; val u = GameMatcher.Verdict.UNKNOWN
        assertEquals(0, GameMatcher.matchTier(100, u)); assertEquals(0, GameMatcher.matchTier(45, c))
        assertEquals(1, GameMatcher.matchTier(60, u)); assertEquals(2, GameMatcher.matchTier(45, u)); assertEquals(2, GameMatcher.matchTier(20, u))
    }

    @Test fun categoryFirstThenMetadata() {
        val q = com.fitztv.sports.Text
        assertEquals(3, q.qualityTag("UK| SKY SPORTS ᶠᴴᴰ"))
        assertEquals(4, q.qualityTag("US| NBA ⁴ᴷ"))
    }

    @Test fun streamTuningEscalates() {
        val t0 = com.fitztv.player.StreamTuning()
        assertEquals("DEFAULT", t0.userAgent("DEFAULT"))
        val t1 = t0.next(); val t3 = t1.next().next().next()
        assertEquals(1, t1.level); assertEquals(3, t3.level)
        assertTrue(t1.userAgent("DEFAULT") != "DEFAULT")
        assertTrue(t1.bufferFactor > t0.bufferFactor && t3.bufferFactor > t1.bufferFactor)
        assertTrue(t3.rebufferMs > t1.rebufferMs && t1.rebufferMs > t0.rebufferMs)
    }

    @Test fun multiviewStepKeepsAgentAndFormatIsRemembered() {
        val t = com.fitztv.player.StreamTuning()
        assertEquals(0, t.next(keepAgent = true).uaIndex)
        assertEquals(1, t.next().uaIndex)
        assertTrue(t.isDefault)
        assertTrue(!t.copy(altFormat = true).isDefault && !t.copy(vlc = true).isDefault)
        assertTrue(t.copy(altFormat = true).sameBuild(t))
    }
}
