package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class GameProtocolCompatibilityTest {
    @Test
    fun `version 40 connect account decodes without resume token`() {
        val raw = """{"type":"connect_account","accessToken":"access","protocolVersion":40}"""
        val decoded = ProtocolJson.decodeFromString<ClientMessage>(raw)
        assertEquals(ClientMessage.ConnectAccount("access", null, 40), decoded)
    }

    @Test
    fun `version 40 session ready decodes without resume token`() {
        val raw = """{"type":"session_ready","playerId":"player-1","protocolVersion":40}"""
        val decoded = ProtocolJson.decodeFromString<ServerMessage>(raw)
        assertEquals(ServerMessage.SessionReady("player-1", null, null, 40), decoded)
    }
}
