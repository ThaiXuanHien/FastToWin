package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.AuthenticatedAccount

internal sealed interface RoomOwnershipRoute {
    data object Legacy : RoomOwnershipRoute
    data class Owned(val routeKey: String) : RoomOwnershipRoute
}

internal class RoomOwnershipRouting(
    private val policy: RoomOwnershipRuntimeConfig
) {
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
