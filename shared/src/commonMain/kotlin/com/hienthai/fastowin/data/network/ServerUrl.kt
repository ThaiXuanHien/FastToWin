package com.hienthai.fastowin.data.network

/** Converts the configured game WebSocket URL into the HTTP API base URL. */
internal fun String.toHttpBaseUrl(): String {
    val httpUrl = when {
        startsWith("wss://") -> "https://${removePrefix("wss://")}"
        startsWith("ws://") -> "http://${removePrefix("ws://")}"
        else -> this
    }
    return httpUrl.removeSuffix("/game").trimEnd('/')
}

/** Builds a cache-busted avatar URL so every surface displays the same uploaded image. */
internal fun String.toAvatarImageUrl(userId: String, revision: Long = 0L): String {
    if (isBlank() || userId.isBlank()) return ""
    return "${toHttpBaseUrl()}/api/avatar/$userId?v=$revision"
}
