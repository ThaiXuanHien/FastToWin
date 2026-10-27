package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.GiftApiException
import com.hienthai.fastowin.data.network.GiftGateway
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GiftMailboxState(
    val isOpen: Boolean = false,
    val items: List<MailboxGift> = emptyList(),
    val total: Long = 0,
    val availableCount: Long = 0,
    val isLoading: Boolean = false,
    val isClaimingAll: Boolean = false,
    val pendingIds: Set<String> = emptySet(),
    val itemErrors: Map<String, String> = emptyMap(),
    val loadError: String? = null
) {
    val hasMore: Boolean get() = items.size < total
    val hasAvailable: Boolean get() = items.any { it.state == GiftState.AVAILABLE }
    val canClaimAll: Boolean get() = hasAvailable || hasMore
}

class GiftMailboxController(
    private val gateway: GiftGateway,
    private val refreshProfile: () -> Unit,
    private val pageSize: Int = 25
) {
    private val _state = MutableStateFlow(GiftMailboxState())
    val state: StateFlow<GiftMailboxState> = _state.asStateFlow()
    private var generation = 0L
    private var nextPage = 0
    private var closed = false
    private var countVersion = 0L
    private var summaryInFlight = false
    private var summaryRefreshRequested = false
    private val activeClaimIds = mutableSetOf<String>()
    private var claimAllInFlight = false
    private var reloadAfterClaimAll = false
    private val appliedReceiptIds = mutableSetOf<String>()

    /** The server count includes unseen pages and excludes expired/cancelled/claimed gifts. */
    suspend fun refreshAvailableCount() {
        if (closed) return
        if (summaryInFlight) {
            summaryRefreshRequested = true
            return
        }
        if (_state.value.isLoading || activeClaimIds.isNotEmpty() || claimAllInFlight) return
        summaryInFlight = true
        try {
            do {
                summaryRefreshRequested = false
                val version = countVersion
                try {
                    val summary = gateway.page(0, 1)
                    if (!closed && version == countVersion) {
                        _state.value = _state.value.copy(availableCount = summary.availableCount.coerceAtLeast(0))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A transient background failure must not clear a known badge or replace view errors.
                }
            } while (!closed && summaryRefreshRequested && !_state.value.isLoading &&
                activeClaimIds.isEmpty() && !claimAllInFlight)
        } finally {
            summaryInFlight = false
        }
    }

    suspend fun open() {
        if (closed) return
        generation++
        countVersion++
        nextPage = 0
        _state.value = GiftMailboxState(isOpen = true, availableCount = _state.value.availableCount)
        if (claimAllInFlight) {
            // A previous view's batch still owns the gateway; keep this new view loading.
            reloadAfterClaimAll = true
            _state.value = _state.value.copy(isLoading = true)
            return
        }
        loadNextPage()
    }

    suspend fun refresh() = open()

    suspend fun loadNextPage() {
        val before = _state.value
        if (closed || !before.isOpen || before.isLoading || claimAllInFlight || (nextPage > 0 && !before.hasMore)) return
        val fence = generation
        val version = ++countVersion
        _state.value = before.copy(isLoading = true, loadError = null)
        try {
            val page = gateway.page(nextPage, pageSize)
            if (fence != generation) return
            nextPage++
            val existing = _state.value.items
            _state.value = _state.value.copy(
                items = (existing + page.items).distinctBy { it.campaignId },
                total = page.total,
                availableCount = if (version == countVersion && activeClaimIds.isEmpty())
                    page.availableCount.coerceAtLeast(0) else _state.value.availableCount,
                isLoading = false
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (fence == generation) _state.value = _state.value.copy(isLoading = false, loadError = error.code())
        }
    }

    suspend fun claim(id: String) {
        val current = _state.value
        if (closed || !current.isOpen || claimAllInFlight || id in activeClaimIds ||
            current.items.none { it.campaignId == id && it.state == GiftState.AVAILABLE }) return
        val fence = generation
        countVersion++
        activeClaimIds += id
        _state.value = current.copy(pendingIds = current.pendingIds + id, itemErrors = current.itemErrors - id)
        try {
            val receipt = gateway.claim(id)
            val race = current.items.firstOrNull { it.campaignId == id }?.clanRace
            if (race != null && !validRaceReceipt(id, race, receipt)) throw GiftApiException("NETWORK_ERROR", "")
            // A route reset fences the view; the accepted claim still changed this account.
            if (closed) return
            refreshProfile()
            applyReceipt(receipt, updateItems = fence == generation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (fence == generation) {
                if (error.code() == "CONFLICT") refresh()
                else _state.value = _state.value.copy(itemErrors = _state.value.itemErrors + (id to error.code()))
            }
        } finally {
            activeClaimIds -= id
            countVersion++
            if (fence == generation) _state.value = _state.value.copy(pendingIds = _state.value.pendingIds - id)
            if (!closed) refreshAvailableCount()
        }
    }

    suspend fun claimAll() {
        val initial = _state.value
        if (closed || !initial.isOpen || claimAllInFlight || activeClaimIds.isNotEmpty()) return
        val fence = generation
        countVersion++
        claimAllInFlight = true
        _state.value = initial.copy(isClaimingAll = true, loadError = null)
        try {
            // Collect all server pages before claiming: the visible page is not the audience.
            var page = 0
            var availableCount = initial.availableCount
            val all = linkedMapOf<String, MailboxGift>()
            do {
                val response = gateway.page(page++, pageSize)
                if (fence != generation) return
                availableCount = response.availableCount.coerceAtLeast(0)
                response.items.forEach { all[it.campaignId] = it }
                if (response.items.isEmpty()) break
            } while (all.size < response.total)
            _state.value = _state.value.copy(items = all.values.toList(), total = all.size.toLong(), availableCount = availableCount)
            val available = all.values.filter { it.state == GiftState.AVAILABLE }.map { it.campaignId }
            for (batch in available.chunked(100)) {
                if (fence != generation) return
                _state.value = _state.value.copy(pendingIds = batch.toSet())
                try {
                    val results = gateway.claimAll(batch).items.associateBy { it.campaignId }
                    if (closed) return
                    val accepted = batch.mapNotNull { id ->
                        results[id]?.result?.takeIf { receipt ->
                            all[id]?.clanRace?.let { validRaceReceipt(id, it, receipt) } ?: true
                        }
                    }.associateBy { it.gift.campaignId }
                    if (accepted.isNotEmpty()) refreshProfile()
                    for (id in batch) {
                        val item = results[id]
                        val receipt = accepted[id]
                        if (receipt != null) applyReceipt(receipt, updateItems = fence == generation)
                        else if (fence == generation) markError(id, item?.error?.code ?: "NETWORK_ERROR")
                    }
                    if (fence != generation) return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (fence != generation) return
                    batch.forEach { markError(it, error.code()) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (fence == generation) _state.value = _state.value.copy(loadError = error.code())
        } finally {
            claimAllInFlight = false
            countVersion++
            if (fence == generation) {
                _state.value = _state.value.copy(isClaimingAll = false, pendingIds = emptySet())
            }
            if (!closed && reloadAfterClaimAll && _state.value.isOpen) {
                reloadAfterClaimAll = false
                open()
            }
            if (!closed) refreshAvailableCount()
        }
    }

    /** Route-away discards mailbox content while retaining the account's unclaimed badge. */
    fun reset() {
        generation++
        countVersion++
        nextPage = 0
        reloadAfterClaimAll = false
        _state.value = GiftMailboxState(availableCount = if (closed) 0 else _state.value.availableCount)
    }

    fun close() {
        if (closed) return
        closed = true
        reset()
        gateway.close()
    }

    private fun applyReceipt(receipt: GiftClaimResult, updateItems: Boolean = true) {
        val id = receipt.gift.campaignId
        countVersion++
        val newlyClaimed = receipt.gift.state == GiftState.CLAIMED && appliedReceiptIds.add(id)
        _state.value = _state.value.copy(
            availableCount = if (newlyClaimed) (_state.value.availableCount - 1).coerceAtLeast(0) else _state.value.availableCount,
            items = if (updateItems) _state.value.items.map { if (it.campaignId == id) receipt.gift else it } else _state.value.items,
            itemErrors = if (updateItems) _state.value.itemErrors - id else _state.value.itemErrors
        )
    }

    private fun markError(id: String, code: String) {
        _state.value = _state.value.copy(itemErrors = _state.value.itemErrors + (id to code))
    }

    private fun validRaceReceipt(id: String, context: ClanRaceMailboxContext, receipt: GiftClaimResult): Boolean =
        receipt.gift.campaignId == id && receipt.gift.state == GiftState.CLAIMED &&
            receipt.gift.clanRace?.entitlementId == context.entitlementId

    private fun Exception.code(): String = (this as? GiftApiException)?.code ?: "NETWORK_ERROR"
}
