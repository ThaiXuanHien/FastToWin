package com.hienthai.fastowin.server.ownership

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class RoomOwnershipRuntimeRoleTest {
    @Test
    fun `an absent runtime role preserves legacy and production ingress configurations`() {
        assertNull(RoomOwnershipRuntimeConfigLoader.fromEnvironment("dev", emptyMap()))
        assertIs<RoomOwnershipPilotConfig>(RoomOwnershipRuntimeConfigLoader.fromEnvironment("dev", pilot()))
        assertIs<RoomOwnershipProductionPolicy>(RoomOwnershipRuntimeConfigLoader.fromEnvironment("prod", production()))
    }

    @Test
    fun `worker rejects legacy mode before any database is opened`() {
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipRuntimeConfigLoader.fromEnvironment("prod", production() + mapOf(
                ROLE to "owned-worker", "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "legacy"
            ))
        }
    }

    @Test
    fun `worker rejects dev pilot mode before fixture resources are opened`() {
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipRuntimeConfigLoader.fromEnvironment("dev", pilot() + (ROLE to "owned-worker"))
        }
    }

    @Test
    fun `worker requires PostgreSQL even when ownership peer policy is otherwise valid`() {
        for (url in listOf(null, "jdbc:h2:mem:worker")) {
            val values = (production() + (ROLE to "owned-worker")).toMutableMap()
            if (url == null) values.remove("DATABASE_URL") else values["DATABASE_URL"] = url
            assertFailsWith<IllegalArgumentException>("worker database URL: $url") {
                RoomOwnershipRuntimeConfigLoader.fromEnvironment("prod", values)
            }
        }
    }

    @Test
    fun `unknown runtime role fails closed instead of silently starting public ingress`() {
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipRuntimeConfigLoader.fromEnvironment("prod", production() + (ROLE to "owned-wroker"))
        }
    }

    @Test
    fun `worker requires the same exclusive private peer policy as production ingress`() {
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipRuntimeConfigLoader.fromEnvironment("prod", production() + mapOf(
                ROLE to "owned-worker", "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "https://public.example.com:8080"
            ))
        }
    }

    private fun production() = mapOf(
        "FASTTOWIN_ENV" to "prod",
        "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
        "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to "00000000-0000-0000-0000-000000000001",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to "00000000-0000-0000-0000-000000000002",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://backend-a.railway.internal:8080",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to "A".repeat(43),
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to "00000000-0000-0000-0000-000000000003",
        "DATABASE_URL" to "jdbc:postgresql://127.0.0.1:5432/worker",
        "DATABASE_PASSWORD" to "isolated-test-password"
    )

    private fun pilot() = production() + mapOf(
        "FASTTOWIN_ENV" to "dev",
        "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "pilot",
        "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to "00000000-0000-0000-0000-000000000004",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://127.0.0.1:65534"
    )

    private companion object {
        const val ROLE = "FASTTOWIN_ROOM_OWNERSHIP_ROLE"
    }
}
