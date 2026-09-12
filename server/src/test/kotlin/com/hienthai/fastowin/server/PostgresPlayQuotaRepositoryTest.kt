package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.RewardedAdProvider
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresPlayQuotaRepositoryTest {
    @Test
    fun `match consumption is atomic and idempotent for both players`() = runTest {
        withPlayers { dataSource, firstPlayerId, secondPlayerId ->
            val repository = PostgresPlayQuotaRepository(dataSource)
            val players = setOf(firstPlayerId, secondPlayerId)
            val matchId = UUID.randomUUID().toString()

            val firstConsumption = repository.consumeMatch(players, matchId, NOW)
            assertEquals(PlayQuotaConsumptionStatus.CONSUMED, firstConsumption.status)
            assertEquals(9, firstConsumption.quotas.getValue(firstPlayerId).remainingMatches)
            assertEquals(9, firstConsumption.quotas.getValue(secondPlayerId).remainingMatches)

            val duplicateConsumption = repository.consumeMatch(players, matchId, NOW)
            assertEquals(PlayQuotaConsumptionStatus.ALREADY_CONSUMED, duplicateConsumption.status)
            assertEquals(9, duplicateConsumption.quotas.getValue(firstPlayerId).remainingMatches)
            assertEquals(9, duplicateConsumption.quotas.getValue(secondPlayerId).remainingMatches)

            repeat(9) {
                assertEquals(
                    PlayQuotaConsumptionStatus.CONSUMED,
                    repository.consumeMatch(
                        setOf(firstPlayerId),
                        UUID.randomUUID().toString(),
                        NOW
                    ).status
                )
            }

            val blocked = repository.consumeMatch(players, UUID.randomUUID().toString(), NOW)
            assertEquals(PlayQuotaConsumptionStatus.EXHAUSTED, blocked.status)
            assertEquals(setOf(firstPlayerId), blocked.exhaustedUserIds)
            assertEquals(0, blocked.quotas.getValue(firstPlayerId).remainingMatches)
            assertEquals(9, blocked.quotas.getValue(secondPlayerId).remainingMatches)
            assertFalse(repository.canPlay(players, NOW).allowed)
        }
    }

    @Test
    fun `rewarded ad grant is idempotent and does not carry into the next day`() = runTest {
        withPlayers { dataSource, firstPlayerId, _ ->
            val repository = PostgresPlayQuotaRepository(dataSource)
            val transactionId = UUID.randomUUID().toString()

            val granted = repository.grantRewardedAd(
                firstPlayerId,
                RewardedAdProvider.DEV_SIMULATED,
                transactionId,
                NOW
            )
            assertEquals(RewardedAdBonusStatus.GRANTED, granted.status)
            assertEquals(12, granted.quota.remainingMatches)
            assertEquals(2, granted.quota.bonusMatchesGranted)

            val duplicate = repository.grantRewardedAd(
                firstPlayerId,
                RewardedAdProvider.DEV_SIMULATED,
                transactionId,
                NOW
            )
            assertEquals(RewardedAdBonusStatus.ALREADY_GRANTED, duplicate.status)
            assertEquals(12, duplicate.quota.remainingMatches)
            assertEquals(2, duplicate.quota.bonusMatchesGranted)

            val nextDay = repository.getSnapshot(firstPlayerId, NEXT_DAY)
            assertEquals(LocalDate.of(2030, 3, 19).toString(), nextDay.quotaDate)
            assertEquals(10, nextDay.remainingMatches)
            assertEquals(0, nextDay.bonusMatchesGranted)
            assertTrue(repository.canPlay(setOf(firstPlayerId), NEXT_DAY).allowed)
        }
    }

    private suspend fun withPlayers(
        block: suspend (HikariDataSource, String, String) -> Unit
    ) {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val identityRepository = PostgresGuestIdentityRepository(dataSource)
            val suffix = UUID.randomUUID().toString().take(8)
            val firstPlayer = identityRepository.resolveGuest("Quota A $suffix", null, NOW)
            val secondPlayer = identityRepository.resolveGuest("Quota B $suffix", null, NOW)
            val playerIds = listOf(firstPlayer.playerId, secondPlayer.playerId)
            try {
                block(dataSource, firstPlayer.playerId, secondPlayer.playerId)
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id IN (?, ?)").use { statement ->
                        statement.setObject(1, UUID.fromString(playerIds[0]))
                        statement.setObject(2, UUID.fromString(playerIds[1]))
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private companion object {
        private val BANGKOK = ZoneId.of("Asia/Bangkok")
        private val NOW = LocalDate.of(2030, 3, 18)
            .atTime(12, 0)
            .atZone(BANGKOK)
            .toInstant()
            .toEpochMilli()
        private val NEXT_DAY = LocalDate.of(2030, 3, 19)
            .atTime(12, 0)
            .atZone(BANGKOK)
            .toInstant()
            .toEpochMilli()
    }
}
