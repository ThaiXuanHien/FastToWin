package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import com.hienthai.fastowin.protocol.PlayerGender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class NewAccount(
    val userId: UUID,
    val emailNormalized: String,
    val passwordHash: String,
    val displayName: String,
    val playerCode: String,
    val devicePlatform: String?,
    val session: NewAuthSession,
    val gender: PlayerGender = PlayerGender.MALE
)

data class NewAuthSession(
    val sessionId: UUID,
    val accessTokenHash: String,
    val refreshTokenHash: String,
    val accessExpiresAtMillis: Long,
    val refreshExpiresAtMillis: Long,
    val nowMillis: Long
)

data class AccountCredentials(val userId: UUID, val passwordHash: String, val displayName: String)
data class AuthenticatedAccount(
    val userId: UUID,
    val displayName: String,
    val sessionId: UUID? = null
)
data class StoredAccountSession(
    val sessionId: UUID,
    val devicePlatform: String?,
    val createdAtMillis: Long,
    val lastSeenAtMillis: Long,
    val expiresAtMillis: Long
)
data class NewPasswordReset(
    val id: UUID,
    val tokenHash: String,
    val nowMillis: Long,
    val expiresAtMillis: Long
)
data class GuestUpgrade(
    val resumeTokenHash: String,
    val emailNormalized: String,
    val passwordHash: String,
    val devicePlatform: String?,
    val session: NewAuthSession
)

sealed interface GuestUpgradeResult {
    data class Success(val userId: UUID, val displayName: String) : GuestUpgradeResult
    data object InvalidGuestSession : GuestUpgradeResult
    data object EmailAlreadyExists : GuestUpgradeResult
    data object Unsupported : GuestUpgradeResult
}

interface AuthRepository {
    suspend fun createAccount(account: NewAccount): Boolean
    suspend fun findActiveAccount(emailNormalized: String): AccountCredentials?
    suspend fun findActiveAccountById(userId: UUID): AccountCredentials?
    suspend fun createSession(userId: UUID, devicePlatform: String?, session: NewAuthSession)
    suspend fun rotateSession(refreshTokenHash: String, replacement: NewAuthSession): UUID?
    suspend fun revokeSession(refreshTokenHash: String, nowMillis: Long): Boolean
    suspend fun findActiveSession(accessTokenHash: String, nowMillis: Long): AuthenticatedAccount?
    suspend fun listActiveSessions(userId: UUID, nowMillis: Long): List<StoredAccountSession>
    suspend fun revokeSessionById(userId: UUID, sessionId: UUID, nowMillis: Long): Boolean
    suspend fun revokeAllSessions(userId: UUID, nowMillis: Long): Int
    suspend fun upgradeGuest(upgrade: GuestUpgrade): GuestUpgradeResult
    suspend fun updatePasswordAndRevokeSessions(userId: UUID, passwordHash: String, nowMillis: Long): Boolean
    suspend fun createPasswordReset(emailNormalized: String, reset: NewPasswordReset): Boolean
    suspend fun consumePasswordReset(
        emailNormalized: String,
        tokenHash: String,
        passwordHash: String,
        nowMillis: Long
    ): Boolean
    suspend fun deleteAccount(userId: UUID): Boolean
}

sealed interface AuthResult {
    data class Success(val session: AuthSessionResponse) : AuthResult
    data class Failure(val code: String, val message: String) : AuthResult
}

sealed interface AccountActionResult {
    data class Success(val message: String, val resetToken: String? = null) : AccountActionResult
    data class Failure(val code: String, val message: String) : AccountActionResult
}

sealed interface AccountSessionsResult {
    data class Success(val sessions: List<AccountSessionSnapshot>) : AccountSessionsResult
    data class Failure(val code: String, val message: String) : AccountSessionsResult
}

