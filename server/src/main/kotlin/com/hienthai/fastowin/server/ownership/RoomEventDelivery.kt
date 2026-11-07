package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.legacyFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

internal data class RoomDeliveryPosition(val roomId: UUID, val epoch: Long)

@Serializable
internal data class RoomQueuePosition(
    val roomId: String,
    val playerId: String,
    val generation: Long,
    val revision: Long,
    val nextEventIndex: Int
)

internal class RoomEventDelivery(
    private val rooms: RoomOwnershipRepository,
    private val wakeups: Flow<UUID> = emptyFlow(),
    private val reconciliationIntervalMillis: Long = 1_000,
    private val reattach: suspend (SocketFence) -> OwnedRoomReply = { OwnedRoomReply.NoRoom },
    private val metrics: RoomOwnershipMetrics? = null
) {
    private val subscriptions = ConcurrentHashMap<SocketFence, Subscription>()

    init {
        require(reconciliationIntervalMillis in 1..1_000)
    }

    suspend fun register(
        socket: SocketFence,
        initial: OwnedRoomReply.Applied?,
        onDrain: suspend () -> Unit = {},
        send: suspend (ServerMessage) -> Unit
    ) {
        val cursor = initial?.let { Cursor(it.roomId, it.epoch, it.revision, it.publicMessage is ServerMessage.RoomClosed) }
        subscriptions[socket] = Subscription(socket, send, onDrain, cursor)
    }

    suspend fun unregister(socket: SocketFence) {
        subscriptions.remove(socket)
    }

    suspend fun closeForDrain(): Unit = coroutineScope {
        subscriptions.values.toList().forEach { subscription ->
            if (subscriptions.remove(subscription.socket, subscription)) launch {
                try {
                    subscription.onDrain()
                } catch (cancelled: CancellationException) {
                    currentCoroutineContext().ensureActive()
                } catch (_: Exception) {
                    // The socket may already be closed.
                }
            }
        }
    }

    fun queuedPositions(): List<RoomQueuePosition> = subscriptions.values.mapNotNull { it.queuePosition }

    suspend fun onCommand(
        socket: SocketFence,
        requestId: String? = null,
        execute: suspend (RoomDeliveryPosition?) -> OwnedRoomReply
    ) {
        val subscription = subscriptions[socket] ?: return
        subscription.mutex.withLock {
            if (subscriptions[socket] !== subscription) return
            if ((subscription.cursor == null || subscription.cursor?.terminated == true) &&
                subscription.discoverAfterUncertainCommand && !discover(subscription)
            ) {
                emit(subscription, recovering(requestId))
                return
            }
            val cursor = subscription.cursor
            if (cursor != null && cursor.pendingSnapshot && !cursor.terminated) {
                try {
                    drain(subscription, cursor)
                } catch (_: SQLException) {
                    // Retain the gate until the new snapshot can reach the socket.
                }
                if (cursor.pendingSnapshot && !cursor.terminated) {
                    emit(subscription, recovering(requestId))
                    return
                }
            }
            if (subscriptions[socket] !== subscription) return
            val position = cursor?.takeUnless { it.terminated }?.let { RoomDeliveryPosition(it.roomId, it.epoch) }
            when (val reply = execute(position)) {
                is OwnedRoomReply.Rejected -> emit(subscription, reply.publicMessage)
                is OwnedRoomReply.Retryable -> {
                    // A forwarded create/join can commit even if its response never arrives.
                    if (cursor == null || cursor.terminated) subscription.discoverAfterUncertainCommand = true
                    else cursor.pendingSnapshot = true
                    emit(subscription, reply.publicMessage)
                }
                is OwnedRoomReply.Applied -> try {
                    if (reply.replayed) deliverReplay(subscription, reply)
                    else deliverCommitted(subscription, reply)
                } catch (_: SQLException) {
                    // The next reconciliation can recover a committed reply from the outbox.
                    if (reply.replayed) {
                        emit(subscription, recovering((reply.publicMessage as? ServerMessage.Error)?.requestId))
                    }
                }
                OwnedRoomReply.NoRoom -> Unit
            }
        }
    }

    suspend fun run(): Unit = coroutineScope {
        launch { wakeups.collect { roomId -> reconcile(roomId) } }
        while (isActive) {
            delay(reconciliationIntervalMillis)
            launch { reconcile() }
        }
    }

    private suspend fun reconcile(roomId: UUID? = null): Unit = coroutineScope {
        subscriptions.values.forEach { subscription ->
            launch {
                if (!subscription.mutex.tryLock()) return@launch
                try {
                    val cursor = subscription.cursor
                    if ((cursor == null || cursor.terminated) && subscription.discoverAfterUncertainCommand) discover(subscription)
                    else if (cursor != null && !cursor.terminated && (roomId == null || roomId == cursor.roomId)) {
                        drain(subscription, cursor)
                    }
                } catch (_: SQLException) {
                    // Leave the cursor in place and try again on the next wake-up or reconciliation.
                } finally {
                    subscription.mutex.unlock()
                }
            }
        }
    }

    private suspend fun deliverCommitted(subscription: Subscription, reply: OwnedRoomReply.Applied) {
        val previous = subscription.cursor
        val cursor = if (previous?.roomId == reply.roomId) previous else {
            Cursor(reply.roomId, reply.epoch, (reply.revision - 1).coerceAtLeast(0)).also {
                subscription.cursor = it
                subscription.discoverAfterUncertainCommand = false
            }
        }
        if (cursor.epoch != reply.epoch) cursor.pendingSnapshot = true
        if (!cursor.terminated && (cursor.epoch != reply.epoch || cursor.revision < reply.revision)) {
            drain(subscription, cursor)
        }
    }

    private suspend fun deliverReplay(subscription: Subscription, reply: OwnedRoomReply.Applied) {
        val cursor = subscription.cursor?.takeIf { it.roomId == reply.roomId }
        if (cursor?.terminated == true) return
        if (cursor != null && cursor.epoch != reply.epoch) cursor.pendingSnapshot = true
        if (cursor != null && (cursor.epoch != reply.epoch || cursor.revision < reply.revision)) {
            drain(subscription, cursor)
            if (cursor.terminated || cursor.pendingSnapshot || cursor.epoch != reply.epoch) return
        }
        val state = rooms.read(reply.roomId)
        if (state == null || state.fence.epoch != reply.epoch || !state.includes(subscription.socket) ||
            !isCurrent(subscription)
        ) return
        if (cursor == null) {
            subscription.cursor = Cursor(reply.roomId, reply.epoch, reply.revision)
            subscription.discoverAfterUncertainCommand = false
        }
        emit(subscription, reply.publicMessage)
    }

    private suspend fun discover(subscription: Subscription): Boolean {
        val socket = subscription.socket
        val roomId = rooms.lookupRoom(socket.playerId) ?: return true
        if (!isCurrent(subscription)) return false
        val reply = reattach(socket) as? OwnedRoomReply.Applied ?: return false
        if (reply.roomId != roomId) return false
        val cursor = Cursor(reply.roomId, reply.epoch, (reply.revision - 1).coerceAtLeast(0))
        subscription.cursor = cursor
        subscription.discoverAfterUncertainCommand = false
        if (reply.publicMessage is ServerMessage.RoomClosed) drain(subscription, cursor)
        else {
            cursor.pendingSnapshot = true
            synchronize(subscription, cursor, rooms.read(cursor.roomId))
        }
        return cursor.terminated || !cursor.pendingSnapshot
    }

    private suspend fun drain(subscription: Subscription, cursor: Cursor) {
        val batch = rooms.readEvents(cursor.roomId, cursor.revision, EVENT_BATCH_SIZE)
        // Read the authoritative head after the outbox so authorization accounts for any later removal.
        val state = rooms.read(cursor.roomId)
        if (!isCurrent(subscription)) return
        if (cursor.pendingSnapshot || state == null || state.fence.epoch != cursor.epoch ||
            batch !is RoomEventBatch.Contiguous || batch.revisions.any { it.ownerEpoch != cursor.epoch }
        ) {
            synchronize(subscription, cursor, state)
            return
        }

        val isMember = state.includes(subscription.socket)
        val playerId = subscription.socket.playerId.toString()
        for (revision in batch.revisions) {
            for (index in cursor.nextEventIndex until revision.events.size) {
                val event = revision.events[index]
                if (playerId in event.recipientIds) {
                    val message = event.message
                    if (message is ServerMessage.RoomClosed) {
                        if (!isMember && message.roomId == cursor.roomId.toString()) {
                            terminate(subscription, cursor, message)
                            return
                        }
                    } else if (isMember && !emit(subscription, message)) return
                }
                cursor.nextEventIndex = index + 1
                subscription.publishCursor()
            }
            cursor.revision = revision.revision
            cursor.nextEventIndex = 0
            subscription.publishCursor()
        }
        if (!isMember && cursor.revision >= state.revision) terminate(subscription, cursor)
    }

    private suspend fun synchronize(subscription: Subscription, cursor: Cursor, state: OwnedRoomState?) {
        cursor.pendingSnapshot = true
        var current = state
        val socket = subscription.socket
        if (current != null && current.includes(socket) && current.fence.epoch != cursor.epoch) {
            val epoch = current.fence.epoch
            if (cursor.reattachedEpoch != epoch) {
                when (val reply = reattach(socket)) {
                    is OwnedRoomReply.Retryable, is OwnedRoomReply.Rejected -> return
                    is OwnedRoomReply.Applied -> if (reply.roomId != cursor.roomId || reply.epoch != epoch) return
                    OwnedRoomReply.NoRoom -> Unit
                }
                cursor.reattachedEpoch = epoch
            }
            current = rooms.read(cursor.roomId)
            if (current != null && current.fence.epoch != epoch) return
        }
        if (!isCurrent(subscription)) return
        if (current == null || !current.includes(socket)) terminate(subscription, cursor)
        else if (emit(subscription, current.snapshot.publicState())) {
            cursor.epoch = current.fence.epoch
            cursor.revision = current.revision
            cursor.nextEventIndex = 0
            cursor.pendingSnapshot = false
            subscription.publishCursor()
            metrics?.resynced()
        }
    }

    private suspend fun isCurrent(subscription: Subscription): Boolean {
        if (subscriptions[subscription.socket] !== subscription) return false
        if (rooms.isCurrentSocket(subscription.socket)) return subscriptions[subscription.socket] === subscription
        subscriptions.remove(subscription.socket, subscription)
        return false
    }

    private suspend fun terminate(
        subscription: Subscription,
        cursor: Cursor,
        message: ServerMessage.RoomClosed = ServerMessage.RoomClosed(cursor.roomId.toString(),
            legacyFallback("Phòng không còn khả dụng."), TextKey.ServerRoomNotFound.name, code = "ROOM_NOT_FOUND")
    ) {
        if (emit(subscription, message)) {
            cursor.terminated = true
            subscription.publishCursor()
        }
    }

    private suspend fun emit(subscription: Subscription, message: ServerMessage): Boolean {
        return try {
            if (!isCurrent(subscription)) return false
            subscription.send(message)
            true
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            subscriptions.remove(subscription.socket, subscription)
            false
        } catch (failure: Exception) {
            if (!failure.isPilotStorageUnavailable()) subscriptions.remove(subscription.socket, subscription)
            false
        }
    }

    private fun OwnedRoomState.includes(socket: SocketFence) =
        !closed && socket.playerId.toString() in snapshot.activePlayers()

    private fun recovering(requestId: String? = null) = ServerMessage.Error(
        "ROOM_RECOVERING", legacyFallback("Phòng đang đồng bộ. Vui lòng thử lại."), requestId
    )

    private class Cursor(val roomId: UUID, var epoch: Long, var revision: Long, var terminated: Boolean = false) {
        // A batch can stop after sending only part of the next revision.
        var nextEventIndex = 0
        var pendingSnapshot = false
        var reattachedEpoch: Long? = null
    }

    private class Subscription(
        val socket: SocketFence,
        val send: suspend (ServerMessage) -> Unit,
        val onDrain: suspend () -> Unit,
        cursor: Cursor?
    ) {
        val mutex = Mutex()
        var discoverAfterUncertainCommand = false
        var cursor: Cursor? = cursor
            set(value) {
                field = value
                publishCursor()
            }
        @Volatile var queuePosition: RoomQueuePosition? = position(cursor)
            private set

        fun publishCursor() { queuePosition = position(cursor) }

        private fun position(cursor: Cursor?): RoomQueuePosition? = cursor?.takeUnless { it.terminated }?.let {
            RoomQueuePosition(it.roomId.toString(), socket.playerId.toString(), socket.generation, it.revision, it.nextEventIndex)
        }
    }

    private companion object {
        const val EVENT_BATCH_SIZE = 32
    }
}
