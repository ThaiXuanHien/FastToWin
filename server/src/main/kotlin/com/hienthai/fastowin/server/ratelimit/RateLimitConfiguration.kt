package com.hienthai.fastowin.server.ratelimit

import com.hienthai.fastowin.server.readEnvironmentSecret
import java.net.URI

internal class RedisConfiguration(val url: String, val namespace: String) {
    override fun toString(): String = "RedisConfiguration(redacted)"
}

internal fun rateLimitConfiguration(values: Map<String, String>, fileReader: (String) -> String): RedisConfiguration? {
    when (values["FASTTOWIN_RATE_LIMIT_STORE"] ?: "memory") {
        "memory" -> return null
        "redis" -> Unit
        else -> throw IllegalArgumentException("Invalid rate limit store.")
    }
    val namespace = values["FASTTOWIN_RATE_LIMIT_NAMESPACE"]
    require(namespace != null && namespace.matches(Regex("[a-z0-9][a-z0-9_-]{0,47}"))) { "Invalid rate limit namespace." }
    val url = try { readEnvironmentSecret("FASTTOWIN_RATE_LIMIT_REDIS_URL", values, fileReader) } catch (_: Exception) { null }
    require(url != null) { "Invalid Redis configuration." }
    val uri = try { URI(url) } catch (_: Exception) { null }
    require(uri != null && uri.scheme in setOf("redis", "rediss") && !uri.host.isNullOrBlank() && uri.fragment == null && uri.query == null && (uri.port == -1 || uri.port in 1..65535)) { "Invalid Redis configuration." }
    return RedisConfiguration(url, namespace)
}