class AuthenticationService(
    private val repository: AuthRepository,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        devicePlatform: String?,
        gender: PlayerGender = PlayerGender.MALE
    ): AuthResult {
        val normalizedEmail = normalizeEmail(email)
            ?: return AuthResult.Failure("INVALID_EMAIL", "Email không hợp lệ.")
        val passwordError = validatePassword(password)
        if (passwordError != null) return AuthResult.Failure("INVALID_PASSWORD", passwordError)
        val safeName = displayName.trim()
        if (safeName.isEmpty() || safeName.length > MAX_DISPLAY_NAME_LENGTH) {
            return AuthResult.Failure(
                "INVALID_DISPLAY_NAME",
                "Biệt danh phải có từ 1 đến $MAX_DISPLAY_NAME_LENGTH ký tự."
            )
        }

        val now = nowMillis()
        val userId = UUID.randomUUID()
        val issued = issueTokens(userId, safeName, now)
        val passwordHash = withContext(Dispatchers.Default) { passwordHasher.hash(password) }
        val created = repository.createAccount(
            NewAccount(
                userId = userId,
                emailNormalized = normalizedEmail,
                passwordHash = passwordHash,
                displayName = safeName,
                playerCode = playerCode(userId),
                devicePlatform = normalizeDevicePlatform(devicePlatform),
                session = issued.record,
                gender = gender
            )
        )
        if (!created) return AuthResult.Failure("EMAIL_ALREADY_EXISTS", "Email này đã được sử dụng.")
        return AuthResult.Success(issued.response)
    }

    suspend fun login(email: String, password: String, devicePlatform: String?): AuthResult {
        val normalizedEmail = normalizeEmail(email)
            ?: return invalidCredentials()
        val account = repository.findActiveAccount(normalizedEmail)
            ?: return invalidCredentials()
        val passwordMatches = withContext(Dispatchers.Default) {
            passwordHasher.verify(password, account.passwordHash)
        }
        if (!passwordMatches) return invalidCredentials()

        val issued = issueTokens(account.userId, account.displayName, nowMillis())
        repository.createSession(account.userId, normalizeDevicePlatform(devicePlatform), issued.record)
        return AuthResult.Success(issued.response)
    }

    suspend fun upgradeGuest(
        resumeToken: String,
        email: String,
        password: String,
        devicePlatform: String?
    ): AuthResult {
        if (!isValidTokenShape(resumeToken)) return invalidGuestSession()
        val normalizedEmail = normalizeEmail(email)
            ?: return AuthResult.Failure("INVALID_EMAIL", "Email không hợp lệ.")
        val passwordError = validatePassword(password)
        if (passwordError != null) return AuthResult.Failure("INVALID_PASSWORD", passwordError)

        val now = nowMillis()
        val placeholderUserId = UUID.randomUUID()
        val issued = issueTokens(placeholderUserId, "", now)
        val passwordHash = withContext(Dispatchers.Default) { passwordHasher.hash(password) }
        return when (val result = repository.upgradeGuest(
            GuestUpgrade(
                resumeTokenHash = hashToken(resumeToken),
                emailNormalized = normalizedEmail,
                passwordHash = passwordHash,
                devicePlatform = normalizeDevicePlatform(devicePlatform),
                session = issued.record
            )
        )) {
            is GuestUpgradeResult.Success -> AuthResult.Success(
                issued.response.copy(
                    userId = result.userId.toString(),
                    displayName = result.displayName
                )
            )
            GuestUpgradeResult.InvalidGuestSession -> invalidGuestSession()
            GuestUpgradeResult.EmailAlreadyExists -> AuthResult.Failure(
                "EMAIL_ALREADY_EXISTS",
                "Email này đã được sử dụng."
            )
            GuestUpgradeResult.Unsupported -> AuthResult.Failure(
                "DATABASE_REQUIRED",
                "Cần chạy server cùng PostgreSQL để lưu tài khoản khách."
            )
        }
    }

    suspend fun refresh(refreshToken: String): AuthResult {
        if (!isValidTokenShape(refreshToken)) return invalidRefreshToken()
        val now = nowMillis()
        val placeholderUserId = UUID.randomUUID()
        val replacement = issueTokens(placeholderUserId, "", now)
        val userId = repository.rotateSession(hashToken(refreshToken), replacement.record)
            ?: return invalidRefreshToken()
        return AuthResult.Success(replacement.response.copy(userId = userId.toString()))
    }

    suspend fun logout(refreshToken: String) {
        if (isValidTokenShape(refreshToken)) repository.revokeSession(hashToken(refreshToken), nowMillis())
    }

    suspend fun authenticateAccessToken(accessToken: String): AuthenticatedAccount? {
        if (!isValidTokenShape(accessToken)) return null
        return repository.findActiveSession(hashToken(accessToken), nowMillis())
    }

    suspend fun listSessions(accessToken: String): AccountSessionsResult {
        val authenticated = authenticateAccessToken(accessToken)
            ?: return invalidAccountSessions()
        val currentSessionId = authenticated.sessionId ?: return invalidAccountSessions()
        val sessions = repository.listActiveSessions(authenticated.userId, nowMillis()).map { session ->
            AccountSessionSnapshot(
                sessionId = session.sessionId.toString(),
                devicePlatform = session.devicePlatform,
                createdAtEpochMillis = session.createdAtMillis,
                lastSeenAtEpochMillis = session.lastSeenAtMillis,
                expiresAtEpochMillis = session.expiresAtMillis,
                isCurrent = session.sessionId == currentSessionId
            )
        }
        return AccountSessionsResult.Success(sessions)
    }

    suspend fun revokeSession(accessToken: String, sessionId: String): AccountActionResult {
        val authenticated = authenticateAccessToken(accessToken)
            ?: return invalidAccountSession()
        val targetSessionId = runCatching { UUID.fromString(sessionId) }.getOrNull()
            ?: return AccountActionResult.Failure("INVALID_SESSION_ID", "Phiên đăng nhập không hợp lệ.")
        return if (repository.revokeSessionById(authenticated.userId, targetSessionId, nowMillis())) {
            AccountActionResult.Success("Đã đăng xuất thiết bị.")
        } else {
            AccountActionResult.Failure("SESSION_NOT_FOUND", "Phiên đăng nhập không còn hoạt động.")
        }
    }

    suspend fun revokeAllSessions(accessToken: String): AccountActionResult {
        val authenticated = authenticateAccessToken(accessToken)
            ?: return invalidAccountSession()
        repository.revokeAllSessions(authenticated.userId, nowMillis())
        return AccountActionResult.Success("Đã đăng xuất khỏi tất cả thiết bị.")
    }

    suspend fun changePassword(
        accessToken: String,
        currentPassword: String,
        newPassword: String
    ): AccountActionResult {
        val authenticated = authenticateAccessToken(accessToken)
            ?: return invalidAccountSession()
        val account = repository.findActiveAccountById(authenticated.userId)
            ?: return invalidAccountSession()
        val matches = withContext(Dispatchers.Default) {
            passwordHasher.verify(currentPassword, account.passwordHash)
        }
        if (!matches) return AccountActionResult.Failure(
            "INVALID_CURRENT_PASSWORD",
            "Mật khẩu hiện tại không đúng."
        )
        val passwordError = validatePassword(newPassword)
        if (passwordError != null) return AccountActionResult.Failure("INVALID_PASSWORD", passwordError)
        if (currentPassword == newPassword) return AccountActionResult.Failure(
            "PASSWORD_UNCHANGED",
            "Mật khẩu mới phải khác mật khẩu hiện tại."
        )
        val passwordHash = withContext(Dispatchers.Default) { passwordHasher.hash(newPassword) }
        return if (repository.updatePasswordAndRevokeSessions(authenticated.userId, passwordHash, nowMillis())) {
            AccountActionResult.Success("Đã đổi mật khẩu. Vui lòng đăng nhập lại.")
        } else invalidAccountSession()
    }

    suspend fun requestPasswordReset(email: String): AccountActionResult {
        val normalizedEmail = normalizeEmail(email)
            ?: return AccountActionResult.Failure("INVALID_EMAIL", "Email không hợp lệ.")
        val now = nowMillis()
        val token = newToken()
        val created = repository.createPasswordReset(
            normalizedEmail,
            NewPasswordReset(
                id = UUID.randomUUID(),
                tokenHash = hashToken(token),
                nowMillis = now,
                expiresAtMillis = now + PASSWORD_RESET_TTL_MILLIS
            )
        )
        return AccountActionResult.Success(
            "Nếu email tồn tại, hướng dẫn khôi phục mật khẩu đã được tạo.",
            resetToken = token.takeIf { created }
        )
    }

    suspend fun resetPassword(
        email: String,
        resetToken: String,
        newPassword: String
    ): AccountActionResult {
        val normalizedEmail = normalizeEmail(email)
            ?: return invalidPasswordReset()
        if (!isValidTokenShape(resetToken)) return invalidPasswordReset()
        val passwordError = validatePassword(newPassword)
        if (passwordError != null) return AccountActionResult.Failure("INVALID_PASSWORD", passwordError)
        val passwordHash = withContext(Dispatchers.Default) { passwordHasher.hash(newPassword) }
        return if (repository.consumePasswordReset(
                normalizedEmail,
                hashToken(resetToken),
                passwordHash,
                nowMillis()
            )) {
            AccountActionResult.Success("Đã đặt lại mật khẩu. Bạn có thể đăng nhập ngay.")
        } else invalidPasswordReset()
    }

    suspend fun deleteAccount(accessToken: String, password: String): AccountActionResult {
        val authenticated = authenticateAccessToken(accessToken)
            ?: return invalidAccountSession()
        val account = repository.findActiveAccountById(authenticated.userId)
            ?: return invalidAccountSession()
        val matches = withContext(Dispatchers.Default) {
            passwordHasher.verify(password, account.passwordHash)
        }
        if (!matches) return AccountActionResult.Failure(
            "INVALID_CURRENT_PASSWORD",
            "Mật khẩu không đúng."
        )
        return if (repository.deleteAccount(authenticated.userId)) {
            AccountActionResult.Success("Tài khoản và dữ liệu cá nhân đã được xóa.")
        } else invalidAccountSession()
    }

    private fun issueTokens(userId: UUID, displayName: String, now: Long): IssuedTokens {
        val accessToken = newToken()
        val refreshToken = newToken()
        val accessExpiry = now + ACCESS_TOKEN_TTL_MILLIS
        val refreshExpiry = now + REFRESH_TOKEN_TTL_MILLIS
        return IssuedTokens(
            record = NewAuthSession(
                sessionId = UUID.randomUUID(),
                accessTokenHash = hashToken(accessToken),
                refreshTokenHash = hashToken(refreshToken),
                accessExpiresAtMillis = accessExpiry,
                refreshExpiresAtMillis = refreshExpiry,
                nowMillis = now
            ),
            response = AuthSessionResponse(
                userId = userId.toString(),
                displayName = displayName,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessExpiresAtEpochMillis = accessExpiry,
                refreshExpiresAtEpochMillis = refreshExpiry
            )
        )
    }

    private fun normalizeEmail(email: String): String? {
        val normalized = email.trim().lowercase()
        if (normalized.length !in 3..254 || !EMAIL_PATTERN.matches(normalized)) return null
        return normalized
    }

    private fun validatePassword(password: String): String? = when {
        password.length < MIN_PASSWORD_LENGTH -> "Mật khẩu phải có ít nhất $MIN_PASSWORD_LENGTH ký tự."
        password.length > MAX_PASSWORD_LENGTH -> "Mật khẩu không được vượt quá $MAX_PASSWORD_LENGTH ký tự."
        else -> null
    }

    private fun normalizeDevicePlatform(value: String?): String? = value
        ?.trim()
        ?.lowercase()
        ?.take(MAX_DEVICE_PLATFORM_LENGTH)
        ?.takeIf(String::isNotEmpty)

    private fun invalidCredentials() = AuthResult.Failure(
        "INVALID_CREDENTIALS",
        "Email hoặc mật khẩu không đúng."
    )

    private fun invalidRefreshToken() = AuthResult.Failure(
        "INVALID_REFRESH_TOKEN",
        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
    )

    private fun invalidGuestSession() = AuthResult.Failure(
        "INVALID_GUEST_SESSION",
        "Phiên khách không hợp lệ hoặc đã hết hạn."
    )

    private fun invalidAccountSession() = AccountActionResult.Failure(
        "INVALID_ACCESS_TOKEN",
        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
    )

    private fun invalidAccountSessions() = AccountSessionsResult.Failure(
        "INVALID_ACCESS_TOKEN",
        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
    )

    private fun invalidPasswordReset() = AccountActionResult.Failure(
        "INVALID_RESET_TOKEN",
        "Mã khôi phục không hợp lệ hoặc đã hết hạn."
    )

    private fun playerCode(userId: UUID): String = userId.toString().replace("-", "").take(10).uppercase()

    private data class IssuedTokens(val record: NewAuthSession, val response: AuthSessionResponse)

    companion object {
        const val ACCESS_TOKEN_TTL_MILLIS = 15L * 60 * 1_000
        const val REFRESH_TOKEN_TTL_MILLIS = 30L * 24 * 60 * 60 * 1_000
        const val PASSWORD_RESET_TTL_MILLIS = 15L * 60 * 1_000
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 128
        private const val MAX_DISPLAY_NAME_LENGTH = 32
        private const val MAX_DEVICE_PLATFORM_LENGTH = 16
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val derived = derive(password, salt, iterations)
        return listOf(
            ALGORITHM_LABEL,
            iterations.toString(),
            Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
            Base64.getUrlEncoder().withoutPadding().encodeToString(derived)
        ).joinToString("$")
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != ALGORITHM_LABEL) return false
        val storedIterations = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_ITERATIONS } ?: return false
        val salt = runCatching { Base64.getUrlDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getUrlDecoder().decode(parts[3]) }.getOrNull() ?: return false
        if (salt.size != SALT_BYTES || expected.size != HASH_BYTES) return false
        return MessageDigest.isEqual(expected, derive(password, salt, storedIterations))
    }

    private fun derive(password: String, salt: ByteArray, count: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, count, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val ALGORITHM_LABEL = "pbkdf2_sha256"
        private const val DEFAULT_ITERATIONS = 210_000
        private const val MAX_ITERATIONS = 1_000_000
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
    }
}

private val tokenRandom = SecureRandom()

private fun newToken(): String = ByteArray(32)
    .also(tokenRandom::nextBytes)
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

internal fun hashToken(token: String): String = MessageDigest.getInstance("SHA-256")
    .digest(token.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

private fun isValidTokenShape(token: String): Boolean = token.length in 40..128
