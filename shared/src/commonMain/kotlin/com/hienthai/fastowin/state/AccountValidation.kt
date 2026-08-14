package com.hienthai.fastowin.state

const val MIN_ACCOUNT_PASSWORD_LENGTH = 8
const val MAX_ACCOUNT_PASSWORD_LENGTH = 128

fun accountPasswordError(password: String): String? = when {
    password.length < MIN_ACCOUNT_PASSWORD_LENGTH ->
        "Mật khẩu phải có ít nhất $MIN_ACCOUNT_PASSWORD_LENGTH ký tự."
    password.length > MAX_ACCOUNT_PASSWORD_LENGTH ->
        "Mật khẩu không được vượt quá $MAX_ACCOUNT_PASSWORD_LENGTH ký tự."
    else -> null
}

fun accountPasswordConfirmationError(password: String, confirmation: String): String? =
    if (confirmation.isNotEmpty() && password != confirmation) "Mật khẩu nhập lại chưa khớp." else null
