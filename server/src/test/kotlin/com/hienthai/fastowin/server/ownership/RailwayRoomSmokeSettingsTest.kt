package com.hienthai.fastowin.server.ownership

import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RailwayRoomSmokeSettingsTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val fixture = UUID.fromString("01234567-89ab-4abc-9abc-0123456789ab")
    private val a = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val b = UUID.fromString("22222222-2222-4222-8222-222222222222")

    @Test
    fun `derives exact endpoints for the current staging fixture`() {
        val settings = RailwayRoomSmokeSettings.fromEnvironment(values(), now)
        assertEquals("room-pilot-0123456789ab-pg.railway.internal", settings.databaseHost)
        assertEquals(
            listOf("room-pilot-0123456789ab-a.railway.internal", "room-pilot-0123456789ab-b.railway.internal"),
            settings.backendHosts
        )
        assertEquals(listOf(a, b), settings.accountIds)
    }

    @Test
    fun `rejects another project environment or service before resolving DNS`() {
        for ((key, value) in mapOf(
            "RAILWAY_PROJECT_ID" to "8daf3d1f-df4c-4f39-a07b-61becae674fa",
            "RAILWAY_ENVIRONMENT_ID" to "351ee85f-7760-4b57-9649-9693fdcdbfea",
            "RAILWAY_ENVIRONMENT_NAME" to "production",
            "RAILWAY_SERVICE_NAME" to "staging-server",
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to UUID.randomUUID().toString(),
            "DATABASE_URL" to "jdbc:postgresql://other.railway.internal/production"
        )) {
            assertFailsWith<IllegalArgumentException>(key) {
                RailwayRoomSmokeSettings.fromEnvironment(values() + (key to value), now)
            }
        }
    }

    @Test
    fun `refuses duplicate accounts and an expired or unbounded window`() {
        assertFailsWith<IllegalArgumentException> {
            RailwayRoomSmokeSettings.fromEnvironment(values() + ("FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to "$a,$a"), now)
        }
        for (deadline in listOf(now.minusSeconds(1), now.plusSeconds(60), now.plusSeconds(7_201))) {
            assertFailsWith<IllegalArgumentException> {
                RailwayRoomSmokeSettings.fromEnvironment(values() + ("FTW_PILOT_DEADLINE_UTC" to deadline.toString()), now)
            }
        }
    }

    @Test
    fun `refuses shared backend addresses and public or loopback resolution`() {
        val settings = RailwayRoomSmokeSettings.fromEnvironment(values(), now)
        val hosts = settings.backendHosts + settings.databaseHost
        val good = hosts.zip(listOf("10.0.0.1", "fd00::2", "172.16.0.3")).toMap()
        settings.verifyPrivateAddresses { listOf(InetAddress.getByName(good.getValue(it))) }
        for (bad in listOf("10.0.0.1", "127.0.0.1", "::1", "203.0.113.1", "fe80::1")) {
            assertFailsWith<IllegalArgumentException>(bad) {
                settings.verifyPrivateAddresses { host ->
                    listOf(InetAddress.getByName(if (host == hosts[1]) bad else good.getValue(host)))
                }
            }
        }
    }

    @Test
    fun refuses_a_private_host_from_another_fixture_service() {
        for ((key, value) in mapOf(
            "FTW_PILOT_DATABASE_HOST" to "room-pilot-deadbeef0000-pg.railway.internal",
            "FTW_PILOT_BACKEND_A_HOST" to "room-pilot-deadbeef0000-a.railway.internal",
            "FTW_PILOT_BACKEND_B_HOST" to "room-pilot-deadbeef0000-b.railway.internal"
        )) {
            assertFailsWith<IllegalArgumentException>(key) {
                RailwayRoomSmokeSettings.fromEnvironment(values() + (key to value), now)
            }
        }
    }

    private fun values() = mapOf(
        "RAILWAY_PROJECT_ID" to "b5c5db71-2df8-46da-84fb-d9b9df6d7951",
        "RAILWAY_ENVIRONMENT_ID" to "8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9",
        "RAILWAY_ENVIRONMENT_NAME" to "staging",
        "RAILWAY_SERVICE_NAME" to "room-pilot-0123456789ab-runner",
        "FTW_PILOT_DEADLINE_UTC" to now.plusSeconds(600).toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to fixture.toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to "$a,$b",
        "FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD" to "f".repeat(64),
        "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD" to ("Pilot1!" + "a".repeat(64)),
        "FTW_PILOT_DATABASE_HOST" to "room-pilot-0123456789ab-pg.railway.internal",
        "FTW_PILOT_BACKEND_A_HOST" to "room-pilot-0123456789ab-a.railway.internal",
        "FTW_PILOT_BACKEND_B_HOST" to "room-pilot-0123456789ab-b.railway.internal"
    )
}
