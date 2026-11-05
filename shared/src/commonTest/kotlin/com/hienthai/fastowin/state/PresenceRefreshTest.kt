package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceRefreshTest {
    @Test
    fun onlyCurrentRequestUpdatesExistingFriends() {
        val friend = FriendSnapshot("friend", "Friend", "CODE")
        val state = GameState(isFriendsOpen = true, social = FriendsSnapshot(friends = listOf(friend)))
        val request = ClientMessage.GetPresence("2")
        val response = ServerMessage.PresenceData("2", null, mapOf(
            "friend" to FriendPresence.PLAYING, "removed" to FriendPresence.ONLINE
        ))
        assertEquals(state, state.withPresenceRefresh(request, response.copy(requestId = "1")))
        assertEquals(state, state.withPresenceRefresh(request, response.copy(clanId = "another-clan")))
        assertEquals(state, state.withPresenceRefresh(null, response))
        assertEquals(listOf(friend.copy(presence = FriendPresence.PLAYING)),
            state.withPresenceRefresh(request, response).social.friends)
    }
}
