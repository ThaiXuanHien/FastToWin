package com.hienthai.fastowin.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerUrlTest {
    @Test
    fun `websocket server URLs convert to HTTP API base URLs`() {
        assertEquals("http://127.0.0.1:8080", "ws://127.0.0.1:8080/game".toHttpBaseUrl())
        assertEquals("https://game.example.com", "wss://game.example.com/game".toHttpBaseUrl())
    }

    @Test
    fun `HTTP base URLs are normalized without changing their scheme`() {
        assertEquals("http://localhost:8080", "http://localhost:8080/".toHttpBaseUrl())
        assertEquals("https://game.example.com", "https://game.example.com/game".toHttpBaseUrl())
    }

    @Test
    fun `avatar URLs share the HTTP base and include their revision`() {
        assertEquals(
            "http://127.0.0.1:8080/api/avatar/player-1?v=3",
            "ws://127.0.0.1:8080/game".toAvatarImageUrl("player-1", revision = 3)
        )
        assertEquals("", "".toAvatarImageUrl("player-1"))
    }
}
