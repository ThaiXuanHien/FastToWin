package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot

interface ClanRepository {
    suspend fun createClan(ownerId: String, name: String, description: String): String?
    suspend fun joinClan(userId: String, clanId: String): Boolean
    suspend fun leaveClan(userId: String): Boolean
    suspend fun getClanByUserId(userId: String): ClanSnapshot?
    suspend fun getClanById(clanId: String): ClanSnapshot?
    suspend fun getClanList(limit: Int = 50, offset: Int = 0): List<ClanSummarySnapshot>
    suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String): Boolean
}
