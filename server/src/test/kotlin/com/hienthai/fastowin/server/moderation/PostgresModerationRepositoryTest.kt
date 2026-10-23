package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.AdminException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresModerationRepositoryTest {
    @Test fun `concurrent retries create one report and one quota event across repository instances`()=runTest {
        fixture {ds,reporter,target ->
            val request=ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target,UgcReportReason.HARASSMENT,"context","parallel-retry")
            val ids=coroutineScope {(1..8).map {async {
                ModerationService(PostgresModerationRepository(ds)).report(reporter,request).reportId
            }}.map {it.await()}}
            assertEquals(1,ids.distinct().size)
            PostgresModerationRepository(ds).transaction {
                assertEquals(1,rateCount(reporter,0))
                assertEquals(1,reports(null,100,0).count {it.reporterId==reporter})
            }
            assertEquals(409,assertFailsWith<AdminException> {
                ModerationService(PostgresModerationRepository(ds)).report(reporter,request.copy(note="changed"))
            }.status)
        }
    }
    @Test fun `account erasure and terms rollback share the caller transaction without leftover private caches`()=runTest {
        fixture {ds,reporter,target ->
            val repo=PostgresModerationRepository(ds)
            val service=ModerationService(repo)
            val receipt=service.report(reporter,ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target,UgcReportReason.OTHER,"Private context","erase"))
            ds.connection.use {connection ->
                connection.autoCommit=false
                JdbcModerationTransaction(connection).acceptTerms(reporter,CONTENT_TERMS_VERSION,100)
                connection.rollback()
            }
            assertNull(service.terms(reporter).acceptedVersion)
            ds.connection.use {connection ->
                connection.autoCommit=false
                connection.prepareStatement("SELECT pg_advisory_xact_lock(7421092187349)").use {it.execute()}
                JdbcModerationTransaction(connection).erasePlayer(reporter)
                connection.commit()
            }
            repo.transaction {
                val report=report(receipt.reportId)!!
                assertNull(report.reporterId);assertEquals("",report.note);assertEquals("",report.evidenceText)
                assertNull(replay(reporter,"erase"));assertEquals(0,rateCount(reporter,0))
            }
        }
    }
    @Test fun `restriction revokes sessions atomically and rollback restores identity status`()=runTest {
        fixture {ds,reporter,_ ->
            val repo=PostgresModerationRepository(ds)
            assertFailsWith<IllegalStateException> {repo.transaction {putRestriction(ModerationRestrictionView(reporter,1,true),100);error("rollback")}}
            assertFalse(repo.transaction {restriction(reporter).blocked})
            repo.transaction {putRestriction(ModerationRestrictionView(reporter,1,true),100)}
            assertTrue(repo.transaction {restriction(reporter).blocked})
            ds.connection.use {connection ->
                connection.prepareStatement("SELECT count(*) FROM sessions WHERE user_id=? AND revoked_at IS NULL").use {statement ->
                    statement.setObject(1,UUID.fromString(reporter));statement.executeQuery().use {it.next();assertEquals(0,it.getInt(1))}
                }
            }
        }
    }
    @Test fun `closed report cleanup cascades replay while quota survives until rolling day expires`()=runTest {
        fixture {ds,reporter,target ->
            val repo=PostgresModerationRepository(ds);var now=1_800_000_000_000L
            val service=ModerationService(repo,nowMillis={now})
            val receipt=service.report(reporter,ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target,UgcReportReason.OTHER,requestId="close"))
            repo.transaction {putReport(report(receipt.reportId)!!.copy(status=ModerationStatus.RESOLVED,closedAtEpochMillis=now-90L*86_400_000-1))}
            service.cleanup()
            repo.transaction {assertNull(report(receipt.reportId));assertNull(replay(reporter,"close"));assertEquals(1,rateCount(reporter,now-86_400_000))}
            now+=86_400_001;service.cleanup()
            assertEquals(0,repo.transaction {rateCount(reporter,0)})
        }
    }
    @Test fun `all target types resolve current source revisions and avatar evidence contains no image bytes`()=runTest {
        fixture {ds,reporter,owner ->
            val clan=UUID.randomUUID().toString();val room=UUID.randomUUID().toString();val tournament=UUID.randomUUID().toString()
            ds.connection.use {connection ->
                connection.prepareStatement("UPDATE profiles SET avatar_data='private-inline-image-bytes',avatar_revision=7 WHERE user_id=?").use {it.setObject(1,UUID.fromString(owner));it.executeUpdate()}
                connection.prepareStatement("INSERT INTO clans(id,name,description,owner_id,created_at) VALUES (?,'Clan','Description',?,NOW())").use {it.setObject(1,UUID.fromString(clan));it.setObject(2,UUID.fromString(owner));it.executeUpdate()}
                connection.prepareStatement("INSERT INTO active_room_snapshots(room_id,state_json,updated_at) VALUES (?,?::jsonb,NOW())").use {
                    it.setObject(1,UUID.fromString(room));it.setString(2,"""{"roomName":"Room name","host":{"playerId":"$owner"}}""");it.executeUpdate()
                }
                connection.prepareStatement("INSERT INTO tournaments(tournament_id,status,player_ids,snapshot_json,created_at) VALUES (?,'LOBBY',ARRAY[?::uuid],?::jsonb,NOW())").use {
                    it.setObject(1,UUID.fromString(tournament));it.setObject(2,UUID.fromString(owner));it.setString(3,"""{"name":"Tournament name","hostPlayerId":"$owner"}""");it.executeUpdate()
                }
            }
            val repo=PostgresModerationRepository(ds)
            val ids=mapOf(UgcTargetType.PLAYER_PROFILE to owner,UgcTargetType.PLAYER_AVATAR to owner,UgcTargetType.CLAN to clan,UgcTargetType.ROOM to room,UgcTargetType.TOURNAMENT to tournament)
            repo.transaction {
                ids.forEach {(type,id)->assertEquals(owner,target(type,id)!!.ownerId)}
                assertEquals("Clan\nDescription",target(UgcTargetType.CLAN,clan)!!.text)
                assertEquals("Room name",target(UgcTargetType.ROOM,room)!!.text)
                assertEquals("Tournament name",target(UgcTargetType.TOURNAMENT,tournament)!!.text)
                assertEquals("7",target(UgcTargetType.PLAYER_AVATAR,owner)!!.revision)
                assertNull(target(UgcTargetType.ROOM,UUID.randomUUID().toString()))
            }
            val receipt=ModerationService(repo).report(reporter,ContentReportRequest(UgcTargetType.PLAYER_AVATAR,owner,UgcReportReason.OTHER,requestId="avatar"))
            repo.transaction {
                val report=report(receipt.reportId)!!
                assertEquals("",report.evidenceText);assertEquals("avatar-revision:7",report.imageReference)
                assertFalse(report.toString().contains("private-inline-image-bytes"))
            }
            val old=repo.transaction {target(UgcTargetType.CLAN,clan)!!}
            repo.transaction {putSuppression(ContentSuppression(old.type,old.id,old.revision,owner,100))}
            ds.connection.use {connection ->connection.prepareStatement("UPDATE clans SET name='New clan name' WHERE id=?").use {it.setObject(1,UUID.fromString(clan));it.executeUpdate()}}
            val current=repo.transaction {target(UgcTargetType.CLAN,clan)!!}
            assertNotEquals(old.revision,current.revision)
            assertFalse(ModerationService(repo).isSuppressed(UgcTargetType.CLAN,clan,current.revision))
        }
    }
    @Test fun `admin removal stores private reason precise suppression and localized warning then erasure scrubs reason`()=runTest {
        fixture {ds,reporter,target ->
            val authority=com.hienthai.fastowin.server.admin.AdminService(
                com.hienthai.fastowin.server.admin.PostgresAdminRepository(ds),
                com.hienthai.fastowin.server.admin.AdminConfiguration("http://localhost:8080",false),
                com.hienthai.fastowin.server.PasswordHasher(iterations=1_000))
            authority.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=authority.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
            val context=ModerationAdminContext(login.token,login.response.csrfToken,"http://localhost:8080")
            val repo=PostgresModerationRepository(ds);val service=ModerationService(repo,authority)
            val receipt=service.report(reporter,ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target,UgcReportReason.OTHER,"Private report","remove"))
            val request=ModerationResolveRequest(1,"resolve","Private admin reasoning",ModerationStatus.RESOLVED,true)
            val result=service.resolve(context,receipt.reportId,request)
            assertEquals("Private admin reasoning",result.lastActionReason)
            assertEquals(result,service.resolve(context,receipt.reportId,request))
            assertNotNull(repo.transaction {suppressions().single().nameRevision})
            ds.connection.use {connection ->
                connection.prepareStatement("SELECT count(*) FROM user_notifications WHERE user_id=? AND title_key='UgcContentRemovedTitle'").use {statement ->
                    statement.setObject(1,UUID.fromString(target));statement.executeQuery().use {it.next();assertEquals(1,it.getInt(1))}
                }
            }
            service.erasePlayer(reporter)
            assertNull(service.detail(context,receipt.reportId).lastActionReason)
            val now=System.currentTimeMillis()
            repo.transaction {appendAction(ModerationActionRecord(UUID.randomUUID().toString(),null,target,null,"MODERATION_BLOCK","Private restriction reason",now,now-1))}
            service.cleanup()
            ds.connection.use {connection ->connection.createStatement().use {statement ->statement.executeQuery("SELECT count(*) FROM ugc_moderation_actions").use {it.next();assertEquals(0,it.getInt(1))}}}
        }
    }
    @Test fun `gameplay status reads proceed while admin mutation advisory lock is held`()=runTest {
        fixture {ds,reporter,_ ->
            ds.connection.use {held ->
                held.autoCommit=false
                held.prepareStatement("SELECT pg_advisory_xact_lock(7421092187349)").use {it.execute()}
                val reading=java.util.concurrent.CompletableFuture.supplyAsync {
                    kotlinx.coroutines.runBlocking {ModerationService(PostgresModerationRepository(ds)).isBlocked(reporter)}
                }
                try {assertFalse(reading.get(2,java.util.concurrent.TimeUnit.SECONDS))}
                finally {held.rollback();reading.get(5,java.util.concurrent.TimeUnit.SECONDS)}
            }
        }
    }
    @Test fun `reported owner erasure scrubs removal and standalone restriction while preserving unrelated records`()=runTest {
        fixture {ds,reporter,target ->
            val authority=com.hienthai.fastowin.server.admin.AdminService(
                com.hienthai.fastowin.server.admin.PostgresAdminRepository(ds),
                com.hienthai.fastowin.server.admin.AdminConfiguration("http://localhost:8080",false),
                com.hienthai.fastowin.server.PasswordHasher(iterations=1_000))
            authority.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=authority.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
            val context=ModerationAdminContext(login.token,login.response.csrfToken,"http://localhost:8080")
            val repo=PostgresModerationRepository(ds);val service=ModerationService(repo,authority)
            val receipt=service.report(reporter,ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target,UgcReportReason.OTHER,"Private owner evidence","owner-report"))
            service.resolve(context,receipt.reportId,ModerationResolveRequest(1,"owner-remove","Private removal",ModerationStatus.RESOLVED,true))
            service.restrict(context,target,ModerationRestrictionRequest(0,"owner-block","Private restriction",true))
            service.acceptTerms(reporter,CONTENT_TERMS_VERSION)
            val unrelatedAction=UUID.randomUUID().toString()
            repo.transaction {
                appendAction(ModerationActionRecord(unrelatedAction,null,reporter,null,"MODERATION_WARN","Unrelated reason",100,Long.MAX_VALUE))
                putSuppression(ContentSuppression(UgcTargetType.PLAYER_PROFILE,reporter,contentRevision("Name"),reporter))
                putReplay(ModerationReplay(reporter,"unrelated","fingerprint","RESTRICTION","""{"userId":"$reporter","version":0,"blocked":false}""",null,Long.MAX_VALUE))
            }
            fun count(table:String):Int=ds.connection.use {connection ->
                connection.createStatement().use {statement ->statement.executeQuery("SELECT count(*) FROM $table").use {it.next();it.getInt(1)}}
            }
            assertEquals(3,count("ugc_moderation_actions"))
            assertEquals(4,count("ugc_request_replays"))
            assertEquals(2,count("ugc_content_suppressions"))
            assertEquals(1,count("ugc_account_restrictions"))

            service.erasePlayer(target)

            repo.transaction {
                val erased=assertNotNull(report(receipt.reportId))
                assertEquals(reporter,erased.reporterId)
                assertNull(erased.targetId);assertNull(erased.targetOwnerId)
                assertEquals("",erased.note);assertEquals("",erased.evidenceText)
                assertNull(erased.targetRevision);assertNull(erased.imageReference)
                assertNull(lastActionReason(receipt.reportId))
                assertEquals(listOf(reporter),suppressions().map {it.targetId})
                assertNotNull(replay(reporter,"unrelated"))
            }
            assertEquals(1,count("ugc_moderation_actions"))
            assertEquals(1,count("ugc_request_replays"))
            assertEquals(1,count("ugc_content_suppressions"))
            assertEquals(0,count("ugc_account_restrictions"))
            assertEquals(CONTENT_TERMS_VERSION,service.terms(reporter).acceptedVersion)
            assertEquals(1,repo.transaction {rateCount(reporter,0)})
        }
    }
    private suspend fun fixture(block:suspend (HikariDataSource,String,String)->Unit) {
        val url=System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(),"TEST_DATABASE_URL required for real PostgreSQL moderation tests")
        val schemaName="ugc_mod_"+UUID.randomUUID().toString().replace("-","")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl=url;username=System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password=System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin";maximumPoolSize=4;schema=schemaName
        }).use {ds ->
            try {
            Flyway.configure().dataSource(ds).schemas(schemaName).defaultSchema(schemaName).locations("classpath:db/migration").load().migrate()
            val reporter=UUID.randomUUID().toString();val target=UUID.randomUUID().toString()
            ds.connection.use {connection ->
                listOf(reporter,target).forEach {id ->
                    connection.prepareStatement("INSERT INTO users(id,account_type,status,created_at,updated_at) VALUES (?,'GUEST','ACTIVE',NOW(),NOW())").use {it.setObject(1,UUID.fromString(id));it.executeUpdate()}
                    connection.prepareStatement("INSERT INTO profiles(user_id,display_name,player_code,created_at,updated_at) VALUES (?,'Name',?,NOW(),NOW())").use {it.setObject(1,UUID.fromString(id));it.setString(2,UUID.randomUUID().toString().replace("-","").take(12));it.executeUpdate()}
                }
                connection.prepareStatement("INSERT INTO sessions(id,user_id,resume_token_hash,created_at,last_seen_at,expires_at) VALUES (?,?,?,NOW(),NOW(),NOW()+INTERVAL '1 day')").use {
                    it.setObject(1,UUID.randomUUID());it.setObject(2,UUID.fromString(reporter));it.setString(3,com.hienthai.fastowin.server.hashToken(UUID.randomUUID().toString()));it.executeUpdate()
                }
            }
            block(ds,reporter,target)
            } finally {
                check(schemaName.matches(Regex("ugc_mod_[a-f0-9]{32}")))
                ds.connection.use {connection ->
                    connection.createStatement().use {it.execute("DROP SCHEMA $schemaName CASCADE")}
                }
            }
        }
    }
}
