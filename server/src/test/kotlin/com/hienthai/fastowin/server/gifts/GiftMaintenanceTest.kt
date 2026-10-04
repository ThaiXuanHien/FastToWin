package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import com.hienthai.fastowin.server.GameEngine
import com.hienthai.fastowin.server.maintenance.MaintenancePolicy
import kotlin.test.*

class GiftMaintenanceTest {
    @Test fun policyCommittedWhileClaimWaitsForTransactionPreventsPayout() = runTest {
        val f = MemoryGiftFixture(); f.initialize(); val campaign = f.publish(); f.gifts.deliverDueBatch()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repository = object : GiftRepository {
            override suspend fun <T> transaction(admin: Boolean, block: GiftTransaction.() -> T): T {
                entered.complete(Unit); release.await(); return f.gifts.repository.transaction(admin, block)
            }
        }
        val engine = GameEngine()
        val gifts = GiftService(repository, f.admin, { f.now }, engine::newIntentAllowed)
        val claim = async { runCatching { gifts.claim(f.player, campaign.id) } }
        entered.await(); engine.publishMaintenancePolicy(MaintenancePolicy(MaintenanceState.MAINTENANCE)); release.complete(Unit)
        assertEquals("MAINTENANCE", (claim.await().exceptionOrNull() as AdminException).code)
        assertEquals(0, f.admin.wallet(f.owner.token, f.player, true, PageRequest()).total)
    }
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
