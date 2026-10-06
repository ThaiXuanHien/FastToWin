package com.hienthai.fastowin.server.admin

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Explicit classpath allowlist; never resolve a request path on the filesystem. */
fun Route.adminAssets() {
    get("/admin") { call.respondRedirect("/admin/") }
    val assets = mapOf(
        "/admin/" to ("index.html" to "text/html"),
        "/admin/index.html" to ("index.html" to "text/html"),
        "/admin/admin.css" to ("admin.css" to "text/css"),
        "/admin/admin.js" to ("admin.js" to "application/javascript"),
        "/admin/admin-network.js" to ("admin-network.js" to "application/javascript"),
        "/admin/admin-gifts.js" to ("admin-gifts.js" to "application/javascript"),
        "/admin/admin-maintenance.js" to ("admin-maintenance.js" to "application/javascript"),
        "/admin/admin-analytics.js" to ("admin-analytics.js" to "application/javascript")
    )
    assets.forEach { (path, asset) ->
        val bytes = requireNotNull(AdminService::class.java.getResourceAsStream("/admin/${asset.first}")) {
            "Missing packaged admin asset: ${asset.first}"
        }.use { it.readBytes() }
        get(path) {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header("X-Content-Type-Options", "nosniff")
            call.response.header("Referrer-Policy", "no-referrer")
            call.response.header("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'")
            call.respondBytes(bytes, ContentType.parse(asset.second).withParameter("charset", "utf-8"))
        }
    }
}
