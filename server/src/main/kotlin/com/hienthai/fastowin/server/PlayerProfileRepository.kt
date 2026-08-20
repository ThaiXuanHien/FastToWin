package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.MatchDetailSnapshot

interface PlayerProfileRepository {
    suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot?
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
    suspend fun updateGold(playerId: String, amountDelta: Int): Boolean = false
    suspend fun buyCosmetic(playerId: String, cosmeticId: String, cosmeticType: String, price: Int): Boolean = false
    suspend fun equipCosmetic(playerId: String, cosmeticId: String, cosmeticType: String): Boolean = false
}

data class DailyCheckInClaimResult(
    val claimed: Boolean,
    val rewardXp: Int
)

enum class MissionRewardClaimStatus { CLAIMED, ALREADY_CLAIMED, NOT_COMPLETED, INVALID_MISSION }

data class MissionRewardClaimResult(
    val status: MissionRewardClaimStatus,
    val rewardXp: Int = 0
)

object NoOpPlayerProfileRepository : PlayerProfileRepository {
    override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = null
    override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean = false
}
