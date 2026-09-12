package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.protocol.RewardedAdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class RewardedAdReceipt(
    val requestId: String,
    val provider: RewardedAdProvider,
    val providerTransactionId: String,
    val proof: String
)

data class RewardedAdGatewayState(
    val availability: RewardedAdAvailability = RewardedAdAvailability.UNAVAILABLE,
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val notice: LocalizedText? = null,
    val error: LocalizedText? = null
)

interface RewardedAdGateway {
    val state: StateFlow<RewardedAdGatewayState>
    val receipts: Flow<RewardedAdReceipt>

    fun configure(availability: RewardedAdAvailability)
    fun show(userId: String)
    fun close()
}

fun devRewardedAdReceipt(
    userId: String,
    transactionId: String,
    requestId: String
): RewardedAdReceipt? {
    if (userId.isBlank() || transactionId.isBlank() || requestId.isBlank()) return null
    return RewardedAdReceipt(
        requestId = requestId,
        provider = RewardedAdProvider.DEV_SIMULATED,
        providerTransactionId = transactionId,
        proof = "FASTTOWIN_DEV_REWARDED_V1:$userId:$transactionId"
    )
}

internal class DefaultRewardedAdGateway(
    private val isWeb: Boolean
) : RewardedAdGateway {
    private val mutableState = MutableStateFlow(RewardedAdGatewayState())
    override val state: StateFlow<RewardedAdGatewayState> = mutableState.asStateFlow()

    private val mutableReceipts = MutableSharedFlow<RewardedAdReceipt>(extraBufferCapacity = 4)
    override val receipts: Flow<RewardedAdReceipt> = mutableReceipts.asSharedFlow()

    private var closed = false

    override fun configure(availability: RewardedAdAvailability) {
        if (closed) return
        mutableState.value = when (availability) {
            RewardedAdAvailability.DEV_SIMULATED -> RewardedAdGatewayState(
                availability = availability,
                isReady = true
            )
            RewardedAdAvailability.MOBILE_PRODUCTION,
            RewardedAdAvailability.UNAVAILABLE -> RewardedAdGatewayState(
                availability = availability,
                isReady = false,
                error = LocalizedText(
                    if (isWeb) TextKey.RewardedAdUseMobile else TextKey.RewardedAdUnavailable
                )
            )
        }
    }

    override fun show(userId: String) {
        if (closed || userId.isBlank()) return
        if (mutableState.value.availability != RewardedAdAvailability.DEV_SIMULATED) {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                isReady = false,
                error = LocalizedText(
                    if (isWeb) TextKey.RewardedAdUseMobile else TextKey.RewardedAdUnavailable
                )
            )
            return
        }

        mutableState.value = mutableState.value.copy(isLoading = true, error = null, notice = null)
        val suffix = randomRewardedAdId()
        val receipt = devRewardedAdReceipt(
            userId = userId,
            transactionId = "dev-transaction-$suffix",
            requestId = "dev-request-$suffix"
        )
        if (receipt != null) mutableReceipts.tryEmit(receipt)
        mutableState.value = mutableState.value.copy(isLoading = false, isReady = true)
    }

    override fun close() {
        closed = true
        mutableState.value = RewardedAdGatewayState()
    }
}

private fun randomRewardedAdId(): String = buildString {
    repeat(2) { append(Random.nextLong().toULong().toString(16).padStart(16, '0')) }
}

@Composable
expect fun rememberRewardedAdGateway(): RewardedAdGateway
