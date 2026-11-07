package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.AuthResult
import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.PostgresAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoomOwnershipFixtureSeedTest {
    @Test
    fun `configuration accepts only the dedicated development database and distinct accounts`() {
        val valid = mapOf(
            "FASTTOWIN_ENV" to "dev",
            "DATABASE_URL" to "jdbc:postgresql://postgres:5432/fasttowin_room_pilot",
            "DATABASE_USER" to "fasttowin_room_pilot",
            "DATABASE_PASSWORD" to "test-database-password",
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to fixtureId.toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to accounts.joinToString(","),
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD" to password
        )
        assertEquals(accounts, RoomOwnershipFixtureSeedSettings.fromEnvironment(valid).accounts)
        listOf(
            "FASTTOWIN_ENV" to "prod",
            "DATABASE_URL" to "jdbc:postgresql://example.test:5432/fasttowin_room_pilot",
            "DATABASE_URL" to "jdbc:postgresql://postgres:5432/fasttowin_room_pilot?currentSchema=elsewhere",
            "DATABASE_USER" to "postgres",
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to "1-1-1-1-1",
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to "${accounts[0]},${accounts[0]}",
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to accounts[0].toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD" to "short"
        ).forEach { replacement ->
            assertFailsWith<IllegalArgumentException> { RoomOwnershipFixtureSeedSettings.fromEnvironment(valid + replacement) }
        }
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipFixtureSeedSettings.fromEnvironment(valid - "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD")
        }
    }

    @Test
    fun `fresh fixture seeds verified ordered logins and rejects changes to its identity`() = runBlocking {
        withSchema { source, schema ->
            seedRoomOwnershipFixture(source, fixtureId, accounts, password, schema)
            seedRoomOwnershipFixture(source, fixtureId, accounts, password, schema)
            val auth = AuthenticationService(PostgresAuthRepository(source))
            listOf("room-pilot-1@example.test", "room-pilot-2@example.test").forEachIndexed { index, email ->
                val session = assertIs<AuthResult.Success>(auth.login(email, password, "test")).session
                assertEquals(accounts[index].toString(), session.userId)
                assertEquals("Room Pilot ${index + 1}", session.displayName)
                assertTrue(session.emailVerified)
            }
            assertFailsWith<IllegalArgumentException> {
                seedRoomOwnershipFixture(source, UUID.randomUUID(), accounts, password, schema)
            }
            assertFailsWith<IllegalArgumentException> {
                seedRoomOwnershipFixture(source, fixtureId, accounts.reversed(), password, schema)
            }
            source.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO users (id, account_type, status, created_at, updated_at) VALUES (?, 'GUEST', 'ACTIVE', NOW(), NOW())"
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.executeUpdate()
                }
            }
            assertFailsWith<IllegalArgumentException> {
                seedRoomOwnershipFixture(source, fixtureId, accounts, password, schema)
            }
        }
    }

    @Test
    fun `unmarked database objects prevent migration`() = runBlocking {
        withSchema { source, schema ->
            source.connection.use { connection ->
                connection.createStatement().use { it.execute("CREATE TABLE unrelated (id INT)") }
            }
            assertFailsWith<IllegalArgumentException> {
                seedRoomOwnershipFixture(source, fixtureId, accounts, password, schema)
            }
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT to_regclass('flyway_schema_history') IS NULL").use { rows ->
                        assertTrue(rows.next() && rows.getBoolean(1))
                    }
                }
            }
        }
    }

    private suspend fun withSchema(block: suspend (DataSource, String) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL fixture required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        val schema = "owned_seed_test_" + UUID.randomUUID().toString().replace("-", "")
        val source = PGSimpleDataSource().apply {
            setUrl(url)
            user = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            currentSchema = schema
        }
        source.connection.use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            try {
                block(source, schema)
            } finally {
                connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    private val fixtureId = UUID.fromString("53b8b502-1ec7-4945-8935-f595127990f3")
    private val accounts = listOf(
        UUID.fromString("e27a2999-a61a-4684-baa0-d5050a8df419"),
        UUID.fromString("b11485c8-c965-4077-9f6e-6edc93e46fef")
    )
    private val password = "LocalFixtureOnly123!"
}
