package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GiftMaintenanceTest {
    @Test fun newPayoutBlockedButAuthenticatedReceiptReplayAndClaimAllRemainReadable() = runTest {
        val f = MemoryGiftFixture(); f.initialize()
        val committed = f.publish(); val pending = f.publish(); f.gifts.deliverDueBatch()
        f.gifts.claim(f.player, committed.id)
        val closed = GiftService(f.gifts.repository, f.admin, { f.now }, newIntentAllowed = { false })
        assertEquals(100, closed.claim(f.player, committed.id).awardedGold)
        assertEquals("MAINTENANCE", assertFailsWith<AdminException> { closed.claim(f.player, pending.id) }.code)
        val all = closed.claimAll(f.player, GiftClaimAllRequest(listOf(committed.id, pending.id)))
        assertNotNull(all.items[0].result); assertEquals("MAINTENANCE", all.items[1].error?.code)
        assertEquals(1, f.admin.wallet(f.owner.token, f.player, true, PageRequest()).total)
        f.repository.seedUser(f.admin.user(f.owner.token, f.player).copy(status = "DELETED"))
        assertFailsWith<AdminException> { closed.claim(f.player, committed.id) }
    }
}
