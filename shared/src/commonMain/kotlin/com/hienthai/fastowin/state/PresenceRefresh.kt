package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ServerMessage

internal fun GameState.withPresenceRefresh(
    request: ClientMessage.GetPresence?,
    response: ServerMessage.PresenceData
): GameState {
    if (request == null || request.requestId != response.requestId || request.clanId != response.clanId) return this
    if (response.clanId == null) {
        if (!isFriendsOpen) return this
        return copy(social = social.copy(friends = social.friends.map { friend ->
            response.states[friend.userId]?.let { friend.copy(presence = it) } ?: friend
        }))
    }
    val clan = currentClan?.takeIf { isClanOpen && it.id == response.clanId } ?: return this
    return copy(currentClan = clan.copy(members = clan.members.map { member ->
        response.states[member.userId]?.let { member.copy(presence = it) } ?: member
    }))
}
