package com.hienthai.fastowin.server

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal data class PasswordHash(val salt: ByteArray, val value: ByteArray) {
    fun matches(password: String): Boolean = MessageDigest.isEqual(value, derive(password, salt))

    companion object {
        private val random = SecureRandom()

        fun create(password: String): PasswordHash {
            val salt = ByteArray(16).also(random::nextBytes)
            return PasswordHash(salt, derive(password, salt))
        }

        private fun derive(password: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }
    }
}
