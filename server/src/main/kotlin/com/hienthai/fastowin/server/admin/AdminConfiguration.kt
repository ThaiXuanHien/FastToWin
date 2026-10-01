package com.hienthai.fastowin.server.admin

import java.net.URI

data class AdminConfiguration(val origin: String, val secureCookie: Boolean) {
    init {
        val uri = runCatching { URI(origin) }.getOrNull()
        require(uri != null && uri.host != null && uri.rawUserInfo == null && uri.rawPath.isNullOrEmpty() &&
            uri.rawQuery == null && uri.rawFragment == null && uri.port in -1..65535 && uri.port != 0 &&
            (uri.scheme == "https" || (!secureCookie && uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "[::1]", "::1")))) {
            "FASTTOWIN_ADMIN_ORIGIN must be an exact HTTPS origin (loopback HTTP is allowed only in dev)."
        }
    }
    companion object {
        fun fromEnvironment(environment: String, values: Map<String, String> = System.getenv()): AdminConfiguration? {
            val enabled = values["FASTTOWIN_ADMIN_ENABLED"] ?: return null
            require(enabled in setOf("true", "false")) { "FASTTOWIN_ADMIN_ENABLED must be true or false." }
            if (enabled == "false") return null
            require(environment in setOf("dev", "prod"))
            return AdminConfiguration(requireNotNull(values["FASTTOWIN_ADMIN_ORIGIN"]) { "FASTTOWIN_ADMIN_ORIGIN is required." }, environment != "dev")
        }
    }
}
