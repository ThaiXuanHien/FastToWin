package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.localization.TextKey

/** The sole provider verification and durable receipt grant authority for both transports. */
class StorePurchaseService(
    private val playerProfileRepository: PlayerProfileRepository,
    private val storePurchaseVerifier: StorePurchaseVerifier = RejectingStorePurchaseVerifier
) {
    suspend fun verify(playerId: String, request: StorePurchaseRequest): ServerMessage.StorePurchaseResult {
        if (request.requestId.length !in 1..64 || request.purchaseToken.length !in 1..4_096 ||
            request.productId.length !in 1..128) {
            return result(
                request,
                StorePurchaseStatus.INVALID,
                message = legacyFallback("Dữ liệu giao dịch không hợp lệ.")
            )
        }
        val gemPackage = GEM_STORE_PACKAGES.firstOrNull { it.productId == request.productId }
            ?: return result(
                request,
                StorePurchaseStatus.INVALID,
                message = legacyFallback("Gói Gem không tồn tại."),
                messageKey = TextKey.ServerResourceNotFound.name
            )
        val verification = storePurchaseVerifier.verify(StorePurchaseVerification(
            userId = playerId,
            store = request.store,
            productId = request.productId,
            purchaseToken = request.purchaseToken
        ))
        if (verification.status != StoreVerificationStatus.PURCHASED) {
            val status = if (verification.status == StoreVerificationStatus.INVALID) {
                StorePurchaseStatus.INVALID
            } else {
                StorePurchaseStatus.UNAVAILABLE
            }
            return result(
                request,
                status,
                message = verification.message,
                messageKey = verification.messageKey,
                messageArgs = verification.messageArgs
            )
        }
        val fingerprint = storePurchaseFingerprint(request.store, request.purchaseToken)
        val grantStatus = if (verification.verifiedOrder == null) playerProfileRepository.grantStorePurchase(
            playerId, request.store.name, request.productId, fingerprint, gemPackage.gems
        ) else playerProfileRepository.grantVerifiedStorePurchase(
            playerId = playerId,
            store = request.store.name,
            productId = request.productId,
            transactionId = fingerprint,
            gems = gemPackage.gems,
            order = verification.verifiedOrder
        )
        val result = when (grantStatus) {
            StorePurchaseGrantStatus.GRANTED -> StorePurchaseStatus.GRANTED to
                legacyFallback("Đã nhận ${gemPackage.gems} Gem.")
            StorePurchaseGrantStatus.ALREADY_GRANTED -> StorePurchaseStatus.ALREADY_GRANTED to
                legacyFallback("Giao dịch này đã được nhận trước đó.")
            StorePurchaseGrantStatus.TOKEN_ALREADY_USED -> StorePurchaseStatus.INVALID to
                legacyFallback("Giao dịch đã thuộc về tài khoản khác.")
            StorePurchaseGrantStatus.PLAYER_NOT_FOUND -> StorePurchaseStatus.FAILED to
                legacyFallback("Không tìm thấy hồ sơ người chơi.")
            StorePurchaseGrantStatus.FAILED -> StorePurchaseStatus.FAILED to
                legacyFallback("Chưa thể cộng Gem. Vui lòng thử lại.")
        }
        return result(
            request,
            result.first,
            gemsGranted = if (result.first == StorePurchaseStatus.GRANTED) gemPackage.gems else 0,
            message = result.second
        )
    }

    private fun result(
        request: StorePurchaseRequest,
        status: StorePurchaseStatus,
        gemsGranted: Int = 0,
        message: String,
        messageKey: String = when (status) {
            StorePurchaseStatus.GRANTED -> TextKey.BillingGemsAdded.name
            StorePurchaseStatus.ALREADY_GRANTED -> TextKey.ServerAlreadyExists.name
            StorePurchaseStatus.INVALID -> TextKey.BillingIncomplete.name
            StorePurchaseStatus.UNAVAILABLE -> TextKey.ServerUnavailable.name
            StorePurchaseStatus.FAILED -> TextKey.ServerActionFailed.name
        },
        messageArgs: Map<String, String> = emptyMap()
    ) = ServerMessage.StorePurchaseResult(
            requestId = request.requestId,
            productId = request.productId,
            status = status,
            gemsGranted = gemsGranted,
            message = message,
            messageKey = messageKey,
            messageArgs = messageArgs
        )

}
