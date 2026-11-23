package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.gifts.*
import com.hienthai.fastowin.server.PlayerProgression
import com.hienthai.fastowin.server.avatars.StoredAvatarFrame
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        val announcementPush: MutableMap<String, Pair<String, String>> = linkedMapOf(),
        var playerStorageAvailable: Boolean = false,
        val mfa: MutableMap<String, AdminMfaRecord> = linkedMapOf(),
        /** adminId to (code hash to used). */
        val recoveryCodes: MutableMap<String, Map<String, Boolean>> = linkedMapOf(),
        val mfaChallenges: MutableMap<String, AdminMfaChallenge> = linkedMapOf(),
        val approvals: MutableMap<Triple<String, String, String>, PublishApproval> = linkedMapOf(),
        val frames: MutableMap<String, StoredAvatarFrame> = linkedMapOf(),
        val framePngs: MutableMap<String, ByteArray> = linkedMapOf()
    ) {
        fun snapshot() = State(accounts.toMutableMap(), sessions.toMutableMap(), requests.toMutableMap(), audits.toMutableList(),
            users.toMutableMap(), matches.toMutableMap(), wallet.toMutableMap(), announcementPush.toMutableMap(), playerStorageAvailable,
            mfa.toMutableMap(), recoveryCodes.toMutableMap(), mfaChallenges.toMutableMap(), approvals.toMutableMap(),
            frames.toMutableMap(), framePngs.toMutableMap())
    }
    suspend fun seedUser(user: UserDetail, matches: List<MatchEntry> = emptyList(), wallet: List<WalletEntry> = emptyList()) = mutex.withLock {
        state.playerStorageAvailable = true; state.users[user.id] = user; state.matches[user.id] = matches; state.wallet[user.id] = wallet
    }
    suspend fun seedAnnouncementPushTarget(userId: String, token: String, language: String = "en") = mutex.withLock {
        state.announcementPush[userId] = language to token
    }
    override suspend fun <T> transaction(block: AdminTransaction.() -> T): T = coordinatedTransaction({}, block)

    /** Caller may publish other copied in-memory state while this mutex is still held. */
    internal suspend fun <T> coordinatedTransaction(publish: () -> Unit, block: AdminTransaction.() -> T): T {
        val job = currentCoroutineContext()[Job]
        return mutex.withLock {
        val next = state.snapshot()
        val tx = object : AdminTransaction, GiftMemoryPlayers, com.hienthai.fastowin.server.announcements.AnnouncementMemoryPushTargets {
            override fun avatarFrames() = next.frames.values.sortedBy(StoredAvatarFrame::id)
            override fun framePng(id: String) = next.framePngs[id]?.copyOf()
            override fun saveAvatarFrame(frame: StoredAvatarFrame, png: ByteArray?) {
                next.frames[frame.id] = frame
                if (png != null) next.framePngs[frame.id] = png.copyOf()
            }
            override fun announcementPushTarget(userId: String): Pair<String, String>? = next.announcementPush[userId]
            override fun giftPlayers(): List<GiftPlayer> {
                available()
                return next.users.values.filter { it.accountType == "REGISTERED" && it.status != "DELETED" }.map { GiftPlayer(it.id,it.createdAtEpochMillis) }
            }
            override fun giftBalance(id: String): GiftBalance {
                available(); val user = next.users[id] ?: missing(); return GiftBalance(user.gold,user.xp,user.gems)
            }
            override fun giftAward(id: String, campaign: GiftCampaign, now: Long): GiftBalance {
                val user = next.users[id] ?: missing()
                val balance = try { GiftBalance(Math.addExact(user.gold,campaign.draft.gold),Math.addExact(user.xp,campaign.draft.xp),Math.addExact(user.gems,campaign.draft.gems)) } catch (_: ArithmeticException) { conflict() }
                val history = next.wallet[id].orEmpty()
                if (history.any { it.sourceType == campaign.walletSourceType() && it.sourceId == campaign.id }) conflict()
                next.users[id] = user.copy(gold=balance.gold,xp=balance.xp,gems=balance.gems,level=PlayerProgression.levelForExperience(balance.xp))
                next.wallet[id] = history + WalletEntry(UUID.randomUUID().toString(),campaign.walletSourceType(),campaign.id,campaign.draft.gold,campaign.draft.gems,campaign.draft.xp,now,campaign.draft.plays)
                return balance
            }
            private fun withMfa(account: AdminAccount) =
                account.copy(identity = account.identity.copy(mfaEnabled = next.mfa[account.identity.id]?.enabled == true))
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
            override fun putPassword(adminId: String, passwordHash: String) {
                next.accounts[adminId]?.let { next.accounts[adminId] = it.copy(passwordHash = passwordHash) }
            }
            override fun mfa(adminId: String) = next.mfa[adminId]
            override fun putMfa(record: AdminMfaRecord) { next.mfa[record.adminId] = record }
            override fun deleteMfa(adminId: String) {
                next.mfa.remove(adminId); next.recoveryCodes.remove(adminId)
                next.mfaChallenges.values.removeAll { it.adminId == adminId }
            }
            override fun replaceRecoveryCodes(adminId: String, hashes: List<String>) { next.recoveryCodes[adminId] = hashes.associateWith { false } }
            override fun useRecoveryCode(adminId: String, hash: String, now: Long): Boolean {
                val codes = next.recoveryCodes[adminId] ?: return false
                if (codes[hash] != false) return false
                next.recoveryCodes[adminId] = codes + (hash to true); return true
            }
            override fun remainingRecoveryCodes(adminId: String) = next.recoveryCodes[adminId].orEmpty().count { !it.value }
            override fun mfaChallenge(tokenHash: String) = next.mfaChallenges[tokenHash]
            override fun putMfaChallenge(challenge: AdminMfaChallenge) { next.mfaChallenges[challenge.tokenHash] = challenge }
            override fun deleteMfaChallenge(tokenHash: String) { next.mfaChallenges.remove(tokenHash) }
            override fun publishApproval(kind: String, targetId: String, versionKey: String) = next.approvals[Triple(kind, targetId, versionKey)]
            override fun putPublishApproval(approval: PublishApproval) {
                next.approvals.putIfAbsent(Triple(approval.kind, approval.targetId, approval.versionKey), approval)
            }
            override fun deletePublishApprovals(kind: String, targetId: String) {
                next.approvals.keys.removeAll { it.first == kind && it.second == targetId }
            }
            override fun deduplication(actorId: String, requestId: String) = next.requests[actorId to requestId]
            override fun putDeduplication(actorId: String, requestId: String, value: AdminDeduplication) { next.requests[actorId to requestId] = value }
            override fun appendAudit(entry: AuditEntry) { next.audits.add(entry) }
            override fun accounts(page: PageRequest) = paginate(next.accounts.values.map { withMfa(it).identity }.sortedBy { it.id }, page)
            override fun audit(page: PageRequest) = paginate(next.audits.sortedWith(compareByDescending<AuditEntry> { it.createdAtEpochMillis }.thenByDescending { it.id }), page)
            fun available() { if (!next.playerStorageAvailable) unavailable() }
            // This dev repository has no shared authentication store or receipt ledger.
            override fun playerCommerce(from: Long, until: Long) = PlayerCommerceOverview(from,until,null,null,null,"UNAVAILABLE")
            override fun playerCreation(actorId: String, requestId: String): PlayerCreationReceipt? = null
            override fun putPlayerCreation(actorId: String, requestId: String, receipt: PlayerCreationReceipt) { unavailable() }
            override fun playerPasswordHash(id: String): String? = null
            override fun createSupportPlayer(id: String, email: String, displayName: String, passwordHash: String, now: Long): UserDetail = unavailable()
            override fun playerOverview(todayFrom: Long, todayUntil: Long): PlayerOverviewStats {
                available()
                val users = next.users.values
                fun total(value: (UserDetail) -> Int?): String? {
                    if (users.any { value(it) == null }) return null
                    return users.fold(java.math.BigInteger.ZERO) { sum, user -> sum + java.math.BigInteger.valueOf(value(user)!!.toLong()) }.toString()
                }
                // Seeded per-user history cannot establish global match/clan totals or complete lifetime results.
                return PlayerOverviewStats(users.size.toString(),users.count { it.accountType == "REGISTERED" }.toString(),
                    users.count { it.accountType == "GUEST" }.toString(),users.count { it.status == "ACTIVE" }.toString(),
                    null,total { it.totalMatches },total { it.wins },total { it.losses },total { it.draws },
                    if (users.all { it.totalMatches != null }) users.count { it.totalMatches!! > 0 }.toString() else null,
                    null,users.count { it.clanId != null }.toString(),
                    if (users.isEmpty()) null else users.fold(java.math.BigDecimal.ZERO) { sum, user -> sum + user.elo.toBigDecimal() }
                        .divide(users.size.toBigDecimal(),1,java.math.RoundingMode.HALF_UP).toPlainString(),users.maxOfOrNull { it.elo })
            }
            override fun users(query: String, includeEmail: Boolean, page: PageRequest): AdminPage<UserSummary> {
                available()
                val items = next.users.values.filter { query.isEmpty() || it.id == query || it.playerCode.equals(query, true) || it.displayName.contains(query, true) || (includeEmail && it.email?.contains(query, true) == true) }
                    .sortedWith(compareByDescending<UserDetail> { it.createdAtEpochMillis }.thenBy { it.id }).map { it.summary() }
                return paginate(items, page)
            }
            override fun user(id: String): UserDetail? { available(); return next.users[id] }
            override fun giftRecipient(reference: String): UserSummary? {
                available()
                return next.users.values.filter { it.accountType == "REGISTERED" && it.status != "DELETED" &&
                    (it.playerCode.equals(reference, true) || it.email?.equals(reference, true) == true) }.singleOrNull()?.summary()
            }
            override fun matches(id: String, page: PageRequest): AdminPage<MatchEntry> {
                available(); return paginate(next.matches[id].orEmpty().sortedWith(compareByDescending<MatchEntry> { it.finishedAtEpochMillis }.thenBy { it.id }), page)
            }
            override fun wallet(id: String, giftsOnly: Boolean, page: PageRequest): AdminPage<WalletEntry> {
                available(); return paginate(next.wallet[id].orEmpty().filter { !giftsOnly || it.sourceType in setOf("ADMIN_GIFT", "COMPENSATION") }
                    .sortedWith(compareByDescending<WalletEntry> { it.createdAtEpochMillis }.thenBy { it.id }), page)
            }
        }
        val result = tx.block()
        job?.ensureActive()
        publish()
        state = next
        result
        }
    }
}
private fun <T> paginate(items: List<T>, page: PageRequest) = page.result(items.drop(page.offset).take(page.pageSize), items.size.toLong())
