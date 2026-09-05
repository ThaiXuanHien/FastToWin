package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

const val SESSION_REPLACED_CLOSE_REASON = "Account signed in elsewhere"

@Serializable
data class ServiceStatusResponse(
    val maintenance: Boolean = false,
    val message: String? = null,
    val pollAfterSeconds: Int = 30
)

@Serializable
enum class PlayerGender { MALE, FEMALE }

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val devicePlatform: String? = null,
    val gender: PlayerGender = PlayerGender.MALE
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
data class EmailVerificationRequest(val accessToken: String)

@Serializable
data class EmailVerificationConfirmRequest(
    val accessToken: String,
    val verificationCode: String
)

@Serializable
data class DeleteAccountRequest(
    val accessToken: String,
    val password: String
)

@Serializable
data class AccountSessionsRequest(val accessToken: String)

@Serializable
data class RevokeAccountSessionRequest(
    val accessToken: String,
    val sessionId: String
)

@Serializable
data class RevokeAllAccountSessionsRequest(val accessToken: String)

@Serializable
data class AccountSessionSnapshot(
    val sessionId: String,
    val devicePlatform: String? = null,
    val createdAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val isCurrent: Boolean
)

@Serializable
data class AccountSessionsResponse(val sessions: List<AccountSessionSnapshot>)

@Serializable
data class AccountActionResponse(
    val message: String,
    val devResetToken: String? = null,
    val devEmailVerificationCode: String? = null,
    val emailVerified: Boolean? = null
)

@Serializable
data class AuthSessionResponse(
    val userId: String,
    val displayName: String = "",
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMillis: Long,
    val refreshExpiresAtEpochMillis: Long,
    val emailVerified: Boolean = true
)

@Serializable
data class AuthErrorResponse(
    val code: String,
    val message: String
)
