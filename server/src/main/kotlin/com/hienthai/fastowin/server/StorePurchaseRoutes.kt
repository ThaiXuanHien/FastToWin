package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.localization.TextKey
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.sql.SQLException

private val receiptJson = Json { ignoreUnknownKeys = false }

fun Route.storePurchaseRoutes(service: StorePurchaseService, auth: AuthenticationService,
    limiter: RateLimiter, policies: ServerRateLimitPolicies) {
    post("/api/store-purchases/verify") {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header("X-Content-Type-Options", "nosniff")
        suspend fun error(status: HttpStatusCode, code: String) = call.respond(status, AuthErrorResponse(code, "", messageKey = when (code) {
            "UNAUTHORIZED" -> TextKey.ServerAuthRequired.name
            "STORAGE_UNAVAILABLE" -> TextKey.ServerUnavailable.name
            "INVALID_REQUEST" -> TextKey.ServerInvalidRequest.name
            else -> TextKey.ServerActionFailed.name
        }))
        try {
            val ipLimit = limiter.consume(RateLimitBuckets.VERIFY_PURCHASE_IP,
                stableRateLimitKey(call.request.origin.remoteHost), policies.verifyPurchasePerIp)
            if (!ipLimit.allowed) {
                call.response.header(HttpHeaders.RetryAfter, ((ipLimit.retryAfterMillis + 999) / 1000).toString())
                error(HttpStatusCode.TooManyRequests, "RATE_LIMITED"); return@post
            }
            val token = call.request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
            val player = token?.let { auth.authenticateAccessToken(it) }?.userId?.toString()
            if (player == null) { error(HttpStatusCode.Unauthorized, "UNAUTHORIZED"); return@post }
            val playerLimit = limiter.consume(RateLimitBuckets.VERIFY_PURCHASE_PLAYER,
                stableRateLimitKey(player), policies.verifyPurchasePerPlayer)
            if (!playerLimit.allowed) {
                call.response.header(HttpHeaders.RetryAfter, ((playerLimit.retryAfterMillis + 999) / 1000).toString())
                error(HttpStatusCode.TooManyRequests, "RATE_LIMITED"); return@post
            }
            val request = call.receiptBody()
            val result = service.verify(player, request)
            call.respond(result)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: SQLException) { error(HttpStatusCode.ServiceUnavailable, "STORAGE_UNAVAILABLE")
        } catch (_: InvalidReceiptBody) { error(HttpStatusCode.BadRequest, "INVALID_REQUEST") }
    }
}

private class InvalidReceiptBody : Exception()
private suspend fun ApplicationCall.receiptBody(): StorePurchaseRequest {
    if (!request.contentType().match(ContentType.Application.Json) ||
        request.contentType().parameter("charset")?.let { !it.equals("utf-8", true) } == true) throw InvalidReceiptBody()
    val bytes = ByteArray(32_769); var size = 0
    val channel = receiveChannel()
    while (!channel.isClosedForRead) {
        if (size == bytes.size) throw InvalidReceiptBody()
        val count = channel.readAvailable(bytes, size, bytes.size - size)
        if (count < 0) break
        size += count
    }
    if (size > 32_768) throw InvalidReceiptBody()
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, size)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { throw InvalidReceiptBody() }
    return try { receiptJson.decodeFromString<StorePurchaseRequest>(text) }
    catch (_: SerializationException) { throw InvalidReceiptBody() }
}
