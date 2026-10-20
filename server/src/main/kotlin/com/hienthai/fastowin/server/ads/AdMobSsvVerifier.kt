package com.hienthai.fastowin.server.ads

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.Signature
import java.util.Base64

/** Only this verifier constructs authenticated provider events; callers never deserialize one from a client body. */
@ConsistentCopyVisibility
data class VerifiedAdMobEvent internal constructor(
    val adUnit: String, val customData: String?, val transactionId: String,
    val rewardAmount: String, val rewardItem: String, val timestampMillis: Long
)

class AdMobSsvVerifier(private val keys: AdMobPublicKeys = CachedAdMobPublicKeys()) {
    suspend fun verify(rawQuery: String): VerifiedAdMobEvent {
        if (rawQuery.length !in 1..8192 || rawQuery.any { it.code !in 33..126 }) invalid()
        val parts = rawQuery.split('&')
        if (parts.size !in 7..16) invalid()
        val fields = linkedMapOf<String, String>()
        parts.forEach { part ->
            val i = part.indexOf('='); if (i < 1) invalid()
            val name = part.substring(0, i)
            if (!Regex("[a-z_]+").matches(name) || fields.containsKey(name)) invalid()
            fields[name] = decode(part.substring(i + 1))
        }
        if (parts[parts.size - 2].substringBefore('=') != "signature" || parts.last().substringBefore('=') != "key_id") invalid()
        val keyId = fields.getValue("key_id")
        if (!Regex("[0-9]{1,20}").matches(keyId)) invalid()
        val signature = fields.getValue("signature")
        if (!Regex("[A-Za-z0-9_-]{1,128}={0,2}").matches(signature)) invalid()
        val publicKey = keys.key(keyId) ?: invalid()
        val content = rawQuery.substring(0, rawQuery.lastIndexOf("&signature="))
        val verified = try { Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey); update(content.toByteArray(Charsets.UTF_8)); verify(Base64.getUrlDecoder().decode(signature))
        } } catch (_: java.security.GeneralSecurityException) { false } catch (_: IllegalArgumentException) { false }
        if (!verified) invalid()
        fun required(name: String) = fields[name]?.takeIf { it.isNotEmpty() } ?: invalid()
        val transaction = required("transaction_id")
        if (!Regex("[a-fA-F0-9]{1,256}").matches(transaction)) invalid()
        val timestamp = required("timestamp").takeIf { Regex("[0-9]{1,16}").matches(it) }?.toLongOrNull() ?: invalid()
        return VerifiedAdMobEvent(required("ad_unit"), fields["custom_data"], transaction,
            required("reward_amount"), required("reward_item"), timestamp)
    }
    private fun decode(value: String): String = try {
        val bytes = ByteArrayOutputStream(); var i = 0
        while (i < value.length) {
            when (val ch = value[i]) {
                '%' -> { if (i + 2 >= value.length) invalid()
                    val byte = value.substring(i + 1, i + 3).toIntOrNull(16) ?: invalid()
                    bytes.write(byte); i += 3 }
                '+' -> { bytes.write(32); i++ }
                else -> { bytes.write(ch.code); i++ }
            }
        }
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString().also { if (it.any { ch -> ch.isISOControl() }) invalid() }
    } catch (_: java.nio.charset.CharacterCodingException) { invalid() }
    private fun invalid(): Nothing = throw AdMobInvalidRequest()
}
