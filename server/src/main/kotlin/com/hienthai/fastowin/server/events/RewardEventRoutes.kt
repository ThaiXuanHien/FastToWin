package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.protocol.RewardEventState
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

private val eventJson=Json { ignoreUnknownKeys=false }
fun Route.rewardEventRoutes(service:RewardEventService,adminEnabled:Boolean=true) {
    route("/api/events") {
        get { call.eventResponse { call.respond(service.publicList(call.page())) } }
        get("/{id}") { call.eventResponse { call.respond(service.publicDetail(call.eventId())) } }
    }
    if(adminEnabled) route("/admin/api/events") {
        get { call.eventResponse {
            val status=call.request.queryParameters["state"]?.let { raw -> RewardEventState.entries.firstOrNull { it.name==raw }?:invalid() }
            call.respond(service.list(call.context(),call.page(),call.request.queryParameters["query"].orEmpty(),status))
        } }
        post { call.eventResponse { service.requireOrigin(call.context().origin); call.respond(service.create(call.context(),call.body())) } }
        get("/{id}") { call.eventResponse { call.respond(service.detail(call.context(),call.eventId())) } }
        put("/{id}/draft") { call.eventResponse { service.requireOrigin(call.context().origin); call.respond(service.update(call.context(),call.eventId(),call.body())) } }
        post("/{id}/preview") { call.eventResponse { call.respond(service.preview(call.context(),call.eventId())) } }
        post("/{id}/schedule") { call.eventResponse { service.requireOrigin(call.context().origin); call.respond(service.schedule(call.context(),call.eventId(),call.body())) } }
        post("/{id}/cancel") { call.eventResponse { service.requireOrigin(call.context().origin); call.respond(service.cancel(call.context(),call.eventId(),call.body())) } }
    }
}
private fun ApplicationCall.context()=RewardEventAdminContext(request.cookies["fasttowin_admin"],request.header("X-Admin-CSRF"),request.header(HttpHeaders.Origin))
private fun ApplicationCall.eventId()=parameters["id"]?:invalid()
private fun ApplicationCall.page():PageRequest {
    fun integer(key:String,default:Int)=request.queryParameters[key]?.let { it.toIntOrNull()?:invalid() }?:default
    return PageRequest(integer("page",0),integer("pageSize",25))
}
private suspend inline fun <reified T> ApplicationCall.body():T {
    if(!request.contentType().match(ContentType.Application.Json)) invalid()
    val buffer=ByteArray(32_769); val channel=receiveChannel(); var size=0
    while(!channel.isClosedForRead) {
        if(size==buffer.size) invalid()
        val count=channel.readAvailable(buffer,size,buffer.size-size); if(count<0) break; size+=count
    }
    if(size>32_768) invalid()
    return try {
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        eventJson.decodeFromString<T>(decoder.decode(ByteBuffer.wrap(buffer,0,size)).toString())
    } catch(_:SerializationException) { invalid() } catch(_:CharacterCodingException) { invalid() }
}
private suspend fun ApplicationCall.eventResponse(block:suspend ()->Unit) {
    response.header(HttpHeaders.CacheControl,"no-store"); response.header("X-Content-Type-Options","nosniff")
    try { block() } catch(error:AdminException) { respond(HttpStatusCode.fromValue(error.status),AdminError(error.code,error.message)) }
}
