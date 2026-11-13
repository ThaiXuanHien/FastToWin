package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.io.EOFException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal data class RoomAcceptanceSettings(
    val project: String, val composeFiles: Pair<Path, Path>?, val receipts: Path,
    val urls: List<String>, val proxyUrls: List<String>, val postgresPort: Int,
    val suite: String, val backendCount: Int, val durationSeconds: Int, val roomCount: Int, val seed: Int,
    val fixtureId: UUID, val accountIds: List<UUID>, val databasePassword: String, val accountPassword: String,
    val databaseHost: String = "127.0.0.1",
    val productionLike: Boolean = false,
    val soleIngress: Boolean = false,
    val topologyStatus: RehearsalStatusConfig? = null
) {
    private val runSuffix get() = fixtureId.toString().replace("-", "").take(12)
    val databaseName get() = if (productionLike) "room_rehearsal_$runSuffix" else "fasttowin_room_pilot"
    val databasePoolName get() = if (productionLike) "room-acceptance-production-$runSuffix" else "room-acceptance-controller"
    val emailPrefix get() = if (productionLike) "room-production" else "room-pilot"

    fun publicIngressNodes(preferred: Int): List<Int> =
        if (soleIngress) listOf(0) else (listOf(preferred) + (0 until backendCount)).distinct()

    /** Benchmark-only source identities, used solely on the isolated trusted-proxy rehearsal network. */
    fun syntheticSourceAddress(accountId: String): String? {
        if (!productionLike) return null
        val index = accountIds.indexOf(UUID.fromString(accountId))
        require(index >= 0)
        return "198.18.${index / 254}.${index % 254 + 1}"
    }

    fun matchAccountIndices(slot: Int, creationOrdinal: Int): List<Int> {
        require(slot >= 0 && slot * 2 + 1 < accountIds.size && creationOrdinal >= 0)
        val first = slot * 2
        return if (productionLike && creationOrdinal % 2 == 1) listOf(first + 1, first) else listOf(first, first + 1)
    }

    companion object {
        fun fromEnvironment(values: Map<String, String>): RoomAcceptanceSettings {
            fun required(key: String) = requireNotNull(values[key]) { "Missing fixture setting $key" }
            val root = Path.of("").toAbsolutePath().normalize()
            val base = Path.of(required("FTW_ACCEPTANCE_COMPOSE_BASE")).toAbsolutePath().normalize()
            val override = Path.of(required("FTW_ACCEPTANCE_COMPOSE_OVERRIDE")).toAbsolutePath().normalize()
            require(base == root.resolve("deploy/room-ownership/compose.local.yaml"))
            require(override == root.resolve("deploy/room-ownership/compose.acceptance.yaml"))
            val receipts = Path.of(required("FTW_ACCEPTANCE_RECEIPTS")).toAbsolutePath().normalize()
            require(receipts.startsWith(root.resolve(".artifacts/room-ownership-local")) && receipts != root)
            val project = required("FTW_ACCEPTANCE_PROJECT")
            require(project.matches(Regex("room-pilot-[0-9a-f]{12}")))
            fun localUrl(key: String): String {
                val uri = URI(required(key))
                require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                    uri.rawUserInfo == null && uri.rawPath in setOf("", "/") && uri.rawQuery == null && uri.rawFragment == null)
                return uri.toString().trimEnd('/')
            }
            val count = (values["FTW_ACCEPTANCE_BACKENDS"] ?: "2").toInt()
            val duration = (values["FTW_ACCEPTANCE_DURATION_SECONDS"] ?: "600").toInt()
            val rooms = (values["FTW_ACCEPTANCE_ROOMS"] ?: "20").toInt()
            val port = required("FTW_ACCEPTANCE_POSTGRES_PORT").toInt()
            val suite = required("FTW_ACCEPTANCE_SUITE")
            require(count in 1..2 && duration in 1..3600 && rooms in 1..100 && port in 1..65535 && suite in setOf("Fault", "Load"))
            val ids = required("FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS").split(',').map { UUID.fromString(it) }
            require(ids.size in 2..256 && ids.distinct().size == ids.size)
            return RoomAcceptanceSettings(project, base to override, receipts,
                listOf(localUrl("FTW_ACCEPTANCE_A"), localUrl("FTW_ACCEPTANCE_B")),
                listOf(localUrl("FTW_ACCEPTANCE_PROXY_A"), localUrl("FTW_ACCEPTANCE_PROXY_B")), port,
                suite, count, duration, rooms, (values["FTW_ACCEPTANCE_SEED"] ?: "73").toInt(),
                UUID.fromString(required("FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID")), ids,
                required("FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD"), required("FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD"))
        }
    }
}

