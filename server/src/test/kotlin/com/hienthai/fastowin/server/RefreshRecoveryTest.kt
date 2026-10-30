package com.hienthai.fastowin.server

import kotlinx.coroutines.*
import java.util.Base64
import kotlin.test.*

class RefreshRecoveryTest {
    private fun success(result: AuthResult): AuthResult.Success {
        assertTrue(result is AuthResult.Success, "Expected refresh success")
        return result
    }
    private fun failure(result: AuthResult): AuthResult.Failure {
        assertTrue(result is AuthResult.Failure, "Expected generic auth failure")
        return result
    }
    private val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
    private val other = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 8 })
    private var now = 1_700_000_000_000L
    private val repository = InMemoryAuthRepository()
    private val service = AuthenticationService(repository, PasswordHasher(1), { now })
    private suspend fun register() = success(service.register("recovery@example.com", "password123", "Recovery", "android")).session

    @Test fun `lost response replays exactly with fixed expiry and no rotation`() = runBlocking<Unit> {
        val initial = register()
        val first = success(service.refresh(initial.refreshToken, secret)).session
        now += 299_999
        assertTrue(first == success(service.refresh(initial.refreshToken, secret)).session)
        assertNotNull(service.authenticateAccessToken(first.accessToken))
        now++
        failure(service.refresh(initial.refreshToken, secret))
        now++
        failure(service.refresh(initial.refreshToken, secret))
    }
    @Test fun `wrong secret and legacy old token cannot replay`() = runBlocking<Unit> {
        val initial = register()
        success(service.refresh(initial.refreshToken, secret))
        failure(service.refresh(initial.refreshToken, other))
        failure(service.refresh(initial.refreshToken))
    }
    @Test fun `invalid secret does not rotate`() = runBlocking<Unit> {
        val initial = register()
        for (invalid in listOf("", "x".repeat(42), "x".repeat(43), "x".repeat(44), secret + "=", "!".repeat(43), "x".repeat(5000))) {
            assertEquals("INVALID_REFRESH_ATTEMPT_SECRET", failure(service.refresh(initial.refreshToken, invalid)).code)
        }
        success(service.refresh(initial.refreshToken, secret))
    }
    @Test fun `pending logout revokes committed successor after recovery expiry`() = runBlocking<Unit> {
        val initial = register()
        val first = success(service.refresh(initial.refreshToken, secret)).session
        now += 300_001
        service.logout(initial.refreshToken, secret)
        assertNull(service.authenticateAccessToken(first.accessToken))
        failure(service.refresh(first.refreshToken))
        failure(service.refresh(initial.refreshToken, secret))
    }
    @Test fun `payload cleanup retains pending logout mapping but never permits replay`() = runBlocking<Unit> {
        val initial = register()
        val first = success(service.refresh(initial.refreshToken, secret)).session
        now += 300_000
        assertEquals(1, service.cleanupRefreshRecoveries())
        assertEquals(0, service.cleanupRefreshRecoveries())
        failure(service.refresh(initial.refreshToken, secret))
        service.logout(initial.refreshToken, other)
        assertNotNull(service.authenticateAccessToken(first.accessToken))
        service.logout(initial.refreshToken, secret)
        assertNull(service.authenticateAccessToken(first.accessToken))
    }
    @Test fun `pending logout before rotation revokes the source session`() = runBlocking<Unit> {
        val initial = register()
        service.logout(initial.refreshToken, secret)
        assertNull(service.authenticateAccessToken(initial.accessToken))
        failure(service.refresh(initial.refreshToken, secret))
    }
    @Test fun `password change reset delete and revoke one invalidate recovery`() = runBlocking<Unit> {
        for (action in listOf("password", "reset", "delete", "one")) {
            val email = "$action@example.com"
            val initial = success(service.register(email, "password123", "Recovery", "android")).session
            val first = success(service.refresh(initial.refreshToken, secret)).session
            when (action) {
                "password" -> assertIs<AccountActionResult.Success>(service.changePassword(first.accessToken, "password123", "new-password123"))
                "reset" -> {
                    val reset = assertIs<AccountActionResult.Success>(service.requestPasswordReset(email))
                    assertIs<AccountActionResult.Success>(service.resetPassword(email, assertNotNull(reset.resetToken), "new-password123"))
                }
                "delete" -> assertIs<AccountActionResult.Success>(service.deleteAccount(first.accessToken, "password123"))
                "one" -> {
                    val identity = assertNotNull(service.authenticateAccessToken(first.accessToken))
                    assertIs<AccountActionResult.Success>(service.revokeSession(first.accessToken, identity.sessionId.toString()))
                }
            }
            failure(service.refresh(initial.refreshToken, secret))
            assertNull(service.authenticateAccessToken(first.accessToken))
        }
    }
    @Test fun `next legacy rotation fences old replay and pending logout`() = runBlocking<Unit> {
        val initial = register()
        val first = success(service.refresh(initial.refreshToken, secret)).session
        val next = success(service.refresh(first.refreshToken)).session
        failure(service.refresh(initial.refreshToken, secret))
        service.logout(initial.refreshToken, secret)
        assertNotNull(service.authenticateAccessToken(next.accessToken))
    }
    @Test fun `concurrent duplicate attempts return one exact generation`() = runBlocking<Unit> {
        val initial = register()
        val results = coroutineScope { (1..12).map { async(Dispatchers.Default) { service.refresh(initial.refreshToken, secret) } }.awaitAll() }
        val first = success(results.first()).session
        results.forEach { assertTrue(first == success(it).session) }
    }
    @Test fun `replacement login and all revocation fence receipt`() = runBlocking<Unit> {
        val initial = register()
        val first = success(service.refresh(initial.refreshToken, secret)).session
        val login = success(service.login("recovery@example.com", "password123", "android")).session
        failure(service.refresh(initial.refreshToken, secret))
        service.logout(initial.refreshToken, secret)
        assertNotNull(service.authenticateAccessToken(login.accessToken))
        assertNull(service.authenticateAccessToken(first.accessToken))
        val rotated = success(service.refresh(login.refreshToken, secret)).session
        service.revokeAllSessions(rotated.accessToken)
        failure(service.refresh(login.refreshToken, secret))
    }
}
