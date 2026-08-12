package com.hienthai.fastowin.data.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidAuthSessionStore(context: Context) : AuthSessionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(serverUrl: String): StoredAuthSession? {
        val encoded = preferences.getString(key(serverUrl), null) ?: return null
        return runCatching {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.size > IV_SIZE_BYTES)
            val iv = encrypted.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = encrypted.copyOfRange(IV_SIZE_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            ProtocolJson.decodeFromString<StoredAuthSession>(
                cipher.doFinal(ciphertext).decodeToString()
            )
        }.getOrElse {
            clear(serverUrl)
            null
        }
    }

    override fun save(serverUrl: String, session: StoredAuthSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, encryptionKey())
        }
        val ciphertext = cipher.doFinal(ProtocolJson.encodeToString(session).encodeToByteArray())
        val encrypted = cipher.iv + ciphertext
        preferences.edit()
            .putString(key(serverUrl), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    override fun clear(serverUrl: String) {
        preferences.edit().remove(key(serverUrl)).apply()
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun key(serverUrl: String): String = "auth_session.$serverUrl"

    private companion object {
        const val PREFERENCES_NAME = "fast_to_win_secure_session"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "fast_to_win_auth_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
