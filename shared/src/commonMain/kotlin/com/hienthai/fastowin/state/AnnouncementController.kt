package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.localization.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import com.hienthai.fastowin.protocol.*
import kotlin.time.TimeSource

enum class AnnouncementView { HOME, INBOX, DETAIL, SETTINGS, UPDATE, INACTIVE }
data class AnnouncementState(
    val items: List<AnnouncementPlayerItem> = emptyList(),
    val banner: AnnouncementPlayerItem? = null,
    val selected: AnnouncementPlayerItem? = null,
    val total: String = "0",
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val pending: Boolean = false,
    val error: String? = null,
    val preferences: AnnouncementPreferenceView? = null,
    val preferenceIntent: AnnouncementPreferenceRequest? = null,
    val serverNowEpochMillis: Long? = null
)
class AnnouncementController(private val gateway: AnnouncementGateway,
    private val navigation: AnnouncementNavigation? = null,
    private val updateBridge: com.hienthai.fastowin.platform.AppUpdateBridge = com.hienthai.fastowin.platform.NoOpAppUpdateBridge,
    private val elapsedMillis: () -> Long = run {
    val origin = TimeSource.Monotonic.markNow()
    val clock: () -> Long = { origin.elapsedNow().inWholeMilliseconds }
    clock
}) {
    private val mutable = MutableStateFlow(AnnouncementState())
    val state = mutable.asStateFlow()
    private var account: String? = null
    private var token: String? = null
    private var language = AppLanguage.ENGLISH
    private var view = AnnouncementView.INACTIVE
    private var accountEpoch = 0L
    private var tokenEpoch = 0L
    private var languageEpoch = 0L
    private var viewEpoch = 0L
    private var requestEpoch = 0L
    private var nextPage = 0
    private var serverAnchor: Long? = null
    private var elapsedAnchor = 0L
    private var closed = false
    private val hidden = mutableSetOf<String>()
    private fun fence() = listOf(accountEpoch, tokenEpoch, languageEpoch, viewEpoch, requestEpoch)
    private fun current(fence: List<Long>) = !closed && this.fence() == fence && account != null && token != null && view != AnnouncementView.INACTIVE
    private fun allowed() = !closed && account != null && token != null && view != AnnouncementView.INACTIVE

    fun context(account: String?, token: String?, language: AppLanguage, view: AnnouncementView) {
        if (closed) return
        val identityChanged = this.account != account || this.token != token || this.language != language
        val changed = identityChanged || this.view != view
        if (this.account != account) accountEpoch++
        if (this.token != token) tokenEpoch++
        if (this.language != language) languageEpoch++
        if (this.view != view) viewEpoch++
        this.account = account; this.token = token; this.language = language; this.view = view
        if (changed) {
            requestEpoch++; nextPage = 0
            mutable.value = AnnouncementState()
            if (identityChanged) { hidden.clear(); serverAnchor = null }
        }
    }
    suspend fun refresh() {
        if (!allowed() || mutable.value.pending || mutable.value.preferenceIntent != null) return
        requestEpoch++
        when (view) {
            AnnouncementView.INBOX -> refreshInbox()
            AnnouncementView.HOME -> request {
                val result = gateway.banners()
                if (current(it)) {
                    anchor(result.serverNowEpochMillis)
                    mutable.value = mutable.value.copy(banner = result.items.filterNot { row -> row.bannerHidden || row.announcement.id in hidden }
                        .maxByOrNull { row -> row.announcement.priority })
                    tick()
                }
            }
            AnnouncementView.SETTINGS -> request {
                val result = gateway.preferences()
                if (current(it)) { anchor(result.serverNowEpochMillis); mutable.value = mutable.value.copy(preferences = result) }
            }
            else -> Unit
        }
    }
    suspend fun more() {
        if (!allowed() || view != AnnouncementView.INBOX || mutable.value.loading || !mutable.value.hasMore) return
        loadPage()
    }
    private suspend fun refreshInbox() = request { fence ->
        val loadedPages = nextPage.coerceAtLeast(1)
        val refreshed = mutableListOf<AnnouncementPlayerItem>()
        var lastPage: AnnouncementPlayerPage? = null
        for (index in 0 until loadedPages) {
            val page = gateway.inbox(index, 25)
            if (!current(fence)) return@request
            anchor(page.serverNowEpochMillis)
            refreshed += page.items
            lastPage = page
            if (page.items.isEmpty()) break
        }
        lastPage?.let { page ->
            // New first-page rows can shift old rows beyond the loaded window; retain them.
            val items = (refreshed + mutable.value.items).distinctBy { it.announcement.id }
            nextPage = page.page + 1
            mutable.value = mutable.value.copy(items = items, total = page.total,
                hasMore = page.items.isNotEmpty() && decimalGreaterThan(page.total, items.size))
            tick()
        }
    }
    private suspend fun loadPage() = request { fence ->
        val page = gateway.inbox(nextPage, 25)
        if (current(fence)) {
            anchor(page.serverNowEpochMillis); nextPage = page.page + 1
            val items = (mutable.value.items + page.items).distinctBy { it.announcement.id }
            mutable.value = mutable.value.copy(items = items, total = page.total,
                hasMore = page.items.isNotEmpty() && decimalGreaterThan(page.total, items.size))
            tick()
        }
    }
    suspend fun select(id: String) {
        if (!allowed() || view != AnnouncementView.DETAIL) return
        requestEpoch++
        if (mutable.value.selected?.announcement?.id != id) mutable.value = mutable.value.copy(selected = null)
        request { fence ->
            val result = gateway.detail(id)
            if (current(fence)) {
                mutable.value = mutable.value.copy(selected = result)
                tick()
                if (serverAnchor == null) {
                    // Detail has no time envelope; obtain authority before waiting for read.
                    try {
                        val clock = gateway.banners()
                        if (current(fence)) { anchor(clock.serverNowEpochMillis); tick() }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { if (current(fence)) mutable.value = mutable.value.copy(error = error.code()) }
                }
                if (!current(fence)) return@request
                val receipt = gateway.read(id)
                if (current(fence)) {
                    anchor(receipt.serverNowEpochMillis)
                    mutable.value = mutable.value.copy(error = null, selected = mutable.value.selected?.let { row ->
                        if (row.dismissed) row else row.copy(notification = row.notification.copy(isRead = true))
                    })
                    tick()
                }
            }
        }
    }
    suspend fun hide(id: String) {
        if (id in hidden || mutable.value.banner?.announcement?.id != id) return
        mutation({ gateway.hide(id) }) {
            hidden += id
            it.copy(banner = null, items = it.items.map { row -> if (row.announcement.id == id) row.copy(bannerHidden = true) else row })
        }
    }
    suspend fun read(id: String? = null) = mutation({ gateway.read(id) }) { state ->
        state.copy(items = state.items.map { if (!it.dismissed && (id == null || it.announcement.id == id)) it.copy(notification = it.notification.copy(isRead = true)) else it })
    }
    suspend fun dismiss(id: String? = null) = mutation({ gateway.dismiss(id) }) { state ->
        state.copy(items = state.items.map { if (id == null || it.announcement.id == id) it.copy(dismissed = true) else it })
    }
    private suspend fun mutation(call: suspend () -> AnnouncementMutationResult, apply: (AnnouncementState) -> AnnouncementState) {
        if (!allowed() || mutable.value.pending) return
        requestEpoch++
        val fence = fence()
        mutable.value = mutable.value.copy(pending = true, loading = false, error = null)
        try {
            val receipt = call()
            if (current(fence)) { anchor(receipt.serverNowEpochMillis); mutable.value = apply(mutable.value); tick() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (current(fence)) mutable.value = mutable.value.copy(error = error.code()) }
        finally { if (current(fence)) mutable.value = mutable.value.copy(pending = false) }
    }
    suspend fun savePreferences(admin: Boolean, event: Boolean) {
        if (mutable.value.preferenceIntent != null) return
        val saved = mutable.value.preferences ?: return
        mutable.value = mutable.value.copy(preferenceIntent = AnnouncementPreferenceRequest(saved.version, admin, event))
        retryPreferences()
    }
    suspend fun retryPreferences() {
        val intent = mutable.value.preferenceIntent ?: return
        if (!allowed() || view != AnnouncementView.SETTINGS || mutable.value.pending) return
        requestEpoch++
        val fence = fence()
        mutable.value = mutable.value.copy(pending = true, error = null)
        try {
            val saved = gateway.savePreferences(intent)
            if (current(fence)) { anchor(saved.serverNowEpochMillis); mutable.value = mutable.value.copy(preferences = saved, preferenceIntent = null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (current(fence)) {
                val code = error.code()
                if (code == "CONFLICT") {
                    mutable.value = mutable.value.copy(preferenceIntent = null, preferences = null, error = code)
                    try {
                        val latest = gateway.preferences()
                        if (current(fence)) { anchor(latest.serverNowEpochMillis); mutable.value = mutable.value.copy(preferences = latest, error = code) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Retry loads authority again; no stale switch is enabled. */ }
                } else mutable.value = mutable.value.copy(error = code)
            }
        } finally { if (current(fence)) mutable.value = mutable.value.copy(pending = false) }
    }
    private suspend fun request(block: suspend (List<Long>) -> Unit) {
        val fence = fence()
        mutable.value = mutable.value.copy(loading = true, error = null)
        try { block(fence) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (current(fence)) mutable.value = mutable.value.copy(error = error.code(),
            banner = if (view == AnnouncementView.HOME) null else mutable.value.banner) }
        finally { if (current(fence)) mutable.value = mutable.value.copy(loading = false) }
    }
    private fun anchor(now: Long) {
        tick()
        // A late response cannot move an already ticking clock backwards.
        serverAnchor = maxOf(now, mutable.value.serverNowEpochMillis ?: now)
        elapsedAnchor = elapsedMillis()
        mutable.value = mutable.value.copy(serverNowEpochMillis = serverAnchor)
    }
    fun tick() {
        val base = serverAnchor ?: return
        val now = base + (elapsedMillis() - elapsedAnchor).coerceAtLeast(0)
        fun effective(row: AnnouncementPlayerItem) = row.copy(announcement = row.announcement.copy(state = when {
            row.announcement.state == AdminAnnouncementState.CANCELLED -> AdminAnnouncementState.CANCELLED
            row.announcement.state == AdminAnnouncementState.EXPIRED -> AdminAnnouncementState.EXPIRED
            now >= row.announcement.endsAtEpochMillis -> AdminAnnouncementState.EXPIRED
            now < row.announcement.startsAtEpochMillis -> AdminAnnouncementState.SCHEDULED
            else -> AdminAnnouncementState.ACTIVE
        }))
        mutable.value = mutable.value.copy(items = mutable.value.items.map(::effective), selected = mutable.value.selected?.let(::effective),
            banner = mutable.value.banner?.let(::effective)?.takeIf { it.announcement.state == AdminAnnouncementState.ACTIVE && !it.bannerHidden && it.announcement.id !in hidden },
            serverNowEpochMillis = now)
    }
    fun close() { if (closed) return; closed = true; requestEpoch++; mutable.value = AnnouncementState(); gateway.close() }
    fun action(matchBlocked: Boolean): Boolean {
        if (!allowed() || view != AnnouncementView.DETAIL) return false
        val selected = mutable.value.selected?.announcement ?: return false
        return dispatchAnnouncementAction(selected, matchBlocked, navigation ?: return false)
    }
    fun correction(id: String, matchBlocked: Boolean): Boolean {
        if (!allowed() || view != AnnouncementView.DETAIL || mutable.value.selected?.announcement?.supersedesAnnouncementId != id) return false
        return dispatchAnnouncementCorrection(id, matchBlocked, navigation ?: return false)
    }
    fun applyUpdate(matchBlocked: Boolean): Boolean {
        if (!allowed() || view != AnnouncementView.UPDATE) return false
        return applyAnnouncementUpdate(updateBridge, matchBlocked)
    }
    private fun Exception.code() = (this as? AnnouncementApiException)?.code ?: "NETWORK_ERROR"
}

private fun decimalGreaterThan(total: String, loaded: Int): Boolean {
    val normalized = total.trimStart('0').ifEmpty { "0" }
    val count = loaded.toString()
    return normalized.length > count.length || (normalized.length == count.length && normalized > count)
}

interface AnnouncementNavigation {
    fun event(id: String)
    fun mailbox()
    fun update()
    fun correction(id: String)
}
fun dispatchAnnouncementAction(context: AdminAnnouncementContext, matchBlocked: Boolean, navigation: AnnouncementNavigation): Boolean {
    if (matchBlocked) return false
    when (context.action) {
        AdminAnnouncementAction.NONE -> return false
        AdminAnnouncementAction.REWARD_EVENT -> navigation.event(context.targetId?.takeIf(::isAnnouncementUuid) ?: return false)
        AdminAnnouncementAction.GIFT_MAILBOX -> { if (context.targetId?.let(::isAnnouncementUuid) != true) return false; navigation.mailbox() }
        AdminAnnouncementAction.APP_UPDATE -> { if (context.targetId != null) return false; navigation.update() }
    }
    return true
}
fun dispatchAnnouncementCorrection(id: String, matchBlocked: Boolean, navigation: AnnouncementNavigation): Boolean {
    if (matchBlocked || !isAnnouncementUuid(id)) return false
    navigation.correction(id)
    return true
}
internal fun isAnnouncementUuid(id: String) = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id)
fun applyAnnouncementUpdate(bridge: com.hienthai.fastowin.platform.AppUpdateBridge, matchBlocked: Boolean): Boolean {
    if (matchBlocked || !bridge.updateAvailable) return false
    bridge.applyUpdate()
    return true
}
