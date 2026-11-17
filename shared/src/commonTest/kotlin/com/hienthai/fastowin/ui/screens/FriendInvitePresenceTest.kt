package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.protocol.FriendPresence
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FriendInvitePresenceTest {
    @Test
    fun `online and offline friends can be invited`() {
        assertTrue(isInvitablePresence(FriendPresence.ONLINE))
        assertTrue(isInvitablePresence(FriendPresence.OFFLINE))
    }

    @Test
    fun `friends already in a room or playing cannot be invited`() {
        assertFalse(isInvitablePresence(FriendPresence.IN_ROOM))
        assertFalse(isInvitablePresence(FriendPresence.PLAYING))
    }
}
