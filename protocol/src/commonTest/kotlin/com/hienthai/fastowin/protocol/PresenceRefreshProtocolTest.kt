package com.hienthai.fastowin.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class PresenceRefreshProtocolTest {
    @Test
    fun legacySessionDoesNotAdvertiseRefresh() {
        val legacy = ProtocolJson.decodeFromString<ServerMessage>("""{"type":"session_ready","playerId":"user","protocolVersion":44}""")
        assertFalse(assertIs<ServerMessage.SessionReady>(legacy).presenceRefreshSupported)
    }

    @Test
    fun scopedRequestAndResponseRoundTrip() {
        val request: ClientMessage = ClientMessage.GetPresence("request-1", "clan-1")
        val response: ServerMessage = ServerMessage.PresenceData("request-1", "clan-1", mapOf("user" to FriendPresence.PLAYING))
        assertEquals(request, ProtocolJson.decodeFromString<ClientMessage>(ProtocolJson.encodeToString(request)))
        assertEquals(response, ProtocolJson.decodeFromString<ServerMessage>(ProtocolJson.encodeToString(response)))
    }
}
