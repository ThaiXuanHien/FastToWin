package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import com.hienthai.fastowin.protocol.ClanDonationCurrency
import com.hienthai.fastowin.protocol.ClanDonationStatus

enum class ClanJoinRequestResult {
    REQUESTED,
    CLAN_NOT_FOUND,
    CLAN_FULL,
    OWN_CLAN,
    ALREADY_MEMBER,
    FAILED
}

enum class ClanJoinResponseResult {
    APPROVED,
    REJECTED,
    REQUEST_NOT_FOUND,
    CLAN_FULL,
    ALREADY_MEMBER,
    FAILED
}

data class ClanDonationResult(
    val status: ClanDonationStatus,
    val experienceGranted: Int = 0,
    val clanLevel: Int = 0
)

interface ClanRepository {
    suspend fun createClan(ownerId: String, name: String, description: String): String?
    suspend fun requestJoinClan(userId: String, clanId: String): ClanJoinRequestResult
    suspend fun respondJoinRequest(
        clanId: String,
        ownerId: String,
        userId: String,
        accept: Boolean
    ): ClanJoinResponseResult
    suspend fun getPendingJoinClanIds(userId: String): List<String>
    suspend fun leaveClan(userId: String): Boolean
    suspend fun getClanByUserId(userId: String): ClanSnapshot?
    suspend fun getClanById(clanId: String): ClanSnapshot?
    suspend fun getClanList(limit: Int = 50, offset: Int = 0, query: String? = null): List<ClanSummarySnapshot>
    suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String): Boolean
    suspend fun updateLogoId(clanId: String, logoId: String): Boolean
    suspend fun addClanTrophies(clanId: String, amount: Int): Boolean
    suspend fun addQuestProgress(clanId: String, userId: String, amount: Int): Boolean
    suspend fun claimQuestReward(clanId: String, userId: String): Boolean
    suspend fun donateToClan(
        userId: String,
        clanId: String,
        requestId: String,
        currency: ClanDonationCurrency,
        amount: Int
    ): ClanDonationResult = ClanDonationResult(ClanDonationStatus.FAILED)
}
