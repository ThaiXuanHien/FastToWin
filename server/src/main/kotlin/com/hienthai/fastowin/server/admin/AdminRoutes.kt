package com.hienthai.fastowin.server.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val ADMIN_COOKIE = "fasttowin_admin"
const val ADMIN_CSRF_HEADER = "X-Admin-CSRF"
private val adminJson = Json { ignoreUnknownKeys = false }

fun Route.adminRoutes(service: AdminService) {
    route("/admin/api") {
        post("/login") { call.adminResponse(service) {
            service.requireOrigin(call.request.header(HttpHeaders.Origin))
            val request = call.adminBody<AdminLoginRequest>()
            val login = service.login(request.email, request.password, call.request.header(HttpHeaders.Origin), call.request.local.remoteHost)
            call.adminCookie(login.token, service.configuration.secureCookie)
            call.respond(login.response)
        } }
        get("/session") { call.adminResponse(service) { call.respond(service.session(call.adminToken())) } }
        post("/logout") { call.adminResponse(service) {
            service.logout(call.adminToken(), call.request.header(ADMIN_CSRF_HEADER), call.request.header(HttpHeaders.Origin))
            call.adminCookie("", service.configuration.secureCookie, expire = true); call.respond(AdminOk())
        } }
        get("/users") { call.adminResponse(service) { call.respond(service.users(call.adminToken(), call.request.queryParameters["q"].orEmpty(), call.adminPage())) } }
        get("/users/{id}") { call.adminResponse(service) { call.respond(service.user(call.adminToken(), call.adminId())) } }
        get("/users/{id}/matches") { call.adminResponse(service) { call.respond(service.matches(call.adminToken(), call.adminId(), call.adminPage())) } }
        get("/users/{id}/wallet") { call.adminResponse(service) { call.respond(service.wallet(call.adminToken(), call.adminId(), false, call.adminPage())) } }
        get("/users/{id}/gifts") { call.adminResponse(service) { call.respond(service.wallet(call.adminToken(), call.adminId(), true, call.adminPage())) } }
        get("/accounts") { call.adminResponse(service) { call.respond(service.accounts(call.adminToken(), call.adminPage())) } }
        post("/accounts") { call.adminResponse(service) {
            service.requireOrigin(call.request.header(HttpHeaders.Origin))
            call.respond(service.createAccount(call.adminToken(), call.request.header(ADMIN_CSRF_HEADER), call.request.header(HttpHeaders.Origin), call.adminBody<CreateAdminRequest>()))
        } }
        post("/accounts/{id}/access") { call.adminResponse(service) {
            service.requireOrigin(call.request.header(HttpHeaders.Origin))
            call.respond(service.changeAccess(call.adminToken(), call.request.header(ADMIN_CSRF_HEADER), call.request.header(HttpHeaders.Origin), call.adminId(), call.adminBody<ChangeAdminAccessRequest>()))
        } }
        get("/audit") { call.adminResponse(service) { call.respond(service.audit(call.adminToken(), call.adminPage())) } }
    }
}
private fun ApplicationCall.adminToken() = request.cookies[ADMIN_COOKIE]
private fun ApplicationCall.adminId() = parameters["id"] ?: invalid()
private fun ApplicationCall.adminPage(): PageRequest {
    fun integer(name: String, default: Int) = request.queryParameters[name]?.let { it.toIntOrNull() ?: invalid() } ?: default
    return PageRequest(integer("page", 0), integer("pageSize", 25))
}
private fun ApplicationCall.adminCookie(token: String, secure: Boolean, expire: Boolean = false) {
    response.cookies.append(Cookie(ADMIN_COOKIE, token, encoding = CookieEncoding.RAW, maxAge = if (expire) 0 else (AdminService.SESSION_MILLIS / 1000).toInt(),
        path = "/admin", secure = secure, httpOnly = true, extensions = mapOf("SameSite" to "Strict")))
}
private suspend inline fun <reified T> ApplicationCall.adminBody(): T {
    if (!request.contentType().match(ContentType.Application.Json)) invalid()
    val channel = receiveChannel(); val buffer = ByteArray(8193); var size = 0
    while (!channel.isClosedForRead) {
        if (size == buffer.size) invalid()
        val count = channel.readAvailable(buffer, size, buffer.size - size)
        if (count == -1) break
        size += count
    }
    if (size > 8192) invalid()
    return try { adminJson.decodeFromString<T>(buffer.copyOf(size).toString(Charsets.UTF_8)) } catch (_: SerializationException) { invalid() }
}
private suspend fun ApplicationCall.adminResponse(service: AdminService, block: suspend () -> Unit) {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("X-Content-Type-Options", "nosniff")
    try {
        request.header(HttpHeaders.Origin)?.let(service::requireOrigin)
        block()
    } catch (error: AdminException) { respond(HttpStatusCode.fromValue(error.status), AdminError(error.code, error.message)) }
}
