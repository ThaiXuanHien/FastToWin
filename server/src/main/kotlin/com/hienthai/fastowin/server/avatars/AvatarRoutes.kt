package com.hienthai.fastowin.server.avatars

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.avatarRoutes(service: AvatarService) {
    get("/api/avatar/{playerId}") {
        call.response.header(HttpHeaders.CacheControl, "no-store, max-age=0")
        call.response.header("X-Content-Type-Options", "nosniff")
        val playerId = call.parameters["playerId"]
        if (playerId == null || !isAvatarPlayerId(playerId)) return@get call.respond(HttpStatusCode.BadRequest)
        when (val result = service.read(playerId)) {
            is AvatarReadResult.Found -> call.respondBytes(result.bytes, ContentType.parse(result.contentType))
            AvatarReadResult.NotFound -> call.respond(HttpStatusCode.NotFound)
            AvatarReadResult.Unavailable -> call.respond(HttpStatusCode.ServiceUnavailable)
        }
    }
}
