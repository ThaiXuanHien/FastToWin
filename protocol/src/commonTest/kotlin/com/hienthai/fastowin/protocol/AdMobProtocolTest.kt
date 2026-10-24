package com.hienthai.fastowin.protocol

import kotlin.test.*

class AdMobProtocolTest {
    @Test fun `granted HTTP session preserves provider transaction and current quota`() {
        val encoded = """{"sessionId":"session","customData":"nonce","adUnitId":"unit","expiresAtEpochMillis":1800001800000,"status":"GRANTED","providerTransactionId":"abcdef","quota":{"quotaDate":"2027-01-15","bonusMatchesGranted":2,"remainingMatches":12,"nextResetAtEpochMillis":1800032400000}}"""
        val view = ProtocolJson.decodeFromString<RewardedAdSessionView>(encoded)
        assertEquals(RewardedAdSessionStatus.GRANTED, view.status)
        assertEquals("abcdef", view.providerTransactionId)
        assertEquals(12, view.quota?.remainingMatches)
        assertEquals(view, ProtocolJson.decodeFromString<RewardedAdSessionView>(ProtocolJson.encodeToString(view)))
        assertEquals("request", ProtocolJson.decodeFromString<RewardedAdSessionRequest>("""{"requestId":"request"}""").requestId)
    }
}
