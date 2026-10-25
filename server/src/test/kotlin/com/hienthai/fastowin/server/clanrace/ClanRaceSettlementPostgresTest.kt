package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class ClanRaceSettlementPostgresTest {
    @Test fun `cutoff freezes only current contributing tenures including rejoin new points and switch clan`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val owner=player().userId;val otherOwner=player().userId;val first=clan(owner);val second=clan(otherOwner)
            val people=(1..5).map { player().userId };people.forEach { joinMember(first,it);donate(it,first) }
            advance("2026-10-27T06:00:00Z")
            people.take(2).forEach { execute("DELETE FROM clan_members WHERE user_id='$it'");joinMember(first,it) }
            donate(people[1],first)
            execute("DELETE FROM clan_members WHERE user_id='${people[2]}'");joinMember(second,people[2]);donate(people[2],second)
            advance("2026-11-01T16:59:59.999Z")
            execute("DELETE FROM clan_members WHERE user_id='${people[3]}'")
            val repo=PostgresClanRaceRepository(source)
            assertEquals(0,ClanRaceSettlement(repo,{now}).settleDueWeeks())
            advance("2026-11-01T17:00:00Z")
            execute("DELETE FROM clan_members WHERE user_id='${people[4]}'")
            advance("2026-11-12T06:00:00Z")
            execute("DELETE FROM clans WHERE id='$first'")
            assertEquals(1,ClanRaceSettlement(repo,{now}).settleDueWeeks())
            assertEquals(setOf(people[1],people[2],people[4]),scalar("SELECT string_agg(user_id::text,',') FROM clan_race_entitlements").split(',').toSet())
            assertEquals(second,scalar("SELECT clan_id FROM clan_race_entitlements WHERE user_id='${people[2]}'"))
            assertEquals("2",scalar("SELECT count(*) FROM clan_race_entitlements WHERE clan_id='$first'"))
            assertEquals("60000",scalar("SELECT payload::jsonb->'standings'->0->>'scoreUnits' FROM clan_race_settlements"))
        }
    }

    @Test fun `failed settlement rolls back result recipients and outbox then retries with an auditable checkpoint`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val owner=player().userId;val clan=clan(owner);donate(owner,clan)
            advance("2026-11-02T06:00:00Z")
            execute("""CREATE FUNCTION reject_entitlement() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''injected settlement failure''; END'; CREATE TRIGGER reject_entitlement BEFORE INSERT ON clan_race_entitlements FOR EACH ROW EXECUTE FUNCTION reject_entitlement()""")
            val repo=PostgresClanRaceRepository(source)
            assertEquals(0,ClanRaceSettlement(repo,{now}).settleDueWeeks())
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_settlements"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_entitlements"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_outbox"))
            assertEquals("FAILED:1:SETTLEMENT_FAILED",scalar("SELECT state||':'||attempts||':'||last_error_code FROM clan_race_settlement_work"))
            val pending=ClanRaceService(repo,admins,{now}).history(owner).items.single()
            assertEquals(ClanRaceState.CLOSED,pending.state);assertEquals(ClanRaceSettlementState.FAILED,pending.settlement?.state)
            execute("DROP TRIGGER reject_entitlement ON clan_race_entitlements; DROP FUNCTION reject_entitlement()")
            assertEquals(1,ClanRaceSettlement(repo,{now}).settleDueWeeks())
            assertEquals("SETTLED:2",scalar("SELECT state||':'||attempts FROM clan_race_settlement_work"))
            assertFailsWith<java.sql.SQLException> { execute("DELETE FROM clan_race_settlements") }
            assertFailsWith<java.sql.SQLException> { execute("DELETE FROM clan_race_entitlements") }
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_entitlements"))
        }
    }

    @Test fun `late settlement freezes cutoff recipients and survives restart and later dissolution`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val owner=player("Owner").userId;val a=player("Returns").userId;val b=player("Stays").userId
            val clan=clan(owner,"Frozen name");joinMember(clan,a);joinMember(clan,b)
            donate(a,clan);donate(b,clan)
            advance("2026-10-27T06:00:00Z")
            execute("DELETE FROM clan_members WHERE user_id='$a'");joinMember(clan,a)
            advance("2026-11-02T06:00:00Z")
            execute("DELETE FROM clan_members WHERE user_id='$b'")
            execute("UPDATE clans SET name='Renamed after cutoff' WHERE id='$clan'")
            val repository=PostgresClanRaceRepository(source)
            assertEquals(1,ClanRaceSettlement(repository,{now}).settleDueWeeks())
            assertEquals("SETTLED",ClanRaceService(repository,admins,{now}).history(owner).items.single().state.name)
            assertEquals(b,scalar("SELECT user_id FROM clan_race_entitlements"))
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_outbox WHERE state='PENDING'"))
            execute("DELETE FROM clans WHERE id='$clan'")
            assertEquals(0,ClanRaceSettlement(repository,{now}).settleDueWeeks())
            assertEquals("1",scalar("SELECT count(*) FROM clan_race_entitlements"))
            assertTrue(scalar("SELECT payload FROM clan_race_settlements").contains("Frozen name"))
            assertFalse(scalar("SELECT payload FROM clan_race_settlements").contains("Renamed after cutoff"))
        }
    }

    @Test fun `rank snapshot excludes zero and deleted before cutoff and keeps positive clan with no recipients`()=runTest {
        raceDatabase {
            publishRace();advance("2026-10-26T06:00:00Z")
            val users=(1..6).map { player().userId };val clans=users.map { clan(it) }
            for(i in 0..4) { donate(users[i],clans[i]);if(i==0) now+=1000 }
            execute("DELETE FROM clans WHERE id='${clans[4]}'")
            execute("DELETE FROM clan_members WHERE user_id='${users[0]}'")
            advance("2026-11-01T17:00:00Z")
            val repository=PostgresClanRaceRepository(source)
            assertEquals(1,ClanRaceSettlement(repository,{now}).settleDueWeeks())
            assertEquals("2",scalar("SELECT count(*) FROM clan_race_entitlements"))
            assertEquals("3",scalar("SELECT jsonb_array_length(payload::jsonb->'winners') FROM clan_race_settlements"))
            assertEquals(clans[0],scalar("SELECT payload::jsonb->'standings'->0->>'clanId' FROM clan_race_settlements"))
            assertEquals("4",scalar("SELECT jsonb_array_length(payload::jsonb->'standings') FROM clan_race_settlements"))
            val ordered=clans.subList(1,4).sorted()
            assertEquals(ordered.first(),scalar("SELECT payload::jsonb->'standings'->1->>'clanId' FROM clan_race_settlements"))
        }
    }

    @Test fun `all fifty contributors count and concurrent workers settle every configured elapsed week once`()=runTest {
        raceDatabase {
            publishRace();publishRace("2026-11-09");advance("2026-10-26T06:00:00Z")
            val people=(1..50).map { player().userId };val clan=clan(people.first())
            people.drop(1).forEach { joinMember(clan,it) }
            people.forEach { donate(it,clan) }
            advance("2026-11-16T06:00:00Z")
            val repository=PostgresClanRaceRepository(source)
            val results=coroutineScope { (1..3).map { async { ClanRaceSettlement(repository,{now}).settleDueWeeks() } }.awaitAll() }
            assertEquals(2,results.sum())
            assertEquals("50",scalar("SELECT count(*) FROM clan_race_entitlements"))
            assertEquals("2",scalar("SELECT count(*) FROM clan_race_settlements"))
            assertEquals("500000",scalar("SELECT payload::jsonb->'standings'->0->>'scoreUnits' FROM clan_race_settlements WHERE week_start='2026-10-26'"))
            assertEquals("0",scalar("SELECT jsonb_array_length(payload::jsonb->'standings') FROM clan_race_settlements WHERE week_start='2026-11-09'"))
            assertEquals("0",scalar("SELECT count(*) FROM clan_race_settlements WHERE week_start='2026-11-02'"))
        }
    }
}

internal fun RaceFixture.joinMember(clan:String,user:String) {
    execute("INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES ('$clan','$user','MEMBER',CURRENT_TIMESTAMP)")
}
internal suspend fun RaceFixture.donate(user:String,clan:String) {
    fund(user)
    assertEquals(ClanDonationStatus.APPLIED,PostgresClanRepository(source,{now}).donateToClan(user,clan,UUID.randomUUID().toString(),ClanDonationCurrency.GOLD,100).status)
}
