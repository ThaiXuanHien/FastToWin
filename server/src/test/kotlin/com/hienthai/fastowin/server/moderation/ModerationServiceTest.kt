package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class ModerationServiceTest {
    private class Fixture {
        var now = 1_800_000_000_000L
        val reporter = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()
        val admins = InMemoryAdminRepository()
        val adminService = AdminService(admins, AdminConfiguration("http://localhost:8080", false), PasswordHasher(iterations = 1_000), { now })
        val repo = InMemoryModerationRepository(admins)
        val service = ModerationService(repo, adminService, { true }, { now })
        lateinit var context: ModerationAdminContext
        suspend fun setup() {
            adminService.bootstrapOwner("owner@example.com", "GoodPassword123!")
            val login = adminService.login("owner@example.com", "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
            context = ModerationAdminContext(login.token, login.response.csrfToken, "http://localhost:8080")
            admins.seedUser(UserDetail(reporter, "Reporter", null, "GUEST", "ACTIVE", now, 1,0,17,0,1000,null,null,null))
            repo.transaction { putTarget(UgcTarget(UgcTargetType.PLAYER_PROFILE, targetId, targetId, "Offensive", "revision-1")) }
        }
        fun request(key: String = UUID.randomUUID().toString(), target: String = targetId) = ContentReportRequest(UgcTargetType.PLAYER_PROFILE, target, UgcReportReason.HARASSMENT, "context", key)
        suspend fun report() = service.report(reporter, request())
    }

    @Test fun `disabled rollout accepts missing version but rejects explicit unsupported consent`() = runTest {
        val f=Fixture();f.setup()
        val service=ModerationService(f.repo,f.adminService,{false},{f.now})
        service.validateAcceptedVersion(null)
        assertEquals("CONTENT_TERMS_VERSION_INVALID",assertFailsWith<AdminException>{service.validateAcceptedVersion("future")}.code)
    }
    @Test fun `action reasons stay with retained reports and disappear on account erasure`() = runTest {
        val f=Fixture();f.setup();val id=f.report().reportId
        f.service.resolve(f.context,id,ModerationResolveRequest(1,"close","Private investigation detail",ModerationStatus.DISMISSED))
        assertEquals("Private investigation detail",f.service.detail(f.context,id).lastActionReason)
        f.service.erasePlayer(f.reporter)
        assertNull(f.service.detail(f.context,id).lastActionReason)
    }
    @Test fun `failed transaction does not publish quota report or terms`() = runTest {
        val f=Fixture();f.setup()
        assertFailsWith<IllegalStateException> {f.repo.transaction {acceptTerms(f.reporter,CONTENT_TERMS_VERSION,f.now);addRateEvent(f.reporter,f.now);error("rollback")}}
        assertNull(f.service.terms(f.reporter).acceptedVersion)
        assertEquals(0,f.repo.transaction {rateCount(f.reporter,0)})
    }
    @Test fun `demoted administrator cannot replay an earlier removal`() = runTest {
        val f=Fixture();f.setup();val id=f.report().reportId
        val request=ModerationResolveRequest(1,"remove","Confirmed violation",ModerationStatus.RESOLVED,true)
        f.service.resolve(f.context,id,request)
        f.adminService.createAccount(f.context.token,f.context.csrfToken,f.context.origin,
            CreateAdminRequest("second@example.com","GoodPassword123!",AdminRole.OWNER,"Second owner","second"))
        val second=f.adminService.login("second@example.com","GoodPassword123!",f.context.origin,"127.0.0.1")
        val actor=f.adminService.session(f.context.token).admin.id
        f.adminService.changeAccess(second.token,second.response.csrfToken,f.context.origin,actor,
            ChangeAdminAccessRequest(AdminRole.SUPPORT,true,"Revoke access","demote"))
        assertEquals(401,assertFailsWith<AdminException>{f.service.resolve(f.context,id,request)}.status)
    }
    @Test fun `open report retries remain idempotent throughout an investigation longer than ninety days`() = runTest {
        val f=Fixture();f.setup();val request=f.request("long-investigation")
        val receipt=f.service.report(f.reporter,request)
        f.now+=100L*86_400_000;f.service.cleanup()
        assertEquals(receipt,f.service.report(f.reporter,request))
        assertEquals(409,assertFailsWith<AdminException>{f.service.report(f.reporter,request.copy(note="Changed"))}.status)
    }
    @Test fun `new request IDs for duplicate reports consume bounded quota while exact retries remain free`() = runTest {
        val f=Fixture();f.setup();val request=f.request("spam-0")
        val first=f.service.report(f.reporter,request)
        repeat(4) {assertEquals(first,f.service.report(f.reporter,request))}
        repeat(4) {index->assertEquals(first.reportId,f.service.report(f.reporter,request.copy(requestId="spam-${index+1}")).reportId)}
        assertEquals(429,assertFailsWith<AdminException>{f.service.report(f.reporter,request.copy(requestId="spam-5"))}.status)
        assertEquals(1L,f.repo.transaction {reportCount(null)})
    }
    @Test fun `deleted targets cannot be resolved using old published memory snapshots`()=runTest {
        val f=Fixture();f.setup();val id=f.report().reportId
        f.repo.transaction {removeTarget(UgcTargetType.PLAYER_PROFILE,f.targetId)}
        assertTrue(f.service.detail(f.context,id).targetMissing)
        assertEquals(409,assertFailsWith<AdminException>{f.service.resolve(f.context,id,ModerationResolveRequest(1,"remove-deleted","Gone",ModerationStatus.RESOLVED,true))}.status)
    }
    @Test fun `retry preserves report ID and rejects changed payload`() = runTest {
        val f=Fixture(); f.setup(); val request=f.request("retry")
        val first=f.service.report(f.reporter,request)
        assertEquals(first.reportId,f.service.report(f.reporter,request).reportId)
        assertEquals(409, assertFailsWith<AdminException> { f.service.report(f.reporter,request.copy(note="changed")) }.status)
        assertEquals(first.reportId, f.service.report(f.reporter,f.request("other-key")).reportId)
        assertEquals(1L,f.repo.transaction { reportCount(null) })
    }
    @Test fun `sixth new request in ten minutes is rejected even when targeting an open report`() = runTest {
        val f=Fixture(); f.setup()
        repeat(5) { index ->
            val id=UUID.randomUUID().toString()
            f.repo.transaction { putTarget(UgcTarget(UgcTargetType.PLAYER_PROFILE,id,id,"Bad $index","r$index")) }
            f.service.report(f.reporter,f.request("request-$index",id))
        }
        val first=f.repo.transaction {reports(null,100,0).first()}
        assertEquals(429,assertFailsWith<AdminException>{f.service.report(f.reporter,f.request("duplicate",first.targetId!!))}.status)
        assertEquals(5,f.repo.transaction {rateCount(f.reporter,f.now-600_000)})
        assertEquals(429,assertFailsWith<AdminException> { f.report() }.status)
        f.now+=600_001
        f.report()
    }
    @Test fun `twenty first report in rolling day is rejected`() = runTest {
        val f=Fixture(); f.setup()
        repeat(20) { index ->
            if(index%5==0) f.now+=600_001
            val id=UUID.randomUUID().toString()
            f.repo.transaction { putTarget(UgcTarget(UgcTargetType.PLAYER_PROFILE,id,id,"Bad","r")) }
            f.service.report(f.reporter,f.request("r-$index",id))
        }
        f.now+=600_001
        assertEquals(429,assertFailsWith<AdminException>{ f.report() }.status)
    }
    @Test fun `missing target and forged reporter are rejected`() = runTest {
        val f=Fixture();f.setup()
        assertEquals(404,assertFailsWith<AdminException>{ f.service.report(f.reporter,f.request(target=UUID.randomUUID().toString())) }.status)
        assertEquals(401,assertFailsWith<AdminException>{ f.service.report(UUID.randomUUID().toString(),f.request()) }.status)
    }
    @Test fun `current terms are required only for publication and acceptance rejects stale version`() = runTest {
        val f=Fixture();f.setup()
        assertEquals("CONTENT_TERMS_REQUIRED",assertFailsWith<AdminException>{f.service.requireContentTerms(f.reporter)}.code)
        assertNull(f.service.terms(f.reporter).acceptedVersion)
        assertEquals(400,assertFailsWith<AdminException>{f.service.acceptTerms(f.reporter,"old")}.status)
        f.service.acceptTerms(f.reporter,CONTENT_TERMS_VERSION)
        f.service.requireContentTerms(f.reporter)
        assertEquals(CONTENT_TERMS_VERSION,f.service.terms(f.reporter).acceptedVersion)
    }
    @Test fun `stale content revision cannot be suppressed and stale admin versions cannot resolve`() = runTest {
        val f=Fixture();f.setup();val id=f.report().reportId
        f.repo.transaction { putTarget(UgcTarget(UgcTargetType.PLAYER_PROFILE,f.targetId,f.targetId,"New","revision-2")) }
        assertTrue(f.service.detail(f.context,id).targetChanged)
        assertEquals(409,assertFailsWith<AdminException>{ f.service.resolve(f.context,id,ModerationResolveRequest(1,"remove","Violation",ModerationStatus.RESOLVED,true)) }.status)
        assertFalse(f.service.isSuppressed(UgcTargetType.PLAYER_PROFILE,f.targetId))
        f.service.resolve(f.context,id,ModerationResolveRequest(1,"review","Investigate",ModerationStatus.REVIEWING))
        assertEquals(409,assertFailsWith<AdminException>{ f.service.resolve(f.context,id,ModerationResolveRequest(1,"stale","Reviewed",ModerationStatus.DISMISSED)) }.status)
    }
    @Test fun `support may triage but cannot remove or restrict and origin csrf are required`() = runTest {
        val f=Fixture();f.setup();val id=f.report().reportId
        f.adminService.createAccount(f.context.token,f.context.csrfToken,f.context.origin,CreateAdminRequest("support@example.com","GoodPassword123!",AdminRole.SUPPORT,"Support","support-create"))
        val login=f.adminService.login("support@example.com","GoodPassword123!",f.context.origin,"127.0.0.1")
        val support=ModerationAdminContext(login.token,login.response.csrfToken,f.context.origin)
        assertEquals(403,assertFailsWith<AdminException>{f.service.resolve(support,id,ModerationResolveRequest(1,"remove","Violation",ModerationStatus.RESOLVED,true))}.status)
        assertEquals(403,assertFailsWith<AdminException>{f.service.restrict(support,f.reporter,ModerationRestrictionRequest(0,"block","Violation",true))}.status)
        assertEquals(403,assertFailsWith<AdminException>{f.service.resolve(support,id,ModerationResolveRequest(1,"close-support","Close",ModerationStatus.DISMISSED))}.status)
        assertEquals(403,assertFailsWith<AdminException>{f.service.resolve(f.context.copy(origin="https://evil.test"),id,ModerationResolveRequest(1,"origin","Review",ModerationStatus.REVIEWING))}.status)
        assertEquals(403,assertFailsWith<AdminException>{f.service.resolve(f.context.copy(csrfToken="wrong"),id,ModerationResolveRequest(1,"csrf","Review",ModerationStatus.REVIEWING))}.status)
        assertEquals(ModerationStatus.REVIEWING,f.service.resolve(support,id,ModerationResolveRequest(1,"review","Review",ModerationStatus.REVIEWING)).report.status)
    }
    @Test fun `closed reports purge after ninety days while active report remains and deletion scrubs evidence`() = runTest {
        val f=Fixture();f.setup();val id=f.report().reportId
        f.service.resolve(f.context,id,ModerationResolveRequest(1,"close","Reviewed",ModerationStatus.DISMISSED))
        val active=f.report().reportId
        f.service.erasePlayer(f.reporter)
        f.repo.transaction { assertNull(report(active)!!.reporterId); assertEquals("",report(active)!!.note); assertEquals("",report(active)!!.evidenceText) }
        f.now+=90L*86_400_000+1
        f.service.cleanup()
        f.repo.transaction { assertNull(report(id)); assertNotNull(report(active)) }
    }
    @Test fun `removal suppresses exact revision and audit excludes raw reason note and player ID`() = runTest {
        val f=Fixture();f.setup();val id=f.report().reportId
        val changed=f.service.resolve(f.context,id,ModerationResolveRequest(1,"remove","Sensitive reason",ModerationStatus.RESOLVED,true))
        assertEquals(ModerationStatus.RESOLVED,changed.report.status)
        assertTrue(f.service.isSuppressed(UgcTargetType.PLAYER_PROFILE,f.targetId,"revision-1"))
        assertFalse(f.service.isSuppressed(UgcTargetType.PLAYER_PROFILE,f.targetId,"revision-2"))
        val audit=f.admins.transaction { audit(PageRequest()).items }
        val entry=audit.single{it.action=="MODERATION_REMOVE"}
        assertNull(entry.reason);assertNull(entry.targetId);assertNull(entry.requestId)
        val request=ModerationRestrictionRequest(0,"block","Repeat violation",true)
        assertTrue(f.service.restrict(f.context,f.reporter,request).blocked)
        assertTrue(f.service.isBlocked(f.reporter))
        assertEquals(17,f.admins.transaction {user(f.reporter)!!.gold})
        assertEquals(1,f.service.restrict(f.context,f.reporter,request).version)
    }
}
