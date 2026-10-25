package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.missions.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

internal suspend fun RaceFixture.publishRace(week:String="2026-10-26",rate:String="0.0001") {
    val service=ClanRaceService(PostgresClanRaceRepository(source),admins,{now})
    val context=ClanRaceAdminContext(login.token,login.response.csrfToken,"https://admin.example")
    val draft=service.create(context,ClanRaceDraftRequest(week,ClanRaceConfig(rate,"1.5",listOf(ClanRaceReward(30,300,3),ClanRaceReward(20,200,2),ClanRaceReward(10,100,1))),reason="Fixture",requestId="race-$week"))
    service.publish(context,week,ClanRaceMutationRequest(draft.version,"Publish","publish-$week"))
}
internal fun RaceFixture.fund(user:String,gold:Int=100000,gems:Int=100) { execute("INSERT INTO player_stats(user_id,gold,gems,updated_at) VALUES ('$user',$gold,$gems,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO UPDATE SET gold=$gold,gems=$gems") }
class ClanRaceScoringPostgresTest {
    @Test fun `real split donations debit exactly once with independent source ledger and positive fractional points`()=runTest {
        raceDatabase {
            val f=this;publishRace()
            val one=player("One").userId;val split=player("Split").userId
            val a=clan(one);val b=clan(split);fund(one);fund(split)
            advance("2026-10-26T06:00:00Z")
            val repo=PostgresClanRepository(source,{now})
            val request=UUID.randomUUID().toString()
            assertEquals(ClanDonationStatus.APPLIED,repo.donateToClan(one,a,request,ClanDonationCurrency.GOLD,10000).status)
            assertEquals(ClanDonationStatus.DUPLICATE,repo.donateToClan(one,a,request,ClanDonationCurrency.GOLD,10000).status)
            repeat(100) { assertEquals(ClanDonationStatus.APPLIED,repo.donateToClan(split,b,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100).status) }
            assertEquals("101",scalar("SELECT count(*) FROM clan_race_events"))
            assertEquals("1000000",scalar("SELECT sum(score_units) FROM clan_race_events WHERE clan_id='$a'"))
            assertEquals("1000000",scalar("SELECT sum(score_units) FROM clan_race_events WHERE clan_id='$b'"))
            assertEquals("90000",scalar("SELECT gold FROM player_stats WHERE user_id='$one'"));assertEquals("90000",scalar("SELECT gold FROM player_stats WHERE user_id='$split'"))
            assertEquals("101",scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='CLAN_DONATION'"))
        }
    }
    @Test fun `mission first claim uses claim week requires actual progress in current tenure but preserves shared assets`()=runTest {
        raceDatabase {
            publishRace();publishRace("2026-11-02")
            val contributor=player("Contributor").userId;val passive=player("Passive").userId
            val clan=clan(contributor);execute("INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES ('$clan','$passive','MEMBER',CURRENT_TIMESTAMP)")
            val repo=PostgresMissionRepository(source);val service=MissionService(repo,admins,{now})
            val ctx=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val draft=service.create(ctx,MissionDraftRequest(MissionConfig("RACE_DONATE",MissionScope.CLAN,MissionAction.DONATE_GOLD,MissionCadence.WEEKLY,MissionDifficulty.NORMAL,100,MissionReward(10,0,1,7)),reason="Fixture",requestId="mission"))
            service.publish(ctx,draft.id,MissionMutationRequest(draft.version,"Publish","mission-publish"))
            advance("2026-10-26T06:00:00Z");fund(contributor)
            assertEquals(ClanDonationStatus.APPLIED,PostgresClanRepository(source,{now}).donateToClan(contributor,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100).status)
            val mission=service.clan(contributor).items.single { it.code=="RACE_DONATE" }
            assertEquals(100,mission.progress)
            service.claim(passive,mission.instanceId,MissionClaimRequest("passive"))
            assertEquals("1",scalar("SELECT gems FROM player_stats WHERE user_id='$passive'"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_events WHERE user_id='$passive'"))
            advance("2026-11-02T06:00:00Z")
            val f=this
            val email=scalar("SELECT email_normalized FROM users WHERE id='$contributor'")
            val session=assertIs<AuthResult.Success>(auth.login(email,"GoodPassword123!","android")).session
            testApplication {
                application { gameModule(authService=f.auth,missionService=service,giftDeliveryEnabled=false) }
                val responses=coroutineScope { (1..2).map { async { client.post("/api/missions/${mission.instanceId}/claim") {
                    bearerAuth(session.accessToken);contentType(ContentType.Application.Json);setBody("""{"requestId":"contributor"}""")
                }.status } }.awaitAll() }
                assertEquals(listOf(HttpStatusCode.OK,HttpStatusCode.OK),responses)
            }
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_events WHERE source_kind='MISSION'"))
            assertEquals("7000000",scalar("SELECT score_units FROM clan_race_events WHERE source_kind='MISSION'"))
            assertEquals("2026-11-02",scalar("SELECT week_start FROM clan_race_events WHERE source_kind='MISSION'"))
            assertEquals("1",scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='MISSION' AND user_id='$contributor'"))
        }
    }
    @Test fun `inactive first claim and rejoined old contributor never backfill while normal rewards remain`()=runTest {
        raceDatabase {
            publishRace("2026-11-02")
            val owner=player("Owner").userId;val member=player("Member").userId;val clan=clan(owner)
            execute("INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES ('$clan','$member','MEMBER',CURRENT_TIMESTAMP)")
            val missions=MissionService(PostgresMissionRepository(source),admins,{now})
            val ctx=MissionAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val draft=missions.create(ctx,MissionDraftRequest(MissionConfig("NO_BACKFILL",MissionScope.CLAN,MissionAction.DONATE_GOLD,MissionCadence.WEEKLY,MissionDifficulty.NORMAL,200,MissionReward(10,0,1,7)),reason="Fixture",requestId="no-backfill"))
            missions.publish(ctx,draft.id,MissionMutationRequest(draft.version,"Publish","no-backfill-publish"))
            advance("2026-10-26T06:00:00Z");fund(owner);fund(member)
            val clans=PostgresClanRepository(source,{now})
            for(user in listOf(owner,member)) assertEquals(ClanDonationStatus.APPLIED,clans.donateToClan(user,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100).status)
            val instance=missions.clan(owner).items.single { it.code=="NO_BACKFILL" }.instanceId
            missions.claim(owner,instance,MissionClaimRequest("inactive-claim"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_events"))
            advance("2026-10-27T06:00:00Z");assertTrue(clans.leaveClan(member));clans.requestJoinClan(member,clan);assertEquals(ClanJoinResponseResult.APPROVED,clans.respondJoinRequest(clan,owner,member,true))
            advance("2026-11-02T06:00:00Z")
            missions.claim(owner,instance,MissionClaimRequest("inactive-retry"))
            missions.claim(member,instance,MissionClaimRequest("new-tenure-claim"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_events"))
            assertEquals("101",scalar("SELECT gems FROM player_stats WHERE user_id='$owner'"));assertEquals("101",scalar("SELECT gems FROM player_stats WHERE user_id='$member'"))
            assertEquals("2",scalar("SELECT count(*) FROM mission_claims WHERE instance_id='$instance'"))
        }
    }
    @Test fun `score overflow rolls back donation source debit mission progress and clan counters`()=runTest {
        raceDatabase {
            publishRace();val player=player().userId;val clan=clan(player);fund(player)
            advance("2026-10-26T06:00:00Z")
            execute("""INSERT INTO clan_race_events(week_start,source_kind,source_id,user_id,clan_id,tenure_id,occurred_at,score_units)
             SELECT '2026-10-26','DONATION','capacity-fixture',user_id,clan_id,id,'2026-10-26T00:00:00Z',99999999999999999999999999999999995000 FROM clan_race_tenures WHERE user_id='$player'""")
            val request=UUID.randomUUID().toString()
            assertEquals(ClanDonationStatus.FAILED,PostgresClanRepository(source,{now}).donateToClan(player,clan,request,ClanDonationCurrency.GOLD,100).status)
            assertEquals("100000",scalar("SELECT gold FROM player_stats WHERE user_id='$player'"))
            assertEquals("0",scalar("SELECT donated_gold FROM clans WHERE id='$clan'"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_donations WHERE request_id='$request'"))
            assertEquals("0",scalar("SELECT count(*) FROM wallet_transactions WHERE source_id='$request'"))
            assertEquals("0",scalar("SELECT count(*) FROM mission_progress_events WHERE source_id='donation:$request'"))
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_events"))
        }
    }
    @Test fun `time sampled after shared gate waits assigns donation to new week`()=runTest {
        raceDatabase {
            publishRace();publishRace("2026-11-02");val player=player().userId;val clan=clan(player);fund(player)
            advance("2026-11-01T16:59:59.999Z")
            source.connection.use { blocker ->
                blocker.autoCommit=false;JdbcClanRaceStore(blocker).lock()
                coroutineScope {
                    val donation=async(Dispatchers.IO) { PostgresClanRepository(source,{now}).donateToClan(player,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100) }
                    delay(100);advance("2026-11-01T17:00:00Z");blocker.commit()
                    assertEquals(ClanDonationStatus.APPLIED,donation.await().status)
                }
            }
            assertEquals("2026-11-02",scalar("SELECT week_start FROM clan_race_events"))
        }
    }
}
