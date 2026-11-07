package com.hienthai.fastowin.server.ownership

import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.hienthai.fastowin.server.ServerRateLimitPolicies
import java.util.Base64

class RoomOwnershipAcceptanceSettingsTest {
    @Test
    fun `raised fixture traffic limits require explicit development pilot opt in`() {
        val values = mapOf(
            "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "pilot",
            "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to UUID.randomUUID().toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to UUID.randomUUID().toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://127.0.0.1:8081",
            "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to Base64.getEncoder().encodeToString(ByteArray(32)),
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to UUID.randomUUID().toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to UUID.randomUUID().toString()
        )
        assertEquals(ServerRateLimitPolicies(), RoomOwnershipPilotConfig.fromEnvironment("dev", values)!!.rateLimitPolicies())
        val raised = RoomOwnershipPilotConfig.fromEnvironment("dev", values + ("FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE" to "true"))!!.rateLimitPolicies()
        assertEquals(5_000, raised.selectNumberPerIp.capacity)
        assertEquals(ServerRateLimitPolicies().selectNumberPerPlayer, raised.selectNumberPerPlayer)
        assertFailsWith<IllegalArgumentException> { RoomOwnershipPilotConfig.fromEnvironment("production", values) }
        assertNull(RoomOwnershipPilotConfig.fromEnvironment("production", values + ("FASTTOWIN_ROOM_OWNERSHIP_MODE" to "legacy")))
        assertTrue(RoomOwnershipMetrics().render(0.0, 2, 10).contains("fasttowin_room_ownership_pool_active_connections 2"))
    }
    @Test
    fun `acceptance rejects non-loopback controls and unowned cleanup projects`() {
        val values = values()
        assertEquals(20, RoomAcceptanceSettings.fromEnvironment(values).roomCount)
        for (key in listOf("FTW_ACCEPTANCE_A", "FTW_ACCEPTANCE_B", "FTW_ACCEPTANCE_PROXY_A", "FTW_ACCEPTANCE_PROXY_B")) {
            assertFailsWith<IllegalArgumentException> {
                RoomAcceptanceSettings.fromEnvironment(values + (key to "http://203.0.113.1:8080"))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            RoomAcceptanceSettings.fromEnvironment(values + ("FTW_ACCEPTANCE_PROJECT" to "production"))
        }
    }

    @Test
    fun `acceptance receipts and compose controls stay inside the selected checkout`() {
        val values = values()
        assertFailsWith<IllegalArgumentException> {
            RoomAcceptanceSettings.fromEnvironment(values + ("FTW_ACCEPTANCE_RECEIPTS" to Path.of("..", "outside").toAbsolutePath().toString()))
        }
        assertFailsWith<IllegalArgumentException> {
            RoomAcceptanceSettings.fromEnvironment(values + ("FTW_ACCEPTANCE_COMPOSE_BASE" to Path.of("..", "compose.yaml").toAbsolutePath().toString()))
        }
    }

    private fun values(): Map<String, String> {
        val root = Path.of("").toAbsolutePath()
        return mapOf(
            "FTW_ACCEPTANCE_PROJECT" to "room-pilot-0123456789ab",
            "FTW_ACCEPTANCE_COMPOSE_BASE" to root.resolve("deploy/room-ownership/compose.local.yaml").toString(),
            "FTW_ACCEPTANCE_COMPOSE_OVERRIDE" to root.resolve("deploy/room-ownership/compose.acceptance.yaml").toString(),
            "FTW_ACCEPTANCE_RECEIPTS" to root.resolve(".artifacts/room-ownership-local/test/fault").toString(),
            "FTW_ACCEPTANCE_A" to "http://127.0.0.1:18080",
            "FTW_ACCEPTANCE_B" to "http://127.0.0.1:18081",
            "FTW_ACCEPTANCE_PROXY_A" to "http://127.0.0.1:18474",
            "FTW_ACCEPTANCE_PROXY_B" to "http://127.0.0.1:18475",
            "FTW_ACCEPTANCE_POSTGRES_PORT" to "15432",
            "FTW_ACCEPTANCE_SUITE" to "Fault",
            "FTW_ACCEPTANCE_BACKENDS" to "2",
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to UUID.randomUUID().toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to List(40) { UUID.randomUUID() }.joinToString(","),
            "FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD" to "FixtureDatabaseOnly",
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD" to "FixturePassword123!"
        )
    }
}
