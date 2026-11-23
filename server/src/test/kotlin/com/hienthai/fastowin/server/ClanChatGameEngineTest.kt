package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.CLAN_CHAT_MAX_LENGTH
import com.hienthai.fastowin.protocol.CLAN_CHAT_PAGE_SIZE
import com.hienthai.fastowin.protocol.CLAN_LOGO_DAILY_CHANGE_LIMIT
import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatSystemEvent
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.server.avatars.inMemoryClanLogoService
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanChatGameEngineTest {
    @Test
    fun `a line reaches every chat capable member once and the sender gets its request id`() = runTest {
        val f = Fixture()
        f.connectAll()
        val deliveries = f.send(f.member, "Xin chào cả bang")
        val own = deliveries.single { f.member in it.recipients.orEmpty() && it.recipients!!.size == 1 }
        val ownMessage = assertIs<ServerMessage.ClanChatMessageReceived>(own.message)
        assertEquals("req-1", ownMessage.requestId)
        assertEquals("Xin chào cả bang", ownMessage.message.text)
        assertEquals(ClanChatMessageKind.USER, ownMessage.message.kind)
        assertEquals("Member", ownMessage.message.senderName)
        val others = deliveries.single { it !== own }
        assertEquals(setOf(f.leader, f.coLeader), others.recipients)
        assertNull(assertIs<ServerMessage.ClanChatMessageReceived>(others.message).requestId)
    }

    @Test
    fun `old clients never receive chat lines`() = runTest {
        val f = Fixture()
        f.connect(f.leader)
        f.connect(f.member)
        f.connect(f.coLeader, protocolVersion = 45)
        val deliveries = f.send(f.member, "hello")
        assertTrue(deliveries.none { f.coLeader in it.recipients.orEmpty() })
    }

    @Test
    fun `a retried request is stored once`() = runTest {
        val f = Fixture()
        f.connectAll()
        f.send(f.member, "one", requestId = "same")
        val retry = f.send(f.member, "one", requestId = "same")
        assertEquals(1, retry.size)
        assertEquals(1, f.history(f.leader).messages.size)
    }

    @Test
    fun `text is normalized, banned words are masked and invalid text is rejected`() = runTest {
        val f = Fixture()
        f.connectAll()
        val masked = assertIs<ServerMessage.ClanChatMessageReceived>(f.send(f.member, "  đm   các bạn\u0007 ").first().message)
        assertEquals("**   các bạn", masked.message.text)
        listOf("", "   ", "x".repeat(CLAN_CHAT_MAX_LENGTH + 1)).forEach { text ->
            val error = assertIs<ServerMessage.Error>(f.send(f.member, text, requestId = "bad").single().message)
            assertEquals("CLAN_CHAT_INVALID_MESSAGE", error.code)
            assertEquals("bad", error.requestId)
        }
        val outsider = assertIs<ServerMessage.Error>(f.send(f.outsider, "hi").single().message)
        assertEquals("NOT_IN_CLAN", outsider.code)
    }

    @Test
    fun `history pages oldest first with unread counts and hides moderated lines`() = runTest {
        val f = Fixture()
        f.connectAll()
        repeat(CLAN_CHAT_PAGE_SIZE + 5) { index ->
            f.clock += 1_000
            f.send(f.member, "line $index", requestId = "r$index")
        }
        val first = f.history(f.leader)
        assertEquals(CLAN_CHAT_PAGE_SIZE, first.messages.size)
        assertTrue(first.hasMore)
        assertEquals("line 5", first.messages.first().text)
        assertEquals("line ${CLAN_CHAT_PAGE_SIZE + 4}", first.messages.last().text)
        assertEquals(CLAN_CHAT_PAGE_SIZE + 5, first.unreadCount)
        val older = f.history(f.leader, before = first.messages.first().id)
        assertTrue(older.isOlderPage)
        assertFalse(older.hasMore)
        assertEquals((0..4).map { "line $it" }, older.messages.map { it.text })
        assertEquals(0, f.history(f.member).unreadCount)

        f.engine.handle(f.leader, ClientMessage.MarkClanChatRead(f.clan.id, first.messages[10].id))
        assertEquals(CLAN_CHAT_PAGE_SIZE - 11, f.history(f.leader).unreadCount)

        val reported = first.messages.last()
        f.suppress(UgcTargetType.CLAN_MESSAGE, reported.id, com.hienthai.fastowin.server.moderation.contentRevision(reported.text))
        assertTrue(f.history(f.leader).messages.none { it.id == reported.id })
        val removal = f.engine.moderationDeliveries(UgcTargetType.CLAN_MESSAGE, reported.id).single()
        assertEquals(reported.id, assertIs<ServerMessage.ClanChatMessageRemoved>(removal.message).messageId)
    }

    @Test
    fun `senders delete their own lines and moderators delete any line`() = runTest {
        val f = Fixture()
        f.connectAll()
        val first = f.lastId(f.send(f.member, "mine", requestId = "a"))
        val second = f.lastId(f.send(f.leader, "leader line", requestId = "b"))
        assertEquals("CLAN_PERMISSION_DENIED",
            assertIs<ServerMessage.Error>(f.engine.handle(f.member, ClientMessage.DeleteClanChatMessage(f.clan.id, second)).single().message).code)
        val removed = f.engine.handle(f.member, ClientMessage.DeleteClanChatMessage(f.clan.id, first)).single()
        assertEquals(setOf(f.leader, f.coLeader, f.member), removed.recipients)
        assertIs<ServerMessage.ClanChatMessageRemoved>(removed.message)
        assertIs<ServerMessage.ClanChatMessageRemoved>(
            f.engine.handle(f.coLeader, ClientMessage.DeleteClanChatMessage(f.clan.id, second)).single().message)
        assertTrue(f.history(f.leader).messages.isEmpty())
    }

    @Test
    fun `mute rules protect the leader and limit co-leaders to regular members`() = runTest {
        val f = Fixture()
        f.connectAll()
        fun mute(actor: String, target: String, minutes: Int = 60) =
            ClientMessage.MuteClanChatMember(f.clan.id, target, minutes).let { message -> suspend { f.engine.handle(actor, message) } }
        assertEquals("CLAN_PERMISSION_DENIED", f.errorCode(mute(f.member, f.coLeader)()))
        assertEquals("CLAN_PERMISSION_DENIED", f.errorCode(mute(f.coLeader, f.leader)()))
        assertEquals("CLAN_PERMISSION_DENIED", f.errorCode(mute(f.leader, f.leader)()))
        assertEquals("INVALID_REQUEST", f.errorCode(mute(f.leader, f.member, 5)()))
        val muted = mute(f.coLeader, f.member)().single()
        assertEquals(setOf(f.leader, f.coLeader, f.member), muted.recipients)
        assertEquals(f.clock + 3_600_000, assertIs<ServerMessage.ClanChatMuteChanged>(muted.message).mutedUntilEpochMillis)

        val blocked = assertIs<ServerMessage.Error>(f.send(f.member, "hello").single().message)
        assertEquals("CLAN_CHAT_MUTED", blocked.code)
        assertEquals((f.clock + 3_600_000).toString(), blocked.messageArgs["until"])
        assertEquals(f.clock + 3_600_000, f.history(f.member).mutedUntilEpochMillis)
        assertEquals(setOf(f.member), f.history(f.leader).mutedMembers.keys)
        assertTrue(f.history(f.member).mutedMembers.isEmpty())

        f.clock += 3_600_001
        assertIs<ServerMessage.ClanChatMessageReceived>(f.send(f.member, "back", requestId = "later").first().message)
        mute(f.leader, f.coLeader, 24 * 60)()
        mute(f.leader, f.coLeader, 0)()
        assertIs<ServerMessage.ClanChatMessageReceived>(f.send(f.coLeader, "free", requestId = "co").first().message)
    }

    @Test
    fun `blocked players never see each other's lines in either direction`() = runTest {
        val f = Fixture()
        f.connectAll()
        f.send(f.leader, "before block", requestId = "b0")
        f.blocks += f.member to f.leader
        f.clock += 1_000
        val fromLeader = f.send(f.leader, "after block", requestId = "b1")
        assertTrue(fromLeader.none { f.member in it.recipients.orEmpty() })
        f.clock += 1_000
        val fromMember = f.send(f.member, "hidden from leader", requestId = "b2")
        assertTrue(fromMember.none { f.leader in it.recipients.orEmpty() })
        assertTrue(f.history(f.member).messages.none { it.senderId == f.leader })
        assertEquals(0, f.history(f.member).unreadCount)
        assertTrue(f.history(f.leader).messages.none { it.senderId == f.member })
        assertEquals(listOf("before block", "after block", "hidden from leader"), f.history(f.coLeader).messages.map { it.text })
    }

    @Test
    fun `an admin mute from a chat report applies in the line's clan and notifies moderators`() = runTest {
        val f = Fixture()
        f.connectAll()
        val line = f.lastId(f.send(f.member, "rude", requestId = "a1"))
        val until = f.clock + 24 * 3_600_000L
        val delivery = f.engine.adminClanChatMute(line, until).single()
        assertEquals(setOf(f.leader, f.coLeader, f.member), delivery.recipients)
        assertEquals("CLAN_CHAT_MUTED", f.errorCode(f.send(f.member, "again", requestId = "a2")))
        assertTrue(f.engine.adminClanChatMute(UUID.randomUUID().toString(), until).isEmpty())
    }

    @Test
    fun `offline members get at most one chat push per half hour`() = runTest {
        val f = Fixture()
        f.connect(f.member)
        f.send(f.member, "first", requestId = "p1")
        f.clock += 60_000
        f.send(f.member, "second", requestId = "p2")
        assertEquals(1, f.push.sent.count { it.first == "leader-token" })
        assertEquals("Member: first", f.push.sent.first { it.first == "leader-token" }.second)
        f.clock += 30 * 60_000
        f.send(f.member, "third", requestId = "p3")
        assertEquals(2, f.push.sent.count { it.first == "leader-token" })
        assertTrue(f.push.sent.none { it.first == "member-token" })
    }

    @Test
    fun `leader appoints co-leaders and every member sees a system line`() = runTest {
        val f = Fixture()
        f.connectAll()
        assertEquals("NOT_CLAN_OWNER", f.errorCode(f.engine.handle(f.coLeader,
            ClientMessage.SetClanMemberRole(f.clan.id, f.member, ClanRole.CO_LEADER))))
        val appointed = f.engine.handle(f.leader, ClientMessage.SetClanMemberRole(f.clan.id, f.member, ClanRole.CO_LEADER))
        assertEquals(ClanRole.CO_LEADER, f.clans.clan.members.single { it.userId == f.member }.role)
        val line = appointed.map { it.message }.filterIsInstance<ServerMessage.ClanChatMessageReceived>().single().message
        assertEquals(ClanChatSystemEvent.CO_LEADER_APPOINTED, line.systemEvent)
        assertEquals(mapOf("player" to "Member", "playerId" to f.member), line.systemArgs)
        assertTrue(appointed.any { it.message is ServerMessage.ClanInfoData })
        f.clans.roleStatus = ClanRoleUpdateStatus.LIMIT_REACHED
        val limit = assertIs<ServerMessage.Error>(f.engine.handle(f.leader,
            ClientMessage.SetClanMemberRole(f.clan.id, f.coLeader, ClanRole.CO_LEADER)).single().message)
        assertEquals("CLAN_CO_LEADER_LIMIT", limit.code)
        assertEquals("3", limit.messageArgs["limit"])
    }

    @Test
    fun `kick and leave post system lines`() = runTest {
        val f = Fixture()
        f.connectAll()
        f.engine.handle(f.leader, ClientMessage.KickClanMember(f.clan.id, f.member))
        f.clock += 1_000
        f.engine.handle(f.coLeader, ClientMessage.LeaveClan)
        val events = f.history(f.leader).messages.map { it.systemEvent to it.systemArgs["playerId"] }
        assertEquals(listOf(ClanChatSystemEvent.MEMBER_KICKED to f.member, ClanChatSystemEvent.MEMBER_LEFT to f.coLeader), events)
    }

    @Test
    fun `leader and co-leaders upload logos within the daily limit`() = runTest {
        val f = Fixture()
        f.connectAll()
        val image = pngBase64(600, 300)
        assertEquals("CLAN_PERMISSION_DENIED", f.errorCode(f.engine.handle(f.member, ClientMessage.UploadClanLogo(f.clan.id, image))))
        assertEquals("CLAN_LOGO_UPLOAD_FAILED", f.errorCode(f.engine.handle(f.leader, ClientMessage.UploadClanLogo(f.clan.id, "bm90IGFuIGltYWdl"))))
        val uploaded = f.engine.handle(f.coLeader, ClientMessage.UploadClanLogo(f.clan.id, image))
        assertTrue(uploaded.any { it.message is ServerMessage.ClanInfoData })
        assertEquals(ClanChatSystemEvent.LOGO_CHANGED, uploaded.map { it.message }
            .filterIsInstance<ServerMessage.ClanChatMessageReceived>().single().message.systemEvent)
        val revision = assertNotNull(f.engine.clanLogoService.currentRevision(f.clan.id))
        val found = assertIs<com.hienthai.fastowin.server.avatars.AvatarReadResult.Found>(f.engine.clanLogoService.read(f.clan.id))
        val decoded = ImageIO.read(found.bytes.inputStream())
        assertEquals(256, decoded.width)
        assertEquals(256, decoded.height)
        repeat(CLAN_LOGO_DAILY_CHANGE_LIMIT - 1) { f.engine.handle(f.leader, ClientMessage.UploadClanLogo(f.clan.id, image)) }
        val limited = assertIs<ServerMessage.Error>(f.engine.handle(f.leader, ClientMessage.UploadClanLogo(f.clan.id, image)).single().message)
        assertEquals("CLAN_LOGO_DAILY_LIMIT", limited.code)
        assertEquals("5", limited.messageArgs["limit"])
        f.clock += 86_400_001
        f.engine.handle(f.leader, ClientMessage.UploadClanLogo(f.clan.id, image))
        assertTrue(f.engine.clanLogoService.currentRevision(f.clan.id)!!.toLong() > revision.toLong())
    }

    @Test
    fun `choosing a catalog icon replaces the uploaded image`() = runTest {
        val f = Fixture()
        f.connectAll()
        f.engine.handle(f.leader, ClientMessage.UploadClanLogo(f.clan.id, pngBase64(64, 64)))
        f.clans.clan = f.clans.clan.copy(logoRevision = f.engine.clanLogoService.currentRevision(f.clan.id))
        f.engine.handle(f.coLeader, ClientMessage.UpdateClanLogo(f.clan.id, "flag"))
        assertNull(f.engine.clanLogoService.currentRevision(f.clan.id))
        assertEquals("flag", f.clans.clan.logoId)
    }

    private class Fixture {
        var clock = 1_900_000_000_000L
        val leader = UUID.randomUUID().toString()
        val coLeader = UUID.randomUUID().toString()
        val member = UUID.randomUUID().toString()
        val outsider = UUID.randomUUID().toString()
        val names = mapOf(leader to "Leader", coLeader to "CoLeader", member to "Member", outsider to "Outsider")
        val clan = ClanSnapshot(UUID.randomUUID().toString(), "Bang Test", "", leader, listOf(
            ClanMemberSnapshot(leader, "Leader", ClanRole.LEADER, 0),
            ClanMemberSnapshot(coLeader, "CoLeader", ClanRole.CO_LEADER, 0),
            ClanMemberSnapshot(member, "Member", ClanRole.MEMBER, 0)
        ), 0, logoId = "shield")
        val clans = FakeClans(clan)
        val push = RecordingPush()
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun findPushTarget(playerId: String, category: com.hienthai.fastowin.protocol.PushNotificationCategory) =
                PushTarget(fcmToken = "${names[playerId]?.lowercase()}-token", language = com.hienthai.fastowin.localization.AppLanguage.VIETNAMESE)
        }
        val blocks = mutableSetOf<Pair<String, String>>()
        val friends = object : FriendRepository by NoOpFriendRepository {
            override suspend fun blockedEitherWay(userId: String) =
                blocks.mapNotNullTo(mutableSetOf()) { (a, b) -> if (a == userId) b else if (b == userId) a else null }
        }
        val engine = GameEngine(
            friendRepository = friends,
            clanRepository = clans,
            playerProfileRepository = profiles,
            pushNotificationService = push,
            nowMillis = { clock },
            clanLogoService = inMemoryClanLogoService(clans) { clock },
            backgroundRunner = { block -> kotlinx.coroutines.runBlocking { block() } }
        )

        suspend fun connect(id: String, protocolVersion: Int = com.hienthai.fastowin.protocol.PROTOCOL_VERSION) {
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(id), names.getValue(id)), protocolVersion = protocolVersion)
        }
        suspend fun connectAll() { listOf(leader, coLeader, member, outsider).forEach { connect(it) } }

        suspend fun send(from: String, text: String, requestId: String = "req-1") =
            engine.handle(from, ClientMessage.SendClanChatMessage(clan.id, requestId, text))

        suspend fun history(of: String, before: String? = null) =
            assertIs<ServerMessage.ClanChatHistory>(engine.handle(of, ClientMessage.GetClanChat(clan.id, before)).single().message)

        fun lastId(deliveries: List<Delivery>) =
            assertIs<ServerMessage.ClanChatMessageReceived>(deliveries.first().message).message.id

        fun errorCode(deliveries: List<Delivery>) = assertIs<ServerMessage.Error>(deliveries.single().message).code

        suspend fun suppress(type: UgcTargetType, id: String, revision: String) {
            engine.moderationService.repository.transaction {
                putSuppression(com.hienthai.fastowin.server.moderation.ContentSuppression(type, id, revision))
            }
            engine.moderationService.invalidateHotReads()
        }
    }

    private class FakeClans(var clan: ClanSnapshot) : ClanRepository by NoOpClanRepository {
        var roleStatus: ClanRoleUpdateStatus? = null
        override suspend fun getClanById(clanId: String) = clan.takeIf { it.id == clanId }
        override suspend fun getClanByUserId(userId: String) = clan.takeIf { c -> c.members.any { it.userId == userId } }
        override suspend fun presenceMemberIds(userId: String, clanId: String) = clan.members.map { it.userId }
        override suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String): Boolean {
            clan = clan.copy(members = clan.members.filterNot { it.userId == targetUserId }); return true
        }
        override suspend fun leaveClan(userId: String): Boolean {
            clan = clan.copy(members = clan.members.filterNot { it.userId == userId }); return true
        }
        override suspend fun updateLogoId(clanId: String, ownerId: String, logoId: String): ClanLogoUpdateStatus {
            clan = clan.copy(logoId = logoId); return ClanLogoUpdateStatus.UPDATED
        }
        override suspend fun setMemberRole(clanId: String, leaderId: String, memberId: String, role: ClanRole): ClanRoleUpdateStatus {
            roleStatus?.let { return it }
            clan = clan.copy(members = clan.members.map { if (it.userId == memberId) it.copy(role = role) else it })
            return ClanRoleUpdateStatus.UPDATED
        }
    }

    private class RecordingPush : PushNotificationService {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun sendNotification(fcmToken: String, language: com.hienthai.fastowin.localization.AppLanguage,
            content: LocalizedPushContent, destinationPath: String): PushDeliveryStatus {
            sent += fcmToken to "${content.bodyArguments["player"]}: ${content.bodyArguments["message"]}"
            return PushDeliveryStatus.SENT
        }
    }
}

internal fun pngBase64(width: Int, height: Int): String {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, if (x < width / 2) 0xCC3344 else 0x3344CC)
    val output = ByteArrayOutputStream()
    ImageIO.write(image, "png", output)
    return Base64.getEncoder().encodeToString(output.toByteArray())
}
