package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.AuthenticatedAccount
import com.hienthai.fastowin.server.PostgresMatchResultRepository
import com.hienthai.fastowin.server.PostgresPlayQuotaRepository
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.admin.AdminException
import com.hienthai.fastowin.server.events.EventRewardSource
import com.hienthai.fastowin.server.events.NoRewardEventSource
import com.hienthai.fastowin.server.legacyFallback
import io.ktor.server.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import com.zaxxer.hikari.HikariDataSource

internal class AdmittedPilotConnection(
    val socket: SocketFence,
    val initialReply: OwnedRoomReply,
    private val permit: AutoCloseable
) : AutoCloseable {
    override fun close() = permit.close()
}

internal class RoomOwnershipPilot private constructor(
    private val config: RoomOwnershipRuntimeConfig,
    private val instanceId: UUID,
    private val rooms: PostgresRoomOwnershipRepository,
    private val engine: OwnedRoomEngine,
    private val settlementWakeups: Channel<Unit>,
    private val transport: OwnerPeerTransport,
    private val newIntentAllowed: () -> Boolean,
    private val admissionAuthority: PostgresOwnedAdmissionRepository?,
    private val admissionBinding: OwnedAdmissionBinding?,
    private val metrics: RoomOwnershipMetrics,
    private val pool: HikariDataSource?,
    private val candidateIdentity: () -> OwnedCandidateIdentity,
    wakeups: Flow<UUID>
) {
    var onPresenceChanged: (Set<UUID>) -> Unit = {}
    val delivery = RoomEventDelivery(rooms, wakeups, reattach = ::reconnect, metrics = metrics,
        onCommittedStateChanged = { onPresenceChanged(it) })
    private val admission = RoomAdmissionGate()
    private val lastHealthyTick = AtomicLong()
    private val drainMutex = Mutex()
    private val rollbackGuard = RoomOwnershipRollbackGuard()
    @Volatile
    private var drained = false

    private val clusterControl by lazy {
        OwnedClusterControl(config, instanceId, checkNotNull(admissionAuthority), checkNotNull(admissionBinding),
            rooms, transport, candidateIdentity, engine::closeWaitingForDrain, ::beginDrain, { drained }, ::isAccepting)
    }

    fun allows(account: AuthenticatedAccount): Boolean = account.emailVerified && account.userId in config.allowedAccounts

    val isProductionMode: Boolean get() = config.fixtureId == null

    suspend fun presence(socket: SocketFence): OwnedPresenceRead = try {
        rooms.presence(socket)
    } catch (_: SQLException) {
        OwnedPresenceRead.Unavailable
    }

    /** Sole ingress coordinates global maintenance; worker readiness only observes admission. */
    suspend fun maintenanceWorkCount(): Int {
        if (!isProductionMode) {
            val snapshot = rooms.durableSnapshot()
            return Math.addExact(snapshot.remainingOpenRooms, snapshot.pendingSettlements)
        }
        check(config.runtimeRole == RoomOwnershipRuntimeRole.INGRESS)
        val authority = checkNotNull(admissionAuthority)
        val observed = authority.observe() ?: throw OwnedAdmissionRejected("Missing owned admission authority")
        val binding = checkNotNull(admissionBinding)
        if (observed.topologySha256 != binding.topologySha256 || observed.policySha256 != binding.policySha256) {
            throw OwnedAdmissionRejected("Owned maintenance configuration does not match admission authority")
        }
        val stopped = if (observed.state == OwnedAdmissionState.ACTIVE) authority.stopNew(observed.generation) else observed
        return authority.maintenanceWorkCount(stopped, binding)
    }

    suspend fun ownedRoom(playerId: UUID): UUID? = rooms.lookupRoom(playerId)

    /** Every authenticated production connect uses this, including accounts outside the owned slice. */
    suspend fun bindGameLane(playerId: UUID): OwnedLaneBinding {
        check(isProductionMode && config.runtimeRole == RoomOwnershipRuntimeRole.INGRESS)
        val socket = rooms.bindSocketForLane(playerId)
        // This must be a new transaction after bindSocketForLane has acknowledged COMMIT.
        val roomId = rooms.lookupRoom(playerId)
        if (!rooms.isCurrentSocket(socket)) throw OwnedAdmissionRejected("Account generation was replaced during reconciliation")
        return OwnedLaneBinding(socket, roomId)
    }

    suspend fun ownsRoom(roomId: String): Boolean = parsePilotUuid(roomId)?.let { rooms.findOwner(it) != null } ?: false

    fun routesCreate(account: AuthenticatedAccount, command: ClientMessage.CreateRoom): Boolean =
        RoomOwnershipRouting(config).route(account, command.gameMode, command.matchType, command.password.isNotBlank()) is RoomOwnershipRoute.Owned

    fun isAccepting(): Boolean = admission.accepting

    suspend fun isReady(): Boolean {
        if (!isAccepting() || !workerHealthy()) return false
        return try {
            (config.fixtureId?.let { rooms.fixtureReady(it) } ?: (rooms.schemaReady() &&
                admissionAuthority?.observe()?.admits(checkNotNull(admissionBinding)) == true)) &&
                isAccepting() && workerHealthy()
        } catch (_: SQLException) {
            false
        }
    }

    private fun workerHealthy(): Boolean {
        val lastTick = lastHealthyTick.get()
        return lastTick != 0L && System.nanoTime() - lastTick < TimeUnit.SECONDS.toNanos(8)
    }

    suspend fun runWorker() {
        var nextRenewal = 0L
        try {
            while (currentCoroutineContext().isActive) {
                if (System.nanoTime() >= nextRenewal) {
                    val healthy = admission.admit { engine.renewalTick() } ?: return
                    lastHealthyTick.set(if (healthy) System.nanoTime() else 0L)
                    nextRenewal = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                }
                val remainingMillis = TimeUnit.NANOSECONDS.toMillis(nextRenewal - System.nanoTime()).coerceAtLeast(1)
                // Waiting must not hold an admission permit, otherwise drain waits on idle work.
                val notified = withTimeoutOrNull(remainingMillis) { settlementWakeups.receive(); true } ?: false
                if (notified) admission.admit { engine.settlePending(); Unit } ?: return
            }
        } finally {
            lastHealthyTick.set(0)
        }
    }

    /** Drain this process for handoff to another owner runtime; this does not authorize legacy rollback. */
    suspend fun beginDrain(): Boolean = drainMutex.withLock {
        if (drained) return true
        admission.beginDrain().await()
        try {
            engine.settlePending()
            if (rooms.durableSnapshot(instanceId).pendingSettlements != 0) return false
            // Look up durable ownership rather than only the engine cache, including a create with an uncertain reply.
            val owned = rooms.ownedFences(instanceId)
            owned.forEach { rooms.release(it) }
            val remaining = rooms.durableSnapshot(instanceId)
            if (remaining.remainingOwnedLeases != 0 || remaining.pendingSettlements != 0) return false
        } catch (_: SQLException) {
            return false
        }
        delivery.closeForDrain()
        drained = true
        true
    }

    /** Call only after stopping admission on every runtime before removing owned routing. */
    suspend fun canRollback(): Boolean {
        if (isProductionMode || !drained) return false
        return try {
            val snapshot = rooms.durableSnapshot()
            rollbackGuard.canRollback(RoomOwnershipDrainReceipt(
                drained = drained,
                remainingOwnedLeases = snapshot.remainingOwnedLeases,
                pendingSettlements = snapshot.pendingSettlements,
                remainingOpenRooms = snapshot.remainingOpenRooms
            ))
        } catch (_: SQLException) {
            false
        }
    }

    suspend fun renderMetrics(): String {
        val queuedAge = try {
            rooms.oldestQueuedEventAgeSeconds(delivery.queuedPositions())
        } catch (_: SQLException) {
            Double.NaN
        }
        return metrics.render(queuedAge, pool?.hikariPoolMXBean?.activeConnections, pool?.maximumPoolSize)
    }

    suspend fun onConnected(account: AuthenticatedAccount): AdmittedPilotConnection? {
        require(allows(account))
        val permit = admission.acquire() ?: return null
        try {
            val socket = rooms.bindSocket(account.userId)
            return AdmittedPilotConnection(socket, reconnectRoom(socket), permit)
        } catch (failure: Throwable) {
            permit.close()
            throw failure
        }
    }

    suspend fun isCurrent(socket: SocketFence): Boolean = rooms.isCurrentSocket(socket)

    suspend fun roomList(): ServerMessage = try {
        ServerMessage.RoomList(rooms.listJoinableRooms())
    } catch (_: SQLException) {
        unavailable().publicMessage
    }

    fun installRoutes(route: Route) {
        route.ownerCommandRoutes(transport, ::onPeerRequest)
        if (isProductionMode) {
            route.ownerCreateRoutes(transport, ::onPeerCreate)
            route.ownerControlRoutes(transport, clusterControl::dispatch)
        }
    }

    /** Task 3 owns serialization and retention. This method prepares a new, never-sent descriptor only. */
    suspend fun prepareOwnedCreate(socket: SocketFence, command: ClientMessage.CreateRoom): OwnedCreatePreparation = try {
        if (!isProductionMode || config.runtimeRole != RoomOwnershipRuntimeRole.INGRESS ||
            socket.playerId !in config.allowedAccounts || !rooms.isCurrentSocket(socket)) {
            OwnedCreatePreparation.Rejected("CREATE_NOT_ALLOWED")
        } else {
            val existing = rooms.lookupRoom(socket.playerId)
            if (existing != null) OwnedCreatePreparation.Existing(existing)
            else {
                val policy = admissionAuthority?.observe()?.takeIf { it.admits(checkNotNull(admissionBinding)) }
                if (policy == null || !newIntentAllowed() || !isAccepting()) OwnedCreatePreparation.Rejected("OWNED_ADMISSION_STOPPED")
                else {
                    val key = socket.playerId.toString()
                    val request = OwnedCreateRequest(UUID.randomUUID(), UUID.randomUUID(), socket, key,
                        RoomOwnershipRouting(config).createNode(key), policy, command)
                    if (request.isSupported()) OwnedCreatePreparation.Ready(request)
                    else OwnedCreatePreparation.Rejected("INVALID_CREATE")
                }
            }
        }
    } catch (_: SQLException) { OwnedCreatePreparation.Rejected("CREATE_STORAGE_UNAVAILABLE") }

    suspend fun createOwned(request: OwnedCreateRequest): OwnedCreateResult = admission.admit {
        if (config.runtimeRole != RoomOwnershipRuntimeRole.INGRESS || !validCreate(request)) {
            OwnedCreateResult.AttemptRejected("CREATE_NOT_ALLOWED")
        } else if (request.destinationNodeId == config.nodeId) executeCreate(request)
        else transport.forwardCreate(request)
    } ?: OwnedCreateResult.Unresolved("PROCESS_DRAINING")

    private fun validCreate(request: OwnedCreateRequest): Boolean = isProductionMode && request.isSupported() &&
        request.socket.playerId in config.allowedAccounts &&
        request.policy.topologySha256 == admissionBinding?.topologySha256 &&
        request.policy.policySha256 == admissionBinding?.policySha256 &&
        request.destinationNodeId == RoomOwnershipRouting(config).createNode(request.routeKey)

    /** Only configured A can send a create to configured worker B; peer HMAC alone is insufficient. */
    private suspend fun onPeerCreate(request: OwnedCreateRequest): OwnedCreateResult = admission.admit {
        if (config.runtimeRole != RoomOwnershipRuntimeRole.OWNED_WORKER || !validCreate(request) ||
            request.destinationNodeId != config.nodeId) OwnedCreateResult.AttemptRejected("CREATE_NOT_ALLOWED")
        else executeCreate(request)
    } ?: OwnedCreateResult.Unresolved("PROCESS_DRAINING")

    private suspend fun executeCreate(request: OwnedCreateRequest): OwnedCreateResult = try {
        // The repository applies this extra process/maintenance guard only after locked journal lookup.
        engine.createOwned(request, newIntentAllowed)
    } catch (_: SQLException) { OwnedCreateResult.Unresolved("CREATE_STORAGE_UNAVAILABLE") }

    suspend fun onMessage(
        socket: SocketFence,
        command: ClientMessage,
        synchronizedRoom: RoomDeliveryPosition? = null
    ): OwnedRoomReply = admission.admit { handleMessage(socket, command, synchronizedRoom) } ?: draining(command)

    private suspend fun handleMessage(
        socket: SocketFence,
        command: ClientMessage,
        synchronizedRoom: RoomDeliveryPosition?
    ): OwnedRoomReply = try {
        val expectedPolicy = if (command.needsPilotAdmission() && admissionAuthority != null) {
            admissionAuthority.observe()?.takeIf { it.admits(checkNotNull(admissionBinding)) }
        } else null
        when {
            socket.playerId !in config.allowedAccounts -> denied()
            command is ClientMessage.CreateRoom && !config.supportsMode(command.gameMode) -> unsupported()
            command.needsPilotAdmission() && admissionAuthority != null && expectedPolicy == null -> maintenance()
            command.needsPilotAdmission() && !newIntentAllowed() -> maintenance()
            command is ClientMessage.CreateRoom -> {
                val existing = rooms.lookupRoom(socket.playerId)
                if (existing == null) {
                    engine.handle(socket, command, admissionPolicy = expectedPolicy).also { recordRoute(it, peer = false) }
                }
                else route(existing, socket, OwnerOperation.RECONNECT, synchronizedRoom = synchronizedRoom)
            }
            else -> {
                val roomId = command.pilotRoomId()
                val id = roomId?.let(::parsePilotUuid)
                when {
                    roomId == null -> unsupported()
                    id == null -> missing()
                    else -> route(id, socket, OwnerOperation.COMMAND, command, synchronizedRoom, expectedPolicy)
                }
            }
        }
    } catch (_: SQLException) {
        metrics.routeFailed()
        unavailable(command)
    }

    suspend fun reconnect(socket: SocketFence): OwnedRoomReply = admission.admit { reconnectRoom(socket) } ?: draining()

    private suspend fun reconnectRoom(socket: SocketFence): OwnedRoomReply = try {
        val id = rooms.lookupRoom(socket.playerId)
        if (id == null) OwnedRoomReply.NoRoom else route(id, socket, OwnerOperation.RECONNECT)
    } catch (_: SQLException) {
        metrics.routeFailed()
        unavailable()
    }

    suspend fun onDisconnected(socket: SocketFence) {
        admission.admit {
            try {
                rooms.lookupRoom(socket.playerId)?.let { route(it, socket, OwnerOperation.DISCONNECT) }
            } catch (_: SQLException) {
                metrics.routeFailed()
                // Socket replacement still invalidates the generation in PostgreSQL.
            }
            Unit
        }
    }

    private suspend fun route(
        roomId: UUID,
        socket: SocketFence,
        operation: OwnerOperation,
        command: ClientMessage? = null,
        synchronizedRoom: RoomDeliveryPosition? = null,
        admissionPolicy: OwnedAdmissionPolicy? = null
    ): OwnedRoomReply {
        val fence = rooms.findOwner(roomId) ?: return missing()
        if (synchronizedRoom?.roomId == roomId && synchronizedRoom.epoch != fence.epoch) {
            metrics.routeFailed()
            return unavailable(command)
        }
        if (fence.instanceId == instanceId) {
            return dispatch(fence, socket, operation, command, admissionPolicy).also { recordRoute(it, peer = false) }
        }
        val result = transport.forward(
            OwnerCommandRequest(
                roomId.toString(), fence.epoch, socket.playerId.toString(), socket.generation, operation, command,
                ownerInstanceId = fence.instanceId.toString(), admissionPolicy = admissionPolicy
            )
        )
        if (result == null) metrics.routeFailed() else recordRoute(result, peer = true)
        return result ?: unavailable(command)
    }

    private fun recordRoute(result: OwnedRoomReply, peer: Boolean) {
        when {
            result is OwnedRoomReply.Retryable -> metrics.routeFailed()
            peer -> metrics.peerRoute()
            else -> metrics.localRoute()
        }
    }

    private suspend fun onPeerRequest(request: OwnerCommandRequest): OwnedRoomReply =
        admission.admit { handlePeerRequest(request) } ?: draining(request.command)

    private suspend fun handlePeerRequest(request: OwnerCommandRequest): OwnedRoomReply = try {
        val playerId = parsePilotUuid(request.playerId)
        val roomId = parsePilotUuid(request.roomId)
        val command = request.command
        when {
            playerId == null || playerId !in config.allowedAccounts -> denied()
            roomId == null || request.epoch <= 0 || request.generation <= 0 -> unsupported()
            parsePilotUuid(request.ownerInstanceId) != instanceId -> unavailable(command)
            request.operation == OwnerOperation.COMMAND && command?.pilotRoomId()?.let(::parsePilotUuid) != roomId -> unsupported()
            request.operation != OwnerOperation.COMMAND && command != null -> unsupported()
            else -> dispatch(OwnerFence(roomId, instanceId, request.epoch),
                SocketFence(playerId, request.generation), request.operation, command, request.admissionPolicy)
        }
    } catch (_: SQLException) {
        unavailable(request.command)
    }

    private suspend fun dispatch(
        fence: OwnerFence,
        socket: SocketFence,
        operation: OwnerOperation,
        command: ClientMessage?,
        admissionPolicy: OwnedAdmissionPolicy? = null
    ): OwnedRoomReply = when (operation) {
        OwnerOperation.COMMAND -> {
            val incoming = checkNotNull(command)
            if (incoming.needsPilotAdmission() && (!newIntentAllowed() ||
                    (admissionBinding != null && admissionPolicy?.admits(admissionBinding) != true))) maintenance()
            else engine.handle(socket, incoming, fence, admissionPolicy)
        }
        OwnerOperation.RECONNECT -> engine.reconnect(socket, fence)
        OwnerOperation.DISCONNECT -> {
            engine.disconnect(socket, fence)
            OwnedRoomReply.NoRoom
        }
    }

    companion object {
        suspend fun open(
            config: RoomOwnershipRuntimeConfig,
            dataSource: DataSource,
            accountPlayer: suspend (UUID) -> StoredActivePlayer?,
            eventRewards: EventRewardSource = NoRewardEventSource,
            wakeups: Flow<UUID>? = null,
            newIntentAllowed: () -> Boolean = { true },
            candidateIdentity: () -> OwnedCandidateIdentity = OwnedCandidateIdentity::running
        ): RoomOwnershipPilot {
            val instanceId = UUID.randomUUID()
            val metrics = RoomOwnershipMetrics()
            val binding = if (config.fixtureId == null) OwnedAdmissionBinding.from(config) else null
            val authority = binding?.let { PostgresOwnedAdmissionRepository(dataSource) }
            val repository = PostgresRoomOwnershipRepository(dataSource, metrics, binding)
            config.fixtureId?.let { fixtureId ->
                require(repository.fixtureReady(fixtureId)) { "Room ownership fixture schema or marker is invalid" }
            } ?: require(repository.schemaReady()) { "Room ownership schema is invalid" }
            verifyAllowedAccounts(dataSource, config)
            val economy = OwnedMatchEconomy(
                repository, PostgresPlayQuotaRepository(dataSource), PostgresMatchResultRepository(dataSource), metrics
            )
            val settlementWakeups = Channel<Unit>(Channel.CONFLATED)
            val engine = OwnedRoomEngine(instanceId, repository, economy, accountPlayer, eventRewards,
                onSettlementPending = { settlementWakeups.trySend(Unit) })
            return RoomOwnershipPilot(
                config, instanceId, repository, engine, settlementWakeups,
                OwnerPeerTransport(config.nodeId, config.peerId, config.peerUri, config.peerSecret),
                newIntentAllowed, authority, binding, metrics, dataSource as? HikariDataSource, candidateIdentity, wakeups ?: postgresRoomEventWakeups(dataSource)
            )
        }

        private suspend fun verifyAllowedAccounts(source: DataSource, config: RoomOwnershipRuntimeConfig) = withContext(Dispatchers.IO) {
            source.connection.use { connection ->
                connection.prepareStatement("""
                    SELECT id FROM users
                    WHERE id = ANY (?) AND account_type = 'REGISTERED' AND status = 'ACTIVE' AND email_verified_at IS NOT NULL
                """.trimIndent()).use { query ->
                    val ids = connection.createArrayOf("uuid", config.allowedAccounts.toTypedArray())
                    try {
                        query.setArray(1, ids)
                        query.executeQuery().use { rows ->
                            val verified = buildSet { while (rows.next()) add(rows.getObject(1, UUID::class.java)) }
                            require(verified == config.allowedAccounts) { "Room ownership accounts must be active and verified" }
                        }
                    } finally {
                        ids.free()
                    }
                }
            }
        }
    }
}

