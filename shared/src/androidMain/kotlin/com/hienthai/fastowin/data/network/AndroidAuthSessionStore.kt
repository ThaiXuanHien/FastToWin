package com.hienthai.fastowin.data.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidAuthSessionStore internal constructor(
    private val preferences: SharedPreferences,
    private val encode: (StoredAuthSession) -> String,
    private val decode: (String) -> StoredAuthSession
) : RecoverableAuthSessionStore {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE), ::encrypt, ::decrypt
    )

    override fun load(serverUrl: String): StoredAuthSession? = synchronized(LOCK) {
        val encoded = preferences.getString(key(serverUrl), null) ?: return@synchronized null
        runCatching { decode(encoded) }.getOrElse {
            clear(serverUrl)
            null
        }
    }

    override fun save(serverUrl: String, session: StoredAuthSession): Unit = synchronized(LOCK) {
        try {
            writeEncoded(serverUrl, encode(session))
        } catch (_: Exception) {
            throw storageFailure()
        }
    }

    override fun clear(serverUrl: String): Unit = synchronized(LOCK) {
        writeEncoded(serverUrl, null)
    }

    override fun prepareRefresh(serverUrl: String, expected: StoredAuthSession): StoredAuthSession? = synchronized(LOCK) {
        val current = load(serverUrl)?.takeIf { expected.sameGeneration(it) } ?: return@synchronized null
        if (current.refreshAttemptSecret != null) return@synchronized current
        val pending = current.copy(refreshAttemptSecret = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also(SECURE_RANDOM::nextBytes)))
        save(serverUrl, pending)
        pending
    }

    override fun completeRefresh(serverUrl: String, expected: StoredAuthSession, refreshed: StoredAuthSession): Boolean = synchronized(LOCK) {
        val current = load(serverUrl)?.takeIf { expected.sameRefreshOwner(it) && it.refreshAttemptSecret != null }
            ?: return@synchronized false
        // Profile/verification edits may have committed while HTTP was in flight.
        save(serverUrl, current.copy(
            userId = refreshed.userId,
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            accessExpiresAtEpochMillis = refreshed.accessExpiresAtEpochMillis,
            refreshExpiresAtEpochMillis = refreshed.refreshExpiresAtEpochMillis,
            refreshAttemptSecret = null
        ))
        true
    }

    override fun updateSession(serverUrl: String, expected: StoredAuthSession, updated: StoredAuthSession): Boolean = synchronized(LOCK) {
        if (!expected.sameRefreshOwner(load(serverUrl)) || !expected.sameRefreshOwner(updated)) return@synchronized false
        save(serverUrl, updated)
        true
    }

    override fun clearSession(serverUrl: String, expected: StoredAuthSession): Boolean = synchronized(LOCK) {
        if (!expected.sameRefreshOwner(load(serverUrl))) return@synchronized false
        clear(serverUrl)
        true
    }

    private fun writeEncoded(serverUrl: String, encoded: String?) {
        val preferenceKey = key(serverUrl)
        val previous = preferences.getString(preferenceKey, null)
        fun commit(value: String?): Boolean {
            val editor = preferences.edit()
            if (value == null) editor.remove(preferenceKey) else editor.putString(preferenceKey, value)
            return editor.commit()
        }
        try {
            if (!commit(encoded)) throw storageFailure()
        } catch (_: Exception) {
            // SharedPreferences changes memory before writing disk, including on false.
            // Restore the old encrypted record so another instance cannot use an
            // uncommitted pending attempt or prematurely observe new credentials.
            runCatching { commit(previous) }
            throw storageFailure()
        }
    }

    private fun storageFailure() = AuthSessionStoreException(
        "ANDROID_SESSION_SAVE_FAILED", "Could not securely save the authentication session."
    )

    private fun key(serverUrl: String): String = "auth_session.$serverUrl"

    private companion object {
        // Shared by every store instance, including load/save/clear and Keystore creation.
        private val LOCK = Any()
        private val SECURE_RANDOM = SecureRandom()
        private fun encrypt(session: StoredAuthSession): String {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
            val ciphertext = cipher.doFinal(ProtocolJson.encodeToString(session).encodeToByteArray())
            return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        }

        private fun decrypt(encoded: String): StoredAuthSession {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.size > IV_SIZE_BYTES)
            val iv = encrypted.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = encrypted.copyOfRange(IV_SIZE_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            return ProtocolJson.decodeFromString(cipher.doFinal(ciphertext).decodeToString())
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

        const val PREFERENCES_NAME = "fast_to_win_secure_session"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "fast_to_win_auth_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
