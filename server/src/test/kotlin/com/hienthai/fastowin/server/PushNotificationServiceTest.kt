package com.hienthai.fastowin.server

import kotlin.test.Test
import kotlin.test.assertEquals

class PushNotificationServiceTest {
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
