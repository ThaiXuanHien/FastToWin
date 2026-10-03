package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import com.hienthai.fastowin.localization.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException

fun Route.publicMaintenanceStatus(provider: () -> ServiceStatusResponse) {
    for (path in listOf("/status", "/api/status")) get(path) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        try { call.respond(provider()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { call.maintenanceUnavailable() }
    }
}

suspend fun ApplicationCall.rejectMaintenanceNewIntent(allowed: Boolean): Boolean {
    if (allowed) return false
    maintenanceUnavailable()
    return true
}

private suspend fun ApplicationCall.maintenanceUnavailable() {
    response.header(HttpHeaders.CacheControl, "no-store")
    respond(HttpStatusCode.ServiceUnavailable, AuthErrorResponse("MAINTENANCE",
        LocalizationService(AppLanguage.VIETNAMESE).text(TextKey.MaintenanceDefaultMessage),
        messageKey = TextKey.MaintenanceDefaultMessage.name))
}
