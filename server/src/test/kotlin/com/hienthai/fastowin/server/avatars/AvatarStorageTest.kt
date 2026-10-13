package com.hienthai.fastowin.server.avatars

import com.hienthai.fastowin.server.PostgresGuestIdentityRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.*

internal class MemoryAvatarObjects : AvatarObjectStore {
    val objects = mutableMapOf<String, ByteArray>()
    var failWrites = false
    var failReads = false
    var failDeletes = false
    var beforePut: suspend () -> Unit = {}
    override suspend fun put(key: String, bytes: ByteArray, contentType: String) {
        beforePut()
        if (failWrites) error("provider unavailable")
        objects[key] = bytes.copyOf()
    }
    override suspend fun read(key: String, maximumBytes: Int): ByteArray? {
        if (failReads) error("provider unavailable")
        return objects[key]?.copyOf()
    }
    override suspend fun delete(key: String) {
        if (failDeletes) error("provider unavailable")
        objects.remove(key)
    }
}

internal suspend fun avatarDatabase(block: suspend (HikariDataSource, String) -> Unit) {
    val url = System.getenv("TEST_DATABASE_URL") ?: error("Avatar DB integration tests require TEST_DATABASE_URL")
    require(url.startsWith("jdbc:postgresql://127.0.0.1:15432/")) { "Only the local avatar test database is allowed" }
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = System.getenv("TEST_DATABASE_USER")
        password = System.getenv("TEST_DATABASE_PASSWORD")
        maximumPoolSize = 4
    }).use { source ->
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
        val player = PostgresGuestIdentityRepository(source).resolveGuest("Avatar test", null, System.currentTimeMillis())
        try { block(source, player.playerId) }
        finally {
            source.connection.use { connection ->
                connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                    statement.setObject(1, UUID.fromString(player.playerId)); statement.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM avatar_cleanup WHERE object_key LIKE ?").use { statement ->
                    statement.setString(1, "avatars/${player.playerId}/%"); statement.executeUpdate()
                }
            }
        }
    }
}

