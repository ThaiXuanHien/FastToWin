package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val devicePlatform: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val devicePlatform: String? = null
)

@Serializable
data class UpgradeGuestRequest(
    val resumeToken: String,
    val email: String,
    val password: String,
    val devicePlatform: String? = null
)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class ChangePasswordRequest(
    val accessToken: String,
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class PasswordResetConfirmRequest(
    val email: String,
    val resetToken: String,
    val newPassword: String
)

@Serializable
data class DeleteAccountRequest(
    val accessToken: String,
    val password: String
)

@Serializable
data class AccountActionResponse(
    val message: String,
    val devResetToken: String? = null
)

@Serializable
data class AuthSessionResponse(
    val userId: String,
    val displayName: String = "",
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMillis: Long,
    val refreshExpiresAtEpochMillis: Long
)

@Serializable
data class AuthErrorResponse(
    val code: String,
    val message: String
)
