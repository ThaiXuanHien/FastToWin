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

private val incidentJson = Json { ignoreUnknownKeys = false }
fun Route.compensationIncidentRoutes(service: CompensationIncidentService) = route("/admin/api/incidents") {
    get { call.incidentResponse { call.respond(service.list(call.context(), call.page())) } }
    get("/capabilities") { call.incidentResponse { call.respond(service.capabilities(call.context())) } }
    post { call.incidentResponse {
        service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.create(call.context(), call.body()))
    } }
    get("/{id}") { call.incidentResponse { call.respond(service.detail(call.context(), call.id())) } }
    put("/{id}") { call.incidentResponse {
        service.requireOrigin(call.request.header(HttpHeaders.Origin)); call.respond(service.update(call.context(), call.id(), call.body()))
    } }
}
private fun ApplicationCall.context() = GiftAdminContext(request.cookies[ADMIN_COOKIE], request.header(ADMIN_CSRF_HEADER), request.header(HttpHeaders.Origin))
private fun ApplicationCall.id() = parameters["id"] ?: invalid()
private fun ApplicationCall.page(): PageRequest {
    fun number(name: String, default: Int) = request.queryParameters[name]?.let { it.toIntOrNull() ?: invalid() } ?: default
    return PageRequest(number("page", 0), number("pageSize", 25))
}
private suspend fun ApplicationCall.body(): CompensationIncidentRequest {
    if (!request.contentType().match(ContentType.Application.Json)) invalid()
    val channel = receiveChannel(); val buffer = ByteArray(524289); var size = 0
    while (!channel.isClosedForRead) {
        if (size == buffer.size) invalid()
        val n = channel.readAvailable(buffer, size, buffer.size - size); if (n < 0) break; size += n
    }
    if (size > 524288) invalid()
    return try { incidentJson.decodeFromString<CompensationIncidentRequest>(buffer.copyOf(size).toString(Charsets.UTF_8)) }
    catch (_: SerializationException) { invalid() }
}
private suspend fun ApplicationCall.incidentResponse(block: suspend () -> Unit) {
    response.header(HttpHeaders.CacheControl, "no-store"); response.header("X-Content-Type-Options", "nosniff")
    try { block() } catch (error: AdminException) { respond(HttpStatusCode.fromValue(error.status), AdminError(error.code, error.message)) }
}
