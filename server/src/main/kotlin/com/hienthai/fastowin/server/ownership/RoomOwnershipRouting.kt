package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.AuthenticatedAccount
import java.util.UUID

internal sealed interface RoomOwnershipRoute {
    data object Legacy : RoomOwnershipRoute
    data class Owned(val routeKey: String) : RoomOwnershipRoute
}

internal class RoomOwnershipRouting(
    private val policy: RoomOwnershipRuntimeConfig
) {
    fun createNode(routeKey: String): UUID {
        require(canonicalCreateUuid(routeKey) != null) { "Create route requires a canonical account UUID" }
        val nodes = listOf(policy.nodeId, policy.peerId).distinct().sortedBy(UUID::toString)
        require(nodes.size == 2)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(
            ("fasttowin/owned-create-routing/v1\naccount=$routeKey\nnodes=${nodes.joinToString(",")}\n").toByteArray(Charsets.UTF_8))
        val index = digest.fold(0) { remainder, byte -> (remainder * 256 + (byte.toInt() and 0xff)) % nodes.size }
        return nodes[index]
    }

    fun route(
        account: AuthenticatedAccount,
        mode: ProtocolGameMode,
        matchType: MatchType,
        privateRoom: Boolean = true
    ): RoomOwnershipRoute {
        if (!privateRoom || matchType != MatchType.CASUAL || !account.emailVerified ||
            account.userId !in policy.allowedAccounts || !policy.supportsMode(mode)) {
            return RoomOwnershipRoute.Legacy
        }
        return RoomOwnershipRoute.Owned(account.userId.toString())
    }
}