fun main() = runBlocking {
    val settings = RoomAcceptanceSettings.fromEnvironment(System.getenv())
    Files.createDirectories(settings.receipts)
    RoomAcceptanceFixture(settings).use { fixture ->
        fixture.verifyFixture()
        fixture.fundQuota()
        try { when (settings.suite) {
            "Fault" -> {
                val receipt = runRoomOwnershipFaults(fixture)
                Files.writeString(settings.receipts.resolve("faults.json"), ProtocolJson.encodeToString(receipt))
            }
            "Load" -> {
                val receipt = runRoomOwnershipLoad(fixture)
                Files.writeString(settings.receipts.resolve("load.json"), ProtocolJson.encodeToString(receipt))
            }
        } } catch (failure: Throwable) {
            fixture.captureDiagnostics(failure)
            throw failure
        }
        println("Room ownership ${settings.suite} accepted")
    }
}

internal data class AcceptanceAccount(val userId: String, val accessToken: String)
internal data class SelectionReceipt(val durationMillis: Double, val snapshot: GameSnapshot, val attempts: Int = 1)
internal class AcceptanceRejected(val code: String) : IllegalStateException("Fixture command rejected with $code")

internal fun verifyRoomAcceptanceDatabase(connection: Connection, settings: RoomAcceptanceSettings) {
    if (settings.productionLike) {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT current_database(), current_user").use { rows ->
                check(rows.next() && rows.getString(1) == settings.databaseName && rows.getString(2) == settings.databaseName) {
                    "Unexpected production rehearsal database identity"
                }
            }
            statement.executeQuery("SELECT count(*) FROM room_ownership_pilot_fixture").use { rows ->
                check(rows.next() && rows.getLong(1) == 0L) { "Production rehearsal cannot use a fixture marker" }
            }
        }
    } else {
        connection.prepareStatement("SELECT fixture_id FROM room_ownership_pilot_fixture WHERE singleton").use {
            it.executeQuery().use { rows -> check(rows.next() && rows.getObject(1, UUID::class.java) == settings.fixtureId) }
        }
    }
    connection.createStatement().use { statement ->
        statement.executeQuery("SELECT id FROM users ORDER BY id").use { rows ->
            val ids = buildSet { while (rows.next()) add(rows.getObject(1, UUID::class.java)) }
            check(ids == settings.accountIds.toSet()) { "Fixture contains unexpected accounts" }
        }
    }
}

internal class RoomAcceptanceFixture(val settings: RoomAcceptanceSettings, control: RoomFaultControl? = null) : AutoCloseable {
    val faults: RoomFaultControl = control ?: DockerRoomFaultControl(
        { operation, target -> docker(operation, target) }, ::toggleLocalProxy)
    val supervised get() = faults.processPauseStores
    @Volatile var creationObserver: ((String, Double, Boolean) -> Unit)? = null

