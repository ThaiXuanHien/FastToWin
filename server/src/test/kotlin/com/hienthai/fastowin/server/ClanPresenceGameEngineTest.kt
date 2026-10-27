package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanJoinRequestSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClanPresenceGameEngineTest {
    @Test
    fun `clan info derives member presence from sessions and preserves public appearance`() = runTest {
        val owner = UUID.randomUUID().toString()
        val offline = UUID.randomUUID().toString()
        val clans = MemoryClans(owner, offline)
        clans.snapshot = clans.snapshot.copy(members = clans.snapshot.members.map {
            it.copy(playerCode = "CODE${it.userId.take(4)}", avatarId = "avatar_rocket", frameId = "frame_gold",
                presence = FriendPresence.PLAYING)
        })
        val engine = GameEngine(clanRepository = clans)
        engine.connectAccount(account(owner))

        val message = engine.handle(owner, ClientMessage.GetClanInfo(clans.snapshot.id))
            .map(Delivery::message).filterIsInstance<ServerMessage.ClanInfoData>().single()

        assertFalse(message.isPresenceRefresh)
        assertEquals(mapOf(owner to FriendPresence.ONLINE, offline to FriendPresence.OFFLINE),
            message.clan.members.associate { it.userId to it.presence })
        assertEquals("avatar_rocket", message.clan.members.first().avatarId)
        assertEquals("frame_gold", message.clan.members.first().frameId)
        assertEquals("CODE${owner.take(4)}", message.clan.members.first().playerCode)
    }

    @Test
    fun `room and disconnect transitions broadcast authoritative clan presence`() = runTest {
        val owner = UUID.randomUUID().toString()
        val member = UUID.randomUUID().toString()
        val clans = MemoryClans(owner, member)
        val engine = GameEngine(clanRepository = clans)
        engine.connectAccount(account(owner))
        engine.connectAccount(account(member))

        val created = engine.handle(owner, ClientMessage.CreateRoom("Clan room", "", ProtocolGameMode.ORDER))
        val room = created.map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        assertEquals(FriendPresence.IN_ROOM, created.clanPresence(owner))
        engine.handle(member, ClientMessage.JoinRoom(room.roomId, ""))
        engine.handle(owner, ClientMessage.SetReady(room.roomId, true))
        val started = engine.handle(member, ClientMessage.SetReady(room.roomId, true))
        assertEquals(FriendPresence.PLAYING, started.clanPresence(owner))
        assertEquals(FriendPresence.PLAYING, started.clanPresence(member))

        val disconnected = engine.markDisconnected(owner)
        assertEquals(FriendPresence.OFFLINE, disconnected.clanPresence(owner))
        assertEquals(setOf(member), disconnected.clanDeliveries().single().recipients)

        engine.connectAccount(account(owner))
        assertEquals(FriendPresence.PLAYING, engine.presenceUpdates(owner).clanPresence(owner))
    }

    @Test
    fun `presence recipients follow current clan membership and exclude disconnected outsiders`() = runTest {
        val owner = UUID.randomUUID().toString()
        val kicked = UUID.randomUUID().toString()
        val outsider = UUID.randomUUID().toString()
        val offline = UUID.randomUUID().toString()
        val clans = MemoryClans(owner, kicked, offline)
        val engine = GameEngine(clanRepository = clans)
        listOf(owner, kicked, outsider).forEach { engine.connectAccount(account(it)) }
        engine.presenceUpdates(owner)
        clans.snapshot = clans.snapshot.copy(members = clans.snapshot.members.filter { it.userId != kicked })

        val update = engine.presenceUpdates(owner).clanDeliveries().single()

        assertEquals(setOf(owner), update.recipients)
        assertEquals(setOf(owner, offline), (update.message as ServerMessage.ClanInfoData).clan.members.map { it.userId }.toSet())
        assertTrue(engine.presenceUpdates(kicked).clanDeliveries().isEmpty())
        assertTrue(engine.presenceUpdates(outsider).clanDeliveries().isEmpty())
    }

    @Test
    fun `clan presence survives unavailable friend repository`() = runTest {
        val owner = UUID.randomUUID().toString()
        val member = UUID.randomUUID().toString()
        val friends = object : FriendRepository by NoOpFriendRepository {
            override suspend fun load(userId: String): StoredFriends = error("friends unavailable")
        }
        val engine = GameEngine(clanRepository = MemoryClans(owner, member), friendRepository = friends)
        engine.connectAccount(account(owner))
        engine.connectAccount(account(member))

        val updates = engine.presenceUpdates(owner)

        assertEquals(FriendPresence.ONLINE, updates.clanPresence(owner))
        assertEquals(setOf(owner, member), updates.clanDeliveries().single().recipients)
    }

    @Test
    fun `friend presence survives unavailable clan repository`() = runTest {
        val owner = UUID.randomUUID().toString()
        val friend = UUID.randomUUID().toString()
        val clans = object : ClanRepository by NoOpClanRepository {
            override suspend fun getClanByUserId(userId: String): ClanSnapshot? = error("clans unavailable")
        }
        val friends = object : FriendRepository by NoOpFriendRepository {
            override suspend fun load(userId: String) = StoredFriends(friends = listOf(
                FriendSnapshot(if (userId == owner) friend else owner, "Friend", "FRIEND1")
            ))
        }
        val engine = GameEngine(clanRepository = clans, friendRepository = friends)
        engine.connectAccount(account(owner))
        engine.connectAccount(account(friend))

        val updates = engine.markDisconnected(owner)
        val friendUpdate = updates.single { it.message is ServerMessage.FriendsData && it.recipients == setOf(friend) }

        assertEquals(FriendPresence.OFFLINE, (friendUpdate.message as ServerMessage.FriendsData).social.friends.single().presence)
        assertTrue(updates.clanDeliveries().isEmpty())
    }

    @Test
    fun `offline member can refresh presence after session cleanup`() = runTest {
        var now = 1_900_000_000_000L
        val owner = UUID.randomUUID().toString()
        val member = UUID.randomUUID().toString()
        val engine = GameEngine(clanRepository = MemoryClans(owner, member), nowMillis = { now })
        engine.connectAccount(account(owner))
        engine.connectAccount(account(member))
        engine.markDisconnected(owner)
        now += 6 * 60_000L
        engine.cleanupExpiredSessions()

        val updates = engine.presenceUpdates(owner)

        assertEquals(FriendPresence.OFFLINE, updates.clanPresence(owner))
        assertEquals(setOf(member), updates.clanDeliveries().single().recipients)
    }

    @Test
    fun `kick refreshes roster for connected remaining members`() = runTest {
        val owner = UUID.randomUUID().toString()
        val kicked = UUID.randomUUID().toString()
        val peer = UUID.randomUUID().toString()
        val offline = UUID.randomUUID().toString()
        val clans = MemoryClans(owner, kicked, peer, offline)
        val engine = GameEngine(clanRepository = clans)
        listOf(owner, kicked, peer).forEach { engine.connectAccount(account(it)) }

        val delivery = engine.handle(owner, ClientMessage.KickClanMember(clans.snapshot.id, kicked))
            .single { it.message is ServerMessage.ClanInfoData }

        assertEquals(setOf(owner, peer), delivery.recipients)
        val message = delivery.message as ServerMessage.ClanInfoData
        assertFalse(message.isPresenceRefresh)
        assertEquals(setOf(owner, peer, offline), message.clan.members.map { it.userId }.toSet())
        assertEquals(FriendPresence.ONLINE, message.clan.members.single { it.userId == peer }.presence)
    }

    @Test
    fun `leave refreshes roster for connected remaining members`() = runTest {
        val owner = UUID.randomUUID().toString()
        val leaving = UUID.randomUUID().toString()
        val peer = UUID.randomUUID().toString()
        val clans = MemoryClans(owner, leaving, peer)
        val engine = GameEngine(clanRepository = clans)
        listOf(owner, leaving, peer).forEach { engine.connectAccount(account(it)) }

        val delivery = engine.handle(leaving, ClientMessage.LeaveClan)
            .single { it.message is ServerMessage.ClanInfoData }

        assertEquals(setOf(owner, peer), delivery.recipients)
        val message = delivery.message as ServerMessage.ClanInfoData
        assertFalse(message.isPresenceRefresh)
        assertEquals(setOf(owner, peer), message.clan.members.map { it.userId }.toSet())
    }

    @Test
    fun `approval refreshes roster for all connected current members including applicant`() = runTest {
        val owner = UUID.randomUUID().toString()
        val peer = UUID.randomUUID().toString()
        val offline = UUID.randomUUID().toString()
        val applicant = UUID.randomUUID().toString()
        val outsider = UUID.randomUUID().toString()
        val clans = MemoryClans(owner, peer, offline)
        clans.snapshot = clans.snapshot.copy(joinRequests = listOf(
            ClanJoinRequestSnapshot(applicant, "Applicant", "APPLICANT", 1_000)
        ))
        val engine = GameEngine(clanRepository = clans, playerProfileRepository = profilesFor(clans))
        listOf(owner, peer, applicant, outsider).forEach { engine.connectAccount(account(it)) }

        val updates = engine.handle(owner,
            ClientMessage.RespondClanJoinRequest(clans.snapshot.id, applicant, accept = true))
        val delivery = updates.single { it.message is ServerMessage.ClanInfoData }

        assertEquals(setOf(owner, peer, applicant), delivery.recipients)
        val message = delivery.message as ServerMessage.ClanInfoData
        assertFalse(message.isPresenceRefresh)
        assertEquals(setOf(owner, peer, offline, applicant), message.clan.members.map { it.userId }.toSet())
        assertEquals(FriendPresence.ONLINE, message.clan.members.single { it.userId == applicant }.presence)
        assertTrue(message.clan.joinRequests.isEmpty())
    }

    @Test
    fun `kicked member receives own profile with cleared clan membership`() = runTest {
        val owner = UUID.randomUUID().toString()
        val kicked = UUID.randomUUID().toString()
        val clans = MemoryClans(owner, kicked)
        val engine = GameEngine(clanRepository = clans, playerProfileRepository = profilesFor(clans))
        listOf(owner, kicked).forEach { engine.connectAccount(account(it)) }

        val updates = engine.handle(owner, ClientMessage.KickClanMember(clans.snapshot.id, kicked))

        val profileDelivery = updates.single { it.message is ServerMessage.ProfileData }
        assertEquals(setOf(kicked), profileDelivery.recipients)
        val profile = (profileDelivery.message as ServerMessage.ProfileData).profile
        assertEquals(kicked, profile.userId)
        assertEquals(null, profile.clanId)
        assertEquals(null, profile.clanName)
        assertTrue(updates.filter { it.message is ServerMessage.ClanInfoData }
            .none { kicked in it.recipients.orEmpty() })
    }

    private fun account(id: String) = AuthenticatedAccount(UUID.fromString(id), "Member")

    private fun profilesFor(clans: MemoryClans) = object : PlayerProfileRepository {
        override suspend fun findByPlayerId(playerId: String) = PlayerProfileSnapshot(
            userId = playerId, displayName = "Member", playerCode = playerId.take(8),
            clanId = clans.snapshot.id.takeIf { clans.snapshot.members.any { it.userId == playerId } },
            clanName = clans.snapshot.name.takeIf { clans.snapshot.members.any { it.userId == playerId } }
        )
        override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
    }

    private fun List<Delivery>.clanDeliveries() = filter {
        (it.message as? ServerMessage.ClanInfoData)?.isPresenceRefresh == true
    }

    private fun List<Delivery>.clanPresence(userId: String): FriendPresence = clanDeliveries()
        .map { (it.message as ServerMessage.ClanInfoData).clan.members.single { member -> member.userId == userId }.presence }
        .distinct().single()

    private class MemoryClans(owner: String, vararg members: String) : ClanRepository by NoOpClanRepository {
        var snapshot = ClanSnapshot(
            id = UUID.randomUUID().toString(), name = "Clan", description = "", ownerId = owner,
            members = (listOf(owner) + members).map { ClanMemberSnapshot(it, "Member", ClanRole.MEMBER, 1_000) },
            trophies = 0
        )

        override suspend fun getClanById(clanId: String) = snapshot.takeIf { it.id == clanId }
        override suspend fun getClanByUserId(userId: String) = snapshot.takeIf { clan -> clan.members.any { it.userId == userId } }
        override suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String): Boolean {
            if (clanId != snapshot.id || currentUserId != snapshot.ownerId) return false
            return leaveClan(targetUserId)
        }
        override suspend fun leaveClan(userId: String): Boolean {
            if (snapshot.members.none { it.userId == userId }) return false
            snapshot = snapshot.copy(members = snapshot.members.filter { it.userId != userId })
            return true
        }
        override suspend fun respondJoinRequest(clanId: String, ownerId: String, userId: String, accept: Boolean): ClanJoinResponseResult {
            if (clanId != snapshot.id || ownerId != snapshot.ownerId || snapshot.joinRequests.none { it.userId == userId }) {
                return ClanJoinResponseResult.REQUEST_NOT_FOUND
            }
            snapshot = snapshot.copy(
                joinRequests = snapshot.joinRequests.filter { it.userId != userId },
                members = if (accept) snapshot.members + ClanMemberSnapshot(userId, "Applicant", ClanRole.MEMBER, 1_000)
                    else snapshot.members
            )
            return if (accept) ClanJoinResponseResult.APPROVED else ClanJoinResponseResult.REJECTED
        }
    }
}
