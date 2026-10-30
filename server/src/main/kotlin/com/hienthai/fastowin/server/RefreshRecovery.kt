package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
import java.util.UUID
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class RefreshRecovery(
    val sessionId: UUID,
    val predecessorHash: String,
    val attemptHash: String,
    val successorRefreshHash: String,
    val successorAccessHash: String,
    val issuedAtMillis: Long,
    val recoveryExpiresAtMillis: Long,
    val metadataExpiresAtMillis: Long,
    val codecVersion: Int = 1,
    val nonce: ByteArray? = null,
    val ciphertext: ByteArray? = null
)

internal const val REFRESH_RECOVERY_TTL_MILLIS = 300_000L

internal fun validRefreshAttemptSecret(secret: String): Boolean {
    if (secret.length != 43 || !secret.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) return false
    val bytes = runCatching { Base64.getUrlDecoder().decode(secret) }.getOrNull() ?: return false
    return try { bytes.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == secret }
    finally { bytes.fill(0) }
}

/** Credentials are transient; only authenticated ciphertext and hashes reach persistence. */
class RefreshRotation internal constructor(
    internal val predecessorToken: String,
    internal val attemptSecret: String,
    internal val nowMillis: () -> Long,
    internal val issue: (UUID, Long) -> Pair<NewAuthSession, AuthSessionResponse>
) {
    internal val predecessorHash = hashToken(predecessorToken)
    internal val attemptHash = hashToken(attemptSecret)
    internal fun receipt(sessionId: UUID, replacement: NewAuthSession, result: AuthSessionResponse): RefreshRecovery = RefreshRecoveryCodec.seal(
        RefreshRecovery(sessionId, predecessorHash, attemptHash, replacement.refreshTokenHash,
            replacement.accessTokenHash, replacement.nowMillis, replacement.nowMillis + REFRESH_RECOVERY_TTL_MILLIS,
            replacement.refreshExpiresAtMillis), predecessorToken, attemptSecret, result)
}

internal object RefreshRecoveryCodec {
    private val random = SecureRandom()
    private val info = "fasttowin/auth-refresh-recovery/v1".toByteArray(Charsets.UTF_8)

    internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
        require(size in 1..(255 * 32))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        var previous = ByteArray(0)
        return try {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val output = ByteArray(size)
            var offset = 0
            var counter = 1
            while (offset < size) {
                mac.update(previous); mac.update(info); mac.update(counter.toByte())
                val next = mac.doFinal()
                previous.fill(0); previous = next
                val count = minOf(next.size, size - offset)
                next.copyInto(output, offset, 0, count)
                offset += count; counter++
            }
            output
        } finally { prk.fill(0); previous.fill(0) }
    }

    private fun key(token: String, secret: String): ByteArray {
        require(validRefreshAttemptSecret(secret))
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        val salt = Base64.getUrlDecoder().decode(secret)
        return try { hkdf(tokenBytes, salt, info, 32) }
        finally { tokenBytes.fill(0); salt.fill(0) }
    }

    // Fixed field order and unambiguous separators; all textual fields have fixed formats.
    private fun aad(r: RefreshRecovery): ByteArray = listOf(
        "fasttowin/auth-refresh-recovery", r.codecVersion, r.sessionId, r.predecessorHash, r.attemptHash,
        r.successorRefreshHash, r.successorAccessHash, r.issuedAtMillis, r.recoveryExpiresAtMillis,
        r.metadataExpiresAtMillis
    ).joinToString("\n").toByteArray(Charsets.UTF_8)

    fun seal(metadata: RefreshRecovery, token: String, secret: String, response: AuthSessionResponse): RefreshRecovery {
        val nonce = ByteArray(12).also(random::nextBytes)
        val derived = key(token, secret)
        val plaintext = ProtocolJson.encodeToString(response).toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(metadata))
            metadata.copy(nonce = nonce, ciphertext = cipher.doFinal(plaintext))
        } finally { derived.fill(0); plaintext.fill(0) }
    }

    fun open(receipt: RefreshRecovery, token: String, secret: String): AuthSessionResponse? {
        if (receipt.codecVersion != 1 || receipt.nonce?.size != 12 || receipt.ciphertext?.size !in 16..8192 ||
            !validRefreshAttemptSecret(secret) || receipt.predecessorHash != hashToken(token) || receipt.attemptHash != hashToken(secret)) return null
        val derived = key(token, secret)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, receipt.nonce))
            cipher.updateAAD(aad(receipt))
            val plaintext = cipher.doFinal(receipt.ciphertext)
            try { ProtocolJson.decodeFromString<AuthSessionResponse>(plaintext.toString(Charsets.UTF_8)) }
            finally { plaintext.fill(0) }
        } catch (_: Exception) { null }
        finally { derived.fill(0) }
    }
}
