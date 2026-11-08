package com.hienthai.fastowin.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushTokenTest {
    @Test
    fun `normalizes a current FCM token before sending it`() {
        assertEquals("fresh-token", normalizePushToken("  fresh-token  "))
    }

    @Test
    fun `ignores blank FCM tokens`() {
        assertNull(normalizePushToken("   "))
        assertNull(normalizePushToken(null))
    }
}
