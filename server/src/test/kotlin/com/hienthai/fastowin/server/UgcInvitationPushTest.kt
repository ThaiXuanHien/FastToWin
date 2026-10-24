package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class UgcInvitationPushTest {
    @Test fun `fresh room push neutralizes exact removed player and room`() = runTest {
        invitationPush(UgcTargetType.ROOM, "exact", "Player", "Room")
    }
    @Test fun `fresh tournament push neutralizes exact removed player and tournament`() = runTest {
        invitationPush(UgcTargetType.TOURNAMENT, "exact", "Player", "Tournament")
    }
    @Test fun `room push preserves equal names belonging to unrelated targets`() = runTest {
        invitationPush(UgcTargetType.ROOM, "unrelated", "Alex", "Arena")
    }
    @Test fun `tournament push preserves equal names belonging to unrelated targets`() = runTest {
        invitationPush(UgcTargetType.TOURNAMENT, "unrelated", "Alex", "Arena")
    }
    @Test fun `room push preserves changed revisions of removed targets`() = runTest {
        invitationPush(UgcTargetType.ROOM, "changed", "Alex", "Arena")
    }
    @Test fun `tournament push preserves changed revisions of removed targets`() = runTest {
        invitationPush(UgcTargetType.TOURNAMENT, "changed", "Alex", "Arena")
    }

    private suspend fun invitationPush(type: UgcTargetType, scenario: String, expectedPlayer: String, expectedName: String) {
        val host = UUID.randomUUID().toString()
        val friend = UUID.randomUUID().toString()
        val repository = InMemoryModerationRepository()
        val sent = mutableListOf<LocalizedPushContent>()
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) =
                PlayerProfileSnapshot(playerId, if (playerId == host) "Alex" else "Friend", "code",
                    progression = PlayerProgressionSnapshot(level = 20))
            override suspend fun findPushTarget(playerId: String, category: PushNotificationCategory) =
                PushTarget("test-token", AppLanguage.ENGLISH).takeIf { playerId == friend }
        }
        val push = object : PushNotificationService {
            override suspend fun sendNotification(fcmToken: String, language: AppLanguage,
                content: LocalizedPushContent, destinationPath: String): PushDeliveryStatus {
                assertEquals("test-token", fcmToken)
                assertEquals(AppLanguage.ENGLISH, language)
                assertEquals(if (type == UgcTargetType.ROOM) "/friends" else "/tournament", destinationPath)
                sent += content
                return PushDeliveryStatus.SENT
            }
        }
        val friends = object : FriendRepository by NoOpFriendRepository {
            override suspend fun areFriends(firstUserId: String, secondUserId: String) =
                firstUserId == host && secondUserId == friend
        }
        val engine = GameEngine(playerProfileRepository = profiles, friendRepository = friends,
            pushNotificationService = push, moderationService = ModerationService(repository))
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(host), "Alex"))
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(friend), "Friend"))
        val target = if (type == UgcTargetType.ROOM) {
            engine.handle(host, ClientMessage.CreateRoom("Arena", "1234", ProtocolGameMode.ORDER))
                .map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        } else {
            engine.handle(host, ClientMessage.CreateTournament("Arena", ProtocolGameMode.ORDER,
                visibility = TournamentVisibility.PRIVATE))
                .map(Delivery::message).filterIsInstance<ServerMessage.TournamentUpdated>().single().tournament.tournamentId
        }
        suspend fun invite() {
            val messages = engine.handle(host, if (type == UgcTargetType.ROOM) ClientMessage.InviteFriend(friend, target)
                else ClientMessage.InviteTournamentPlayer(target, friend)).map(Delivery::message)
            assertTrue(messages.none { it is ServerMessage.Error }, messages.toString())
        }
        val nameKey = if (type == UgcTargetType.ROOM) "room" else "tournament"
        invite()
        assertEquals(mapOf("player" to "Alex", nameKey to "Arena"), sent.single().bodyArguments)
        repository.transaction {
            putSuppression(ContentSuppression(UgcTargetType.PLAYER_PROFILE,
                if (scenario == "unrelated") UUID.randomUUID().toString() else host,
                contentRevision(if (scenario == "changed") "Old Alex" else "Alex")))
            putSuppression(ContentSuppression(type,
                if (scenario == "unrelated") UUID.randomUUID().toString() else target,
                contentRevision(if (scenario == "changed") "Old Arena" else "Arena")))
        }

        invite()

        assertEquals(2, sent.size)
        assertEquals(mapOf("player" to expectedPlayer, nameKey to expectedName), sent.last().bodyArguments)
        // Previously delivered content is immutable; only the fresh delivery is projected.
        assertEquals("Alex", sent.first().bodyArguments["player"])
        assertEquals("Arena", sent.first().bodyArguments[nameKey])
        for (language in AppLanguage.entries) {
            val rendered = renderPushNotification(language, sent.last())
            assertTrue(rendered.body.contains(expectedPlayer))
            assertTrue(rendered.body.contains(expectedName))
        }
    }
}