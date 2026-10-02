package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.GiftApiClient
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
    val isLoading: Boolean = false,
    val isClaimingAll: Boolean = false,
    val pendingIds: Set<String> = emptySet(),
    val itemErrors: Map<String, String> = emptyMap(),
    val loadError: String? = null
) {
    val hasMore: Boolean get() = items.size < total
    val hasAvailable: Boolean get() = items.any { it.state == GiftState.AVAILABLE }
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

    suspend fun open() {
        generation++
        nextPage = 0
        _state.value = GiftMailboxState(isOpen = true)
        loadNextPage()
    }

    suspend fun refresh() = open()

    suspend fun loadNextPage() {
        val before = _state.value
        if (!before.isOpen || before.isLoading || (nextPage > 0 && !before.hasMore)) return
        val fence = generation
        _state.value = before.copy(isLoading = true, loadError = null)
        try {
            val page = gateway.page(nextPage, pageSize)
            if (fence != generation) return
            nextPage++
            val existing = _state.value.items
            _state.value = _state.value.copy(
                items = (existing + page.items).distinctBy { it.campaignId },
                total = page.total,
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
        if (!current.isOpen || current.isClaimingAll || id in current.pendingIds ||
            current.items.none { it.campaignId == id && it.state == GiftState.AVAILABLE }) return
        val fence = generation
        _state.value = current.copy(pendingIds = current.pendingIds + id, itemErrors = current.itemErrors - id)
        try {
            val receipt = gateway.claim(id)
            if (fence != generation) return
            applyReceipt(receipt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (fence == generation) {
                if (error.code() == "CONFLICT") refresh()
                else _state.value = _state.value.copy(itemErrors = _state.value.itemErrors + (id to error.code()))
            }
        } finally {
            if (fence == generation) _state.value = _state.value.copy(pendingIds = _state.value.pendingIds - id)
        }
    }

    suspend fun claimAll() {
        val initial = _state.value
        if (!initial.isOpen || initial.isClaimingAll || initial.pendingIds.isNotEmpty()) return
        val fence = generation
        _state.value = initial.copy(isClaimingAll = true, loadError = null)
        try {
            // Collect all server pages before claiming: the visible page is not the audience.
            var page = 0
            val all = linkedMapOf<String, MailboxGift>()
            do {
                val response = gateway.page(page++, pageSize)
                if (fence != generation) return
                response.items.forEach { all[it.campaignId] = it }
                if (response.items.isEmpty()) break
            } while (all.size < response.total)
            _state.value = _state.value.copy(items = all.values.toList(), total = all.size.toLong())
            val available = all.values.filter { it.state == GiftState.AVAILABLE }.map { it.campaignId }
            for (batch in available.chunked(100)) {
                if (fence != generation) return
                _state.value = _state.value.copy(pendingIds = batch.toSet())
                try {
                    val results = gateway.claimAll(batch).items.associateBy { it.campaignId }
                    if (fence != generation) return
                    for (id in batch) {
                        val item = results[id]
                        val receipt = item?.result
                        if (receipt != null) applyReceipt(receipt)
                        else markError(id, item?.error?.code ?: "NETWORK_ERROR")
                    }
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
            if (fence == generation) _state.value = _state.value.copy(isClaimingAll = false, pendingIds = emptySet())
        }
    }

    fun reset() {
        generation++
        nextPage = 0
        _state.value = GiftMailboxState()
    }

    fun close() {
        reset()
        (gateway as? GiftApiClient)?.close()
    }

    private fun applyReceipt(receipt: GiftClaimResult) {
        val id = receipt.gift.campaignId
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.campaignId == id) receipt.gift else it },
            itemErrors = _state.value.itemErrors - id
        )
        // The receipt includes current balances; GameController is the sole profile authority.
        refreshProfile()
    }

    private fun markError(id: String, code: String) {
        _state.value = _state.value.copy(itemErrors = _state.value.itemErrors + (id to code))
    }

    private fun Exception.code(): String = (this as? GiftApiException)?.code ?: "NETWORK_ERROR"
}