internal fun ClientMessage.isOwnedRoomCommand(): Boolean = this is ClientMessage.CreateRoom || pilotRoomId() != null

internal fun Throwable.isPilotStorageUnavailable(): Boolean =
    this is SQLException || (this is AdminException && status == 503 && code == "STORAGE_UNAVAILABLE")

internal fun ClientMessage.isUnsupportedPilotGameCommand(): Boolean = when (this) {
    is ClientMessage.KickPlayer, is ClientMessage.InviteFriend, is ClientMessage.RespondRoomInvitation,
    is ClientMessage.SendEmoji, is ClientMessage.RequestRematch, is ClientMessage.RespondRematch,
    is ClientMessage.JoinMatchmaking, ClientMessage.CancelMatchmaking,
    is ClientMessage.JoinPublicTournament, is ClientMessage.CreateTournament, is ClientMessage.StartTournament,
    is ClientMessage.InviteTournamentPlayer, is ClientMessage.RespondTournamentInvitation,
    is ClientMessage.CheckInTournament, is ClientMessage.LeaveTournament -> true
    else -> false
}

internal fun OwnedRoomReply.Applied.gameSnapshot(): GameSnapshot? = when (val message = publicMessage) {
    is ServerMessage.RoomCreated -> message.game
    is ServerMessage.RoomUpdated -> message.game
    is ServerMessage.GameStarted -> message.game
    is ServerMessage.GameStateUpdated -> message.game
    is ServerMessage.GameFinished -> message.game
    else -> null
}

