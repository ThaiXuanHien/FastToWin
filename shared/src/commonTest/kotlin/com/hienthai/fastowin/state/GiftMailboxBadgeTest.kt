package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.GiftGateway
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GiftMailboxBadgeTest {
    private fun gift(id: String, state: GiftState = GiftState.AVAILABLE) = MailboxGift(
        id, "Quà", "Gift", "Nội dung", "Message", 25, 5, 1000, 9000, state
    )

    @Test fun `summary finds unseen available gifts without opening or downloading the mailbox`() = runTest {
        val requests = mutableListOf<Pair<Int, Int>>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage {
                requests += page to pageSize
                return GiftMailboxPage(listOf(gift("old", GiftState.CLAIMED)), 0, 1, 120, availableCount = 73)
            }
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {})
        controller.refreshAvailableCount()
        assertEquals(73L, controller.state.value.availableCount)
        assertEquals(listOf(0 to 1), requests)
        assertFalse(controller.state.value.isOpen)
        assertTrue(controller.state.value.items.isEmpty())
        controller.reset()
        assertEquals(73L, controller.state.value.availableCount)
        controller.close()
        assertEquals(0L, controller.state.value.availableCount)
    }

    @Test fun `claim reduces badge after route away and duplicate receipt never reduces it twice`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var serverCount = 3L
        var claims = 0
        val duplicateSummaryEntered = CompletableDeferred<Unit>()
        val duplicateSummaryRelease = CompletableDeferred<Unit>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage {
                if (pageSize == 1 && claims == 2) {
                    duplicateSummaryEntered.complete(Unit)
                    duplicateSummaryRelease.await()
                }
                return GiftMailboxPage(listOf(gift("a")), page, pageSize, 3, availableCount = serverCount)
            }
            override suspend fun claim(id: String): GiftClaimResult {
                claims++
                if (!entered.isCompleted) { entered.complete(Unit); release.await() }
                serverCount = 2
                return GiftClaimResult(gift(id, GiftState.CLAIMED), 25, 5, 25, 5)
            }
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {})
        controller.open()
        val claim = async { controller.claim("a") }
        entered.await()
        controller.reset()
        release.complete(Unit)
        claim.await()
        assertEquals(2L, controller.state.value.availableCount)
        assertTrue(controller.state.value.items.isEmpty())
        // A stale list may offer the same id again; its idempotent receipt cannot decrement twice.
        controller.open()
        val duplicateClaim = async { controller.claim("a") }
        duplicateSummaryEntered.await()
        // Observe the local badge before an authoritative refresh could mask a second decrement.
        assertEquals(2L, controller.state.value.availableCount)
        duplicateSummaryRelease.complete(Unit)
        duplicateClaim.await()
        assertEquals(2L, controller.state.value.availableCount)
    }

    @Test fun `summary started before claim cannot resurrect claimed gift badge`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var serverCount = 1L
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage {
                val snapshot = serverCount
                if (pageSize == 1 && !entered.isCompleted) { entered.complete(Unit); release.await() }
                return GiftMailboxPage(listOf(gift("a")), page, pageSize, 1, availableCount = snapshot)
            }
            override suspend fun claim(id: String): GiftClaimResult {
                serverCount = 0
                return GiftClaimResult(gift(id, GiftState.CLAIMED), 25, 5, 25, 5)
            }
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {})
        controller.open()
        val summary = async { controller.refreshAvailableCount() }
        entered.await()
        controller.claim("a")
        assertEquals(0L, controller.state.value.availableCount)
        release.complete(Unit)
        summary.await()
        assertEquals(0L, controller.state.value.availableCount)
    }

    @Test fun `closed account ignores delayed summary and failures retain last known count`() = runTest {
        var delayed = false
        var fails = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage {
                if (fails) error("offline")
                if (delayed) { entered.complete(Unit); release.await() }
                return GiftMailboxPage(emptyList(), page, pageSize, 200, availableCount = 4)
            }
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {})
        controller.refreshAvailableCount()
        fails = true
        controller.refreshAvailableCount()
        assertEquals(4L, controller.state.value.availableCount)
        fails = false
        delayed = true
        val request = async { controller.refreshAvailableCount() }
        entered.await()
        controller.close()
        release.complete(Unit)
        request.await()
        assertEquals(0L, controller.state.value.availableCount)
    }

    @Test fun `claim all decrements successful receipts only and keeps failed gifts badged`() = runTest {
        var serverCount = 2L
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(
                listOf(gift("a"), gift("b"), gift("expired", GiftState.EXPIRED), gift("cancelled", GiftState.CANCELLED)),
                page, pageSize, 4, availableCount = serverCount
            )
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>): GiftClaimAllResult {
                serverCount = 1
                return GiftClaimAllResult(listOf(
                GiftClaimItem("a", result = GiftClaimResult(gift("a", GiftState.CLAIMED), 25, 5, 25, 5)),
                GiftClaimItem("b", error = GiftError("CONFLICT", "expired"))
                ))
            }
        }
        val controller = GiftMailboxController(gateway, {})
        controller.open()
        controller.claimAll()
        assertEquals(1L, controller.state.value.availableCount)
        assertEquals("CONFLICT", controller.state.value.itemErrors["b"])
    }

    @Test fun `older pagination cannot overwrite count or receipt accepted during its request`() = runTest {
        var serverCount = 2L
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage {
                val snapshot = serverCount
                if (page == 1) { entered.complete(Unit); release.await() }
                return GiftMailboxPage(listOf(gift(if (page == 0) "a" else "b")), page, pageSize, 2, availableCount = snapshot)
            }
            override suspend fun claim(id: String): GiftClaimResult {
                serverCount = 1
                return GiftClaimResult(gift(id, GiftState.CLAIMED), 25, 5, 25, 5)
            }
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {}, pageSize = 2)
        controller.open()
        val more = async { controller.loadNextPage() }
        entered.await()
        controller.claim("a")
        assertEquals(1L, controller.state.value.availableCount)
        release.complete(Unit)
        more.await()
        assertEquals(1L, controller.state.value.availableCount)
        assertEquals(GiftState.CLAIMED, controller.state.value.items.first().state)
        assertEquals(listOf("a", "b"), controller.state.value.items.map { it.campaignId })
    }

    @Test fun `opening mailbox fences an older background summary`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage {
                if (pageSize == 1) {
                    entered.complete(Unit)
                    release.await()
                    return GiftMailboxPage(emptyList(), 0, 1, 50, availableCount = 8)
                }
                return GiftMailboxPage(listOf(gift("old", GiftState.EXPIRED)), 0, pageSize, 50, availableCount = 0)
            }
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {})
        val summary = async { controller.refreshAvailableCount() }
        entered.await()
        controller.open()
        release.complete(Unit)
        summary.await()
        assertEquals(0L, controller.state.value.availableCount)
        assertTrue(controller.state.value.isOpen)
        assertEquals(GiftState.EXPIRED, controller.state.value.items.single().state)
    }


    @Test fun `reopening during an older claim all reloads the mailbox when that batch completes`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var claimed = false
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(
                listOf(gift("a", if (claimed) GiftState.CLAIMED else GiftState.AVAILABLE)),
                page, pageSize, 1, availableCount = if (claimed) 0 else 1
            )
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>): GiftClaimAllResult {
                entered.complete(Unit)
                release.await()
                claimed = true
                return GiftClaimAllResult(listOf(GiftClaimItem("a", result =
                    GiftClaimResult(gift("a", GiftState.CLAIMED), 25, 5, 25, 5))))
            }
        }
        val controller = GiftMailboxController(gateway, {})
        controller.open()
        val batch = async { controller.claimAll() }
        entered.await()
        controller.reset()
        controller.open()
        val loadingWhileWaiting = controller.state.value.isLoading
        release.complete(Unit)
        batch.await()
        assertTrue(loadingWhileWaiting)
        assertTrue(controller.state.value.isOpen)
        assertFalse(controller.state.value.isLoading)
        assertEquals(GiftState.CLAIMED, controller.state.value.items.single().state)
        assertEquals(0L, controller.state.value.availableCount)
    }

}
