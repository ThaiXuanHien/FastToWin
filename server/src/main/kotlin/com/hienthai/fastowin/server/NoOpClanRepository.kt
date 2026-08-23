package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot

object NoOpClanRepository : ClanRepository {
    override suspend fun createClan(ownerId: String, name: String, description: String): String? = null
    override suspend fun requestJoinClan(userId: String, clanId: String) = ClanJoinRequestResult.FAILED
    override suspend fun respondJoinRequest(clanId: String, ownerId: String, userId: String, accept: Boolean) =
        ClanJoinResponseResult.FAILED
    override suspend fun getPendingJoinClanIds(userId: String): List<String> = emptyList()
    override suspend fun leaveClan(userId: String): Boolean = false
    override suspend fun getClanByUserId(userId: String): ClanSnapshot? = null
    override suspend fun getClanById(clanId: String): ClanSnapshot? = null
    override suspend fun getClanList(limit: Int, offset: Int, query: String?): List<ClanSummarySnapshot> = emptyList()
    override suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String): Boolean = false
    override suspend fun updateLogoId(clanId: String, logoId: String): Boolean = false
    override suspend fun addClanTrophies(clanId: String, amount: Int): Boolean = false
    override suspend fun addQuestProgress(clanId: String, userId: String, amount: Int): Boolean = false
    override suspend fun claimQuestReward(clanId: String, userId: String): Boolean = false
}
