package com.hienthai.fastowin.server.avatars

import java.io.IOException
import kotlin.test.*

class AvatarStorageConfigurationTest {
    private val accessName = "FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID"
    private val secretName = "FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY"
    private val access = "a".repeat(32)
    private val secret = "b".repeat(64)
    private val base = mapOf(
        "FASTTOWIN_AVATAR_STORAGE" to "r2",
        "FASTTOWIN_AVATAR_R2_ACCOUNT_ID" to "c".repeat(32),
        "FASTTOWIN_AVATAR_R2_BUCKET" to "avatar-private"
    )

    @Test fun `file-only credentials load mounted secret values and trim line endings`() {
        val values = base + mapOf("${accessName}_FILE" to "mounted-access", "${secretName}_FILE" to "mounted-secret")
        val configuration = AvatarStorageConfiguration.fromEnvironment(values) { path ->
            when (path) {
                "mounted-access" -> "$access\r\n"
                "mounted-secret" -> "$secret\n"
                else -> error("Unexpected test path")
            }
        }
        val r2 = assertNotNull(configuration.r2)
        assertEquals(access, r2.accessKeyId)
        assertEquals(secret, r2.secretAccessKey)
        assertFalse(configuration.toString().contains(access))
        assertFalse(r2.toString().contains(secret))
    }

    @Test fun `each credential can independently mix direct and file sources during rollback`() {
        for (fileName in listOf(accessName, secretName)) {
            val directName = if (fileName == accessName) secretName else accessName
            val direct = if (directName == accessName) access else secret
            val fromFile = if (fileName == accessName) access else secret
            val values = base + mapOf("FASTTOWIN_AVATAR_STORAGE" to "database", directName to direct, "${fileName}_FILE" to "mounted-secret")
            val r2 = assertNotNull(AvatarStorageConfiguration.fromEnvironment(values) { fromFile }.r2)
            assertEquals(access, r2.accessKeyId)
            assertEquals(secret, r2.secretAccessKey)
        }
    }

    @Test fun `direct and file conflicts for either credential fail without exposing values or paths`() {
        for (name in listOf(accessName, secretName)) {
            val path = "private-mount-path-must-not-leak"
            val values = base + mapOf(accessName to access, secretName to secret, "${name}_FILE" to path)
            val failure = assertFailsWith<IllegalArgumentException> {
                AvatarStorageConfiguration.fromEnvironment(values) { error("Conflicting file must not be read") }
            }
            assertTrue(assertNotNull(failure.message).contains(name))
            assertTrue(assertNotNull(failure.message).contains("${name}_FILE"))
            for (sensitive in listOf(path, access, secret, "Conflicting file must not be read")) {
                assertFalse(failure.stackTraceToString().contains(sensitive))
            }
        }
    }

    @Test fun `unreadable credential files fail with redacted messages and no unsafe cause`() {
        for (name in listOf(accessName, secretName)) {
            val other = if (name == accessName) secretName else accessName
            val values = base + mapOf(other to if (other == accessName) access else secret,
                "${name}_FILE" to "sensitive-path")
            val failure = assertFailsWith<IllegalArgumentException> {
                AvatarStorageConfiguration.fromEnvironment(values) { throw IOException("sensitive-file-content") }
            }
            assertTrue(assertNotNull(failure.message).contains(name))
            assertTrue(assertNotNull(failure.message).contains("${name}_FILE"))
            assertFalse(failure.stackTraceToString().contains("sensitive-path"))
            assertFalse(failure.stackTraceToString().contains("sensitive-file-content"))
            assertNull(failure.cause)
        }
    }

    @Test fun `blank or malformed secret file contents still fail credential validation safely`() {
        for (contents in listOf("\r\n", "malformed-private-credential")) {
            val values = base + mapOf(accessName to access, "${secretName}_FILE" to "mounted-secret")
            val failure = assertFailsWith<IllegalArgumentException> {
                AvatarStorageConfiguration.fromEnvironment(values) { contents }
            }
            assertFalse(failure.stackTraceToString().contains("malformed-private-credential"))
            assertNull(failure.cause)
        }
    }
}
