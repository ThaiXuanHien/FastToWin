package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.AccountSessionsRequest
import com.hienthai.fastowin.protocol.AccountSessionsResponse
import com.hienthai.fastowin.protocol.ChangePasswordRequest
import com.hienthai.fastowin.protocol.DeleteAccountRequest
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.LogoutRequest
import com.hienthai.fastowin.protocol.PasswordResetConfirmRequest
import com.hienthai.fastowin.protocol.PasswordResetRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RefreshTokenRequest
import com.hienthai.fastowin.protocol.RegisterRequest
import com.hienthai.fastowin.protocol.PlayerGender
import com.hienthai.fastowin.protocol.RevokeAccountSessionRequest
import com.hienthai.fastowin.protocol.RevokeAllAccountSessionsRequest
import com.hienthai.fastowin.protocol.UpgradeGuestRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

class AuthApiClient(serverUrl: String) {
    private val baseUrl = serverUrl.toHttpBaseUrl()
    private val client = HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        devicePlatform: String,
        gender: PlayerGender
    ): AuthSessionResponse = execute(
        "$baseUrl/auth/register",
        RegisterRequest(email, password, displayName, devicePlatform, gender)
    )

    suspend fun login(email: String, password: String, devicePlatform: String): AuthSessionResponse =
        execute("$baseUrl/auth/login", LoginRequest(email, password, devicePlatform))

    suspend fun upgradeGuest(
        resumeToken: String,
        email: String,
        password: String,
        devicePlatform: String
    ): AuthSessionResponse = execute(
        "$baseUrl/auth/upgrade-guest",
        UpgradeGuestRequest(resumeToken, email, password, devicePlatform)
    )

    suspend fun refresh(refreshToken: String): AuthSessionResponse =
        execute("$baseUrl/auth/refresh", RefreshTokenRequest(refreshToken))

    suspend fun logout(refreshToken: String) {
        val response = client.post("$baseUrl/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(refreshToken))
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
    }

    suspend fun changePassword(
        accessToken: String,
        currentPassword: String,
        newPassword: String
    ): AccountActionResponse = executeAction(
        "$baseUrl/auth/change-password",
        ChangePasswordRequest(accessToken, currentPassword, newPassword)
    )

    suspend fun requestPasswordReset(email: String): AccountActionResponse = executeAction(
        "$baseUrl/auth/password-reset/request",
        PasswordResetRequest(email)
    )

    suspend fun confirmPasswordReset(
        email: String,
        resetToken: String,
        newPassword: String
    ): AccountActionResponse = executeAction(
        "$baseUrl/auth/password-reset/confirm",
        PasswordResetConfirmRequest(email, resetToken, newPassword)
    )

    suspend fun deleteAccount(accessToken: String, password: String): AccountActionResponse = executeAction(
        "$baseUrl/auth/delete-account",
        DeleteAccountRequest(accessToken, password)
    )

    suspend fun listSessions(accessToken: String): AccountSessionsResponse = executeResponse(
        "$baseUrl/auth/sessions",
        AccountSessionsRequest(accessToken)
    )

    suspend fun revokeSession(accessToken: String, sessionId: String): AccountActionResponse = executeAction(
        "$baseUrl/auth/sessions/revoke",
        RevokeAccountSessionRequest(accessToken, sessionId)
    )

    suspend fun revokeAllSessions(accessToken: String): AccountActionResponse = executeAction(
        "$baseUrl/auth/sessions/revoke-all",
        RevokeAllAccountSessionsRequest(accessToken)
    )

    fun close() = client.close()

    private suspend inline fun <reified T : Any> execute(url: String, request: T): AuthSessionResponse {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
        return response.body()
    }

    private suspend inline fun <reified T : Any> executeAction(url: String, request: T): AccountActionResponse {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
        return response.body()
    }

    private suspend inline fun <reified Request : Any, reified Response : Any> executeResponse(
        url: String,
        request: Request
    ): Response {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
        return response.body()
    }

    private suspend fun io.ktor.client.statement.HttpResponse.toAuthException(): AuthApiException {
        val error = runCatching { body<AuthErrorResponse>() }.getOrNull()
        return AuthApiException(
            code = error?.code ?: "NETWORK_ERROR",
            message = error?.message ?: "Máy chủ không xử lý được yêu cầu đăng nhập."
        )
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
    }
}

class AuthApiException(val code: String, override val message: String) : Exception(message)
