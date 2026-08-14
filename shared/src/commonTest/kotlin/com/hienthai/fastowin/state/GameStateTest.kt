package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class GameStateTest {
    @Test
    fun `pending social badge counts incoming friend and room invitations only`() {
        val incoming = listOf(
            friendRequest("incoming-1", "player-1"),
            friendRequest("incoming-2", "player-2")
        )
        val outgoing = listOf(friendRequest("outgoing-1", "player-3"))
        val roomInvitation = ServerMessage.RoomInvitation(
            invitationId = "room-invitation-1",
            fromUserId = "player-4",
            fromDisplayName = "Player 4",
            roomId = "room-1",
            roomName = "Test room",
            expiresAtEpochMillis = 10_000L
        )
        val state = GameState(
            social = FriendsSnapshot(incomingRequests = incoming, outgoingRequests = outgoing),
            roomInvitations = listOf(roomInvitation)
        )

        assertEquals(3, state.pendingSocialInvitationCount)
    }

    private fun friendRequest(requestId: String, userId: String) = FriendRequestSnapshot(
        requestId = requestId,
        userId = userId,
        displayName = userId,
        playerCode = userId.uppercase()
    )
}
