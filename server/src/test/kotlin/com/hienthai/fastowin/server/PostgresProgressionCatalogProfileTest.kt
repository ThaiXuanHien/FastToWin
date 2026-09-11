package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.FRAME_CATALOG
import com.hienthai.fastowin.protocol.TITLE_CATALOG
import com.hienthai.fastowin.protocol.CosmeticType
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresProgressionCatalogProfileTest {
    @Test
    fun `profile exposes the complete progression catalogs before items are unlocked`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        testDataSource(url).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Catalog locked items", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            try {
                val cosmetics = PostgresPlayerProfileRepository(dataSource)
                    .findByPlayerId(player.playerId)!!
                    .progression.cosmetics

                assertEquals(
                    FRAME_CATALOG.map { it.id }.toSet(),
                    cosmetics.filter { it.type == CosmeticType.FRAME && it.id in FRAME_CATALOG.map { item -> item.id } }
                        .map { it.id }
                        .toSet()
                )
                assertEquals(
                    TITLE_CATALOG.map { it.id }.toSet(),
                    cosmetics.filter { it.type == CosmeticType.TITLE && it.id in TITLE_CATALOG.map { item -> item.id } }
                        .map { it.id }
                        .toSet()
                )
                assertTrue(cosmetics.filter { it.id in FRAME_CATALOG.map { item -> item.id } }.none { it.unlocked })
                assertTrue(cosmetics.filter { it.id in TITLE_CATALOG.map { item -> item.id } }.none { it.unlocked })
                (FRAME_CATALOG + TITLE_CATALOG).forEach { definition ->
                    val cosmetic = cosmetics.single { it.id == definition.id }
                    assertEquals(definition.nameKey, cosmetic.nameKey)
                    assertEquals(definition.unlockDescriptionKey, cosmetic.unlockDescriptionKey)
                }
                assertTrue(cosmetics.single { it.id == "frame_default" }.unlocked)
                assertTrue(cosmetics.single { it.id == "title_rookie" }.unlocked)
            } finally {
                deleteUser(dataSource, userId)
            }
        }
    }

    @Test
    fun `only receipt backed catalog items can be equipped`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        testDataSource(url).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Catalog owned items", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
                        VALUES (?, 'frame_lightning', 'FRAME', CURRENT_TIMESTAMP),
                               (?, 'title_godspeed', 'TITLE', CURRENT_TIMESTAMP)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.setObject(2, userId)
                        statement.executeUpdate()
                    }
                }

                val repository = PostgresPlayerProfileRepository(dataSource)
                assertTrue(repository.equipCosmetics(player.playerId, "frame_lightning", "title_godspeed"))
                assertFalse(repository.equipCosmetics(player.playerId, "frame_peerless", "title_speed_king"))

                val cosmetics = repository.findByPlayerId(player.playerId)!!.progression.cosmetics
                assertTrue(cosmetics.single { it.id == "frame_lightning" }.unlocked)
                assertTrue(cosmetics.single { it.id == "frame_lightning" }.equipped)
                assertTrue(cosmetics.single { it.id == "title_godspeed" }.unlocked)
                assertTrue(cosmetics.single { it.id == "title_godspeed" }.equipped)
                assertFalse(cosmetics.single { it.id == "frame_peerless" }.unlocked)
                assertFalse(cosmetics.single { it.id == "title_speed_king" }.unlocked)
            } finally {
                deleteUser(dataSource, userId)
            }
        }
    }
}

private fun testDataSource(url: String) = HikariDataSource(HikariConfig().apply {
    jdbcUrl = url
    username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
    password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
    maximumPoolSize = 2
})

private fun deleteUser(dataSource: HikariDataSource, userId: UUID) {
    dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
            statement.setObject(1, userId)
            statement.executeUpdate()
        }
    }
}