internal fun ClientMessage.pilotRoomId(): String? = when (this) {
    is ClientMessage.JoinRoom -> roomId
    is ClientMessage.SetReady -> roomId
    is ClientMessage.SelectNumber -> roomId
    is ClientMessage.LeaveRoom -> roomId
    else -> null
}

private fun ClientMessage.needsPilotAdmission() =
    this is ClientMessage.CreateRoom || this is ClientMessage.JoinRoom || (this is ClientMessage.SetReady && ready)

private fun denied() = OwnedRoomReply.Rejected(ServerMessage.Error("PILOT_ACCOUNT_REQUIRED", "A pilot account is required."))
private fun unsupported() = OwnedRoomReply.Rejected(ServerMessage.Error("UNSUPPORTED_PILOT_COMMAND", "This action is outside the room pilot."))
private fun missing() = OwnedRoomReply.Rejected(ServerMessage.Error("ROOM_NOT_FOUND", legacyFallback("Phòng không còn tồn tại.")))
private fun maintenance() = OwnedRoomReply.Retryable(ServerMessage.Error("MAINTENANCE", legacyFallback("Máy chủ đang bảo trì.")))
private fun draining(command: ClientMessage? = null) = OwnedRoomReply.Retryable(ServerMessage.Error(
    "ROOM_DRAINING", legacyFallback("Máy chủ đang bàn giao phòng. Vui lòng kết nối lại."),
    (command as? ClientMessage.SelectNumber)?.requestId
))
private fun unavailable(command: ClientMessage? = null) = OwnedRoomReply.Retryable(ServerMessage.Error(
    "ROOM_RECOVERING", legacyFallback("Phòng đang đồng bộ. Vui lòng thử lại."),
    (command as? ClientMessage.SelectNumber)?.requestId
))
