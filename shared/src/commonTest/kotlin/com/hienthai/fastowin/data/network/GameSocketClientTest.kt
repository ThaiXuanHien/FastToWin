package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
import com.hienthai.fastowin.localization.TextKey
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameSocketClientTest {
    @Test
    fun `replacement close stops reconnect loop`() {
        assertFalse(shouldReconnectAfterSocketClose(SESSION_REPLACED_CLOSE_REASON))
        assertTrue(shouldReconnectAfterSocketClose("Network failure"))
        assertTrue(shouldReconnectAfterSocketClose(null))
    }

    @Test
    fun `client generated socket errors expose stable localization keys`() {
        assertEquals(
            TextKey.ServerInvalidRequest.name,
            socketClientError("PROTOCOL_DECODE_FAILED", "Legacy fallback").messageKey
        )
        assertEquals(
            TextKey.ServerUnavailable.name,
            socketClientError("CONNECTION_FAILED", "Legacy fallback").messageKey
        )
        assertEquals(
            TextKey.ServerSessionExpired.name,
            socketClientError("SESSION_REPLACED", "Legacy fallback").messageKey
        )
    }
}
