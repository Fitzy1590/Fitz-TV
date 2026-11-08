package com.fitztv

import com.fitztv.sports.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityTagTest {
    @Test fun superscriptAndPlainTags() {
        assertEquals(4, Text.qualityTag("Eleven Sports 1 ³⁸⁴⁰ᴾ"))
        assertEquals(4, Text.qualityTag("US| NBA PASS PPV ⁸ᴷ"))
        assertEquals(3, Text.qualityTag("beIN Sports 1 ᶠᴴᴰ"))
        assertEquals(3, Text.qualityTag("ESPN 1080p 50fps"))
        assertEquals(4, Text.qualityTag("Sky Sports Main Event UHD"))
        assertEquals(1, Text.qualityTag("Fox Sports 503 SD"))
        assertNull(Text.qualityTag("Fox Footy"))
        assertNull(Text.qualityTag("SKY Sport Select ᴿᴬᵂ"))
    }
}
