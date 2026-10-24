package com.hienthai.fastowin.server.missions

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*

class MissionEnginePostgresTest {
    // Removing source dedup, snapshot isolation, or the shared legacy claim must break this test.
    @Test fun `same action definitions freeze targets rewards and one legacy or HTTP grant`() = runTest {
        database { source ->
            val ids=PostgresGuestIdentityRepository(source)
            val player=ids.resolveGuest("Mission owner",null,1000).playerId
            val repository=PostgresMissionRepository(source)
            var now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),nowMillis={now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            var context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val service=MissionService(repository,admins,{now})
            // Use an isolated sparse pool so both PLAY definitions occupy the two normal slots.
            source.connection.use { c -> c.createStatement().use { it.executeUpdate("UPDATE mission_definitions SET archived_from='2026-10-25'") } }
            fun config(code:String,target:Int,gold:Int)=MissionConfig(code,MissionScope.PERSONAL,MissionAction.PLAY,MissionCadence.DAILY,MissionDifficulty.NORMAL,target,MissionReward(10,gold,1))
            val one=service.create(context,MissionDraftRequest(config("CUSTOM_PLAY_ONE",1,77),reason="Fixture",requestId="create-one"))
            val three=service.create(context,MissionDraftRequest(config("CUSTOM_PLAY_THREE",3,111),reason="Fixture",requestId="create-three"))
            service.publish(context,one.id,MissionMutationRequest(one.version,"Publish","publish-one"))
            service.publish(context,three.id,MissionMutationRequest(three.version,"Publish","publish-three"))
            now=Instant.parse("2026-10-25T06:00:00Z").toEpochMilli()
            repository.transaction { record(MissionProgressEvent("match:one",player,now,matchType=MatchType.CASUAL,played=true)) }
            repository.transaction { record(MissionProgressEvent("match:one",player,now,matchType=MatchType.CASUAL,played=true)) }
            val instances=service.personal(player).items.filter { it.code.startsWith("CUSTOM_") }
            assertEquals(listOf(1,1),instances.sortedBy { it.target }.map { it.progress })
            assertEquals(listOf(1,3),instances.sortedBy { it.target }.map { it.target })
            val renewed=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            context=MissionAdminContext(renewed.token,renewed.response.csrfToken,"https://admin.example")
            val published=service.detail(context,one.id)
            service.update(context,one.id,MissionDraftRequest(config("CUSTOM_PLAY_ONE",1,999),published.version,"Future reward","edit"))
            val first=service.personal(player).items.single { it.target==1 }
            assertEquals(77,first.reward.gold)
            val claim=service.claim(player,first.instanceId,MissionClaimRequest("first-claim"))
            assertEquals(MissionClaimState.CLAIMED,claim.state)
            val legacy=PostgresPlayerProfileRepository(source,Clock.fixed(Instant.ofEpochMilli(now),ZoneId.of("Asia/Bangkok"))).claimMissionReward(player,"CUSTOM_PLAY_ONE")
            assertEquals(MissionRewardClaimStatus.ALREADY_CLAIMED,legacy?.status)
            val wallet=source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT gold,gems,experience_points FROM player_stats WHERE user_id='$player'").use { it.next(); listOf(it.getInt(1),it.getInt(2),it.getInt(3)) } } }
            assertEquals(listOf(77,1,10),wallet)
            assertEquals(1,source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM wallet_transactions WHERE user_id='$player' AND source_type='MISSION'").use { it.next();it.getInt(1) } } })
        }
    }

    @Test fun `distinct simultaneous events preserve updates while replay does not`() = runTest {
        database { source ->
            val player=PostgresGuestIdentityRepository(source).resolveGuest("Concurrent",null,1000).playerId
            val repo=PostgresMissionRepository(source)
            val time=Instant.parse("2026-09-02T06:00:00Z").toEpochMilli()
            (1..8).map { index -> async { repo.transaction { record(MissionProgressEvent("match:$index",player,time,played=true,matchType=MatchType.CASUAL)) } } }.awaitAll()
            val list=MissionService(repo,nowMillis={time}).personal(player).items
            assertEquals(2,list.single { it.code=="DAILY_CASUAL_2" }.progress)
            assertEquals(8,list.single { it.code=="WEEKLY_PLAY_15" }.progress)
        }
    }

    @Test fun `HTTP published catalog settles actual match and one grant across legacy retry`() = runTest {
        database { source ->
            var now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val auth=AuthenticationService(PostgresAuthRepository(source),PasswordHasher(iterations=1000),{now})
            var account=assertIs<AuthResult.Success>(auth.register("player@example.com","GoodPassword123!","Player","android")).session
            val opponent=PostgresGuestIdentityRepository(source).resolveGuest("Opponent",null,now)
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val owner=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val context=MissionAdminContext(owner.token,owner.response.csrfToken,"https://admin.example")
            val service=MissionService(PostgresMissionRepository(source),admins,{now})
            execute(source,"UPDATE mission_definitions SET archived_from='2026-10-25'")
            testApplication {
                application { gameModule(authService=auth,adminService=admins,missionService=service,giftDeliveryEnabled=false) }
                suspend fun create(code:String,target:Int,gold:Int):MissionAdminView {
                    val response=client.post("/admin/api/missions") {
                        contentType(ContentType.Application.Json);header(HttpHeaders.Origin,"https://admin.example")
                        header(HttpHeaders.Cookie,"fasttowin_admin=${owner.token}");header("X-Admin-CSRF",owner.response.csrfToken)
                        setBody(Json.encodeToString(MissionDraftRequest(MissionConfig(code,MissionScope.PERSONAL,MissionAction.PLAY,MissionCadence.DAILY,MissionDifficulty.NORMAL,target,MissionReward(10,gold,1)),reason="Fixture",requestId="create-$code")))
                    }
                    assertEquals(HttpStatusCode.OK,response.status,response.bodyAsText())
                    return Json.decodeFromString(response.bodyAsText())
                }
                val one=create("HTTP_PLAY_ONE",1,77);val three=create("HTTP_PLAY_THREE",3,111)
                for(value in listOf(one,three)) {
                    val response=client.post("/admin/api/missions/${value.id}/publish") {
                        contentType(ContentType.Application.Json);header(HttpHeaders.Origin,context.origin)
                        header(HttpHeaders.Cookie,"fasttowin_admin=${owner.token}");header("X-Admin-CSRF",owner.response.csrfToken)
                        setBody(Json.encodeToString(MissionMutationRequest(value.version,"Publish","publish-${value.id}")))
                    }
                    assertEquals(HttpStatusCode.OK,response.status,response.bodyAsText())
                }
                now=Instant.parse("2026-10-25T06:00:00Z").toEpochMilli()
                account=assertIs<AuthResult.Success>(auth.login("player@example.com","GoodPassword123!","android")).session
                val match=CompletedMatch(UUID.randomUUID().toString(),"Mission HTTP",ProtocolGameMode.ORDER,now-1000,now,account.userId,
                    listOf(CompletedMatchPlayer(account.userId,"Player",1,MatchOutcome.WIN),CompletedMatchPlayer(opponent.playerId,"Opponent",0,MatchOutcome.LOSS)),matchType=MatchType.CASUAL)
                PostgresMatchResultRepository(source).save(match);PostgresMatchResultRepository(source).save(match)
                val response=client.get("/api/missions/personal") { bearerAuth(account.accessToken) }
                assertEquals(HttpStatusCode.OK,response.status);assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
                val missions=Json.decodeFromString<MissionListResponse>(response.bodyAsText()).items.filter { it.code.startsWith("HTTP_") }.sortedBy { it.target }
                assertEquals(listOf(1,3),missions.map { it.target });assertEquals(listOf(1,1),missions.map { it.progress })
                val claim=client.post("/api/missions/${missions.first().instanceId}/claim") { bearerAuth(account.accessToken);contentType(ContentType.Application.Json);setBody("{\"requestId\":\"http-claim\"}") }
                assertEquals(HttpStatusCode.OK,claim.status,claim.bodyAsText())
                val legacy=PostgresPlayerProfileRepository(source,Clock.fixed(Instant.ofEpochMilli(now),ZoneId.of("Asia/Bangkok"))).claimMissionReward(account.userId,"HTTP_PLAY_ONE")
                assertEquals(MissionRewardClaimStatus.ALREADY_CLAIMED,legacy?.status)
                assertEquals(listOf(257,1,54),balances(source,account.userId)) // 80/24 match +100/20 first-match achievement +77/10 mission.
                assertEquals(1,number(source,"SELECT count(*) FROM wallet_transactions WHERE user_id='${account.userId}' AND source_type='MISSION'"))
                assertEquals(1,number(source,"SELECT count(*) FROM wallet_transactions WHERE user_id='${account.userId}' AND source_type='MATCH'"))
            }
        }
    }

    @Test fun `HTTP current role CSRF identity membership payload and storage failures are enforced`() = runTest {
        database { source ->
            val now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val auth=AuthenticationService(PostgresAuthRepository(source),PasswordHasher(iterations=1000),{now})
            val account=assertIs<AuthResult.Success>(auth.register("auth-player@example.com","GoodPassword123!","Player","android")).session
            val other=PostgresGuestIdentityRepository(source).resolveGuest("Other",null,now).playerId
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            val actor=admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val service=MissionService(PostgresMissionRepository(source),admins,{now})
            val context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val config=MissionConfig("AUTH_DRAFT",MissionScope.PERSONAL,MissionAction.PLAY,MissionCadence.DAILY,MissionDifficulty.NORMAL,1,MissionReward(0,15))
            val draft=service.create(context,MissionDraftRequest(config,reason="Fixture",requestId="create"))
            val otherInstance=service.personal(other).items.first().instanceId
            val clan=clan(source,account.userId,listOf(other))
            markLegacyClan(source,clan)
            val clanInstance=service.clan(account.userId).items.single { it.code=="LEGACY_CLAN_QUEST" }.instanceId
            testApplication {
                application { gameModule(authService=auth,adminService=admins,missionService=service,giftDeliveryEnabled=false) }
                assertEquals(HttpStatusCode.Unauthorized,client.get("/api/missions").status)
                assertEquals(HttpStatusCode.Unauthorized,client.get("/admin/api/missions") { bearerAuth(account.accessToken) }.status)
                val unknown=client.post("/api/missions/$otherInstance/claim") { bearerAuth(account.accessToken);contentType(ContentType.Application.Json);setBody("{\"requestId\":\"other\"}") }
                assertEquals(HttpStatusCode.NotFound,unknown.status)
                execute(source,"DELETE FROM clan_members WHERE clan_id='$clan' AND user_id='${account.userId}'")
                val left=client.post("/api/missions/$clanInstance/claim") { bearerAuth(account.accessToken);contentType(ContentType.Application.Json);setBody("{\"requestId\":\"left\"}") }
                assertEquals(HttpStatusCode.Forbidden,left.status)
                suspend fun publish(csrf:String=login.response.csrfToken,origin:String="https://admin.example",version:Long=draft.version)=client.post("/admin/api/missions/${draft.id}/publish") {
                    contentType(ContentType.Application.Json);header(HttpHeaders.Origin,origin);header(HttpHeaders.Cookie,"fasttowin_admin=${login.token}");header("X-Admin-CSRF",csrf)
                    setBody(Json.encodeToString(MissionMutationRequest(version,"Publish","publish-$version")))
                }
                assertEquals(HttpStatusCode.Forbidden,publish(csrf="bad").status)
                assertEquals(HttpStatusCode.Forbidden,publish(origin="https://evil.example").status)
                execute(source,"UPDATE admin_accounts SET role='OPERATOR' WHERE id='${actor.id}'")
                assertEquals(HttpStatusCode.Forbidden,publish().status)
                execute(source,"UPDATE admin_accounts SET role='SUPPORT' WHERE id='${actor.id}'")
                assertEquals(HttpStatusCode.Forbidden,client.get("/admin/api/missions") { header(HttpHeaders.Cookie,"fasttowin_admin=${login.token}") }.status)
                execute(source,"UPDATE admin_accounts SET role='OWNER' WHERE id='${actor.id}'")
                assertEquals(HttpStatusCode.Conflict,publish(version=99).status)
                val oversized=client.post("/admin/api/missions") { contentType(ContentType.Application.Json);setBody("x".repeat(32769)) }
                assertEquals(HttpStatusCode.BadRequest,oversized.status)
                val invalid=client.post("/api/missions/$otherInstance/claim") { bearerAuth(account.accessToken);contentType(ContentType.Application.Json);setBody("{\"requestId\":\"test\",\"rewardGold\":10000}") }
                assertEquals(HttpStatusCode.BadRequest,invalid.status)
                assertEquals("no-store",invalid.headers[HttpHeaders.CacheControl])
            }
            testApplication {
                application { gameModule(authService=auth,missionService=MissionService(null),giftDeliveryEnabled=false) }
                val response=client.get("/api/missions") { bearerAuth(account.accessToken) }
                assertEquals(HttpStatusCode.ServiceUnavailable,response.status)
                assertTrue(response.bodyAsText().contains("STORAGE_UNAVAILABLE"))
            }
        }
    }

    @Test fun `legacy import preserves modified rewards and multiple clan claim identities idempotently`() = runTest {
        database { source ->
            val ids=PostgresGuestIdentityRepository(source)
            val users=(1..3).map { ids.resolveGuest("Legacy $it",null,it.toLong()).playerId }
            val player=users.first();val clan=clan(source,player,users.drop(1))
            execute(source,"UPDATE clans SET quest_target=10,quest_progress=10,quest_reward_gold=15,quest_reward_xp=2,quest_reward_gems=1 WHERE id='$clan'")
            execute(source,"UPDATE clan_members SET quest_reward_claimed=TRUE,quest_contribution=3 WHERE clan_id='$clan' AND user_id IN ('${users[0]}','${users[1]}')")
            markLegacyClan(source,clan)
            execute(source,"INSERT INTO user_missions(user_id,mission_code,period_start,progress,target,reward_xp,reward_gold,reward_gems) VALUES ('$player','DAILY_PLAY_1','2026-10-24',7,7,0,9,0)")
            val repo=PostgresMissionRepository(source)
            repo.transaction { migrateLegacy();migrateLegacy() }
            val now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val service=MissionService(repo,nowMillis={now})
            val personal=service.personal(player).items.single { it.code=="DAILY_PLAY_1" }
            assertEquals(7,personal.target);assertEquals(7,personal.progress);assertEquals(MissionReward(0,9),personal.reward)
            val legacyProfile=PostgresPlayerProfileRepository(source,Clock.fixed(Instant.ofEpochMilli(now),ZoneId.of("Asia/Bangkok"))).findByPlayerId(player)!!
            assertEquals("Chơi 7 trận",legacyProfile.progression.dailyMissions.single { it.code=="DAILY_PLAY_1" }.title)
            service.claim(player,personal.instanceId,MissionClaimRequest("legacy-personal-http"))
            assertEquals(MissionRewardClaimStatus.ALREADY_CLAIMED,PostgresPlayerProfileRepository(source,Clock.fixed(Instant.ofEpochMilli(now),ZoneId.of("Asia/Bangkok"))).claimMissionReward(player,"DAILY_PLAY_1")?.status)
            assertEquals(listOf(9,0,0),balances(source,player))
            assertEquals(MissionClaimState.CLAIMED,service.clan(users[0]).items.single { it.code=="LEGACY_CLAN_QUEST" }.state)
            assertEquals(MissionClaimState.CLAIMED,service.clan(users[1]).items.single { it.code=="LEGACY_CLAN_QUEST" }.state)
            val unclaimed=service.clan(users[2]).items.single { it.code=="LEGACY_CLAN_QUEST" }
            assertEquals(MissionClaimState.CLAIMABLE,unclaimed.state);assertEquals(0,unclaimed.personalContribution)
            service.claim(users[2],unclaimed.instanceId,MissionClaimRequest("legacy-clan-http"))
            assertFalse(PostgresClanRepository(source).claimQuestReward(clan,users[2]))
            assertEquals(listOf(15,1,2),balances(source,users[2]))
            repo.transaction { migrateLegacy() }
            assertEquals(3,number(source,"SELECT count(*) FROM mission_claims WHERE instance_id='${unclaimed.instanceId}'"))
            assertEquals(1,number(source,"SELECT count(*) FROM wallet_transactions WHERE user_id='${users[2]}' AND source_type='CLAN_QUEST' AND source_id='$clan'"))
        }
    }

    @Test fun `wallet SQL failure rolls back claim then simultaneous retry grants exactly once`() = runTest {
        database { source ->
            val player=PostgresGuestIdentityRepository(source).resolveGuest("Rollback",null,1000).playerId
            execute(source,"INSERT INTO user_missions(user_id,mission_code,period_start,progress,target,reward_xp,reward_gold,reward_gems) VALUES ('$player','DAILY_PLAY_1','2026-10-24',1,1,4,12,2)")
            execute(source,"INSERT INTO player_stats(user_id,gold,gems,experience_points,updated_at) VALUES ('$player',100,5,8,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO UPDATE SET gold=100,gems=5,experience_points=8")
            val time=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val repo=PostgresMissionRepository(source);val service=MissionService(repo,nowMillis={time})
            val instance=service.personal(player).items.single { it.code=="DAILY_PLAY_1" }.instanceId
            execute(source,"CREATE FUNCTION reject_mission_wallet() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''fixture rollback''; END'")
            execute(source,"CREATE TRIGGER reject_mission_wallet BEFORE INSERT ON mission_claims FOR EACH ROW EXECUTE FUNCTION reject_mission_wallet()")
            val error=assertFailsWith<AdminException> { service.claim(player,instance,MissionClaimRequest("retry")) }
            assertEquals(503,error.status);assertEquals(listOf(100,5,8),balances(source,player))
            assertEquals(0,number(source,"SELECT count(*) FROM mission_claims WHERE instance_id='$instance'"))
            execute(source,"DROP TRIGGER reject_mission_wallet ON mission_claims")
            withContext(Dispatchers.Default) { withTimeout(10_000) { (1..6).map { async { service.claim(player,instance,MissionClaimRequest("retry")) } }.awaitAll() } }
            assertEquals(listOf(112,7,12),balances(source,player))
            assertEquals(1,number(source,"SELECT count(*) FROM wallet_transactions WHERE user_id='$player' AND source_type='MISSION'"))
            assertEquals(1,number(source,"SELECT count(*) FROM mission_claims WHERE instance_id='$instance'"))
        }
    }

    @Test fun `actual check in and match callers race claim without a transaction lock inversion`() = runTest {
        database { source ->
            val ids=PostgresGuestIdentityRepository(source);val player=ids.resolveGuest("Race",null,1000);val opponent=ids.resolveGuest("Opponent",null,1001)
            val time=Instant.parse("2026-09-03T06:00:00Z").toEpochMilli()
            val clock=Clock.fixed(Instant.ofEpochMilli(time),ZoneId.of("Asia/Bangkok"));val profile=PostgresPlayerProfileRepository(source,clock)
            profile.claimDailyCheckIn(player.playerId)
            val service=MissionService(PostgresMissionRepository(source),nowMillis={time})
            val instance=service.personal(player.playerId).items.single { it.code=="DAILY_CHECK_IN" }.instanceId
            val match=CompletedMatch(UUID.randomUUID().toString(),"Race",ProtocolGameMode.ORDER,time-1000,time,player.playerId,
                listOf(CompletedMatchPlayer(player.playerId,"Race",1,MatchOutcome.WIN),CompletedMatchPlayer(opponent.playerId,"Opponent",0,MatchOutcome.LOSS)),matchType=MatchType.CASUAL)
            withContext(Dispatchers.Default) { withTimeout(10_000) {
                listOf(async { service.claim(player.playerId,instance,MissionClaimRequest("race")) },async { profile.claimDailyCheckIn(player.playerId) },async { PostgresMatchResultRepository(source).save(match) }).awaitAll()
            } }
            assertEquals(1,number(source,"SELECT count(*) FROM wallet_transactions WHERE user_id='${player.playerId}' AND source_type='MISSION'"))
            assertEquals(1,service.personal(player.playerId).items.single { it.code=="DAILY_CHECK_IN" }.progress)
        }
    }

    @Test fun `archive retains earned instance across rollover and preview warns of sparse future pool`() = runTest {
        database { source ->
            var now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val player=PostgresGuestIdentityRepository(source).resolveGuest("Archive",null,1000).playerId
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            suspend fun context():MissionAdminContext { val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1");return MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example") }
            val service=MissionService(PostgresMissionRepository(source),admins,{now})
            execute(source,"UPDATE mission_definitions SET archived_from='2026-10-25'")
            val context=context()
            val draft=service.create(context,MissionDraftRequest(MissionConfig("ARCHIVE_PLAY",MissionScope.PERSONAL,MissionAction.PLAY,MissionCadence.DAILY,MissionDifficulty.NORMAL,1,MissionReward(0,13)),reason="Fixture",requestId="create"))
            val preview=service.preview(context,draft.id)
            assertEquals("2026-10-25",preview.effectiveFrom);assertEquals(listOf("MISSION_POOL_SHORTAGE"),preview.warnings)
            val request=MissionMutationRequest(draft.version,"Publish","publish")
            val published=service.publish(context,draft.id,request)
            assertEquals(published,service.publish(context,draft.id,request))
            assertFailsWith<java.sql.SQLException> { execute(source,"UPDATE mission_revisions SET reward_gold=999 WHERE definition_id='${draft.id}'") }
            assertFailsWith<AdminException> { service.deleteDraft(context,draft.id,MissionMutationRequest(published.version,"Delete","delete")) }
            now=Instant.parse("2026-10-25T06:00:00Z").toEpochMilli()
            val repo=PostgresMissionRepository(source)
            repo.transaction { record(MissionProgressEvent("match:earned",player,now,played=true)) }
            val earned=service.personal(player).items.single { it.code=="ARCHIVE_PLAY" }
            val renewed=context()
            service.archive(renewed,draft.id,MissionMutationRequest(published.version,"Stop next period","archive"))
            now=Instant.parse("2026-10-26T06:00:00Z").toEpochMilli()
            val retained=service.personal(player).items.filter { it.code=="ARCHIVE_PLAY" }
            assertEquals(listOf(earned.instanceId),retained.map { it.instanceId });assertEquals("2026-10-25",retained.single().periodStart)
            service.claim(player,earned.instanceId,MissionClaimRequest("earned-after-archive"))
            assertEquals(listOf(13,0,0),balances(source,player))
            assertEquals(1,number(source,"SELECT count(*) FROM mission_instances WHERE owner_id='$player' AND definition_id='${draft.id}'"))
        }
    }

