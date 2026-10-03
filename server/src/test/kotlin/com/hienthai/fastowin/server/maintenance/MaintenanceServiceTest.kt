package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class MaintenanceServiceTest {
    @Test fun drainingKeepsCurrentMatchOnly() {
        val policy = MaintenancePolicy(MaintenanceState.DRAINING)
        assertTrue(policy.blocksNewIntent); assertTrue(policy.allowsExistingMatch); assertFalse(policy.isFullyClosed)
    }
    @Test fun emergencyClosesGame() {
        val policy = MaintenancePolicy(MaintenanceState.MAINTENANCE)
        assertTrue(policy.blocksNewIntent); assertFalse(policy.allowsExistingMatch); assertTrue(policy.isFullyClosed)
    }
    @Test fun unavailableStartupNeverServesGame() = runTest {
        val service = MaintenanceService(UnavailableMaintenanceRepository)
        assertTrue(service.effectivePolicy().blocksNewIntent)
        assertFalse(service.effectivePolicy().allowsExistingMatch)
        assertNull(service.effectivePolicy().state)
        error(503) { service.publicStatus() }
        error(503) { service.refresh(0) }
    }
    @Test fun ownerReplayRequiresCurrentRoleSessionCsrfAndOrigin() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        for (ctx in listOf(f.operator, f.support, f.owner.copy(csrfToken=null), f.owner.copy(origin="http://localhost:3000"))) {
            error(403) { f.service.update(ctx,f.request()) }
        }
        error(401) { f.service.update(f.owner.copy(token=null),f.request()) }
        val request = f.request(); val result = f.service.update(f.owner,request)
        assertEquals(1,result.persisted.version)
        assertEquals(result,f.service.update(f.owner,request))
        error(409) { f.service.update(f.owner,request.copy(messageEn="Changed")) }
        error(409) { f.service.update(f.owner,f.request()) }
        f.admin.logout(f.owner.token,f.owner.csrfToken,f.owner.origin)
        error(401) { f.service.update(f.owner,request) }
    }
    @Test fun operatorSupportReadAndPublicStatusMaskPrivateFields() = runTest {
        val f=MaintenanceFixture(); f.initialize(); f.service.update(f.owner,f.request())
        assertEquals("Internal reason",f.service.read(f.operator).persisted.reason)
        assertEquals(MaintenanceState.ANNOUNCING,f.service.read(f.support).persisted.state)
        f.service.refresh(1)
        val body=Json.encodeToString(f.service.publicStatus())
        assertFalse(body.contains("Internal reason")); assertFalse(body.contains("actor")); assertFalse(body.contains("owner@example"))
        assertFalse(f.service.publicStatus().maintenance)
        assertEquals("Maintenance",f.service.publicStatus().messageEn)
    }
    @Test fun plannedDeadlinesSurviveServiceRestartAndNeverAutomaticallyReopen() = runTest {
        val f=MaintenanceFixture(); f.initialize()
        val created=f.service.update(f.owner,f.request().copy(warningMinutes=1,drainMinutes=2,expectedEndEpochMillis=f.now+1))
        assertEquals(60_000+f.now,created.persisted.transitionAtEpochMillis)
        f.service=MaintenanceService(f.repository,f.admin,{ f.now })
        f.now+=60_000; assertEquals(MaintenanceState.DRAINING,f.service.refresh(1).state)
        assertEquals(180_000L+MaintenanceFixture.START,f.service.publicStatus().transitionAtEpochMillis)
        f.now+=120_000; assertEquals(MaintenanceState.MAINTENANCE,f.service.refresh(1).state)
        f.now+=1_000_000; assertEquals(MaintenanceState.MAINTENANCE,f.service.refresh(0).state)
        assertTrue(f.service.publicStatus().maintenance)
        val snapshot=f.service.read(f.owner).persisted
        assertEquals(3,snapshot.version); assertNotNull(snapshot.actorId)
    }
    @Test fun zeroWarningEmptyDrainAndEmergencyClose() = runTest {
        val f=MaintenanceFixture(); f.initialize()
        val first=f.service.update(f.owner,f.request().copy(warningMinutes=0))
        assertEquals(MaintenanceState.DRAINING,first.persisted.state)
        assertEquals(MaintenanceState.MAINTENANCE,f.service.refresh(0).state)
        val disabled=f.service.update(f.owner,f.request().copy(expectedVersion=2,enabled=false))
        assertEquals(MaintenanceState.ACTIVE,disabled.persisted.state)
        val emergency=f.service.update(f.owner,f.request().copy(expectedVersion=3,mode=MaintenanceMode.EMERGENCY))
        assertEquals(MaintenanceState.MAINTENANCE,emergency.persisted.state)
    }
    @Test fun overrideCannotBeDisabledAndDoesNotOverwriteDatabase() = runTest {
        val f=MaintenanceFixture(); f.initialize()
        f.service=MaintenanceService(f.repository,f.admin,{ f.now },environmentOverride=true)
        assertEquals(MaintenanceSource.ENVIRONMENT,f.service.effectivePolicy().source)
        error(409) { f.service.update(f.owner,f.request().copy(enabled=false)) }
        val result=f.service.read(f.owner)
        assertEquals(MaintenanceState.ACTIVE,result.persisted.state)
        assertEquals(MaintenanceState.MAINTENANCE,result.effective.state)
    }
    @Test fun invalidPayloadDoesNotPersistOrAuditAndTextIsBounded() = runTest {
        val f=MaintenanceFixture(); f.initialize()
        val good=f.request()
        for (request in listOf(good.copy(reason=""),good.copy(reason="a\nb"),good.copy(reason="a".repeat(501)),
            good.copy(requestId=" x"),good.copy(expectedVersion=-1),good.copy(mode=null),good.copy(cause="SQL"),
            good.copy(messageVi=""),good.copy(messageEn="a".repeat(1001)),good.copy(messageEn="x\u0000"),
            good.copy(warningMinutes=-1),good.copy(warningMinutes=61),good.copy(drainMinutes=0),good.copy(drainMinutes=61),
            good.copy(expectedEndEpochMillis=-1),good.copy(expectedEndEpochMillis=Long.MAX_VALUE))) {
            error(400) { f.service.update(f.owner,request) }
        }
        assertEquals(0,f.service.read(f.owner).persisted.version)
        val result=f.service.update(f.owner,good.copy(messageEn="First\nSecond"))
        assertEquals("First\nSecond",result.persisted.messageEn)
    }
    @Test fun policyReadFailureBlocksNewIntentWhileRetainingKnownExistingMatchPolicy() = runTest {
        val f=MaintenanceFixture(); f.initialize(); f.service.refresh(1)
        val failing=object : MaintenanceRepository {
            override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T { unavailable() }
        }
        val service=MaintenanceService(failing,f.admin)
        error(503) { service.refresh(1) }
        assertTrue(service.effectivePolicy().blocksNewIntent)
        assertEquals(MaintenanceSource.UNAVAILABLE,service.effectivePolicy().source)
    }
    @Test fun cancellationPropagates() = runTest {
        val repo=object : MaintenanceRepository {
            override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T { throw CancellationException("cancel") }
        }
        assertFailsWith<CancellationException> { MaintenanceService(repo).refresh(0) }
    }
    @Test fun enabledEditsPreserveDeadlineAndRequireExplicitDisableForRescheduleOrModeDowngrade() = runTest {
        val f=MaintenanceFixture(); f.initialize(); val first=f.service.update(f.owner,f.request())
        f.now+=1000
        val edit=f.request().copy(expectedVersion=1,messageEn="Edited")
        val updated=f.service.update(f.owner,edit)
        assertEquals(first.persisted.transitionAtEpochMillis,updated.persisted.transitionAtEpochMillis)
        assertEquals(first.persisted.startedAtEpochMillis,updated.persisted.startedAtEpochMillis)
        error(409) { f.service.update(f.owner,f.request().copy(expectedVersion=2,warningMinutes=6)) }
        val emergency=f.service.update(f.owner,f.request().copy(expectedVersion=2,mode=MaintenanceMode.EMERGENCY))
        assertEquals(MaintenanceState.MAINTENANCE,emergency.persisted.state)
        error(409) { f.service.update(f.owner,f.request().copy(expectedVersion=3)) }
    }
    @Test fun auditFailureRollsBackMemorySnapshotAndReplay() = runTest {
        val f=MaintenanceFixture(); f.initialize(); f.repository.failAudit=true
        val request=f.request(); error(503) { f.service.update(f.owner,request) }
        f.repository.failAudit=false
        assertEquals(0,f.service.read(f.owner).persisted.version)
        assertEquals(1,f.service.update(f.owner,request).persisted.version)
    }
    @Test fun checkedTimestampOverflowRejectsWithoutWrite() = runTest {
        val f=MaintenanceFixture(); f.initialize(); f.now=253402300799998L
        // Existing session expiry is earlier: issue a fresh session at this clock first.
        f.owner=f.login("owner@example.com")
        error(400) { f.service.update(f.owner,f.request()) }
    }
    @Test fun staleUnavailabilityRetainsKnownPolicyAndRecoversOnSuccessfulRefresh() = runTest {
        val f=MaintenanceFixture(); f.initialize()
        var failing=false
        val repository=object:MaintenanceRepository {
            override suspend fun <T> transaction(block:MaintenanceTransaction.()->T):T {
                if(failing) throw java.sql.SQLException("secret SQL")
                return f.repository.transaction(block)
            }
        }
        val service=MaintenanceService(repository,f.admin,{ f.now })
        assertEquals(MaintenanceState.ACTIVE,service.refresh(1).state)
        failing=true; error(503) { service.refresh(1) }
        assertTrue(service.effectivePolicy().blocksNewIntent); assertTrue(service.effectivePolicy().allowsExistingMatch)
        assertEquals(MaintenanceState.ACTIVE,service.effectivePolicy().state)
        error(503) { service.publicStatus() }
        failing=false; assertFalse(service.refresh(1).blocksNewIntent)
    }
    @Test fun updateNeverClaimsAppliedAndRefreshRejectsNegativeCounts() = runTest {
        val f=MaintenanceFixture(); f.initialize()
        f.service.refresh(1)
        val result=f.service.update(f.owner,f.request().copy(mode=MaintenanceMode.EMERGENCY))
        assertEquals(1,result.persisted.version)
        assertEquals(0,result.runtime.version)
        assertEquals(MaintenanceState.ACTIVE,result.effective.state)
        error(400) { f.service.refresh(-1) }
        assertEquals(MaintenanceState.MAINTENANCE,f.service.refresh(1).state)
        assertEquals(1,f.service.read(f.owner).runtime.version)
    }
}

