package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.postgresql.ds.PGSimpleDataSource
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.system.exitProcess

internal const val STAGING_PROJECT = "b5c5db71-2df8-46da-84fb-d9b9df6d7951"
internal const val STAGING_ENVIRONMENT = "8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9"
private const val FIXTURE_DATABASE = "fasttowin_room_pilot"
private const val SMOKE_TIMEOUT_SECONDS = 120L

internal class RailwayRoomSmokeSettings private constructor(
    val runName: String,
    val fixtureId: UUID,
    val accountIds: List<UUID>,
    val baseline: Boolean,
    private val databasePassword: String,
    private val accountPassword: String,
    databaseHostOverride: String,
    private val backendHostsOverride: List<String>
) {
    val databaseHost = databaseHostOverride
    val backendHosts = backendHostsOverride

    fun verifyPrivateAddresses(resolve: (String) -> List<InetAddress> = ::resolve) {
        verifiedEndpointHosts(resolve)
    }

    private fun verifiedEndpointHosts(resolve: (String) -> List<InetAddress>): List<String> {
        val targets = (backendHosts.filter(String::isNotEmpty) + databaseHost).map { privateAddresses(it, resolve) }
        require(targets.flatten().toSet().size == targets.sumOf { it.size }) {
            "Staging fixture services must have distinct private addresses"
        }
        return targets.map { addresses -> addresses.sortedBy(InetAddress::getHostAddress).first().socketHost() }
    }

    fun seed() {
        val resolvedDatabaseHost = verifiedEndpointHosts(::resolve).last()
        val dataSource = PGSimpleDataSource().apply {
            setUrl("jdbc:postgresql://$resolvedDatabaseHost:5432/$FIXTURE_DATABASE")
            user = FIXTURE_DATABASE
            password = databasePassword
            connectTimeout = 4
            socketTimeout = 15
        }
        seedRoomOwnershipFixture(dataSource, fixtureId, accountIds, accountPassword)
    }

    fun acceptanceSettings(resolve: (String) -> List<InetAddress> = ::resolve): RoomAcceptanceSettings {
        // Resolve and validate the marked fixture before connecting, then keep
        // the private DNS names for the actual clients. Railway may rotate a
        // service address while the smoke run is starting; resolving once and
        // pinning that address can send a peer hop to a stale task.
        verifiedEndpointHosts(resolve)
        return RoomAcceptanceSettings(
            project = runName,
            composeFiles = null,
            receipts = Path.of("receipts"),
            urls = backendHosts.map { "http://$it:8080" },
            proxyUrls = emptyList(),
            postgresPort = 5432,
            suite = if (baseline) "Load" else "Smoke",
            backendCount = if (baseline) 1 else 2,
            durationSeconds = if (baseline) 600 else 0,
            roomCount = if (baseline) 20 else 1,
            seed = 73,
            fixtureId = fixtureId,
            accountIds = accountIds,
            databasePassword = databasePassword,
            accountPassword = accountPassword,
            databaseHost = databaseHost
        )
    }

    companion object {
        fun fromEnvironment(values: Map<String, String>, now: Instant = Instant.now(), requireBackendHosts: Boolean = true): RailwayRoomSmokeSettings {
            val suite = values["FTW_PILOT_SUITE"] ?: "smoke"
            require(suite in setOf("smoke", "baseline")) { "Unsupported staging suite" }
            val baseline = suite == "baseline"
            require(values.keys.none { it in setOf("RAILWAY_TOKEN", "RAILWAY_API_TOKEN") }) {
                "Runner must not receive Railway management credentials"
            }
            require(values["RAILWAY_PROJECT_ID"] == STAGING_PROJECT &&
                values["RAILWAY_ENVIRONMENT_ID"] == STAGING_ENVIRONMENT &&
                values["RAILWAY_ENVIRONMENT_NAME"] == "staging"
            ) { "Runner requires the FastToWin Staging environment" }
            require(values.keys.none { it.startsWith("DATABASE_") || it in setOf("DATABASE_URL", "JDBC_DATABASE_URL", "PGHOST") }) {
                "External database configuration is forbidden"
            }
            val fixtureId = requireNotNull(values["FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID"]?.let(::parsePilotUuid)) {
                "A valid fixture marker is required"
            }
            val runName = "room-pilot-" + fixtureId.toString().replace("-", "").take(12)
            require(values["RAILWAY_SERVICE_NAME"] == "$runName-runner") { "Unexpected staging runner service" }
            val deadline = runCatching { Instant.parse(values["FTW_PILOT_DEADLINE_UTC"]) }.getOrNull()
            require(deadline != null && !deadline.isBefore(now.plusSeconds(if (baseline) 900 else SMOKE_TIMEOUT_SECONDS)) &&
                !deadline.isAfter(now.plusSeconds(7_200))
            ) { "An unexpired staging window of at most two hours is required" }
            val accountIds = values["FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS"].orEmpty().split(',').map {
                requireNotNull(parsePilotUuid(it)) { "Invalid fixture account ID" }
            }
            val accountCount = if (baseline) 40 else 2
            require(accountIds.size == accountCount && accountIds.distinct().size == accountCount) {
                "Unexpected fixture account count or duplicate account"
            }
            val databasePassword = values["FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD"].orEmpty()
            val accountPassword = values["FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD"].orEmpty()
            val databaseHost = values["FTW_PILOT_DATABASE_HOST"]?.trim().orEmpty()
            val configuredHosts = listOf("FTW_PILOT_BACKEND_A_HOST", "FTW_PILOT_BACKEND_B_HOST").map { values[it]?.trim().orEmpty() }
            require(!baseline || configuredHosts[1].isEmpty()) { "Baseline requires only backend A" }
            val backendHosts = configuredHosts.filter(String::isNotEmpty)
            val expectedHosts = listOf(
                "$runName-a.railway.internal",
                "$runName-b.railway.internal"
            ).take(if (baseline) 1 else 2)
            require(databaseHost == "$runName-pg.railway.internal" &&
                if (requireBackendHosts) configuredHosts.take(expectedHosts.size) == expectedHosts else backendHosts.isEmpty()
            ) {
                "Runner endpoints must match its marked staging fixture services"
            }
            require(databasePassword.matches(Regex("[a-f0-9]{64}")) &&
                accountPassword.matches(Regex("Pilot1![a-f0-9]{64}"))
            ) { "Expected generated fixture passwords" }
            return RailwayRoomSmokeSettings(runName, fixtureId, accountIds, baseline, databasePassword, accountPassword, databaseHost, backendHosts)
        }
    }
}

private fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()

internal fun privateAddresses(host: String, resolve: (String) -> List<InetAddress>): Set<InetAddress> {
    val addresses = resolve(host).toSet()
    require(addresses.isNotEmpty() && addresses.all {
        when (it) {
            is Inet4Address -> it.isSiteLocalAddress
            is Inet6Address -> it.address[0].toInt() and 0xfe == 0xfc
            else -> false
        }
    }) { "Fixture hostname must resolve exclusively to private addresses" }
    return addresses
}

internal fun InetAddress.socketHost(): String = when (this) {
    is Inet6Address -> "[" + hostAddress.substringBefore('%') + "]"
    else -> hostAddress
}

fun main(args: Array<String>) {
    var phase = "configuration"
    try {
        require(args.isEmpty() || args.toList() == listOf("--seed"))
        val settings = RailwayRoomSmokeSettings.fromEnvironment(System.getenv(), requireBackendHosts = args.isEmpty())
        if (args.isNotEmpty()) {
            phase = "seed"
            settings.seed()
            println("""{"fixtureSeeded":true}""")
            return
        }
        if (settings.baseline) {
            phase = "baseline"
            runRailwayRoomBaseline(settings)
            return
        }
        phase = "private-dns"
        runBlocking {
            withTimeout(SMOKE_TIMEOUT_SECONDS * 1_000) {
                phase = "fixture-marker-and-readiness"
                RoomAcceptanceFixture(settings.acceptanceSettings()).use { fixture ->
                    fixture.verifyFixture()
                    fixture.requireBothReady()
                    phase = "cross-backend-presence"
                    fixture.verifyCrossBackendPresence()
                    phase = "shared-login-quota"
                    fixture.verifySharedLoginQuota()
                    phase = "cross-backend-match"
                    val match = fixture.createMatch(0)
                    try {
                        check(match.socket(0).backend == 0 && match.socket(1).backend == 1)
                        val number = match.nextNumber
                        val selected = match.select(number, "staging-smoke-selection")
                        check(number in selected.snapshot.selectedNumbers && selected.snapshot.matchId == match.matchId)
                        match.verifyEconomy()
                    } finally {
                        withContext(NonCancellable) { match.close() }
                    }
                }
            }
        }
        println("""{"readyBackends":2,"onlineSeenFromBothBackends":true,"sharedLoginQuota":true,"sameCommittedSelection":true,"finalizedQuotaReceipts":2}""")
    } catch (failure: Exception) {
        System.err.println("Staging room smoke failed during $phase: ${failure.message ?: failure::class.simpleName}")
        exitProcess(1)
    }
}

private suspend fun RoomAcceptanceFixture.verifyCrossBackendPresence() {
    withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.prepareStatement("""
                INSERT INTO friendships(id, requester_id, addressee_id, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACCEPTED', clock_timestamp(), clock_timestamp())
                ON CONFLICT DO NOTHING
            """.trimIndent()).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, settings.accountIds[0])
                statement.setObject(3, settings.accountIds[1])
                statement.executeUpdate()
            }
        }
    }
    val users = accounts()
    val sockets = connectPair(users[0], 0, users[1], 1)
    try {
        sockets.forEachIndexed { index, socket ->
            check(socket.backend == index && socket.ready.presenceRefreshSupported)
            val requestId = "staging-presence-$index"
            socket.send(ClientMessage.GetPresence(requestId))
            val observed = socket.await(expected = "presence response") {
                it is ServerMessage.PresenceData && it.requestId == requestId
            } as ServerMessage.PresenceData
            check(observed.states[users[1 - index].userId] == FriendPresence.ONLINE)
        }
    } finally {
        withContext(NonCancellable) { sockets.forEach { it.closeGracefully() } }
    }
}

private suspend fun RoomAcceptanceFixture.verifySharedLoginQuota() {
    // A fresh, nonexistent fixture email avoids using the two actual account buckets.
    val email = "room-pilot-limit-${UUID.randomUUID()}@example.test"
    for (attempt in 0..8) {
        val result = client.post(settings.urls[attempt % 2] + "/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ProtocolJson.encodeToString(LoginRequest(email, "UnusedFixturePassword1!", "android")))
        }
        val expected = if (attempt < 8) HttpStatusCode.Unauthorized else HttpStatusCode.TooManyRequests
        check(result.status == expected)
    }
}
