package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.MissionApiException
import com.hienthai.fastowin.data.network.MissionGateway
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlin.random.Random

data class MissionState(
    val items: List<MissionInstanceView> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val pendingIds: Set<String> = emptySet(),
    val itemErrors: Map<String, String> = emptyMap()
)

/** Owns only server snapshots and request intent; reward calculation stays on the server. */
class MissionController(
    private val gateway: MissionGateway,
    private val refreshProfile: () -> Unit,
    private val requestId: () -> String = { List(32) { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("") }
) {
    private data class Context(val identity: String?, val scope: MissionScope, val clan: String?, val view: String, val sessionEpoch: String?)
    private val mutableState = MutableStateFlow(MissionState())
    val state: StateFlow<MissionState> = mutableState.asStateFlow()
    private var context: Context? = null
    private var generation = 0L
    private var loadVersion = 0L
    private var closed = false
    private val intents = mutableMapOf<String, String>()
    private val claimed = mutableSetOf<String>()
    private val walletReconciled = mutableSetOf<String>()

    /** Account, token epoch, view and clan changes immediately remove stale claim affordances. */
    fun setContext(identity: String?, scope: MissionScope, clanId: String? = null, view: String = "open", sessionEpoch: String? = null) {
        val next = Context(identity, scope, clanId, view, sessionEpoch)
        if (closed || context == next) return
        if (context?.identity != identity || context?.clan != clanId) { intents.clear(); claimed.clear(); walletReconciled.clear() }
        context = next
        generation++
        loadVersion++
        mutableState.value = MissionState()
    }

    suspend fun refresh() {
        val owner = context ?: return
        if (closed || owner.identity == null || owner.view == "closed") return
        val fence = generation
        val version = ++loadVersion
        mutableState.value = mutableState.value.copy(isLoading = true, loadError = null)
        try {
            val response = gateway.list(owner.scope)
            if (!current(fence) || version != loadVersion) return
            val byId = response.items.associateBy { it.instanceId }
            // Claimed past periods may disappear from this endpoint; only a wallet reread can reconcile them.
            val reconciled = intents.keys.filter { id ->
                id !in walletReconciled && (byId[id] == null || byId[id]?.state == MissionClaimState.CLAIMED)
            }
            response.items.filter { it.state == MissionClaimState.CLAIMED }.forEach { claimed += it.instanceId }
            val items = response.items.map { item ->
                if (item.instanceId in claimed) item.copy(state = MissionClaimState.CLAIMED) else item
            }
            mutableState.value = mutableState.value.copy(
                items = items,
                itemErrors = mutableState.value.itemErrors.filterKeys { id -> items.any { it.instanceId == id && it.state != MissionClaimState.CLAIMED } }
            )
            if (reconciled.isNotEmpty()) {
                walletReconciled += reconciled
                refreshProfile()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (current(fence) && version == loadVersion) mutableState.value = mutableState.value.copy(loadError = error.code())
        } finally {
            if (current(fence) && version == loadVersion) mutableState.value = mutableState.value.copy(isLoading = false)
        }
    }

    suspend fun claim(id: String) {
        val before = mutableState.value
        if (closed || context?.identity == null || context?.view == "closed" || id in before.pendingIds ||
            before.items.none { it.instanceId == id && it.state == MissionClaimState.CLAIMABLE }) return
        val fence = generation
        // A list already in flight predates this grant and cannot overwrite its result.
        loadVersion++
        val intent = intents.getOrPut(id, requestId)
        mutableState.value = before.copy(pendingIds = before.pendingIds + id, itemErrors = before.itemErrors - id, isLoading = false)
        try {
            val receipt = gateway.claim(id, intent)
            if (!current(fence)) return
            if (receipt.instanceId != id || receipt.state != MissionClaimState.CLAIMED) throw MissionApiException("NETWORK_ERROR")
            claimed += id
            mutableState.value = mutableState.value.copy(items = mutableState.value.items.map {
                if (it.instanceId == id) it.copy(state = MissionClaimState.CLAIMED) else it
            })
            if (walletReconciled.add(id)) refreshProfile()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (current(fence) && mutableState.value.items.any { it.instanceId == id && it.state != MissionClaimState.CLAIMED })
                mutableState.value = mutableState.value.copy(itemErrors = mutableState.value.itemErrors + (id to error.code()))
        } finally {
            if (current(fence)) mutableState.value = mutableState.value.copy(pendingIds = mutableState.value.pendingIds - id)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        generation++
        mutableState.value = MissionState()
        intents.clear(); claimed.clear(); walletReconciled.clear()
        gateway.close()
    }
    private fun current(fence: Long) = !closed && fence == generation
    private fun Throwable.code() = (this as? MissionApiException)?.code ?: "NETWORK_ERROR"
}
