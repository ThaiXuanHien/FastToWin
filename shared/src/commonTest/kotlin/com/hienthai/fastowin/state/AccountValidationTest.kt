package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountValidationTest {
    @Test
    fun `password length follows backend limits`() {
        assertEquals(
            LocalizedText(TextKey.PasswordTooShort, mapOf("minimum" to 8)),
            accountPasswordError("1234567")
        )
        assertNull(accountPasswordError("12345678"))
        assertNull(accountPasswordError("x".repeat(128)))
        assertEquals(
            LocalizedText(TextKey.PasswordTooLong, mapOf("maximum" to 128)),
            accountPasswordError("x".repeat(129))
        )
    }

    @Test
    fun `confirmation error only appears after user starts typing`() {
        assertNull(accountPasswordConfirmationError("12345678", ""))
        assertEquals(
            LocalizedText(TextKey.PasswordMismatch),
            accountPasswordConfirmationError("12345678", "12345679")
        )
        assertNull(accountPasswordConfirmationError("12345678", "12345678"))
    }
}
