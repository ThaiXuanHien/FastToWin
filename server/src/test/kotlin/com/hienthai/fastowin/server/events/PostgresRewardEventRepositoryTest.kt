package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.maintenance.*
import com.zaxxer.hikari.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresRewardEventRepositoryTest {
    private suspend fun database(poolSize:Int=5,block:suspend Fixture.()->Unit) {
        val url=System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(),"TEST_DATABASE_URL absent: PostgreSQL coverage skipped")
        val schema="reward_event_test_${UUID.randomUUID().toString().replace("-","")}"
        fun config()=HikariConfig().apply {
            jdbcUrl=url; username=System.getenv("TEST_DATABASE_USER"); password=System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize=poolSize; connectionTimeout=5000
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema=schema }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    Fixture(source,setup).apply { initialize() }.block()
                }
            } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
    private class Fixture(val source:HikariDataSource,val monitor:HikariDataSource) {
        var now=500L
        val admin=AdminService(PostgresAdminRepository(source),AdminConfiguration(RewardEventFixture.ORIGIN,false),PasswordHasher(1000),{ now })
        val repository=PostgresRewardEventRepository(source)
        val service=RewardEventService(repository,admin,{ now })
        lateinit var owner:RewardEventAdminContext
        suspend fun initialize() {
            admin.bootstrapOwner("owner@example.com","GoodPassword123!")
            val result=admin.login("owner@example.com","GoodPassword123!",RewardEventFixture.ORIGIN,"127.0.0.1")
            owner=RewardEventAdminContext(result.token,result.response.csrfToken,RewardEventFixture.ORIGIN)
        }
        fun request()=RewardEventDraftRequest(0,UUID.randomUUID().toString(),"Internal reason",RewardEventFixture().draft().copy(goldMultiplier=2))
        fun mutation(version:Long)=RewardEventMutationRequest(version,UUID.randomUUID().toString(),"Internal reason")
        suspend fun draft()=service.create(owner,request())
        suspend fun schedule(value:RewardEventAdminView):Pair<RewardEventMutationRequest,RewardEventAdminView> {
            val request=mutation(value.version).copy(previewToken=service.preview(owner,value.id).previewToken)
            return request to service.schedule(owner,value.id,request)
        }
        fun sql(text:String)=source.connection.use { it.createStatement().use { s -> s.execute(text) } }
        fun count(table:String)=source.connection.use { it.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM $table").use { r -> r.next(); r.getInt(1) } } }
        suspend fun awaitGate(shared:Boolean=false)=withTimeout(10_000) {
            val query=if(shared) "SELECT pg_advisory_xact_lock_shared(7421092187350)" else "SELECT pg_advisory_xact_lock(7421092187350)"
            while(monitor.connection.use { c -> c.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE wait_event='advisory' AND query=?").use { s ->
                s.setString(1,query); s.executeQuery().use { r -> r.next(); r.getInt(1) }
            } }==0) delay(10)
        }
    }
    @Test fun publicationWaitsForActualStartAndQueuedStartSeesCommittedCatalogWithPoolTwo()=runBlocking { database(poolSize=2) {
        val draft=draft(); val request=mutation(draft.version).copy(previewToken=service.preview(owner,draft.id).previewToken)
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        coroutineScope {
            val oldStart=async(Dispatchers.IO) { repository.withStartCatalog { catalog -> entered.complete(Unit); release.await(); catalog.snapshot(1000,MatchType.RANKED) } }
            entered.await()
            val publish=async(Dispatchers.IO) { service.schedule(owner,draft.id,request) }
            awaitGate(); assertFalse(publish.isCompleted)
            val queuedStart=async(Dispatchers.IO) { repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) } }
            try {
                withTimeout(10_000) { while(source.hikariPoolMXBean.threadsAwaitingConnection==0) delay(10) }
                assertFalse(queuedStart.isCompleted)
            } finally { release.complete(Unit) }
            assertEquals(1,oldStart.await().goldMultiplier)
            assertEquals(RewardEventState.SCHEDULED,withTimeout(10_000) { publish.await() }.state)
            assertEquals(2,withTimeout(10_000) { queuedStart.await() }.goldMultiplier)
        }
    } }
    @Test fun cancellationWaitsForCapturedStartThenExcludesLaterStarts()=runBlocking { database {
        val live=schedule(draft()).second
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        coroutineScope {
            val start=async(Dispatchers.IO) { repository.withStartCatalog { entered.complete(Unit); release.await(); it.snapshot(1000,MatchType.RANKED) } }
            entered.await(); val cancel=async(Dispatchers.IO) { service.cancel(owner,live.id,mutation(live.version)) }
            try {
                awaitGate(); assertFalse(cancel.isCompleted)
                // The queued writer holds global admin authorization, yet trusted maintenance observation and public reads progress.
                withTimeout(10_000) {
                    assertEquals(live.id,service.publicList().items.single().id)
                    assertEquals(MaintenanceState.ACTIVE,MaintenanceService(PostgresMaintenanceRepository(source),admin,{ now }).refresh(0).state)
                }
            } finally { release.complete(Unit) }
            val frozen=start.await(); cancel.await()
            assertEquals(2,frozen.goldMultiplier); assertEquals(live.version,frozen.sources.single().version)
            assertEquals(1,repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) }.goldMultiplier)
        }
    } }
    @Test fun startWaitsForPublicationCommitAndCancelledCallbackReleasesSharedGate()=runBlocking { database {
        val draft=draft(); val request=mutation(draft.version).copy(previewToken=service.preview(owner,draft.id).previewToken)
        // Pause publication at its audit INSERT, after put, before commit; other starts must wait.
        sql("CREATE FUNCTION event_audit_barrier() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN IF NEW.action='EVENT_SCHEDULE' THEN PERFORM pg_advisory_xact_lock(918273648); END IF; RETURN NEW; END ${'$'}${'$'}")
        sql("CREATE TRIGGER event_audit_barrier BEFORE INSERT ON admin_audit FOR EACH ROW EXECUTE FUNCTION event_audit_barrier()")
        source.connection.use { barrier ->
            barrier.autoCommit=false; barrier.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(918273648)") }
            coroutineScope {
                val publication=async(Dispatchers.IO) { service.schedule(owner,draft.id,request) }
                withTimeout(10_000) {
                    while(monitor.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE wait_event='advisory' AND query LIKE 'INSERT INTO admin_audit%'").use { r -> r.next(); r.getInt(1) } } }==0) delay(10)
                }
                val start=async(Dispatchers.IO) { repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) } }
                try { awaitGate(shared=true); assertFalse(start.isCompleted) } finally { barrier.rollback() }
                publication.await(); assertEquals(2,start.await().goldMultiplier)
            }
        }
        val entered=CompletableDeferred<Unit>()
        val callback=launch(Dispatchers.IO) { repository.withStartCatalog { entered.complete(Unit); awaitCancellation() } }
        entered.await(); callback.cancelAndJoin()
        assertEquals(RewardEventState.CANCELLED,withTimeout(10_000) { service.cancel(owner,draft.id,mutation(2)) }.state)
    } }
    @Test fun authorizationAfterGateWaitPrecedesReplayAndAuditFailureRollsBackEverything()=runBlocking { database {
        val draft=draft(); val (request,live)=schedule(draft)
        eventError(409) { service.schedule(owner,draft.id,request.copy(reason="mismatched")) }
        val actor=admin.authenticate(owner.token)!!.id
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        coroutineScope {
            val reader=launch(Dispatchers.IO) { repository.withStartCatalog { entered.complete(Unit); release.await() } }
            entered.await(); val replay=async(Dispatchers.IO) { runCatching { service.schedule(owner,draft.id,request) } }
            try {
                awaitGate()
                sql("UPDATE admin_accounts SET role='OPERATOR' WHERE id='$actor'")
            } finally { release.complete(Unit) }
            reader.join(); assertEquals(403,assertIs<AdminException>(replay.await().exceptionOrNull()).status)
        }
        sql("UPDATE admin_accounts SET role='OWNER' WHERE id='$actor'")
        val before=count("reward_event_requests")
        sql("ALTER TABLE admin_audit RENAME TO missing_admin_audit")
        eventError(503) { service.cancel(owner,live.id,mutation(live.version)) }
        assertEquals(before,count("reward_event_requests"))
        assertEquals(2,repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) }.goldMultiplier)
        sql("ALTER TABLE missing_admin_audit RENAME TO admin_audit")
        assertEquals(live,RewardEventService(PostgresRewardEventRepository(source),admin,{ now }).schedule(owner,draft.id,request))
        admin.logout(owner.token,owner.csrfToken,owner.origin)
        eventError(401) { service.schedule(owner,draft.id,request) }
    } }
    @Test fun globalAuthorizationBarrierDoesNotBlockPublicSourceOrMaintenanceObservation()=runBlocking { database {
        val live=schedule(draft()).second
        source.connection.use { authorization ->
            authorization.autoCommit=false; authorization.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
            try {
                withTimeout(10_000) {
                    assertEquals(live.id,service.publicList().items.single().id)
                    assertEquals(2,repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) }.goldMultiplier)
                    assertEquals(MaintenanceState.ACTIVE,MaintenanceService(PostgresMaintenanceRepository(source),admin,{ now }).refresh(0).state)
                }
            } finally { authorization.rollback() }
        }
    } }
    @Test fun failedPublicationAuditRemainsDraftAndAbsentDurableTablesFailClosed()=runBlocking { database {
        val value=draft(); val request=mutation(value.version).copy(previewToken=service.preview(owner,value.id).previewToken)
        sql("ALTER TABLE admin_audit RENAME TO missing_admin_audit")
        eventError(503) { service.schedule(owner,value.id,request) }
        eventError(404) { service.publicDetail(value.id) }
        assertEquals(1,count("reward_event_requests")); assertEquals(1,repository.withStartCatalog { it.snapshot(1000,MatchType.RANKED) }.goldMultiplier)
        sql("ALTER TABLE missing_admin_audit RENAME TO admin_audit")
        assertEquals(2,service.schedule(owner,value.id,request).version)
        sql("ALTER TABLE reward_events RENAME TO missing_reward_events")
        eventError(503) { service.publicList() }
        eventError(503) { repository.withStartCatalog { error("must not enter callback") } }
        val actualService=service; val actualAdmin=admin
        testApplication {
            application { gameModule(adminService=actualAdmin,rewardEventService=actualService) }
            val response=client.get("/api/events")
            assertEquals(HttpStatusCode.ServiceUnavailable,response.status); assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
            assertTrue("STORAGE_UNAVAILABLE" in response.bodyAsText())
            for(secret in listOf("reward_events","SQLException","SELECT")) assertFalse(secret in response.bodyAsText())
        }
    } }
    @Test fun globalAdminLockWaitRechecksRevocationBeforeDurableReplay()=runBlocking { database {
        val value=draft(); val (request,_)=schedule(value); val actor=admin.authenticate(owner.token)!!.id
        source.connection.use { authorization ->
            authorization.autoCommit=false; authorization.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
            coroutineScope {
                val replay=async(Dispatchers.IO) { runCatching { service.schedule(owner,value.id,request) } }
                try {
                    withTimeout(10_000) {
                        while(monitor.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE wait_event='advisory' AND query='SELECT pg_advisory_xact_lock(7421092187349)'").use { r -> r.next(); r.getInt(1) } } }==0) delay(10)
                    }
                    authorization.createStatement().use { it.execute("UPDATE admin_sessions SET revoked=TRUE WHERE admin_id='$actor'") }
                    authorization.commit()
                    assertEquals(401,assertIs<AdminException>(replay.await().exceptionOrNull()).status)
                } finally { authorization.rollback() }
            }
        }
        assertEquals(2,count("reward_event_requests"))
    } }
}
