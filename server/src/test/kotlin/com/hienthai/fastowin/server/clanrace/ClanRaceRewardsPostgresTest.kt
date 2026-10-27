package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.gifts.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

class ClanRaceRewardsPostgresTest {
    @Test fun `guest contribution keeps pending entitlement until existing same identity upgrade and then gets seven full days`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val guest=PostgresGuestIdentityRepository(source).resolveGuest("Guest racer",null,now)
            val clan=clan(guest.playerId);donate(guest.playerId,clan)
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_events WHERE user_id='${guest.playerId}'"))
            advance("2026-11-02T06:00:00Z")
            val repo=PostgresClanRaceRepository(source);ClanRaceSettlement(repo,{now}).settleDueWeeks()
            assertEquals(0,ClanRaceDelivery(repo,{now}).deliverDueBatch())
            assertEquals("PENDING:REGISTRATION_REQUIRED:1",scalar("SELECT state||':'||last_error_code||':'||attempts FROM clan_race_outbox"))
            val waiting=ClanRaceService(repo,admins,{now}).history(guest.playerId).items.single().settlement!!.delivery
            assertEquals(1,waiting.pending);assertEquals(1,waiting.waitingRegistration);assertEquals(0,waiting.failed)
            assertEquals("0",scalar("SELECT count(*) FROM admin_gift_campaigns"))
            val gifts=GiftService(PostgresGiftRepository(source),admins,{now})
            assertEquals(401,assertFailsWith<com.hienthai.fastowin.server.admin.AdminException> {
                gifts.mailbox(guest.playerId,com.hienthai.fastowin.server.admin.PageRequest())
            }.status)
            assertEquals(0,ClanRaceDelivery(repo,{now}).deliverDueBatch())
            assertEquals("1",scalar("SELECT attempts FROM clan_race_outbox"))
            advance("2026-11-16T06:00:00Z")
            val upgraded=assertIs<AuthResult.Success>(auth.upgradeGuest(guest.resumeToken,"${UUID.randomUUID()}@example.com","GoodPassword123!","android")).session
            assertEquals(guest.playerId,upgraded.userId)
            assertEquals(1,ClanRaceDelivery(repo,{now}).deliverDueBatch())
            val gift=gifts.mailbox(upgraded.userId,com.hienthai.fastowin.server.admin.PageRequest()).items.single()
            assertEquals(now,gift.availableAtEpochMillis);assertEquals(now+604800000L,gift.expiresAtEpochMillis)
            assertEquals(GiftState.CLAIMED,gifts.claim(upgraded.userId,gift.campaignId).gift.state)
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_entitlements"))
            assertEquals("1",scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='CLAN_WEEKLY_RACE'"))
        }
    }

    @Test fun `delivery availability clock is sampled after recipient row lock wait`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val user=player().userId;val clan=clan(user);donate(user,clan)
            advance("2026-11-02T06:00:00Z")
            val repo=PostgresClanRaceRepository(source);ClanRaceSettlement(repo,{now}).settleDueWeeks()
            source.connection.use { blocker ->
                blocker.autoCommit=false
                blocker.createStatement().use { it.execute("SELECT id FROM users WHERE id='$user' FOR UPDATE") }
                val blockerPid=blocker.createStatement().use { it.executeQuery("SELECT pg_backend_pid()").use { r -> r.next();r.getInt(1) } }
                coroutineScope {
                    val delivery=async(Dispatchers.IO) { ClanRaceDelivery(repo,{now}).deliverDueBatch() }
                    try {
                        withContext(Dispatchers.IO) { withTimeout(5000) {
                            while(scalar("SELECT count(*) FROM pg_stat_activity a WHERE a.application_name=current_setting('application_name') AND a.wait_event_type='Lock' AND $blockerPid=ANY(pg_blocking_pids(a.pid))")!="1") delay(10)
                        } }
                        advance("2026-11-05T06:00:00Z");blocker.commit()
                        assertEquals(1,delivery.await())
                    } finally { blocker.rollback() }
                }
            }
            val gift=GiftService(PostgresGiftRepository(source),admins,{now}).mailbox(user,com.hienthai.fastowin.server.admin.PageRequest()).items.single()
            assertEquals(now,gift.availableAtEpochMillis);assertEquals(now+604800000L,gift.expiresAtEpochMillis)
        }
    }

    @Test fun `concurrent HTTP claim all grants multiple weeks exactly once and reports failed foreign items`()=runTest {
        raceDatabase {
            publishRace();publishRace("2026-11-02")
            advance("2026-10-26T06:00:00Z")
            val player=player();val clan=clan(player.userId);donate(player.userId,clan)
            advance("2026-11-02T06:00:00Z");donate(player.userId,clan)
            advance("2026-11-09T06:00:00Z")
            val repo=PostgresClanRaceRepository(source)
            assertEquals(2,ClanRaceSettlement(repo,{now}).settleDueWeeks())
            assertEquals(2,ClanRaceDelivery(repo,{now}).deliverDueBatch())
            val gifts=GiftService(PostgresGiftRepository(source),admins,{now});val f=this
            val session=assertIs<AuthResult.Success>(auth.refresh(player.refreshToken)).session
            val ids=gifts.mailbox(player.userId,com.hienthai.fastowin.server.admin.PageRequest()).items.map { it.campaignId }
            val beforeXp=scalar("SELECT experience_points FROM player_stats WHERE user_id='${player.userId}'").toInt()
            testApplication {
                application { gameModule(authService=f.auth,giftService=gifts,giftDeliveryEnabled=false) }
                val claims=coroutineScope { (1..3).map { async {
                    client.post("/api/gifts/mailbox/claim-all") {
                        bearerAuth(session.accessToken);contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(GiftClaimAllRequest(ids+ids.first()+UUID.randomUUID().toString())))
                    }
                } }.awaitAll() }
                claims.forEach { response ->
                    assertEquals(HttpStatusCode.OK,response.status,response.bodyAsText())
                    val items=Json.decodeFromString<GiftClaimAllResult>(response.bodyAsText()).items
                    assertEquals(3,items.count { it.result?.gift?.state==GiftState.CLAIMED })
                    assertEquals("NOT_FOUND",items.last().error?.code)
                }
            }
            assertEquals("100500",scalar("SELECT gold FROM player_stats WHERE user_id='${player.userId}'"))
            assertEquals((beforeXp+60).toString(),scalar("SELECT experience_points FROM player_stats WHERE user_id='${player.userId}'"))
            assertEquals("106",scalar("SELECT gems FROM player_stats WHERE user_id='${player.userId}'"))
            assertEquals("2",scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='CLAN_WEEKLY_RACE'"))
            assertEquals("2",scalar("SELECT count(DISTINCT source_id) FROM wallet_transactions WHERE source_type='CLAN_WEEKLY_RACE'"))
            assertEquals(2,ClanRaceService(repo,admins,{now}).history(player.userId).items.sumOf { it.settlement!!.delivery.claimed })
        }
    }

    @Test fun `HTTP mailbox claims frozen race gift once with typed context source and seven days from actual availability`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val recipient=player();val stranger=player();val clan=clan(recipient.userId,"Winner");donate(recipient.userId,clan)
            advance("2026-11-02T06:00:00Z")
            val repository=PostgresClanRaceRepository(source)
            ClanRaceSettlement(repository,{now}).settleDueWeeks()
            execute("DELETE FROM clans WHERE id='$clan'")
            advance("2026-11-12T06:00:00Z")
            val f=this
            val gifts=GiftService(PostgresGiftRepository(source),admins,{now})
            assertEquals(0,gifts.mailbox(recipient.userId,com.hienthai.fastowin.server.admin.PageRequest()).total)
            assertEquals(1,ClanRaceDelivery(repository,{now}).deliverDueBatch())
            assertEquals(0,ClanRaceDelivery(repository,{now}).deliverDueBatch())
            val fresh=assertIs<AuthResult.Success>(auth.refresh(recipient.refreshToken)).session
            val foreign=assertIs<AuthResult.Success>(auth.refresh(stranger.refreshToken)).session
            login=admins.login(actor.email,"GoodPassword123!","https://admin.example","127.0.0.1")
            testApplication {
                application { gameModule(authService=f.auth,adminService=f.admins,giftService=gifts,giftDeliveryEnabled=false) }
                val response=client.get("/api/gifts/mailbox") { bearerAuth(fresh.accessToken) }
                assertEquals(HttpStatusCode.OK,response.status,response.bodyAsText())
                val page=Json.decodeFromString<GiftMailboxPage>(response.bodyAsText());val gift=page.items.single()
                assertEquals(1,page.availableCount);assertEquals(f.now,gift.availableAtEpochMillis)
                assertEquals(604800000L,gift.expiresAtEpochMillis-gift.availableAtEpochMillis)
                assertEquals(300,gift.gold);assertEquals(30,gift.xp);assertEquals(3,gift.gems);assertNull(gift.compensation)
                val race=Json.parseToJsonElement(response.bodyAsText()).jsonObject["items"]!!.jsonArray.single().jsonObject["clanRace"]!!.jsonObject
                assertEquals("2026-10-26",race["weekStart"]!!.jsonPrimitive.content)
                assertEquals("Winner",race["clanName"]!!.jsonPrimitive.content)
                assertEquals("1",race["finalRank"]!!.jsonPrimitive.content)
                assertEquals("CLAN_WEEKLY_RACE",race["sourceType"]!!.jsonPrimitive.content)
                assertEquals(gift.campaignId,race["entitlementId"]!!.jsonPrimitive.content)
                assertEquals(HttpStatusCode.NotFound,client.post("/api/gifts/mailbox/${gift.campaignId}/claim") { bearerAuth(foreign.accessToken) }.status)
                val beforeXp=f.scalar("SELECT experience_points FROM player_stats WHERE user_id='${recipient.userId}'").toInt()
                val claims=coroutineScope { (1..4).map { async { client.post("/api/gifts/mailbox/${gift.campaignId}/claim") { bearerAuth(fresh.accessToken) } } }.awaitAll() }
                claims.forEach { assertEquals(HttpStatusCode.OK,it.status,it.bodyAsText()) }
                assertEquals("100200",f.scalar("SELECT gold FROM player_stats WHERE user_id='${recipient.userId}'"))
                assertEquals((beforeXp+30).toString(),f.scalar("SELECT experience_points FROM player_stats WHERE user_id='${recipient.userId}'"))
                assertEquals("103",f.scalar("SELECT gems FROM player_stats WHERE user_id='${recipient.userId}'"))
                assertEquals("1",f.scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='CLAN_WEEKLY_RACE' AND source_id='${gift.campaignId}'"))
                assertEquals("0",f.scalar("SELECT count(*) FROM wallet_transactions WHERE source_type IN ('ADMIN_GIFT','COMPENSATION')"))
                val all=client.post("/api/gifts/mailbox/claim-all") { bearerAuth(fresh.accessToken);contentType(ContentType.Application.Json);setBody(Json.encodeToString(GiftClaimAllRequest(listOf(gift.campaignId,gift.campaignId,UUID.randomUUID().toString())))) }
                val results=Json.decodeFromString<GiftClaimAllResult>(all.bodyAsText()).items
                assertEquals(2,results.count { it.result?.gift?.state==GiftState.CLAIMED });assertEquals("NOT_FOUND",results.last().error?.code)
                val cancel=client.post("/admin/api/gift-campaigns/${gift.campaignId}/cancel") {
                    header(HttpHeaders.Cookie,"fasttowin_admin=${f.login.token}");header(HttpHeaders.Origin,"https://admin.example");header("X-Admin-CSRF",f.login.response.csrfToken)
                    contentType(ContentType.Application.Json);setBody(Json.encodeToString(GiftCancelRequest(1,"cancel-race","Not an ordinary gift")))
                }
                assertEquals(HttpStatusCode.NotFound,cancel.status,cancel.bodyAsText())
            }
        }
    }

    @Test fun `partial delivery restart deleted account and failed recipient retain durable outcomes`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val users=(1..3).map { player().userId };val clan=clan(users[0]);users.drop(1).forEach { joinMember(clan,it) };users.forEach { donate(it,clan) }
            advance("2026-11-02T06:00:00Z")
            val repo=PostgresClanRaceRepository(source);ClanRaceSettlement(repo,{now}).settleDueWeeks()
            val deleted=scalar("SELECT user_id FROM clan_race_entitlements ORDER BY id LIMIT 1")
            assertTrue(PostgresAuthRepository(source).deleteAccount(UUID.fromString(deleted)))
            assertEquals(1,ClanRaceDelivery(repo,{now}).deliverDueBatch(1))
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_outbox WHERE state='ACCOUNT_UNAVAILABLE'"))
            execute("""CREATE FUNCTION reject_race_gift() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''injected delivery failure''; END'; CREATE TRIGGER reject_race_gift BEFORE INSERT ON admin_gift_campaigns FOR EACH ROW EXECUTE FUNCTION reject_race_gift()""")
            assertEquals(0,ClanRaceDelivery(repo,{now}).deliverDueBatch())
            assertEquals("2",scalar("SELECT count(*) FROM clan_race_outbox WHERE state='PENDING' AND attempts=1 AND last_error_code='DELIVERY_FAILED'"))
            assertEquals("0",scalar("SELECT count(*) FROM admin_gift_campaigns"))
            execute("DROP TRIGGER reject_race_gift ON admin_gift_campaigns; DROP FUNCTION reject_race_gift()")
            advance("2026-11-13T06:00:00Z")
            assertEquals(2,coroutineScope { (1..3).map { async { ClanRaceDelivery(repo,{now}).deliverDueBatch() } }.awaitAll() }.sum())
            assertEquals(0,ClanRaceDelivery(repo,{now}).deliverDueBatch())
            assertEquals("2",scalar("SELECT count(*) FROM admin_gift_campaigns WHERE source_type='CLAN_WEEKLY_RACE' AND expires_at-available_at=interval '7 days'"))
            assertEquals("0",scalar("SELECT count(*) FROM users WHERE id='$deleted'"))
            assertEquals("3",scalar("SELECT count(*) FROM clan_race_entitlements"))
        }
    }

    @Test fun `HTTP overflow rolls back all assets and claim marker allowing retry while expiry is enforced`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val player=player();val clan=clan(player.userId);donate(player.userId,clan)
            val expiring=player();joinMember(clan,expiring.userId);donate(expiring.userId,clan)
            advance("2026-11-02T06:00:00Z")
            val repo=PostgresClanRaceRepository(source);ClanRaceSettlement(repo,{now}).settleDueWeeks();ClanRaceDelivery(repo,{now}).deliverDueBatch()
            val id=scalar("SELECT id FROM clan_race_entitlements WHERE user_id='${player.userId}'")
            val expiringId=scalar("SELECT id FROM clan_race_entitlements WHERE user_id='${expiring.userId}'")
            val f=this;val gifts=GiftService(PostgresGiftRepository(source),admins,{now})
            val session=assertIs<AuthResult.Success>(auth.refresh(player.refreshToken)).session
            execute("UPDATE player_stats SET gold=2147483647,experience_points=12,gems=14 WHERE user_id='${player.userId}'")
            testApplication {
                application { gameModule(authService=f.auth,giftService=gifts,giftDeliveryEnabled=false) }
                suspend fun claim()=client.post("/api/gifts/mailbox/$id/claim") { bearerAuth(session.accessToken) }
                assertEquals(HttpStatusCode.Conflict,claim().status)
                assertEquals("12:14",f.scalar("SELECT experience_points||':'||gems FROM player_stats WHERE user_id='${player.userId}'"))
                assertEquals("0",f.scalar("SELECT count(*) FROM admin_gift_recipients WHERE claimed_at IS NOT NULL"))
                assertEquals("0",f.scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='CLAN_WEEKLY_RACE'"))
                f.execute("UPDATE player_stats SET gold=0 WHERE user_id='${player.userId}'")
                val retry=claim();assertEquals(HttpStatusCode.OK,retry.status,retry.bodyAsText())
                val result=Json.decodeFromString<GiftClaimResult>(retry.bodyAsText())
                assertEquals(300,result.gold);assertEquals(42,result.xp);assertEquals(17,result.gems)
                val available=f.now
                f.now=available+604800000L
                val renewed=assertIs<AuthResult.Success>(f.auth.refresh(session.refreshToken)).session
                val expiredSession=assertIs<AuthResult.Success>(f.auth.refresh(expiring.refreshToken)).session
                val expired=client.post("/api/gifts/mailbox/$expiringId/claim") { bearerAuth(expiredSession.accessToken) }
                assertEquals(HttpStatusCode.Conflict,expired.status)
                val replay=client.post("/api/gifts/mailbox/$id/claim") { bearerAuth(renewed.accessToken) }
                assertEquals(GiftState.CLAIMED,Json.decodeFromString<GiftClaimResult>(replay.bodyAsText()).gift.state)
                val mailbox=client.get("/api/gifts/mailbox") { bearerAuth(expiredSession.accessToken) }
                assertEquals(GiftState.EXPIRED,Json.decodeFromString<GiftMailboxPage>(mailbox.bodyAsText()).items.single().state)
                assertEquals("1",f.scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='CLAN_WEEKLY_RACE'"))
            }
        }
    }

    @Test fun `application worker settles and delivers on startup with injected clock and history HTTP exposes frozen result`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val player=player();val clan=clan(player.userId);donate(player.userId,clan)
            advance("2026-11-02T06:00:00Z")
            val repo=PostgresClanRaceRepository(source);val f=this
            val session=assertIs<AuthResult.Success>(auth.refresh(player.refreshToken)).session
            testApplication {
                application { gameModule(authService=f.auth,adminService=f.admins,
                    giftService=GiftService(PostgresGiftRepository(f.source),f.admins,{f.now}),giftDeliveryEnabled=true,
                    clanRaceService=ClanRaceService(repo,f.admins,{f.now}),
                    clanRaceSettlement=ClanRaceSettlement(repo,{f.now}),clanRaceDelivery=ClanRaceDelivery(repo,{f.now})) }
                startApplication()
                withContext(Dispatchers.IO) { withTimeout(10000) {
                    while(f.scalar("SELECT count(*) FROM clan_race_outbox WHERE state='DELIVERED'")!="1") delay(10)
                } }
                val history=client.get("/api/clan-race/history") { bearerAuth(session.accessToken) }
                assertEquals(HttpStatusCode.OK,history.status,history.bodyAsText())
                val item=Json.decodeFromString<ClanRaceHistory>(history.bodyAsText()).items.single()
                assertEquals(ClanRaceState.SETTLED,item.state);assertEquals(clan,item.settlement?.result?.winners?.single()?.standing?.clanId)
                assertEquals(1,item.settlement?.delivery?.delivered)
                assertEquals(1,Json.decodeFromString<GiftMailboxPage>(client.get("/api/gifts/mailbox") { bearerAuth(session.accessToken) }.bodyAsText()).availableCount)
            }
        }
    }
}
