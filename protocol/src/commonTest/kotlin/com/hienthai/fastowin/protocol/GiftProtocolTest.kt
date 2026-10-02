package com.hienthai.fastowin.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class GiftProtocolTest {
    @Test fun `mailbox HTTP payload decodes independently of websocket message types`() {
        val payload="""{"items":[{"campaignId":"gift-id","titleVi":"Quà","titleEn":"Gift","bodyVi":"Cảm ơn","bodyEn":"Thanks","gold":100,"xp":30,"availableAtEpochMillis":1000,"expiresAtEpochMillis":2000,"state":"AVAILABLE"}],"page":0,"pageSize":25,"total":1}"""
        val page=ProtocolJson.decodeFromString<GiftMailboxPage>(payload)
        assertEquals("Quà",page.items.single().titleVi); assertNull(page.items.single().claimedAtEpochMillis)
        assertEquals(page,ProtocolJson.decodeFromString<GiftMailboxPage>(ProtocolJson.encodeToString(page)))
        assertFalse(ProtocolJson.encodeToString(page).contains("actorId"))
    }
    @Test fun `partial claim all preserves per item error and awarded balances`() {
        val payload="""{"items":[{"campaignId":"bad","error":{"code":"CONFLICT","message":"Expired"}}]}"""
        val result=ProtocolJson.decodeFromString<GiftClaimAllResult>(payload)
        assertNull(result.items.single().result); assertEquals("CONFLICT",result.items.single().error!!.code)
        assertEquals(result,ProtocolJson.decodeFromString<GiftClaimAllResult>(ProtocolJson.encodeToString(result)))
    }
}
