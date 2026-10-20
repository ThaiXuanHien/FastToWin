package com.hienthai.fastowin.server.ads

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.server.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.sql.SQLException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Route.adMobRoutes(service: AdMobService?, auth: AuthenticationService, limiter: RateLimiter, policies: ServerRateLimitPolicies) {
    post("/api/rewarded-ads/sessions") { call.adMobResponse {
        call.adMobLimit(limiter, "admob-prepare-ip", call.request.origin.remoteHost, policies.adMobPreparePerIp)
        val user = call.adMobPlayer(auth)
        call.adMobLimit(limiter, "admob-prepare-user", user, policies.adMobPreparePerPlayer)
        val configured = service ?: throw AdMobUnavailable()
        call.respond(configured.prepare(user, call.adMobBody().requestId))
    } }
    get("/api/rewarded-ads/sessions/{id}") { call.adMobResponse {
        call.adMobLimit(limiter, "admob-status-ip", call.request.origin.remoteHost, policies.adMobStatusPerIp)
        val user = call.adMobPlayer(auth)
        call.adMobLimit(limiter, "admob-status-user", user, policies.adMobStatusPerPlayer)
        val configured = service ?: throw AdMobUnavailable()
        val view = configured.status(user, call.parameters["id"] ?: throw AdMobInvalidRequest())
            ?: throw AdMobHttpError(HttpStatusCode.NotFound, "NOT_FOUND")
        call.respond(view)
    } }
    get("/api/rewarded-ads/ssv") { call.adMobResponse {
        call.adMobLimit(limiter, "admob-ssv-ip", call.request.origin.remoteHost, policies.adMobCallbackPerIp)
        val configured = service ?: throw AdMobUnavailable()
        // request.uri retains percent escapes and order; queryParameters would decode/reorder the signed content.
        configured.callback(call.request.uri.substringAfter('?', ""))
        call.respond(HttpStatusCode.OK)
    } }
}
private class AdMobHttpError(val status: HttpStatusCode, val code: String) : RuntimeException()
private suspend fun ApplicationCall.adMobPlayer(auth: AuthenticationService): String {
    val token = request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
    return token?.let { auth.authenticateAccessToken(it)?.userId?.toString() }
        ?: throw AdMobHttpError(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
}
private suspend fun ApplicationCall.adMobLimit(limiter: RateLimiter, bucket: String, key: String, policy: RateLimitPolicy) {
    val result = limiter.consume(bucket, stableRateLimitKey(key), policy)
    if (!result.allowed) {
        response.header(HttpHeaders.RetryAfter, ((result.retryAfterMillis + 999) / 1000).toString())
        throw AdMobHttpError(HttpStatusCode.TooManyRequests, "RATE_LIMITED")
    }
}
private suspend fun ApplicationCall.adMobBody(): RewardedAdSessionRequest {
    if (!request.contentType().match(ContentType.Application.Json) ||
        request.contentType().parameter("charset")?.let { !it.equals("utf-8", true) } == true) throw AdMobInvalidRequest()
    val buffer = ByteArray(1025); var size = 0; val channel = receiveChannel()
    while (!channel.isClosedForRead) {
        if (size == buffer.size) throw AdMobInvalidRequest()
        val count = channel.readAvailable(buffer, size, buffer.size - size)
        if (count < 0) break
        size += count
    }
    if (size > 1024) throw AdMobInvalidRequest()
    return try {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(buffer, 0, size)).toString()
        Json.decodeFromString<RewardedAdSessionRequest>(text)
    } catch (_: SerializationException) { throw AdMobInvalidRequest()
    } catch (_: java.nio.charset.CharacterCodingException) { throw AdMobInvalidRequest() }
}
private suspend fun ApplicationCall.adMobResponse(block: suspend () -> Unit) {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("X-Content-Type-Options", "nosniff")
    suspend fun error(status: HttpStatusCode, code: String) = respond(status, AuthErrorResponse(code, "", messageKey = when(code) {
        "UNAUTHORIZED" -> TextKey.ServerAuthRequired.name
        "INVALID_REQUEST" -> TextKey.ServerInvalidRequest.name
        "NOT_FOUND" -> TextKey.ServerInvalidRequest.name
        else -> TextKey.ServerUnavailable.name
    }))
    try { block() }
    catch (e: AdMobHttpError) { error(e.status, e.code) }
    catch (_: AdMobInvalidRequest) { error(HttpStatusCode.BadRequest, "INVALID_REQUEST") }
    catch (_: AdMobTooManySessions) { error(HttpStatusCode.TooManyRequests, "RATE_LIMITED") }
    catch (_: AdMobUnavailable) { error(HttpStatusCode.ServiceUnavailable, "UNAVAILABLE") }
    catch (_: SQLException) { error(HttpStatusCode.ServiceUnavailable, "STORAGE_UNAVAILABLE") }
}
