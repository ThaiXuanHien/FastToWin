package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import kotlinx.serialization.Serializable

@Serializable enum class GiftAudience { ALL_REGISTERED, USER_IDS, REGISTERED_BETWEEN }
@Serializable enum class CampaignState { DRAFT, PUBLISHED, CANCELLED }
@Serializable data class GiftDraftRequest(
    val titleVi: String, val titleEn: String, val bodyVi: String, val bodyEn: String,
    val reason: String, val gold: Int, val xp: Int,
    val audience: GiftAudience = GiftAudience.ALL_REGISTERED,
    val userIds: List<String> = emptyList(),
    val registeredFromEpochMillis: Long? = null, val registeredUntilEpochMillis: Long? = null,
    val availableAtEpochMillis: Long, val expiresAtEpochMillis: Long? = null,
    val requestId: String, val expectedVersion: Int? = null, val gems: Int = 0
)
@Serializable data class GiftPreviewRequest(val expectedVersion: Int)
@Serializable data class GiftPublishRequest(val expectedVersion: Int, val previewToken: String, val requestId: String, val reason: String)
@Serializable data class GiftCancelRequest(val expectedVersion: Int, val requestId: String, val reason: String)
@Serializable data class GiftCampaign(
    val id: String, val version: Int, val state: CampaignState, val draft: GiftDraftRequest,
    val createdAtEpochMillis: Long, val publishedAtEpochMillis: Long? = null,
    val cancelledAtEpochMillis: Long? = null, val cancellationReason: String? = null,
    val compensation: CompensationMailboxContext? = null
)
@Serializable data class GiftCounters(
    val eligible: Long = 0, val pending: Long = 0, val delivered: Long = 0, val claimed: Long = 0,
    val expired: Long = 0, val cancelled: Long = 0, val skipped: Long = 0,
    val claimedGold: Long = 0, val claimedXp: Long = 0, val claimedGems: Long = 0
)
@Serializable data class GiftCampaignDetail(val campaign: GiftCampaign, val counters: GiftCounters)
@Serializable data class GiftPreview(
    val campaignId: String, val version: Int, val recipientCount: Long, val duplicateCount: Int,
    val invalidUserIds: List<String>, val totalGold: Long, val totalXp: Long, val previewToken: String, val totalGems: Long = 0
)
data class GiftAdminContext(val token: String?, val csrfToken: String? = null, val origin: String? = null)
data class GiftRecipient(val campaignId: String, val userId: String, val delivery: String = "PENDING", val claimedAt: Long? = null)
data class GiftPlayer(val id: String, val registeredAt: Long)
data class GiftBalance(val gold: Int, val xp: Int, val gems: Int = 0)
data class GiftRequestRecord(val fingerprint: String, val result: GiftCampaignDetail)

interface GiftRepository {
    suspend fun <T> transaction(admin: Boolean = false, block: GiftTransaction.() -> T): T
}
interface GiftTransaction {
    val admin: AdminTransaction
    val compensation: com.hienthai.fastowin.server.compensation.CompensationStore
    fun campaign(id: String, lock: Boolean = false): GiftCampaign?
    fun putCampaign(campaign: GiftCampaign)
    fun campaigns(page: PageRequest): AdminPage<GiftCampaign>
    fun activityCoverage(from: Long, until: Long, now: Long): com.hienthai.fastowin.server.analytics.AnalyticsCoverage =
        com.hienthai.fastowin.server.analytics.AnalyticsCoverage("SERVER", "LIVE", com.hienthai.fastowin.server.analytics.CoverageStatus.UNAVAILABLE, null, null)
    fun activityRecipients(from: Long, until: Long, now: Long): List<String> =
        throw AdminException(400, "ACTIVITY_SOURCE_UNAVAILABLE", "Connected account activity is unavailable for this interval.")
    fun registeredPlayers(): List<GiftPlayer>
    fun activePlayer(id: String): Boolean
    fun request(actor: String, requestId: String): GiftRequestRecord?
    fun putRequest(actor: String, requestId: String, record: GiftRequestRecord)
    fun freeze(campaignId: String, users: List<String>)
    fun recipient(campaignId: String, userId: String, lock: Boolean = false): GiftRecipient?
    fun putRecipient(recipient: GiftRecipient)
    fun pendingCampaigns(now: Long, limit: Int): List<String>
    fun pendingRecipients(campaignId: String, limit: Int): List<GiftRecipient>
    fun counters(campaign: GiftCampaign, now: Long): GiftCounters
    fun mailbox(userId: String, now: Long, page: PageRequest): Pair<List<Pair<GiftCampaign, GiftRecipient>>, Long>
    fun mailboxAvailableCount(userId: String, now: Long): Long
    fun balance(userId: String): GiftBalance
    fun award(userId: String, campaign: GiftCampaign, now: Long): GiftBalance
}
internal fun GiftCampaign.walletSourceType() = if (compensation == null) "ADMIN_GIFT" else "COMPENSATION"
