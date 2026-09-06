package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey

const val MIN_ACCOUNT_PASSWORD_LENGTH = 8
const val MAX_ACCOUNT_PASSWORD_LENGTH = 128

fun accountPasswordError(password: String): LocalizedText? = when {
    password.length < MIN_ACCOUNT_PASSWORD_LENGTH ->
        LocalizedText(TextKey.PasswordTooShort, mapOf("minimum" to MIN_ACCOUNT_PASSWORD_LENGTH))
    password.length > MAX_ACCOUNT_PASSWORD_LENGTH ->
        LocalizedText(TextKey.PasswordTooLong, mapOf("maximum" to MAX_ACCOUNT_PASSWORD_LENGTH))
    else -> null
}

fun accountPasswordConfirmationError(password: String, confirmation: String): LocalizedText? =
    if (confirmation.isNotEmpty() && password != confirmation) LocalizedText(TextKey.PasswordMismatch) else null
