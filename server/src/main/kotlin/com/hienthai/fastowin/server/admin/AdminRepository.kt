package com.hienthai.fastowin.server.admin

/** Each block is atomic. PostgreSQL uses one connection/transaction and serializes access changes. */
interface AdminRepository {
    suspend fun <T> transaction(block: AdminTransaction.() -> T): T
}
interface AdminTransaction {
    fun accountByEmail(email: String): AdminAccount?
    fun account(id: String): AdminAccount?
    fun putAccount(account: AdminAccount)
    fun activeOwners(): Int
    fun session(tokenHash: String): AdminSession?
    fun putSession(session: AdminSession)
    fun revokeSessions(adminId: String)
    fun deduplication(actorId: String, requestId: String): AdminDeduplication?
    fun putDeduplication(actorId: String, requestId: String, value: AdminDeduplication)
    fun appendAudit(entry: AuditEntry)
    fun accounts(page: PageRequest): AdminPage<AdminIdentity>
    fun audit(page: PageRequest): AdminPage<AuditEntry>
    fun users(query: String, includeEmail: Boolean, page: PageRequest): AdminPage<UserSummary>
    fun user(id: String): UserDetail?
    fun matches(id: String, page: PageRequest): AdminPage<MatchEntry>
    fun wallet(id: String, giftsOnly: Boolean, page: PageRequest): AdminPage<WalletEntry>
}
