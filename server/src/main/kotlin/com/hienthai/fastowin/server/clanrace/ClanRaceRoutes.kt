package com.hienthai.fastowin.server.clanrace

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

private val raceJson=Json { ignoreUnknownKeys=false }
fun Route.clanRaceRoutes(service:ClanRaceService,auth:AuthenticationService,adminEnabled:Boolean) {
    route("/api/clan-race") {
        get { call.respondRace { call.respond(service.current(call.player(auth))) } }
        get("/standings") { call.respondRace { call.respond(service.standings(call.player(auth),call.page())) } }
        get("/contributions") { call.respondRace { call.respond(service.contributions(call.player(auth))) } }
        get("/history") { call.respondRace { call.respond(service.history(call.player(auth),call.page())) } }
    }
    if(adminEnabled) route("/admin/api/clan-race") {
        get { call.respondRace { call.respond(service.list(call.context(),call.page())) } }
        post { call.respondRace { call.respond(service.create(call.context(),call.body())) } }
        get("/{id}") { call.respondRace { call.respond(service.detail(call.context(),call.id())) } }
        put("/{id}/draft") { call.respondRace { call.respond(service.update(call.context(),call.id(),call.body())) } }
        post("/{id}/clone") { call.respondRace { call.respond(service.clone(call.context(),call.id(),call.body())) } }
        post("/{id}/preview") { call.respondRace { call.respond(service.preview(call.context(),call.id())) } }
        post("/{id}/publish") { call.respondRace { call.respond(service.publish(call.context(),call.id(),call.body())) } }
        post("/{id}/archive") { call.respondRace { call.respond(service.archive(call.context(),call.id(),call.body())) } }
    }
}
private fun ApplicationCall.context()=ClanRaceAdminContext(request.cookies["fasttowin_admin"],request.header("X-Admin-CSRF"),request.header(HttpHeaders.Origin))
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
    return try { raceJson.decodeFromString<T>(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,0,size)).toString()) }
    catch(_:SerializationException) { invalid() } catch(_:java.nio.charset.CharacterCodingException) { invalid() }
}
private suspend fun ApplicationCall.respondRace(block:suspend ()->Unit) {
    response.header(HttpHeaders.CacheControl,"no-store");response.header("X-Content-Type-Options","nosniff")
    try { block() } catch(error:AdminException) { respond(HttpStatusCode.fromValue(error.status),ClanRaceApiError(error.code,error.message)) }
}
