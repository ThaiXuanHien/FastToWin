package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
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
}
