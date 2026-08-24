package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.MatchDetailSnapshot
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot

interface PlayerProfileRepository {
    suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot?
    suspend fun settleCompletedSeasonRewards(playerId: String): Boolean = false
    suspend fun acknowledgeSeasonReward(playerId: String, seasonNumber: Int): Boolean = false
    suspend fun findAppearance(playerId: String): PlayerAppearance? = findByPlayerId(playerId)?.let { profile ->
        PlayerAppearance(
            avatarId = profile.avatarId,
            frameId = profile.progression.cosmetics.firstOrNull {
                it.type == CosmeticType.FRAME && it.equipped
            }?.id ?: "frame_default"
        )
    }
    suspend fun findByPlayerCode(playerCode: String): PlayerProfileSnapshot? = null
    suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean
    suspend fun updateFcmToken(playerId: String, token: String): Boolean = false
    suspend fun findFcmToken(playerId: String): String? = null
    suspend fun findMatchDetail(playerId: String, matchId: String): MatchDetailSnapshot? = null
    suspend fun equipCosmetics(playerId: String, frameId: String, titleId: String): Boolean = false
    suspend fun claimDailyCheckIn(playerId: String): DailyCheckInClaimResult? = null
    suspend fun claimMissionReward(playerId: String, missionCode: String): MissionRewardClaimResult? = null
    suspend fun updateAvatarData(playerId: String, base64: String): Boolean = false
    suspend fun getAvatarData(playerId: String): String? = null
    suspend fun loadWalletHistory(playerId: String, limit: Int = 50): List<WalletTransactionSnapshot> = emptyList()
    suspend fun applyWalletTransaction(
        playerId: String,
        sourceType: String,
        sourceId: String,
        goldDelta: Int = 0,
        gemsDelta: Int = 0,
        xpDelta: Int = 0
    ): WalletMutationStatus = WalletMutationStatus.PLAYER_NOT_FOUND
    suspend fun grantStorePurchase(
        playerId: String,
        store: String,
        productId: String,
        transactionId: String,
        gems: Int
    ): StorePurchaseGrantStatus = StorePurchaseGrantStatus.PLAYER_NOT_FOUND
    suspend fun buyCosmetic(playerId: String, cosmeticId: String, cosmeticType: String, price: Int): Boolean = false
    suspend fun equipCosmetic(playerId: String, cosmeticId: String, cosmeticType: String): Boolean = false
}

data class DailyCheckInClaimResult(
    val claimed: Boolean,
    val rewardXp: Int,
    val rewardGold: Int = 0,
    val rewardGems: Int = 0
)

enum class MissionRewardClaimStatus { CLAIMED, ALREADY_CLAIMED, NOT_COMPLETED, INVALID_MISSION }

enum class WalletMutationStatus { APPLIED, DUPLICATE, INSUFFICIENT_FUNDS, PLAYER_NOT_FOUND }

enum class StorePurchaseGrantStatus {
    GRANTED,
    ALREADY_GRANTED,
    TOKEN_ALREADY_USED,
    PLAYER_NOT_FOUND,
    FAILED
}

data class PlayerAppearance(
    val avatarId: String? = null,
    val frameId: String = "frame_default"
)

data class MissionRewardClaimResult(
    val status: MissionRewardClaimStatus,
    val rewardXp: Int = 0,
    val rewardGold: Int = 0,
    val rewardGems: Int = 0
)

object NoOpPlayerProfileRepository : PlayerProfileRepository {
    override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = null
    override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean = false
}
