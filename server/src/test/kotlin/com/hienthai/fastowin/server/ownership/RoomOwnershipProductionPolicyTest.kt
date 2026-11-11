package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.AuthenticatedAccount
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomOwnershipProductionPolicyTest {
    @Test
    fun `production policy requires explicit opt in and keeps unsupported modes legacy`() {
        val account = UUID.randomUUID()
        val values = values(account)
        val policy = RoomOwnershipProductionPolicy.fromEnvironment(values)

        requireNotNull(policy)
        assertTrue(policy.allows(AuthenticatedAccount(account, "pilot"), ProtocolGameMode.ORDER))
        assertFalse(policy.allows(AuthenticatedAccount(account, "pilot"), ProtocolGameMode.TIME_ATTACK))
        assertFalse(policy.allows(AuthenticatedAccount(UUID.randomUUID(), "other"), ProtocolGameMode.ORDER))
        assertFalse(policy.allows(AuthenticatedAccount(account, "pilot"), ProtocolGameMode.RANDOM_TARGET))
        assertEquals(account.toString(), policy.routeKey(account))
    }

    @Test
    fun `production policy rejects fixture acceptance settings and public peers`() {
        assertNull(RoomOwnershipProductionPolicy.fromEnvironment(emptyMap()))
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipProductionPolicy.fromEnvironment(values(UUID.randomUUID()) + mapOf(
                "FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE" to "true"
            ))
        }
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipProductionPolicy.fromEnvironment(values(UUID.randomUUID()) + mapOf(
                "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "https://example.com:8080"
            ))
        }
    }

    private fun values(account: UUID): Map<String, String> = mapOf(
        "FASTTOWIN_ENV" to "prod",
        "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
        "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to UUID.randomUUID().toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to UUID.randomUUID().toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://backend-b.railway.internal:8080",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to "A".repeat(43),
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to account.toString()
    )
}

