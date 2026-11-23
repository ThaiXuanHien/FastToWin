package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatMessageSnapshot
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.protocol.normalizeClanChatText
import com.hienthai.fastowin.server.moderation.ContentSuppression
import com.hienthai.fastowin.server.moderation.contentRevision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanChatFilterTest {
    @Test
    fun `masks whole banned words and phrases but never accent variants of ordinary words`() {
        assertEquals("****, ** *** lắm", ClanChatFilter.mask("FUCK, óc chó lắm"))
        assertEquals("các bạn ngủ ngon, buổi tối vui", ClanChatFilter.mask("các bạn ngủ ngon, buổi tối vui"))
        assertEquals("shitake? ***!", ClanChatFilter.mask("shitake? vcl!"))
        // Decomposed tone marks (some keyboards) are matched after NFC normalization.
        assertEquals("****", ClanChatFilter.mask("đe\u0301o"))
    }

    @Test
    fun `normalization strips controls and limits line breaks`() {
        assertEquals("a b\nc", normalizeClanChatText("  a\tb\r\nc\u0000  "))
        assertEquals("1\n2\n3\n4\n5\n6 7", normalizeClanChatText("1\n2\n3\n4\n5\n6\n7"))
    }

    @Test
    fun `projection hides suppressed uploaded logos and neutralizes moderated sender names`() {
        val clan = ClanSnapshot("00000000-0000-0000-0000-000000000001", "Clan", "", "00000000-0000-0000-0000-000000000002",
            emptyList(), 0, logoRevision = "7")
        val logo = ContentSuppression(UgcTargetType.CLAN_LOGO, clan.id, "7")
        val hidden = sanitizeContent(ServerMessage.ClanInfoData(clan), listOf(logo)) as ServerMessage.ClanInfoData
        assertNull(hidden.clan.logoRevision)
        val newer = sanitizeContent(ServerMessage.ClanInfoData(clan.copy(logoRevision = "8")), listOf(logo)) as ServerMessage.ClanInfoData
        assertEquals("8", newer.clan.logoRevision)

        val sender = "00000000-0000-0000-0000-000000000003"
        val line = ClanChatMessageSnapshot("00000000-0000-0000-0000-000000000004", clan.id, ClanChatMessageKind.USER, 1,
            sender, "Rude name", "hi")
        val name = ContentSuppression(UgcTargetType.PLAYER_PROFILE, sender, contentRevision("Rude name"))
        val projected = sanitizeContent(ServerMessage.ClanChatMessageReceived(clan.id, line), listOf(name)) as ServerMessage.ClanChatMessageReceived
        assertEquals("Player", projected.message.senderName)
    }

    @Test
    fun `new moderation targets are withheld from clients older than chat`() {
        assertFalse(ServerMessage.ContentModerationChanged(UgcTargetType.CLAN_MESSAGE, "x").readableBy(45))
        assertTrue(ServerMessage.ContentModerationChanged(UgcTargetType.CLAN_MESSAGE, "x").readableBy(46))
        assertTrue(ServerMessage.ContentModerationChanged(UgcTargetType.CLAN, "x").readableBy(45))
        assertTrue(ServerMessage.ClanChatMessageRemoved("c", "m").requiresClanChatClient())
        assertFalse(ServerMessage.ContentModerationChanged(UgcTargetType.CLAN, "x").requiresClanChatClient())
    }
}
