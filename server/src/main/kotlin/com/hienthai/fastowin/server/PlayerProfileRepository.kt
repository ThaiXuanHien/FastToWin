package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.MatchDetailSnapshot

interface PlayerProfileRepository {
    suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot?
    suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean
    suspend fun findMatchDetail(playerId: String, matchId: String): MatchDetailSnapshot? = null
    suspend fun equipCosmetics(playerId: String, frameId: String, titleId: String): Boolean = false
    suspend fun claimDailyCheckIn(playerId: String): DailyCheckInClaimResult? = null
    suspend fun claimMissionReward(playerId: String, missionCode: String): MissionRewardClaimResult? = null
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
