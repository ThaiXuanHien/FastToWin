package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.hashToken
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ADM-04: time-based one-time passwords for admin accounts (RFC 6238: HMAC-SHA1,
 * 6 digits, 30 second steps) so a leaked password alone cannot open the console.
 */
data class AdminMfaSettings(
    /** When true every role must enroll before a session is issued. */
    val required: Boolean,
    private val key: ByteArray
) {
    init { require(key.size == 32) { "FASTTOWIN_ADMIN_MFA_KEY must decode to exactly 32 bytes." } }

    internal val cipher: AdminSecretCipher get() = AdminSecretCipher(key)

    override fun equals(other: Any?) = other is AdminMfaSettings && other.required == required && other.key.contentEquals(key)
    override fun hashCode() = 31 * required.hashCode() + key.contentHashCode()
    override fun toString() = "AdminMfaSettings(required=$required, key=[redacted])"

    companion object {
        /** Development only: a fixed, public key keeps enrolled dev accounts usable across restarts. */
        val DEVELOPMENT = AdminMfaSettings(false, MessageDigest.getInstance("SHA-256")
            .digest("fasttowin-development-admin-mfa-key".toByteArray(StandardCharsets.UTF_8)))

        fun fromEnvironment(environment: String, values: Map<String, String>, fileReader: (String) -> String = { Files.readString(Path.of(it)) }): AdminMfaSettings {
            val encoded = values["FASTTOWIN_ADMIN_MFA_KEY_FILE"]?.trim()?.takeIf(String::isNotEmpty)?.let { fileReader(it).trim() }
                ?: values["FASTTOWIN_ADMIN_MFA_KEY"]?.trim()?.takeIf(String::isNotEmpty)
            val requiredValue = values["FASTTOWIN_ADMIN_MFA_REQUIRED"]?.trim()?.lowercase()
            require(requiredValue == null || requiredValue in setOf("true", "false")) { "FASTTOWIN_ADMIN_MFA_REQUIRED must be true or false." }
            if (environment != "dev") {
                require(requiredValue != "false") { "Admin MFA cannot be disabled in production." }
                val key = requireNotNull(encoded) {
                    "FASTTOWIN_ADMIN_MFA_KEY or FASTTOWIN_ADMIN_MFA_KEY_FILE is required when the admin console is enabled in production."
                }
                return AdminMfaSettings(true, decodeKey(key))
            }
            val key = encoded?.let(::decodeKey) ?: DEVELOPMENT.keyCopy()
            return AdminMfaSettings(requiredValue == "true", key)
        }

        private fun decodeKey(value: String): ByteArray = runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { throw IllegalArgumentException("FASTTOWIN_ADMIN_MFA_KEY must be standard Base64.") }
    }

    private fun keyCopy() = key.copyOf()
}

/** AES-256-GCM for TOTP secrets at rest; the admin id is bound as associated data. */
internal class AdminSecretCipher(private val key: ByteArray) {
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, adminId: String): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(adminId.toByteArray(StandardCharsets.UTF_8))
        val sealed = cipher.doFinal(plain)
        return "v1." + Base64.getEncoder().encodeToString(nonce + sealed)
    }

    fun decrypt(value: String, adminId: String): ByteArray {
        require(value.startsWith("v1.")) { "Unsupported admin secret format." }
        val bytes = Base64.getDecoder().decode(value.removePrefix("v1."))
        require(bytes.size > 12 + 16) { "Admin secret is truncated." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes, 0, 12))
        cipher.updateAAD(adminId.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
}

internal object Totp {
    const val STEP_SECONDS = 30L
    const val DIGITS = 6
    private const val ISSUER = "FastToWin Admin"
    private val random = SecureRandom()

    fun newSecret(): ByteArray = ByteArray(20).also(random::nextBytes)

    fun step(nowMillis: Long): Long = Math.floorDiv(nowMillis / 1000, STEP_SECONDS)

    fun code(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /**
     * Returns the matching step within one step of clock drift, or null. Steps at or before
     * [lastUsedStep] are rejected so an observed code cannot be replayed.
     */
    fun verify(secret: ByteArray, input: String, nowMillis: Long, lastUsedStep: Long): Long? {
        val code = input.filter { !it.isWhitespace() }
        if (code.length != DIGITS || !code.all(Char::isDigit)) return null
        val current = step(nowMillis)
        return (current - 1..current + 1).firstOrNull { candidate ->
            candidate > lastUsedStep && MessageDigest.isEqual(code(secret, candidate).toByteArray(), code.toByteArray())
        }
    }

    fun otpauthUri(email: String, secret: ByteArray): String {
        fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        return "otpauth://totp/${encode("$ISSUER:$email")}?secret=${Base32.encode(secret)}&issuer=${encode(ISSUER)}" +
            "&algorithm=SHA1&digits=$DIGITS&period=$STEP_SECONDS"
    }
}

internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String = buildString {
        var buffer = 0; var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff); bits += 8
            while (bits >= 5) { append(ALPHABET[(buffer shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 31])
    }
}

/** Single-use recovery codes; only their hashes are stored. */
internal object RecoveryCodes {
    const val COUNT = 10
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    fun generate(): List<String> = List(COUNT) {
        val raw = (1..10).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        "${raw.substring(0, 5)}-${raw.substring(5)}"
    }

    fun normalize(input: String): String = input.lowercase().filter { it.isLetterOrDigit() }

    fun hash(input: String): String = hashToken("admin-recovery-code:" + normalize(input))
}