class AvatarStorageTest {
    @Test fun `historical JPEG above upload cap remains readable through bounded legacy fallback`() = runTest {
        avatarDatabase { source, player ->
            val image = java.awt.image.BufferedImage(280, 280, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val random = java.util.Random(123)
            for (y in 0 until 280) for (x in 0 until 280) image.setRGB(x, y, random.nextInt())
            val output = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(image, "jpg", output)
            val bytes = output.toByteArray()
            assertTrue(bytes.size > 45 * 1024 && bytes.size < 49 * 1024)
            val base64 = Base64.getEncoder().encodeToString(bytes)
            source.connection.use { connection ->
                connection.prepareStatement("UPDATE profiles SET avatar_data = ? WHERE user_id = ?").use {
                    it.setString(1, base64); it.setObject(2, UUID.fromString(player)); it.executeUpdate()
                }
            }
            val service = AvatarService(PostgresAvatarRepository(source))
            assertFalse(service.upload(player, base64))
            val result = assertIs<AvatarReadResult.Found>(service.read(player))
            assertTrue(result.bytes.size <= 45 * 1024)
        }
    }
    @Test fun `default config is database and r2 rejects missing malformed configuration safely`() {
        assertEquals(AvatarStorageMode.DATABASE, AvatarStorageConfiguration.fromEnvironment(emptyMap()).mode)
        for (values in listOf(mapOf("FASTTOWIN_AVATAR_STORAGE" to "other"),
            mapOf("FASTTOWIN_AVATAR_STORAGE" to "r2"),
            mapOf("FASTTOWIN_AVATAR_STORAGE" to "r2", "FASTTOWIN_AVATAR_R2_ACCOUNT_ID" to "https://evil",
                "FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY" to "secret-should-never-print"))) {
            val error = assertFailsWith<IllegalArgumentException> { AvatarStorageConfiguration.fromEnvironment(values) }
            assertFalse(error.toString().contains("secret-should-never-print"))
            assertFalse(error.toString().contains("https://evil"))
        }
        val values = mapOf("FASTTOWIN_AVATAR_STORAGE" to "r2", "FASTTOWIN_AVATAR_R2_ACCOUNT_ID" to "a".repeat(32),
            "FASTTOWIN_AVATAR_R2_BUCKET" to "avatar-private", "FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID" to "a".repeat(32),
            "FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY" to "b".repeat(64))
        for (bucket in listOf("has.dot", "-leading", "trailing-", "UPPERCASE", "x")) {
            assertFailsWith<IllegalArgumentException> {
                AvatarStorageConfiguration.fromEnvironment(values + ("FASTTOWIN_AVATAR_R2_BUCKET" to bucket))
            }
        }
        val configuration = AvatarStorageConfiguration.fromEnvironment(values)
        assertEquals(AvatarStorageMode.R2, configuration.mode)
        assertFailsWith<IllegalArgumentException> { configuration.validateDatabase(false) }
        configuration.validateDatabase(true)
        assertFalse(configuration.toString().contains("b".repeat(64)))
        assertFalse(configuration.toString().contains("a".repeat(32)))
        assertNotNull(AvatarStorageConfiguration.fromEnvironment(values + ("FASTTOWIN_AVATAR_STORAGE" to "database")).r2)
    }

    @Test fun `database upload normalizes legacy bytes and missing player cannot write`() = runTest {
        avatarDatabase { source, player ->
            val repository = PostgresAvatarRepository(source)
            val service = AvatarService(repository)
            assertTrue(service.upload(player, imageBase64()))
            val bytes = assertIs<AvatarReadResult.Found>(service.read(player)).bytes
            assertEquals(0xd8, bytes[1].toInt() and 255)
            assertNotNull(repository.current(player)?.legacyBase64)
            assertFalse(service.upload(UUID.randomUUID().toString(), imageBase64()))
        }
    }

    @Test fun `object upload preserves old avatar on provider and CAS failure with no legacy fallback`() = runTest {
        avatarDatabase { source, player ->
            val repository = PostgresAvatarRepository(source)
            assertTrue(AvatarService(repository).upload(player, imageBase64()))
            val previous = repository.current(player)
            val objects = MemoryAvatarObjects()
            val service = AvatarService(repository, objects, objectWrites = true)
            objects.failWrites = true
            assertFalse(service.upload(player, imageBase64(40)))
            assertEquals(previous, repository.current(player))
            objects.failWrites = false
            val failedRepository = object : AvatarRepository by repository {
                override suspend fun commit(playerId: String, expectedRevision: Long, metadata: AvatarMetadata): Boolean = error("DB unavailable")
            }
            assertFalse(AvatarService(failedRepository, objects, objectWrites = true).upload(player, imageBase64(50)))
            assertEquals(previous, repository.current(player))
            assertTrue(service.upload(player, imageBase64(60)))
            val current = assertNotNull(repository.current(player))
            assertNull(current.legacyBase64)
            assertNotNull(current.metadata)
            assertIs<AvatarReadResult.Found>(service.read(player))
            objects.failReads = true
            assertEquals(AvatarReadResult.Unavailable, service.read(player))
            assertEquals(AvatarReadResult.Unavailable, AvatarService(repository).read(player))
        }
    }

    @Test fun `concurrent updates publish one unique object and clean only unreferenced keys after restart`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val objects = MemoryAvatarObjects()
            val bothStarted = CompletableDeferred<Unit>()
            var started = 0
            objects.beforePut = { if (++started == 2) bothStarted.complete(Unit); bothStarted.await() }
            val service = AvatarService(repository, objects, objectWrites = true)
            val results = coroutineScope { listOf(async { service.upload(player, imageBase64(30)) }, async { service.upload(player, imageBase64(40)) }).awaitAll() }
            assertEquals(1, results.count { it })
            assertEquals(2, objects.objects.size)
            val active = assertNotNull(repository.current(player)?.metadata).key
            now += 25 * 60 * 60_000L
            val restarted = AvatarService(PostgresAvatarRepository(source, nowMillis = { now }), objects, objectWrites = true)
            restarted.cleanupBatch()
            assertEquals(setOf(active), objects.objects.keys)
        }
    }

    @Test fun `cleanup claims prevent late CAS and referenced objects survive queued cleanup`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val key = "avatars/$player/${UUID.randomUUID()}.jpg"
            assertTrue(repository.beginIntent(player, 0, key))
            now += 2 * 60 * 60_000L
            assertEquals(key, repository.claimCleanup(10).single().key)
            val bytes = Base64.getDecoder().decode(imageBase64(20, 20, "jpg"))
            val metadata = AvatarMetadata(key, "image/jpeg", bytes.size, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, 20, 20)
            assertFalse(repository.commit(player, 0, metadata))
            assertNull(repository.current(player)?.metadata)
            val objects = MemoryAvatarObjects()
            assertTrue(AvatarService(repository, objects, objectWrites = true).upload(player, imageBase64()))
            val active = assertNotNull(repository.current(player)?.metadata).key
            source.connection.use { connection ->
                connection.prepareStatement("INSERT INTO avatar_cleanup(object_key, state, available_at) VALUES (?, 'pending', NOW() - INTERVAL '1 day')").use {
                    it.setString(1, active); it.executeUpdate()
                }
            }
            now += 25 * 60 * 60_000L
            AvatarService(repository, objects, objectWrites = true).cleanupBatch()
            assertTrue(objects.objects.containsKey(active))
        }
    }

    @Test fun `account cascade queues active key and retry survives restart without restoring metadata`() = runTest {
        avatarDatabase { source, player ->
            var now = System.currentTimeMillis()
            val repository = PostgresAvatarRepository(source, nowMillis = { now })
            val objects = MemoryAvatarObjects()
            val service = AvatarService(repository, objects, objectWrites = true)
            assertTrue(service.upload(player, imageBase64()))
            val key = assertNotNull(repository.current(player)?.metadata).key
            source.connection.use { connection ->
                connection.prepareStatement("DELETE FROM users WHERE id = ?").use {
                    it.setObject(1, UUID.fromString(player)); it.executeUpdate()
                }
            }
            now += 60_000
            objects.failDeletes = true
            service.cleanupBatch()
            assertTrue(objects.objects.containsKey(key))
            assertEquals(AvatarReadResult.NotFound, service.read(player))
            assertFalse(service.upload(player, imageBase64()))
            objects.failDeletes = false
            now += 60 * 60_000L
            AvatarService(PostgresAvatarRepository(source, nowMillis = { now }), objects, objectWrites = true).cleanupBatch()
            assertFalse(objects.objects.containsKey(key))
        }
    }

    @Test fun `deleted profile during put and cancellation cannot publish a pointer`() = runTest {
        avatarDatabase { source, player ->
            val repository = PostgresAvatarRepository(source)
            val objects = MemoryAvatarObjects()
            objects.beforePut = { throw CancellationException("stop") }
            assertFailsWith<CancellationException> { AvatarService(repository, objects, objectWrites = true).upload(player, imageBase64()) }
            objects.beforePut = {
                source.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use {
                        it.setObject(1, UUID.fromString(player)); it.executeUpdate()
                    }
                }
            }
            assertFalse(AvatarService(repository, objects, objectWrites = true).upload(player, imageBase64()))
            assertNull(repository.current(player))
        }
    }

    @Test fun `oversized corrupt and mismatched objects are never served`() = runTest {
        avatarDatabase { source, player ->
            val repository = PostgresAvatarRepository(source)
            val objects = MemoryAvatarObjects()
            val service = AvatarService(repository, objects, objectWrites = true)
            assertFalse(service.upload(player, Base64.getEncoder().encodeToString(ByteArray(45 * 1024 + 1))))
            assertTrue(objects.objects.isEmpty())
            assertTrue(service.upload(player, imageBase64()))
            val key = assertNotNull(repository.current(player)?.metadata).key
            objects.objects[key] = byteArrayOf(1, 2, 3)
            assertEquals(AvatarReadResult.Unavailable, service.read(player))
            objects.objects[key] = ByteArray(45 * 1024 + 1)
            assertEquals(AvatarReadResult.Unavailable, service.read(player))
        }
    }
}
