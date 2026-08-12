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
