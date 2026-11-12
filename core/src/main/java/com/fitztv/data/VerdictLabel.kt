package com.fitztv.data

/** Short label for the verdict strip (TV player overlay, phone player). */
fun GameMatcher.Verdict.label(): String = when (this) {
    GameMatcher.Verdict.CONFIRMED -> "Confirmed"
    GameMatcher.Verdict.UNKNOWN -> "Unverified"
    GameMatcher.Verdict.CONTRADICTED -> "Other event"
}
