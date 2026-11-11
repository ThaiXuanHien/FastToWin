package com.hienthai.fastowin.server.ownership

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomOwnershipProductionSeedIntegrationTest {
    @Test
    fun seedsOnlyItsDedicatedDatabaseAndRefusesReplayWithoutChangingAccounts() = database { source, settings ->
        val activated = seedRoomOwnershipProductionAccounts(source, settings)
        assertTrue(activated.generation > 0)
        assertTrue(activated.admits(settings.admissionBinding))
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM users").use { rows ->
                    check(rows.next())
                    assertEquals(settings.accounts.size, rows.getInt(1))
                }
                statement.executeQuery("SELECT count(*) FROM room_ownership_pilot_fixture").use { rows ->
                    check(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
                statement.executeQuery("SELECT generation, state, topology_sha256, policy_sha256 FROM owned_admission_policy").use { rows ->
                    check(rows.next())
                    assertEquals(activated, rows.ownedAdmissionPolicy())
                    assertFalse(rows.next())
                }
            }
        }
        assertFailsWith<IllegalArgumentException> { seedRoomOwnershipProductionAccounts(source, settings) }
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM users").use { rows ->
                    val actual = buildSet { while (rows.next()) add(rows.getObject(1, UUID::class.java)) }
                    assertEquals(settings.accounts.toSet(), actual)
                }
            }
        }
    }

    @Test
    fun rejectsAnOccupiedDatabaseBeforeRunningAnyMigrations() = database { source, settings ->
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE sentinel(value integer)")
                statement.execute("INSERT INTO sentinel VALUES (42)")
            }
        }
        assertFailsWith<IllegalArgumentException> { seedRoomOwnershipProductionAccounts(source, settings) }
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT value FROM sentinel").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(42, rows.getInt(1))
                    assertFalse(rows.next())
                }
                statement.executeQuery("SELECT to_regclass('flyway_schema_history') IS NULL").use { rows ->
                    check(rows.next())
                    assertTrue(rows.getBoolean(1))
                }
            }
        }
    }

    private fun database(block: (PGSimpleDataSource, RoomOwnershipProductionSeedSettings) -> Unit) {
        val rawUrl = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!rawUrl.isNullOrBlank(), "Isolated PostgreSQL is required")
        val uri = URI(requireNotNull(rawUrl).removePrefix("jdbc:"))
        require(uri.host in setOf("localhost", "127.0.0.1") && uri.port in 1..65535)
        val settings = RoomOwnershipProductionSeedSettings.fromEnvironment(
            RoomOwnershipProductionSeedTest.values(UUID.randomUUID())
        )
        val name = settings.databaseName
        require(name.matches(Regex("room_rehearsal_[a-f0-9]{12}")))
        val control = PGSimpleDataSource().apply {
            setUrl(rawUrl)
            user = System.getenv("TEST_DATABASE_USER")
            password = System.getenv("TEST_DATABASE_PASSWORD")
        }
        val source = PGSimpleDataSource().apply {
            setUrl("jdbc:postgresql://" + uri.host + ":" + uri.port + "/" + name)
            user = name
            password = settings.database.password
        }
        var roleCreated = false
        var databaseCreated = false
        try {
            control.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE ROLE " + name + " LOGIN PASSWORD '" + settings.database.password + "'")
                    roleCreated = true
                    statement.execute("CREATE DATABASE " + name + " OWNER " + name)
                    databaseCreated = true
                }
            }
            block(source, settings)
        } finally {
            control.connection.use { connection ->
                connection.createStatement().use { statement ->
                    if (databaseCreated) statement.execute("DROP DATABASE " + name + " WITH (FORCE)")
                    if (roleCreated) statement.execute("DROP ROLE " + name)
                }
            }
        }
    }
}

