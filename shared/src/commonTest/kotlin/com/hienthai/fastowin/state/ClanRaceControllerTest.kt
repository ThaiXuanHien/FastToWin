package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClanRaceControllerTest {
    private val week = "2026-10-26"
    private fun current(now: Long = 3, state: ClanRaceState = ClanRaceState.ACTIVE) = ClanRaceCurrent(week, 1, 9, state, serverNowEpochMillis = now)
    private inner class Gateway : ClanRaceGateway {
        var load: suspend () -> ClanRaceCurrent = { this@ClanRaceControllerTest.current() }
        var error: String? = null
        var clan: String? = "own"
        override suspend fun current(): ClanRaceCurrent = error?.let { throw ClanRaceApiException(it) } ?: load()
        override suspend fun standings(page: Int) = ClanRaceStandings(week, emptyList(), page, 25, 51,
            ClanRaceStanding(51, "own", "Own", null, 3, "1", "0.000001", 2), 3)
        override suspend fun contributions() = ClanRaceContributions(week, clan, listOf(
            ClanRaceContribution("me", "Me", "old", false, false, "10", "0", "10", "0.00001"),
            ClanRaceContribution("me", "Me", "new", true, true, "1", "0", "1", "0.000001")), 3)
        override suspend fun history(page: Int) = ClanRaceHistory(emptyList(), page, 25, 0, 3)
    }
    @Test fun `tiny eligible contribution own rank and tenure rows remain authoritative`() = runTest {
        val api = Gateway(); val controller = ClanRaceController(api)
        controller.setContext("me", "own", "open", "token")
        controller.refresh()
        assertEquals(51, controller.state.value.standings?.ownClan?.rank)
        assertEquals(listOf(false, true), controller.state.value.contributions?.items?.map { it.eligible })
        assertEquals("0.000001", controller.state.value.contributions?.items?.last()?.points)
        assertEquals(3, controller.state.value.lastRefreshedAtEpochMillis)
    }
    @Test fun `every account clan view and token change fences suspended response`() = runTest {
        for (change in listOf("account", "clan", "view", "token")) {
            val api = Gateway(); val pending = CompletableDeferred<ClanRaceCurrent>(); api.load = { pending.await() }
            val controller = ClanRaceController(api); controller.setContext("me", "own", "open", "a")
            val request = launch { controller.refresh() }; runCurrent()
            controller.setContext(if(change == "account") "other" else "me", if(change == "clan") "other" else "own",
                if(change == "view") "closed" else "open", if(change == "token") "b" else "a")
            pending.complete(current()); request.join()
            assertNull(controller.state.value.current, change)
            assertFalse(controller.state.value.isLoading, change)
        }
    }
    @Test fun `manual and reconnect refresh reject out of order older snapshot`() = runTest {
        val api = Gateway(); val pending = CompletableDeferred<ClanRaceCurrent>(); api.load = { pending.await() }
        val controller = ClanRaceController(api); controller.setContext("me", "own")
        val old = launch { controller.refresh() }; runCurrent()
        api.load = { current(7) }; controller.refresh()
        pending.complete(current(2)); old.join()
        assertEquals(7, controller.state.value.current?.serverNowEpochMillis)
    }
    @Test fun `refresh errors retain valid data and inactive is explicit rather than empty success`() = runTest {
        val api = Gateway(); val controller = ClanRaceController(api); controller.setContext("me", "own")
        controller.refresh()
        for (code in listOf("UNAUTHORIZED", "FORBIDDEN", "STORAGE_UNAVAILABLE", "NETWORK_ERROR")) {
            api.error = code; controller.refresh()
            assertEquals(ClanRaceState.ACTIVE, controller.state.value.current?.state)
            assertEquals(code, controller.state.value.loadError)
            assertFalse(controller.state.value.isLoading)
        }
        api.error = null; api.load = { current(state = ClanRaceState.INACTIVE) }; controller.refresh()
        assertEquals(ClanRaceState.INACTIVE, controller.state.value.current?.state)
        assertNull(controller.state.value.loadError)
    }
    @Test fun `mismatched server membership never publishes private old clan rows`() = runTest {
        val api = Gateway(); api.clan = "other"; val controller = ClanRaceController(api); controller.setContext("me", "own")
        controller.refresh()
        assertNull(controller.state.value.contributions)
        assertEquals("CONFLICT", controller.state.value.loadError)
    }
    @Test fun `wasm fetch Error keeps last snapshot and can retry without escaping refresh`() = runTest {
        val api = Gateway(); val controller = ClanRaceController(api); controller.setContext("me", "own")
        controller.refresh()
        // Ktor's Wasm commonFetch wraps a rejected browser fetch in kotlin.Error, not Exception.
        api.load = { throw Error("Fail to fetch") }
        controller.refresh()
        assertEquals(ClanRaceState.ACTIVE, controller.state.value.current?.state)
        assertEquals("NETWORK_ERROR", controller.state.value.loadError)
        assertFalse(controller.state.value.isLoading)
        api.load = { current(7) }; controller.refresh()
        assertEquals(7, controller.state.value.lastRefreshedAtEpochMillis)
        assertNull(controller.state.value.loadError)
    }
    @Test fun `refresh cancellation propagates without converting it into network error`() = runTest {
        val api = Gateway(); val controller = ClanRaceController(api); controller.setContext("me", "own")
        api.load = { throw kotlinx.coroutines.CancellationException("closed view") }
        assertFailsWith<kotlinx.coroutines.CancellationException> { controller.refresh() }
        assertFalse(controller.state.value.isLoading)
        assertNull(controller.state.value.loadError)
    }
}
