package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.server.admin.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.sql.SQLException

private val maintenanceJson=Json { ignoreUnknownKeys=false }
fun Route.maintenanceRoutes(service:MaintenanceService) {
    route("/admin/api/maintenance") {
        get { call.maintenanceResponse { call.respond(service.read(call.context())) } }
        post { call.maintenanceResponse {
            service.requireOrigin(call.request.header(HttpHeaders.Origin))
            call.respond(service.update(call.context(),call.body()))
        } }
    }
}
private fun ApplicationCall.context()=MaintenanceAdminContext(request.cookies["fasttowin_admin"],request.header("X-Admin-CSRF"),request.header(HttpHeaders.Origin))
private suspend fun ApplicationCall.body():MaintenanceUpdateRequest {
    if(!request.contentType().match(ContentType.Application.Json)) invalid()
    val buffer=ByteArray(16_385); val channel=receiveChannel(); var size=0
    while(!channel.isClosedForRead) {
        if(size==buffer.size) invalid()
        val count=channel.readAvailable(buffer,size,buffer.size-size)
        if(count<0) break
        size+=count
    }
    if(size>16_384) invalid()
    return try {
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        maintenanceJson.decodeFromString<MaintenanceUpdateRequest>(decoder.decode(ByteBuffer.wrap(buffer,0,size)).toString())
    } catch (_:SerializationException) { invalid() } catch (_:CharacterCodingException) { invalid() }
}
private suspend fun ApplicationCall.maintenanceResponse(block:suspend ()->Unit) {
    response.header(HttpHeaders.CacheControl,"no-store"); response.header("X-Content-Type-Options","nosniff")
    try { block() }
    catch(error:AdminException) { respond(HttpStatusCode.fromValue(error.status),AdminError(error.code,error.message)) }
    catch(_:SQLException) {
        val error=try { unavailable() } catch(e:AdminException) { e }
        respond(HttpStatusCode.ServiceUnavailable,AdminError(error.code,error.message))
    }
}
