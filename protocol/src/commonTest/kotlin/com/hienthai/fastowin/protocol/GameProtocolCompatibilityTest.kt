package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

    @Test
    fun `play quota message preserves authoritative values`() {
        val message: ServerMessage = ServerMessage.PlayQuotaData(
            PlayQuotaSnapshot(
                quotaDate = "2026-09-12",
                baseMatches = 10,
                matchesConsumed = 8,
                bonusMatchesGranted = 2,
                remainingMatches = 4,
                nextResetAtEpochMillis = 1_789_148_400_000,
                rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
            )
        )

        val encoded = ProtocolJson.encodeToString<ServerMessage>(message)

        assertEquals(message, ProtocolJson.decodeFromString<ServerMessage>(encoded))
    }

    @Test
    fun `rewarded ad claim preserves idempotency identifiers`() {
        val message: ClientMessage = ClientMessage.ClaimRewardedAdBonus(
            requestId = "request-1",
            provider = RewardedAdProvider.DEV_SIMULATED,
            providerTransactionId = "dev-transaction-1",
            proof = "FASTTOWIN_DEV_REWARDED_V1:user-1:dev-transaction-1"
        )

        val encoded = ProtocolJson.encodeToString<ClientMessage>(message)

        assertEquals(message, ProtocolJson.decodeFromString<ClientMessage>(encoded))
    }
}
