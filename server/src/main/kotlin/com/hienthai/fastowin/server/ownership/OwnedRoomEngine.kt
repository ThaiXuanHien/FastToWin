package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.protocolTextKeyForCode
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.MatchSelectionEvent
import com.hienthai.fastowin.server.PasswordHash
import com.hienthai.fastowin.server.SelectionResult
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.StoredActiveRoom
import com.hienthai.fastowin.server.admin.AdminException
import com.hienthai.fastowin.server.events.EventRewardSource
import com.hienthai.fastowin.server.events.NoRewardEventSource
import com.hienthai.fastowin.server.legacyFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal sealed interface OwnedRoomReply {
    data class Applied(
        val publicMessage: ServerMessage,
        val roomId: UUID,
        val epoch: Long,
        val revision: Long,
        val replayed: Boolean = false
    ) : OwnedRoomReply

    data class Rejected(val publicMessage: ServerMessage.Error) : OwnedRoomReply
    data class Retryable(val publicMessage: ServerMessage.Error) : OwnedRoomReply
    data object NoRoom : OwnedRoomReply
}

internal class OwnedRoomEngine(
    private val instanceId: UUID,
    private val rooms: RoomOwnershipRepository,
    private val economy: OwnedMatchEconomy,
    private val accountPlayer: suspend (UUID) -> StoredActivePlayer?,
    private val eventRewards: EventRewardSource = NoRewardEventSource
) {
    private val leases = ConcurrentHashMap<UUID, OwnerFence>()

    suspend fun handle(socket: SocketFence, message: ClientMessage, expectedOwner: OwnerFence? = null): OwnedRoomReply = try {
        when (message) {
            is ClientMessage.CreateRoom -> if (expectedOwner == null) create(socket, message) else retry()
            is ClientMessage.JoinRoom -> command(message.roomId, expectedOwner = expectedOwner) { state -> join(socket, state, message) }
            is ClientMessage.SetReady -> command(message.roomId, expectedOwner = expectedOwner) { state -> ready(socket, state, message.ready) }
            is ClientMessage.SelectNumber -> command(message.roomId, message.requestId, expectedOwner) { state ->
                reply(mutate(state, socket, checkExpiry = false) { locked -> select(socket, locked, message) }, message.requestId)
            }
            is ClientMessage.LeaveRoom -> command(message.roomId, expectedOwner = expectedOwner) { state ->
                reply(mutate(state, socket) { locked -> leave(socket, locked) })
            }
            else -> reject("UNSUPPORTED_PILOT_COMMAND")
        }
    } catch (_: SQLException) {
        retry((message as? ClientMessage.SelectNumber)?.requestId)
    }

    suspend fun reconnect(socket: SocketFence, expectedOwner: OwnerFence? = null): OwnedRoomReply = try {
        val id = rooms.lookupRoom(socket.playerId)
        if (id == null) OwnedRoomReply.NoRoom else command(id.toString(), expectedOwner = expectedOwner) { state ->
            reply(mutate(state, socket, checkExpiry = false) { locked ->
                val room = locked.snapshot
                val actor = socket.playerId.toString()
                if (actor !in room.activePlayers()) return@mutate RoomMutation.Reject("NOT_IN_ROOM")
                val expiry = expire(locked, actor)
                if (expiry != null && (expiry.close || actor !in expiry.snapshot.activePlayers())) return@mutate expiry
                val base = locked.copy(
                    snapshot = expiry?.snapshot ?: room,
                    recoveryDeadline = locked.recoveryDeadline.takeUnless { expiry?.clearRecoveryDeadline == true }
                )
                val attached = base.attach(socket)
                val fullyRecovered = base.recoveryDeadline != null &&
                    attached.pilotSocketGenerations.keys.containsAll(attached.activePlayers())
                val reply = attached.publicState()
                if (expiry == null && attached == room && !fullyRecovered) RoomMutation.Replay(reply)
                else RoomMutation.Change(
                    attached,
                    events = expiry?.events?.takeIf { it.isNotEmpty() } ?: listOf(event(reply, setOf(actor))),
                    clearRecoveryDeadline = fullyRecovered || expiry?.clearRecoveryDeadline == true,
                    reply = reply
                )
            })
        }
    } catch (_: SQLException) {
        retry()
    }

    suspend fun disconnect(socket: SocketFence, expectedOwner: OwnerFence? = null) {
        repeat(DISCONNECT_ATTEMPTS) {
            try {
                val id = rooms.lookupRoom(socket.playerId) ?: return
                val state = localRoom(id)?.takeIf { expectedOwner == null || it.fence == expectedOwner } ?: return
                val result = rooms.withFencedRoom(state.fence, state.revision, socket) { _, locked ->
                    val room = locked.snapshot
                    val actor = socket.playerId.toString()
                    if (actor !in room.activePlayers()) {
                        return@withFencedRoom RoomMutation.Unchanged
                    }
                    val connections = locked.normalizeConnections()
                    if (actor !in connections.pilotSocketGenerations && actor in connections.pilotDisconnectDeadlines) {
                        return@withFencedRoom if (connections == room) RoomMutation.Unchanged
                        else RoomMutation.Change(connections)
                    }
                    val deadline = locked.observedAt.toEpochMilli() + RECONNECT_MILLIS
                    RoomMutation.Change(connections.copy(
                        pilotSocketGenerations = connections.pilotSocketGenerations - actor,
                        pilotDisconnectDeadlines = connections.pilotDisconnectDeadlines + (actor to deadline)
                    ))
                }
                remember(result)
                if (result != FencedResult.Stale && result != FencedResult.TemporaryFailure) return
            } catch (_: SQLException) {
                // A concurrent command may have committed. Re-read before trying this idempotent change again.
            }
        }
    }

    suspend fun renewalTick() {
        for (fence in leases.values.toList()) {
            try {
                if (rooms.renew(fence)) rooms.read(fence.roomId)?.let { recover(it) }
                else leases.remove(fence.roomId, fence)
            } catch (_: SQLException) {
                // A busy room does not delay recovery of other rooms until the next tick.
            }
        }
        try {
            for (id in rooms.expiredRooms(EXPIRED_ROOM_BATCH)) {
                try {
                    rooms.claim(id, instanceId)?.let { remember(it); recover(it) }
                } catch (_: SQLException) {
                    // Another worker may be changing this room. The next tick can try again.
                }
            }
            rooms.purgeExpiredClosedRooms(EXPIRED_ROOM_BATCH)
        } catch (_: SQLException) {
            // The next tick retries the scan after PostgreSQL returns.
        }
    }

    private suspend fun create(socket: SocketFence, message: ClientMessage.CreateRoom): OwnedRoomReply {
        if (message.gameMode != ProtocolGameMode.ORDER || message.matchType != MatchType.CASUAL ||
            message.password.isBlank()
        ) return reject("INVALID_PILOT_ROOM")
        val name = message.roomName.trim().take(MAX_ROOM_NAME_LENGTH)
        if (name.isEmpty()) return reject("INVALID_ROOM_NAME")
        val existing = reconnect(socket)
        if (existing != OwnedRoomReply.NoRoom) return existing
        val player = player(socket) ?: return reject("ACCOUNT_REQUIRED")
        val password = withContext(Dispatchers.Default) { PasswordHash.create(message.password) }
        val room = StoredActiveRoom(
            UUID.randomUUID().toString(), roomName = name, host = player, gameMode = message.gameMode,
            passwordSalt = password.salt, passwordHash = password.value, phase = RoomPhase.WAITING,
            pilotConnectionEpoch = 1, pilotSocketGenerations = mapOf(player.playerId to socket.generation)
        )
        val created = ServerMessage.RoomCreated(room.classicSnapshot())
        val result = rooms.createRoom(instanceId, socket, room, listOf(event(created, room.activePlayers())))
        return if (result is FencedResult.Rejected && result.reason == "ALREADY_IN_ROOM") reconnect(socket)
        else reply(result, defaultMessage = created)
    }

    private suspend fun join(
        socket: SocketFence,
        state: OwnedRoomState,
        message: ClientMessage.JoinRoom
    ): OwnedRoomReply {
        if (message.asSpectator) return reject("INVALID_PILOT_ROOM")
        val actor = socket.playerId.toString()
        if (actor in state.snapshot.activePlayers()) return reconnect(socket, state.fence)
        val player = player(socket) ?: return reject("ACCOUNT_REQUIRED")
        val room = state.snapshot
        val password = PasswordHash(checkNotNull(room.passwordSalt), checkNotNull(room.passwordHash))
        val validPassword = withContext(Dispatchers.Default) { password.matches(message.password) }
        return reply(mutate(state, socket) { locked ->
            if (!validPassword) return@mutate RoomMutation.Reject("WRONG_PASSWORD")
            if (locked.snapshot.phase != RoomPhase.WAITING || locked.snapshot.guest != null) {
                return@mutate RoomMutation.Reject("ROOM_FULL")
            }
            val next = locked.attach(socket).copy(
                guest = player, readyPlayerIds = emptySet(), sequence = locked.snapshot.sequence + 1,
                scores = locked.snapshot.scores + (actor to 0)
            )
            change(next, ServerMessage.RoomUpdated(next.classicSnapshot()))
        })
    }

    private suspend fun ready(socket: SocketFence, state: OwnedRoomState, isReady: Boolean): OwnedRoomReply {
        val room = state.snapshot
        val actor = socket.playerId.toString()
        val members = room.activePlayers()
        val attached = state.attachedGenerations()
        if (actor in members && room.phase == RoomPhase.WAITING && isReady &&
            members.size == 2 && (members - actor).all { it in room.readyPlayerIds } &&
            attached.keys.containsAll(members) && attached[actor] == socket.generation
        ) {
            val startAt = state.observedAt.toEpochMilli()
            // Loading the catalog and applying the fenced transaction use separate connections.
            val candidate = try {
                eventRewards.withStartCatalog { catalog ->
                    room.startClassic(startAt, catalog.snapshot(startAt, room.matchType))
                }
            } catch (error: AdminException) {
                if (error.status != 503 || error.code != "STORAGE_UNAVAILABLE") throw error
                return retry()
            }
            val started = ServerMessage.GameStarted(candidate.classicSnapshot())
            return reply(
                economy.start(state.fence, state.revision, socket, candidate, listOf(event(started, members)),
                    requireAttachedPlayers = true),
                defaultMessage = started
            )
        }
        return reply(mutate(state, socket) { locked ->
            val current = locked.snapshot
            if (actor !in current.activePlayers()) return@mutate RoomMutation.Reject("NOT_IN_ROOM")
            if (locked.attachedGenerations()[actor] != socket.generation) return@mutate RoomMutation.Reject("ROOM_RECOVERING")
            if (current.phase == RoomPhase.PLAYING && isReady) return@mutate RoomMutation.Replay(current.publicState())
            if (current.phase != RoomPhase.WAITING) return@mutate RoomMutation.Reject("ROOM_NOT_WAITING")
            val readyIds = if (isReady) current.readyPlayerIds + actor else current.readyPlayerIds - actor
            if (readyIds == current.readyPlayerIds) return@mutate RoomMutation.Replay(current.publicState())
            val next = current.copy(readyPlayerIds = readyIds, sequence = current.sequence + 1)
            change(next, ServerMessage.RoomUpdated(next.classicSnapshot()))
        })
    }

    private fun select(socket: SocketFence, state: OwnedRoomState, command: ClientMessage.SelectNumber): RoomMutation {
        val room = state.snapshot
        val actor = socket.playerId.toString()
        if (actor !in room.activePlayers()) return RoomMutation.Reject("NOT_IN_ROOM")
        if (command.requestId.isBlank() || command.requestId.length > MAX_REQUEST_ID_LENGTH) {
            return RoomMutation.Reject("INVALID_REQUEST_ID")
        }
        val attached = state.attachedGenerations()
        if (attached[actor] != socket.generation) return RoomMutation.Reject("ROOM_RECOVERING")
        val requestKey = "$actor:${command.requestId}"
        room.processedRequests[requestKey]?.let { response ->
            val previous = room.selectionEvents.first { it.playerId == actor && it.requestId == command.requestId }
            return if (previous.number == command.number) RoomMutation.Replay(response)
            else RoomMutation.Reject("REQUEST_ID_REUSED")
        }
        expire(state, actor)?.takeIf { it.reply != null }?.let { return it }
        if (room.phase != RoomPhase.PLAYING) return RoomMutation.Reject("GAME_NOT_PLAYING")
        if (state.recoveryDeadline != null && !attached.keys.containsAll(room.activePlayers())) {
            return RoomMutation.Reject("ROOM_RECOVERING")
        }
        if (room.processedRequests.size >= MAX_REQUESTS_PER_MATCH) return RoomMutation.Reject("TOO_MANY_REQUESTS")
        val accepted = command.number == room.currentTarget
        val event = MatchSelectionEvent(
            actor, command.requestId, command.number, room.currentTarget,
            if (accepted) SelectionResult.ACCEPTED else SelectionResult.REJECTED,
            state.observedAt.toEpochMilli(), room.selectionEvents.size + 1
        )
        val selected = room.recordClassicSelection(event)
        val next = if (selected.phase == RoomPhase.FINISHED) {
            selected.withResultsGrace(event.occurredAtMillis, attached.keys)
        } else selected
        val update = ServerMessage.GameStateUpdated(next.classicSnapshot(), command.number, actor, accepted)
        val response = if (accepted) update else errorMessage("WRONG_NUMBER", command.requestId)
        val events = listOf(event(update, room.activePlayers())) +
            if (accepted) emptyList() else listOf(event(response, setOf(actor)))
        return RoomMutation.Change(
            next.copy(processedRequests = room.processedRequests + (requestKey to response)),
            events, reply = response
        )
    }

    private fun leave(socket: SocketFence, state: OwnedRoomState): RoomMutation {
        val room = state.snapshot
        val actor = socket.playerId.toString()
        if (actor !in room.activePlayers()) return RoomMutation.Reject("NOT_IN_ROOM")
        if (state.attachedGenerations()[actor] != socket.generation) return RoomMutation.Reject("ROOM_RECOVERING")
        val next = room.copy(sequence = room.sequence + 1)
        return when (room.phase) {
            RoomPhase.PLAYING -> {
                val now = state.observedAt.toEpochMilli()
                val finished = next.withResultsGrace(now, state.attachedGenerations().keys).finishClassic(now,
                    forcedWinner = (room.activePlayers() - actor).single(), intentionalLeaver = actor)
                change(finished, finished.publicState(), clearRecovery = true)
            }
            RoomPhase.FINISHED -> {
                val departed = next.departResults(setOf(actor))
                val notice = ServerMessage.RoomClosed(room.roomId, legacyFallback("Bạn đã rời màn kết quả."),
                    TextKey.ResultsLeftNotice.name, code = "RESULTS_LEFT")
                change(departed, notice, setOf(actor),
                    close = departed.activePlayers().isEmpty() && departed.pendingCompletedMatch == null)
            }
            RoomPhase.WAITING -> {
                val notice = if (actor == room.host.playerId) {
                    ServerMessage.RoomClosed(room.roomId, legacyFallback("Chủ phòng đã rời phòng."),
                        TextKey.RoomHostLeftNotice.name, code = "ROOM_HOST_LEFT")
                } else {
                    val name = checkNotNull(room.guest).displayName
                    ServerMessage.RoomClosed(room.roomId, legacyFallback("$name đã rời phòng."), TextKey.RoomPlayerLeftNotice.name,
                        mapOf("player" to name), "ROOM_PLAYER_LEFT")
                }
                change(next, notice, close = true)
            }
        }
    }

    private suspend fun recover(state: OwnedRoomState) {
        if (state.closed || state.fence.instanceId != instanceId) return
        if (state.snapshot.pendingCompletedMatch != null) {
            val settlement = economy.settle(state.fence, state.revision)
            remember(settlement)
            if (settlement !is FencedResult.Committed) return
            recover(settlement.state)
            return
        }
        if (state.recoveryDeadline == null && state.snapshot.pilotDisconnectDeadlines.isEmpty() &&
            state.snapshot.activePlayers().isNotEmpty() &&
            state.attachedGenerations().keys.containsAll(state.snapshot.activePlayers())
        ) return
        val result = rooms.withFencedRoom(state.fence, state.revision, null) { _, locked ->
            expire(locked) ?: RoomMutation.Unchanged
        }
        remember(result)
        if (result is FencedResult.Committed && result.state.snapshot.pendingCompletedMatch != null) {
            remember(economy.settle(result.state.fence, result.state.revision))
        }
    }

    private fun expire(state: OwnedRoomState, requester: String? = null): RoomMutation.Change? {
        val room = state.normalizeConnections()
        val active = room.activePlayers()
        val now = state.observedAt.toEpochMilli()
        val recoveryEnd = state.recoveryDeadline?.toEpochMilli()
        if (recoveryEnd != null && now < recoveryEnd) return null
        val connected = room.pilotSocketGenerations.keys.intersect(active)
        val absent = active - connected
        val expired = absent.filterTo(mutableSetOf()) { id ->
            recoveryEnd != null || (room.pilotDisconnectDeadlines[id] ?: Long.MAX_VALUE) <= now
        }
        if (expired.isEmpty() && active.isNotEmpty()) {
            return RoomMutation.Change(room).takeUnless { room == state.snapshot }
        }
        if (room.phase == RoomPhase.PLAYING && connected.size == 1) {
            val missing = expired.single()
            val finished = room.copy(sequence = room.sequence + 1).withResultsGrace(now, connected)
                .finishClassic(now, forcedWinner = connected.single(), disconnected = missing)
            return change(finished, finished.publicState(), clearRecovery = true)
        }
        if (room.phase == RoomPhase.FINISHED) {
            val updated = room.copy(sequence = room.sequence + 1).departResults(expired)
            val notice = disconnectedNotice(room)
            return RoomMutation.Change(
                updated,
                events = if (expired.isEmpty()) emptyList() else listOf(event(notice, expired)),
                close = updated.activePlayers().isEmpty() && updated.pendingCompletedMatch == null,
                reply = notice.takeIf { requester in expired }
            )
        }
        return change(room.copy(sequence = room.sequence + 1), disconnectedNotice(room), close = true)
    }

    private suspend fun mutate(
        state: OwnedRoomState,
        socket: SocketFence,
        checkExpiry: Boolean = true,
        action: (OwnedRoomState) -> RoomMutation
    ): FencedResult = rooms.withFencedRoom(state.fence, state.revision, socket) { _, locked ->
        // A nonmember cannot trigger cleanup and receive a private room's result.
        val actor = socket.playerId.toString()
        val expiry = if (checkExpiry && actor in locked.snapshot.activePlayers()) expire(locked, actor) else null
        if (expiry?.reply != null) expiry else action(locked)
    }

    private suspend fun command(
        roomId: String,
        requestId: String? = null,
        expectedOwner: OwnerFence? = null,
        execute: suspend (OwnedRoomState) -> OwnedRoomReply
    ): OwnedRoomReply {
        val id = runCatching { UUID.fromString(roomId) }.getOrNull() ?: return reject("ROOM_NOT_FOUND", requestId)
        val state = rooms.read(id)?.takeUnless { it.closed } ?: return reject("ROOM_NOT_FOUND", requestId)
        if (state.fence.instanceId != instanceId || (expectedOwner != null && state.fence != expectedOwner)) return retry(requestId)
        remember(state)
        return execute(state)
    }

    private suspend fun localRoom(id: UUID): OwnedRoomState? =
        rooms.read(id)?.takeIf { !it.closed && it.fence.instanceId == instanceId }?.also(::remember)

    private suspend fun player(socket: SocketFence): StoredActivePlayer? =
        accountPlayer(socket.playerId)?.takeIf { it.isAccount && it.playerId == socket.playerId.toString() }
            ?.copy(accountResumeToken = null)

    private fun OwnedRoomState.normalizeConnections(): StoredActiveRoom {
        val attached = attachedGenerations()
        val sameEpoch = snapshot.pilotConnectionEpoch == fence.epoch
        val missing = snapshot.activePlayers() - attached.keys
        if (sameEpoch && attached == snapshot.pilotSocketGenerations &&
            (recoveryDeadline != null || snapshot.pilotDisconnectDeadlines.keys.containsAll(missing))
        ) return snapshot
        val deadline = observedAt.toEpochMilli() + RECONNECT_MILLIS
        val deadlines = if (recoveryDeadline != null) emptyMap() else
            missing.associateWith { snapshot.pilotDisconnectDeadlines[it] ?: deadline } + snapshot.pilotDisconnectDeadlines
        return snapshot.copy(pilotConnectionEpoch = fence.epoch, pilotSocketGenerations = attached,
            pilotDisconnectDeadlines = deadlines)
    }

    private fun OwnedRoomState.attach(socket: SocketFence): StoredActiveRoom {
        val room = normalizeConnections()
        val id = socket.playerId.toString()
        return room.copy(pilotSocketGenerations = room.pilotSocketGenerations + (id to socket.generation),
            pilotDisconnectDeadlines = room.pilotDisconnectDeadlines - id)
    }

    private fun StoredActiveRoom.departResults(players: Set<String>) = copy(
        departedPlayerIds = departedPlayerIds + players,
        pilotSocketGenerations = pilotSocketGenerations - players,
        pilotDisconnectDeadlines = pilotDisconnectDeadlines - players
    )

    private fun StoredActiveRoom.withResultsGrace(now: Long, connected: Set<String>) = copy(
        pilotSocketGenerations = pilotSocketGenerations.filterKeys { it in connected },
        pilotDisconnectDeadlines = (activePlayers() - connected).associateWith { now + RECONNECT_MILLIS }
    )

    private fun change(
        room: StoredActiveRoom,
        message: ServerMessage,
        recipients: Set<String> = room.activePlayers(),
        close: Boolean = false,
        clearRecovery: Boolean = false
    ) = RoomMutation.Change(room, listOf(event(message, recipients)), close, clearRecovery, message)

    private fun event(message: ServerMessage, recipients: Set<String>) = RoomOwnershipEvent(recipients, message)

    private fun disconnectedNotice(room: StoredActiveRoom) = ServerMessage.RoomClosed(
        room.roomId, legacyFallback("Người chơi đã mất kết nối quá lâu."),
        TextKey.RoomDisconnectedTooLongNotice.name, code = "ROOM_DISCONNECTED_TOO_LONG"
    )

    private fun reply(result: FencedResult, requestId: String? = null, defaultMessage: ServerMessage? = null): OwnedRoomReply {
        remember(result)
        return when (result) {
            is FencedResult.Committed -> result.state.applied(checkNotNull(result.reply ?: defaultMessage))
            is FencedResult.Replay -> result.state.applied(result.message, replayed = true)
            is FencedResult.Unchanged -> retry(requestId)
            is FencedResult.Rejected -> if (result.reason == "ROOM_RECOVERING") retry(requestId) else reject(result.reason, requestId)
            FencedResult.Stale, FencedResult.TemporaryFailure -> retry(requestId)
        }
    }

    private fun remember(result: FencedResult) {
        if (result is FencedResult.Committed) remember(result.state)
    }

    private fun remember(state: OwnedRoomState) {
        if (state.closed) leases.remove(state.fence.roomId, state.fence)
        else leases.compute(state.fence.roomId) { _, existing ->
            if (existing == null || existing.epoch <= state.fence.epoch) state.fence else existing
        }
    }

    private fun OwnedRoomState.applied(message: ServerMessage, replayed: Boolean = false) =
        OwnedRoomReply.Applied(message, fence.roomId, fence.epoch, revision, replayed)

    private fun retry(requestId: String? = null) = OwnedRoomReply.Retryable(errorMessage("ROOM_RECOVERING", requestId))

    private fun reject(code: String, requestId: String? = null) = OwnedRoomReply.Rejected(errorMessage(code, requestId))

    private fun errorMessage(code: String, requestId: String? = null): ServerMessage.Error {
        val message = when (code) {
            "ROOM_RECOVERING" -> legacyFallback("Phòng đang đồng bộ. Vui lòng thử lại.")
            "WRONG_PASSWORD" -> legacyFallback("Mật khẩu phòng không đúng.")
            "ROOM_NOT_FOUND" -> legacyFallback("Phòng không còn tồn tại.")
            "ROOM_FULL" -> legacyFallback("Phòng đã đầy hoặc đang chơi.")
            "NOT_IN_ROOM" -> legacyFallback("Bạn không ở trong phòng này.")
            "WRONG_NUMBER" -> legacyFallback("Chưa đúng số, thử lại nhé!")
            "REQUEST_ID_REUSED" -> legacyFallback("Mã lượt chọn đã được dùng cho số khác.")
            "PLAY_QUOTA_EXHAUSTED" -> legacyFallback("Bạn đã hết lượt chơi.")
            else -> legacyFallback("Không thể thực hiện thao tác trong phòng.")
        }
        return ServerMessage.Error(code, message, requestId, protocolTextKeyForCode(code)?.name)
    }

    private companion object {
        const val MAX_ROOM_NAME_LENGTH = 48
        const val MAX_REQUEST_ID_LENGTH = 64
        const val MAX_REQUESTS_PER_MATCH = 2_000
        const val RECONNECT_MILLIS = 30_000L
        const val DISCONNECT_ATTEMPTS = 3
        const val EXPIRED_ROOM_BATCH = 32
    }
}