internal suspend fun error(status: Int, block: suspend () -> Unit) {
    assertEquals(status,assertFailsWith<AdminException> { block() }.status)
}
internal class MaintenanceFixture {
    companion object { const val START=1_800_000_000_000L }
    var now=START
    val admins=InMemoryAdminRepository()
    val admin=AdminService(admins,AdminConfiguration("http://localhost:8080",false),PasswordHasher(1000),{ now })
    val repository=MemoryMaintenanceRepository(admins)
    var service=MaintenanceService(repository,admin,{ now })
    lateinit var owner: MaintenanceAdminContext
    lateinit var operator: MaintenanceAdminContext
    lateinit var support: MaintenanceAdminContext
    suspend fun initialize() {
        admin.bootstrapOwner("owner@example.com","GoodPassword123!"); owner=login("owner@example.com")
        for (role in listOf(AdminRole.OPERATOR,AdminRole.SUPPORT)) {
            admin.createAccount(owner.token,owner.csrfToken,owner.origin,CreateAdminRequest("${role.name.lowercase()}@example.com","GoodPassword123!",role,"Fixture",role.name))
        }
        operator=login("operator@example.com"); support=login("support@example.com")
    }
    suspend fun login(email:String): MaintenanceAdminContext {
        val login=admin.login(email,"GoodPassword123!","http://localhost:8080","127.0.0.1")
        return MaintenanceAdminContext(login.token,login.response.csrfToken,"http://localhost:8080")
    }
    fun request()=MaintenanceUpdateRequest(0,java.util.UUID.randomUUID().toString(),"Internal reason",MaintenanceMode.PLANNED,
        "UPDATE","Bảo trì","Maintenance",enabled=true)
}
