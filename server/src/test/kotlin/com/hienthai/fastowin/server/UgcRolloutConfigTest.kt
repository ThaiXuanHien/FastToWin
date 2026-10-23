package com.hienthai.fastowin.server
import kotlin.test.*
class UgcRolloutConfigTest {
    @Test fun `terms enforcement defaults off and requires explicit valid switch`() {
        assertFalse(contentTermsEnforced(emptyMap()))
        assertFalse(contentTermsEnforced(mapOf("FASTTOWIN_CONTENT_TERMS_ENFORCED" to "false")))
        assertTrue(contentTermsEnforced(mapOf("FASTTOWIN_CONTENT_TERMS_ENFORCED" to "true")))
        assertFailsWith<IllegalArgumentException> { contentTermsEnforced(mapOf("FASTTOWIN_CONTENT_TERMS_ENFORCED" to "maybe")) }
    }
}
