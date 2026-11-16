package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.RateLimiter
import com.hienthai.fastowin.server.InMemoryRateLimiter
import com.hienthai.fastowin.server.RateLimitPolicy
import com.hienthai.fastowin.server.RateLimitUnavailableException
import com.hienthai.fastowin.server.hashToken
import com.hienthai.fastowin.server.legacyFallback
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
    private val rateLimiter: RateLimiter = InMemoryRateLimiter(),
    val alerts: AdminAlertSink = AdminAlertSink.NONE,
    val publishLimits: AdminPublishLimits = AdminPublishLimits()
) {
    private val random = SecureRandom()
    private val failedLogins = FailedLoginTracker(nowMillis)

    /** ADM-11: called only after the transaction that caused the event has committed. */
    internal fun alert(title: String, vararg details: Pair<String, String?>) {
        alerts.alert(AdminAlert(title, details.mapNotNull { (k, v) -> v?.let { k to it } }, nowMillis()))
    }

    private fun failedSignIn(email: String, address: String?) {
        failedLogins.record(email)?.let { count ->
            alert("Nhiều lần đăng nhập quản trị thất bại", "Email" to email, "Số lần trong 15 phút" to count.toString(), "IP" to address)
        }
    }
    private val dummyHash by lazy { passwordHasher.hash(randomToken()) }
    private fun randomToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun csrf(token: String) = hashToken("admin-csrf:$token")
    fun requireOrigin(origin: String?) { if (origin != configuration.origin) forbidden() }

    /** Password-only login for accounts without a second factor; enrolled accounts must use [beginLogin]. */
    suspend fun login(email: String, password: String, origin: String?, address: String): AdminLogin =
        when (val outcome = beginLogin(email, password, origin, address)) {
            is AdminLoginOutcome.Session -> outcome.login
            is AdminLoginOutcome.Challenge -> throw AdminException(401, "MFA_REQUIRED", legacyFallback("Cần mã xác thực hai lớp."))
        }

    /**
     * Verifies the password. Accounts with TOTP (or every account while MFA is required)
     * receive a short-lived challenge instead of a session (ADM-04).
     */
    suspend fun beginLogin(email: String, password: String, origin: String?, address: String): AdminLoginOutcome {
        requireOrigin(origin)
        val normalized = normalizeEmail(email)
        validatePassword(password, provision = false)
        val allowed = try {
            rateLimiter.consume("admin-login-ip", hashToken(address), RateLimitPolicy(20, 60_000)).allowed &&
                rateLimiter.consume("admin-login-account", hashToken(normalized), RateLimitPolicy(8, 300_000)).allowed
        } catch (_: RateLimitUnavailableException) {
            throw AdminException(503, "RATE_LIMIT_UNAVAILABLE", legacyFallback("Dịch vụ tạm thời không khả dụng."))
        }
        if (!allowed) {
            throw AdminException(429, "RATE_LIMITED", legacyFallback("Vui lòng thử lại sau."))
        }
        val candidate = repository.readTransaction { accountByEmail(normalized) }
        val valid = withContext(Dispatchers.Default) { passwordHasher.verify(password, candidate?.passwordHash ?: dummyHash) }
        if (!valid || candidate == null) { failedSignIn(normalized, address); unauthorized() }
        val token = randomToken()
        val outcome = repository.transaction<AdminLoginOutcome> {
            val current = account(candidate.identity.id)
            if (current == null || !current.identity.active || current.passwordHash != candidate.passwordHash) unauthorized()
            val mfa = mfa(current.identity.id)
            if (mfa?.enabled != true && !configuration.mfa.required) {
                return@transaction AdminLoginOutcome.Session(openSession(current, token))
            }
            val challenge = AdminMfaChallenge(hashToken(token), current.identity.id, enrollment = mfa?.enabled != true, attempts = 0,
                expiresAtEpochMillis = nowMillis() + MFA_CHALLENGE_MILLIS)
            putMfaChallenge(challenge)
            if (!challenge.enrollment) return@transaction AdminLoginOutcome.Challenge(AdminMfaChallengeResponse("VERIFY", token))
            // Enrollment keeps a pending secret until the first valid code proves the authenticator has it.
            val cipher = configuration.mfa.cipher
            val secret = mfa?.let { cipher.decrypt(it.secretCiphertext, it.adminId) } ?: Totp.newSecret().also {
                putMfa(AdminMfaRecord(current.identity.id, cipher.encrypt(it, current.identity.id), enabled = false, lastUsedStep = 0))
            }
            AdminLoginOutcome.Challenge(AdminMfaChallengeResponse("ENROLL", token, Base32.encode(secret),
                Totp.otpauthUri(current.identity.email, secret)))
        }
        if (outcome is AdminLoginOutcome.Session) signedIn(outcome.login.response.admin, address, "Mật khẩu")
        return outcome
    }

    private fun signedIn(admin: AdminIdentity, address: String?, method: String) =
        alert("Đăng nhập quản trị", "Email" to admin.email, "Vai trò" to admin.role.name, "Cách xác thực" to method, "IP" to address)

    /** Completes a challenge with a TOTP code or, for enrolled accounts, a single-use recovery code. */
    suspend fun completeMfa(request: AdminMfaVerifyRequest, origin: String?, address: String? = null): AdminMfaLogin {
        requireOrigin(origin)
        if (!request.challengeToken.matches(Regex("[A-Za-z0-9_-]{43}"))) unauthorized()
        if ((request.code == null) == (request.recoveryCode == null)) invalid()
        if ((request.code?.length ?: 0) > 16 || (request.recoveryCode?.length ?: 0) > 32) invalid()
        val token = randomToken()
        var failedEmail: String? = null
        // Failed attempts must persist, so failures are returned from the transaction and thrown after commit.
        val result = repository.transaction<Result<AdminMfaLogin>> {
            val tokenHash = hashToken(request.challengeToken)
            val challenge = mfaChallenge(tokenHash) ?: return@transaction Result.failure(mfaFailure("MFA_CHALLENGE_EXPIRED"))
            val now = nowMillis()
            if (challenge.expiresAtEpochMillis <= now) {
                deleteMfaChallenge(tokenHash); return@transaction Result.failure(mfaFailure("MFA_CHALLENGE_EXPIRED"))
            }
            val account = account(challenge.adminId)?.takeIf { it.identity.active }
            val record = mfa(challenge.adminId)
            if (account == null || record == null || record.enabled == challenge.enrollment) {
                deleteMfaChallenge(tokenHash); return@transaction Result.failure(mfaFailure("MFA_CHALLENGE_EXPIRED"))
            }
            val recovery = request.recoveryCode
            val verified = if (recovery != null) {
                !challenge.enrollment && useRecoveryCode(account.identity.id, RecoveryCodes.hash(recovery), now)
            } else {
                val secret = configuration.mfa.cipher.decrypt(record.secretCiphertext, record.adminId)
                Totp.verify(secret, request.code.orEmpty(), now, record.lastUsedStep)?.also { step ->
                    putMfa(record.copy(enabled = true, lastUsedStep = step))
                } != null
            }
            if (!verified) {
                val attempts = challenge.attempts + 1
                if (attempts >= MFA_MAX_ATTEMPTS) deleteMfaChallenge(tokenHash) else putMfaChallenge(challenge.copy(attempts = attempts))
                auditEntry(account.identity.id, "ADMIN_MFA_FAILED")
                failedEmail = account.identity.email
                return@transaction Result.failure(mfaFailure(if (attempts >= MFA_MAX_ATTEMPTS) "MFA_CHALLENGE_EXPIRED" else "MFA_INVALID_CODE"))
            }
            deleteMfaChallenge(tokenHash)
            val recoveryCodes = if (challenge.enrollment) RecoveryCodes.generate().also { codes ->
                replaceRecoveryCodes(account.identity.id, codes.map(RecoveryCodes::hash))
                auditEntry(account.identity.id, "ADMIN_MFA_ENROLL")
            } else emptyList()
            if (recovery != null) auditEntry(account.identity.id, "ADMIN_MFA_RECOVERY_CODE_USED",
                after = "remaining=${remainingRecoveryCodes(account.identity.id)}")
            // A verified code means the factor is enabled now (enrollment just completed or already on).
            val opened = openSession(account.copy(identity = account.identity.copy(mfaEnabled = true)), token)
            Result.success(AdminMfaLogin(opened.token, AdminMfaSessionResponse(opened.response.admin, opened.response.csrfToken, recoveryCodes)))
        }
        val login = result.getOrElse { failure ->
            failedEmail?.let { failedSignIn(it, address) }
            throw failure
        }
        signedIn(login.response.admin, address, if (request.recoveryCode != null) "Mã khôi phục" else "Mật khẩu + TOTP")
        return login
    }

    /** Owner-only: removes another admin's second factor (lost device) and ends their sessions. */
    suspend fun resetMfa(token: String?, csrfToken: String?, origin: String?, id: String, request: AdminMfaResetRequest): AdminIdentity {
        requireOrigin(origin); uuid(id); validateMutation(request.reason, request.requestId)
        val fingerprint = fingerprint("mfa-reset", id, request.reason)
        return repository.transaction {
            val actor = principal(token, csrfToken, true, true).second.identity
            replay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
            // Owners recover their own factor with recovery codes or the offline tool, never with their session.
            if (actor.id == id) forbidden()
            val existing = account(id) ?: missing()
            deleteMfa(id); revokeSessions(id)
            val identity = existing.identity.copy(mfaEnabled = false)
            auditEntry(actor.id, "ADMIN_MFA_RESET", id, request.requestId, request.reason)
            putDeduplication(actor.id, request.requestId, AdminDeduplication(fingerprint, identity))
            identity
        }.also { alert("Đặt lại xác thực hai lớp", "Tài khoản" to it.email, "Lý do" to request.reason) }
    }

    /**
     * Offline tool only (ADM-10): replaces the password, removes the second factor and ends
     * every session of an existing, active admin. The account must enroll again at next sign-in.
     */
    suspend fun recoverOffline(email: String, newPassword: String, reason: String): AdminIdentity {
        val normalized = normalizeEmail(email); validatePassword(newPassword, provision = true)
        if (reason.isBlank() || reason.length > 500) invalid()
        val hash = withContext(Dispatchers.Default) { passwordHasher.hash(newPassword) }
        return repository.transaction {
            val existing = accountByEmail(normalized)?.takeIf { it.identity.active } ?: missing()
            putPassword(existing.identity.id, hash)
            deleteMfa(existing.identity.id); revokeSessions(existing.identity.id)
            auditEntry(null, "ADMIN_OFFLINE_RECOVERY", existing.identity.id, reason = reason.trim())
            existing.identity.copy(mfaEnabled = false)
        }
    }

    private fun AdminTransaction.openSession(account: AdminAccount, token: String): AdminLogin {
        putSession(AdminSession(hashToken(token), account.identity.id, hashToken(csrf(token)), nowMillis() + SESSION_MILLIS))
        auditEntry(account.identity.id, "ADMIN_LOGIN")
        return AdminLogin(token, AdminSessionResponse(account.identity, csrf(token)))
    }

    private fun mfaFailure(code: String) = AdminException(401, code, legacyFallback(
        if (code == "MFA_INVALID_CODE") "Mã xác thực không đúng." else "Phiên xác thực đã hết hạn. Vui lòng đăng nhập lại."))

    suspend fun authenticate(token: String?): AdminIdentity? = try {
        repository.readTransaction { principal(token).second.identity }
    } catch (error: AdminException) { if (error.status == 401) null else throw error }

    suspend fun session(token: String?): AdminSessionResponse = repository.readTransaction {
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
        // Persist only nonsecret fields. A fast password digest here would weaken the PBKDF2 credential.
        val fingerprint = fingerprint("create", normalized, request.role.name, request.reason)
        return withContext(Dispatchers.Default) {
            repository.transaction {
                val actor = principal(token, csrfToken, true, true).second.identity
                replay(actor.id, request.requestId, fingerprint)?.let { previous ->
                    val createdAccount = account(previous.id) ?: conflict()
                    if (!passwordHasher.verify(request.password, createdAccount.passwordHash)) conflict()
                    return@transaction previous
                }
                if (accountByEmail(normalized) != null) conflict()
                val identity = AdminIdentity(UUID.randomUUID().toString(), normalized, request.role)
                putAccount(AdminAccount(identity, hash))
                auditEntry(actor.id, "ADMIN_CREATE", identity.id, request.requestId, request.reason, after = accessSummary(identity))
                putDeduplication(actor.id, request.requestId, AdminDeduplication(fingerprint, identity))
                identity
            }
        }.also { alert("Cấp tài khoản quản trị mới", "Tài khoản" to it.email, "Vai trò" to it.role.name, "Lý do" to request.reason) }
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
        }.also { alert("Thay đổi quyền quản trị", "Tài khoản" to it.email, "Quyền mới" to accessSummary(it), "Lý do" to request.reason) }
    }

    suspend fun createPlayer(token: String?, csrfToken: String?, origin: String?, request: CreatePlayerRequest): UserDetail {
        requireOrigin(origin)
        val email = normalizeEmail(request.email); val name = request.displayName.trim()
        validateMutation(request.reason, request.requestId); validatePassword(request.password, true)
        if (name.isEmpty() || name.length > 32 || name.any(Char::isISOControl) ||
            !request.password.any(Char::isUpperCase) || !request.password.any(Char::isLowerCase) ||
            !request.password.any(Char::isDigit) || !request.password.any { !it.isLetterOrDigit() } || request.password.any(Char::isISOControl)) invalid()
        repository.transaction { principal(token, csrfToken, true, ownerOnly = true) }
        val hash = withContext(Dispatchers.Default) { passwordHasher.hash(request.password) }
        val signature = fingerprint("support-player", email, name, request.reason)
        return withContext(Dispatchers.Default) { repository.transaction {
            val actor = principal(token, csrfToken, true, true).second.identity
            playerCreation(actor.id, request.requestId)?.let { receipt ->
                if (receipt.fingerprint != signature || !passwordHasher.verify(request.password, playerPasswordHash(receipt.userId) ?: conflict())) conflict()
                return@transaction user(receipt.userId) ?: conflict()
            }
            val created = createSupportPlayer(UUID.randomUUID().toString(), email, name, hash, nowMillis())
            auditEntry(actor.id, "PLAYER_SUPPORT_CREATE", created.id, request.requestId, request.reason,
                after = "accountType=REGISTERED;emailVerified=false;supportEmailVerificationExempt=true")
            putPlayerCreation(actor.id, request.requestId, PlayerCreationReceipt(signature, created.id))
            created
        } }
    }

    suspend fun users(token: String?, query: String, page: PageRequest): AdminPage<UserSummary> = repository.readTransaction {
        val actor = principal(token).second.identity
        val q = query.trim(); if (q.length > 128 || q.any(Char::isISOControl)) invalid()
        if (actor.role == AdminRole.SUPPORT && '@' in q) forbidden()
        val result = users(q, actor.role != AdminRole.SUPPORT, page)
        auditEntry(actor.id, "USER_SEARCH") // Never persist the query or player email.
        result.copy(items = result.items.map { if (actor.role == AdminRole.SUPPORT) it.copy(email = maskEmail(it.email)) else it })
    }
    suspend fun playerOverview(token: String?): PlayerOverviewResponse = repository.readTransaction {
        val actor = principal(token).second.identity
        if (actor.role == AdminRole.SUPPORT) forbidden()
        val snapshotAt = nowMillis()
        val todayFrom = java.time.Instant.ofEpochMilli(snapshotAt).atZone(java.time.ZoneId.of("Asia/Bangkok"))
            .toLocalDate().atStartOfDay(java.time.ZoneId.of("Asia/Bangkok")).toInstant().toEpochMilli()
        val stats = playerOverview(todayFrom, snapshotAt)
        auditEntry(actor.id, "PLAYER_OVERVIEW")
        PlayerOverviewResponse(snapshotAt, todayFrom, snapshotAt, stats.totalPlayers, stats.registeredPlayers,
            stats.guestPlayers, stats.playableAccounts, stats.matchesToday, stats.recordedParticipations,
            stats.recordedWins, stats.recordedLosses, stats.recordedDraws, stats.playedProfiles, stats.clanCount,
            stats.clanMembers, stats.averageElo, stats.maxElo, playerCommerce(todayFrom - 6 * 86_400_000L, snapshotAt))
    }
    suspend fun user(token: String?, id: String): UserDetail = repository.readTransaction {
        val actor = principal(token).second.identity; uuid(id)
        val result = user(id) ?: missing(); auditEntry(actor.id, "USER_DETAIL", id)
        if (actor.role == AdminRole.SUPPORT) result.copy(email = maskEmail(result.email)) else result
    }
    suspend fun giftRecipient(token: String?, reference: String): UserSummary = repository.readTransaction {
        val actor = principal(token).second.identity
        if (actor.role == AdminRole.SUPPORT) forbidden()
        val value = reference.trim()
        if (value.isEmpty() || reference.length > 254 || reference.any(Char::isISOControl)) invalid()
        val result = giftRecipient(value) ?: throw AdminException(404, "NOT_FOUND", legacyFallback("Không tìm thấy tài khoản đã đăng ký đủ điều kiện nhận quà với mã hoặc email này."))
        auditEntry(actor.id, "GIFT_RECIPIENT_LOOKUP", result.id)
        result
    }
    suspend fun matches(token: String?, id: String, page: PageRequest): AdminPage<MatchEntry> = repository.readTransaction {
        val actor = principal(token).second.identity; uuid(id); user(id) ?: missing()
        val result = matches(id, page); auditEntry(actor.id, "USER_MATCHES", id); result
    }
    suspend fun wallet(token: String?, id: String, giftsOnly: Boolean, page: PageRequest): AdminPage<WalletEntry> = repository.readTransaction {
        val actor = principal(token).second.identity; uuid(id); user(id) ?: missing()
        val result = wallet(id, giftsOnly, page); auditEntry(actor.id, if (giftsOnly) "USER_GIFTS" else "USER_WALLET", id); result
    }
    suspend fun accounts(token: String?, page: PageRequest): AdminPage<AdminIdentity> = repository.readTransaction {
        val actor = principal(token, ownerOnly = true).second.identity
        val result = accounts(page); auditEntry(actor.id, "ADMIN_LIST"); result
    }
    suspend fun audit(token: String?, page: PageRequest): AdminPage<AuditEntry> = repository.readTransaction {
        val actor = principal(token, ownerOnly = true).second.identity
        val result = audit(page); auditEntry(actor.id, "AUDIT_READ"); result
    }
    /** Caller must hold the repository's admin authorization lock for this transaction. */
    internal fun authorize(transaction: AdminTransaction, token: String?, csrfToken: String? = null,
        mutation: Boolean = false, ownerOnly: Boolean = false): AdminIdentity =
        transaction.principal(token, csrfToken, mutation, ownerOnly).second.identity

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
    companion object {
        const val SESSION_MILLIS = 8 * 60 * 60 * 1000L
        const val MFA_CHALLENGE_MILLIS = 5 * 60 * 1000L
        const val MFA_MAX_ATTEMPTS = 5
    }
}
internal fun maskEmail(email: String?): String? {
    if (email == null) return null
    val local = email.substringBefore('@'); val domain = email.substringAfter('@')
    val suffix = domain.substringAfterLast('.', "")
    return "${local.take(1)}***@${domain.take(1)}***${if (suffix.isEmpty()) "" else ".$suffix"}"
}
