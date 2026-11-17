package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.server.moderation.ContentSuppression
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PushNotificationCategory
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.server.moderation.InMemoryModerationRepository
import com.hienthai.fastowin.server.moderation.ModerationService
import com.hienthai.fastowin.server.moderation.contentRevision
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendActivityPushTest {
    private val alex = UUID.randomUUID().toString()
    private val bao = UUID.randomUUID().toString()

    private class RecordedPush(val playerToken: String, val content: LocalizedPushContent, val path: String)

    @Test
    fun `friend request and acceptance push the other player with moderated names`() = runTest {
        val moderation = InMemoryModerationRepository()
        val sent = mutableListOf<RecordedPush>()
        val categories = mutableListOf<PushNotificationCategory>()
        val engine = engine(moderation, sent, categories, friendActivityOn = true)

        engine.handle(alex, ClientMessage.SendFriendRequest("BAO123"))
        engine.handle(bao, ClientMessage.RespondFriendRequest("request-1", accept = true))

        assertEquals(listOf("token-$bao", "token-$alex"), sent.map { it.playerToken })
        assertEquals(TextKey.PushFriendRequestTitle, sent[0].content.titleKey)
        assertEquals(mapOf("player" to "Alex"), sent[0].content.bodyArguments)
        assertEquals(TextKey.PushFriendAcceptedTitle, sent[1].content.titleKey)
        assertEquals(mapOf("player" to "Bao"), sent[1].content.bodyArguments)
        assertTrue(sent.all { it.path == "/friends" })
        assertTrue(categories.all { it == PushNotificationCategory.FRIEND_ACTIVITY })

        // A removed nickname never leaves the app in a push.
        moderation.transaction { putSuppression(ContentSuppression(UgcTargetType.PLAYER_PROFILE, alex, contentRevision("Alex"))) }
        engine.handle(alex, ClientMessage.SendFriendRequest("BAO123"))
        assertEquals(mapOf("player" to "Player"), sent.last().content.bodyArguments)
    }

    @Test
    fun `players who turned friend activity off get no push`() = runTest {
        val sent = mutableListOf<RecordedPush>()
        val engine = engine(InMemoryModerationRepository(), sent, mutableListOf(), friendActivityOn = false)

        engine.handle(alex, ClientMessage.SendFriendRequest("BAO123"))
        engine.handle(bao, ClientMessage.RespondFriendRequest("request-1", accept = true))

        assertTrue(sent.isEmpty())
    }

    private suspend fun engine(
        moderation: InMemoryModerationRepository,
        sent: MutableList<RecordedPush>,
        categories: MutableList<PushNotificationCategory>,
        friendActivityOn: Boolean
    ): GameEngine {
        val names = mapOf(alex to "Alex", bao to "Bao")
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) =
                names[playerId]?.let { PlayerProfileSnapshot(playerId, it, "code") }
            override suspend fun findPushTarget(playerId: String, category: PushNotificationCategory): PushTarget? {
                categories += category
                return PushTarget("token-$playerId", AppLanguage.ENGLISH).takeIf { friendActivityOn }
            }
        }
        val friends = object : FriendRepository by NoOpFriendRepository {
            override suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long) =
                FriendRequestResult.Success(bao)
            override suspend fun load(userId: String) = if (userId == bao) StoredFriends(
                incomingRequests = listOf(FriendRequestSnapshot("request-1", alex, "Alex", "ALEX12"))
            ) else StoredFriends()
            override suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long) =
                FriendResponseResult.Success(alex)
        }
        val push = object : PushNotificationService {
            override suspend fun sendNotification(
                fcmToken: String, language: AppLanguage, content: LocalizedPushContent, destinationPath: String
            ): PushDeliveryStatus {
                sent += RecordedPush(fcmToken, content, destinationPath)
                return PushDeliveryStatus.SENT
            }
        }
        return GameEngine(
            playerProfileRepository = profiles,
            friendRepository = friends,
            pushNotificationService = push,
            moderationService = ModerationService(moderation)
        ).also { engine ->
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(alex), "Alex"))
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(bao), "Bao"))
        }
    }
}
