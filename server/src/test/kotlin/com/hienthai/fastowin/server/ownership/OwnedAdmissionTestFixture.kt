package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.PostgresMatchResultRepository
import com.hienthai.fastowin.server.PostgresPlayQuotaRepository
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.StoredActiveRoom
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.sql.Connection
import java.util.Base64
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertNotNull

internal val ADMISSION_NODE_A = UUID.fromString("00000000-0000-4000-8000-000000000001")
internal val ADMISSION_NODE_B = UUID.fromString("00000000-0000-4000-8000-000000000002")
internal val ADMISSION_HOST = UUID.fromString("10000000-0000-4000-8000-000000000001")
internal val ADMISSION_GUEST = UUID.fromString("10000000-0000-4000-8000-000000000002")

internal fun admissionProductionValues(
    role: RoomOwnershipRuntimeRole = RoomOwnershipRuntimeRole.INGRESS,
    accounts: List<UUID> = listOf(ADMISSION_HOST, ADMISSION_GUEST),
    nodeA: UUID = ADMISSION_NODE_A,
    nodeB: UUID = ADMISSION_NODE_B
): Map<String, String> {
    val ingress = role == RoomOwnershipRuntimeRole.INGRESS
    return mapOf(
        "FASTTOWIN_ENV" to "prod",
        "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
        "FASTTOWIN_ROOM_OWNERSHIP_ROLE" to if (ingress) "ingress" else "owned-worker",
        "DATABASE_URL" to "jdbc:postgresql://database.railway.internal:5432/isolated",
        "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to (if (ingress) nodeA else nodeB).toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to (if (ingress) nodeB else nodeA).toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://${if (ingress) "owned-worker" else "ingress"}.railway.internal:8080",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to Base64.getEncoder().encodeToString(ByteArray(32) { 19 }),
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to accounts.joinToString(",")
    )
}

internal fun admissionProductionConfig(
    role: RoomOwnershipRuntimeRole = RoomOwnershipRuntimeRole.INGRESS,
    accounts: List<UUID> = listOf(ADMISSION_HOST, ADMISSION_GUEST),
    nodeA: UUID = ADMISSION_NODE_A,
    nodeB: UUID = ADMISSION_NODE_B
): RoomOwnershipRuntimeConfig {
    val validated = requireNotNull(RoomOwnershipProductionPolicy.fromEnvironment(
        admissionProductionValues(role, accounts, nodeA, nodeB)
    ))
    // Only transport is redirected to the isolated fixture. The role/IDs/account policy is parser-validated.
    return object : RoomOwnershipRuntimeConfig by validated {
        override val peerUri = URI("http://127.0.0.1:65534")
    }
}

