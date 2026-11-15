package com.hienthai.fastowin.server.clanrace

import java.math.BigInteger
import kotlin.test.*

class ClanRacePoolsTest {
    @Test fun strengthSeparatesExperiencedClansAndKeepsNewClansInStarterPools() {
        val clans = (1..41).map { ClanRaceStrength("clan-%03d".format(it), BigInteger.valueOf(it.toLong()), 10) } +
            ClanRaceStrength("starter", BigInteger.ZERO, 50)
        val pools = assignRacePools(clans)
        assertEquals("starter-1", pools.getValue("starter"))
        assertEquals(pools.getValue("clan-041"), pools.getValue("clan-040"))
        assertNotEquals(pools.getValue("clan-041"), pools.getValue("clan-001"))
        assertTrue(pools.values.groupingBy { it }.eachCount().values.all { it <= 20 })
        assertEquals(pools, assignRacePools(clans.reversed()))
    }
    @Test fun activeRosterBreaksEqualContributionStrengthBeforeStableIdentity() {
        val clans = (1..21).map { ClanRaceStrength("clan-$it", BigInteger.ONE, it) }
        val pools = assignRacePools(clans)
        assertNotEquals(pools.getValue("clan-1"), pools.getValue("clan-21"))
        assertEquals("strength-2", pools.getValue("clan-1"))
    }
}
