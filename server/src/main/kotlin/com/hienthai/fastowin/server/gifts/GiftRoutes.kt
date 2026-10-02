package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.admin.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val giftJson = Json { ignoreUnknownKeys = false }
fun Route.giftRoutes(service: GiftService, auth: AuthenticationService, adminEnabled: Boolean) {
    if (adminEnabled) route("/admin/api/gift-campaigns") {
        get { call.giftResponse { call.respond(service.list(call.context(),call.page())) } }
        post { call.giftResponse { service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.create(call.context(),call.body<GiftDraftRequest>())) } }
        get("/{id}") { call.giftResponse { call.respond(service.detail(call.context(),call.id())) } }
        put("/{id}") { call.giftResponse { service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.update(call.context(),call.id(),call.body<GiftDraftRequest>())) } }
        post("/{id}/preview") { call.giftResponse { service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.preview(call.context(),call.id(),call.body<GiftPreviewRequest>())) } }
        post("/{id}/publish") { call.giftResponse { service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.publish(call.context(),call.id(),call.body<GiftPublishRequest>())) } }
        post("/{id}/cancel") { call.giftResponse { service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.cancel(call.context(),call.id(),call.body<GiftCancelRequest>())) } }
    }
    route("/gifts/mailbox") {
        get { call.giftResponse { call.respond(service.mailbox(call.player(auth),call.page())) } }
        post("/{id}/claim") {
            val receivedAt = service.receivedAt()
            call.giftResponse { call.respond(service.claim(call.player(auth),call.id(),receivedAt)) }
        }
        post("/claim-all") {
            val receivedAt = service.receivedAt()
            call.giftResponse { call.respond(service.claimAll(call.player(auth),call.body<GiftClaimAllRequest>(),receivedAt)) }
        }
    }
}
private fun ApplicationCall.context() = GiftAdminContext(request.cookies[ADMIN_COOKIE],request.header(ADMIN_CSRF_HEADER),request.header(HttpHeaders.Origin))
private fun ApplicationCall.id() = parameters["id"] ?: invalid()
private fun ApplicationCall.page(): PageRequest {
    fun number(name: String, default: Int) = request.queryParameters[name]?.let { it.toIntOrNull() ?: invalid() } ?: default
    return PageRequest(number("page",0),number("pageSize",25))
}
private suspend fun ApplicationCall.player(auth: AuthenticationService): String {
    val token = request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ") ?: unauthorized()
    return auth.authenticateAccessToken(token)?.userId?.toString() ?: unauthorized()
}
private suspend inline fun <reified T> ApplicationCall.body(): T {
    if (!request.contentType().match(ContentType.Application.Json)) invalid()
    val channel = receiveChannel(); val buffer = ByteArray(524289); var size = 0
    while (!channel.isClosedForRead) {
        if (size == buffer.size) invalid()
        val n = channel.readAvailable(buffer,size,buffer.size-size); if (n < 0) break; size += n
    }
    if (size > 524288) invalid()
    return try { giftJson.decodeFromString<T>(buffer.copyOf(size).toString(Charsets.UTF_8)) } catch (_: SerializationException) { invalid() }
}
private suspend fun ApplicationCall.giftResponse(block: suspend () -> Unit) {
    response.header(HttpHeaders.CacheControl,"no-store"); response.header("X-Content-Type-Options","nosniff")
    try { block() } catch (error: AdminException) { respond(HttpStatusCode.fromValue(error.status),GiftError(error.code,error.message)) }
}
