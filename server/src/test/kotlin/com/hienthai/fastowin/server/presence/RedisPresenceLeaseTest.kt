package com.hienthai.fastowin.server.presence

import com.hienthai.fastowin.protocol.FriendPresence
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class RedisPresenceLeaseTest {
    @Test
    fun `default lease expires after writer disappears and another backend stays online`() = runBlocking {
        assumeTrue(System.getenv("FASTTOWIN_TEST_PRESENCE_FULL_TTL") == "true", "Opt-in Redis timing fixture required")
        val config = PresenceConfiguration(
            checkNotNull(System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL")),
            "t" + UUID.randomUUID().toString().replace("-", "")
        )
        val player = UUID.randomUUID().toString()
        val survivor = UUID.randomUUID()
        RedisPresenceStore(config).use { second ->
            assertTrue(second.renew(player, survivor, FriendPresence.ONLINE))
            RedisPresenceStore(config).use { first ->
                assertTrue(first.renew(player, UUID.randomUUID(), FriendPresence.PLAYING))
                assertEquals(FriendPresence.PLAYING, second.read(listOf(player))?.get(player))
                // Close the writer without revoking the lease, like a backend that cannot run cleanup.
            }
            val started = TimeSource.Monotonic.markNow()
            val heartbeat = launch {
                while (isActive) {
                    delay(20_000)
                    assertTrue(second.renew(player, survivor, FriendPresence.ONLINE))
                }
            }
            try {
                delay(45_000)
                assertEquals(FriendPresence.PLAYING, second.read(listOf(player))?.get(player))
                withTimeout(20_000) {
                    while (second.read(listOf(player))?.get(player) != FriendPresence.ONLINE) delay(100)
                }
                val elapsed = started.elapsedNow().inWholeMilliseconds
                assertTrue(elapsed in 58_000..65_000)
                println("PRESENCE_LEASE expiredAfterMs=$elapsed survivor=ONLINE")
            } finally {
                heartbeat.cancelAndJoin()
                assertTrue(second.revoke(player, survivor))
            }
        }
    }
}
