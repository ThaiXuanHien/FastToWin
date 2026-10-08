package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RewardEventControllerTest {
    private fun event(id: String = "a") = RewardEventPublicView(id,1,RewardEventState.ACTIVE,"Tăng thưởng","Rewards","Mô tả","Details","UTC",1000,2000,2,2,setOf(MatchType.CASUAL,MatchType.RANKED))
    private class Gateway(var response: suspend (Int) -> RewardEventPublicPage) : RewardEventGateway {
        override suspend fun page(page: Int, pageSize: Int) = response(page)
        var detailResponse: suspend (String) -> RewardEventPublicDetail = { id -> RewardEventPublicDetail(RewardEventPublicView(id,1,RewardEventState.CANCELLED,"Đã hủy","Cancelled","Mô tả","Details","UTC",1000,2000,2,2,setOf(MatchType.CASUAL)),1500) }
        override suspend fun detail(id: String) = detailResponse(id)
    }
    @Test fun `expiry removes active banner before refresh and empty page is known`() = runTest {
        var clock = 0L
        val gateway = Gateway { RewardEventPublicPage(listOf(event()),1500,0,25,1) }
        val controller = RewardEventController(gateway) { clock }
        controller.enter("a",AppLanguage.VIETNAMESE,RewardEventView.HOME)
        assertEquals(1,controller.state.value.active.size)
        clock = 500
        controller.tick()
        assertTrue(controller.state.value.active.isEmpty())
        assertEquals(RewardEventState.ENDED,controller.state.value.items.single().state)
        gateway.response = { RewardEventPublicPage(emptyList(),2000,0,25,0) }
        controller.refresh()
        assertEquals(RewardEventLoad.READY,controller.state.value.load)
        assertTrue(controller.state.value.items.isEmpty())
    }
    @Test fun `offline unavailable and retry do not fabricate known empty`() = runTest {
        val gateway = Gateway { throw IllegalStateException("offline") }
        val controller = RewardEventController(gateway)
        controller.enter(null,AppLanguage.ENGLISH,RewardEventView.HOME)
        assertEquals(RewardEventLoad.ERROR,controller.state.value.load)
        assertNull(controller.state.value.serverNowEpochMillis)
        gateway.response = { throw RewardEventApiException("UNAVAILABLE") }
        controller.refresh()
        assertEquals(RewardEventLoad.UNAVAILABLE,controller.state.value.load)
        gateway.response = { RewardEventPublicPage(emptyList(),1500,0,25,0) }
        controller.refresh()
        assertEquals(RewardEventLoad.READY,controller.state.value.load)
    }
    @Test fun `two delayed account language view responses cannot replace current success or loading`() = runTest {
        val first = CompletableDeferred<Unit>(); val second = CompletableDeferred<Unit>()
        var calls = 0
        val gateway = Gateway {
            when (calls++) {
                0 -> { withContext(NonCancellable) { first.await() }; RewardEventPublicPage(listOf(event("old-account")),1500,0,25,1) }
                1 -> { withContext(NonCancellable) { second.await() }; throw IllegalStateException("old-language") }
                else -> RewardEventPublicPage(listOf(event("current")),1500,0,25,1)
            }
        }
        val controller = RewardEventController(gateway)
        val a = launch { controller.enter("a",AppLanguage.VIETNAMESE,RewardEventView.HOME) }; runCurrent()
        assertEquals(RewardEventLoad.LOADING,controller.state.value.load)
        val b = launch { controller.enter("b",AppLanguage.VIETNAMESE,RewardEventView.HOME) }; runCurrent()
        controller.enter("b",AppLanguage.ENGLISH,RewardEventView.DETAILS)
        first.complete(Unit); second.complete(Unit); a.join(); b.join()
        assertEquals(listOf("current"),controller.state.value.items.map { it.id })
        assertEquals(RewardEventLoad.READY,controller.state.value.load)
        controller.reset()
        assertEquals(RewardEventLoad.UNKNOWN,controller.state.value.load)
        assertTrue(controller.state.value.items.isEmpty())
    }
    @Test fun `detail cancellation invalidates active list and page loading visits remaining published events`() = runTest {
        val gateway = Gateway { page -> RewardEventPublicPage(listOf(event(if (page == 0) "a" else "b")),1500,page,25,2) }
        val controller = RewardEventController(gateway)
        controller.enter("a",AppLanguage.ENGLISH,RewardEventView.DETAILS)
        controller.select("a")
        assertEquals(1,controller.state.value.items.size)
        assertEquals(RewardEventState.CANCELLED,controller.state.value.items.single().state)
        assertTrue(controller.state.value.active.isEmpty())
        controller.loadMore()
        assertEquals(listOf("a","b"),controller.state.value.items.map { it.id })
        assertFalse(controller.state.value.hasMore)
    }
    @Test fun `superseded HTTP refresh keeps caller alive for future expiry polling`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val gateway = Gateway {
            if (calls++ == 0) withContext(NonCancellable) { release.await() }
            RewardEventPublicPage(listOf(event()),1500,0,25,1)
        }
        val controller = RewardEventController(gateway)
        var pollingContinues = false
        val poller = launch {
            controller.enter("a",AppLanguage.ENGLISH,RewardEventView.HOME)
            delay(1)
            pollingContinues = true
        }
        runCurrent()
        controller.refresh()
        release.complete(Unit)
        poller.join()
        assertTrue(pollingContinues)
        assertEquals(RewardEventLoad.READY,controller.state.value.load)
    }
    @Test fun `published history navigation is available from known event route and fenced during live play`() {
        assertEquals(RewardEventView.DETAILS,rewardEventViewForRoute("/events/ended-source",GameState()))
        assertEquals(RewardEventView.HOME,rewardEventViewForRoute("/",GameState()))
        assertNull(rewardEventViewForRoute("/events/ended-source",GameState(isMatchStarted=true)))
        assertNull(rewardEventViewForRoute("/",GameState(isMatchmaking=true)))
        assertNull(rewardEventViewForRoute("/room/current",GameState()))
        assertNull(rewardEventViewForRoute("/account/matches",GameState()))
    }
    @Test fun `late historical detail success error and finally cannot reopen a reset or replaced view`() = runTest {
        for (failure in listOf(false,true)) {
            val release = CompletableDeferred<Unit>()
            val gateway = Gateway { RewardEventPublicPage(listOf(event()),1500,0,25,1) }
            val controller = RewardEventController(gateway)
            controller.enter("a",AppLanguage.VIETNAMESE,RewardEventView.DETAILS)
            gateway.detailResponse = { id ->
                withContext(NonCancellable) { release.await() }
                if (failure) throw IllegalStateException("late old detail")
                RewardEventPublicDetail(event(id),1500)
            }
            val old = launch { controller.select("ended-source") }; runCurrent()
            controller.reset()
            assertNull(controller.state.value.selected)
            assertEquals(RewardEventLoad.UNKNOWN,controller.state.value.load)
            controller.enter("b",AppLanguage.ENGLISH,RewardEventView.DETAILS)
            gateway.detailResponse = { id -> RewardEventPublicDetail(event(id),1500) }
            controller.select("fresh")
            release.complete(Unit); old.join()
            assertEquals("fresh",controller.state.value.selected?.id)
            assertEquals(RewardEventLoad.READY,controller.state.value.load)
            assertFalse(controller.state.value.isLoadingMore)
            controller.close()
            controller.tick()
            assertTrue(controller.state.value.items.isEmpty())
            assertNull(controller.state.value.selected)
        }
    }
}
