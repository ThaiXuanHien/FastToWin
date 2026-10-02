package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.gifts.*
import com.hienthai.fastowin.server.PlayerProgression
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Dev security storage. Player storage is unavailable until explicitly populated by a test fixture. */
class InMemoryAdminRepository : AdminRepository {
    private val mutex = Mutex()
    private var state = State()
    private data class State(
        val accounts: MutableMap<String, AdminAccount> = linkedMapOf(),
        val sessions: MutableMap<String, AdminSession> = linkedMapOf(),
        val requests: MutableMap<Pair<String, String>, AdminDeduplication> = linkedMapOf(),
        val audits: MutableList<AuditEntry> = mutableListOf(),
        val users: MutableMap<String, UserDetail> = linkedMapOf(),
        val matches: MutableMap<String, List<MatchEntry>> = linkedMapOf(),
        val wallet: MutableMap<String, List<WalletEntry>> = linkedMapOf(),
        var playerStorageAvailable: Boolean = false
    ) {
        fun snapshot() = State(accounts.toMutableMap(), sessions.toMutableMap(), requests.toMutableMap(), audits.toMutableList(),
            users.toMutableMap(), matches.toMutableMap(), wallet.toMutableMap(), playerStorageAvailable)
    }
    suspend fun seedUser(user: UserDetail, matches: List<MatchEntry> = emptyList(), wallet: List<WalletEntry> = emptyList()) = mutex.withLock {
        state.playerStorageAvailable = true; state.users[user.id] = user; state.matches[user.id] = matches; state.wallet[user.id] = wallet
    }
    override suspend fun <T> transaction(block: AdminTransaction.() -> T): T = mutex.withLock {
        val next = state.snapshot()
        val tx = object : AdminTransaction, GiftMemoryPlayers {
            override fun giftPlayers(): List<GiftPlayer> {
                available()
                return next.users.values.filter { it.accountType == "REGISTERED" && it.status != "DELETED" }.map { GiftPlayer(it.id,it.createdAtEpochMillis) }
            }
            override fun giftBalance(id: String): GiftBalance {
                available(); val user = next.users[id] ?: missing(); return GiftBalance(user.gold,user.xp)
            }
            override fun giftAward(id: String, campaign: GiftCampaign, now: Long): GiftBalance {
                val user = next.users[id] ?: missing()
                val balance = try { GiftBalance(Math.addExact(user.gold,campaign.draft.gold),Math.addExact(user.xp,campaign.draft.xp)) } catch (_: ArithmeticException) { conflict() }
                val history = next.wallet[id].orEmpty()
                if (history.any { it.sourceType == "ADMIN_GIFT" && it.sourceId == campaign.id }) conflict()
                next.users[id] = user.copy(gold=balance.gold,xp=balance.xp,level=PlayerProgression.levelForExperience(balance.xp))
                next.wallet[id] = history + WalletEntry(UUID.randomUUID().toString(),"ADMIN_GIFT",campaign.id,campaign.draft.gold,0,campaign.draft.xp,now)
                return balance
            }
            override fun accountByEmail(email: String) = next.accounts.values.firstOrNull { it.identity.email == email }
            override fun account(id: String) = next.accounts[id]
            override fun putAccount(account: AdminAccount) {
                if (accountByEmail(account.identity.email)?.identity?.id?.let { it != account.identity.id } == true) conflict()
                next.accounts[account.identity.id] = account
            }
            override fun activeOwners() = next.accounts.values.count { it.identity.active && it.identity.role == AdminRole.OWNER }
            override fun session(tokenHash: String) = next.sessions[tokenHash]
            override fun putSession(session: AdminSession) { next.sessions[session.tokenHash] = session }
            override fun revokeSessions(adminId: String) { next.sessions.replaceAll { _, session -> if (session.adminId == adminId) session.copy(revoked = true) else session } }
            override fun deduplication(actorId: String, requestId: String) = next.requests[actorId to requestId]
            override fun putDeduplication(actorId: String, requestId: String, value: AdminDeduplication) { next.requests[actorId to requestId] = value }
            override fun appendAudit(entry: AuditEntry) { next.audits.add(entry) }
            override fun accounts(page: PageRequest) = paginate(next.accounts.values.map { it.identity }.sortedBy { it.id }, page)
            override fun audit(page: PageRequest) = paginate(next.audits.sortedWith(compareByDescending<AuditEntry> { it.createdAtEpochMillis }.thenByDescending { it.id }), page)
            fun available() { if (!next.playerStorageAvailable) unavailable() }
            override fun users(query: String, includeEmail: Boolean, page: PageRequest): AdminPage<UserSummary> {
                available()
                val items = next.users.values.filter { query.isEmpty() || it.id == query || it.displayName.contains(query, true) || (includeEmail && it.email?.contains(query, true) == true) }
                    .sortedWith(compareByDescending<UserDetail> { it.createdAtEpochMillis }.thenBy { it.id }).map { it.summary() }
                return paginate(items, page)
            }
            override fun user(id: String): UserDetail? { available(); return next.users[id] }
            override fun matches(id: String, page: PageRequest): AdminPage<MatchEntry> {
                available(); return paginate(next.matches[id].orEmpty().sortedWith(compareByDescending<MatchEntry> { it.finishedAtEpochMillis }.thenBy { it.id }), page)
            }
            override fun wallet(id: String, giftsOnly: Boolean, page: PageRequest): AdminPage<WalletEntry> {
                available(); return paginate(next.wallet[id].orEmpty().filter { !giftsOnly || it.sourceType in setOf("ADMIN_GIFT", "COMPENSATION") }
                    .sortedWith(compareByDescending<WalletEntry> { it.createdAtEpochMillis }.thenBy { it.id }), page)
            }
        }
        val result = tx.block()
        state = next
        result
    }
}
private fun <T> paginate(items: List<T>, page: PageRequest) = page.result(items.drop(page.offset).take(page.pageSize), items.size.toLong())
