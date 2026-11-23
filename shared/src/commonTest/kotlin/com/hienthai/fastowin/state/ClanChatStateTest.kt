package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatMessageSnapshot
import com.hienthai.fastowin.protocol.ClanChatSystemEvent
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.ui.screens.chatTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanChatStateTest {
    private val clan = "clan-1"
    private fun line(id: String, at: Long, sender: String? = "other", kind: ClanChatMessageKind = ClanChatMessageKind.USER) =
        ClanChatMessageSnapshot(id, clan, kind, at, sender, "Other", "text $id",
            systemEvent = if (kind == ClanChatMessageKind.SYSTEM) ClanChatSystemEvent.MEMBER_JOINED else null)

    @Test
    fun `first page replaces lines, keeps newer live lines and adopts unread and mute state`() {
        val live = ClanChatState().loading(clan).withMessage(line("c", 300), null, "me")
        val loaded = live.withHistory(ServerMessage.ClanChatHistory(clan, listOf(line("a", 100), line("b", 200)),
            hasMore = true, unreadCount = 4, mutedUntilEpochMillis = 999, mutedMembers = mapOf("x" to 5L)))
        assertEquals(listOf("a", "b", "c"), loaded.messages.map { it.id })
        assertTrue(loaded.hasMore)
        assertFalse(loaded.isLoading)
        assertEquals(4, loaded.unreadCount)
        assertEquals(999, loaded.mutedUntilEpochMillis)
        assertTrue(loaded.isMuted(998))
        assertFalse(loaded.isMuted(999))

        val older = loaded.copy(isLoadingOlder = true).withHistory(ServerMessage.ClanChatHistory(clan,
            listOf(line("z", 50), line("a", 100)), hasMore = false, isOlderPage = true))
        assertEquals(listOf("z", "a", "b", "c"), older.messages.map { it.id })
        assertFalse(older.hasMore)
        assertFalse(older.isLoadingOlder)
        assertEquals(4, older.unreadCount)

        val otherClan = older.withHistory(ServerMessage.ClanChatHistory("clan-2", emptyList(), hasMore = false))
        assertEquals(older, otherClan)
    }

    @Test
    fun `unread counts other members' lines only while chat is hidden`() {
        var state = ClanChatState().loading(clan)
        state = state.withMessage(line("1", 1), null, "me")
        state = state.withMessage(line("2", 2, sender = "me"), null, "me")
        state = state.withMessage(line("3", 3, sender = null, kind = ClanChatMessageKind.SYSTEM), null, "me")
        state = state.withMessage(line("1", 1), null, "me")
        assertEquals(1, state.unreadCount)
        assertEquals(listOf("1", "2", "3"), state.messages.map { it.id })
        state = state.shown(true)
        assertEquals(0, state.unreadCount)
        state = state.withMessage(line("4", 4), null, "me")
        assertEquals(0, state.unreadCount)
        assertEquals("4", state.newestMessageId)
    }

    @Test
    fun `pending lines resolve on confirmation, keep failures and can be discarded`() {
        var state = ClanChatState().loading(clan).sending("r1", "hello", 10).sending("r2", "again", 11)
        assertTrue(state.hasPending("r1"))
        state = state.withMessage(line("m1", 12, sender = "me"), "r1", "me")
        assertFalse(state.hasPending("r1"))
        state = state.failed("r2", "Too fast")
        assertEquals("Too fast", state.pending.single().failure)
        state = state.discard("r2")
        assertTrue(state.pending.isEmpty())
    }

    @Test
    fun `removals, mutes and clan switches stay scoped to the current clan`() {
        var state = ClanChatState().loading(clan).withMessage(line("1", 1), null, "me")
        state = state.withoutMessage("clan-2", "1")
        assertEquals(1, state.messages.size)
        state = state.withoutMessage(clan, "1")
        assertTrue(state.messages.isEmpty())
        state = state.withMute(ServerMessage.ClanChatMuteChanged(clan, "me", 500), "me")
        assertEquals(500, state.mutedUntilEpochMillis)
        assertEquals(mapOf("me" to 500L), state.mutedMembers)
        state = state.withMute(ServerMessage.ClanChatMuteChanged(clan, "me", null), "me")
        assertNull(state.mutedUntilEpochMillis)
        assertTrue(state.mutedMembers.isEmpty())
        val switched = state.shown(true).loading("clan-2")
        assertEquals("clan-2", switched.clanId)
        assertTrue(switched.isVisible)
        assertTrue(switched.isLoading)
    }

    @Test
    fun `blocking a sender hides only that player's lines`() {
        val state = ClanChatState().loading(clan)
            .withMessage(line("1", 1, sender = "rude"), null, "me")
            .withMessage(line("2", 2, sender = "friend"), null, "me")
            .withMessage(line("3", 3, sender = null, kind = ClanChatMessageKind.SYSTEM), null, "me")
            .withoutSender("rude")
        assertEquals(listOf("2", "3"), state.messages.map { it.id })
    }

    @Test
    fun `chat time shows the clock today and the date otherwise`() {
        val zone = TimeZone.of("Asia/Ho_Chi_Minh")
        val now = 1_795_000_000_000L
        assertEquals(chatTime(now, now, zone).length, 5)
        assertEquals(11, chatTime(now - 3L * 86_400_000L, now, zone).length)
    }
}
