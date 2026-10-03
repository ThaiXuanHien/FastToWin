package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.zaxxer.hikari.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresMaintenanceRepositoryTest {
    private suspend fun database(block:suspend Fixture.()->Unit) {
        val url=System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(),"TEST_DATABASE_URL absent: PostgreSQL coverage skipped")
        val schema="maintenance_test_${UUID.randomUUID().toString().replace("-","")}"
        fun config()=HikariConfig().apply { jdbcUrl=url; username=System.getenv("TEST_DATABASE_USER"); password=System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize=5 }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema=schema }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    Fixture(source).apply { initialize() }.block()
                }
            } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
    private class Fixture(val source:HikariDataSource) {
        var now=MaintenanceFixture.START
        val admin=AdminService(PostgresAdminRepository(source),AdminConfiguration("http://localhost:8080",false),PasswordHasher(1000),{ now })
        val repository=PostgresMaintenanceRepository(source)
        var service=MaintenanceService(repository,admin,{ now })
        lateinit var owner:MaintenanceAdminContext
        suspend fun initialize() {
            admin.bootstrapOwner("owner@example.com","GoodPassword123!")
            val result=admin.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
            owner=MaintenanceAdminContext(result.token,result.response.csrfToken,"http://localhost:8080")
        }
        fun request()=MaintenanceUpdateRequest(0,UUID.randomUUID().toString(),"Internal reason",MaintenanceMode.PLANNED,"UPDATE","Bảo trì","Maintenance",warningMinutes=0,drainMinutes=1,enabled=true)
        fun sql(text:String)=source.connection.use { it.createStatement().use { s -> s.execute(text) } }
        fun count(table:String)=source.connection.use { it.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM $table").use { r -> r.next(); r.getInt(1) } } }
    }
    @Test fun restartReplayAndDueTransitionAreDurable() = runTest { database {
        val request=request(); val result=service.update(owner,request)
        service=MaintenanceService(PostgresMaintenanceRepository(source),admin,{ now })
        assertEquals(result,service.update(owner,request)); assertEquals(1,count("runtime_maintenance_requests"))
        now+=60_000; assertEquals(MaintenanceState.MAINTENANCE,service.refresh(1).state)
        val persisted=service.read(owner).persisted
        assertEquals(2,persisted.version); assertEquals(result.persisted.actorId,persisted.actorId)
        assertEquals(1,count("runtime_maintenance"))
        assertEquals(result,service.update(owner,request))
    } }
    @Test fun auditFailureRollsBackSnapshotReplayAndTransition() = runTest { database {
        val before=count("admin_audit"); sql("ALTER TABLE admin_audit RENAME TO missing_admin_audit")
        val request=request(); error(503) { service.update(owner,request) }
        assertEquals(0,count("runtime_maintenance_requests"))
        sql("ALTER TABLE missing_admin_audit RENAME TO admin_audit")
        assertEquals(0,service.read(owner).persisted.version)
        assertEquals(1,service.update(owner,request).persisted.version)
        now+=60_000; sql("ALTER TABLE admin_audit RENAME TO missing_admin_audit")
        error(503) { service.refresh(1) }
        sql("ALTER TABLE missing_admin_audit RENAME TO admin_audit")
        assertEquals(MaintenanceState.DRAINING,service.read(owner).persisted.state)
        assertTrue(count("admin_audit")>before)
    } }
    @Test fun disableRacingAutomaticTransitionCannotRestoreStalePolicy() = runTest { database {
        service.update(owner,request()); now+=60_000
        val disable=request().copy(expectedVersion=1,enabled=false)
        val results=coroutineScope {
            val refresh=async(Dispatchers.IO) { service.refresh(1) }
            val update=async(Dispatchers.IO) { runCatching { service.update(owner,disable) } }
            refresh.await(); update.await()
        }
        val current=service.read(owner).persisted
        if (results.isSuccess) assertEquals(MaintenanceState.ACTIVE,current.state)
        else { assertEquals(409,assertIs<AdminException>(results.exceptionOrNull()).status); assertEquals(MaintenanceState.MAINTENANCE,current.state) }
        assertEquals(2,current.version)
    } }
    @Test fun revocationAndRoleChangePrecedeDurableReplay() = runTest { database {
        val request=request(); service.update(owner,request)
        val id=admin.authenticate(owner.token)!!.id
        sql("UPDATE admin_accounts SET role='OPERATOR' WHERE id='$id'")
        error(403) { service.update(owner,request) }
        sql("UPDATE admin_accounts SET role='OWNER' WHERE id='$id'")
        admin.logout(owner.token,owner.csrfToken,owner.origin)
        error(401) { service.update(owner,request) }
        assertEquals(1,count("runtime_maintenance_requests"))
    } }
    @Test fun realAuthorizationSqlFailureReturnsSafe503() = runTest { database {
        val actualAdmin=admin; val actualService=service; val token=owner.token
        sql("ALTER TABLE admin_sessions RENAME TO missing_admin_sessions")
        testApplication {
            application { gameModule(adminService=actualAdmin,maintenanceService=actualService) }
            val response=client.get("/admin/api/maintenance") { header(HttpHeaders.Cookie,"fasttowin_admin=$token") }
            assertEquals(HttpStatusCode.ServiceUnavailable,response.status)
            assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
            val body=response.bodyAsText(); assertTrue(body.contains("STORAGE_UNAVAILABLE"))
            for (text in listOf("admin_sessions","SELECT","SQLException")) assertFalse(body.contains(text))
        }
    } }
    @Test fun missingOrCorruptSnapshotIsUnavailableNeverActive() = runTest { database {
        sql("UPDATE runtime_maintenance SET payload='{}'")
        // A legacy omitted-default ACTIVE object is a valid seeded snapshot.
        assertEquals(MaintenanceState.ACTIVE,service.refresh(1).state)
        sql("UPDATE runtime_maintenance SET payload='broken'")
        error(503) { service.refresh(1) }
        assertTrue(service.effectivePolicy().blocksNewIntent)
        sql("UPDATE runtime_maintenance SET payload='{\"state\":\"DRAINING\",\"mode\":\"PLANNED\"}',state='DRAINING'")
        error(503) { service.refresh(1) }
        sql("DELETE FROM runtime_maintenance")
        error(503) { service.refresh(1) }
    } }
    @Test fun databaseRuntimeExposesDurableRepositoryWithoutEnablingRoutes() = runTest { database {
        val schema=source.schema
        val base=System.getenv("TEST_DATABASE_URL")
        val url=if (Regex("[?&]currentSchema=[^&]*").containsMatchIn(base)) {
            base.replace(Regex("([?&])currentSchema=[^&]*")) { "${it.groupValues[1]}currentSchema=$schema" }
        } else base + (if ('?' in base) "&" else "?") + "currentSchema=$schema"
        DatabaseRuntime.open(DatabaseSettings(url,System.getenv("TEST_DATABASE_USER"),System.getenv("TEST_DATABASE_PASSWORD"),2)).use { runtime ->
            val value=runtime.maintenanceRepository.transaction { snapshot() }
            assertEquals(MaintenanceState.ACTIVE,value.state)
        }
    } }
    @Test fun concurrentMutationsCommitOnlyOneExpectedVersion() = runTest { database {
        val results=coroutineScope {
            List(2) { async(Dispatchers.IO) { runCatching { service.update(owner,request()) } } }.awaitAll()
        }
        assertEquals(1,results.count { it.isSuccess })
        assertEquals(409,assertIs<AdminException>(results.single { it.isFailure }.exceptionOrNull()).status)
        assertEquals(1,service.read(owner).persisted.version)
        assertEquals(1,count("runtime_maintenance_requests"))
    } }
    @Test fun adminRevocationLockOrdersAuthorizationBeforeReplay() = runTest { database {
        val request=request(); service.update(owner,request)
        val actor=admin.authenticate(owner.token)!!.id
        val locked=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        coroutineScope {
            val revoke=async(Dispatchers.IO) {
                PostgresAdminRepository(source).transaction {
                    revokeSessions(actor); locked.countDown()
                    check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))
                }
            }
            withContext(Dispatchers.IO) { assertTrue(locked.await(10,java.util.concurrent.TimeUnit.SECONDS)) }
            val replay=async(Dispatchers.IO) { runCatching { service.update(owner,request) } }
            release.countDown(); revoke.await()
            assertEquals(401,assertIs<AdminException>(replay.await().exceptionOrNull()).status)
        }
        assertEquals(1,count("runtime_maintenance_requests"))
    } }
}
