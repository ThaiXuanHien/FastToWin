package com.hienthai.fastowin.server.ownership

import java.net.InetAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductionRehearsalSettingsTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val id = UUID.fromString("01234567-89ab-4abc-9abc-0123456789ab")
    private val run = "room-prod-0123456789ab"
    private val accounts = List(40) { UUID(0, it + 1L) }

    @Test
    fun `production adapter binds full workload to its dedicated database without marker settings`() {
        val settings = ProductionRehearsalSettings.parse(values(), now)
        val addresses = settings.endpoints(::resolve)
        val acceptance = settings.acceptance(addresses)
        assertEquals(setOf("pg", "a", "b", "redis", "proxy"), addresses.keys)
        assertEquals("room_rehearsal_0123456789ab", acceptance.databaseName)
        assertTrue(acceptance.productionLike)
        assertEquals(600, acceptance.durationSeconds)
        assertEquals(20, acceptance.roomCount)
        assertEquals(2, acceptance.backendCount)
        assertEquals(accounts, acceptance.accountIds)
        assertEquals(id, acceptance.fixtureId)
        assertEquals("room-production", acceptance.emailPrefix)
        assertEquals("room-acceptance-production-0123456789ab", acceptance.databasePoolName)
        assertEquals("Fault", ProductionRehearsalSettings.parse(values() + ("FTW_REHEARSAL_PROFILE" to "fault"), now)
            .acceptance(addresses).suite)
    }

    @Test
    fun `production adapter rejects cross-run hosts unexpected identities and reduced workload`() {
        val wrong = listOf(
            "FTW_REHEARSAL_PROFILE" to "baseline",
            "FTW_REHEARSAL_BACKEND_POOL_SIZE" to "10",
            "FTW_REHEARSAL_DURATION_SECONDS" to "599",
            "FTW_REHEARSAL_ROOMS" to "19",
            "FTW_REHEARSAL_BACKENDS" to "1",
            "FTW_REHEARSAL_BACKEND_A_HOST" to "room-prod-other-a.railway.internal",
            "FTW_REHEARSAL_BACKEND_B_HOST" to "https://public.example",
            "FTW_REHEARSAL_REDIS_HOST" to "127.0.0.1",
            "FTW_REHEARSAL_PROXY_HOST" to "other.railway.internal",
            "RAILWAY_SERVICE_NAME" to "room-prod-other-runner",
            "DATABASE_USER" to "postgres",
            "DATABASE_URL" to "jdbc:postgresql://$run-pg.railway.internal:5432/postgres",
            "FTW_REHEARSAL_JAR_SHA256" to "bad",
            "FTW_REHEARSAL_DEADLINE_UTC" to now.plusSeconds(899).toString(),
            "FTW_REHEARSAL_DEADLINE_UTC" to now.plusSeconds(7201).toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to accounts.take(2).joinToString(","),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE" to "true",
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to id.toString(),
            "ROOM_FAULT_TOKEN" to "bad",
            "FTW_REHEARSAL_REDIS_PASSWORD" to "bad",
            "RAILWAY_TOKEN" to "managed",
            "PGPASSWORD" to "managed"
        )
        for ((key, value) in wrong) assertFailsWith<IllegalArgumentException>(key) {
            ProductionRehearsalSettings.parse(values() + (key to value), now)
        }
        assertFailsWith<IllegalArgumentException> { ProductionRehearsalSettings.parse(values() - "FTW_REHEARSAL_BACKEND_B_HOST", now) }
    }

    @Test
    fun `private DNS pinning rejects public and overlapping addresses`() {
        val settings = ProductionRehearsalSettings.parse(values(), now)
        for (address in listOf("127.0.0.1", "203.0.113.1", "10.0.0.1")) {
            assertFailsWith<IllegalArgumentException> { settings.endpoints { listOf(InetAddress.getByName(address)) } }
        }
        assertFailsWith<IllegalArgumentException> {
            settings.endpoints { resolve(it) + InetAddress.getByName("203.0.113.1") }
        }
    }

    @Test
    fun `explicit supervisor compatible prefix remains derived from the same run identity`() {
        val pilotRun = run.replace("room-prod", "room-pilot")
        val mapped = values().mapValues { (_, value) -> value.replace(run, pilotRun) } +
            ("FTW_REHEARSAL_PREFIX" to "room-pilot")
        assertEquals(pilotRun, ProductionRehearsalSettings.parse(mapped, now).runName)
        assertFailsWith<IllegalArgumentException> {
            ProductionRehearsalSettings.parse(values() + ("FTW_REHEARSAL_PREFIX" to "other"), now)
        }
    }

    @Test
    fun `production jar identity is checked against actual bytes before receipt export`() {
        val jar = Files.createTempFile("production-rehearsal-", ".jar")
        try {
            val bytes = byteArrayOf(1, 2, 3)
            Files.write(jar, bytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val settings = ProductionRehearsalSettings.parse(values() + ("FTW_REHEARSAL_JAR_SHA256" to digest), now)
            assertEquals(digest, settings.verifiedJarSha(jar))
            Files.write(jar, byteArrayOf(4))
            assertFailsWith<IllegalArgumentException> { settings.verifiedJarSha(jar) }
        } finally { Files.deleteIfExists(jar) }
    }

    @Test
    fun `production source identities stay distinct and room creation alternates the pair`() {
        val settings = ProductionRehearsalSettings.parse(values(), now).acceptance(
            ProductionRehearsalSettings.parse(values(), now).endpoints(::resolve)
        )
        assertEquals(40, accounts.map { settings.syntheticSourceAddress(it.toString()) }.toSet().size)
        assertEquals("198.18.0.1", settings.syntheticSourceAddress(accounts.first().toString()))
        assertEquals(listOf(0, 1), settings.matchAccountIndices(0, 0))
        assertEquals(listOf(1, 0), settings.matchAccountIndices(0, 1))
        assertEquals(listOf(0, 1), settings.matchAccountIndices(0, 2))
        val fixture = settings.copy(productionLike = false)
        assertEquals(null, fixture.syntheticSourceAddress(accounts.first().toString()))
        assertEquals(listOf(0, 1), fixture.matchAccountIndices(0, 1))
        assertEquals("fasttowin_room_pilot", fixture.databaseName)
        assertEquals("room-pilot", fixture.emailPrefix)
        assertFalse(fixture.productionLike)
    }

    private fun resolve(host: String): List<InetAddress> = listOf(InetAddress.getByName("10.0.0." + when {
        host.endsWith("-pg.railway.internal") -> 1
        host.endsWith("-a.railway.internal") -> 2
        host.endsWith("-b.railway.internal") -> 3
        host.endsWith("-redis.railway.internal") -> 4
        else -> 5
    }))

    private fun values() = mapOf(
        "RAILWAY_PROJECT_ID" to STAGING_PROJECT,
        "RAILWAY_ENVIRONMENT_ID" to STAGING_ENVIRONMENT,
        "RAILWAY_ENVIRONMENT_NAME" to "staging",
        "RAILWAY_SERVICE_NAME" to "$run-runner",
        "FASTTOWIN_ENV" to "prod",
        "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
        "FASTTOWIN_ROOM_OWNERSHIP_ROLE" to "ingress",
        "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to ADMISSION_NODE_A.toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to ADMISSION_NODE_B.toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://$run-b.railway.internal:8080",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 19 }),
        "FTW_REHEARSAL_SCOPE" to "room-ownership-production",
        "FTW_REHEARSAL_ID" to id.toString(),
        "FTW_REHEARSAL_MANIFEST_SHA256" to "a".repeat(64),
        "FTW_REHEARSAL_JAR_SHA256" to "b".repeat(64),
        "FTW_REHEARSAL_PROFILE" to "two-backend",
        "FTW_REHEARSAL_BACKEND_POOL_SIZE" to "20",
        "FTW_REHEARSAL_DEADLINE_UTC" to now.plusSeconds(1800).toString(),
        "DATABASE_URL" to "jdbc:postgresql://$run-pg.railway.internal:5432/room_rehearsal_0123456789ab",
        "DATABASE_USER" to "room_rehearsal_0123456789ab",
        "DATABASE_PASSWORD" to "c".repeat(64),
        "FTW_REHEARSAL_BACKEND_A_HOST" to "$run-a.railway.internal",
        "FTW_REHEARSAL_BACKEND_B_HOST" to "$run-b.railway.internal",
        "FTW_REHEARSAL_REDIS_HOST" to "$run-redis.railway.internal",
        "FTW_REHEARSAL_PROXY_HOST" to "$run-proxy.railway.internal",
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to accounts.joinToString(","),
        "FASTTOWIN_ROOM_OWNERSHIP_REHEARSAL_PASSWORD" to "Rehearsal1!" + "d".repeat(64),
        "FTW_REHEARSAL_REDIS_PASSWORD" to "e".repeat(64),
        "ROOM_FAULT_TOKEN" to "f".repeat(64)
    )
}
