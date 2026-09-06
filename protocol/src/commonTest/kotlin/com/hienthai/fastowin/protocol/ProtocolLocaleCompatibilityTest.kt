package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtocolLocaleCompatibilityTest {
    @Test
    fun `push token message decodes locale and legacy payload`() {
        val localized = ProtocolJson.decodeFromString<ClientMessage>(
            """{"type":"update_fcm_token","token":"token-ja","languageTag":"ja-JP"}"""
        ) as ClientMessage.UpdateFcmToken
        val legacy = ProtocolJson.decodeFromString<ClientMessage>(
            """{"type":"update_fcm_token","token":"token-legacy"}"""
        ) as ClientMessage.UpdateFcmToken

        assertEquals("ja-JP", localized.languageTag)
        assertNull(legacy.languageTag)
    }

    @Test
    fun `account email requests decode locale and legacy payload`() {
        val reset = ProtocolJson.decodeFromString<PasswordResetRequest>(
            """{"email":"player@example.com","languageTag":"zh-CN"}"""
        )
        val legacyReset = ProtocolJson.decodeFromString<PasswordResetRequest>(
            """{"email":"legacy@example.com"}"""
        )
        val verification = ProtocolJson.decodeFromString<EmailVerificationRequest>(
            """{"accessToken":"access-token","languageTag":"ja-JP"}"""
        )
        val legacyVerification = ProtocolJson.decodeFromString<EmailVerificationRequest>(
            """{"accessToken":"legacy-token"}"""
        )

        assertEquals("zh-CN", reset.languageTag)
        assertNull(legacyReset.languageTag)
        assertEquals("ja-JP", verification.languageTag)
        assertNull(legacyVerification.languageTag)
    }
}
