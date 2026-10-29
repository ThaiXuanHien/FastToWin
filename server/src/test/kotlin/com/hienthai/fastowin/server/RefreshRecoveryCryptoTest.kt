package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
import java.util.Base64
import java.util.UUID
import kotlin.test.*

class RefreshRecoveryCryptoTest {
    @Test fun `HKDF matches RFC5869 appendix A1`() {
        fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertContentEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            RefreshRecoveryCodec.hkdf(ByteArray(22) { 0x0b }, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42))
    }
    @Test fun `receipt authenticates payload and every bound metadata field`() {
        val token = "a".repeat(43)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
        val response = AuthSessionResponse("user", "", "access", "refresh", 901000, 2592001000)
        val metadata = RefreshRecovery(UUID.randomUUID(), hashToken(token), hashToken(secret), hashToken("refresh"), hashToken("access"), 1000, 301000, 2592001000)
        val receipt = RefreshRecoveryCodec.seal(metadata, token, secret, response)
        assertEquals(response, RefreshRecoveryCodec.open(receipt, token, secret))
        val ciphertext = assertNotNull(receipt.ciphertext)
        assertEquals(12, receipt.nonce!!.size)
        assertFalse(ciphertext.toString(Charsets.UTF_8).contains("refresh"))
        val changes = listOf(receipt.copy(sessionId = UUID.randomUUID()), receipt.copy(predecessorHash = "0".repeat(64)),
            receipt.copy(attemptHash = "0".repeat(64)), receipt.copy(successorRefreshHash = "0".repeat(64)),
            receipt.copy(successorAccessHash = "0".repeat(64)), receipt.copy(issuedAtMillis = 1001),
            receipt.copy(recoveryExpiresAtMillis = 301001), receipt.copy(metadataExpiresAtMillis = 2592001001),
            receipt.copy(codecVersion = 2), receipt.copy(nonce = ByteArray(12)),
            receipt.copy(ciphertext = ciphertext.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }),
            receipt.copy(ciphertext = byteArrayOf(1)), receipt.copy(ciphertext = ByteArray(8193)),
            receipt.copy(nonce = null, ciphertext = null), receipt.copy(nonce = ByteArray(13)))
        changes.forEach { assertNull(RefreshRecoveryCodec.open(it, token, secret)) }
        assertNull(RefreshRecoveryCodec.open(receipt, "b".repeat(43), secret))
        assertNull(RefreshRecoveryCodec.open(receipt, token, Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 2 })))
        assertNull(RefreshRecoveryCodec.open(receipt, token, "x".repeat(43)))
        val another = RefreshRecoveryCodec.seal(metadata, token, secret, response)
        assertFalse(receipt.nonce.contentEquals(another.nonce))
    }
}
