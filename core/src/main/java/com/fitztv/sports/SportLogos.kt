package com.fitztv.sports

/**
 * Which sports show a league logo on their chip. A sport with one flagship competition and a
 * logo in the feed (NBA, NFL, NBL, AFL, UFC ...) uses it; sports spread over many competitions
 * (soccer, cricket, rugby) get a plain sport icon instead of one league's crest. A logo that
 * fails to load also falls back to the sport icon.
 */
object SportLogos {
    private val generic = setOf(Sport.SOCCER, Sport.CRICKET, Sport.NRL, Sport.GENERAL)
    fun useGeneric(sport: Sport?): Boolean = sport == null || sport in generic
}
