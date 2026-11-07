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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

internal class RoomOwnershipPilot private constructor(
    private val config: RoomOwnershipPilotConfig,
    private val instanceId: UUID,
    private val rooms: PostgresRoomOwnershipRepository,
    private val engine: OwnedRoomEngine,
    private val transport: OwnerPeerTransport,
    private val newIntentAllowed: () -> Boolean,
    wakeups: Flow<UUID>
) {
    val delivery = RoomEventDelivery(rooms, wakeups, reattach = ::reconnect)

    fun allows(account: AuthenticatedAccount): Boolean = account.emailVerified && account.userId in config.allowedAccounts

    suspend fun onConnected(account: AuthenticatedAccount): SocketFence {
        require(allows(account))
        return rooms.bindSocket(account.userId)
    }

    suspend fun isCurrent(socket: SocketFence): Boolean = rooms.isCurrentSocket(socket)

    suspend fun roomList(): ServerMessage = try {
        ServerMessage.RoomList(rooms.listJoinableRooms())
    } catch (_: SQLException) {
        unavailable().publicMessage
    }

    fun installRoutes(route: Route) = route.ownerCommandRoutes(transport, ::onPeerRequest)

    suspend fun onMessage(
        socket: SocketFence,
        command: ClientMessage,
        synchronizedRoom: RoomDeliveryPosition? = null
    ): OwnedRoomReply = try {
        when {
            socket.playerId !in config.allowedAccounts -> denied()
            command.needsPilotAdmission() && !newIntentAllowed() -> maintenance()
            command is ClientMessage.CreateRoom -> {
                val existing = rooms.lookupRoom(socket.playerId)
                if (existing == null) engine.handle(socket, command)
                else route(existing, socket, OwnerOperation.RECONNECT, synchronizedRoom = synchronizedRoom)
            }
            else -> {
                val roomId = command.pilotRoomId()
                val id = roomId?.let(::parsePilotUuid)
                when {
                    roomId == null -> unsupported()
                    id == null -> missing()
                    else -> route(id, socket, OwnerOperation.COMMAND, command, synchronizedRoom)
                }
            }
        }
    } catch (_: SQLException) {
        unavailable(command)
    }

    suspend fun reconnect(socket: SocketFence): OwnedRoomReply = try {
        val id = rooms.lookupRoom(socket.playerId)
        if (id == null) OwnedRoomReply.NoRoom else route(id, socket, OwnerOperation.RECONNECT)
    } catch (_: SQLException) {
        unavailable()
    }

    suspend fun onDisconnected(socket: SocketFence) {
        try {
            rooms.lookupRoom(socket.playerId)?.let { route(it, socket, OwnerOperation.DISCONNECT) }
        } catch (_: SQLException) {
            // Socket replacement still invalidates the generation in PostgreSQL.
        }
    }

    private suspend fun route(
        roomId: UUID,
        socket: SocketFence,
        operation: OwnerOperation,
        command: ClientMessage? = null,
        synchronizedRoom: RoomDeliveryPosition? = null
    ): OwnedRoomReply {
        val state = rooms.read(roomId)?.takeUnless { it.closed } ?: return missing()
        val fence = state.fence
        if (synchronizedRoom?.roomId == roomId && synchronizedRoom.epoch != fence.epoch) return unavailable(command)
        if (fence.instanceId == instanceId) return dispatch(fence, socket, operation, command)
        return transport.forward(
            OwnerCommandRequest(
                roomId.toString(), fence.epoch, socket.playerId.toString(), socket.generation, operation, command,
                ownerInstanceId = fence.instanceId.toString()
            )
        ) ?: unavailable(command)
    }

    private suspend fun onPeerRequest(request: OwnerCommandRequest): OwnedRoomReply = try {
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
                SocketFence(playerId, request.generation), request.operation, command)
        }
    } catch (_: SQLException) {
        unavailable(request.command)
    }

    private suspend fun dispatch(
        fence: OwnerFence,
        socket: SocketFence,
        operation: OwnerOperation,
        command: ClientMessage?
    ): OwnedRoomReply = when (operation) {
        OwnerOperation.COMMAND -> {
            val incoming = checkNotNull(command)
            if (incoming.needsPilotAdmission() && !newIntentAllowed()) maintenance()
            else engine.handle(socket, incoming, fence)
        }
        OwnerOperation.RECONNECT -> engine.reconnect(socket, fence)
        OwnerOperation.DISCONNECT -> {
            engine.disconnect(socket, fence)
            OwnedRoomReply.NoRoom
        }
    }

    companion object {
        suspend fun open(
            config: RoomOwnershipPilotConfig,
            dataSource: DataSource,
            accountPlayer: suspend (UUID) -> StoredActivePlayer?,
            eventRewards: EventRewardSource = NoRewardEventSource,
            wakeups: Flow<UUID>? = null,
            newIntentAllowed: () -> Boolean = { true }
        ): RoomOwnershipPilot {
            verifyFixture(dataSource, config)
            val instanceId = UUID.randomUUID()
            val repository = PostgresRoomOwnershipRepository(dataSource)
            val economy = OwnedMatchEconomy(repository, PostgresPlayQuotaRepository(dataSource), PostgresMatchResultRepository(dataSource))
            val engine = OwnedRoomEngine(instanceId, repository, economy, accountPlayer, eventRewards)
            return RoomOwnershipPilot(
                config, instanceId, repository, engine,
                OwnerPeerTransport(config.nodeId, config.peerId, config.peerUri, config.peerSecret),
                newIntentAllowed, wakeups ?: postgresRoomEventWakeups(dataSource)
            )
        }

        private suspend fun verifyFixture(source: DataSource, config: RoomOwnershipPilotConfig) = withContext(Dispatchers.IO) {
            source.connection.use { connection ->
                connection.prepareStatement("SELECT fixture_id FROM room_ownership_pilot_fixture WHERE singleton").use { query ->
                    query.executeQuery().use { rows ->
                        require(rows.next() && rows.getObject(1, UUID::class.java) == config.fixtureId) {
                            "Room ownership fixture marker is absent or mismatched"
                        }
                    }
                }
                connection.prepareStatement("""
                    SELECT id FROM users
                    WHERE id = ANY (?) AND account_type = 'REGISTERED' AND status = 'ACTIVE' AND email_verified_at IS NOT NULL
                """.trimIndent()).use { query ->
                    val ids = connection.createArrayOf("uuid", config.allowedAccounts.toTypedArray())
                    try {
                        query.setArray(1, ids)
                        query.executeQuery().use { rows ->
                            val verified = buildSet { while (rows.next()) add(rows.getObject(1, UUID::class.java)) }
                            require(verified == config.allowedAccounts) { "Pilot accounts must be verified fixture accounts" }
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
private fun unavailable(command: ClientMessage? = null) = OwnedRoomReply.Retryable(ServerMessage.Error(
    "ROOM_RECOVERING", legacyFallback("Phòng đang đồng bộ. Vui lòng thử lại."),
    (command as? ClientMessage.SelectNumber)?.requestId
))
