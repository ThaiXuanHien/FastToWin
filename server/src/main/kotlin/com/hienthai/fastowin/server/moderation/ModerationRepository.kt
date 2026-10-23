package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.AdminTransaction

interface ModerationRepository {
    suspend fun <T> transaction(block: ModerationTransaction.() -> T): T
    suspend fun <T> read(block: ModerationReadTransaction.() -> T): T = transaction {block(this)}
}
interface ModerationReadTransaction {
    fun acceptedVersion(playerId: String): String?
    fun playerExists(playerId: String): Boolean
    fun target(type: UgcTargetType, id: String): UgcTarget?
    fun suppression(type: UgcTargetType, targetId: String, revision: String?): Boolean
    fun suppressions(): List<ContentSuppression>
    fun restriction(userId: String): ModerationRestrictionView
}
interface ModerationTransaction : ModerationReadTransaction {
    val admin: AdminTransaction
    fun registerPlayer(playerId: String)
    fun acceptTerms(playerId: String, version: String, nowMillis: Long)
    /** Publishes live targets for dev/in-memory repositories; PostgreSQL derives targets from source tables. */
    fun putTarget(target: UgcTarget)
    fun removeTarget(type: UgcTargetType, id: String)
    fun publishedTargets(): List<Pair<UgcTargetType,String>>
    fun appendAction(value: ModerationActionRecord)
    fun lastActionReason(reportId: String): String?
    fun report(id: String): ModerationReport?
    fun openReport(reporterId: String, type: UgcTargetType, targetId: String): ModerationReport?
    fun putReport(value: ModerationReport)
    fun reports(status: ModerationStatus?, limit: Int, offset: Int): List<ModerationReport>
    fun reportCount(status: ModerationStatus?): Long
    fun replay(actorId: String, requestId: String): ModerationReplay?
    fun putReplay(value: ModerationReplay)
    fun rateCount(playerId: String, since: Long): Int
    fun addRateEvent(playerId: String, nowMillis: Long)
    fun putSuppression(value: ContentSuppression)
    /** Persists restriction and revokes every access/refresh/resume session on block. */
    fun putRestriction(value: ModerationRestrictionView, nowMillis: Long)
    /** Remove all identifying evidence, including reports authored by or targeting the erased player. */
    fun erasePlayer(playerId: String)
    fun cleanup(closedBefore: Long, nowMillis: Long): Int
}
