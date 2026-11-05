package com.hienthai.fastowin.server.presence

import com.hienthai.fastowin.server.readEnvironmentSecret
import java.net.URI

internal class PresenceConfiguration(val url: String, val namespace: String) {
    override fun toString() = "PresenceConfiguration(redacted)"
}

internal fun presenceConfiguration(values: Map<String, String>, fileReader: (String) -> String): PresenceConfiguration? {
    when (values["FASTTOWIN_PRESENCE_STORE"] ?: "memory") {
        "memory" -> return null
        "redis" -> Unit
        else -> throw IllegalArgumentException("Invalid presence store.")
    }
    val namespace = values["FASTTOWIN_PRESENCE_NAMESPACE"]
    require(namespace != null && namespace.matches(Regex("[a-z0-9][a-z0-9_-]{0,47}"))) {
        "Invalid presence namespace."
    }
    val url = runCatching { readEnvironmentSecret("FASTTOWIN_PRESENCE_REDIS_URL", values, fileReader) }.getOrNull()
    val uri = url?.let { runCatching { URI(it) }.getOrNull() }
    require(uri != null && uri.scheme in setOf("redis", "rediss") && !uri.host.isNullOrBlank() &&
        uri.fragment == null && uri.query == null && (uri.port == -1 || uri.port in 1..65535)) {
        "Invalid presence Redis configuration."
    }
    return PresenceConfiguration(requireNotNull(url), namespace)
}
