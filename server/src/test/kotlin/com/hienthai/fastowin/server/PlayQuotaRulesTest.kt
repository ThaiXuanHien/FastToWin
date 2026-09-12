package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.RewardedAdProvider
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayQuotaRulesTest {
    @Test
    fun `quota resets at Bangkok midnight`() {
        val beforeReset = Instant.parse("2026-09-11T16:59:59Z").toEpochMilli()
        val afterReset = Instant.parse("2026-09-11T17:00:00Z").toEpochMilli()

        assertEquals(LocalDate.parse("2026-09-11"), PlayQuotaRules.quotaDate(beforeReset))
        assertEquals(LocalDate.parse("2026-09-12"), PlayQuotaRules.quotaDate(afterReset))
        assertEquals(afterReset, PlayQuotaRules.nextResetAtEpochMillis(beforeReset))
    }

    @Test
    fun `remaining quota includes every verified bonus and never becomes negative`() {
        assertEquals(4, PlayQuotaRules.remaining(matchesConsumed = 10, bonusMatchesGranted = 4))
        assertEquals(0, PlayQuotaRules.remaining(matchesConsumed = 13, bonusMatchesGranted = 2))
    }

    @Test
    fun `in memory quota consumes ten unique matches and rejects the eleventh`() = runTest {
        val repository = InMemoryPlayQuotaRepository()

        repeat(10) { index ->
            val result = repository.consumeMatch(setOf(USER_ID), "match-$index", NOW)
            assertEquals(PlayQuotaConsumptionStatus.CONSUMED, result.status)
        }

        val check = repository.canPlay(setOf(USER_ID), NOW)
        val rejected = repository.consumeMatch(setOf(USER_ID), "match-10", NOW)

        assertFalse(check.allowed)
        assertEquals(setOf(USER_ID), check.exhaustedUserIds)
        assertEquals(PlayQuotaConsumptionStatus.EXHAUSTED, rejected.status)
        assertEquals(0, rejected.quotas.getValue(USER_ID).remainingMatches)
    }

    @Test
    fun `in memory quota does not consume the same match twice`() = runTest {
        val repository = InMemoryPlayQuotaRepository()

        val first = repository.consumeMatch(setOf(USER_ID), "match-1", NOW)
        val duplicate = repository.consumeMatch(setOf(USER_ID), "match-1", NOW)

        assertEquals(PlayQuotaConsumptionStatus.CONSUMED, first.status)
        assertEquals(PlayQuotaConsumptionStatus.ALREADY_CONSUMED, duplicate.status)
        assertEquals(9, duplicate.quotas.getValue(USER_ID).remainingMatches)
    }

    @Test
    fun `in memory rewarded receipt grants two matches exactly once`() = runTest {
        val repository = InMemoryPlayQuotaRepository()

        val first = repository.grantRewardedAd(
            USER_ID,
            RewardedAdProvider.DEV_SIMULATED,
            "transaction-1",
            NOW
        )
        val duplicate = repository.grantRewardedAd(
            USER_ID,
            RewardedAdProvider.DEV_SIMULATED,
            "transaction-1",
            NOW
        )

        assertEquals(RewardedAdBonusStatus.GRANTED, first.status)
        assertEquals(12, first.quota.remainingMatches)
        assertEquals(RewardedAdBonusStatus.ALREADY_GRANTED, duplicate.status)
        assertEquals(12, duplicate.quota.remainingMatches)
    }

    @Test
    fun `new Bangkok day restores ten base matches without carrying bonus`() = runTest {
        val repository = InMemoryPlayQuotaRepository()
        repository.consumeMatch(setOf(USER_ID), "match-1", NOW)
        repository.grantRewardedAd(USER_ID, RewardedAdProvider.DEV_SIMULATED, "transaction-1", NOW)

        val nextDay = repository.getSnapshot(USER_ID, NEXT_DAY)

        assertEquals("2026-09-13", nextDay.quotaDate)
        assertEquals(10, nextDay.remainingMatches)
        assertEquals(0, nextDay.bonusMatchesGranted)
        assertTrue(repository.canPlay(setOf(USER_ID), NEXT_DAY).allowed)
    }

    private companion object {
        const val USER_ID = "00000000-0000-0000-0000-000000000001"
        val NOW: Long = Instant.parse("2026-09-12T10:00:00Z").toEpochMilli()
        val NEXT_DAY: Long = Instant.parse("2026-09-13T10:00:00Z").toEpochMilli()
    }
}
