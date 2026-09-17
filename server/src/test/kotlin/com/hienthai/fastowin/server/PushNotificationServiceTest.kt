package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PushNotificationServiceTest {
    @Test
    fun `uses inline Firebase service account JSON for managed hosting`() {
        val json = """{"type":"service_account","project_id":"fast-to-win"}"""

        val source = resolveFirebaseCredentialSource(
            environment = mapOf(
                "FASTTOWIN_FIREBASE_SERVICE_ACCOUNT_JSON" to json,
                "GOOGLE_APPLICATION_CREDENTIALS" to "/run/secrets/firebase.json"
            )
        )

        assertEquals(FirebaseCredentialSource.InlineJson(json), source)
    }

    @Test
    fun `normalizes Vietnamese and English regional tags and falls back to English`() {
        assertEquals(AppLanguage.VIETNAMESE, resolveNotificationLanguage("vi-VN"))
        assertEquals(AppLanguage.ENGLISH, resolveNotificationLanguage("en-US"))
        assertEquals(AppLanguage.ENGLISH, resolveNotificationLanguage("ja-JP"))
        assertEquals(AppLanguage.ENGLISH, resolveNotificationLanguage("zh-CN"))
        assertEquals(AppLanguage.ENGLISH, resolveNotificationLanguage("ar-EG"))
        assertEquals(AppLanguage.ENGLISH, resolveNotificationLanguage(null))
    }

    @Test
    fun `renders Vietnamese push and exposes canonical Web Push language`() {
        val content = renderPushNotification(
            language = resolveNotificationLanguage("vi-VN"),
            content = LocalizedPushContent(
                titleKey = TextKey.PushTournamentInvitationTitle,
                bodyKey = TextKey.PushTournamentInvitationMessage,
                bodyArguments = mapOf("player" to "Hien", "tournament" to "Summer Cup")
            )
        )

        assertEquals("Lời mời giải đấu", content.title)
        assertEquals("Hien đã mời bạn vào giải đấu Summer Cup.", content.body)
        assertEquals("vi", content.languageTag)
    }

    @Test
    fun `renders unsupported locales in English`() {
        val unsupportedChinese = renderPushNotification(
            language = resolveNotificationLanguage("zh-CN"),
            content = LocalizedPushContent(
                titleKey = TextKey.PushRoomInvitationTitle,
                bodyKey = TextKey.PushRoomInvitationMessage,
                bodyArguments = mapOf("player" to "Hien", "room" to "极速房")
            )
        )
        val fallback = renderPushNotification(
            language = resolveNotificationLanguage("ar-EG"),
            content = LocalizedPushContent(
                titleKey = TextKey.PushRoomInvitationTitle,
                bodyKey = TextKey.PushRoomInvitationMessage,
                bodyArguments = mapOf("player" to "Hien", "room" to "Fast room")
            )
        )

        assertEquals("Room invitation", unsupportedChinese.title)
        assertEquals("Hien invited you to room 极速房.", unsupportedChinese.body)
        assertEquals("en", unsupportedChinese.languageTag)
        assertEquals("Room invitation", fallback.title)
        assertEquals("Hien invited you to room Fast room.", fallback.body)
        assertEquals("en", fallback.languageTag)
    }

    @Test
    fun `normalizes destination used by web and ios notifications`() {
        assertEquals("/account/missions", normalizePushDestination("account/missions"))
        assertEquals("/room/abc-123", normalizePushDestination(" /room/abc-123?source=push "))
    }

    @Test
    fun `falls back to notifications for unsafe destination length`() {
        assertEquals("/notifications", normalizePushDestination(""))
        assertEquals("/notifications", normalizePushDestination("/"))
        assertEquals("/notifications", normalizePushDestination("/" + "x".repeat(300)))
    }
}
