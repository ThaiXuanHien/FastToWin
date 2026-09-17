package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendRequestPromptTest {
    @Test
    fun `prompt selects the first incoming request not deferred in this session`() {
        val first = request("request-1")
        val second = request("request-2")

        assertEquals(second, nextFriendRequestPrompt(listOf(first, second), setOf(first.requestId)))
    }

    @Test
    fun `prompt stays hidden when every incoming request was deferred`() {
        val request = request("request-1")

        assertNull(nextFriendRequestPrompt(listOf(request), setOf(request.requestId)))
    }

    private fun request(id: String) = FriendRequestSnapshot(
        requestId = id,
        userId = "user-$id",
        displayName = "Player $id",
        playerCode = "PLAYER01"
    )
}
