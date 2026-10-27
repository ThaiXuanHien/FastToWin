package com.hienthai.fastowin.server.missions

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
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.sql.SQLException

private val missionJson=Json { ignoreUnknownKeys=false }
fun Route.missionRoutes(service:MissionService,auth:AuthenticationService,adminEnabled:Boolean) {
    route("/api/missions") {
        get { call.respondMission { call.respond(service.personal(call.player(auth))) } }
        get("/personal") { call.respondMission { call.respond(service.personal(call.player(auth))) } }
        get("/clan") { call.respondMission { call.respond(service.clan(call.player(auth))) } }
        post("/{id}/claim") { call.respondMission { call.respond(service.claim(call.player(auth),call.id(),call.body())) } }
    }
    if(adminEnabled) route("/admin/api/missions") {
        get { call.respondMission {
            val scope=call.request.queryParameters["scope"]?.let { raw -> MissionScope.entries.firstOrNull { it.name==raw }?:invalid() }
            val state=call.request.queryParameters["state"]?.let { raw -> MissionCatalogState.entries.firstOrNull { it.name==raw }?:invalid() }
            call.respond(service.list(call.context(),call.page(),call.request.queryParameters["query"].orEmpty(),scope,state))
        } }
        post { call.respondMission { call.respond(service.create(call.context(),call.body())) } }
        get("/{id}") { call.respondMission { call.respond(service.detail(call.context(),call.id())) } }
        put("/{id}/draft") { call.respondMission { call.respond(service.update(call.context(),call.id(),call.body())) } }
        post("/{id}/preview") { call.respondMission { call.respond(service.preview(call.context(),call.id())) } }
        post("/{id}/publish") { call.respondMission { call.respond(service.publish(call.context(),call.id(),call.body())) } }
        post("/{id}/archive") { call.respondMission { call.respond(service.archive(call.context(),call.id(),call.body())) } }
        delete("/{id}/draft") { call.respondMission { call.respond(service.deleteDraft(call.context(),call.id(),call.body())) } }
    }
}
private fun ApplicationCall.context()=MissionAdminContext(request.cookies["fasttowin_admin"],request.header("X-Admin-CSRF"),request.header(HttpHeaders.Origin))
private fun ApplicationCall.id()=parameters["id"]?:invalid()
private fun ApplicationCall.page():PageRequest {
    fun number(name:String,default:Int)=request.queryParameters[name]?.let { it.toIntOrNull()?:invalid() }?:default
    return PageRequest(number("page",0),number("pageSize",25))
}
private suspend fun ApplicationCall.player(auth:AuthenticationService):String {
    val token=request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?:unauthorized()
    return try { auth.authenticateAccessToken(token)?.userId?.toString()?:unauthorized() } catch(_:SQLException) { unavailable() }
}
private suspend inline fun <reified T> ApplicationCall.body():T {
    if(!request.contentType().match(ContentType.Application.Json)) invalid()
    val bytes=ByteArray(32_769);val channel=receiveChannel();var size=0
    while(!channel.isClosedForRead) { if(size==bytes.size) invalid();val count=channel.readAvailable(bytes,size,bytes.size-size);if(count<0) break;size+=count }
    if(size>32_768) invalid()
    return try { missionJson.decodeFromString<T>(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,0,size)).toString()) }
    catch(_:SerializationException) { invalid() } catch(_:java.nio.charset.CharacterCodingException) { invalid() }
}
private suspend fun ApplicationCall.respondMission(block:suspend ()->Unit) {
    response.header(HttpHeaders.CacheControl,"no-store");response.header("X-Content-Type-Options","nosniff")
    try { block() } catch(error:AdminException) { respond(HttpStatusCode.fromValue(error.status),MissionApiError(error.code,error.message)) }
}