internal suspend fun withAdmissionFixture(
    migrationTarget: String? = null,
    block: suspend (OwnedAdmissionTestFixture) -> Unit
) {
    val url = System.getenv("TEST_DATABASE_URL")
    if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
    assumeTrue(url != null, "Isolated PostgreSQL required")
    require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
    val schema = "owned_admission_" + UUID.randomUUID().toString().replace("-", "")
    fun pool(selected: String?) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        maximumPoolSize = 8
        if (selected != null) this.schema = selected
    })
    pool(null).use { control ->
        control.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
        try {
            val migrations = Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema)
            if (migrationTarget != null) migrations.target(migrationTarget)
            migrations.load().migrate()
            pool(schema).use { source ->
                source.connection.use { connection ->
                    connection.prepareStatement("""
                        INSERT INTO users(id, account_type, status, email_normalized, password_hash,
                            email_verified_at, created_at, updated_at)
                        VALUES (?, 'REGISTERED', 'ACTIVE', ?, 'isolated-test-only', clock_timestamp(),
                            clock_timestamp(), clock_timestamp())
                    """.trimIndent()).use { statement ->
                        listOf(ADMISSION_HOST, ADMISSION_GUEST).forEach { id ->
                            statement.setObject(1, id)
                            statement.setString(2, "$id@example.test")
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
                block(OwnedAdmissionTestFixture(source, schema))
            }
        } finally {
            control.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}

internal class OwnedAdmissionTestFixture(val source: HikariDataSource, private val schema: String) {
    val config = admissionProductionConfig()
    val binding = OwnedAdmissionBinding.from(config)
    val authority = PostgresOwnedAdmissionRepository(source)
    val rooms = PostgresRoomOwnershipRepository(source, admission = binding)
    val unguardedFixtureRooms = PostgresRoomOwnershipRepository(source)
    val instance = UUID.randomUUID()
    val economy = OwnedMatchEconomy(rooms, PostgresPlayQuotaRepository(source), PostgresMatchResultRepository(source))
    val players = listOf(ADMISSION_HOST, ADMISSION_GUEST).associateWith {
        StoredActivePlayer(it.toString(), if (it == ADMISSION_HOST) "Host" else "Guest", isAccount = true)
    }

    fun migrateLatest() = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate()

    suspend fun activate(): OwnedAdmissionPolicy = authority.bootstrap(assertNotNull(authority.observe()).generation, binding)

    suspend fun pilot(selectedConfig: RoomOwnershipRuntimeConfig = config) = RoomOwnershipPilot.open(
        selectedConfig, source, { players[it] }, wakeups = emptyFlow(), candidateIdentity = { OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64)) }
    )

    fun snapshot() = StoredActiveRoom(
        UUID.randomUUID().toString(), roomName = "Admission fixture", host = players.getValue(ADMISSION_HOST),
        passwordSalt = byteArrayOf(1), passwordHash = byteArrayOf(2), gameMode = ProtocolGameMode.ORDER,
        phase = RoomPhase.WAITING
    )

    suspend fun create(policy: OwnedAdmissionPolicy? = null): OwnedRoomState {
        val socket = rooms.bindSocket(ADMISSION_HOST)
        val initial = snapshot().copy(pilotConnectionEpoch = 1,
            pilotSocketGenerations = mapOf(ADMISSION_HOST.toString() to socket.generation))
        return assertIs<FencedResult.Committed>(rooms.createRoom(instance, socket, initial,
            listOf(RoomOwnershipEvent(setOf(ADMISSION_HOST), ServerMessage.RoomCreated(initial.classicSnapshot()))),
            admissionPolicy = policy)).state
    }

    suspend fun waitingPair(policy: OwnedAdmissionPolicy): Pair<OwnedRoomState, SocketFence> {
        val state = create(policy)
        val socket = rooms.bindSocket(ADMISSION_GUEST)
        val joined = assertIs<FencedResult.Committed>(rooms.withFencedRoom(
            state.fence, state.revision, socket, admissionPolicy = policy
        ) { _, room -> RoomMutation.Change(room.snapshot.copy(
            guest = players.getValue(ADMISSION_GUEST), readyPlayerIds = setOf(ADMISSION_HOST.toString()),
            pilotSocketGenerations = room.snapshot.pilotSocketGenerations + (ADMISSION_GUEST.toString() to socket.generation)
        )) }).state
        return joined to socket
    }

    fun playing(state: OwnedRoomState) = state.snapshot.copy(phase = RoomPhase.PLAYING,
        startedAtEpochMillis = 1_900_000_000_000L, numbers = (1..50).toList(), readyPlayerIds = emptySet())

    fun execute(sql: String) = source.connection.use { c -> c.createStatement().use { it.execute(sql) } }
    fun count(table: String): Int = source.connection.use { c -> c.createStatement().use { statement ->
        statement.executeQuery("SELECT count(*) FROM $table").use { rows -> check(rows.next()); rows.getInt(1) }
    } }

    fun pid(connection: Connection): Int = connection.createStatement().use { s ->
        s.executeQuery("SELECT pg_backend_pid()").use { rows -> check(rows.next()); rows.getInt(1) }
    }

    suspend fun awaitBlockedBy(pid: Int, count: Int = 1) = withTimeout(800) {
        while (source.connection.use { c -> c.prepareStatement("""
            SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
        """.trimIndent()).use { s -> s.setInt(1, pid); s.executeQuery().use { r -> r.next(); r.getInt(1) } } } < count) delay(5)
    }
}
