package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.admin.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.sql.SQLException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val announcementJson = Json { ignoreUnknownKeys = false }

fun Route.announcementRoutes(service: AnnouncementService, auth: AuthenticationService, adminEnabled: Boolean) {
    route("/api/announcements") {
        get { call.announcementResponse { call.respond(service.inbox(call.player(auth), call.announcementPage())) } }
        get("/banners") { call.announcementResponse { call.respond(service.banners(call.player(auth))) } }
        post("/read-all") { call.announcementResponse { call.respond(service.markRead(call.player(auth), null)) } }
        post("/dismiss-all") { call.announcementResponse { call.respond(service.dismiss(call.player(auth), null)) } }
        get("/push-preferences") { call.announcementResponse { call.respond(service.preference(call.player(auth))) } }
        put("/push-preferences") { call.announcementResponse {
            call.respond(service.updatePreference(call.player(auth), call.announcementBody<AnnouncementPreferenceRequest>()))
        } }
        get("/{id}") { call.announcementResponse { call.respond(service.playerDetail(call.player(auth), call.announcementId())) } }
        post("/{id}/read") { call.announcementResponse { call.respond(service.markRead(call.player(auth), call.announcementId())) } }
        post("/{id}/dismiss") { call.announcementResponse { call.respond(service.dismiss(call.player(auth), call.announcementId())) } }
        post("/{id}/hide-banner") { call.announcementResponse { call.respond(service.hideBanner(call.player(auth), call.announcementId())) } }
    }
    if (adminEnabled) route("/admin/api/announcements") {
        get { call.announcementResponse {
            val status = call.request.queryParameters["status"]?.let { raw ->
                AnnouncementStatus.entries.firstOrNull { it.name == raw } ?: invalid()
            }
            call.respond(service.list(call.adminContext(), call.announcementPage(), status))
        } }
        get("/capabilities") { call.announcementResponse { call.respond(service.capabilities(call.adminContext())) } }
        post { call.announcementResponse { call.respond(HttpStatusCode.Created, service.create(call.adminContext(), call.announcementBody<AnnouncementDraftRequest>())) } }
        get("/{id}") { call.announcementResponse { call.respond(service.detail(call.adminContext(), call.announcementId())) } }
        put("/{id}") { call.announcementResponse { call.respond(service.update(call.adminContext(), call.announcementId(), call.announcementBody<AnnouncementDraftRequest>())) } }
        post("/{id}/preview") { call.announcementResponse { call.respond(service.preview(call.adminContext(), call.announcementId())) } }
        post("/{id}/publish") { call.announcementResponse {
            call.respond(service.publish(call.adminContext(), call.announcementId(), call.announcementBody<AnnouncementMutationRequest>()))
        } }
        post("/{id}/cancel") { call.announcementResponse {
            call.respond(service.cancel(call.adminContext(), call.announcementId(), call.announcementBody<AnnouncementMutationRequest>()))
        } }
    }
}

private fun ApplicationCall.adminContext() = AnnouncementAdminContext(
    request.cookies[ADMIN_COOKIE], request.header(ADMIN_CSRF_HEADER), request.header(HttpHeaders.Origin)
)
private fun ApplicationCall.announcementId() = parameters["id"] ?: invalid()
private fun ApplicationCall.announcementPage(): PageRequest {
    fun integer(key: String, default: Int) = request.queryParameters[key]?.let { it.toIntOrNull() ?: invalid() } ?: default
    return PageRequest(integer("page", 0), integer("pageSize", 25))
}
private suspend fun ApplicationCall.player(auth: AuthenticationService): String {
    val token = request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ") ?: unauthorized()
    return try { auth.authenticateAccessToken(token)?.userId?.toString() ?: unauthorized() }
    catch (_: SQLException) { unavailable() }
}
private suspend inline fun <reified T> ApplicationCall.announcementBody(): T {
    if (!request.contentType().match(ContentType.Application.Json)) invalid()
    val channel = receiveChannel()
    val buffer = ByteArray(32_769)
    var size = 0
    while (!channel.isClosedForRead) {
        if (size == buffer.size) invalid()
        val read = channel.readAvailable(buffer, size, buffer.size - size)
        if (read < 0) break
        size += read
    }
    if (size > 32_768) invalid()
    return try {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        announcementJson.decodeFromString<T>(decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString())
    } catch (_: SerializationException) { invalid() } catch (_: CharacterCodingException) { invalid() }
}
private suspend fun ApplicationCall.announcementResponse(block: suspend () -> Unit) {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("X-Content-Type-Options", "nosniff")
    try { block() }
    catch (error: AdminException) { respond(HttpStatusCode.fromValue(error.status), AdminError(error.code, error.message)) }
}
