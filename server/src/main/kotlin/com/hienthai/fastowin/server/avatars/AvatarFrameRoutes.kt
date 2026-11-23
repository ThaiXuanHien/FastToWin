package com.hienthai.fastowin.server.avatars

import com.hienthai.fastowin.protocol.AvatarFrameCatalog
import com.hienthai.fastowin.server.admin.AdminException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

internal fun Route.avatarFrameRoutes(source: AvatarFrameSource?) {
    route("/api/avatar-frames") {
        get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            try {
                val frames = managedAvatarFrames(source?.avatarFrameDefinitions().orEmpty()).map { it.definition }
                call.respond(AvatarFrameCatalog(frames))
            } catch (_: AdminException) {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }
        get("/{id}/image") {
            call.response.header("X-Content-Type-Options", "nosniff")
            val artworkId = call.parameters["id"]?.let(::avatarFrameArtworkId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            try {
                val bundled = call.request.queryParameters["v"] == "0"
                val png = if (bundled) builtInAvatarFrameImage(artworkId)
                else source?.avatarFrameImage(artworkId) ?: builtInAvatarFrameImage(artworkId)
                if (png == null) return@get call.respond(HttpStatusCode.NotFound)
                call.response.header(HttpHeaders.CacheControl, if (bundled) "public, max-age=3600" else "no-cache")
                call.respondBytes(png, ContentType.Image.PNG)
            } catch (_: AdminException) {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }
    }
}
