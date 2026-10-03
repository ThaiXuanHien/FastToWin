package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.GiftGateway
import com.hienthai.fastowin.data.network.GiftApiException
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GiftMailboxControllerTest {
    private fun gift(id: String, state: GiftState = GiftState.AVAILABLE) = MailboxGift(
        id, "Quà $id", "Gift $id", "Nội dung", "Message", 25, 5,
        1000, 9000, state
    )

    @Test fun `claim all visits every server page and preserves partial errors for retry`() = runTest {
        val pages = listOf(listOf(gift("a"), gift("b")), listOf(gift("c")))
        val gateway = object : GiftGateway {
            val batches = mutableListOf<List<String>>()
            override suspend fun page(page: Int, pageSize: Int) =
                GiftMailboxPage(pages.getOrElse(page) { emptyList() }, page, 2, 3)
            override suspend fun claim(id: String) = GiftClaimResult(gift(id, GiftState.CLAIMED), 25, 5, 100, 50)
            override suspend fun claimAll(ids: List<String>): GiftClaimAllResult {
                batches += ids
                return GiftClaimAllResult(ids.map { id ->
                    if (id == "b") GiftClaimItem(id, error = GiftError("CONFLICT", "server text"))
                    else GiftClaimItem(id, result = GiftClaimResult(gift(id, GiftState.CLAIMED), 25, 5, 100, 50))
                })
            }
        }
        var refreshes = 0
        val controller = GiftMailboxController(gateway, { refreshes++ }, pageSize = 2)
        controller.open()
        controller.claimAll()
        assertEquals(listOf(listOf("a", "b", "c")), gateway.batches)
        assertEquals(listOf("a", "b", "c"), controller.state.value.items.map { it.campaignId })
        assertEquals(listOf(GiftState.CLAIMED, GiftState.AVAILABLE, GiftState.CLAIMED), controller.state.value.items.map { it.state })
        assertEquals("CONFLICT", controller.state.value.itemErrors["b"])
        assertEquals(1, refreshes)
        controller.claim("b")
        assertEquals(GiftState.CLAIMED, controller.state.value.items[1].state)
        assertFalse(controller.state.value.itemErrors.containsKey("b"))
    }

    @Test fun `claim all remains offered when only an unseen page has available gifts`() = runTest {
        val gateway = object : GiftGateway {
            val batches = mutableListOf<List<String>>()
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(
                if (page == 0) listOf(gift("claimed", GiftState.CLAIMED)) else listOf(gift("ready")),
                page, 1, 2
            )
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>): GiftClaimAllResult {
                batches += ids
                return GiftClaimAllResult(ids.map { GiftClaimItem(it, result = GiftClaimResult(gift(it, GiftState.CLAIMED), 25, 5, 25, 5)) })
            }
        }
        val controller = GiftMailboxController(gateway, {})
        controller.open()
        assertFalse(controller.state.value.hasAvailable)
        assertTrue(controller.state.value.canClaimAll)
        controller.claimAll()
        assertEquals(listOf(listOf("ready")), gateway.batches)
        assertFalse(controller.state.value.canClaimAll)
    }

    @Test fun `route away fences a late claim receipt and profile refresh`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(listOf(gift("a")), 0, 25, 1)
            override suspend fun claim(id: String): GiftClaimResult {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return GiftClaimResult(gift(id, GiftState.CLAIMED), 25, 5, 25, 5)
            }
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        var refreshes = 0
        val controller = GiftMailboxController(gateway, { refreshes++ })
        controller.open()
        val request = async { controller.claim("a") }
        entered.await()
        controller.reset() // The /gifts route-away transition invokes this lifecycle fence.
        release.complete(Unit)
        request.await()
        assertFalse(controller.state.value.isOpen)
        assertTrue(controller.state.value.items.isEmpty())
        assertEquals(0, refreshes)
    }

    @Test fun `claim all with no available gifts is harmless`() = runTest {
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(
                listOf(gift("claimed", GiftState.CLAIMED), gift("expired", GiftState.EXPIRED)), page, pageSize, 2
            )
            override suspend fun claim(id: String) = error("unexpected claim")
            override suspend fun claimAll(ids: List<String>) = error("unexpected batch")
        }
        var refreshes = 0
        val controller = GiftMailboxController(gateway, { refreshes++ })
        controller.open()
        controller.claimAll()
        assertFalse(controller.state.value.canClaimAll)
        assertFalse(controller.state.value.isClaimingAll)
        assertTrue(controller.state.value.itemErrors.isEmpty())
        assertEquals(0, refreshes)
    }

    @Test fun `dropped claim response keeps gift retryable and server receipt wins`() = runTest {
        var calls = 0
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(listOf(gift("a")), 0, 25, 1)
            override suspend fun claim(id: String): GiftClaimResult {
                calls++
                if (calls == 1) throw IllegalStateException("dropped response")
                return GiftClaimResult(gift(id, GiftState.CLAIMED), 25, 5, 125, 55)
            }
            override suspend fun claimAll(ids: List<String>) = GiftClaimAllResult(emptyList())
        }
        var refreshes = 0
        val controller = GiftMailboxController(gateway, { refreshes++ })
        controller.open()
        controller.claim("a")
        assertEquals(GiftState.AVAILABLE, controller.state.value.items.single().state)
        assertTrue(controller.state.value.itemErrors.containsKey("a"))
        controller.claim("a")
        assertEquals(GiftState.CLAIMED, controller.state.value.items.single().state)
        assertEquals(1, refreshes)
    }

    @Test fun `account reset fences a late response`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return GiftMailboxPage(listOf(gift("old")), 0, 25, 1)
            }
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {})
        val request = async { controller.open() }
        entered.await()
        controller.reset()
        release.complete(Unit)
        request.await()
        assertTrue(controller.state.value.items.isEmpty())
        assertFalse(controller.state.value.isOpen)
    }

    @Test fun `expired and cancelled gifts never enter claim batch`() = runTest {
        val gateway = object : GiftGateway {
            var ids = emptyList<String>()
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(
                listOf(gift("expired", GiftState.EXPIRED), gift("cancelled", GiftState.CANCELLED), gift("ready")), 0, 25, 3
            )
            override suspend fun claim(id: String) = error("unexpected claim")
            override suspend fun claimAll(ids: List<String>): GiftClaimAllResult {
                this.ids = ids
                return GiftClaimAllResult(ids.map { GiftClaimItem(it, result = GiftClaimResult(gift(it, GiftState.CLAIMED), 25, 5, 25, 5)) })
            }
        }
        val controller = GiftMailboxController(gateway, {})
        controller.open()
        controller.claimAll()
        assertEquals(listOf("ready"), gateway.ids)
    }

    @Test fun `conflicting single claim reloads final expired state from server`() = runTest {
        var finalState = GiftState.AVAILABLE
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(listOf(gift("a", finalState)), 0, 25, 1)
            override suspend fun claim(id: String): GiftClaimResult {
                finalState = GiftState.EXPIRED
                throw GiftApiException("CONFLICT", "expired")
            }
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, {})
        controller.open()
        controller.claim("a")
        assertEquals(GiftState.EXPIRED, controller.state.value.items.single().state)
    }
}
