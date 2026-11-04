package com.hienthai.fastowin.server.presence

import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.server.RateLimitPolicy
import com.hienthai.fastowin.server.RateLimitUnavailableException
import com.hienthai.fastowin.server.ratelimit.RedisFaultProxy
import com.hienthai.fastowin.server.ratelimit.openRateLimitRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedisPresenceStoreTest {
    private fun namespace() = "t" + UUID.randomUUID().toString().replace("-", "")

    private fun fixture(): String {
        val url = System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL")
        assumeTrue(!url.isNullOrBlank(), "Isolated Redis presence fixture required")
        return requireNotNull(url)
    }

    @Test
    fun `two stores merge sockets and revoke only their own leases`() = runBlocking {
        val namespace = namespace()
        val player = UUID.randomUUID().toString()
        val firstSocket = UUID.randomUUID()
        val secondSocket = UUID.randomUUID()
        val config = PresenceConfiguration(fixture(), namespace)
        RedisPresenceStore(config).use { first ->
            RedisPresenceStore(config).use { second ->
                assertTrue(first.renew(player, firstSocket, FriendPresence.ONLINE))
                assertTrue(second.renew(player, secondSocket, FriendPresence.PLAYING))
                assertEquals(FriendPresence.PLAYING, first.read(listOf(player))?.get(player))
                assertTrue(first.revoke(player, firstSocket))
                assertEquals(FriendPresence.PLAYING, second.read(listOf(player))?.get(player))
                assertTrue(second.renew(player, secondSocket, FriendPresence.IN_ROOM))
                assertEquals(FriendPresence.IN_ROOM, first.read(listOf(player, player))?.get(player))
                assertTrue(second.revoke(player, secondSocket))
                assertEquals(FriendPresence.OFFLINE, first.read(listOf(player))?.get(player))
            }
        }
    }

    @Test
    fun `expired playing lease does not hide a live online lease`() = runBlocking {
        val namespace = namespace()
        val player = UUID.randomUUID().toString()
        val config = PresenceConfiguration(fixture(), namespace)
        RedisPresenceStore(config, leaseMillis = 300).use { short ->
            RedisPresenceStore(config).use { live ->
                assertTrue(short.renew(player, UUID.randomUUID(), FriendPresence.PLAYING))
                val socket = UUID.randomUUID()
                assertTrue(live.renew(player, socket, FriendPresence.ONLINE))
                delay(400)
                assertEquals(FriendPresence.ONLINE, short.read(listOf(player))?.get(player))
                assertTrue(live.revoke(player, socket))
            }
        }
    }

    @Test
    fun `redis timeout is unavailable and the next read recovers within its namespace`() = runBlocking {
        val url = fixture()
        val namespace = namespace()
        val player = UUID.randomUUID().toString()
        RedisPresenceStore(PresenceConfiguration(url, namespace)).use { direct ->
            RedisPresenceStore(PresenceConfiguration(url, namespace())).use { isolated ->
                val socket = UUID.randomUUID()
                assertTrue(direct.renew(player, socket, FriendPresence.ONLINE))
                assertEquals(FriendPresence.OFFLINE, isolated.read(listOf(player))?.get(player))
                RedisFaultProxy(url).use { proxy ->
                    RedisPresenceStore(PresenceConfiguration(proxy.proxyUrl, namespace)).use { throughProxy ->
                        proxy.mode = RedisFaultProxy.Mode.DROP
                        assertNull(throughProxy.read(listOf(player)))
                        proxy.release()
                        withTimeout(5_000) {
                            while (throughProxy.read(listOf(player))?.get(player) != FriendPresence.ONLINE) delay(50)
                        }
                    }
                }
                assertTrue(direct.revoke(player, socket))
            }
        }
    }

    @Test
    fun `presence credential cannot consume rate-limit keys`(): Unit = runBlocking {
        val url = fixture()
        openRateLimitRuntime(mapOf(
            "FASTTOWIN_RATE_LIMIT_STORE" to "redis",
            "FASTTOWIN_RATE_LIMIT_REDIS_URL" to url,
            "FASTTOWIN_RATE_LIMIT_NAMESPACE" to namespace()
        )).use { runtime ->
            assertFailsWith<RateLimitUnavailableException> {
                runtime.consume("login-ip", "acl-probe", RateLimitPolicy(1, 60_000))
            }
        }
    }
}
