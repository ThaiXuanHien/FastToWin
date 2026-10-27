package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.compensation.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared with the in-memory admin transaction so player balances, history and audit use its snapshot. */
internal interface GiftMemoryPlayers {
    fun giftPlayers(): List<GiftPlayer>
    fun giftBalance(id: String): GiftBalance
    fun giftAward(id: String, campaign: GiftCampaign, now: Long): GiftBalance
}
class InMemoryGiftRepository(private val admins: InMemoryAdminRepository) : GiftRepository {
    private val mutex = Mutex()
    private data class State(
        val campaigns: MutableMap<String, GiftCampaign> = linkedMapOf(),
        val recipients: MutableMap<Pair<String,String>, GiftRecipient> = linkedMapOf(),
        val requests: MutableMap<Pair<String,String>, GiftRequestRecord> = linkedMapOf(),
        val compensation: MemoryCompensationState = MemoryCompensationState()
    ) {
        fun snapshot() = State(campaigns.toMutableMap(), recipients.toMutableMap(), requests.toMutableMap(), compensation.snapshot())
    }
    private var state = State()
    override suspend fun <T> transaction(admin: Boolean, block: GiftTransaction.() -> T): T = mutex.withLock {
        val next = state.snapshot()
        val result = admins.transaction {
            val adminTx = this; val players = this as GiftMemoryPlayers
            val tx = object : GiftTransaction {
                override val admin = adminTx
                override val compensation = MemoryCompensationStore(next.compensation, { next.campaigns[it] }, { next.recipients.values })
                override fun campaign(id: String, lock: Boolean) = next.campaigns[id]
                override fun putCampaign(campaign: GiftCampaign) { next.campaigns[campaign.id] = campaign }
                override fun campaigns(page: PageRequest): AdminPage<GiftCampaign> {
                    val all = next.campaigns.values.filter { it.compensation == null && it.clanRace == null }.sortedByDescending { it.id }; return page.result(all.drop(page.offset).take(page.pageSize), all.size.toLong())
                }
                override fun registeredPlayers() = players.giftPlayers()
                override fun activePlayer(id: String) = players.giftPlayers().any { it.id == id }
                override fun request(actor: String, requestId: String) = next.requests[actor to requestId]
                override fun putRequest(actor: String, requestId: String, record: GiftRequestRecord) { next.requests[actor to requestId] = record }
                override fun freeze(campaignId: String, users: List<String>) { users.forEach { user -> next.recipients[campaignId to user] = GiftRecipient(campaignId,user) } }
                override fun recipient(campaignId: String, userId: String, lock: Boolean) = next.recipients[campaignId to userId]
                override fun putRecipient(recipient: GiftRecipient) { next.recipients[recipient.campaignId to recipient.userId] = recipient }
                override fun pendingCampaigns(now: Long, limit: Int) = next.campaigns.values.filter { it.state == CampaignState.PUBLISHED && it.draft.availableAtEpochMillis <= now && pendingRecipients(it.id,1).isNotEmpty() }.sortedBy { it.id }.take(limit).map { it.id }
                override fun pendingRecipients(campaignId: String, limit: Int) = next.recipients.values.filter { it.campaignId == campaignId && it.delivery == "PENDING" }.sortedBy { it.userId }.take(limit)
                override fun counters(campaign: GiftCampaign, now: Long): GiftCounters {
                    val all = next.recipients.values.filter { it.campaignId == campaign.id }; val cancelled = campaign.state == CampaignState.CANCELLED
                    val claimed = all.count { it.claimedAt != null }.toLong()
                    return GiftCounters(all.size.toLong(), if (cancelled) 0 else all.count { it.delivery == "PENDING" }.toLong(), all.count { it.delivery == "DELIVERED" }.toLong(), claimed,
                        if (!cancelled && now >= campaign.draft.expiresAtEpochMillis!!) all.count { it.delivery == "DELIVERED" && it.claimedAt == null }.toLong() else 0,
                        if (cancelled) all.count { it.delivery != "SKIPPED" && it.claimedAt == null }.toLong() else 0, all.count { it.delivery == "SKIPPED" }.toLong(), claimed * campaign.draft.gold, claimed * campaign.draft.xp, claimed * campaign.draft.gems)
                }
                override fun mailbox(userId: String, now: Long, page: PageRequest): Pair<List<Pair<GiftCampaign, GiftRecipient>>, Long> {
                    val all = next.recipients.values.filter { it.userId == userId && it.delivery == "DELIVERED" }.map { next.campaigns.getValue(it.campaignId) to it }
                        .filter { it.first.draft.availableAtEpochMillis <= now }.sortedWith(compareByDescending<Pair<GiftCampaign,GiftRecipient>> { it.first.draft.availableAtEpochMillis }.thenByDescending { it.first.id })
                    return all.drop(page.offset).take(page.pageSize) to all.size.toLong()
                }
                override fun mailboxAvailableCount(userId: String, now: Long) = next.recipients.values.count { r ->
                    val c = next.campaigns.getValue(r.campaignId)
                    r.userId == userId && r.delivery == "DELIVERED" && r.claimedAt == null &&
                        c.state == CampaignState.PUBLISHED && c.draft.availableAtEpochMillis <= now && now < c.draft.expiresAtEpochMillis!!
                }.toLong()
                override fun balance(userId: String) = players.giftBalance(userId)
                override fun award(userId: String, campaign: GiftCampaign, now: Long) = players.giftAward(userId, campaign, now)
            }
            tx.block()
        }
        state = next; result
    }
}
/** No-DB runtime has no real game wallet. Fail explicitly rather than granting disconnected balances. */
object UnavailableGiftRepository : GiftRepository {
    override suspend fun <T> transaction(admin: Boolean, block: GiftTransaction.() -> T): T = unavailable()
}
