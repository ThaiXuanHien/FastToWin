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
    @Test fun `race batch rejects an unconfirmed receipt and retries through the same mailbox endpoint`() = runTest {
        val race = ClanRaceMailboxContext("entitlement", "2026-10-26", 1, 9, "clan", "Frozen clan", 2,
            "1", "0.000001", true, 9, 10, 10000, ClanRaceReward(7, 8, 9), "CLAN_WEEKLY_RACE")
        val available = gift("race").copy(clanRace = race)
        var confirmed = false
        var refreshes = 0
        val batches = mutableListOf<List<String>>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(listOf(available), page, pageSize, 1, 1)
            override suspend fun claim(id: String) = error("single flow unused")
            override suspend fun claimAll(ids: List<String>): GiftClaimAllResult {
                batches += ids
                val response = available.copy(state = if(confirmed) GiftState.CLAIMED else GiftState.AVAILABLE)
                return GiftClaimAllResult(listOf(GiftClaimItem("race", GiftClaimResult(response, 8, 7, 99, 88, 9, 77))))
            }
        }
        val controller = GiftMailboxController(gateway, { refreshes++ })
        controller.open(); controller.claimAll()
        assertEquals(0, refreshes)
        assertEquals("NETWORK_ERROR", controller.state.value.itemErrors["race"])
        confirmed = true; controller.claimAll()
        assertEquals(listOf(listOf("race"), listOf("race")), batches)
        assertEquals(1, refreshes)
        assertEquals(GiftState.CLAIMED, controller.state.value.items.single().state)
    }

    @Test fun `race gift waits for matching claimed receipt and retains idempotent existing claim flow`() = runTest {
        val race = ClanRaceMailboxContext("entitlement", "2026-10-26", 1, 9, "clan", "Frozen clan", 2,
            "1", "0.000001", true, 9, 10, 10000, ClanRaceReward(7, 8, 9), "CLAN_WEEKLY_RACE")
        val available = gift("race").copy(clanRace = race, gold = 8, xp = 7, gems = 9)
        var valid = false
        var claims = 0
        var refreshes = 0
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(listOf(available), page, pageSize, 1, 1)
            override suspend fun claim(id: String): GiftClaimResult {
                claims++
                return GiftClaimResult(available.copy(state = if(valid) GiftState.CLAIMED else GiftState.AVAILABLE), 8, 7, 99, 88, 9, 77)
            }
            override suspend fun claimAll(ids: List<String>) = error("unused")
        }
        val controller = GiftMailboxController(gateway, { refreshes++ })
        controller.open(); controller.claim("race")
        assertEquals(0, refreshes, "an available receipt is not server-confirmed success")
        assertEquals("NETWORK_ERROR", controller.state.value.itemErrors["race"])
        valid = true
        controller.claim("race"); controller.claim("race")
        assertEquals(2, claims)
        assertEquals(1, refreshes)
        assertEquals(GiftState.CLAIMED, controller.state.value.items.single().state)
        assertEquals(race, controller.state.value.items.single().clanRace)
    }

    @Test fun `compensation uses existing claim receipt and profile refresh authority`() = runTest {
        val context = CompensationMailboxContext("incident", "INC-DEMO", 2, 0, 10, "Lý do", "Reason")
        val available = gift("round").copy(gold = 0, xp = Int.MAX_VALUE, compensation = context)
        val receipt = available.copy(state = GiftState.CLAIMED, claimedAtEpochMillis = 2000)
        val claims = mutableListOf<String>()
        val gateway = object : GiftGateway {
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(listOf(available, gift("ordinary")), 0, 25, 2)
            override suspend fun claim(id: String): GiftClaimResult {
                claims += id
                return GiftClaimResult(receipt, 0, Int.MAX_VALUE, 7, 13)
            }
            override suspend fun claimAll(ids: List<String>) = error("no additional claim flow")
        }
        var refreshes = 0
        val controller = GiftMailboxController(gateway, { refreshes++ })
        controller.open()
        assertEquals(context, controller.state.value.items.first().compensation)
        assertEquals(null, controller.state.value.items.last().compensation)
        controller.claim("round")
        controller.claim("round")
        assertEquals(listOf("round"), claims)
        assertEquals(receipt, controller.state.value.items.first())
        assertEquals(1, refreshes)
        assertEquals(gift("ordinary"), controller.state.value.items.last())
    }

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

    @Test fun `route away fences a late claim receipt but refreshes same-account profile`() = runTest {
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
        assertEquals(1, refreshes)
    }

    @Test fun `closed account ignores a late claim receipt and profile refresh`() = runTest {
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
        var oldRefreshes = 0
        var newRefreshes = 0
        val oldController = GiftMailboxController(gateway, { oldRefreshes++ })
        oldController.open()
        val request = async { oldController.claim("a") }
        entered.await()
        oldController.close() // Account/controller replacement disposes the old mailbox.
        val replacement = GiftMailboxController(gateway, { newRefreshes++ })
        release.complete(Unit)
        request.await()
        assertFalse(oldController.state.value.isOpen)
        assertEquals(0, oldRefreshes)
        assertEquals(0, newRefreshes)
        replacement.close()
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
