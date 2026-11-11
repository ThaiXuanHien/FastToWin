package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.AuthenticatedAccount
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoomOwnershipRoutingTest {
    @Test
    fun `only opted in casual order rooms use a stable owned route`() {
        val account = UUID.randomUUID()
        val policy = policy(account)
        val routing = RoomOwnershipRouting(policy)
        val authenticated = AuthenticatedAccount(account, "pilot")

        val first = routing.route(authenticated, ProtocolGameMode.ORDER, MatchType.CASUAL)
        val reconnect = routing.route(authenticated, ProtocolGameMode.ORDER, MatchType.CASUAL)

        assertIs<RoomOwnershipRoute.Owned>(first)
        assertEquals(first, reconnect)
        assertEquals(account.toString(), first.routeKey)
        assertIs<RoomOwnershipRoute.Legacy>(
            routing.route(authenticated, ProtocolGameMode.ORDER, MatchType.RANKED)
        )
        assertIs<RoomOwnershipRoute.Legacy>(
            routing.route(authenticated, ProtocolGameMode.RANDOM_TARGET, MatchType.CASUAL)
        )
    }

    @Test
    fun `non opted in account remains on legacy route`() {
        val policy = policy(UUID.randomUUID())
        val routing = RoomOwnershipRouting(policy)
        val account = AuthenticatedAccount(UUID.randomUUID(), "legacy")

        assertIs<RoomOwnershipRoute.Legacy>(
            routing.route(account, ProtocolGameMode.ORDER, MatchType.CASUAL)
        )
    }

    private fun policy(account: UUID): RoomOwnershipProductionPolicy {
        val values = mapOf(
            "FASTTOWIN_ENV" to "prod",
            "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
            "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to UUID.randomUUID().toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to UUID.randomUUID().toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://backend-b.railway.internal:8080",
            "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to Base64.getEncoder().encodeToString(ByteArray(32)),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to account.toString()
        )
        return requireNotNull(RoomOwnershipProductionPolicy.fromEnvironment(values))
    }
}

