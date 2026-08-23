package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `match start closes overlays and pending room invitation prompt`() {
        val invitation = ServerMessage.RoomInvitation(
            invitationId = "room-invitation-1",
            fromUserId = "player-1",
            fromDisplayName = "Player 1",
            roomId = "room-1",
            roomName = "Test room",
            expiresAtEpochMillis = 10_000L
        )
        val state = GameState(
            isProfileOpen = true,
            isProfileLoading = true,
            isLeaderboardOpen = true,
            isLeaderboardLoading = true,
            isFriendsOpen = true,
            isFriendsLoading = true,
            roomInvitationPrompt = invitation
        )

        val prepared = state.prepareForMatchStart()

        assertFalse(prepared.isProfileOpen)
        assertFalse(prepared.isProfileLoading)
        assertFalse(prepared.isLeaderboardOpen)
        assertFalse(prepared.isLeaderboardLoading)
        assertFalse(prepared.isFriendsOpen)
        assertFalse(prepared.isFriendsLoading)
        assertNull(prepared.roomInvitationPrompt)
    }

    @Test
    fun `notifications preserve the previous destination when opened and closed`() {
        val previous = GameState(
            isProfileOpen = true,
            isFriendsOpen = true,
            isClanOpen = true,
            isShopOpen = true,
            error = "Old error"
        )

        val opened = previous.openNotificationsOverlay()

        assertTrue(opened.isNotificationsOpen)
        assertTrue(opened.isProfileOpen)
        assertTrue(opened.isFriendsOpen)
        assertTrue(opened.isClanOpen)
        assertTrue(opened.isShopOpen)
        assertNull(opened.error)

        val closed = opened.closeNotificationsOverlay()

        assertFalse(closed.isNotificationsOpen)
        assertTrue(closed.isProfileOpen)
        assertTrue(closed.isFriendsOpen)
        assertTrue(closed.isClanOpen)
        assertTrue(closed.isShopOpen)
    }

    @Test
    fun `post match friend action recognizes recent opponent and pending requests`() {
        val opponentId = "player-2"
        val recent = RecentPlayerSnapshot(
            userId = opponentId,
            displayName = "Opponent",
            playerCode = "OPPONENT",
            lastPlayedAtEpochMillis = 1_000L,
            matchesPlayed = 1
        )
        val base = GameState(
            profile = PlayerProfileSnapshot(userId = "player-1", displayName = "Me", playerCode = "ME000001"),
            opponent = PlayerState("Opponent", id = opponentId),
            social = FriendsSnapshot(recentPlayers = listOf(recent))
        )
        assertEquals(PostMatchFriendStatus.AVAILABLE, base.postMatchFriendStatus)

        val outgoing = base.copy(
            social = base.social.copy(outgoingRequests = listOf(friendRequest("request-1", opponentId)))
        )
        assertEquals(PostMatchFriendStatus.REQUEST_SENT, outgoing.postMatchFriendStatus)

        val incoming = base.copy(
            social = base.social.copy(incomingRequests = listOf(friendRequest("request-2", opponentId)))
        )
        assertEquals(PostMatchFriendStatus.REQUEST_RECEIVED, incoming.postMatchFriendStatus)
    }

    @Test
    fun `friend relationship takes priority over recent opponent`() {
        val opponentId = "player-2"
        val state = GameState(
            profile = PlayerProfileSnapshot(userId = "player-1", displayName = "Me", playerCode = "ME000001"),
            opponent = PlayerState("Opponent", id = opponentId),
            social = FriendsSnapshot(
                friends = listOf(FriendSnapshot(opponentId, "Opponent", "OPPONENT")),
                recentPlayers = listOf(
                    RecentPlayerSnapshot(opponentId, "Opponent", "OPPONENT", lastPlayedAtEpochMillis = 1_000L, matchesPlayed = 2)
                )
            )
        )

        assertEquals(PostMatchFriendStatus.FRIEND, state.postMatchFriendStatus)
    }

    private fun friendRequest(requestId: String, userId: String) = FriendRequestSnapshot(
        requestId = requestId,
        userId = userId,
        displayName = userId,
        playerCode = userId.uppercase()
    )
}
