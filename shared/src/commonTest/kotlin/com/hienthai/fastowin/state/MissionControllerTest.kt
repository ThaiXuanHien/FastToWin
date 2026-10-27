package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MissionControllerTest {
    private class Gateway : MissionGateway {
        val loads = mutableListOf<CompletableDeferred<MissionListResponse>>()
        val claims = mutableListOf<Pair<String, CompletableDeferred<MissionClaimResponse>>>()
        override suspend fun list(scope: MissionScope): MissionListResponse = CompletableDeferred<MissionListResponse>().also { loads += it }.await()
        override suspend fun claim(instanceId: String, requestId: String): MissionClaimResponse = CompletableDeferred<MissionClaimResponse>().also { claims += requestId to it }.await()
    }
    private fun row(id: String, state: MissionClaimState = MissionClaimState.CLAIMABLE) = MissionInstanceView(id, "ADMIN_CODE", MissionScope.PERSONAL, MissionAction.PLAY, MissionCadence.DAILY, MissionDifficulty.NORMAL, "2026-10-24", "2026-10-25", 7, 7, 7, MissionReward(gold=50), state, "MissionActionPLAY", mapOf("target" to "7"))
    private fun response(vararg rows: MissionInstanceView) = MissionListResponse(rows.toList(), 1)
    @Test fun `old account response cannot replace current mission list`() = runTest {
        val api = Gateway(); val controller = MissionController(api, {})
        controller.setContext("A", MissionScope.PERSONAL)
        val a = launch { controller.refresh() }; runCurrent(); assertTrue(controller.state.value.isLoading, "Opening missions must show loading before a response")
        controller.setContext("B", MissionScope.PERSONAL)
        val b = launch { controller.refresh() }; runCurrent()
        api.loads[1].complete(response(row("B"))); b.join()
        api.loads[0].complete(response(row("A"))); a.join()
        assertEquals(listOf("B"), controller.state.value.items.map { it.instanceId })
    }
    @Test fun `stale claim does not refresh another account wallet`() = runTest {
        val api = Gateway(); var wallets = 0; val controller = MissionController(api, { wallets++ })
        controller.setContext("A", MissionScope.PERSONAL)
        val load = launch { controller.refresh() }; runCurrent(); api.loads[0].complete(response(row("A"))); load.join()
        val claim = launch { controller.claim("A") }; runCurrent()
        controller.setContext("B", MissionScope.PERSONAL)
        api.claims[0].second.complete(MissionClaimResponse("A", MissionClaimState.CLAIMED, MissionReward(gold=50))); claim.join()
        assertEquals(0, wallets); assertTrue(controller.state.value.items.isEmpty()); assertTrue(controller.state.value.pendingIds.isEmpty())
    }
    @Test fun `unknown result retry keeps intent and older list cannot reset claimed state`() = runTest {
        val api = Gateway(); var wallets=0; var ids=0; val controller = MissionController(api, { wallets++ }, { "request-${++ids}" })
        controller.setContext("A", MissionScope.PERSONAL)
        val load = launch { controller.refresh() }; runCurrent(); api.loads[0].complete(response(row("A"))); load.join()
        val stale = launch { controller.refresh() }; runCurrent()
        val failed = launch { controller.claim("A") }; runCurrent(); api.claims[0].second.completeExceptionally(Error("browser offline")); failed.join()
        assertFalse(controller.state.value.pendingIds.contains("A")); assertEquals("NETWORK_ERROR", controller.state.value.itemErrors["A"])
        val retry = launch { controller.claim("A") }; runCurrent(); api.claims[1].second.complete(MissionClaimResponse("A", MissionClaimState.CLAIMED, MissionReward(gold=50))); retry.join()
        api.loads[1].complete(response(row("A"))); stale.join()
        assertEquals(listOf("request-1", "request-1"), api.claims.map { it.first })
        assertEquals(MissionClaimState.CLAIMED, controller.state.value.items.single().state); assertEquals(1, wallets)
    }
    @Test fun `browser error and cancellation reset loading and pending`() = runTest {
        val api=Gateway(); val controller=MissionController(api,{})
        controller.setContext("A", MissionScope.PERSONAL)
        val fail=launch { controller.refresh() }; runCurrent(); api.loads[0].completeExceptionally(Error("fetch")); fail.join()
        assertFalse(controller.state.value.isLoading); assertEquals("NETWORK_ERROR",controller.state.value.loadError)
        val load=launch { controller.refresh() }; runCurrent(); api.loads[1].complete(response(row("A"))); load.join()
        val claim=launch { controller.claim("A") }; runCurrent(); claim.cancelAndJoin()
        assertTrue(controller.state.value.pendingIds.isEmpty()); assertTrue(claim.isCancelled)
        val cancel=launch { controller.refresh() }; runCurrent(); cancel.cancelAndJoin(); assertFalse(controller.state.value.isLoading)
    }
    @Test fun `view and clan changes fence completions and latest refresh wins`() = runTest {
        val api=Gateway(); val controller=MissionController(api,{})
        controller.setContext("A", MissionScope.CLAN,"clan-1","view-1")
        val first=launch { controller.refresh() }; runCurrent()
        controller.setContext("A", MissionScope.CLAN,"clan-2","view-1")
        val second=launch { controller.refresh() }; runCurrent()
        val third=launch { controller.refresh() }; runCurrent()
        api.loads[2].complete(response(row("latest"))); third.join()
        api.loads[1].complete(response(row("older"))); second.join()
        api.loads[0].complete(response(row("old-clan"))); first.join()
        assertEquals("latest",controller.state.value.items.single().instanceId)
        controller.setContext("A",MissionScope.CLAN,"clan-2","closed")
        assertTrue(controller.state.value.items.isEmpty())
    }
    @Test fun `claimed snapshot reconciles an unknown result and refreshes authoritative wallet once`() = runTest {
        val api=Gateway(); var wallets=0; val controller=MissionController(api,{wallets++})
        controller.setContext("A",MissionScope.PERSONAL)
        val load=launch { controller.refresh() }; runCurrent(); api.loads[0].complete(response(row("A"))); load.join()
        val claim=launch { controller.claim("A") }; runCurrent(); api.claims[0].second.completeExceptionally(Error("response lost")); claim.join()
        val reconcile=launch { controller.refresh() }; runCurrent(); api.loads[1].complete(response(row("A",MissionClaimState.CLAIMED))); reconcile.join()
        assertEquals(1,wallets); assertEquals(MissionClaimState.CLAIMED,controller.state.value.items.single().state); assertTrue(controller.state.value.itemErrors.isEmpty())
        val repeat=launch { controller.refresh() }; runCurrent(); api.loads[2].complete(response(row("A",MissionClaimState.CLAIMED))); repeat.join()
        assertEquals(1,wallets)
    }
    @Test fun `past claimed row disappearing after lost response reconciles wallet without inventing state`() = runTest {
        val api=Gateway(); var wallets=0; val controller=MissionController(api,{wallets++})
        controller.setContext("A",MissionScope.PERSONAL)
        val load=launch { controller.refresh() }; runCurrent(); api.loads[0].complete(response(row("past"))); load.join()
        val claim=launch { controller.claim("past") }; runCurrent(); api.claims[0].second.completeExceptionally(Error("response lost")); claim.join()
        val reconcile=launch { controller.refresh() }; runCurrent(); api.loads[1].complete(response()); reconcile.join()
        assertEquals(1,wallets); assertTrue(controller.state.value.items.isEmpty())
    }
    @Test fun `token epoch fences response and same account retry preserves intent across view and token changes`() = runTest {
        val api=Gateway(); val controller=MissionController(api,{},{"stable-intent"})
        controller.setContext("A",MissionScope.PERSONAL,sessionEpoch="old-token")
        val old=launch { controller.refresh() }; runCurrent()
        controller.setContext("A",MissionScope.PERSONAL,sessionEpoch="new-token")
        api.loads[0].complete(response(row("stale"))); old.join(); assertTrue(controller.state.value.items.isEmpty())
        val load=launch { controller.refresh() }; runCurrent(); api.loads[1].complete(response(row("A"))); load.join()
        val claim=launch { controller.claim("A") }; runCurrent(); api.claims[0].second.completeExceptionally(Error("lost")); claim.join()
        controller.setContext("A",MissionScope.PERSONAL,view="closed",sessionEpoch="new-token")
        controller.setContext("A",MissionScope.PERSONAL,sessionEpoch="third-token")
        val reload=launch { controller.refresh() }; runCurrent(); api.loads[2].complete(response(row("A"))); reload.join()
        val retry=launch { controller.claim("A") }; runCurrent(); api.claims[1].second.complete(MissionClaimResponse("A",MissionClaimState.CLAIMED,MissionReward())); retry.join()
        assertEquals(listOf("stable-intent","stable-intent"),api.claims.map{it.first})
    }
    @Test fun `pending and ineligible rows cannot submit duplicate claims`() = runTest {
        val api=Gateway(); val controller=MissionController(api,{})
        controller.setContext("A",MissionScope.PERSONAL)
        val load=launch { controller.refresh() }; runCurrent(); api.loads[0].complete(response(row("A"),row("locked",MissionClaimState.IN_PROGRESS))); load.join()
        controller.claim("locked"); controller.claim("unknown"); assertTrue(api.claims.isEmpty())
        val claim=launch { controller.claim("A") }; runCurrent(); controller.claim("A"); assertEquals(1,api.claims.size)
        api.claims[0].second.complete(MissionClaimResponse("A",MissionClaimState.CLAIMED,MissionReward())); claim.join()
        controller.claim("A"); assertEquals(1,api.claims.size)
        controller.close(); assertTrue(controller.state.value.items.isEmpty())
    }
}
