package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.AuthenticatedAccount

internal sealed interface RoomOwnershipRoute {
    data object Legacy : RoomOwnershipRoute
    data class Owned(val routeKey: String) : RoomOwnershipRoute
}

internal class RoomOwnershipRouting(
    private val policy: RoomOwnershipProductionPolicy
) {
    fun route(
        account: AuthenticatedAccount,
        mode: ProtocolGameMode,
        matchType: MatchType
    ): RoomOwnershipRoute {
        if (matchType != MatchType.CASUAL || !policy.allows(account, mode)) {
            return RoomOwnershipRoute.Legacy
        }
        return RoomOwnershipRoute.Owned(policy.routeKey(account.userId))
    }
}
