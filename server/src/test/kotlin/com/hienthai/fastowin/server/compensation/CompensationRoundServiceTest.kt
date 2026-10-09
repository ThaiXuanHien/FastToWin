package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import com.hienthai.fastowin.protocol.CompensationMailboxContext
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.*

@Serializable private data class LegacyCompensationGift(val bodyVi: String, val bodyEn: String)
@Serializable private data class LegacyCompensationMailbox(val items: List<LegacyCompensationGift>)
private val legacyCompensationReader = Json { ignoreUnknownKeys = true }

class CompensationRoundServiceTest {
    private suspend fun fixture(block: suspend (MemoryGiftFixture, CompensationRoundService, CompensationIncident) -> Unit) {
        val f = MemoryGiftFixture(); f.initialize()
        val i = CompensationIncidentService(f.gifts.repository, f.admin, { f.now }).create(f.owner,
            CompensationIncidentRequest("OUTAGE", "Sự cố", "Incident", "An toàn", "Safe", 0, 10, "PRIVATE", "PRIVATE", f.admin.session(f.owner.token).admin.id, "incident", "Fixture"))
        block(f, CompensationRoundService(f.gifts.repository, f.admin, { f.now }), i)
    }
    private fun request(f: MemoryGiftFixture) = CompensationRoundRequest(1, "Ảnh hưởng", "Affected", 100, 30, f.now, "round", "Fixture", expectedNextRoundNumber = 1)
    @Test fun `new request duplicate default round one conflicts`() = runTest { fixture { f, s, i ->
        s.create(f.owner, i.id, request(f))
        assertEquals(409, assertFailsWith<AdminException> { s.create(f.owner, i.id, request(f).copy(requestId = "duplicate")) }.status)
    } }
    @Test fun `round number and supplemental reason are explicitly required`() = runTest { fixture { f, s, i ->
        for (bad in listOf(request(f).copy(expectedNextRoundNumber = null), request(f).copy(expectedNextRoundNumber = 2)))
            assertEquals(400, assertFailsWith<AdminException> { s.create(f.owner, i.id, bad) }.status)
    } }
    @Test fun `activity unavailable rejects rather than selecting zero`() = runTest { fixture { f, s, i ->
        assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", assertFailsWith<AdminException> { s.create(f.owner, i.id, request(f).copy(audience = CompensationAudience.ACTIVITY)) }.code)
    } }
    @Test fun `exact publish retry returns its committed result`() = runTest { fixture { f, s, i ->
        val c = s.create(f.owner, i.id, request(f)); val preview = s.preview(f.owner, i.id, c.campaign.id, CompensationRoundPreviewRequest(1, 1, "preview", "Fixture"))
        val p = CompensationRoundPublishRequest(1, 1, preview.previewToken, "publish", "Fixture")
        val first = s.publish(f.owner, i.id, c.campaign.id, p)
        assertEquals(first, s.publish(f.owner, i.id, c.campaign.id, p))
        f.gifts.repository.transaction { assertEquals(1, compensation.maximumRoundNumber(i.id)) }
    } }
    @Test fun `concurrent administrators cannot create two default rounds`() = runTest { fixture { f, s, i ->
        val results = coroutineScope { List(2) { n -> async { runCatching { s.create(f.owner, i.id, request(f).copy(requestId = "concurrent$n")) } } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess })
    } }
    @Test fun `previous user distinct count differs from paid entitlements`() = runTest { fixture { f, s, i ->
        f.gifts.repository.transaction(true) {
            for (n in 1..2) {
                val id = UUID.randomUUID().toString()
                putCampaign(GiftCampaign(id, 2, CampaignState.PUBLISHED, f.request().copy(expiresAtEpochMillis = f.now + 2592000000), f.now, f.now,
                    compensation = CompensationMailboxContext(i.id, i.incidentKey, n, 0, 10, "Ảnh hưởng", "Affected")))
                compensation.putRound(CompensationRound(id, i.id, n, if (n > 1) "Additional" else null, "Ảnh hưởng", "Affected"))
                freeze(id, listOf(f.player))
            }
        }
        f.gifts.deliverDueBatch()
        val earlier = f.gifts.repository.transaction { compensation.rounds(i.id, PageRequest()).items }
        for (round in earlier) f.gifts.claim(f.player, round.campaignId)
        val c = s.create(f.owner, i.id, request(f).copy(expectedNextRoundNumber = 3, supplementalReason = "Additional"))
        val p = s.preview(f.owner, i.id, c.campaign.id, CompensationRoundPreviewRequest(1, 1, "preview", "Fixture"))
        assertEquals("1", p.previousClaimedUserCount); assertEquals("2", p.previousClaimedEntitlementCount); assertEquals("1", p.selectedPreviouslyClaimedUserCount)
    } }
    @Test fun `publication freezes recipients and excludes later registered accounts`() = runTest { fixture { f, s, i ->
        val c = s.create(f.owner, i.id, request(f))
        val p = s.preview(f.owner, i.id, c.campaign.id, CompensationRoundPreviewRequest(1, 1, "preview", "Fixture"))
        val published = s.publish(f.owner, i.id, c.campaign.id, CompensationRoundPublishRequest(1, 1, p.previewToken, "publish", "Fixture"))
        assertEquals("1", published.report.eligible)
        val late = UUID.randomUUID().toString(); val player = f.admin.user(f.owner.token, f.player)
        f.repository.seedUser(player.copy(id = late, email = "late@example.com"))
        f.gifts.deliverDueBatch()
        assertTrue(f.gifts.mailbox(late, PageRequest()).items.isEmpty())
        assertEquals(404, assertFailsWith<AdminException> { f.gifts.claim(late, c.campaign.id) }.status)
        assertEquals(100, f.gifts.claim(f.player, c.campaign.id).awardedGold)
    } }
    @Test fun `maximum copy and UTC envelope survive legacy mailbox reader intact`() = runTest { fixture { f, s, original ->
        val incidents = CompensationIncidentService(f.gifts.repository, f.admin, { f.now })
        val i = incidents.update(f.owner, original.id, CompensationIncidentRequest(original.incidentKey, original.titleVi, original.titleEn,
            "v".repeat(4000), "e".repeat(4000), 253402300799998, 253402300799999, "PRIVATE", "PRIVATE", original.responsibleAdminId, "edit", "Fixture", 1))
        val c = s.create(f.owner, i.id, request(f).copy(incidentVersion = 2, receivingReasonVi = "r".repeat(1000), receivingReasonEn = "s".repeat(1000)))
        for ((body, description, reason) in listOf(Triple(c.campaign.draft.bodyVi, i.descriptionVi, "r".repeat(1000)), Triple(c.campaign.draft.bodyEn, i.descriptionEn, "s".repeat(1000)))) {
            assertTrue(body.length in 5001..5200); assertTrue(body.contains(description)); assertTrue(body.endsWith(reason))
            assertTrue(body.contains("9999-12-31T23:59:59.998Z")); assertTrue(body.contains("9999-12-31T23:59:59.999Z"))
        }
        val p = s.preview(f.owner, i.id, c.campaign.id, CompensationRoundPreviewRequest(2, 1, "preview", "Fixture"))
        s.publish(f.owner, i.id, c.campaign.id, CompensationRoundPublishRequest(2, 1, p.previewToken, "publish", "Fixture")); f.gifts.deliverDueBatch()
        val mailbox = f.gifts.mailbox(f.player, PageRequest())
        val encoded = com.hienthai.fastowin.protocol.ProtocolJson.encodeToString(com.hienthai.fastowin.protocol.GiftMailboxPage.serializer(), mailbox)
        val oldReader = legacyCompensationReader.decodeFromString<LegacyCompensationMailbox>(encoded)
        assertEquals(c.campaign.draft.bodyVi, oldReader.items.single().bodyVi); assertEquals(c.campaign.draft.bodyEn, oldReader.items.single().bodyEn)
        assertEquals(c.campaign.draft.bodyVi, mailbox.items.single().bodyVi); assertEquals(c.campaign.draft.bodyEn, mailbox.items.single().bodyEn)
        assertEquals(400, assertFailsWith<AdminException> { f.gifts.create(f.owner, f.request().copy(bodyVi = "v".repeat(4001))) }.status)
    } }
}
