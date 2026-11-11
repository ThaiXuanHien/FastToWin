package com.hienthai.fastowin.server.ownership

import org.postgresql.ds.PGSimpleDataSource
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class RoomOwnershipProductionSeedIntegrationTest {
    @Test
    fun `production seeder creates accounts without fixture marker`() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runBlocking
        val password = System.getenv("TEST_DATABASE_PASSWORD") ?: return@runBlocking
        val dataSource = PGSimpleDataSource().apply {
            setUrl(url)
            user = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            this.password = password
        }
        val accounts = List(2) { UUID.randomUUID() }
        try {
            seedRoomOwnershipProductionAccounts(dataSource, accounts, "ProductionSeed123!")
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT count(*) FROM users WHERE id = ANY (?)"
                ).use { statement ->
                    statement.setArray(1, connection.createArrayOf("uuid", accounts.toTypedArray()))
                    statement.executeQuery().use { rows ->
                        rows.next()
                        assertEquals(2, rows.getInt(1))
                    }
                }
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT count(*) FROM room_ownership_pilot_fixture"
                    ).use { rows ->
                        rows.next()
                        assertEquals(0, rows.getInt(1))
                    }
                }
                val repository = PostgresRoomOwnershipRepository(dataSource)
                assertFalse(repository.fixtureReady(UUID.randomUUID()))
            }
        } finally {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DELETE FROM users")
                }
            }

        }
    }
}

