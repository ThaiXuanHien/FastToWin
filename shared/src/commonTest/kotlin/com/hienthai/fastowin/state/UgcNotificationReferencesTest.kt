package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.protocol.*
import kotlin.test.*

class UgcNotificationReferencesTest {
    private val playerId = "11111111-1111-4111-8111-111111111111"
    private val roomId = "22222222-2222-4222-8222-222222222222"
    private val clanId = "33333333-3333-4333-8333-333333333333"

    @Test fun `notifications report only the exact typed IDs supplied alongside the displayed text`() {
        val notification = notification(mapOf("playerId" to playerId, "player" to "<b>Player</b>",
            "roomId" to roomId, "room" to "Room", "clanId" to clanId, "clan" to "Clan"))
        assertEquals(listOf(UgcTargetType.PLAYER_PROFILE to playerId, UgcTargetType.ROOM to roomId,
            UgcTargetType.CLAN to clanId), notification.reportReferences().map { it.type to it.id })
        assertEquals(listOf("<b>Player</b>", "Room", "Clan"), notification.reportReferences().map { it.label })
    }
    @Test fun `legacy or malformed metadata never guesses an identity from names or notification IDs`() {
        val legacy = notification(mapOf("player" to playerId, "room" to roomId)).copy(id = "friend:$playerId", actionData = clanId)
        assertTrue(legacy.reportReferences().isEmpty())
        val malformed = notification(mapOf("playerId" to "$playerId/extra", "roomId" to "unknown", "clanId" to ""))
        assertTrue(malformed.reportReferences().isEmpty())
    }
    @Test fun `official notices never become player reports through incidental metadata`() {
        val notice = notification(mapOf("playerId" to playerId, "player" to "Player")).copy(id = "admin-announcement:$roomId")
        assertTrue(notice.reportReferences().isEmpty())
    }
    @Test fun `locally generated live invitation retains exact report IDs through language changes`() {
        val invitation = ServerMessage.RoomInvitation("invite", playerId, "Player", roomId, "Room", 100)
        val generated = roomInvitationNotification(invitation, 1, LocalizationService(AppLanguage.VIETNAMESE))
        val english = generated.relocalized(LocalizationService(AppLanguage.ENGLISH))
        assertEquals(listOf(UgcTargetType.PLAYER_PROFILE to playerId, UgcTargetType.ROOM to roomId),
            english.reportReferences().map { it.type to it.id })
    }
    private fun notification(args: Map<String, String>) = AppNotification("notification", AppNotificationKind.ROOM_INVITATION,
        "Invitation", "Player invited you to Room", 1, destination = AppNotificationDestination.FRIENDS, messageArgs = args)
}

