package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class ClanRaceTenurePostgresTest {
    @Test fun `cutoff history survives kick rejoin clan dissolution and account deletion after cutoff`()=runTest {
        raceDatabase {
            publishRace(rate="0.000001")
            val owner=player("Owner").userId;val member=player("Member").userId
            val clan=clan(owner)
            val repo=PostgresClanRepository(source,{now});val races=PostgresClanRaceRepository(source)
            advance("2026-10-26T06:00:00Z")
            assertEquals(ClanJoinRequestResult.REQUESTED,repo.requestJoinClan(member,clan))
            assertEquals(ClanJoinResponseResult.APPROVED,repo.respondJoinRequest(clan,owner,member,true))
            fund(member);fund(owner)
            assertEquals(ClanDonationStatus.APPLIED,repo.donateToClan(member,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100).status)
            assertEquals(ClanDonationStatus.APPLIED,repo.donateToClan(owner,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100).status)
            val week=LocalDate.parse("2026-10-26");val cutoff=raceWeekMillis(week.plusWeeks(1))
            advance("2026-11-01T16:59:59.999Z")
            assertEquals(setOf(owner,member),races.transaction { eligibleAt(week,clan,cutoff).map { it.userId }.toSet() })
            advance("2026-11-01T17:00:00Z")
            assertTrue(repo.kickMember(clan,owner,member))
            assertEquals(setOf(owner,member),races.transaction { eligibleAt(week,clan,cutoff).map { it.userId }.toSet() })
            advance("2026-11-02T06:00:00Z")
            assertTrue(repo.leaveClan(owner))
            assertEquals(setOf(owner,member),races.transaction { eligibleAt(week,clan,cutoff).map { it.userId }.toSet() })
            assertTrue(PostgresAuthRepository(source).deleteAccount(UUID.fromString(member)))
            assertEquals("2",scalar("SELECT count(*) FROM clan_race_events"))
            assertEquals(setOf(owner,member),races.transaction { eligibleAt(week,clan,cutoff).map { it.userId }.toSet() })
            assertEquals("0",scalar("SELECT count(*) FROM clans WHERE id='$clan'"))
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_clans WHERE clan_id='$clan' AND deleted_at IS NOT NULL"))
        }
    }
    @Test fun `rejoin has new tenure and cannot inherit old race eligibility or mission contribution proof`()=runTest {
        raceDatabase {
            publishRace(rate="0.000001")
            val owner=player("Owner").userId;val member=player("Member").userId;val clan=clan(owner)
            val repo=PostgresClanRepository(source,{now});val races=PostgresClanRaceRepository(source)
            advance("2026-10-26T06:00:00Z")
            suspend fun join() { repo.requestJoinClan(member,clan);assertEquals(ClanJoinResponseResult.APPROVED,repo.respondJoinRequest(clan,owner,member,true)) }
            join();fund(member)
            repo.donateToClan(member,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100)
            val week=LocalDate.parse("2026-10-26");val cutoff=raceWeekMillis(week.plusWeeks(1))
            assertEquals(listOf(member),races.transaction { eligibleAt(week,clan,cutoff).map { it.userId } })
            advance("2026-10-27T06:00:00Z");assertTrue(repo.leaveClan(member));join()
            assertEquals(emptyList(),races.transaction { eligibleAt(week,clan,cutoff) })
            repo.donateToClan(member,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100)
            assertEquals(listOf(member),races.transaction { eligibleAt(week,clan,cutoff).map { it.userId } })
            assertEquals("2",scalar("SELECT count(DISTINCT tenure_id) FROM clan_race_events WHERE user_id='$member'"))
            advance("2026-11-01T16:59:59.999Z");assertTrue(repo.kickMember(clan,owner,member))
            assertEquals(emptyList(),races.transaction { eligibleAt(week,clan,cutoff) })
        }
    }
    @Test fun `concurrent accept donation kick and last member dissolve retain only correct histories`()=runTest {
        raceDatabase {
            publishRace();val one=player("One").userId;val two=player("Two").userId;val candidate=player("Candidate").userId
            val a=clan(one);val b=clan(two);advance("2026-10-26T06:00:00Z")
            val repo=PostgresClanRepository(source,{now});repo.requestJoinClan(candidate,a);repo.requestJoinClan(candidate,b)
            val accepted=coroutineScope { listOf(async { repo.respondJoinRequest(a,one,candidate,true) },async { repo.respondJoinRequest(b,two,candidate,true) }).awaitAll() }
            assertEquals(1,accepted.count { it==ClanJoinResponseResult.APPROVED })
            val clan=scalar("SELECT clan_id FROM clan_members WHERE user_id='$candidate'");fund(candidate)
            coroutineScope { listOf(async { repo.donateToClan(candidate,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100) },async { repo.leaveClan(candidate) }).awaitAll() }
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_tenures WHERE user_id='$candidate' AND ended_at IS NULL"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_events e JOIN clan_race_tenures t ON t.id=e.tenure_id WHERE e.user_id='$candidate' AND (e.clan_id<>t.clan_id OR e.occurred_at<t.joined_at OR e.occurred_at>t.ended_at)"))
            assertTrue(repo.leaveClan(one));assertTrue(repo.leaveClan(two))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_tenures WHERE ended_at IS NULL"))
        }
    }
}
