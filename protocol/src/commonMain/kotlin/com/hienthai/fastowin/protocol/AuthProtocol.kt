package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

const val SESSION_REPLACED_CLOSE_REASON = "Account signed in elsewhere"

@Serializable
data class ServiceStatusResponse(
    val maintenance: Boolean = false,
    val message: String? = null,
    val pollAfterSeconds: Int = 30,
    val state: MaintenanceState? = if (maintenance) MaintenanceState.MAINTENANCE else MaintenanceState.ACTIVE,
    val mode: MaintenanceMode? = null,
    val version: Long? = null,
    val messageVi: String? = null,
    val messageEn: String? = null,
    val transitionAtEpochMillis: Long? = null,
    val expectedEndEpochMillis: Long? = null,
    val serverTimeEpochMillis: Long? = null
)

@Serializable
enum class PlayerGender { MALE, FEMALE }

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val devicePlatform: String? = null,
    val gender: PlayerGender = PlayerGender.MALE,
    val acceptedContentTermsVersion: String? = null,
    val humanVerificationToken: String? = null
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
    val devicePlatform: String? = null,
    val acceptedContentTermsVersion: String? = null,
    val humanVerificationToken: String? = null
)

@Serializable
data class RefreshTokenRequest(val refreshToken: String, val refreshAttemptSecret: String? = null)

@Serializable
data class LogoutRequest(val refreshToken: String, val refreshAttemptSecret: String? = null)

@Serializable
data class ChangePasswordRequest(
    val accessToken: String,
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class PasswordResetRequest(
    val email: String,
    val languageTag: String? = null,
    val humanVerificationToken: String? = null
)

@Serializable
data class PasswordResetConfirmRequest(
    val email: String,
    val resetToken: String,
    val newPassword: String
)

@Serializable
data class EmailVerificationRequest(
    val accessToken: String,
    val languageTag: String? = null,
    val humanVerificationToken: String? = null
)

/**
 * SEC-07: forms protected by a Cloudflare Turnstile challenge. The wire name is the Turnstile
 * `action`, which the server checks so a token solved for one form cannot unlock another.
 */
@Serializable
enum class HumanVerificationAction(val wireName: String) {
    REGISTER("register"),
    UPGRADE_GUEST("upgrade_guest"),
    PASSWORD_RESET("password_reset"),
    EMAIL_VERIFICATION("email_verification");

    companion object {
        fun fromWireName(value: String?): HumanVerificationAction? = entries.firstOrNull { it.wireName == value }
    }
}

/** Public, unauthenticated: tells clients whether to show the challenge and with which site key. */
@Serializable
data class HumanVerificationConfigResponse(
    val enabled: Boolean = false,
    val siteKey: String? = null
)

const val HUMAN_VERIFICATION_CONFIG_PATH = "/auth/human-verification"
const val HUMAN_VERIFICATION_PAGE_PATH = "/auth/human-verification/challenge"
const val HUMAN_VERIFICATION_REQUIRED_CODE = "HUMAN_VERIFICATION_REQUIRED"
const val HUMAN_VERIFICATION_FAILED_CODE = "HUMAN_VERIFICATION_FAILED"
const val HUMAN_VERIFICATION_UNAVAILABLE_CODE = "HUMAN_VERIFICATION_UNAVAILABLE"

@Serializable
data class EmailVerificationConfirmRequest(
    val accessToken: String,
    val verificationCode: String
)

@Serializable
data class DeleteAccountRequest(
    val accessToken: String,
    /** Ignored by the server since App Review 5.1.1(v); older clients still send it. */
    val password: String = ""
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
    val emailVerified: Boolean? = null,
    val messageKey: String? = null,
    val messageArgs: Map<String, String> = emptyMap()
)

@Serializable
data class AuthSessionResponse(
    val userId: String,
    val displayName: String = "",
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMillis: Long,
    val refreshExpiresAtEpochMillis: Long,
    val emailVerified: Boolean = true,
    val supportEmailVerificationExempt: Boolean = false
)

@Serializable
data class AuthErrorResponse(
    val code: String,
    val message: String,
    val messageKey: String? = null,
    val messageArgs: Map<String, String> = emptyMap()
)

@Serializable
data class LocalizedMessage(
    val key: String? = null,
    val arguments: Map<String, String> = emptyMap(),
    val fallback: String = ""
)
