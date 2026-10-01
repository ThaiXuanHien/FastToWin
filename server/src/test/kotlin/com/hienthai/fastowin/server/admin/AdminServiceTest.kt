package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.InMemoryRateLimiter
import kotlinx.coroutines.test.runTest
import kotlin.test.*

internal class AdminFixture {
    var now = 1_800_000_000_000L
    val hasher = PasswordHasher(iterations = 1_000)
    val repository = InMemoryAdminRepository()
    val configuration = AdminConfiguration("http://localhost:8080", false)
    val service = AdminService(repository, configuration, hasher, { now }, InMemoryRateLimiter({ now }))
    suspend fun owner() = service.bootstrapOwner("owner@example.com", "GoodPassword123!")
    suspend fun login(email: String = "owner@example.com", password: String = "GoodPassword123!") =
        service.login(email, password, configuration.origin, "127.0.0.1")
}

class AdminServiceTest {
    @Test fun `eight hour expiry denies session instead of accepting stale credentials`() = runTest {
        val f = AdminFixture(); f.owner(); val login = f.login()
        assertEquals(AdminRole.OWNER, f.service.session(login.token).admin.role)
        f.now += 8 * 60 * 60 * 1000
        assertEquals(401, assertFailsWith<AdminException> { f.service.session(login.token) }.status)
    }
    @Test fun `repeated request cannot duplicate grant and different payload conflicts`() = runTest {
        val f = AdminFixture(); f.owner(); val login = f.login()
        val request = CreateAdminRequest("support@example.com", "GoodPassword123!", AdminRole.SUPPORT, "Hỗ trợ", "grant-1")
        val first = f.service.createAccount(login.token, login.response.csrfToken, f.configuration.origin, request)
        assertEquals(first, f.service.createAccount(login.token, login.response.csrfToken, f.configuration.origin, request))
        assertEquals(409, assertFailsWith<AdminException> {
            f.service.createAccount(login.token, login.response.csrfToken, f.configuration.origin, request.copy(role = AdminRole.OWNER))
        }.status)
        val audit = f.service.audit(login.token, PageRequest()).items
        assertEquals(1, audit.count { it.action == "ADMIN_CREATE" })
        assertFalse(audit.toString().contains("GoodPassword")); assertFalse(audit.toString().contains(login.token))
    }
    @Test fun `creation deduplication stores only nonsecret fingerprint and verifies password replay safely`() = runTest {
        val request=CreateAdminRequest("support@example.com","GoodPassword123!",AdminRole.SUPPORT,"Hỗ trợ","safe-replay")
        suspend fun create(f: AdminFixture, password: String): Pair<AdminLogin,AdminIdentity> {
            f.owner(); val login=f.login()
            return login to f.service.createAccount(login.token,login.response.csrfToken,f.configuration.origin,request.copy(password=password))
        }
        val first=AdminFixture(); val (login,identity)=create(first,request.password)
        val stored=first.repository.transaction { deduplication(login.response.admin.id,request.requestId) }!!
        // This digest is derived solely from create/email/role/reason. Password changes cannot change its bytes.
        assertEquals("17973d02c8a96af49ac3d988e55090f5e7f3cab3adea4cc4c8394baca6b18cb3",stored.fingerprint)
        val second=AdminFixture(); val (otherLogin,_)=create(second,"DifferentPassword!")
        assertEquals(stored.fingerprint,second.repository.transaction { deduplication(otherLogin.response.admin.id,request.requestId) }!!.fingerprint)
        assertEquals(identity,first.service.createAccount(login.token,login.response.csrfToken,first.configuration.origin,request))
        for (changed in listOf(request.copy(password="DifferentPassword!"),request.copy(role=AdminRole.OWNER),request.copy(reason="Khác"),request.copy(email="other@example.com"))) {
            assertEquals(409,assertFailsWith<AdminException> {
                first.service.createAccount(login.token,login.response.csrfToken,first.configuration.origin,changed)
            }.status)
        }
        // A later role change does not replace the original successful result snapshot on retry.
        first.service.changeAccess(login.token,login.response.csrfToken,first.configuration.origin,identity.id,
            ChangeAdminAccessRequest(AdminRole.OPERATOR,true,"Vận hành","change-after-create"))
        assertEquals(identity,first.service.createAccount(login.token,login.response.csrfToken,first.configuration.origin,request))
    }
    @Test fun `revocation is immediate and last owner cannot be disabled`() = runTest {
        val f = AdminFixture(); val owner = f.owner(); val login = f.login()
        assertEquals(409, assertFailsWith<AdminException> {
            f.service.changeAccess(login.token, login.response.csrfToken, f.configuration.origin, owner.id,
                ChangeAdminAccessRequest(AdminRole.SUPPORT, false, "Thu hồi", "revoke-owner"))
        }.status)
        val support = f.service.createAccount(login.token, login.response.csrfToken, f.configuration.origin,
            CreateAdminRequest("support@example.com", "GoodPassword123!", AdminRole.SUPPORT, "Hỗ trợ", "support"))
        val session = f.login(support.email)
        f.service.changeAccess(login.token, login.response.csrfToken, f.configuration.origin, support.id,
            ChangeAdminAccessRequest(AdminRole.SUPPORT, false, "Thu hồi", "revoke-support"))
        assertEquals(401, assertFailsWith<AdminException> { f.service.session(session.token) }.status)
    }
    @Test fun `support cannot query email or grant access and masks directory email`() = runTest {
        val f = AdminFixture(); f.owner(); val owner = f.login()
        f.repository.seedUser(UserDetail("11111111-1111-1111-1111-111111111111", "Player", "player@example.com", "REGISTERED", "ACTIVE", 1, 1, 0, 0, 0, 1000, null, null, null))
        f.service.createAccount(owner.token, owner.response.csrfToken, f.configuration.origin,
            CreateAdminRequest("support@example.com", "GoodPassword123!", AdminRole.SUPPORT, "Hỗ trợ", "support"))
        val support = f.login("support@example.com")
        assertEquals("p***@e***.com", f.service.users(support.token, "Player", PageRequest()).items.single().email)
        assertEquals(403, assertFailsWith<AdminException> { f.service.users(support.token, "player@example.com", PageRequest()) }.status)
        assertEquals(403, assertFailsWith<AdminException> { f.service.accounts(support.token, PageRequest()) }.status)
        assertEquals(403, assertFailsWith<AdminException> {
            f.service.createAccount(support.token, support.response.csrfToken, f.configuration.origin,
                CreateAdminRequest("evil@example.com", "GoodPassword123!", AdminRole.OWNER, "X", "evil"))
        }.status)
        assertFalse(f.service.audit(owner.token, PageRequest()).items.toString().contains("player@example.com"))
    }
    @Test fun `wrong password and unknown account have same failure and bounded limiter`() = runTest {
        val f = AdminFixture(); f.owner()
        val wrong = assertFailsWith<AdminException> { f.login(password = "WrongPassword!") }
        val unknown = assertFailsWith<AdminException> { f.login("unknown@example.com", "WrongPassword!") }
        assertEquals(wrong.code, unknown.code); assertEquals(wrong.message, unknown.message)
        repeat(7) { assertEquals(401, assertFailsWith<AdminException> { f.login(password = "WrongPassword!") }.status) }
        assertEquals(429, assertFailsWith<AdminException> { f.login() }.status)
    }
    @Test fun `unavailable player storage reports unavailable instead of empty results`() = runTest {
        val f = AdminFixture(); f.owner(); val login = f.login()
        assertEquals(503, assertFailsWith<AdminException> { f.service.users(login.token, "", PageRequest()) }.status)
        assertFailsWith<AdminException> { PageRequest(pageSize = 101) }; assertFailsWith<AdminException> { PageRequest(page = -1) }
    }
    @Test fun `operator cannot manage accounts and another sessions csrf cannot revoke`() = runTest {
        val f = AdminFixture(); f.owner(); val owner = f.login(); val otherOwnerSession = f.login()
        f.service.createAccount(owner.token,owner.response.csrfToken,f.configuration.origin,
            CreateAdminRequest("operator@example.com","GoodPassword123!",AdminRole.OPERATOR,"Vận hành","operator"))
        val operator = f.login("operator@example.com")
        assertEquals(403,assertFailsWith<AdminException> { f.service.accounts(operator.token,PageRequest()) }.status)
        assertEquals(403,assertFailsWith<AdminException> { f.service.audit(operator.token,PageRequest()) }.status)
        assertEquals(403,assertFailsWith<AdminException> {
            f.service.changeAccess(operator.token,operator.response.csrfToken,f.configuration.origin,owner.response.admin.id,
                ChangeAdminAccessRequest(AdminRole.OPERATOR,true,"Sai quyền","op-mutate"))
        }.status)
        assertEquals(403,assertFailsWith<AdminException> { f.service.logout(owner.token,otherOwnerSession.response.csrfToken,f.configuration.origin) }.status)
        assertEquals(403,assertFailsWith<AdminException> { f.service.logout(owner.token,owner.response.csrfToken,"http://localhost:8081") }.status)
        assertEquals(owner.response.csrfToken, f.service.session(owner.token).csrfToken)
    }
    @Test fun `bootstrap is idempotent but cannot replace owner credential or grant an existing non owner`() = runTest {
        val f=AdminFixture(); val first=f.owner()
        assertEquals(first,f.service.bootstrapOwner("owner@example.com","DifferentPassword!"))
        assertEquals(AdminRole.OWNER,f.login().response.admin.role)
        assertEquals(401,assertFailsWith<AdminException> { f.login(password="DifferentPassword!") }.status)
        assertEquals(409,assertFailsWith<AdminException> { f.service.bootstrapOwner("other@example.com","GoodPassword123!") }.status)
    }
    @Test fun `only hashed session credentials are persisted and secrets stay out of safe DTOs`() = runTest {
        val f=AdminFixture(); f.owner(); val login=f.login()
        val stored = f.repository.transaction { session(com.hienthai.fastowin.server.hashToken(login.token)) }!!
        assertNotEquals(login.token, stored.tokenHash); assertNotEquals(login.response.csrfToken,stored.csrfHash)
        assertEquals(64,stored.tokenHash.length); assertEquals(64,stored.csrfHash.length)
        assertFalse(login.response.toString().contains(login.token))
    }
    @Test fun `directory and histories honor pagination and actual gift sources`() = runTest {
        val f=AdminFixture(); f.owner(); val owner=f.login()
        val id="11111111-1111-1111-1111-111111111111"
        f.repository.seedUser(UserDetail(id,"Alpha","alpha@example.com","REGISTERED","ACTIVE",1,1,0,0,0,1000,null,null,null),
            listOf(MatchEntry("m1","CASUAL","WIN",12,1),MatchEntry("m2","RANKED","LOSS",8,2)),
            listOf(WalletEntry("w1","ADMIN_GIFT","g1",5,0,0,1),WalletEntry("w2","COMPENSATION","g2",7,0,0,2),WalletEntry("w3","CHECK_IN","g3",10,0,0,3)))
        assertEquals("m1",f.service.matches(owner.token,id,PageRequest(1,1)).items.single().id)
        assertEquals(3,f.service.wallet(owner.token,id,false,PageRequest(pageSize=1)).total)
        assertEquals(setOf("w1","w2"),f.service.wallet(owner.token,id,true,PageRequest()).items.map { it.id }.toSet())
        assertEquals(404,assertFailsWith<AdminException> { f.service.user(owner.token,"22222222-2222-2222-2222-222222222222") }.status)
    }
    @Test fun `enabled configuration rejects non origin and insecure production`() {
        assertNull(AdminConfiguration.fromEnvironment("prod", emptyMap()))
        for (origin in listOf("http://example.com", "https://example.com/", "https://example.com?q=x", "https://user@example.com")) {
            assertFailsWith<IllegalArgumentException> { AdminConfiguration.fromEnvironment("prod", mapOf("FASTTOWIN_ADMIN_ENABLED" to "true", "FASTTOWIN_ADMIN_ORIGIN" to origin)) }
        }
    }
}
