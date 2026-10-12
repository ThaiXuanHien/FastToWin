package com.hienthai.fastowin.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.*

class GiftProtocolTest {
    @Test fun `legacy claim JSON defaults gems while new receipts round trip`() {
        val gift = MailboxGift("gift", "Quà", "Gift", "Quà", "Gift", 100, 30, 1, 2, GiftState.CLAIMED)
        val encodedGift = ProtocolJson.encodeToString(gift)
        val legacy = """{"gift":$encodedGift,"awardedGold":100,"awardedXp":30,"gold":200,"xp":60}"""
        val old = ProtocolJson.decodeFromString<GiftClaimResult>(legacy)
        assertEquals(0, old.awardedGems); assertEquals(0, old.gems)
        val current = old.copy(gift = gift.copy(gems = 7), awardedGems = 7, gems = 14)
        assertEquals(current, ProtocolJson.decodeFromString<GiftClaimResult>(ProtocolJson.encodeToString(current)))
        val page = GiftMailboxPage(listOf(current.gift), 1, 1, 9, availableCount = 4)
        assertEquals(page, ProtocolJson.decodeFromString<GiftMailboxPage>(ProtocolJson.encodeToString(page)))
    }

    @Test fun `gem rewards in mailbox payload survive decode and encode`() {
        val payload = """{"campaignId":"gift","titleVi":"Quà","titleEn":"Gift","bodyVi":"Quà","bodyEn":"Gift","gold":0,"xp":0,"gems":25,"availableAtEpochMillis":1,"expiresAtEpochMillis":2,"state":"AVAILABLE"}"""
        val gift = ProtocolJson.decodeFromString<MailboxGift>(payload)
        val encoded = ProtocolJson.encodeToString(gift)
        assertTrue(encoded.contains("\"gems\":25"), encoded)
    }
    @Test fun `future metadata is ignored while exact rewards and safe stored body survive`() {
        val gift = MailboxGift("round", "Đền bù", "Compensation", "UTC [0,10). Lý do.",
            "UTC [0,10). Reason.", 0, Int.MAX_VALUE, 10, 20, GiftState.AVAILABLE,
            compensation = CompensationMailboxContext("incident", "INC-DEMO", Int.MAX_VALUE, 0, 10, "Lý do", "Reason"))
        val encoded = ProtocolJson.encodeToString(gift).replace("\"incidentKey\":", "\"futureMetadata\":true,\"incidentKey\":")
        assertEquals(gift, ProtocolJson.decodeFromString<MailboxGift>(encoded))
    }

    @Test fun `mailbox HTTP payload decodes independently of websocket message types`() {
        val payload="""{"items":[{"campaignId":"gift-id","titleVi":"Quà","titleEn":"Gift","bodyVi":"Cảm ơn","bodyEn":"Thanks","gold":100,"xp":30,"availableAtEpochMillis":1000,"expiresAtEpochMillis":2000,"state":"AVAILABLE"}],"page":0,"pageSize":25,"total":1}"""
        val page=ProtocolJson.decodeFromString<GiftMailboxPage>(payload)
        assertEquals("Quà",page.items.single().titleVi); assertNull(page.items.single().claimedAtEpochMillis)
        assertNull(page.items.single().compensation)
        assertEquals(0, page.items.single().gems); assertEquals(0L, page.availableCount)
        assertEquals(page,ProtocolJson.decodeFromString<GiftMailboxPage>(ProtocolJson.encodeToString(page)))
        assertFalse(ProtocolJson.encodeToString(page).contains("actorId"))
    }
    @Serializable private data class LegacyGift(val campaignId: String, val gold: Int, val xp: Int)
    @Test fun `legacy reader ignoring unknown fields accepts optional compensation metadata`() {
        val context = CompensationMailboxContext("incident-id", "OUTAGE", 2, 0, 10, "Ảnh hưởng", "Affected")
        val gift = MailboxGift("campaign-id", "Quà", "Gift", "An toàn", "Safe", 100, 30, 10, 20, GiftState.AVAILABLE, compensation = context)
        val encoded = ProtocolJson.encodeToString(gift)
        assertEquals(LegacyGift("campaign-id", 100, 30), Json { ignoreUnknownKeys = true }.decodeFromString<LegacyGift>(encoded))
        assertEquals(context, ProtocolJson.decodeFromString<MailboxGift>(encoded).compensation)
        for (privateField in listOf("causeCode", "evidence", "responsibleAdminId")) assertFalse(encoded.contains(privateField))
    }
    @Test fun `partial claim all preserves per item error and awarded balances`() {
        val payload="""{"items":[{"campaignId":"bad","error":{"code":"CONFLICT","message":"Expired"}}]}"""
        val result=ProtocolJson.decodeFromString<GiftClaimAllResult>(payload)
        assertNull(result.items.single().result); assertEquals("CONFLICT",result.items.single().error!!.code)
        assertEquals(result,ProtocolJson.decodeFromString<GiftClaimAllResult>(ProtocolJson.encodeToString(result)))
    }
}
