package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.GiftAdminContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val roundJson = Json { ignoreUnknownKeys = false }
fun Route.compensationRoundRoutes(service: CompensationRoundService) = route("/admin/api/incidents/{incidentId}/rounds") {
    get { call.roundResponse { call.respond(service.list(call.roundContext(), call.incidentId(), call.roundPage())) } }
    post { call.roundResponse {
        service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.create(call.roundContext(), call.incidentId(), call.roundBody()))
    } }
    get("/{roundId}") { call.roundResponse { call.respond(service.detail(call.roundContext(), call.incidentId(), call.roundId())) } }
    put("/{roundId}") { call.roundResponse {
        service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.update(call.roundContext(), call.incidentId(), call.roundId(), call.roundBody()))
    } }
    post("/{roundId}/preview") { call.roundResponse {
        service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.preview(call.roundContext(), call.incidentId(), call.roundId(), call.roundBody()))
    } }
    post("/{roundId}/publish") { call.roundResponse {
        service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.publish(call.roundContext(), call.incidentId(), call.roundId(), call.roundBody()))
    } }
    post("/{roundId}/cancel") { call.roundResponse {
        service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.cancel(call.roundContext(), call.incidentId(), call.roundId(), call.roundBody()))
    } }
}
private fun ApplicationCall.roundContext() = GiftAdminContext(request.cookies[ADMIN_COOKIE], request.header(ADMIN_CSRF_HEADER), request.header(HttpHeaders.Origin))
private fun ApplicationCall.incidentId() = parameters["incidentId"] ?: invalid()
private fun ApplicationCall.roundId() = parameters["roundId"] ?: invalid()
private fun ApplicationCall.roundPage(): PageRequest {
    fun number(name: String, default: Int) = request.queryParameters[name]?.let { it.toIntOrNull() ?: invalid() } ?: default
    return PageRequest(number("page", 0), number("pageSize", 25))
}
private suspend inline fun <reified T> ApplicationCall.roundBody(): T {
    if (!request.contentType().match(ContentType.Application.Json)) invalid()
    val channel = receiveChannel(); val buffer = ByteArray(524289); var size = 0
    while (!channel.isClosedForRead) {
        if (size == buffer.size) invalid()
        val n = channel.readAvailable(buffer, size, buffer.size - size); if (n < 0) break; size += n
    }
    if (size > 524288) invalid()
    return try { roundJson.decodeFromString<T>(buffer.copyOf(size).toString(Charsets.UTF_8)) }
    catch (_: SerializationException) { invalid() }
}
private suspend fun ApplicationCall.roundResponse(block: suspend () -> Unit) {
    response.header(HttpHeaders.CacheControl, "no-store"); response.header("X-Content-Type-Options", "nosniff")
    try { block() } catch (error: AdminException) { respond(HttpStatusCode.fromValue(error.status), AdminError(error.code, error.message)) }
}