    @Test fun `donation progress and mission contribution retain distinct replay identities`() = runTest {
        database { source ->
            var now=System.currentTimeMillis()-86_400_000
            val player=PostgresGuestIdentityRepository(source).resolveGuest("Donor",null,now).playerId
            val clan=clan(source,player,emptyList())
            execute(source,"INSERT INTO player_stats(user_id,gold,gems,experience_points,updated_at) VALUES ('$player',1000,5,0,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO UPDATE SET gold=1000,gems=5,experience_points=0")
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val service=MissionService(PostgresMissionRepository(source),admins,{now})
            val config=MissionConfig("CLAN_DONATION",MissionScope.CLAN,MissionAction.DONATE_GOLD,MissionCadence.DAILY,MissionDifficulty.NORMAL,100,MissionReward(3,0,2,7))
            val goldRefund=assertFailsWith<AdminException> { service.create(context,MissionDraftRequest(config.copy(reward=MissionReward(gold=20)),reason="Unsupported clan refund",requestId="gold-refund")) }
            assertEquals("CLAN_DONATION_REFUND_UNSUPPORTED",goldRefund.code)
            val gemsRefund=assertFailsWith<AdminException> { service.create(context,MissionDraftRequest(config.copy(action=MissionAction.DONATE_GEMS),reason="Unsupported clan refund",requestId="gems-refund")) }
            assertEquals("CLAN_DONATION_REFUND_UNSUPPORTED",gemsRefund.code)
            assertEquals(400,assertFailsWith<AdminException> { service.create(context,MissionDraftRequest(config.copy(reward=MissionReward(gold=101)),reason="Over refund",requestId="invalid")) }.status)
            val draft=service.create(context,MissionDraftRequest(config,reason="Fixture",requestId="create"))
            service.publish(context,draft.id,MissionMutationRequest(draft.version,"Publish","publish"))
            now=System.currentTimeMillis()
            val donations=PostgresClanRepository(source);val request=UUID.randomUUID().toString()
            assertEquals(ClanDonationStatus.APPLIED,donations.donateToClan(player,clan,request,ClanDonationCurrency.GOLD,100).status)
            assertEquals(ClanDonationStatus.DUPLICATE,donations.donateToClan(player,clan,request,ClanDonationCurrency.GOLD,100).status)
            val instance=service.clan(player).items.single { it.code=="CLAN_DONATION" }
            assertEquals(100,instance.progress);assertEquals(100,instance.personalContribution)
            service.claim(player,instance.instanceId,MissionClaimRequest("donation-claim"));service.claim(player,instance.instanceId,MissionClaimRequest("donation-claim"))
            assertEquals(listOf(900,7,3),balances(source,player))
            assertEquals(1,number(source,"SELECT count(*) FROM mission_contribution_events WHERE user_id='$player' AND source_type='MISSION' AND points=7"))
            assertEquals(1,number(source,"SELECT count(*) FROM mission_contribution_events WHERE user_id='$player' AND source_type='DONATION'"))
            assertEquals(2,number(source,"SELECT count(DISTINCT source_id) FROM mission_contribution_events WHERE user_id='$player'"))
        }
    }

