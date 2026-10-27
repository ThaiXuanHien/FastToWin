package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.protocol.BlockedPlayerSnapshot
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanMemberFriendActionTest {
    private val request = FriendRequestSnapshot("request-1", "member", "Member", "MEMBER01")

    @Test
    fun `self has no friendship control`() {
        assertNull(clanMemberFriendAction("member", "MEMBER01", "member", FriendsSnapshot(), false))
    }

    @Test
    fun `eligible player can send a request with a public code`() {
        val action = action()
        assertEquals(ClanMemberFriendActionType.SEND, action.type)
        assertTrue(action.enabled)
    }

    @Test
    fun `missing code or loading disables sending`() {
        assertFalse(action(code = "").enabled)
        assertFalse(action(code = "  ").enabled)
        assertFalse(action(loading = true).enabled)
    }

    @Test
    fun `failed initial social load cannot enable a request against stale relationships`() {
        assertFalse(requireNotNull(clanMemberFriendAction("member", "MEMBER01", "self", FriendsSnapshot(), false,
            isSocialReady = false)).enabled)
    }

    @Test
    fun `existing friends and outgoing requests cannot be added again`() {
        val friends = action(FriendsSnapshot(friends = listOf(FriendSnapshot("member", "Member", "MEMBER01"))))
        assertEquals(ClanMemberFriendActionType.ALREADY_FRIENDS, friends.type)
        assertFalse(friends.enabled)
        val outgoing = action(FriendsSnapshot(outgoingRequests = listOf(request)))
        assertEquals(ClanMemberFriendActionType.REQUEST_SENT, outgoing.type)
        assertFalse(outgoing.enabled)
    }

    @Test
    fun `incoming request accepts by its request id`() {
        val incoming = action(FriendsSnapshot(incomingRequests = listOf(request)))
        assertEquals(ClanMemberFriendActionType.ACCEPT, incoming.type)
        assertEquals("request-1", incoming.requestId)
        assertTrue(incoming.enabled)
        assertFalse(action(FriendsSnapshot(incomingRequests = listOf(request)), loading = true).enabled)
        assertFalse(action(FriendsSnapshot(incomingRequests = listOf(request)), code = "").enabled)
    }

    @Test
    fun `blocked relationship takes priority over stale request data`() {
        val blocked = action(FriendsSnapshot(
            incomingRequests = listOf(request),
            blockedPlayers = listOf(BlockedPlayerSnapshot("member", "Member", "MEMBER01"))
        ))
        assertEquals(ClanMemberFriendActionType.BLOCKED, blocked.type)
        assertFalse(blocked.enabled)
    }

    private fun action(social: FriendsSnapshot = FriendsSnapshot(), code: String = "MEMBER01", loading: Boolean = false) =
        requireNotNull(clanMemberFriendAction("member", code, "self", social, loading))
}
