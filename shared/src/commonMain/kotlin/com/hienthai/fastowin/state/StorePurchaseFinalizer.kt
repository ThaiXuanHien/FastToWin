package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.StorePurchaseGateway
import com.hienthai.fastowin.platform.PlatformStorePurchase
import com.hienthai.fastowin.platform.StoreBillingGateway
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StoreFinalizationState(
    val accountId: String? = null,
    val profileRevision: Int = 0,
    val result: ServerMessage.StorePurchaseResult? = null
)

/** Root-owned paid work, independent of the game controller and its socket. Main/UI thread confined. */
class StorePurchaseFinalizer(private val billing: StoreBillingGateway) {
    private data class Pending(val purchase: PlatformStorePurchase, var owner: String?)
    private val pending = mutableMapOf<String, Pending>()
    private val retryMutex = Mutex()
    private var accountId: String? = null
    private var gateway: StorePurchaseGateway? = null
    private var generation = 0L
    private val mutableState = MutableStateFlow(StoreFinalizationState())
    val state = mutableState.asStateFlow()
    val pendingCount: Int get() = pending.size

    fun setAccount(id: String?, api: StorePurchaseGateway?) {
        if (id == accountId && api === gateway) return
        generation++
        accountId = id; gateway = api
        mutableState.value = StoreFinalizationState(id)
    }

    fun enqueue(purchase: PlatformStorePurchase) {
        pending.getOrPut(purchase.purchaseToken) {
            Pending(purchase, purchase.accountId ?: accountId?.takeIf { matches(purchase, it) })
        }
    }

    suspend fun retryPending() = retryMutex.withLock {
        val owner = accountId ?: return@withLock
        val api = gateway ?: return@withLock
        val epoch = generation
        for (entry in pending.values.toList()) {
            if (epoch != generation) break
            if (entry.owner == null && matches(entry.purchase, owner)) entry.owner = owner
            if (entry.owner != owner || !matches(entry.purchase, owner)) continue
            val purchase = entry.purchase
            val result = try {
                api.verify(StorePurchaseRequest(purchase.store, purchase.productId, purchase.purchaseToken, purchase.requestId))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                ServerMessage.StorePurchaseResult(purchase.requestId, purchase.productId, StorePurchaseStatus.UNAVAILABLE,
                    message = "", messageKey = "ServerUnavailable")
            }
            if (epoch != generation || result.requestId != purchase.requestId || result.productId != purchase.productId) continue
            mutableState.value = mutableState.value.copy(result = result)
            if (result.status == StorePurchaseStatus.GRANTED || result.status == StorePurchaseStatus.ALREADY_GRANTED) {
                // Balances are refreshed even if consumption fails; the paid receipt remains retryable.
                mutableState.value = mutableState.value.copy(profileRevision = mutableState.value.profileRevision + 1)
                val finished = try { billing.finishPurchaseAndConfirm(purchase.purchaseToken) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
                if (epoch == generation && finished) pending.remove(purchase.purchaseToken)
            }
        }
    }

    private fun matches(purchase: PlatformStorePurchase, id: String): Boolean =
        (purchase.accountId == null || purchase.accountId == id) &&
            (purchase.obfuscatedAccountId == null || purchase.obfuscatedAccountId == sha256(id.encodeToByteArray()))
}