    @Test fun `clan streak counts unique members reaching personal threshold without summing streaks`() = runTest {
        database { source ->
            var now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val ids=PostgresGuestIdentityRepository(source);val one=ids.resolveGuest("One",null,1000).playerId;val two=ids.resolveGuest("Two",null,1001).playerId
            clan(source,one,listOf(two))
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val service=MissionService(PostgresMissionRepository(source),admins,{now})
            val config=MissionConfig("CLAN_STREAK",MissionScope.CLAN,MissionAction.WIN_STREAK,MissionCadence.DAILY,MissionDifficulty.HARD,2,MissionReward(),streakThreshold=3)
            val draft=service.create(context,MissionDraftRequest(config,reason="Fixture",requestId="create"))
            service.publish(context,draft.id,MissionMutationRequest(draft.version,"Publish","publish"))
            now=Instant.parse("2026-10-25T06:00:00Z").toEpochMilli()
            val repo=PostgresMissionRepository(source)
            repo.transaction { record(MissionProgressEvent("match:1",one,now,winStreak=2));record(MissionProgressEvent("match:2",two,now,winStreak=2)) }
            assertEquals(0,service.clan(one).items.single { it.code=="CLAN_STREAK" }.progress)
            repo.transaction { record(MissionProgressEvent("match:3",one,now,winStreak=3));record(MissionProgressEvent("match:4",one,now,winStreak=4)) }
            assertEquals(1,service.clan(one).items.single { it.code=="CLAN_STREAK" }.progress)
            repo.transaction { record(MissionProgressEvent("match:5",two,now,winStreak=3)) }
            val instance=service.clan(two).items.single { it.code=="CLAN_STREAK" }
            assertEquals(2,instance.progress);assertEquals(1,instance.personalContribution)
        }
    }
    @Test fun `new clan has no phantom static legacy quest after catalog cutover`() = runTest {
        database { source ->
            val player=PostgresGuestIdentityRepository(source).resolveGuest("New clan",null,1000).playerId
            val clan=clan(source,player,emptyList())
            val service=MissionService(PostgresMissionRepository(source))
            assertEquals(emptyList(),service.clan(player).items)
            assertNull(PostgresClanRepository(source).getClanById(clan)?.quest)
            execute(source,"UPDATE clans SET quest_progress=quest_target WHERE id='$clan'")
            assertFalse(PostgresClanRepository(source).claimQuestReward(clan,player))
            assertEquals(0,number(source,"SELECT count(*) FROM mission_instances WHERE owner_id='$clan'"))
        }
    }
    @Test fun `V63 upgrade freezes existing clan rewards and every pre-cutover member claim`() = runTest {
        database(target="63") { source ->
            val ids=PostgresGuestIdentityRepository(source)
            val users=(1..3).map { ids.resolveGuest("Upgrade $it",null,it.toLong()).playerId }
            val clan=clan(source,users[0],users.drop(1))
            execute(source,"UPDATE clans SET quest_target=12,quest_progress=12,quest_reward_gold=19,quest_reward_xp=3,quest_reward_gems=2 WHERE id='$clan'")
            execute(source,"UPDATE clan_members SET quest_reward_claimed=TRUE,quest_contribution=6 WHERE clan_id='$clan' AND user_id IN ('${users[0]}','${users[1]}')")
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
            assertEquals(1,number(source,"SELECT count(*) FROM mission_legacy_clans WHERE clan_id='$clan' AND target=12 AND reward_gold=19"))
            assertEquals(2,number(source,"SELECT count(*) FROM mission_legacy_clan_members WHERE clan_id='$clan' AND claimed"))
            val repo=PostgresMissionRepository(source);repo.transaction { migrateLegacy();migrateLegacy() }
            val service=MissionService(repo)
            assertEquals(MissionClaimState.CLAIMED,service.clan(users[0]).items.single().state)
            val unclaimed=service.clan(users[2]).items.single()
            assertEquals(MissionReward(3,19,2),unclaimed.reward)
            assertEquals(MissionClaimState.CLAIMABLE,unclaimed.state)
            service.claim(users[2],unclaimed.instanceId,MissionClaimRequest("upgrade-claim"))
            assertEquals(listOf(19,2,3),balances(source,users[2]))
            execute(source,"DELETE FROM clan_members WHERE clan_id='$clan' AND user_id='${users[0]}'")
            execute(source,"INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES ('$clan','${users[0]}','MEMBER',CURRENT_TIMESTAMP)")
            assertEquals(MissionClaimState.CLAIMED,service.clan(users[0]).items.single().state)
            assertEquals(0,number(source,"SELECT count(*) FROM wallet_transactions WHERE user_id='${users[0]}'"))
        }
    }

