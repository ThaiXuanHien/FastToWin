package com.hienthai.fastowin.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReferralRegistrationProtocolTest {
    @Test
    fun `signup preserves the optional referral code`() {
        val legacy = """{"email":"new@example.test","password":"Password1","displayName":"New player"}"""
        fun roundTrip(payload: String) = ProtocolJson.parseToJsonElement(
            ProtocolJson.encodeToString(ProtocolJson.decodeFromString<RegisterRequest>(payload))
        ).jsonObject

        assertEquals("ABCD123456", roundTrip(legacy.dropLast(1) + ",\"referralCode\":\"ABCD123456\"}")
            ["referralCode"]?.jsonPrimitive?.content)
        assertNull(ProtocolJson.decodeFromString<RegisterRequest>(legacy).referralCode)
    }
}
