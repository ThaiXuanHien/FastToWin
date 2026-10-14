package com.hienthai.fastowin.server.avatars

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import java.util.UUID
import kotlin.test.*

class AvatarCleanupTest {
    @Test fun `slow batch claims each delete just in time so another worker cannot reclaim unstarted items`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val keys = (1..3).map { "avatars/$player/${UUID.randomUUID()}.jpg" }
            keys.forEach { key ->
                assertTrue(repository.beginIntent(player, 0, key))
                now += 1 // Deterministic oldest-first ordering.
            }
            now += 2 * 60 * 60_000L
            val secondStarted = CompletableDeferred<Unit>()
            val finishSecond = CompletableDeferred<Unit>()
            val deletes = mutableMapOf<String, Int>()
            val storage = object : AvatarObjectStore {
                override suspend fun put(key: String, bytes: ByteArray, contentType: String) = error("unused")
                override suspend fun read(key: String, maximumBytes: Int): ByteArray? = error("unused")
                override suspend fun delete(key: String) {
                    deletes[key] = deletes.getOrDefault(key, 0) + 1
                    if (key == keys[0]) now += 45_000
                    if (key == keys[1] && deletes[key] == 1) {
                        now += 30_000 // Initial batch lease expired; this delete's own lease has not.
                        secondStarted.complete(Unit)
                        finishSecond.await()
                    }
                }
            }
            val first = async { AvatarService(repository, storage).cleanupBatch() }
            try {
                secondStarted.await()
                AvatarService(PostgresAvatarRepository(source, nowMillis = { now }), storage).cleanupBatch()
            } finally { finishSecond.complete(Unit) }
            first.await()
            assertEquals(keys.associateWith { 1 }, deletes)
            source.connection.use { connection ->
                connection.prepareStatement("SELECT attempts, state FROM avatar_cleanup WHERE object_key LIKE ?").use {
                    it.setString(1, "avatars/$player/%")
                    it.executeQuery().use { rows ->
                        var count = 0
                        while (rows.next()) {
                            count++
                            assertEquals(1, rows.getInt(1))
                            assertEquals("deleted", rows.getString(2))
                        }
                        assertEquals(3, count)
                    }
                }
            }
        }
    }

    @Test fun `failed cleanup persists a future retry without discarding the intent`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val key = "avatars/$player/${UUID.randomUUID()}.jpg"
            assertTrue(repository.beginIntent(player, 0, key))
            now += 2 * 60 * 60_000L
            assertEquals(key, repository.claimCleanup(1).single().key)
            repository.retryCleanup(key)
            assertTrue(repository.claimCleanup(1).isEmpty())
            source.connection.use { connection ->
                connection.prepareStatement("SELECT state, attempts, last_error_code FROM avatar_cleanup WHERE object_key = ?").use {
                    it.setString(1, key)
                    it.executeQuery().use { row ->
                        assertTrue(row.next()); assertEquals("deleting", row.getString(1))
                        assertEquals(1, row.getInt(2)); assertEquals("OBJECT_DELETE_UNAVAILABLE", row.getString(3))
                    }
                }
            }
        }
    }
    @Test fun `replacement waits 24 hours and repeated failures stop visibly at retry cap`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val objects = MemoryAvatarObjects()
            val service = AvatarService(repository, objects, objectWrites = true)
            assertTrue(service.upload(player, imageBase64()))
            val old = assertNotNull(repository.current(player)?.metadata).key
            assertTrue(service.upload(player, imageBase64(40)))
            val active = assertNotNull(repository.current(player)?.metadata).key
            now += 23 * 60 * 60_000L
            service.cleanupBatch()
            assertTrue(objects.objects.containsKey(old))
            now += 2 * 60 * 60_000L
            objects.failDeletes = true
            repeat(8) { service.cleanupBatch(); now += 2 * 60 * 60_000L }
            source.connection.use { connection ->
                connection.prepareStatement("SELECT state, attempts, last_error_code FROM avatar_cleanup WHERE object_key = ?").use {
                    it.setString(1, old)
                    it.executeQuery().use { row ->
                        assertTrue(row.next()); assertEquals("failed", row.getString(1))
                        assertEquals(8, row.getInt(2)); assertEquals("OBJECT_DELETE_UNAVAILABLE", row.getString(3))
                    }
                }
            }
            objects.failDeletes = false
            service.cleanupBatch()
            assertEquals(setOf(old, active), objects.objects.keys)
        }
    }

    @Test fun `crash after final cleanup claim becomes visible failed work after lease expires`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val key = "avatars/$player/${UUID.randomUUID()}.jpg"
            assertTrue(repository.beginIntent(player, 0, key))
            now += 2 * 60 * 60_000L
            repeat(8) {
                assertEquals(key, repository.claimCleanup(1).single().key)
                now += 2 * 60_000L
            }
            assertTrue(repository.claimCleanup(1).isEmpty())
            source.connection.use { connection ->
                connection.prepareStatement("SELECT state FROM avatar_cleanup WHERE object_key = ?").use {
                    it.setString(1, key)
                    it.executeQuery().use { row -> assertTrue(row.next()); assertEquals("failed", row.getString(1)) }
                }
            }
        }
    }

    @Test fun `an expired intent cannot commit even before any cleanup has claimed it`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val key = "avatars/$player/${UUID.randomUUID()}.jpg"
            assertTrue(repository.beginIntent(player, 0, key))
            now += 2 * 60 * 60_000L
            val metadata = AvatarMetadata(key, "image/jpeg", 10, "0".repeat(64), 1, 1)
            assertFalse(repository.commit(player, 0, metadata))
            assertNull(repository.current(player)?.metadata)
        }
    }
}
