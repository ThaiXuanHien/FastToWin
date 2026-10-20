package com.hienthai.fastowin.platform

import com.hienthai.fastowin.data.network.RewardedAdSessionGateway
import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

enum class RewardedAdAuthorization { ALLOWED, AGE_REQUIRED, AGE_BLOCKED, DENIED }
enum class RewardedAdShowResult { EARNED, DISMISSED, UNAVAILABLE }
enum class RewardedAdMode { DISABLED, SIMULATED, DEMO, TEST, LIVE }
enum class RewardedAdAgeBand { UNKNOWN, UNDER_13, AGE_13_15, AGE_16_17, ADULT }
data class RewardedAdConfiguration(
    val mode: RewardedAdMode = RewardedAdMode.DISABLED,
    val appId: String = "",
    val adUnitId: String = "",
    val testDeviceIds: List<String> = emptyList()
) {
    val canRequest: Boolean get() = when (mode) {
        RewardedAdMode.DEMO -> appId == DEMO_APP_ID && adUnitId == DEMO_UNIT_ID
        RewardedAdMode.TEST, RewardedAdMode.LIVE ->
            Regex("ca-app-pub-[0-9]{16}~[0-9]{10}").matches(appId) &&
                Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(adUnitId) &&
                !appId.startsWith(DEMO_PUBLISHER) && !adUnitId.startsWith(DEMO_PUBLISHER) &&
                (mode == RewardedAdMode.LIVE || (testDeviceIds.isNotEmpty() && testDeviceIds.all { Regex("[A-Fa-f0-9]{32}").matches(it) }))
        else -> false
    }
    companion object {
        private const val DEMO_PUBLISHER = "ca-app-pub-3940256099942544"
        const val DEMO_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val DEMO_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
/** SDK callbacks and platform UI stay behind this seam; earned is never authoritative. */
interface RewardedAdDriver {
    val adUnitId: String? get() = null
    suspend fun authorize(): RewardedAdAuthorization
    suspend fun load(): Boolean
    suspend fun show(session: RewardedAdSessionView): RewardedAdShowResult
    fun cancel() = Unit
    fun close()
}
interface PendingRewardedAdStore {
    fun read(ownerId: String): String?
    fun write(ownerId: String, sessionId: String?)
}
/** Created per authenticated owner. Cancellation plus generation fences every late platform callback. */
class VerifiedRewardedAdGateway(
    private val api: RewardedAdSessionGateway,
    private val driver: RewardedAdDriver,
    private val store: PendingRewardedAdStore,
    scope: CoroutineScope,
    private val nowMillis: () -> Long = ::epochMillis
) : RewardedAdGateway {
    private val lifetime = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + lifetime)
    private val mutableState = MutableStateFlow(RewardedAdGatewayState())
    override val state = mutableState.asStateFlow()
    private val mutableReceipts = MutableSharedFlow<RewardedAdReceipt>(extraBufferCapacity = 1)
    override val receipts = mutableReceipts.asSharedFlow()
    private var generation = 0
    private var closed = false
    private var busy = false
    private var job: Job? = null
    private val emitted = mutableSetOf<String>()

    override fun configure(availability: RewardedAdAvailability) {
        if (closed) return
        if (availability == RewardedAdAvailability.UNAVAILABLE) {
            generation++; job?.cancel(); driver.cancel(); busy = false
            mutableState.value = RewardedAdGatewayState(error = LocalizedText(TextKey.RewardedAdUnavailable))
            return
        }
        mutableState.value = mutableState.value.copy(availability = availability, isReady = true,
            error = if (state.value.availability == RewardedAdAvailability.UNAVAILABLE) null else state.value.error)
        if (!busy && store.read(api.ownerId) != null && !mutableState.value.isPendingVerification) start(recoverOnly = true)
    }
    override fun show(userId: String) {
        if (closed || busy || userId != api.ownerId || !state.value.isReady) return
        start(recoverOnly = false)
    }
    private fun start(recoverOnly: Boolean) {
        busy = true
        val fence = ++generation
        mutableState.value = state.value.copy(isLoading = true, error = null, notice = LocalizedText(TextKey.RewardedAdLoading))
        job = scope.launch {
            try {
                val existing = store.read(api.ownerId)
                if (existing != null) { poll(existing, fence); return@launch }
                if (recoverOnly) return@launch
                when (driver.authorize()) {
                    RewardedAdAuthorization.ALLOWED -> Unit
                    RewardedAdAuthorization.AGE_REQUIRED -> { fail(TextKey.RewardedAdAgeMessage); return@launch }
                    RewardedAdAuthorization.AGE_BLOCKED -> { fail(TextKey.RewardedAdAgeBlocked); return@launch }
                    RewardedAdAuthorization.DENIED -> { fail(TextKey.RewardedAdPrivacyDenied); return@launch }
                }
                // Avoid consuming the server's bounded pending-session quota on no-fill.
                if (!driver.load()) { fail(TextKey.RewardedAdUnavailable); return@launch }
                val session = api.prepare(Random.nextLong().toULong().toString(16) + Random.nextLong().toULong().toString(16))
                currentCoroutineContext().ensureActive()
                if (!current(fence) || (driver.adUnitId != null && session.adUnitId != driver.adUnitId) || session.expiresAtEpochMillis <= nowMillis()) {
                    fail(TextKey.RewardedAdUnavailable); return@launch
                }
                if (session.status != RewardedAdSessionStatus.PENDING) { fail(TextKey.RewardedAdUnavailable); return@launch }
                store.write(api.ownerId, session.sessionId)
                val outcome = driver.show(session)
                currentCoroutineContext().ensureActive()
                if (!current(fence)) return@launch
                if (outcome == RewardedAdShowResult.UNAVAILABLE) {
                    // The SDK may have started a show: retain the session for a delayed signed callback.
                    mutableState.value = state.value.copy(error = LocalizedText(TextKey.RewardedAdUnavailable))
                } else if (outcome == RewardedAdShowResult.DISMISSED) {
                    mutableState.value = state.value.copy(notice = LocalizedText(TextKey.RewardedAdCancelled))
                }
                poll(session.sessionId, fence)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (current(fence)) {
                    if (store.read(api.ownerId) != null) pending() else fail(TextKey.RewardedAdUnavailable)
                }
            } finally {
                if (current(fence)) { busy = false; mutableState.value = state.value.copy(isLoading = false) }
            }
        }
    }
    private suspend fun poll(sessionId: String, fence: Int) {
        pending()
        val finished = withTimeoutOrNull(60_000) {
            var wait = 1000L
            while (current(fence)) {
                val result = api.status(sessionId)
                currentCoroutineContext().ensureActive()
                if (!current(fence) || result.sessionId != sessionId) return@withTimeoutOrNull false
                when (result.status) {
                    RewardedAdSessionStatus.GRANTED -> {
                        val transaction = result.providerTransactionId?.takeIf { it.isNotBlank() }
                        if (transaction == null || result.quota == null) return@withTimeoutOrNull false
                        if (emitted.add(sessionId)) mutableReceipts.emit(RewardedAdReceipt(sessionId, RewardedAdProvider.ADMOB_ANDROID, transaction, sessionId))
                        store.write(api.ownerId, null)
                        mutableState.value = state.value.copy(isPendingVerification = false, notice = LocalizedText(TextKey.RewardedAdGranted), error = null)
                        return@withTimeoutOrNull true
                    }
                    RewardedAdSessionStatus.EXPIRED, RewardedAdSessionStatus.FAILED -> {
                        store.write(api.ownerId, null)
                        fail(TextKey.RewardedAdCancelled)
                        return@withTimeoutOrNull true
                    }
                    RewardedAdSessionStatus.PENDING -> { delay(wait); wait = (wait * 2).coerceAtMost(10_000) }
                }
            }
            false
        }
        if (finished != true && current(fence)) pending()
    }
    private fun pending() { mutableState.value = state.value.copy(isPendingVerification = true, notice = LocalizedText(TextKey.RewardedAdPending)) }
    private fun fail(key: TextKey) { mutableState.value = state.value.copy(isPendingVerification = false, notice = null, error = LocalizedText(key)) }
    private fun current(fence: Int) = !closed && generation == fence
    override fun cancel() {
        if (closed) return
        generation++; job?.cancel(); driver.cancel(); busy = false
        mutableState.value = state.value.copy(isLoading = false, isPendingVerification = store.read(api.ownerId) != null,
            notice = LocalizedText(if (store.read(api.ownerId) != null) TextKey.RewardedAdPending else TextKey.RewardedAdCancelled), error = null)
    }
    override fun close() {
        if (closed) return
        closed = true; generation++; lifetime.cancel(); driver.close(); api.close()
        mutableState.value = RewardedAdGatewayState()
    }
}
