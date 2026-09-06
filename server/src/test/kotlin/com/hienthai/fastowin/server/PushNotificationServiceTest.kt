package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PushNotificationServiceTest {
    @Test
    fun `normalizes supported regional tags and falls back to English`() {
        assertEquals(AppLanguage.JAPANESE, resolveNotificationLanguage("ja-JP"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, resolveNotificationLanguage("zh-CN"))
        assertEquals(AppLanguage.ENGLISH, resolveNotificationLanguage("ar-EG"))
        assertEquals(AppLanguage.ENGLISH, resolveNotificationLanguage(null))
    }

    @Test
    fun `renders Japanese push and exposes canonical Web Push language`() {
        val content = renderPushNotification(
            language = resolveNotificationLanguage("ja-JP"),
            content = LocalizedPushContent(
                titleKey = TextKey.PushTournamentInvitationTitle,
                bodyKey = TextKey.PushTournamentInvitationMessage,
                bodyArguments = mapOf("player" to "Hien", "tournament" to "Summer Cup")
            )
        )

        assertEquals("大会への招待", content.title)
        assertEquals("Hienさんが大会「Summer Cup」に招待しました。", content.body)
        assertEquals("ja", content.languageTag)
    }

    @Test
    fun `renders Simplified Chinese and unsupported locale falls back to English`() {
        val chinese = renderPushNotification(
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

        assertEquals("房间邀请", chinese.title)
        assertEquals("Hien 邀请你加入房间“极速房”。", chinese.body)
        assertEquals("zh-Hans", chinese.languageTag)
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
