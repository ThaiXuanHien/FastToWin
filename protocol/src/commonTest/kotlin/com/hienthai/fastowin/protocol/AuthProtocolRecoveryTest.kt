package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthProtocolRecoveryTest {
    @Test fun `legacy requests decode without attempt and new requests preserve it`() {
        assertNull(ProtocolJson.decodeFromString<RefreshTokenRequest>("""{"refreshToken":"old"}""").refreshAttemptSecret)
        assertNull(ProtocolJson.decodeFromString<LogoutRequest>("""{"refreshToken":"old"}""").refreshAttemptSecret)
        val refresh = RefreshTokenRequest("old", "attempt")
        val logout = LogoutRequest("old", "attempt")
        assertEquals(refresh, ProtocolJson.decodeFromString<RefreshTokenRequest>(ProtocolJson.encodeToString(refresh)))
        assertEquals(logout, ProtocolJson.decodeFromString<LogoutRequest>(ProtocolJson.encodeToString(logout)))
    }
}
