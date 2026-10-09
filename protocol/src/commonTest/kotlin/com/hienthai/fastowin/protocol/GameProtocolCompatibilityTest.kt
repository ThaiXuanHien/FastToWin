package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GameProtocolCompatibilityTest {
    @Serializable private data class OldNotification(
        val id: String, val kind: NotificationKind, val title: String, val message: String,
        val createdAtEpochMillis: Long, val isRead: Boolean = false,
        val destination: NotificationDestination, val actionData: String? = null,
        val titleKey: String? = null, val titleArgs: Map<String, String> = emptyMap(),
        val messageKey: String? = null, val messageArgs: Map<String, String> = emptyMap()
    )
    @Serializable private sealed class OldServerMessage {
        @Serializable @SerialName("notifications_data")
        data class NotificationsData(val notifications: List<OldNotification>) : OldServerMessage()
    }

    @Test fun `announcement notification preserves old reader and missing context defaults null`() {
        val context = AdminAnnouncementContext(
            "00000000-0000-0000-0000-000000000001", "Tiêu đề", "Title", "Nội dung", "Body",
            AdminAnnouncementCategory.GENERAL, 2, 100, 200, AdminAnnouncementState.ACTIVE,
            AdminAnnouncementAction.NONE
        )
        val notification = NotificationSnapshot(
            "admin-announcement:${context.id}", NotificationKind.COSMETIC, "Tiêu đề", "Nội dung",
            100, destination = NotificationDestination.PROFILE, announcement = context
        )
        val wire = ProtocolJson.encodeToString<ServerMessage>(ServerMessage.NotificationsData(listOf(notification)))
        val oldReader = Json { classDiscriminator = "type"; ignoreUnknownKeys = true }
        val old = oldReader.decodeFromString<OldServerMessage>(wire)
        val oldNotification = (old as OldServerMessage.NotificationsData).notifications.single()
        assertEquals(notification.id, oldNotification.id)
        assertEquals(NotificationKind.COSMETIC, oldNotification.kind)
        assertEquals(NotificationDestination.PROFILE, oldNotification.destination)
        assertEquals("Tiêu đề", oldNotification.title)
        assertEquals("Nội dung", oldNotification.message)
        assertEquals(null, oldNotification.actionData)
        assertEquals(ServerMessage.NotificationsData(listOf(notification)), ProtocolJson.decodeFromString<ServerMessage>(wire))
        val oldPayload = """{"id":"legacy","kind":"COSMETIC","title":"Old","message":"Safe","createdAtEpochMillis":1,"destination":"PROFILE"}"""
        assertEquals(null, ProtocolJson.decodeFromString<NotificationSnapshot>(oldPayload).announcement)
    }
    @Serializable private data class LegacyGame(val roomId: String, val roomName: String, val hostId: String,
        val gameMode: ProtocolGameMode, val phase: RoomPhase, val players: List<PlayerSnapshot>)

    @Test fun `old game reader tolerates frozen rewards and old payload remains neutral`() {
        val game = GameSnapshot("room", roomName = "room", hostId = "host", gameMode = ProtocolGameMode.ORDER,
            phase = RoomPhase.FINISHED, players = emptyList(), eventRewards = MatchRewardEventSnapshot(100, 2, 2),
            rewardBreakdowns = mapOf("host" to MatchRewardBreakdown(100, 100, 200, 30, 30, 60)))
        val old = Json { ignoreUnknownKeys = true }.decodeFromString<LegacyGame>(ProtocolJson.encodeToString(game))
        assertEquals("room", old.roomId); assertEquals(RoomPhase.FINISHED, old.phase)
        val decoded = ProtocolJson.decodeFromString<GameSnapshot>(ProtocolJson.encodeToString(old))
        assertEquals(null, decoded.eventRewards); assertEquals(emptyMap(), decoded.rewardBreakdowns)
        val message: ServerMessage = ServerMessage.GameFinished(game)
        assertEquals(message, ProtocolJson.decodeFromString<ServerMessage>(ProtocolJson.encodeToString(message)))
    }
    @Test
    fun `version 40 connect account decodes without resume token`() {
        val raw = """{"type":"connect_account","accessToken":"access","protocolVersion":40}"""
        val decoded = ProtocolJson.decodeFromString<ClientMessage>(raw)
        assertEquals(ClientMessage.ConnectAccount("access", null, 40), decoded)
    }

    @Test
    fun `version 40 session ready decodes without resume token`() {
        val raw = """{"type":"session_ready","playerId":"player-1","protocolVersion":40}"""
        val decoded = ProtocolJson.decodeFromString<ServerMessage>(raw)
        assertEquals(ServerMessage.SessionReady("player-1", null, null, 40), decoded)
    }

    @Test
    fun `play quota message preserves authoritative values`() {
        val message: ServerMessage = ServerMessage.PlayQuotaData(
            PlayQuotaSnapshot(
                quotaDate = "2026-09-12",
                baseMatches = 10,
                matchesConsumed = 8,
                bonusMatchesGranted = 2,
                remainingMatches = 4,
                nextResetAtEpochMillis = 1_789_148_400_000,
                rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
            )
        )

        val encoded = ProtocolJson.encodeToString<ServerMessage>(message)

        assertEquals(message, ProtocolJson.decodeFromString<ServerMessage>(encoded))
    }

    @Test
    fun `rewarded ad claim preserves idempotency identifiers`() {
        val message: ClientMessage = ClientMessage.ClaimRewardedAdBonus(
            requestId = "request-1",
            provider = RewardedAdProvider.DEV_SIMULATED,
            providerTransactionId = "dev-transaction-1",
            proof = "FASTTOWIN_DEV_REWARDED_V1:user-1:dev-transaction-1"
        )

        val encoded = ProtocolJson.encodeToString<ClientMessage>(message)

        assertEquals(message, ProtocolJson.decodeFromString<ClientMessage>(encoded))
    }

    @Test
    fun `legacy tournament snapshot defaults to private`() {
        val decoded = ProtocolJson.decodeFromString<TournamentSnapshot>(
            """{"tournamentId":"00000000-0000-0000-0000-000000000001","name":"Legacy","hostPlayerId":"00000000-0000-0000-0000-000000000002","gameMode":"ORDER","phase":"LOBBY","createdAtEpochMillis":1}"""
        )

        assertEquals(TournamentVisibility.PRIVATE, decoded.visibility)
    }

    @Test
    fun `create tournament payload without visibility defaults to public`() {
        val decoded = ProtocolJson.decodeFromString<ClientMessage>(
            """{"type":"create_tournament","name":"Cup","gameMode":"ORDER"}"""
        )

        assertEquals(
            ClientMessage.CreateTournament("Cup", ProtocolGameMode.ORDER, visibility = TournamentVisibility.PUBLIC),
            decoded
        )
    }

    @Test
    fun `public tournament query and response round trip`() {
        val query: ClientMessage = ClientMessage.GetPublicTournaments(
            PublicTournamentQuery("cup", ProtocolGameMode.ORDER, 8, TournamentFeeFilter.PAID)
        )
        assertEquals(query, ProtocolJson.decodeFromString<ClientMessage>(ProtocolJson.encodeToString(query)))

        val join: ClientMessage = ClientMessage.JoinPublicTournament("00000000-0000-0000-0000-000000000001")
        assertEquals(join, ProtocolJson.decodeFromString<ClientMessage>(ProtocolJson.encodeToString(join)))

        val response: ServerMessage = ServerMessage.PublicTournamentsData(
            listOf(PublicTournamentSummary(
                tournamentId = "00000000-0000-0000-0000-000000000001",
                name = "Cup",
                hostPlayerId = "00000000-0000-0000-0000-000000000002",
                hostDisplayName = "Host",
                gameMode = ProtocolGameMode.ORDER,
                playerCount = 2,
                maxPlayers = 4,
                entryFee = 100,
                prizePool = 200,
                createdAtEpochMillis = 1L,
            ))
        )
        assertEquals(response, ProtocolJson.decodeFromString<ServerMessage>(ProtocolJson.encodeToString(response)))

        val invalidated: ServerMessage = ServerMessage.PublicTournamentsInvalidated
        assertEquals(invalidated, ProtocolJson.decodeFromString<ServerMessage>(ProtocolJson.encodeToString(invalidated)))
    }
}
