package com.hienthai.fastowin.server.clanrace

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import com.hienthai.fastowin.protocol.MatchRewardEventSnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.server.MatchOutcome
import com.hienthai.fastowin.server.RewardEconomy

class ClanRaceRulesTest {
    @Test fun exactRatesAndSplitDonations() {
        val rate=parseRaceRateUnits("0.01")
        assertEquals(BigInteger("10000"),rate)
        assertEquals(BigInteger("1000000"),raceDonationUnits(100,rate))
        assertEquals(BigInteger("1000000"),(1..100).fold(BigInteger.ZERO) { sum,_ -> sum+raceDonationUnits(1,rate) })
        assertEquals(BigInteger.ONE,parseRaceRateUnits("0.000001"))
        assertEquals("0.000001",racePoints(BigInteger.ONE))
        for(text in listOf("0","-1","1e2","0.0000001"," 1","1.","999999999999999999999999999999999")) assertFailsWith<IllegalArgumentException>(text) { parseRaceRateUnits(text) }
        assertFailsWith<IllegalArgumentException> { raceDonationUnits(-1,rate) }
        assertFailsWith<IllegalArgumentException> { checkedRaceUnits(BigInteger.TEN.pow(38)) }
        assertEquals(BigInteger.TEN.pow(38)-BigInteger.ONE,checkedRaceUnits(BigInteger.TEN.pow(38)-BigInteger.ONE))
    }
    @Test fun mondayBoundaryUsesBangkok() {
        assertEquals(LocalDate.parse("2026-10-19"),raceWeekStart(Instant.parse("2026-10-25T16:59:59.999Z").toEpochMilli()))
        assertEquals(LocalDate.parse("2026-10-26"),raceWeekStart(Instant.parse("2026-10-25T17:00:00Z").toEpochMilli()))
    }

    @Test fun clanWinBonusUsesBaseGoldAndLeavesOtherOutcomesAlone() {
        val event = MatchRewardEventSnapshot(goldMultiplier = 2)
        fun ranked(level:Int, outcome:MatchOutcome = MatchOutcome.WIN, left:Boolean = false) =
            RewardEconomy.matchRewardBreakdown(MatchType.RANKED, outcome, left, event, level)

        assertEquals(200, ranked(4).totalGold)
        assertEquals(5, ranked(5).clanBonusGold)
        assertEquals(205, ranked(9).totalGold)
        assertEquals(210, ranked(10).totalGold)
        assertEquals(210, ranked(100).totalGold)
        assertEquals(100, ranked(10, MatchOutcome.DRAW).totalGold)
        assertEquals(50, ranked(10, MatchOutcome.LOSS).totalGold)
        assertEquals(0, ranked(10, left = true).totalGold)
        assertEquals(84, RewardEconomy.matchRewardBreakdown(
            MatchType.CASUAL, MatchOutcome.WIN, false, MatchRewardEventSnapshot(), 5
        ).totalGold)
    }
}