    private suspend fun <T> creationStep(name: String, operation: suspend () -> T): T {
        val observer = creationObserver ?: return operation()
        val started = System.nanoTime()
        var failed = false
        try { return operation() }
        catch (failure: Throwable) { failed = true; throw failure }
        finally { observer(name, (System.nanoTime() - started) / 1_000_000.0, failed) }
    }
    private var databaseTimings = RoomOwnershipDatabaseTimings()
    val backendCount get() = settings.backendCount
    val durationSeconds get() = settings.durationSeconds
    val roomCount get() = settings.roomCount
    val seed get() = settings.seed
    val loadRateLimitsDescription = if (settings.productionLike)
        "Unchanged production policies; synthetic per-account 198.18.0.0/15 forwarded source identities on the isolated trusted-proxy network; " +
            "alternating creators within each pair; createPlayer5/min; selectPlayer20/s; quotaBonus1000."
        else "Synthetic pilot acceptance caps: loginIP512/min; wsConnectIP5000/min; wsIP5000/s; " +
            "createPlayer1000/min/createIP5000/min; joinPlayer1000/min/joinIP5000/min; selectIP5000/s; selectPlayer20/s; quotaBonus1000."
    val source = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${settings.databaseHost}:${settings.postgresPort}/${settings.databaseName}"
        username = settings.databaseName
        password = settings.databasePassword
        maximumPoolSize = 4
        connectionTimeout = 3_000
        addDataSourceProperty("socketTimeout", "4")
        poolName = settings.databasePoolName
    })
    val client = HttpClient(OkHttp) {
        followRedirects = false
        install(WebSockets)
        install(HttpTimeout) { requestTimeoutMillis = 5_000; connectTimeoutMillis = 2_000 }
    }
    private var cachedAccounts: List<AcceptanceAccount>? = null
    private val ownerNodes = ConcurrentHashMap<UUID, Int>()
    private val publicSockets = List(2) { AtomicInteger() }
    fun publicSocketCounts(): List<Int> = publicSockets.map { it.get() }
    private val creationOrdinals = ConcurrentHashMap<Int, AtomicInteger>()
    @Volatile var intentionallyUnavailableBackend: Int? = null

    suspend fun verifyFixture() = withContext(Dispatchers.IO) {
        withFixtureDatabase { connection ->
            verifyRoomAcceptanceDatabase(connection, settings)
        }
        if (settings.soleIngress) refreshOwnerProcesses()
    }

    suspend fun refreshOwnerProcesses(nodes: List<Int> = listOf(0, 1)) {
        val status = checkNotNull(settings.topologyStatus)
        for (node in nodes) {
            val process = status.process(node)
            ownerNodes.entries.removeIf { it.value == node && it.key != process }
            ownerNodes[process] = node
        }
    }

    fun resetDatabaseTimings() { databaseTimings = RoomOwnershipDatabaseTimings() }
    fun databaseTimingMetrics(): Map<String, Double> = baselineSafeMetrics(databaseTimings.render()).filterKeys {
        it.startsWith("fasttowin_room_ownership_db_pool_acquire_") || it.startsWith("fasttowin_room_ownership_db_transaction_body_")
    }

    fun <T> withFixtureDatabase(operation: (Connection) -> T): T =
        databaseTimings.measure(OwnershipDatabaseStage.POOL_ACQUIRE) { source.connection }.use { connection ->
            databaseTimings.measure(OwnershipDatabaseStage.BODY) { operation(connection) }
        }

    suspend fun fundQuota() {
        verifyFixture()
        sql("""
            INSERT INTO daily_play_quotas(user_id, quota_date, bonus_matches_granted)
            SELECT id, (clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 1000 FROM users
            ON CONFLICT(user_id, quota_date) DO UPDATE SET bonus_matches_granted=1000
        """.trimIndent())
    }

    suspend fun accounts(): List<AcceptanceAccount> {
        cachedAccounts?.let { return it }
        // Called once before the concurrent room producers start.
        return settings.accountIds.mapIndexed { index, id ->
            val response = client.post(settings.urls[0] + "/auth/login") {
                settings.syntheticSourceAddress(id.toString())?.let { header("X-Forwarded-For", it) }
                contentType(ContentType.Application.Json)
                setBody(ProtocolJson.encodeToString(LoginRequest("${settings.emailPrefix}-${index + 1}@example.test", settings.accountPassword, "android")))
            }
            check(response.status == HttpStatusCode.OK) { "Fixture login failed with ${response.status.value}" }
            val session = ProtocolJson.decodeFromString<AuthSessionResponse>(response.bodyAsText())
            check(session.userId == id.toString())
            AcceptanceAccount(session.userId, session.accessToken)
        }.also { cachedAccounts = it }
    }

    suspend fun resetRooms() {
        verifyFixture()
        sql("DELETE FROM room_ownership_pilot_rooms")
    }

    suspend fun createWaitingMatch(index: Int, targetOwner: Int? = null) = createMatch(index, start = false, targetOwner = targetOwner)

    suspend fun createMatchWithRetry(index: Int): AcceptanceMatch {
        // The producer holds its slot mutex and owns a distinct account pair.
        // Other slots must keep recycling while this pair waits for recovery.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        var last: Throwable? = null
        while (System.nanoTime() < deadline) {
            try { return createMatch(index) }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                last = failure
                delay(100)
            }
        }
        throw IllegalStateException("Fixture room creation did not recover in 25 seconds", last)
    }

    suspend fun createMatch(index: Int, start: Boolean = true, targetOwner: Int? = null): AcceptanceMatch {
        val users = accounts()
        require(index * 2 + 1 < users.size)
        val backend = index % backendCount
        val ordinal = creationOrdinals.computeIfAbsent(index) { AtomicInteger() }
        val pairIndices = if (settings.soleIngress && targetOwner != null)
            checkNotNull(settings.topologyStatus).targetPair(settings.accountIds, index, targetOwner)
        else settings.matchAccountIndices(index, ordinal.get())
        val pair = pairIndices.map(users::get)
        val (host, guest) = creationStep("connect_pair") { connectPair(pair[0], backend, pair[1], (backend + 1) % backendCount) }
        // SessionReady carries an existing room when a previous recycle attempt
        // failed after admission. Keep that id so the cleanup path can release
        // the pair before the next retry; otherwise CreateRoom is rejected while
        // the stale membership remains in the fixture.
        var createdRoomId: String? = host.ready.currentGame?.roomId ?: guest.ready.currentGame?.roomId
        try {
            val created = creationStep("create_room") {
                host.send(ClientMessage.CreateRoom("Acceptance $index", "PrivateAcceptance123!", ProtocolGameMode.ORDER))
                host.await(25_000, "host RoomCreated") { it is ServerMessage.RoomCreated } as ServerMessage.RoomCreated
            }
            val id = created.game.roomId
            createdRoomId = id
            ordinal.incrementAndGet()
            creationStep("join_room") {
                guest.send(ClientMessage.JoinRoom(id, "PrivateAcceptance123!"))
                host.await(25_000, "host join update") { it is ServerMessage.RoomUpdated && it.game.players.size == 2 }
                guest.await(25_000, "guest join update") { it is ServerMessage.RoomUpdated && it.game.players.size == 2 }
            }
            val waiting = creationStep("ready_host") {
                host.send(ClientMessage.SetReady(id, true))
                val reply = host.await(25_000, "host ready update") { it is ServerMessage.RoomUpdated } as ServerMessage.RoomUpdated
                guest.await(25_000, "guest ready update") { it is ServerMessage.RoomUpdated }
                reply
            }
            val owner = if (settings.soleIngress) ownerFor(snapshot(id)) else host.backend
            if (!settings.soleIngress) ownerNodes[snapshot(id).instanceId] = owner
            if (settings.soleIngress && targetOwner != null) check(owner == targetOwner) { "Created room has unexpected verified owner" }
            if (!start) return AcceptanceMatch(this, pair, mutableListOf(host, guest), waiting.game, owner)
            val (started, other) = creationStep("start_match") {
                guest.send(ClientMessage.SetReady(id, true))
                val first = host.await(25_000, "host GameStarted") { it is ServerMessage.GameStarted } as ServerMessage.GameStarted
                val second = guest.await(25_000, "guest GameStarted") { it is ServerMessage.GameStarted } as ServerMessage.GameStarted
                first to second
            }
            check(started.game == other.game)
            return AcceptanceMatch(this, pair, mutableListOf(host, guest), started.game, owner)
        } catch (failure: Throwable) {
            createdRoomId?.let { roomId ->
                runCatching { host.send(ClientMessage.LeaveRoom(roomId)) }
                runCatching { guest.send(ClientMessage.LeaveRoom(roomId)) }
            }
            // LeaveRoom is persisted by the owner asynchronously. Do not let
            // the next retry race the membership row that is still being
            // removed, especially after a paused owner has just recovered.
            runCatching {
                awaitMembershipsCleared(listOf(users[index * 2].userId, users[index * 2 + 1].userId))
            }
            host.closeGracefully(); guest.closeGracefully()
            throw failure
        }
    }

    private suspend fun awaitMembershipsCleared(playerIds: List<String>) = withTimeoutOrNull(20_000) {
        while (true) {
            val remaining = withContext(Dispatchers.IO) {
                withFixtureDatabase { connection ->
                    connection.prepareStatement(
                        "SELECT count(*) FROM room_ownership_pilot_memberships WHERE player_id IN (?, ?)"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(playerIds[0]))
                        statement.setObject(2, UUID.fromString(playerIds[1]))
                        statement.executeQuery().use { rows -> check(rows.next()); rows.getLong(1) }
                    }
                }
            }
            if (remaining == 0L) return@withTimeoutOrNull
            delay(50)
        }
    }

    suspend fun connectPair(host: AcceptanceAccount, hostBackend: Int, guest: AcceptanceAccount, guestBackend: Int): List<AcceptanceSocket> {
        val first = connect(host, hostBackend)
        try {
            return listOf(first, connect(guest, guestBackend))
        } catch (failure: Throwable) {
            first.close()
            throw failure
        }
    }

    suspend fun connect(account: AcceptanceAccount, preferred: Int, resumeToken: String? = null): AcceptanceSocket {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        var last: Exception? = null
        do {
            for (node in settings.publicIngressNodes(preferred)) {
                if (node == intentionallyUnavailableBackend) continue
                try {
                    val socket = AcceptanceSocket(client.webSocketSession(settings.urls[node].replace("http:", "ws:") + "/game") {
                        settings.syntheticSourceAddress(account.userId)?.let { header("X-Forwarded-For", it) }
                    }, node)
                    try {
                        socket.send(ClientMessage.ConnectAccount(account.accessToken, resumeToken))
                        socket.ready = socket.await(25_000, "SessionReady") { it is ServerMessage.SessionReady } as ServerMessage.SessionReady
                        publicSockets[node].incrementAndGet()
                        return socket
                    } catch (failure: Throwable) { socket.closeGracefully(); throw failure }
                } catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (last == null) {
                        Files.createDirectories(settings.receipts)
                        Files.writeString(settings.receipts.resolve("connection-first-failure.json"), rehearsalFailureDiagnostic(failure))
                    }
                    if (failure is AcceptanceRejected && failure.code !in RETRYABLE_CODES) throw failure
                    last = failure
                }
            }
            delay(100)
        } while (System.nanoTime() < deadline)
        throw IllegalStateException("Fixture socket did not recover in 25 seconds", last)
    }

    suspend fun snapshot(roomId: String): StoredRoomReceipt = withContext(Dispatchers.IO) {
        withFixtureDatabase { connection ->
            connection.prepareStatement("SELECT owner_epoch, revision, snapshot::text, instance_id FROM room_ownership_pilot_rooms WHERE room_id=?").use {
                it.setObject(1, UUID.fromString(roomId))
                it.executeQuery().use { rows ->
                    check(rows.next()) { "Fixture room disappeared" }
                    StoredRoomReceipt(rows.getLong(1), rows.getLong(2),
                        ProtocolJson.decodeFromString(rows.getString(3)), rows.getObject(4, UUID::class.java))
                }
            }
        }
    }

    suspend fun awaitStored(roomId: String, predicate: (StoredRoomReceipt) -> Boolean): StoredRoomReceipt =
        withTimeout(25_000) {
            while (true) {
                val stored = snapshot(roomId)
                if (predicate(stored)) return@withTimeout stored
                delay(25)
            }
            error("Unreachable")
        }

    suspend fun sql(query: String) = withContext(Dispatchers.IO) {
        withFixtureDatabase { it.createStatement().use { statement -> statement.execute(query) } }
    }

    suspend fun number(query: String): Long = withContext(Dispatchers.IO) {
        withFixtureDatabase { it.createStatement().use { statement ->
            statement.executeQuery(query).use { rows -> check(rows.next()); rows.getLong(1) }
        } }
    }

    private suspend fun docker(vararg args: String): String = withContext(Dispatchers.IO) {
        val (base, override) = checkNotNull(settings.composeFiles) { "Docker commands require the local Compose fixture" }
        require(args.firstOrNull() in setOf("kill", "start", "stop", "pause", "unpause", "ps", "logs"))
        require(args.drop(1).all { it in setOf("backend-a", "backend-b", "postgres", "redis", "--no-deps", "--tail", "40") })
        val command = listOf("docker", "compose", "-p", settings.project, "-f", base.toString(),
            "-f", override.toString()) + args
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = async { process.inputStream.bufferedReader().readText() }
        if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Fixture Docker action timed out") }
        val text = output.await()
        check(process.exitValue() == 0) { "Fixture Docker action failed" }
        text
    }

    suspend fun readiness(node: Int): Boolean = try {
        client.get(settings.urls[node] + "/ready").status == HttpStatusCode.OK
    } catch (failure: Exception) { currentCoroutineContext().ensureActive(); false }

    suspend fun awaitReady(node: Int) = withTimeout(35_000) {
        while (!readiness(node)) delay(100)
    }

    suspend fun requireBothReady() {
        check(readiness(0) && readiness(1)) { "Both fixture processes must be reachable before a fault" }
    }

    suspend fun metrics(): List<String> = (0 until backendCount).mapNotNull { node ->
        if (node == intentionallyUnavailableBackend) null
        else {
            try {
                val response = client.get(settings.urls[node] + "/internal/metrics")
                check(response.status == HttpStatusCode.OK)
                response.bodyAsText()
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                // A scrape may already be in flight when the fault controller kills this node.
                if (node == intentionallyUnavailableBackend) null else throw failure
            }
        }
    }

    suspend fun sampleBacklog(): Double = metrics().maxOf { metric(it, "fasttowin_room_ownership_oldest_queued_event_seconds") }
    suspend fun poolSaturation(): Double = metrics().maxOf {
        metric(it, "fasttowin_room_ownership_pool_active_connections") / metric(it, "fasttowin_room_ownership_pool_max_connections")
    }

    suspend fun faultOwner(match: AcceptanceMatch): Long = faultOwners(listOf(match), hardKill = true)

    suspend fun faultOwners(matches: List<AcceptanceMatch>, hardKill: Boolean): Long {
        require(matches.isNotEmpty())
        requireBothReady()
        val before = matches.associateWith { snapshot(it.roomId) }
        val owner = ownerFor(before.values.first())
        check(before.values.all { ownerFor(it) == owner }) { "Fault batch must share one owner process" }
        val observationStart = System.nanoTime()
        fun observe(stage: String) {
            if (supervised && !hardKill) {
                val millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - observationStart)
                Files.writeString(settings.receipts.resolve("load-outages.ndjson"),
                    """{"stage":"$stage","owner":$owner,"rooms":${matches.size},"elapsedMillis":$millis}""" + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
            }
        }
        intentionallyUnavailableBackend = owner
        var primaryFailure: Throwable? = null
        try {
            observe("inject")
            val timer = RecoveryTimer.inject { faults.backend(owner, if (hardKill) BackendAction.KILL else BackendAction.PAUSE) }
            observe("acknowledged")
            // Measure reconnect from the outage, including the peer worker's lease takeover.
            coroutineScope {
                matches.map { match ->
                    async { match.reconnect() }
                }.awaitAll()
            }
            val after = matches.associateWith { match ->
                awaitStored(match.roomId) { it.epoch > checkNotNull(before[match]).epoch }
            }
            after.forEach { (match, receipt) ->
                val previous = checkNotNull(before[match])
                if (!settings.soleIngress) ownerNodes[receipt.instanceId] = 1 - owner
                val newOwner = ownerFor(receipt)
                check(newOwner != owner) { "Takeover must belong to the verified surviving process" }
                check(receipt.snapshot.selectedNumbers == previous.snapshot.selectedNumbers) { "A committed board changed during failover" }
                check(receipt.snapshot.matchId == previous.snapshot.matchId && receipt.snapshot.numbers == previous.snapshot.numbers &&
                    receipt.snapshot.scores == previous.snapshot.scores && receipt.snapshot.sequence >= previous.snapshot.sequence)
                match.ownerBackend = newOwner
            }
            val elapsed = timer.elapsedMillis()
            observe("takeover")
            check(elapsed <= 25_000) { "Owner recovery took ${elapsed}ms (limit 25000ms)" }
            return elapsed
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                faults.backend(owner, if (hardKill) BackendAction.START else BackendAction.RESUME)
                observe("restored")
                awaitReady(owner)
                if (settings.soleIngress) refreshOwnerProcesses(listOf(owner))
                observe("ready")
            } catch (restartFailure: Throwable) {
                if (!supervised) for (node in 0..1) {
                    val logs = runCatching { docker("logs", "--tail", "40", nodeName(node)) }.getOrDefault("Fixture log unavailable")
                    Files.writeString(settings.receipts.resolve("restart-failure-${nodeName(node)}.log"), logs)
                }
                if (primaryFailure != null) primaryFailure.addSuppressed(restartFailure) else throw restartFailure
            } finally { intentionallyUnavailableBackend = null }
        }
    }

    fun ownerFor(receipt: StoredRoomReceipt): Int = checkNotNull(ownerNodes[receipt.instanceId]) { "Unknown fixture owner process" }

    private suspend fun toggleLocalProxy(node: Int, blocked: Boolean) {
        val response = client.post(settings.proxyUrls[node] + "/proxies/pg") {
            contentType(ContentType.Application.Json)
            setBody("""{"enabled":${!blocked}}""")
        }
        check(response.status == HttpStatusCode.OK)
    }

    suspend fun captureDiagnostics(failure: Throwable? = null) = withContext(NonCancellable) {
        if (supervised || Files.exists(settings.receipts.resolve("failure-backend-a.log"))) return@withContext
        failure?.let { Files.writeString(settings.receipts.resolve("failure-stack.txt"), it.stackTraceToString()) }
        for (node in 0 until backendCount) {
            val raw = runCatching { docker("logs", "--tail", "40", nodeName(node)) }.getOrDefault("Fixture logs unavailable")
            var safe = raw.replace(settings.databasePassword, "[redacted]").replace(settings.accountPassword, "[redacted]")
            cachedAccounts.orEmpty().forEach { safe = safe.replace(it.accessToken, "[redacted]") }
            safe = safe.replace(Regex("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"), "[synthetic-id]")
            Files.writeString(settings.receipts.resolve("failure-${nodeName(node)}.log"), safe)
        }
        runCatching {
            val counts = mapOf(
                "pendingSettlements" to number("SELECT count(*) FROM room_ownership_pilot_rooms WHERE snapshot->'pendingCompletedMatch' IS NOT NULL AND snapshot->'pendingCompletedMatch' <> 'null'::jsonb"),
                "matches" to number("SELECT count(*) FROM matches"),
                "expiredLeases" to number("SELECT count(*) FROM room_ownership_pilot_rooms WHERE closed_at IS NULL AND lease_until<clock_timestamp()"),
                "settlements" to number("SELECT count(*) FROM room_ownership_pilot_settlements")
            )
            Files.writeString(settings.receipts.resolve("failure-counts.json"), ProtocolJson.encodeToString(counts))
            metrics().forEachIndexed { node, text -> Files.writeString(settings.receipts.resolve("failure-metrics-$node.prom"), text) }
        }
    }

    override fun close() { faults.close(); client.close(); source.close() }
}

