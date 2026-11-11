package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AccountGameLaneTest {
    @Test fun `connection replacement and commands share account exclusion while other accounts progress`() = runBlocking {
        val lanes = AccountGameLane()
        val account = UUID.randomUUID()
        val other = UUID.randomUUID()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val replacementEntered = CompletableDeferred<Unit>()
        val old = Any()
        val replacement = Any()
        lanes.withAccount(account) { register(old, SocketFence(account, 1)) }
        val command = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) {
                assertTrue(isCurrent(old))
                entered.complete(Unit)
                release.await()
                assertTrue(isCurrent(old), "Replacement must not mutate identity during accepted work")
            }
        }
        entered.await()
        val connect = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) {
                register(replacement, SocketFence(account, 2))
                replacementEntered.complete(Unit)
            }
        }
        assertFalse(replacementEntered.isCompleted)
        withTimeout(1_000) { lanes.withAccount(other) { register(Any(), SocketFence(other, 1)) } }
        release.complete(Unit)
        command.join(); connect.join()
        lanes.withAccount(account) {
            assertFalse(isCurrent(old))
            assertTrue(isCurrent(replacement))
            assertEquals(SocketFence(account, 2), socket(replacement))
            assertFalse(retire(old), "An old finally must not retire its replacement")
            assertTrue(isCurrent(replacement))
        }
    }

    @Test fun `queued obsolete frame is rejected after the preceding replacement`() = runBlocking {
        val lanes = AccountGameLane()
        val account = UUID.randomUUID()
        val old = Any()
        val current = Any()
        lanes.withAccount(account) { register(old, SocketFence(account, 1)) }
        val replacementReady = CompletableDeferred<Unit>()
        val finishReplacement = CompletableDeferred<Unit>()
        val replacement = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) {
                register(current, SocketFence(account, 2))
                replacementReady.complete(Unit)
                finishReplacement.await()
            }
        }
        replacementReady.await()
        var obsoleteDispatched = false
        val queuedFrame = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) { if (isCurrent(old)) obsoleteDispatched = true }
        }
        finishReplacement.complete(Unit)
        replacement.join(); queuedFrame.join()
        assertFalse(obsoleteDispatched)
        lanes.withAccount(account) { assertTrue(isCurrent(current)) }
    }

    @Test fun `live registration pins state across idle references and canceled waiter`() = runBlocking {
        val lanes = AccountGameLane()
        val account = UUID.randomUUID()
        val identity = Any()
        val original = lanes.withAccount(account) { register(identity, SocketFence(account, 7)); this }
        assertSame(original, lanes.withAccount(account) { this })
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) { entered.complete(Unit); release.await() }
        }
        entered.await()
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) { error("Canceled waiter must never enter") }
        }
        waiter.cancelAndJoin()
        release.complete(Unit); holder.join()
        lanes.withAccount(account) {
            assertSame(original, this)
            assertTrue(isCurrent(identity))
            assertEquals(SocketFence(account, 7), socket(identity))
        }
    }

    @Test fun `canceled holder and waiter leave no unregistered account state behind`() = runBlocking {
        val lanes = AccountGameLane()
        val account = UUID.randomUUID()
        val entered = CompletableDeferred<Any>()
        val never = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) { entered.complete(this); never.await() }
        }
        val original = entered.await()
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) { error("Canceled waiter must never enter") }
        }
        waiter.cancelAndJoin(); holder.cancelAndJoin()
        lanes.withAccount(account) {
            assertNotSame(original, this, "Zero references and no live registration must permit eviction")
            assertFalse(isCurrent(Any()), "An evicted context must not confer dispatch authority")
        }
    }

    @Test fun `final release racing acquisition never creates two account holders`() = runBlocking {
        val lanes = AccountGameLane()
        val account = UUID.randomUUID()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val start = CompletableDeferred<Unit>()
        val calls = (1..8).map {
            async(Dispatchers.Default) {
                start.await()
                repeat(80) {
                    lanes.withAccount(account) {
                        val count = active.incrementAndGet()
                        maximum.accumulateAndGet(count, ::maxOf)
                        kotlinx.coroutines.yield()
                        active.decrementAndGet()
                    }
                }
            }
        }
        start.complete(Unit)
        withTimeout(5_000) { calls.forEach { it.await() } }
        assertEquals(1, maximum.get())
        assertEquals(0, active.get())
    }

    @Test fun `retirement evicts only after the current waiter finishes and next connection must bind anew`() = runBlocking {
        val lanes = AccountGameLane()
        val account = UUID.randomUUID()
        val identity = Any()
        val old = lanes.withAccount(account) { register(identity, SocketFence(account, 1)); this }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val retirement = launch(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) { assertTrue(retire(identity)); entered.complete(Unit); release.await() }
        }
        entered.await()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            lanes.withAccount(account) {
                assertSame(old, this, "A waiting reference must keep using the old exclusion entry")
                assertFalse(isCurrent(identity))
                assertEquals(null, socket(identity))
                this
            }
        }
        release.complete(Unit)
        retirement.join(); assertSame(old, waiter.await())
        lanes.withAccount(account) {
            assertNotSame(old, this)
            assertFalse(isCurrent(identity))
            assertEquals(null, socket(identity))
        }
    }
}
