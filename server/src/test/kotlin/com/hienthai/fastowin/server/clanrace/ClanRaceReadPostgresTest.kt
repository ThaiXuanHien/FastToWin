package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.*

class ClanRaceReadPostgresTest {
    @Test fun `HTTP ranks all clans paginates and returns own outside page while contribution details stay private`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val f=this;val people=(1..5).map { player("Player $it") }
            val clans=people.take(4).map { clan(it.userId) }
            for(index in 0..3) {
                fund(people[index].userId)
                assertEquals(ClanDonationStatus.APPLIED,PostgresClanRepository(source,{now}).donateToClan(people[index].userId,clans[index],UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,(4-index)*100).status)
                now+=1000
            }
            testApplication {
                application { gameModule(authService=f.auth,adminService=f.admins,clanRaceService=ClanRaceService(PostgresClanRaceRepository(f.source),f.admins,{f.now}),giftDeliveryEnabled=false) }
                val response=client.get("/api/clan-race/standings?pageSize=2") { bearerAuth(people[3].accessToken) }
                assertEquals(HttpStatusCode.OK,response.status,response.bodyAsText())
                val result=Json.decodeFromString<ClanRaceStandings>(response.bodyAsText())
                assertEquals(4,result.total);assertEquals(listOf(1,2),result.items.map { it.rank });assertEquals(4,result.ownClan?.rank)
                assertEquals("10000",result.ownClan?.scoreUnits);assertEquals("0.01",result.ownClan?.points)
                val detail=client.get("/api/clan-race/contributions?clanId=${clans[0]}") { bearerAuth(people[3].accessToken) }
                assertEquals(HttpStatusCode.OK,detail.status)
                val own=Json.decodeFromString<ClanRaceContributions>(detail.bodyAsText())
                assertEquals(clans[3],own.clanId);assertEquals(listOf(people[3].userId),own.items.map { it.userId });assertTrue(own.items.single().eligible)
                val noClan=client.get("/api/clan-race/contributions") { bearerAuth(people[4].accessToken) }
                assertNull(Json.decodeFromString<ClanRaceContributions>(noClan.bodyAsText()).clanId)
                assertEquals(HttpStatusCode.BadRequest,client.get("/api/clan-race/standings?page=-1") { bearerAuth(people[3].accessToken) }.status)
                assertEquals(HttpStatusCode.OK,client.get("/api/clan-race/history") { bearerAuth(people[3].accessToken) }.status)
                assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
            }
        }
    }
    @Test fun `tie order uses final score reach time then stable id and preserves identity after deletion`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val users=(1..4).map { player("Tie $it").userId };val clans=users.map { clan(it) }
            val repo=PostgresClanRepository(source,{now});val races=PostgresClanRaceRepository(source)
            for(index in 0..2) {
                fund(users[index]);repo.donateToClan(users[index],clans[index],UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100)
                if(index==0) now+=1000
            }
            val cutoff=raceWeekMillis(java.time.LocalDate.parse("2026-11-02"));val week=java.time.LocalDate.parse("2026-10-26")
            val expected=listOf(clans[0])+clans.subList(1,3).sorted()
            val before=races.transaction { standingsAt(week,cutoff) }
            assertEquals(expected,before.map { it.clanId });assertEquals(listOf(1,2,3),before.map { it.rank })
            val old=before.first().name
            advance("2026-11-02T06:00:00Z")
            races.transaction { setTime(now);update("UPDATE clans SET name='Changed',logo_id='new-logo',level=4 WHERE id=?",uuid(clans[0])) }
            assertTrue(repo.leaveClan(users[0]))
            assertEquals(old,races.transaction { standingsAt(week,cutoff) }.first().name)
            assertEquals(1,races.transaction { standingsAt(week,cutoff) }.first().level)
            assertEquals(expected,races.transaction { standingsAt(week,cutoff) }.map { it.clanId })
            val history=ClanRaceService(races,admins,{now}).history(users[1])
            assertEquals("CLOSED",history.items.single().state.name)
        }
    }
}
