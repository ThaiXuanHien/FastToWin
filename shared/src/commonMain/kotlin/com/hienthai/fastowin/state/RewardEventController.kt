package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.RewardEventGateway
import com.hienthai.fastowin.data.network.RewardEventApiException
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

enum class RewardEventLoad { UNKNOWN, LOADING, READY, ERROR, UNAVAILABLE }
enum class RewardEventView { HOME, DETAILS }
internal fun rewardEventViewForRoute(route: String, state: GameState): RewardEventView? = when {
    state.isMatchStarted || state.isGameOver || state.isMatchmaking -> null
    route == "/events" || route.startsWith("/events/") -> RewardEventView.DETAILS
    route == "/" -> RewardEventView.HOME
    else -> null
}
data class RewardEventUiState(
    val load: RewardEventLoad = RewardEventLoad.UNKNOWN,
    val items: List<RewardEventPublicView> = emptyList(),
    val selected: RewardEventPublicView? = null,
    val serverNowEpochMillis: Long? = null,
    val total: Long = 0,
    val isLoadingMore: Boolean = false
) {
    val active: List<RewardEventPublicView> get() = if (load == RewardEventLoad.READY) items.filter { it.state == RewardEventState.ACTIVE } else emptyList()
    val hasMore: Boolean get() = items.size < total
}
class RewardEventController(
    private val gateway: RewardEventGateway,
    private val elapsedMillis: () -> Long = run {
        val origin = TimeSource.Monotonic.markNow()
        val clock: () -> Long = { origin.elapsedNow().inWholeMilliseconds }
        clock
    }
) {
    private val mutable = MutableStateFlow(RewardEventUiState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var page = 0
    private var open = false
    private var closed = false
    private var request: CompletableJob? = null
    private data class Context(val account: String?, val language: AppLanguage, val view: RewardEventView)
    private var context: Context? = null
    private var serverAnchor: Long? = null
    private var elapsedAnchor = 0L

    // Caller changes any account/language/view context by entering again, including guest -> account.
    suspend fun enter(account: String?, language: AppLanguage, view: RewardEventView) {
        if (closed) return
        reset()
        context = Context(account, language, view)
        open = true
        refresh()
    }
    suspend fun refresh() {
        if (closed || !open) return
        generation++
        request?.cancel()
        page = 0
        serverAnchor = null
        mutable.value = RewardEventUiState(load = RewardEventLoad.LOADING)
        loadPage(append = false)
    }
    suspend fun loadMore() {
        if (!open || closed || mutable.value.load != RewardEventLoad.READY ||
            mutable.value.isLoadingMore || !mutable.value.hasMore) return
        loadPage(append = true)
    }
    private suspend fun loadPage(append: Boolean) {
        val fence = generation to context
        val job = Job(currentCoroutineContext()[Job])
        request = job
        mutable.value = mutable.value.copy(isLoadingMore = append)
        try {
            val result = withContext(job) { gateway.page(page,25) }
            if (!isCurrent(fence)) return
            anchor(result.serverNowEpochMillis)
            page++
            mutable.value = mutable.value.copy(load = RewardEventLoad.READY,
                items = ((if (append) mutable.value.items else emptyList()) + result.items).distinctBy { it.id },
                serverNowEpochMillis = result.serverNowEpochMillis, total = result.total)
            tick()
        } catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancelled
        } catch (error: Exception) {
            if (isCurrent(fence)) mutable.value = mutable.value.copy(load = error.load())
        } finally {
            job.complete()
            if (isCurrent(fence) && request == job) {
                mutable.value = mutable.value.copy(isLoadingMore = false)
                request = null
            }
        }
    }
    suspend fun select(id: String) {
        if (!open || closed) return
        generation++
        request?.cancel()
        val job = Job(currentCoroutineContext()[Job])
        request = job
        val fence = generation to context
        mutable.value = mutable.value.copy(load = RewardEventLoad.LOADING, selected = null, isLoadingMore = false)
        try {
            val result = withContext(job) { gateway.detail(id) }
            if (!isCurrent(fence)) return
            anchor(result.serverNowEpochMillis)
            mutable.value = mutable.value.copy(load = RewardEventLoad.READY, selected = result.event,
                items = mutable.value.items.map { if (it.id == id) result.event else it },
                serverNowEpochMillis = result.serverNowEpochMillis)
            tick()
        } catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancelled
        } catch (error: Exception) {
            if (isCurrent(fence)) mutable.value = mutable.value.copy(load = error.load(),
                items = if ((error as? RewardEventApiException)?.code == "NOT_FOUND") mutable.value.items.filterNot { it.id == id } else mutable.value.items)
        } finally {
            job.complete()
            if (isCurrent(fence) && request == job) request = null
        }
    }
    fun tick() {
        val base = serverAnchor ?: return
        val now = base + (elapsedMillis() - elapsedAnchor).coerceAtLeast(0)
        fun effective(event: RewardEventPublicView): RewardEventPublicView = event.copy(state = when {
            event.state == RewardEventState.CANCELLED -> RewardEventState.CANCELLED
            now >= event.endsAtEpochMillis -> RewardEventState.ENDED
            now < event.startsAtEpochMillis -> RewardEventState.SCHEDULED
            else -> RewardEventState.ACTIVE
        })
        mutable.value = mutable.value.copy(items = mutable.value.items.map(::effective),
            selected = mutable.value.selected?.let(::effective), serverNowEpochMillis = now)
    }
    private fun anchor(now: Long) { serverAnchor = now; elapsedAnchor = elapsedMillis() }
    fun reset() {
        generation++
        request?.cancel()
        request = null
        open = false
        context = null
        page = 0
        serverAnchor = null
        mutable.value = RewardEventUiState()
    }
    fun close() { if (closed) return; reset(); closed = true; gateway.close() }
    private fun isCurrent(fence: Pair<Long, Context?>) = !closed && open && fence.first == generation && fence.second == context
    private fun Exception.load() = if ((this as? RewardEventApiException)?.code == "UNAVAILABLE") RewardEventLoad.UNAVAILABLE else RewardEventLoad.ERROR
}
