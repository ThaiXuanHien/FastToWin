package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class CompensationGiftClaimTest {
    @Test fun `persisted compensation metadata selects compensation wallet source`() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        val campaign = f.publish()
        val context = CompensationMailboxContext(UUID.randomUUID().toString(), "OUTAGE_1", 1, 0, 10, "Bị ảnh hưởng", "Affected")
        f.gifts.repository.transaction { putCampaign(campaign.copy(compensation = context)) }
        f.gifts.deliverDueBatch(); f.gifts.claim(f.player, campaign.id); f.gifts.claim(f.player, campaign.id)
        val wallet = f.admin.wallet(f.owner.token, f.player, true, PageRequest()).items.single()
        assertEquals("COMPENSATION", wallet.sourceType)
        assertEquals(campaign.id, wallet.sourceId)
        assertEquals(100, f.admin.user(f.owner.token, f.player).gold)
        assertEquals(context, f.gifts.mailbox(f.player, PageRequest()).items.single().compensation)
        assertEquals(0, f.gifts.list(f.owner, PageRequest()).total)
        assertEquals(404, assertFailsWith<AdminException> { f.gifts.detail(f.owner, campaign.id) }.status)
        assertEquals(404, assertFailsWith<AdminException> { f.gifts.cancel(f.owner, campaign.id, GiftCancelRequest(2, "cancel", "Stop")) }.status)
    }
}
