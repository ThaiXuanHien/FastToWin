package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
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
    val session: NewAuthSession
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
data class AuthenticatedAccount(val userId: UUID, val displayName: String)

interface AuthRepository {
    suspend fun createAccount(account: NewAccount): Boolean
    suspend fun findActiveAccount(emailNormalized: String): AccountCredentials?
    suspend fun createSession(userId: UUID, devicePlatform: String?, session: NewAuthSession)
    suspend fun rotateSession(refreshTokenHash: String, replacement: NewAuthSession): UUID?
    suspend fun revokeSession(refreshTokenHash: String, nowMillis: Long): Boolean
    suspend fun findActiveSession(accessTokenHash: String, nowMillis: Long): AuthenticatedAccount?
}

sealed interface AuthResult {
    data class Success(val session: AuthSessionResponse) : AuthResult
    data class Failure(val code: String, val message: String) : AuthResult
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
        devicePlatform: String?
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
                session = issued.record
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

    private fun playerCode(userId: UUID): String = userId.toString().replace("-", "").take(10).uppercase()

    private data class IssuedTokens(val record: NewAuthSession, val response: AuthSessionResponse)

    companion object {
        const val ACCESS_TOKEN_TTL_MILLIS = 15L * 60 * 1_000
        const val REFRESH_TOKEN_TTL_MILLIS = 30L * 24 * 60 * 60 * 1_000
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
