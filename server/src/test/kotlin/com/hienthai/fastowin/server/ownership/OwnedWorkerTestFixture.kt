package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.flow.emptyFlow
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.util.UUID
import kotlin.test.assertIs

internal suspend fun withOwnedWorkerFixture(block: suspend (OwnedWorkerTestFixture) -> Unit) {
    val url = System.getenv("TEST_DATABASE_URL")
    if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
    assumeTrue(url != null, "Isolated PostgreSQL required")
    require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
    val schema = "owned_worker_" + UUID.randomUUID().toString().replace("-", "")
    fun pool(selectedSchema: String?) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        maximumPoolSize = 5
        if (selectedSchema != null) this.schema = selectedSchema
    })
    pool(null).use { control ->
        control.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
        try {
            Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema).load().migrate()
            pool(schema).use { source ->
                val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1000))
                val players = listOf("Host", "Guest").associate { name ->
                    val account = assertIs<AuthResult.Success>(auth.register(
                        "${name.lowercase()}@example.test", "WorkerPassword123!", name, "test"
                    )).session
                    UUID.fromString(account.userId) to StoredActivePlayer(account.userId, name, true)
                }
                source.connection.use { it.createStatement().use { s ->
                    s.executeUpdate("UPDATE users SET email_verified_at = clock_timestamp()")
                } }
                block(OwnedWorkerTestFixture(source, players))
            }
        } finally {
            control.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}

internal class OwnedWorkerTestFixture(val source: HikariDataSource, val players: Map<UUID, StoredActivePlayer>) {
    val databaseSettings get() = DatabaseSettings(
        "${source.jdbcUrl}?currentSchema=${source.schema}", source.username, source.password, 5
    )
    val config = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER,
        players.keys.toList(), UUID.randomUUID(), UUID.randomUUID())
    val rooms = PostgresRoomOwnershipRepository(source)
    suspend fun activate(): OwnedAdmissionPolicy {
        val authority = PostgresOwnedAdmissionRepository(source)
        return authority.bootstrap(requireNotNull(authority.observe()).generation, OwnedAdmissionBinding.from(config))
    }
    suspend fun stopNew() {
        val authority = PostgresOwnedAdmissionRepository(source)
        authority.stopNew(requireNotNull(authority.observe()).generation)
    }
    suspend fun pilot(newIntentAllowed: () -> Boolean = { true }) = RoomOwnershipPilot.open(
        config, source, { players[it] }, wakeups = emptyFlow(), newIntentAllowed = newIntentAllowed
    )
}
