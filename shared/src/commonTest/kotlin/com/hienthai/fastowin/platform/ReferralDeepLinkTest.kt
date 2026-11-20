package com.hienthai.fastowin.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferralDeepLinkTest {
    @Test
    fun `public invite paths and native links carry the same code`() {
        assertEquals("/invite/ABCD123456", referralWebPath("abcd123456"))
        assertEquals("ABCD123456", parseReferralWebPath("/invite/abcd123456?source=share"))
        assertTrue(AppDeepLinkRouter.openUri(referralNativeLink("abcd123456")))
        assertEquals("ABCD123456", ReferralDeepLinkRouter.pendingCode.value)
        ReferralDeepLinkRouter.consume("ABCD123456")
        assertNull(ReferralDeepLinkRouter.pendingCode.value)
    }

    @Test
    fun `invite links reject extra path segments and non-code content`() {
        assertNull(parseReferralWebPath("/invite/abc/extra"))
        assertNull(parseReferralWebPath("/invite/%2e%2e"))
        assertNull(parseReferralWebPath("/challenge/ABCD123456"))
        assertFalse(AppDeepLinkRouter.openUri("fasttowin://invite/abc.def"))
    }
}
