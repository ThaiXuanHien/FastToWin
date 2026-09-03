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
    fun `older snapshot from current room cannot overwrite rematch state`() {
        val state = GameState(
            currentRoomId = "room-1",
            latestGameSequence = 42L,
            isRematchRequestedByOpponent = true
        )

        assertFalse(state.canApplyGameSnapshot("room-1", 41L))
        assertTrue(state.canApplyGameSnapshot("room-1", 42L))
        assertTrue(state.canApplyGameSnapshot("room-2", 1L))
    }

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

    @Test
    fun `wrong selection updates local mistake count immediately`() {
        val state = GameState(
            currentTarget = 7,
            player = PlayerState("Me", currentTarget = 7, wrongSelections = 2, combo = 4)
        )

        val updated = state.registerOptimisticNumberSelection(9)

        assertEquals(3, updated.player.wrongSelections)
        assertEquals(0, updated.player.combo)
        assertEquals(state, state.registerOptimisticNumberSelection(7))
    }

    @Test
    fun `connection loss during play cannot leak a rematch notice into results`() {
        val playing = GameState(
            isMatchStarted = true,
            currentRoomId = "room-1",
            currentTarget = 4,
            player = PlayerState("Me", score = 20),
            opponent = PlayerState("Opponent", score = 10)
        )

        for (code in listOf("CONNECTION_NOT_READY", "CONNECTION_FAILED", "SEND_FAILED")) {
            val disconnected = playing.withRematchError(ServerMessage.Error(code, "Connection failed"))
            assertEquals(playing, disconnected)

            val recovered = disconnected.withReadySession("player-1")
            val result = recovered.copy(isGameOver = true)
            assertNull(result.rematchNotice)
            assertNull(result.rematchNoticeErrorCode)
            assertEquals("room-1", result.currentRoomId)
            assertEquals(4, result.currentTarget)
            assertEquals(20, result.player.score)
            assertEquals(10, result.opponent.score)
        }
    }

    @Test
    fun `background connection errors preserve confirmed rematch invitations`() {
        for (requestedByMe in listOf(true, false)) {
            val waiting = GameState(
                isGameOver = true,
                isRematchRequestedByMe = requestedByMe,
                isRematchRequestedByOpponent = !requestedByMe,
                rematchExpiresAtEpochMillis = 10_000L,
                rematchNotice = "Confirmed invitation"
            )
            val updated = waiting.withRematchError(
                ServerMessage.Error("CONNECTION_NOT_READY", "Connection failed")
            )

            assertEquals(waiting, updated)
            assertEquals("Confirmed invitation", updated.withReadySession("me").rematchNotice)
        }
    }

    @Test
    fun `failed pending rematch becomes retryable and clears its network notice on resume`() {
        for (code in listOf("CONNECTION_NOT_READY", "CONNECTION_FAILED", "SEND_FAILED")) {
            val pending = GameState(
                isGameOver = true,
                isRematchActionPending = true,
                isRematchRequestedByMe = true,
                rematchNotice = "Sending request",
                error = "Old error"
            )
            val failed = pending.withRematchError(ServerMessage.Error(code, "Connection failed"))

            assertFalse(failed.isRematchActionPending)
            assertFalse(failed.isRematchRequestedByMe)
            assertEquals("Connection failed", failed.rematchNotice)
            assertEquals(code, failed.rematchNoticeErrorCode)

            val recovered = failed.withReadySession("me")
            assertNull(recovered.rematchNotice)
            assertNull(recovered.rematchNoticeErrorCode)
            assertNull(recovered.error)
            assertFalse(recovered.isSearching)
            assertEquals("me", recovered.player.id)
        }
    }

    @Test
    fun `session recovery preserves non network rematch failures`() {
        val failed = GameState(isGameOver = true, isRematchActionPending = true)
            .withRematchError(ServerMessage.Error("OPPONENT_LEFT", "Opponent left"))

        val recovered = failed.withReadySession("me")

        assertFalse(recovered.isRematchActionPending)
        assertEquals("Opponent left", recovered.rematchNotice)
        assertEquals("OPPONENT_LEFT", recovered.rematchNoticeErrorCode)
    }

    @Test
    fun `session recovery does not classify rematch notices by their text`() {
        val state = GameState(isGameOver = true, rematchNotice = "Connection failed")
        assertEquals("Connection failed", state.withReadySession("me").rematchNotice)
    }

    @Test
    fun `unrelated server errors leave rematch state unchanged`() {
        val state = GameState(
            isGameOver = true,
            isRematchActionPending = true,
            isRematchRequestedByOpponent = true,
            rematchNotice = "Accepting invitation"
        )
        assertEquals(state, state.withRematchError(ServerMessage.Error("WRONG_PASSWORD", "Wrong password")))
    }

    private fun friendRequest(requestId: String, userId: String) = FriendRequestSnapshot(
        requestId = requestId,
        userId = userId,
        displayName = userId,
        playerCode = userId.uppercase()
    )
}
