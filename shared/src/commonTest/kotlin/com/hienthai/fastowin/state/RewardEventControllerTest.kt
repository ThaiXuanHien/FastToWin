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
    @Test fun `background refresh retains the active banner until replacement arrives`() = runTest {
        val release = CompletableDeferred<Unit>()
        val gateway = Gateway { RewardEventPublicPage(listOf(event()),1500,0,25,1) }
        val controller = RewardEventController(gateway) { 0L }
        controller.enter("a",AppLanguage.VIETNAMESE,RewardEventView.HOME)
        gateway.response = {
            release.await()
            RewardEventPublicPage(listOf(event("replacement")),1600,0,25,1)
        }
        val refresh = launch { controller.refresh() }
        runCurrent()
        try {
            assertEquals(RewardEventLoad.READY,controller.state.value.load)
            assertEquals(listOf("a"),controller.state.value.active.map { it.id })
            assertEquals(1500,controller.state.value.serverNowEpochMillis)
            assertTrue(controller.state.value.isRefreshing)
        } finally {
            release.complete(Unit)
            refresh.join()
        }
        assertEquals(listOf("replacement"),controller.state.value.active.map { it.id })
        assertFalse(controller.state.value.isRefreshing)
        controller.close()
    }
    @Test fun `failed background refresh hides active authority and a retry replaces it`() = runTest {
        for (unavailable in listOf(false,true)) {
            val gateway = Gateway { RewardEventPublicPage(listOf(event()),1500,0,25,1) }
            val controller = RewardEventController(gateway) { 0L }
            controller.enter("a",AppLanguage.ENGLISH,RewardEventView.HOME)
            gateway.response = {
                if (unavailable) throw RewardEventApiException("UNAVAILABLE")
                throw IllegalStateException("offline")
            }
            controller.refresh()
            assertEquals(if (unavailable) RewardEventLoad.UNAVAILABLE else RewardEventLoad.ERROR,controller.state.value.load)
            assertTrue(controller.state.value.active.isEmpty())
            assertFalse(controller.state.value.isRefreshing)
            gateway.response = { RewardEventPublicPage(listOf(event("new")),1600,0,25,1) }
            controller.refresh()
            assertEquals(listOf("new"),controller.state.value.active.map { it.id })
            assertFalse(controller.state.value.isRefreshing)
            controller.close()
        }
    }
    @Test fun `load more cannot supersede a pending first page refresh`() = runTest {
        val release = CompletableDeferred<Unit>()
        val gateway = Gateway { RewardEventPublicPage(listOf(event()),1500,0,25,2) }
        val controller = RewardEventController(gateway) { 0L }
        controller.enter("a",AppLanguage.ENGLISH,RewardEventView.DETAILS)
        val requestedPages = mutableListOf<Int>()
        gateway.response = { page ->
            requestedPages += page
            release.await()
            RewardEventPublicPage(listOf(event("fresh")),1600,page,25,2)
        }
        val refresh = launch { controller.refresh() }
        runCurrent()
        try {
            // This would issue another request if READY alone enabled pagination.
            val more = launch { controller.loadMore() }
            runCurrent()
            assertTrue(more.isCompleted)
            assertEquals(listOf(0),requestedPages)
        } finally {
            release.complete(Unit)
            refresh.join()
        }
        assertEquals(listOf("fresh"),controller.state.value.items.map { it.id })
        assertFalse(controller.state.value.isRefreshing)
        controller.close()
    }
    @Test fun `late background response cannot restore the previous account banner or clear a new refresh`() = runTest {
        for (failure in listOf(false,true)) {
            val oldRelease = CompletableDeferred<Unit>()
            val newRelease = CompletableDeferred<Unit>()
            val gateway = Gateway { RewardEventPublicPage(listOf(event("old")),1500,0,25,1) }
            val controller = RewardEventController(gateway) { 0L }
            controller.enter("a",AppLanguage.VIETNAMESE,RewardEventView.HOME)
            gateway.response = {
                withContext(NonCancellable) { oldRelease.await() }
                if (failure) throw IllegalStateException("late old refresh")
                RewardEventPublicPage(listOf(event("obsolete")),1600,0,25,1)
            }
            val oldRefresh = launch { controller.refresh() }
            runCurrent()
            controller.reset()
            assertTrue(controller.state.value.items.isEmpty())
            assertFalse(controller.state.value.isRefreshing)
            gateway.response = { RewardEventPublicPage(listOf(event("current")),1500,0,25,1) }
            controller.enter("b",AppLanguage.ENGLISH,RewardEventView.DETAILS)
            gateway.response = {
                newRelease.await()
                RewardEventPublicPage(listOf(event("newest")),1600,0,25,1)
            }
            val newRefresh = launch { controller.refresh() }
            runCurrent()
            try {
                oldRelease.complete(Unit)
                oldRefresh.join()
                assertEquals(listOf("current"),controller.state.value.items.map { it.id })
                assertEquals(RewardEventLoad.READY,controller.state.value.load)
                assertTrue(controller.state.value.isRefreshing)
            } finally {
                newRelease.complete(Unit)
                newRefresh.join()
            }
            assertEquals(listOf("newest"),controller.state.value.active.map { it.id })
            assertFalse(controller.state.value.isRefreshing)
            controller.close()
        }
    }
    @Test fun `expiry clock keeps running during a delayed background refresh`() = runTest {
        var clock = 0L
        val release = CompletableDeferred<Unit>()
        val gateway = Gateway { RewardEventPublicPage(listOf(event()),1500,0,25,1) }
        val controller = RewardEventController(gateway) { clock }
        controller.enter("a",AppLanguage.ENGLISH,RewardEventView.HOME)
        gateway.response = {
            release.await()
            RewardEventPublicPage(emptyList(),2000,0,25,0)
        }
        val refresh = launch { controller.refresh() }
        runCurrent()
        try {
            clock = 500
            controller.tick()
            assertEquals(1,controller.state.value.items.size)
            assertEquals(RewardEventState.ENDED,controller.state.value.items.single().state)
            assertEquals(2000,controller.state.value.serverNowEpochMillis)
            assertTrue(controller.state.value.active.isEmpty())
        } finally {
            release.complete(Unit)
            refresh.join()
        }
        assertEquals(RewardEventLoad.READY,controller.state.value.load)
        assertTrue(controller.state.value.items.isEmpty())
        controller.close()
    }
    @Test fun `periodic polling expires the banner while its HTTP refresh is still pending`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val active = event().copy(endsAtEpochMillis = 33500)
        val gateway = Gateway {
            if (calls++ > 0) release.await()
            RewardEventPublicPage(listOf(active),1500 + testScheduler.currentTime,0,25,1)
        }
        val controller = RewardEventController(gateway) { testScheduler.currentTime }
        val polling = launch {
            runRewardEventPolling(controller,"a",AppLanguage.VIETNAMESE,RewardEventView.HOME,null)
        }
        try {
            runCurrent()
            advanceTimeBy(30001)
            runCurrent()
            assertEquals(2,calls)
            assertTrue(controller.state.value.isRefreshing)
            assertEquals(listOf("a"),controller.state.value.active.map { it.id })
            advanceTimeBy(3000)
            runCurrent()
            assertTrue(controller.state.value.active.isEmpty())
            assertEquals(RewardEventState.ENDED,controller.state.value.items.single().state)
            assertTrue(controller.state.value.isRefreshing)
            assertEquals(2,calls)
        } finally {
            polling.cancelAndJoin()
            controller.reset()
        }
        val reset = controller.state.value
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(reset,controller.state.value)
        controller.close()
    }
    @Test fun `polling refreshes at crossed start and end boundaries`() = runTest {
        var calls = 0
        val scheduled = event().copy(state = RewardEventState.SCHEDULED,startsAtEpochMillis = 2500,endsAtEpochMillis = 4000)
        val gateway = Gateway {
            calls++
            val now = 1500 + testScheduler.currentTime
            val items = if (now < 4000) listOf(scheduled) else emptyList()
            RewardEventPublicPage(items,now,0,25,items.size.toLong())
        }
        val controller = RewardEventController(gateway) { testScheduler.currentTime }
        val polling = launch { runRewardEventPolling(controller,null,AppLanguage.ENGLISH,RewardEventView.HOME,null) }
        try {
            runCurrent()
            assertEquals(RewardEventState.SCHEDULED,controller.state.value.items.single().state)
            // The sequential poll observes the independent clock on its next one-second tick.
            advanceTimeBy(2001)
            runCurrent()
            assertEquals(2,calls)
            assertEquals(listOf("a"),controller.state.value.active.map { it.id })
            advanceTimeBy(2000)
            runCurrent()
            assertEquals(3,calls)
            assertTrue(controller.state.value.items.isEmpty())
            assertEquals(RewardEventLoad.READY,controller.state.value.load)
        } finally {
            polling.cancelAndJoin()
            controller.close()
        }
    }
    @Test fun `historical polling refreshes the selected detail instead of returning to the list`() = runTest {
        var pages = 0
        var details = 0
        val gateway = Gateway {
            pages++
            RewardEventPublicPage(emptyList(),1500,0,25,0)
        }
        gateway.detailResponse = { id ->
            details++
            RewardEventPublicDetail(event(id).copy(state = RewardEventState.CANCELLED,version = details.toLong(),nameEn = "History $details"),
                1500 + testScheduler.currentTime)
        }
        val controller = RewardEventController(gateway) { testScheduler.currentTime }
        val polling = launch { runRewardEventPolling(controller,"a",AppLanguage.ENGLISH,RewardEventView.DETAILS,"historical") }
        try {
            runCurrent()
            assertEquals("historical",controller.state.value.selected?.id)
            assertEquals("History 1",controller.state.value.selected?.nameEn)
            advanceTimeBy(30001)
            runCurrent()
            assertEquals("historical",controller.state.value.selected?.id)
            assertEquals("History 2",controller.state.value.selected?.nameEn)
            assertEquals(RewardEventState.CANCELLED,controller.state.value.selected?.state)
            assertEquals(1,pages)
            assertFalse(controller.state.value.isRefreshing)
        } finally {
            polling.cancelAndJoin()
            controller.close()
        }
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
