package com.hienthai.fastowin.server.admin

/** Each block is atomic. PostgreSQL uses one connection/transaction and serializes access changes. */
interface AdminRepository {
    suspend fun <T> transaction(block: AdminTransaction.() -> T): T

    /**
     * For blocks that never change admin access state (lookups, audit appends are fine).
     * They may run concurrently with each other and with player registration, but still
     * wait for access changes made through [transaction].
     */
    suspend fun <T> readTransaction(block: AdminTransaction.() -> T): T = transaction(block)
}
interface AdminTransaction {
    fun accountByEmail(email: String): AdminAccount?
    fun account(id: String): AdminAccount?
    fun putAccount(account: AdminAccount)
    fun activeOwners(): Int
    fun session(tokenHash: String): AdminSession?
    fun putSession(session: AdminSession)
    fun revokeSessions(adminId: String)
    fun putPassword(adminId: String, passwordHash: String)
    fun mfa(adminId: String): AdminMfaRecord?
    fun putMfa(record: AdminMfaRecord)
    /** Removes the second factor, its recovery codes and pending challenges. */
    fun deleteMfa(adminId: String)
    fun replaceRecoveryCodes(adminId: String, hashes: List<String>)
    /** Marks an unused code as used; false when it is unknown or already used. */
    fun useRecoveryCode(adminId: String, hash: String, now: Long): Boolean
    fun remainingRecoveryCodes(adminId: String): Int
    fun mfaChallenge(tokenHash: String): AdminMfaChallenge?
    fun putMfaChallenge(challenge: AdminMfaChallenge)
    fun deleteMfaChallenge(tokenHash: String)
    fun publishApproval(kind: String, targetId: String, versionKey: String): PublishApproval?
    fun putPublishApproval(approval: PublishApproval)
    /** Removes every pending version for the target, so a completed or changed draft starts over. */
    fun deletePublishApprovals(kind: String, targetId: String)
    fun deduplication(actorId: String, requestId: String): AdminDeduplication?
    fun putDeduplication(actorId: String, requestId: String, value: AdminDeduplication)
    fun appendAudit(entry: AuditEntry)
    fun accounts(page: PageRequest): AdminPage<AdminIdentity>
    fun audit(page: PageRequest): AdminPage<AuditEntry>
    fun users(query: String, includeEmail: Boolean, page: PageRequest): AdminPage<UserSummary>
    fun playerOverview(todayFrom: Long, todayUntil: Long): PlayerOverviewStats
    fun playerCommerce(from: Long, until: Long): PlayerCommerceOverview
    fun playerCreation(actorId: String, requestId: String): PlayerCreationReceipt?
    fun putPlayerCreation(actorId: String, requestId: String, receipt: PlayerCreationReceipt)
    fun playerPasswordHash(id: String): String?
    fun createSupportPlayer(id: String, email: String, displayName: String, passwordHash: String, now: Long): UserDetail
    fun user(id: String): UserDetail?
    fun giftRecipient(reference: String): UserSummary?
    fun matches(id: String, page: PageRequest): AdminPage<MatchEntry>
    fun wallet(id: String, giftsOnly: Boolean, page: PageRequest): AdminPage<WalletEntry>
}
