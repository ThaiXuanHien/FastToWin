package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.RateLimiter
import com.hienthai.fastowin.server.InMemoryRateLimiter
import com.hienthai.fastowin.server.RateLimitPolicy
import com.hienthai.fastowin.server.hashToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class AdminService(
    val repository: AdminRepository,
    val configuration: AdminConfiguration,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val rateLimiter: RateLimiter = InMemoryRateLimiter()
) {
    private val random = SecureRandom()
    private val dummyHash by lazy { passwordHasher.hash(randomToken()) }
    private fun randomToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun csrf(token: String) = hashToken("admin-csrf:$token")
    fun requireOrigin(origin: String?) { if (origin != configuration.origin) forbidden() }

    suspend fun login(email: String, password: String, origin: String?, address: String): AdminLogin {
        requireOrigin(origin)
        val normalized = normalizeEmail(email)
        validatePassword(password, provision = false)
        if (!rateLimiter.consume("admin-login-ip", hashToken(address), RateLimitPolicy(20, 60_000)).allowed ||
            !rateLimiter.consume("admin-login-account", hashToken(normalized), RateLimitPolicy(8, 300_000)).allowed) {
            throw AdminException(429, "RATE_LIMITED", "Vui lòng thử lại sau.")
        }
        val candidate = repository.transaction { accountByEmail(normalized) }
        val valid = withContext(Dispatchers.Default) { passwordHasher.verify(password, candidate?.passwordHash ?: dummyHash) }
        if (!valid || candidate == null) unauthorized()
        val token = randomToken()
        return repository.transaction {
            val current = account(candidate.identity.id)
            if (current == null || !current.identity.active || current.passwordHash != candidate.passwordHash) unauthorized()
            putSession(AdminSession(hashToken(token), current.identity.id, hashToken(csrf(token)), nowMillis() + SESSION_MILLIS))
            auditEntry(current.identity.id, "ADMIN_LOGIN")
            AdminLogin(token, AdminSessionResponse(current.identity, csrf(token)))
        }
    }

    suspend fun authenticate(token: String?): AdminIdentity? = try {
        repository.transaction { principal(token).second.identity }
    } catch (error: AdminException) { if (error.status == 401) null else throw error }

    suspend fun session(token: String?): AdminSessionResponse = repository.transaction {
        val (_, account) = principal(token)
        auditEntry(account.identity.id, "ADMIN_SESSION")
        AdminSessionResponse(account.identity, csrf(token!!))
    }

    suspend fun logout(token: String?, csrfToken: String?, origin: String?) {
        requireOrigin(origin)
        repository.transaction {
            val (session, account) = principal(token, csrfToken, mutation = true)
            putSession(session.copy(revoked = true))
            auditEntry(account.identity.id, "ADMIN_LOGOUT")
        }
    }

    suspend fun bootstrapOwner(email: String, password: String): AdminIdentity {
        val normalized = normalizeEmail(email); validatePassword(password, provision = true)
        val hash = withContext(Dispatchers.Default) { passwordHasher.hash(password) }
        return repository.transaction {
            val existing = accountByEmail(normalized)
            if (existing?.identity?.let { it.active && it.role == AdminRole.OWNER } == true) return@transaction existing.identity
            if (activeOwners() != 0 || existing != null) conflict()
            val identity = AdminIdentity(UUID.randomUUID().toString(), normalized, AdminRole.OWNER)
            putAccount(AdminAccount(identity, hash))
            auditEntry(null, "ADMIN_BOOTSTRAP", identity.id, after = accessSummary(identity))
            identity
        }
    }

    suspend fun createAccount(token: String?, csrfToken: String?, origin: String?, request: CreateAdminRequest): AdminIdentity {
        requireOrigin(origin); normalizeEmail(request.email); validatePassword(request.password, true); validateMutation(request.reason, request.requestId)
        // Reject unprivileged callers before expensive hashing; the transaction rechecks after hashing.
        repository.transaction { principal(token, csrfToken, true, ownerOnly = true) }
        val hash = withContext(Dispatchers.Default) { passwordHasher.hash(request.password) }
        val normalized = normalizeEmail(request.email)
        val fingerprint = fingerprint("create", normalized, hashToken(request.password), request.role.name, request.reason)
        return repository.transaction {
            val actor = principal(token, csrfToken, true, true).second.identity
            replay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
            if (accountByEmail(normalized) != null) conflict()
            val identity = AdminIdentity(UUID.randomUUID().toString(), normalized, request.role)
            putAccount(AdminAccount(identity, hash))
            auditEntry(actor.id, "ADMIN_CREATE", identity.id, request.requestId, request.reason, after = accessSummary(identity))
            putDeduplication(actor.id, request.requestId, AdminDeduplication(fingerprint, identity))
            identity
        }
    }

    suspend fun changeAccess(token: String?, csrfToken: String?, origin: String?, id: String, request: ChangeAdminAccessRequest): AdminIdentity {
        requireOrigin(origin); uuid(id); validateMutation(request.reason, request.requestId)
        val fingerprint = fingerprint("access", id, request.role.name, request.active.toString(), request.reason)
        return repository.transaction {
            val actor = principal(token, csrfToken, true, true).second.identity
            replay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
            val existing = account(id) ?: missing()
            if (existing.identity.active && existing.identity.role == AdminRole.OWNER &&
                (!request.active || request.role != AdminRole.OWNER) && activeOwners() <= 1) conflict()
            val identity = existing.identity.copy(role = request.role, active = request.active)
            putAccount(existing.copy(identity = identity)); revokeSessions(id)
            auditEntry(actor.id, "ADMIN_ACCESS", id, request.requestId, request.reason, accessSummary(existing.identity), accessSummary(identity))
            putDeduplication(actor.id, request.requestId, AdminDeduplication(fingerprint, identity))
            identity
        }
    }

    suspend fun users(token: String?, query: String, page: PageRequest): AdminPage<UserSummary> = repository.transaction {
        val actor = principal(token).second.identity
        val q = query.trim(); if (q.length > 128 || q.any(Char::isISOControl)) invalid()
        if (actor.role == AdminRole.SUPPORT && '@' in q) forbidden()
        val result = users(q, actor.role != AdminRole.SUPPORT, page)
        auditEntry(actor.id, "USER_SEARCH") // Never persist the query or player email.
        result.copy(items = result.items.map { if (actor.role == AdminRole.SUPPORT) it.copy(email = maskEmail(it.email)) else it })
    }
    suspend fun user(token: String?, id: String): UserDetail = repository.transaction {
        val actor = principal(token).second.identity; uuid(id)
        val result = user(id) ?: missing(); auditEntry(actor.id, "USER_DETAIL", id)
        if (actor.role == AdminRole.SUPPORT) result.copy(email = maskEmail(result.email)) else result
    }
    suspend fun matches(token: String?, id: String, page: PageRequest): AdminPage<MatchEntry> = repository.transaction {
        val actor = principal(token).second.identity; uuid(id); user(id) ?: missing()
        val result = matches(id, page); auditEntry(actor.id, "USER_MATCHES", id); result
    }
    suspend fun wallet(token: String?, id: String, giftsOnly: Boolean, page: PageRequest): AdminPage<WalletEntry> = repository.transaction {
        val actor = principal(token).second.identity; uuid(id); user(id) ?: missing()
        val result = wallet(id, giftsOnly, page); auditEntry(actor.id, if (giftsOnly) "USER_GIFTS" else "USER_WALLET", id); result
    }
    suspend fun accounts(token: String?, page: PageRequest): AdminPage<AdminIdentity> = repository.transaction {
        val actor = principal(token, ownerOnly = true).second.identity
        val result = accounts(page); auditEntry(actor.id, "ADMIN_LIST"); result
    }
    suspend fun audit(token: String?, page: PageRequest): AdminPage<AuditEntry> = repository.transaction {
        val actor = principal(token, ownerOnly = true).second.identity
        val result = audit(page); auditEntry(actor.id, "AUDIT_READ"); result
    }
    private fun AdminTransaction.principal(token: String?, csrfToken: String? = null, mutation: Boolean = false, ownerOnly: Boolean = false): Pair<AdminSession, AdminAccount> {
        if (token == null || !token.matches(Regex("[A-Za-z0-9_-]{43}"))) unauthorized()
        val session = session(hashToken(token)) ?: unauthorized()
        if (session.revoked || session.expiresAtEpochMillis <= nowMillis()) unauthorized()
        val account = account(session.adminId)?.takeIf { it.identity.active } ?: unauthorized()
        if (mutation && (csrfToken == null || csrfToken.length != 64 || !MessageDigest.isEqual(hashToken(csrfToken).toByteArray(), session.csrfHash.toByteArray()))) forbidden()
        if (ownerOnly && account.identity.role != AdminRole.OWNER) forbidden()
        return session to account
    }
    private fun AdminTransaction.replay(actorId: String, requestId: String, fingerprint: String): AdminIdentity? {
        val previous = deduplication(actorId, requestId) ?: return null
        if (previous.fingerprint != fingerprint) conflict()
        return previous.identity
    }
    private fun AdminTransaction.auditEntry(actor: String?, action: String, target: String? = null, requestId: String? = null,
        reason: String? = null, before: String? = null, after: String? = null) =
        appendAudit(AuditEntry(UUID.randomUUID().toString(), actor, action, target, requestId, reason, before, after, nowMillis()))
    private fun accessSummary(identity: AdminIdentity) = "role=${identity.role};active=${identity.active}"
    private fun fingerprint(vararg values: String) = hashToken(values.joinToString("") { "${it.length}:$it" })
    private fun validateMutation(reason: String, requestId: String) {
        if (reason.isBlank() || reason.length > 500 || reason.any(Char::isISOControl) || !requestId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid()
    }
    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase(java.util.Locale.ROOT)
        if (normalized.length > 254 || !normalized.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) invalid()
        return normalized
    }
    private fun validatePassword(password: String, provision: Boolean) {
        if (password.length !in (if (provision) 12 else 1)..128) invalid()
    }
    private fun uuid(id: String) { if (runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false).not()) invalid() }
    companion object { const val SESSION_MILLIS = 8 * 60 * 60 * 1000L }
}
internal fun maskEmail(email: String?): String? {
    if (email == null) return null
    val local = email.substringBefore('@'); val domain = email.substringAfter('@')
    val suffix = domain.substringAfterLast('.', "")
    return "${local.take(1)}***@${domain.take(1)}***${if (suffix.isEmpty()) "" else ".$suffix"}"
}
