package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.server.admin.AdminPage
import kotlinx.serialization.Serializable

// HTTP projections only. Domain arithmetic and persisted replay JSON keep Long.
@Serializable internal data class GiftAdminCounters(
    val eligible: String, val pending: String, val delivered: String, val claimed: String,
    val expired: String, val cancelled: String, val skipped: String,
    val claimedGold: String, val claimedXp: String, val claimedGems: String
)
@Serializable internal data class GiftAdminDetail(val campaign: GiftCampaign, val counters: GiftAdminCounters)
@Serializable internal data class GiftAdminPage(
    val items: List<GiftAdminDetail>, val page: Int, val pageSize: Int, val total: String
)
@Serializable internal data class GiftAdminPreview(
    val campaignId: String, val version: Int, val recipientCount: String, val duplicateCount: Int,
    val invalidUserIds: List<String>, val totalGold: String, val totalXp: String, val previewToken: String, val totalGems: String
)
internal fun GiftCampaignDetail.adminResponse() = GiftAdminDetail(campaign, counters.run {
    GiftAdminCounters(eligible.toString(), pending.toString(), delivered.toString(), claimed.toString(),
        expired.toString(), cancelled.toString(), skipped.toString(), claimedGold.toString(), claimedXp.toString(), claimedGems.toString())
})
internal fun AdminPage<GiftCampaignDetail>.adminResponse() =
    GiftAdminPage(items.map { it.adminResponse() }, page, pageSize, total.toString())
internal fun GiftPreview.adminResponse() = GiftAdminPreview(campaignId, version, recipientCount.toString(),
    duplicateCount, invalidUserIds, totalGold.toString(), totalXp.toString(), previewToken, totalGems.toString())
