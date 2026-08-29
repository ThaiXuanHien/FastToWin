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