internal data class StoredRoomReceipt(val epoch: Long, val revision: Long, val snapshot: com.hienthai.fastowin.server.StoredActiveRoom, val instanceId: UUID)

internal class AcceptanceSocket(val session: DefaultClientWebSocketSession, val backend: Int) {
    lateinit var ready: ServerMessage.SessionReady
    suspend fun send(message: ClientMessage) = session.send(ProtocolJson.encodeToString<ClientMessage>(message))
    suspend fun await(timeoutMillis: Long = 8_000, expected: String = "protocol response", predicate: (ServerMessage) -> Boolean): ServerMessage = try {
        withTimeout(timeoutMillis) {
            for (frame in session.incoming) if (frame is Frame.Text) {
                val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                if (message is ServerMessage.Error) throw AcceptanceRejected(message.code)
                if (predicate(message)) return@withTimeout message
            }
            val close = withTimeoutOrNull(250) { session.closeReason.await() }
            throw ObservedSocketClose(backend, close?.code?.toInt() ?: 0, close?.message)
        }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        throw IllegalStateException("Fixture timed out waiting for $expected after ${timeoutMillis}ms on backend $backend", timeout)
    }
    suspend fun closeGracefully() {
        runCatching {
            withTimeout(1_000) { session.close(CloseReason(CloseReason.Codes.NORMAL, "fixture recycle")) }
        }
        session.cancel()
    }
    suspend fun leaveRoom(roomId: String) = withTimeout(25_000) {
        send(ClientMessage.LeaveRoom(roomId))
        while (true) {
            val response = try {
                await {
                    (it is ServerMessage.RoomClosed && it.roomId == roomId) ||
                        (it is ServerMessage.GameFinished && it.game.roomId == roomId)
                }
            } catch (failure: AcceptanceRejected) {
                if (failure.code != "ROOM_RECOVERING") throw failure
                // Concurrent settlement can advance the revision between read and fenced leave.
                delay(100)
                send(ClientMessage.LeaveRoom(roomId))
                continue
            }
            if (response is ServerMessage.RoomClosed) return@withTimeout
            // GameFinished acknowledges finishing play, not leaving the results membership.
            send(ClientMessage.LeaveRoom(roomId))
        }
    }
    fun close() { session.cancel() }
}

