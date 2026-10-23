package com.hienthai.fastowin.server.moderation

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

private val moderationJson=Json {ignoreUnknownKeys=false}
fun Route.moderationRoutes(service:ModerationService,adminEnabled:Boolean=true) {
    if(!adminEnabled) return
    listOf("/admin/api/moderation","/api/admin/moderation").forEach {prefix ->
        route(prefix) {
            get("/reports") {call.moderationResponse {
                val status=call.request.queryParameters["status"]?.let {raw->ModerationStatus.entries.firstOrNull {it.name==raw} ?: invalid()}
                fun integer(key:String,default:Int)=call.request.queryParameters[key]?.let {it.toIntOrNull() ?: invalid()} ?: default
                call.respond(service.list(call.moderationContext(),PageRequest(integer("page",0),integer("pageSize",25)),status))
            }}
            get("/reports/{id}") {call.moderationResponse {call.respond(service.detail(call.moderationContext(),call.parameters["id"] ?: invalid()))}}
            post("/reports/{id}/resolve") {call.moderationResponse {
                call.respond(service.resolve(call.moderationContext(),call.parameters["id"] ?: invalid(),call.moderationBody<ModerationResolveRequest>()))
            }}
            get("/users/{id}/restriction") {call.moderationResponse {call.respond(service.userRestriction(call.moderationContext(),call.parameters["id"] ?: invalid()))}}
            post("/users/{id}/restriction") {call.moderationResponse {
                call.respond(service.restrict(call.moderationContext(),call.parameters["id"] ?: invalid(),call.moderationBody<ModerationRestrictionRequest>()))
            }}
        }
    }
}
private fun ApplicationCall.moderationContext()=ModerationAdminContext(request.cookies[ADMIN_COOKIE],request.header(ADMIN_CSRF_HEADER),request.header(HttpHeaders.Origin))
private suspend inline fun <reified T> ApplicationCall.moderationBody():T {
    if(!request.contentType().match(ContentType.Application.Json)) invalid()
    val channel=receiveChannel();val bytes=ByteArray(8193);var size=0
    while(!channel.isClosedForRead) {
        if(size==bytes.size) invalid()
        val read=channel.readAvailable(bytes,size,bytes.size-size)
        if(read<0) break
        size+=read
    }
    if(size>8192) invalid()
    return try {
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        moderationJson.decodeFromString<T>(decoder.decode(ByteBuffer.wrap(bytes,0,size)).toString())
    } catch(_:SerializationException) {invalid()} catch(_:CharacterCodingException) {invalid()}
}
private suspend fun ApplicationCall.moderationResponse(block:suspend ()->Unit) {
    response.header(HttpHeaders.CacheControl,"no-store");response.header("X-Content-Type-Options","nosniff")
    try {block()} catch(error:AdminException) {respond(HttpStatusCode.fromValue(error.status),AdminError(error.code,error.message))}
}