    @Test fun `editing draft action cannot rewrite an unmigrated legacy instance`() = runTest {
        database { source ->
            val now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val player=PostgresGuestIdentityRepository(source).resolveGuest("Lazy legacy",null,1000).playerId
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val repo=PostgresMissionRepository(source);val service=MissionService(repo,admins,{now})
            val legacy=service.list(context,PageRequest(pageSize=100)).items.single { it.draft.code=="DAILY_PLAY_1" }
            service.update(context,legacy.id,MissionDraftRequest(legacy.draft.copy(action=MissionAction.WIN,target=99,reward=MissionReward(gold=999)),legacy.version,"Future change","edit"))
            execute(source,"INSERT INTO user_missions(user_id,mission_code,period_start,progress,target,reward_xp,reward_gold,reward_gems) VALUES ('$player','DAILY_PLAY_1','2026-10-24',0,7,0,9,0)")
            repo.transaction { record(MissionProgressEvent("match:legacy",player,now,played=true,won=false)) }
            val instance=service.personal(player).items.single { it.code=="DAILY_PLAY_1" }
            assertEquals(MissionAction.PLAY,instance.action);assertEquals(1,instance.progress);assertEquals(7,instance.target);assertEquals(MissionReward(0,9),instance.reward)
        }
    }
    @Test fun `legacy profile snapshot works with one available database connection`() = runTest {
        database { source ->
            val player=PostgresGuestIdentityRepository(source).resolveGuest("One connection",null,1000).playerId
            HikariDataSource(HikariConfig().apply {
                jdbcUrl=source.jdbcUrl;username=source.username;password=source.password;maximumPoolSize=1;connectionTimeout=500
            }).use { single ->
                assertNotNull(PostgresPlayerProfileRepository(single).findByPlayerId(player))
            }
        }
    }
    @Test fun `admin validates draft fields and deletes unused drafts with durable request audit`() = runTest {
        database { source ->
            val now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val service=MissionService(PostgresMissionRepository(source),admins,{now})
            val valid=MissionConfig("VALID_DRAFT",MissionScope.PERSONAL,MissionAction.PLAY,MissionCadence.DAILY,MissionDifficulty.NORMAL,1,MissionReward(gold=5))
            val invalid=listOf(valid.copy(target=0),valid.copy(target=-1),valid.copy(target=Int.MAX_VALUE),valid.copy(reward=MissionReward(gold=-1)),
                valid.copy(reward=MissionReward(xp=Int.MAX_VALUE)),valid.copy(startsOn="2026-11-10",endsOn="2026-11-09"),valid.copy(startsOn="not-a-date"),
                valid.copy(action=MissionAction.ACCURACY),valid.copy(action=MissionAction.ACCURACY,accuracyPercent=101),valid.copy(accuracyPercent=90),
                valid.copy(scope=MissionScope.CLAN,action=MissionAction.WIN_STREAK),valid.copy(streakThreshold=3),valid.copy(reward=MissionReward(contributionPoints=1)),
                valid.copy(action=MissionAction.CHECK_IN,matchType=MatchType.CASUAL),valid.copy(code="bad code"))
            for((index,value) in invalid.withIndex()) assertEquals(400,assertFailsWith<AdminException> { service.create(context,MissionDraftRequest(value,reason="Validation fixture",requestId="invalid-$index")) }.status)
            assertEquals(18L,service.list(context,PageRequest(pageSize=100)).total)
            val draft=service.create(context,MissionDraftRequest(valid,reason="Create",requestId="create"))
            val edit=MissionDraftRequest(valid.copy(target=3),draft.version,"Increase target","edit")
            val changed=service.update(context,draft.id,edit)
            assertEquals(changed,service.update(context,draft.id,edit))
            assertEquals(409,assertFailsWith<AdminException> { service.update(context,draft.id,edit.copy(reason="Other payload")) }.status)
            val request=MissionMutationRequest(changed.version,"Remove unused","delete")
            assertEquals(changed,service.deleteDraft(context,draft.id,request))
            assertEquals(changed,service.deleteDraft(context,draft.id,request))
            assertEquals(404,assertFailsWith<AdminException> { service.detail(context,draft.id) }.status)
            assertEquals(1,number(source,"SELECT count(*) FROM admin_audit WHERE action='MISSION_UPDATE' AND target_id='${draft.id}' AND before_summary IS NOT NULL AND after_summary IS NOT NULL"))
            assertEquals(1,number(source,"SELECT count(*) FROM admin_audit WHERE action='MISSION_DELETE' AND target_id='${draft.id}' AND reason='Remove unused'"))
        }
    }
    @Test fun `archive ignores unpublished weekly draft and stops the next published daily period`() = runTest {
        database { source ->
            var now=Instant.parse("2026-10-23T06:00:00Z").toEpochMilli()
            val player=PostgresGuestIdentityRepository(source).resolveGuest("Daily archive",null,1000).playerId
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            suspend fun context():MissionAdminContext { val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1");return MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example") }
            execute(source,"UPDATE mission_definitions SET archived_from='2026-10-24'")
            val repo=PostgresMissionRepository(source);val service=MissionService(repo,admins,{now})
            val config=MissionConfig("ARCHIVE_MIXED_DAILY",MissionScope.PERSONAL,MissionAction.PLAY,MissionCadence.DAILY,MissionDifficulty.NORMAL,1,MissionReward(gold=13))
            val draft=service.create(context(),MissionDraftRequest(config,reason="Fixture",requestId="create"))
            val published=service.publish(context(),draft.id,MissionMutationRequest(draft.version,"Publish","publish"))
            now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            repo.transaction { record(MissionProgressEvent("match:earned",player,now,played=true)) }
            val earned=service.personal(player).items.single { it.code==config.code }
            val context=context()
            val edited=service.update(context,draft.id,MissionDraftRequest(config.copy(period=MissionCadence.WEEKLY),published.version,"Unpublished weekly draft","edit"))
            service.archive(context,draft.id,MissionMutationRequest(edited.version,"Stop next published period","archive"))
            now=Instant.parse("2026-10-25T06:00:00Z").toEpochMilli()
            val retained=service.personal(player).items.filter { it.code==config.code }
            assertEquals(listOf("2026-10-24"),retained.map { it.periodStart })
            assertEquals(listOf(earned.instanceId),retained.map { it.instanceId })
            assertEquals(1,number(source,"SELECT count(*) FROM mission_instances WHERE owner_id='$player' AND definition_id='${draft.id}'"))
            service.claim(player,earned.instanceId,MissionClaimRequest("earned-after-mixed-archive"))
            assertEquals(listOf(13,0,0),balances(source,player))
        }
    }

    @Test fun `archive follows current weekly schedule and cancels a pending daily revision despite later draft`() = runTest {
        for(pendingDaily in listOf(false,true)) database { source ->
            var now=Instant.parse("2026-10-18T06:00:00Z").toEpochMilli()
            val player=PostgresGuestIdentityRepository(source).resolveGuest("Weekly archive",null,1000).playerId
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            suspend fun context():MissionAdminContext { val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1");return MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example") }
            execute(source,"UPDATE mission_definitions SET archived_from='2026-10-19'")
            val repo=PostgresMissionRepository(source);val service=MissionService(repo,admins,{now})
            val config=MissionConfig("ARCHIVE_MIXED_WEEKLY",MissionScope.PERSONAL,MissionAction.PLAY,MissionCadence.WEEKLY,MissionDifficulty.NORMAL,1,MissionReward(gold=17))
            val draft=service.create(context(),MissionDraftRequest(config,reason="Fixture",requestId="create"))
            var value=service.publish(context(),draft.id,MissionMutationRequest(draft.version,"Publish","publish"))
            now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            repo.transaction { record(MissionProgressEvent("match:earned",player,now,played=true)) }
            val earned=service.personal(player).items.single { it.code==config.code }
            val context=context()
            value=service.update(context,draft.id,MissionDraftRequest(config.copy(period=MissionCadence.DAILY),value.version,"Daily draft","daily-draft"))
            if(pendingDaily) {
                value=service.publish(context,draft.id,MissionMutationRequest(value.version,"Schedule Sunday daily","publish-daily"))
                value=service.update(context,draft.id,MissionDraftRequest(config,value.version,"Unpublished weekly draft","weekly-draft"))
            }
            service.archive(context,draft.id,MissionMutationRequest(value.version,"Stop all published schedules","archive"))
            val cutoff=if(pendingDaily) "2026-10-25" else "2026-10-26"
            assertEquals(1,number(source,"SELECT count(*) FROM mission_definitions WHERE id='${draft.id}' AND archived_from='$cutoff'"))
            for(date in listOf("2026-10-25","2026-10-26")) {
                now=Instant.parse("${date}T06:00:00Z").toEpochMilli()
                assertEquals(listOf(earned.instanceId),service.personal(player).items.filter { it.code==config.code }.map { it.instanceId })
            }
            assertEquals(1,number(source,"SELECT count(*) FROM mission_instances WHERE owner_id='$player' AND definition_id='${draft.id}'"))
            service.claim(player,earned.instanceId,MissionClaimRequest("weekly-earned"))
            assertEquals(listOf(17,0,0),balances(source,player))
        }
    }

    @Test fun `legacy mode-specific snapshots preserve old localized keys and reject other match types`() = runTest {
        database { source ->
            val now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val clock=Clock.fixed(Instant.ofEpochMilli(now),ZoneId.of("Asia/Bangkok"))
            data class Case(val code:String,val start:String,val target:Int,val mode:MatchType,val key:String,val title:String,val english:String)
            val cases=listOf(
                Case("DAILY_CASUAL_2","2026-10-24",2,MatchType.CASUAL,"MissionCasualTwo","Chơi 2 trận đấu thường","Play 2 casual matches"),
                Case("DAILY_RANKED_2","2026-10-24",2,MatchType.RANKED,"MissionRankedTwo","Chơi 2 trận đấu hạng","Play 2 ranked matches"),
                Case("WEEKLY_RANKED_WIN_3","2026-10-19",3,MatchType.RANKED,"MissionWeeklyRankedWinThree","Thắng 3 trận đấu hạng","Win 3 ranked matches")
            )
            val repo=PostgresMissionRepository(source)
            for(case in cases) {
                val player=PostgresGuestIdentityRepository(source).resolveGuest("Legacy mode",null,1000).playerId
                execute(source,"INSERT INTO user_missions(user_id,mission_code,period_start,progress,target,reward_xp,reward_gold,reward_gems) VALUES ('$player','${case.code}','${case.start}',0,${case.target},0,0,0)")
                val profile=PostgresPlayerProfileRepository(source,clock)
                fun snapshot(value:PlayerProfileSnapshot)=(value.progression.dailyMissions+value.progression.weeklyMissions).single { it.code==case.code }
                val mission=snapshot(profile.findByPlayerId(player)!!)
                assertEquals(case.title,mission.title);assertEquals(case.key,mission.titleKey)
                assertEquals(case.english,legacyRenderedTitle(mission,AppLanguage.ENGLISH))
                val other=if(case.mode==MatchType.CASUAL) MatchType.RANKED else MatchType.CASUAL
                repo.transaction { record(MissionProgressEvent("match:other",player,now,matchType=other,played=true,won=true)) }
                assertEquals(0,snapshot(profile.findByPlayerId(player)!!).progress)
                repo.transaction { record(MissionProgressEvent("match:eligible",player,now,matchType=case.mode,played=true,won=true)) }
                assertEquals(1,snapshot(profile.findByPlayerId(player)!!).progress)
            }
        }
    }

    @Test fun `changed legacy targets use truthful mode-qualified fallback instead of a fixed old key`() = runTest {
        database { source ->
            val now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val clock=Clock.fixed(Instant.ofEpochMilli(now),ZoneId.of("Asia/Bangkok"))
            data class Case(val code:String,val start:String,val fixedKey:String,val title:String)
            val cases=listOf(
                Case("DAILY_CASUAL_2","2026-10-24","MissionCasualTwo","Chơi 7 trận (Đấu thường)"),
                Case("DAILY_RANKED_2","2026-10-24","MissionRankedTwo","Chơi 7 trận (Đấu xếp hạng)"),
                Case("WEEKLY_RANKED_WIN_3","2026-10-19","MissionWeeklyRankedWinThree","Thắng 7 trận (Đấu xếp hạng)")
            )
            for(case in cases) {
                val player=PostgresGuestIdentityRepository(source).resolveGuest("Changed legacy mode",null,1000).playerId
                execute(source,"INSERT INTO user_missions(user_id,mission_code,period_start,progress,target,reward_xp,reward_gold,reward_gems) VALUES ('$player','${case.code}','${case.start}',0,7,0,0,0)")
                val profile=PostgresPlayerProfileRepository(source,clock).findByPlayerId(player)!!
                val mission=(profile.progression.dailyMissions+profile.progression.weeklyMissions).single { it.code==case.code }
                assertEquals(7,mission.target);assertEquals(case.title,mission.title);assertNotEquals(case.fixedKey,mission.titleKey)
                // Legacy consumers supply emptyMap. They must show complete fallback, never a fixed2/3 target or broad unqualified action.
                assertEquals(case.title,legacyRenderedTitle(mission,AppLanguage.ENGLISH))
            }
        }
    }

    @Test fun `personal mode qualified streak create is rejected before persistence`() = typedStreakRejected(MissionScope.PERSONAL,"create")
    @Test fun `clan mode qualified streak create is rejected before persistence`() = typedStreakRejected(MissionScope.CLAN,"create")
    @Test fun `personal mode qualified streak update is rejected before persistence`() = typedStreakRejected(MissionScope.PERSONAL,"update")
    @Test fun `clan mode qualified streak update is rejected before persistence`() = typedStreakRejected(MissionScope.CLAN,"update")
    @Test fun `personal mode qualified streak publish is rejected before persistence`() = typedStreakRejected(MissionScope.PERSONAL,"publish")
    @Test fun `clan mode qualified streak publish is rejected before persistence`() = typedStreakRejected(MissionScope.CLAN,"publish")

    private fun typedStreakRejected(scope:MissionScope,operation:String) = runTest {
        database { source ->
            val now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val repo=PostgresMissionRepository(source);val service=MissionService(repo,admins,{now})
            testApplication {
                application { gameModule(adminService=admins,missionService=service,giftDeliveryEnabled=false) }
                for(mode in listOf(MatchType.CASUAL,MatchType.RANKED)) {
                    val code="TYPED_${scope.name}_${mode.name}"
                    val config=MissionConfig(code,scope,MissionAction.WIN_STREAK,MissionCadence.DAILY,MissionDifficulty.HARD,
                        if(scope==MissionScope.CLAN) 2 else 3,MissionReward(),matchType=mode,streakThreshold=if(scope==MissionScope.CLAN) 3 else null)
                    // Settlement's 2 CASUAL + 1 RANKED wins produce global streak3; neither is a three-win mode streak.
                    // Reject the unsupported condition rather than promising a per-mode statistic we do not store.
                    val draft=if(operation=="create") null else service.create(context,MissionDraftRequest(
                        config.copy(action=MissionAction.WIN,streakThreshold=null),reason="Supported mode fixture",requestId="setup-$mode"))
                    if(operation=="publish") repo.transaction { put(draft!!.copy(draft=config)) } // Pre-fix saved draft.
                    val before=draft?.let { service.detail(context,it.id) }
                    val definitions=number(source,"SELECT count(*) FROM mission_definitions")
                    val revisions=number(source,"SELECT count(*) FROM mission_revisions")
                    val route=when(operation) { "create" -> "/admin/api/missions";"update" -> "/admin/api/missions/${draft!!.id}/draft";else -> "/admin/api/missions/${draft!!.id}/publish" }
                    val response=client.request(route) {
                        method=if(operation=="update") HttpMethod.Put else HttpMethod.Post
                        contentType(ContentType.Application.Json);header(HttpHeaders.Origin,context.origin)
                        header(HttpHeaders.Cookie,"fasttowin_admin=${login.token}");header("X-Admin-CSRF",login.response.csrfToken)
                        setBody(if(operation=="publish") Json.encodeToString(MissionMutationRequest(draft!!.version,"Reject unsupported mode","reject-$mode"))
                            else Json.encodeToString(MissionDraftRequest(config,draft?.version?:0,"Reject unsupported mode","reject-$mode")))
                    }
                    assertEquals(HttpStatusCode.BadRequest,response.status,"$scope $mode $operation: ${response.bodyAsText()}")
                    assertEquals("INVALID_REQUEST",Json.decodeFromString<MissionApiError>(response.bodyAsText()).code)
                    assertEquals(definitions,number(source,"SELECT count(*) FROM mission_definitions"))
                    assertEquals(revisions,number(source,"SELECT count(*) FROM mission_revisions"))
                    if(before!=null) assertEquals(before,service.detail(context,before.id))
                }
            }
        }
    }

    @Test fun `unrestricted streak uses actual global mixed mode settlement and typed wins retain filtering`() = runTest {
        database { source ->
            var now=Instant.parse("2026-10-23T06:00:00Z").toEpochMilli()
            val ids=PostgresGuestIdentityRepository(source)
            val one=ids.resolveGuest("Mixed modes",null,1000).playerId;val two=ids.resolveGuest("Other",null,1001).playerId
            clan(source,one,listOf(two))
            val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
            admins.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admins.login("owner@example.com","GoodPassword123!","https://admin.example","127.0.0.1")
            val context=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val service=MissionService(PostgresMissionRepository(source),admins,{now})
            execute(source,"UPDATE mission_definitions SET archived_from='2026-10-24'")
            val configs=listOf(
                MissionConfig("GLOBAL_PERSONAL",MissionScope.PERSONAL,MissionAction.WIN_STREAK,MissionCadence.DAILY,MissionDifficulty.HARD,3,MissionReward()),
                MissionConfig("GLOBAL_CLAN",MissionScope.CLAN,MissionAction.WIN_STREAK,MissionCadence.DAILY,MissionDifficulty.HARD,2,MissionReward(),streakThreshold=3),
                MissionConfig("RANKED_WINS",MissionScope.PERSONAL,MissionAction.WIN,MissionCadence.DAILY,MissionDifficulty.NORMAL,3,MissionReward(),matchType=MatchType.RANKED))
            for(config in configs) {
                val draft=service.create(context,MissionDraftRequest(config,reason="Global semantics",requestId="create-${config.code}"))
                service.publish(context,draft.id,MissionMutationRequest(draft.version,"Publish","publish-${config.code}"))
            }
            now=System.currentTimeMillis()
            val matches=PostgresMatchResultRepository(source)
            for((index,mode) in listOf(MatchType.CASUAL,MatchType.CASUAL,MatchType.RANKED).withIndex()) {
                val match=CompletedMatch(UUID.randomUUID().toString(),"Mixed mode streak",ProtocolGameMode.ORDER,now-1000,now,one,
                    listOf(CompletedMatchPlayer(one,"Mixed modes",1,MatchOutcome.WIN),CompletedMatchPlayer(two,"Other",0,MatchOutcome.LOSS)),matchType=mode)
                matches.save(match);matches.save(match)
                val personal=service.personal(one).items
                assertEquals(if(index==2) 3 else 0,personal.single { it.code=="GLOBAL_PERSONAL" }.progress)
                assertEquals(if(index==2) 1 else 0,personal.single { it.code=="RANKED_WINS" }.progress)
                assertEquals(if(index==2) 1 else 0,service.clan(one).items.single { it.code=="GLOBAL_CLAN" }.progress)
            }
            assertEquals(3,number(source,"SELECT current_win_streak FROM player_stats WHERE user_id='$one'"))
            assertEquals(1,service.clan(one).items.single { it.code=="GLOBAL_CLAN" }.personalContribution)
        }
    }

    private fun legacyRenderedTitle(mission:MissionSnapshot,language:AppLanguage):String =
        mission.titleKey?.let { raw -> TextKey.entries.firstOrNull { it.name==raw } }
            ?.let { key -> runCatching { LocalizationService(language).text(key,emptyMap()) }.getOrNull() } ?: mission.title
    private fun markLegacyClan(source:HikariDataSource,clan:String) {
        execute(source,"INSERT INTO mission_legacy_clans(clan_id,target,reward_xp,reward_gold,reward_gems) SELECT id,quest_target,quest_reward_xp,quest_reward_gold,quest_reward_gems FROM clans WHERE id='$clan'")
        execute(source,"INSERT INTO mission_legacy_clan_members(clan_id,user_id,contribution,claimed) SELECT clan_id,user_id,quest_contribution,quest_reward_claimed FROM clan_members WHERE clan_id='$clan'")
    }
    private fun execute(source:HikariDataSource,sql:String) { source.connection.use { it.createStatement().use { s -> s.execute(sql) } } }
    private fun number(source:HikariDataSource,sql:String):Int=source.connection.use { it.createStatement().use { s -> s.executeQuery(sql).use { r -> r.next();r.getInt(1) } } }
    private fun balances(source:HikariDataSource,user:String):List<Int> = source.connection.use { it.createStatement().use { s -> s.executeQuery("SELECT gold,gems,experience_points FROM player_stats WHERE user_id='$user'").use { r -> r.next();listOf(r.getInt(1),r.getInt(2),r.getInt(3)) } } }
    private fun clan(source:HikariDataSource,owner:String,members:List<String>):String {
        val clan=UUID.randomUUID().toString()
        execute(source,"INSERT INTO clans(id,name,owner_id,created_at) VALUES ('$clan','Fixture clan','$owner',CURRENT_TIMESTAMP)")
        for(player in listOf(owner)+members) execute(source,"INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES ('$clan','$player','MEMBER',CURRENT_TIMESTAMP)")
        return clan
    }
    private suspend fun database(target:String?=null,block:suspend (HikariDataSource)->Unit) {
        val url=System.getenv("TEST_DATABASE_URL") ?: return
        val schema="mission_fixture_"+UUID.randomUUID().toString().replace("-","")
        fun source(url:String)=HikariDataSource(HikariConfig().apply { jdbcUrl=url;username=System.getenv("TEST_DATABASE_USER")?:"fasttowin";password=System.getenv("TEST_DATABASE_PASSWORD")?:"fasttowin";maximumPoolSize=4 })
        source(url).use { owner ->
            owner.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                source(url.substringBefore('?')+"?currentSchema=$schema").use { source ->
                    val configuration=Flyway.configure().dataSource(source).locations("classpath:db/migration")
                    if(target!=null) configuration.target(org.flywaydb.core.api.MigrationVersion.fromVersion(target))
                    configuration.load().migrate();block(source)
                }
            } finally { owner.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
}
