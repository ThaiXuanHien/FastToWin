package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Local to the single public ingress. References include mutex waiters, even canceled ones. */
internal class AccountGameLane {
    private val registryLock = Any()
    private val accounts = mutableMapOf<UUID, Entry>()

    suspend fun <T> withAccount(accountId: UUID, block: suspend LaneContext.() -> T): T {
        val entry = synchronized(registryLock) {
            accounts.getOrPut(accountId) { Entry(accountId) }.also { it.references++ }
        }
        try {
            return entry.mutex.withLock { entry.context.block() }
        } finally {
            synchronized(registryLock) {
                check(entry.references > 0)
                entry.references--
                if (entry.references == 0 && !entry.context.registered && accounts[accountId] === entry) {
                    accounts.remove(accountId)
                }
            }
        }
    }

    /** Obtain only inside withAccount; identity mutation and dispatch share that exclusion. */
    sealed interface LaneContext {
        fun register(identity: Any, socket: SocketFence?)
        fun isCurrent(identity: Any): Boolean
        fun socket(identity: Any): SocketFence?
        fun retire(identity: Any): Boolean
        fun pending(): PendingOwnedAttempt?
        fun begin(attempt: PendingOwnedAttempt)
        fun resolve(attempt: PendingOwnedAttempt)
    }

    private class Entry(accountId: UUID) {
        val mutex = Mutex()
        val context = Context(accountId)
        var references = 0
    }

    private class Context(private val accountId: UUID) : LaneContext {
        private var identity: Any? = null
        private var boundSocket: SocketFence? = null
        private var pendingAttempt: PendingOwnedAttempt? = null
        val registered: Boolean get() = identity != null

        override fun register(identity: Any, socket: SocketFence?) {
            require(socket == null || socket.playerId == accountId && socket.generation > 0)
            this.identity = identity
            boundSocket = socket
            pendingAttempt = null
        }

        override fun isCurrent(identity: Any): Boolean = this.identity === identity

        override fun socket(identity: Any): SocketFence? = boundSocket.takeIf { isCurrent(identity) }

        override fun retire(identity: Any): Boolean {
            if (!isCurrent(identity)) return false
            this.identity = null
            boundSocket = null
            pendingAttempt = null
            return true
        }

        override fun pending(): PendingOwnedAttempt? = pendingAttempt

        override fun begin(attempt: PendingOwnedAttempt) {
            check(boundSocket == attempt.socket && pendingAttempt == null)
            pendingAttempt = attempt
        }

        override fun resolve(attempt: PendingOwnedAttempt) {
            check(pendingAttempt === attempt)
            pendingAttempt = null
        }
    }
}

/** Fresh membership read after a committed production session-generation barrier. */
internal data class OwnedLaneBinding(val socket: SocketFence, val ownedRoomId: UUID?)

internal sealed interface PendingOwnedAttempt {
    val socket: SocketFence

    class Create(val request: OwnedCreateRequest) : PendingOwnedAttempt {
        override val socket get() = request.socket
    }

    class Join(override val socket: SocketFence, val command: ClientMessage.JoinRoom) : PendingOwnedAttempt
}
