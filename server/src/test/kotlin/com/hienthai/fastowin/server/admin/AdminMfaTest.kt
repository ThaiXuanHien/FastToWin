package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.InMemoryRateLimiter
import com.hienthai.fastowin.server.PasswordHasher
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.*

class AdminMfaTest {
    private class Fixture(required: Boolean = true) {
        var now = 1_800_000_000_000L
        val repository = InMemoryAdminRepository()
        val configuration = AdminConfiguration("http://localhost:8080", false, AdminMfaSettings(required, ByteArray(32) { it.toByte() }))
        val service = AdminService(repository, configuration, PasswordHasher(iterations = 1_000), { now }, InMemoryRateLimiter({ now }))
        val origin = configuration.origin

        suspend fun begin(email: String = "owner@example.com") = service.beginLogin(email, "GoodPassword123!", origin, "127.0.0.1")
        fun code(secret: String, offsetSteps: Long = 0) = Totp.code(decodeBase32(secret), Totp.step(now) + offsetSteps)

        /** Enrolls the owner and returns its Base32 secret plus the one-time recovery codes. */
        suspend fun enroll(): Pair<String, AdminMfaLogin> {
            val challenge = assertIs<AdminLoginOutcome.Challenge>(begin()).response
            assertEquals("ENROLL", challenge.mfa)
            val secret = assertNotNull(challenge.secret)
            return secret to service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, code = code(secret)), origin)
        }
    }

    @Test fun `RFC 6238 SHA1 test vectors`() {
        val secret = "12345678901234567890".toByteArray()
        // RFC 6238 appendix B lists 8 digits; the 6-digit codes are their last six digits.
        assertEquals("287082", Totp.code(secret, 59L / 30))
        assertEquals("081804", Totp.code(secret, 1111111109L / 30))
        assertEquals("050471", Totp.code(secret, 1111111111L / 30))
        assertEquals("005924", Totp.code(secret, 1234567890L / 30))
        assertEquals("279037", Totp.code(secret, 2000000000L / 30))
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Base32.encode(secret))
    }

    @Test fun `secrets are encrypted and bound to their admin`() {
        val cipher = AdminSecretCipher(ByteArray(32) { 7 })
        val sealed = cipher.encrypt("secret".toByteArray(), "admin-a")
        assertFalse(sealed.contains("secret"))
        assertContentEquals("secret".toByteArray(), cipher.decrypt(sealed, "admin-a"))
        assertFails { cipher.decrypt(sealed, "admin-b") }
        assertFails { AdminSecretCipher(ByteArray(32) { 8 }).decrypt(sealed, "admin-a") }
    }

    @Test fun `required MFA enrolls on first sign-in and returns recovery codes once`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        assertEquals(401, assertFailsWith<AdminException> { f.service.login("owner@example.com", "GoodPassword123!", f.origin, "127.0.0.1") }.status)

        val (_, login) = f.enroll()

        assertEquals(RecoveryCodes.COUNT, login.response.recoveryCodes.size)
        assertTrue(login.response.admin.mfaEnabled)
        assertEquals(AdminRole.OWNER, f.service.session(login.token).admin.role)
        val actions = f.service.audit(login.token, PageRequest(pageSize = 50)).items.map { it.action }
        assertTrue("ADMIN_MFA_ENROLL" in actions)
        assertFalse(f.service.audit(login.token, PageRequest(pageSize = 50)).items.toString().contains(login.response.recoveryCodes.first()))
    }

    @Test fun `enrolled accounts need a fresh code and a used code cannot be replayed`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val (secret, _) = f.enroll()

        val challenge = assertIs<AdminLoginOutcome.Challenge>(f.begin()).response
        assertEquals("VERIFY", challenge.mfa); assertNull(challenge.secret)
        // The enrollment code's step is spent.
        assertEquals("MFA_INVALID_CODE", assertFailsWith<AdminException> {
            f.service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, code = f.code(secret)), f.origin)
        }.code)
        f.now += 30_000
        val login = f.service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, code = f.code(secret)), f.origin)
        assertTrue(login.response.recoveryCodes.isEmpty())
        assertEquals("owner@example.com", f.service.session(login.token).admin.email)
    }

    @Test fun `five wrong codes end the challenge`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val (secret, _) = f.enroll(); f.now += 60_000
        val challenge = assertIs<AdminLoginOutcome.Challenge>(f.begin()).response
        repeat(4) {
            assertEquals("MFA_INVALID_CODE", assertFailsWith<AdminException> {
                f.service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, code = "000000"), f.origin)
            }.code)
        }
        assertEquals("MFA_CHALLENGE_EXPIRED", assertFailsWith<AdminException> {
            f.service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, code = "000000"), f.origin)
        }.code)
        assertEquals("MFA_CHALLENGE_EXPIRED", assertFailsWith<AdminException> {
            f.service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, code = f.code(secret)), f.origin)
        }.code)
    }

    @Test fun `challenges expire after five minutes`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val challenge = assertIs<AdminLoginOutcome.Challenge>(f.begin()).response
        f.now += AdminService.MFA_CHALLENGE_MILLIS
        assertEquals("MFA_CHALLENGE_EXPIRED", assertFailsWith<AdminException> {
            f.service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, code = f.code(challenge.secret!!)), f.origin)
        }.code)
    }

    @Test fun `each recovery code signs in once`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val (_, enrolled) = f.enroll()
        val recovery = enrolled.response.recoveryCodes.first()

        val first = assertIs<AdminLoginOutcome.Challenge>(f.begin()).response
        val login = f.service.completeMfa(AdminMfaVerifyRequest(first.challengeToken, recoveryCode = recovery.uppercase()), f.origin)
        assertEquals("owner@example.com", f.service.session(login.token).admin.email)

        val second = assertIs<AdminLoginOutcome.Challenge>(f.begin()).response
        assertEquals("MFA_INVALID_CODE", assertFailsWith<AdminException> {
            f.service.completeMfa(AdminMfaVerifyRequest(second.challengeToken, recoveryCode = recovery), f.origin)
        }.code)
    }

    @Test fun `recovery codes cannot complete enrollment`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val challenge = assertIs<AdminLoginOutcome.Challenge>(f.begin()).response
        assertFailsWith<AdminException> {
            f.service.completeMfa(AdminMfaVerifyRequest(challenge.challengeToken, recoveryCode = "abcde-fghjk"), f.origin)
        }
    }

    @Test fun `optional MFA keeps password sign-in until an admin enrolls`() = runTest {
        val f = Fixture(required = false); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        assertIs<AdminLoginOutcome.Session>(f.begin())
    }

    @Test fun `owners reset another admin's factor and end their sessions but not their own`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val (_, owner) = f.enroll()
        val support = f.service.createAccount(owner.token, owner.response.csrfToken, f.origin,
            CreateAdminRequest("support@example.com", "GoodPassword123!", AdminRole.SUPPORT, "Hỗ trợ", "create-support"))
        val supportChallenge = assertIs<AdminLoginOutcome.Challenge>(f.begin("support@example.com")).response
        f.now += 30_000
        val supportLogin = f.service.completeMfa(
            AdminMfaVerifyRequest(supportChallenge.challengeToken, code = f.code(supportChallenge.secret!!)), f.origin)

        val reset = f.service.resetMfa(owner.token, owner.response.csrfToken, f.origin, support.id, AdminMfaResetRequest("Mất điện thoại", "reset-1"))

        assertFalse(reset.mfaEnabled)
        assertEquals(401, assertFailsWith<AdminException> { f.service.session(supportLogin.token) }.status)
        assertEquals("ENROLL", assertIs<AdminLoginOutcome.Challenge>(f.begin("support@example.com")).response.mfa)
        assertEquals(403, assertFailsWith<AdminException> {
            f.service.resetMfa(owner.token, owner.response.csrfToken, f.origin, owner.response.admin.id, AdminMfaResetRequest("Tự đặt lại", "reset-2"))
        }.status)
    }

    @Test fun `offline recovery replaces the password and clears the factor`() = runTest {
        val f = Fixture(); f.service.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val (_, owner) = f.enroll()

        f.service.recoverOffline("owner@example.com", "NewGoodPassword456!", "Mất thiết bị xác thực")

        assertEquals(401, assertFailsWith<AdminException> { f.service.session(owner.token) }.status)
        assertEquals(401, assertFailsWith<AdminException> { f.service.beginLogin("owner@example.com", "GoodPassword123!", f.origin, "127.0.0.1") }.status)
        val challenge = assertIs<AdminLoginOutcome.Challenge>(f.service.beginLogin("owner@example.com", "NewGoodPassword456!", f.origin, "127.0.0.1"))
        assertEquals("ENROLL", challenge.response.mfa)
    }

    @Test fun `production requires a key and refuses to disable MFA`() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
        assertTrue(AdminMfaSettings.fromEnvironment("prod", mapOf("FASTTOWIN_ADMIN_MFA_KEY" to key)).required)
        assertFailsWith<IllegalArgumentException> { AdminMfaSettings.fromEnvironment("prod", emptyMap()) }
        assertFailsWith<IllegalArgumentException> {
            AdminMfaSettings.fromEnvironment("prod", mapOf("FASTTOWIN_ADMIN_MFA_KEY" to key, "FASTTOWIN_ADMIN_MFA_REQUIRED" to "false"))
        }
        assertFailsWith<IllegalArgumentException> { AdminMfaSettings.fromEnvironment("prod", mapOf("FASTTOWIN_ADMIN_MFA_KEY" to "c2hvcnQ=")) }
        assertFalse(AdminMfaSettings.fromEnvironment("dev", emptyMap()).required)
        assertTrue(AdminMfaSettings.fromEnvironment("dev", mapOf("FASTTOWIN_ADMIN_MFA_REQUIRED" to "true")).required)
    }

    private companion object {
        fun decodeBase32(value: String): ByteArray {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var buffer = 0; var bits = 0
            val out = mutableListOf<Byte>()
            for (c in value) {
                buffer = (buffer shl 5) or alphabet.indexOf(c); bits += 5
                if (bits >= 8) { out += ((buffer shr (bits - 8)) and 0xff).toByte(); bits -= 8 }
            }
            return out.toByteArray()
        }
    }
}
