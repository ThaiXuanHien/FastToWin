package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.GOLD_EXCHANGE_OFFERS
import com.hienthai.fastowin.protocol.GoldExchangeStatus
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresGoldExchangeTest {
    @Test
    fun `exchange is atomic idempotent and rejects insufficient gems`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Gold exchange test", null, 1_000L)
            val playerId = UUID.fromString(player.playerId)
            val repository = PostgresPlayerProfileRepository(dataSource)
            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (user_id, gold, gems, updated_at)
                        VALUES (?, 0, 100, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE
                        SET gold = 0, gems = 100, updated_at = CURRENT_TIMESTAMP
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, playerId)
                        statement.executeUpdate()
                    }
                }

                val offer = GOLD_EXCHANGE_OFFERS.first()
                val requestId = UUID.randomUUID().toString()
                assertEquals(
                    GoldExchangeStatus.GRANTED,
                    repository.exchangeGemsForGold(player.playerId, requestId, offer)
                )
                repository.findByPlayerId(player.playerId)!!.progression.let { progression ->
                    assertEquals(90, progression.gems)
                    assertEquals(1_000, progression.gold)
                }

                assertEquals(
                    GoldExchangeStatus.ALREADY_GRANTED,
                    repository.exchangeGemsForGold(player.playerId, requestId, offer)
                )
                repository.findByPlayerId(player.playerId)!!.progression.let { progression ->
                    assertEquals(90, progression.gems)
                    assertEquals(1_000, progression.gold)
                }
                assertEquals(
                    1,
                    repository.loadWalletHistory(player.playerId).count {
                        it.sourceType == "GOLD_EXCHANGE" && it.sourceId == requestId
                    }
                )

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "UPDATE player_stats SET gems = 5 WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, playerId)
                        statement.executeUpdate()
                    }
                }
                val expensiveOffer = GOLD_EXCHANGE_OFFERS.last()
                assertEquals(
                    GoldExchangeStatus.INSUFFICIENT_GEMS,
                    repository.exchangeGemsForGold(
                        player.playerId,
                        UUID.randomUUID().toString(),
                        expensiveOffer
                    )
                )
                repository.findByPlayerId(player.playerId)!!.progression.let { progression ->
                    assertEquals(5, progression.gems)
                    assertEquals(1_000, progression.gold)
                }
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        statement.setObject(1, playerId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
