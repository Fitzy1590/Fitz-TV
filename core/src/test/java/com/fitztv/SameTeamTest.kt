package com.fitztv

import com.fitztv.data.isTeam
import com.fitztv.data.sameTeam
import com.fitztv.sports.Sport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameTeamTest {
    @Test fun clubSuffixes() {
        assertTrue(sameTeam("Adelaide United", "Adelaide United FC"))
        assertTrue(sameTeam("Melbourne City", "Melbourne City FC"))
        assertTrue(sameTeam("Manchester United", "Manchester United"))
        assertFalse(sameTeam("Melbourne City", "Melbourne Victory"))
        assertFalse(sameTeam("Manchester United", "Manchester City"))
        assertTrue(isTeam("Adelaide United", "Adelaide United FC", Sport.SOCCER))
    }
}
