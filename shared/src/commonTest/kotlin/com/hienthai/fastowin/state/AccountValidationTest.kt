package com.hienthai.fastowin.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountValidationTest {
    @Test
    fun `password length follows backend limits`() {
        assertEquals(
            "Mật khẩu phải có ít nhất 8 ký tự.",
            accountPasswordError("1234567")
        )
        assertNull(accountPasswordError("12345678"))
        assertNull(accountPasswordError("x".repeat(128)))
        assertEquals(
            "Mật khẩu không được vượt quá 128 ký tự.",
            accountPasswordError("x".repeat(129))
        )
    }

    @Test
    fun `confirmation error only appears after user starts typing`() {
        assertNull(accountPasswordConfirmationError("12345678", ""))
        assertEquals(
            "Mật khẩu nhập lại chưa khớp.",
            accountPasswordConfirmationError("12345678", "12345679")
        )
        assertNull(accountPasswordConfirmationError("12345678", "12345678"))
    }
}