internal class AcceptanceMatch(
    private val fixture: RoomAcceptanceFixture,
    private val accounts: List<AcceptanceAccount>,
    private val sockets: MutableList<AcceptanceSocket>,
    initial: GameSnapshot,
    var ownerBackend: Int
) {
    val roomId = initial.roomId
    private var stableMatchId = initial.matchId.takeIf { initial.phase != RoomPhase.WAITING }
    private var stableBoard = initial.numbers.takeIf { initial.phase != RoomPhase.WAITING }
    val matchId get() = stableMatchId ?: lastSnapshot.matchId
    var nextNumber = initial.currentTarget
        private set
    var lastSnapshot = initial
        private set
    private val mutex = Mutex()

    fun socket(playerIndex: Int) = sockets[playerIndex]
    fun playerId(playerIndex: Int) = accounts[playerIndex].userId

    suspend fun select(number: Int, requestId: String, playerIndex: Int = 0, verifyPeer: Boolean = true): SelectionReceipt = mutex.withLock {
        val started = System.nanoTime()
        val deadline = started + TimeUnit.SECONDS.toNanos(28)
        var attempts = 0
        while (true) {
            attempts++
            try {
                sockets[playerIndex].send(ClientMessage.SelectNumber(roomId, number, requestId))
                val own = sockets[playerIndex].await(timeoutMillis = 25_000) {
                    it is ServerMessage.GameStateUpdated && it.acceptedNumber == number
                } as ServerMessage.GameStateUpdated
                val other = if (verifyPeer) sockets[1 - playerIndex].await(timeoutMillis = 25_000) {
                    it is ServerMessage.GameStateUpdated && it.acceptedNumber == number
                } as ServerMessage.GameStateUpdated else own
                check(own.game == other.game) { "Clients disagree on the committed selection" }
                lastSnapshot = own.game
                nextNumber = own.game.currentTarget
                return@withLock SelectionReceipt((System.nanoTime() - started) / 1_000_000.0, own.game, attempts)
            } catch (failure: Throwable) {
                currentCoroutineContext().ensureActive()
                if (failure is AcceptanceRejected && failure.code !in RETRYABLE_CODES) throw failure
                if (failure is IllegalStateException && failure !is AcceptanceRejected &&
                    failure.message == "Clients disagree on the committed selection") throw failure
                if (failure !is EOFException && failure.cause !is EOFException && failure !is AcceptanceRejected) throw failure
                check(System.nanoTime() < deadline) { "Selection could not reconcile in 28 seconds" }
                reconnectInternalWithRetry()
                if (number in lastSnapshot.selectedNumbers) {
                    val stored = fixture.snapshot(roomId).snapshot
                    check(stored.selectionEvents.any { it.playerId == accounts[playerIndex].userId && it.requestId == requestId && it.number == number }) {
                        "The reconciled selection was not this request"
                    }
                    return@withLock SelectionReceipt((System.nanoTime() - started) / 1_000_000.0, lastSnapshot, attempts)
                }
            }
        }
        error("Unreachable")
    }

    suspend fun sendWithoutReading(command: ClientMessage, playerIndex: Int = 0) = mutex.withLock { sockets[playerIndex].send(command) }

    internal suspend fun reconnectInternalWithRetry() {
        var last: Throwable? = null
        repeat(5) {
            try { reconnectInternal(); return }
            catch (failure: Exception) { currentCoroutineContext().ensureActive(); last = failure; delay(100) }
        }
        throw IllegalStateException("Fixture socket reconnect failed", last)
    }

    suspend fun reconnect(preserveOldSockets: Boolean = false): Long = mutex.withLock {
        val start = System.nanoTime()
        var last: Throwable? = null
        while (System.nanoTime() - start < TimeUnit.SECONDS.toNanos(25)) {
            try {
                reconnectInternal(preserveOldSockets)
                return@withLock TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                last = failure
                delay(100)
            }
        }
        throw IllegalStateException("Fixture match reconnect did not complete in 25 seconds", last)
    }

    private suspend fun reconnectInternal(preserveOldSockets: Boolean = false) {
        if (!preserveOldSockets) {
            for (socket in sockets) socket.closeGracefully()
        }
        val backend = if (fixture.intentionallyUnavailableBackend == ownerBackend) 1 - ownerBackend else ownerBackend
        val newSockets = fixture.connectPair(accounts[0], backend, accounts[1], backend)
        val snapshots = try {
            verifyReconnectSnapshots(newSockets)
        } catch (failure: Throwable) {
            newSockets.forEach { it.close() }
            throw failure
        }
        sockets.clear(); sockets.addAll(newSockets)
        lastSnapshot = snapshots.last()
        if (lastSnapshot.phase != RoomPhase.WAITING) {
            stableMatchId = lastSnapshot.matchId
            stableBoard = lastSnapshot.numbers
        }
        nextNumber = lastSnapshot.currentTarget
    }

    private fun verifyReconnectSnapshots(newSockets: List<AcceptanceSocket>): List<GameSnapshot> {
        val snapshots = newSockets.map { checkNotNull(it.ready.currentGame) { "Match membership was lost during recovery" } }
        check(snapshots.all { it.roomId == roomId && it.matchId == snapshots[0].matchId && it.selectedNumbers == snapshots[0].selectedNumbers })
        for (snapshot in snapshots) {
            stableMatchId?.let { check(snapshot.matchId == it) { "Recovery replaced the stable match" } }
            stableBoard?.let { check(snapshot.numbers == it) { "Recovery replaced the committed board" } }
            check(snapshot.selectedNumbers.take(lastSnapshot.selectedNumbers.size) == lastSnapshot.selectedNumbers) { "Recovery lost committed selections" }
            check(snapshot.sequence >= lastSnapshot.sequence) { "Recovery regressed the sequence" }
            for (player in lastSnapshot.players) {
                val current = snapshot.players.single { it.id == player.id }
                check(current.score >= player.score && current.correctSelections >= player.correctSelections) { "Recovery lost points" }
            }
        }
        return snapshots
    }

    suspend fun verifyFinishedEconomy(snapshot: GameSnapshot, intentionalLeaver: String) {
        check(snapshot.phase == RoomPhase.FINISHED && snapshot.roomId == roomId && snapshot.matchId == matchId)
        check(accounts.any { it.userId == intentionalLeaver })
        lastSnapshot = snapshot
        verifyEconomy(setOf(intentionalLeaver))
    }

    suspend fun verifyEconomy(unrewardedPlayers: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        UUID.fromString(matchId)
        val receiptCount = fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE match_id='$matchId' AND reservation_state='FINALIZED'")
        check(receiptCount == 2L) { "Each match must debit each player once" }
        if (lastSnapshot.phase == RoomPhase.FINISHED) {
            withTimeout(30_000) { while (fixture.number("SELECT count(*) FROM matches WHERE id='$matchId'") != 1L) delay(50) }
            val wallets = fixture.number("SELECT count(*) FROM wallet_transactions WHERE source_type='MATCH' AND source_id='$matchId'")
            val expectedPayees = accounts.map { it.userId }.toSet() - unrewardedPlayers
            check(wallets == expectedPayees.size.toLong()) { "A completed match must reward each eligible player once" }
            fixture.withFixtureDatabase { connection ->
                connection.prepareStatement("SELECT user_id,gold_delta,xp_delta,gems_delta FROM wallet_transactions WHERE source_type='MATCH' AND source_id=?").use { statement ->
                    statement.setString(1, matchId)
                    statement.executeQuery().use { rows ->
                        val paid = mutableSetOf<String>()
                        while (rows.next()) {
                            val id = rows.getObject(1).toString()
                            val winner = lastSnapshot.winnerPlayerId
                            val expected = when { winner == null -> 40 to 16; winner == id -> 80 to 24; else -> 20 to 8 }
                            check(rows.getInt(2) == expected.first && rows.getInt(3) == expected.second && rows.getInt(4) == 0)
                            check(paid.add(id))
                        }
                        check(paid == expectedPayees) { "Match paid unexpected players" }
                    }
                }
                for (account in accounts) {
                    connection.prepareStatement("""
                        SELECT s.total_matches, (SELECT count(*) FROM match_players p WHERE p.user_id=s.user_id),
                          s.experience_points, s.gold,
                          (SELECT coalesce(sum(xp_delta),0) FROM wallet_transactions w WHERE w.user_id=s.user_id),
                          (SELECT coalesce(sum(gold_delta),0) FROM wallet_transactions w WHERE w.user_id=s.user_id)
                        FROM player_stats s WHERE s.user_id=?
                    """.trimIndent()).use { statement ->
                        statement.setObject(1, UUID.fromString(account.userId))
                        statement.executeQuery().use { rows ->
                            check(rows.next() && rows.getLong(1) == rows.getLong(2)) { "Statistics counted a match twice" }
                            check(rows.getLong(3) == rows.getLong(5) && rows.getLong(4) == rows.getLong(6)) { "Stats and reward ledger diverged" }
                        }
                    }
                }
            }
        }
    }

    suspend fun close() = mutex.withLock {
        for (socket in sockets) {
            try {
                socket.leaveRoom(roomId)
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                Files.createDirectories(fixture.settings.receipts)
                Files.writeString(fixture.settings.receipts.resolve("close-failure.json"),
                    rehearsalFailureDiagnostic(failure))
            }
            socket.closeGracefully()
        }
    }
}

internal fun nodeName(index: Int) = if (index == 0) "backend-a" else "backend-b"
internal fun metric(text: String, name: String): Double = text.lineSequence().first { it.startsWith("$name ") }.substringAfter(' ').toDouble()
private val RETRYABLE_CODES = setOf("ROOM_RECOVERING", "ROOM_DRAINING", "MAINTENANCE")
