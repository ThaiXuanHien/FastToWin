package com.hienthai.fastowin.server.clanrace

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.UUID
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlin.test.*

class ClanRacePoolsPostgresTest {
    @Test fun `frozen clan bonus credits wallet and recorded breakdown exactly once`() = runTest {
        raceDatabase {
            val winner=player().userId;val loser=player().userId
            val id=UUID.randomUUID().toString()
            val match=CompletedMatch(id,"Clan bonus",ProtocolGameMode.ORDER,now,now+1000,winner,
                listOf(CompletedMatchPlayer(winner,"Winner",50,MatchOutcome.WIN),CompletedMatchPlayer(loser,"Loser",0,MatchOutcome.LOSS)),
                matchType=MatchType.RANKED,eventRewards=MatchRewardEventSnapshot(goldMultiplier=2),clanLevels=mapOf(winner to 10,loser to 10))
            val repository=PostgresMatchResultRepository(source)
            repository.save(match);repository.save(match)
            assertEquals("210",scalar("SELECT gold_delta FROM wallet_transactions WHERE source_type='MATCH' AND source_id='$id' AND user_id='$winner'"))
            assertEquals("10",scalar("SELECT reward_breakdown->>'clanBonusGold' FROM match_players WHERE match_id='$id' AND user_id='$winner'"))
            assertEquals("210",scalar("SELECT reward_breakdown->>'totalGold' FROM match_players WHERE match_id='$id' AND user_id='$winner'"))
            assertEquals("1",scalar("SELECT count(*) FROM wallet_transactions WHERE source_type='MATCH' AND source_id='$id' AND user_id='$winner'"))
        }
    }

    @Test fun `late freeze reads completed historical contribution weeks before current week points`() = runTest {
        raceDatabase {
            publishRace();publishRace("2026-11-02")
            val owner=player().userId;val clan=clan(owner)
            advance("2026-10-26T06:00:00Z")
            donate(owner,clan);donate(owner,clan)
            advance("2026-11-02T06:00:00Z")
            donate(owner,clan)
            PostgresClanRaceRepository(source).transaction { ensureRacePools(LocalDate.parse("2026-11-02"),now) }
            assertEquals("20000",scalar("SELECT prior_score_units FROM clan_race_pool_members WHERE week_start='2026-11-02' AND clan_id='$clan'"))
            assertEquals("strength-1",scalar("SELECT pool_id FROM clan_race_pool_members WHERE week_start='2026-11-02' AND clan_id='$clan'"))
        }
    }

    @Test fun `late freeze reconstructs roster at start and each pool settles its own top three`() = runTest {
        raceDatabase {
            publishRace()
            val owners=(1..21).map { player().userId }
            val clans=owners.map { clan(it) }
            advance("2026-10-26T06:00:00Z")
            val extra=player().userId
            joinMember(clans.first(),extra)
            owners.zip(clans).forEach { (owner,clan) -> donate(owner,clan) }
            val repository=PostgresClanRaceRepository(source)
            repository.transaction { ensureRacePools(LocalDate.parse("2026-10-26"),now) }
            assertEquals("2",scalar("SELECT count(DISTINCT pool_id) FROM clan_race_pool_members"))
            assertEquals("1",scalar("SELECT active_members FROM clan_race_pool_members WHERE clan_id='${clans.first()}'"))
            assertEquals(listOf(10,11),scalar("SELECT string_agg(n::text,',' ORDER BY n) FROM (SELECT count(*) n FROM clan_race_pool_members GROUP BY pool_id) counts").split(',').map(String::toInt))
            val frozen=scalar("SELECT pool_id FROM clan_race_pool_members WHERE clan_id='${clans.first()}'")
            advance("2026-10-28T06:00:00Z")
            val newcomer=player().userId;val newClan=clan(newcomer);donate(newcomer,newClan)
            val restarted=PostgresClanRaceRepository(source)
            restarted.transaction { ensureRacePools(LocalDate.parse("2026-10-26"),now) }
            assertEquals(frozen,scalar("SELECT pool_id FROM clan_race_pool_members WHERE clan_id='${clans.first()}'"))
            assertTrue(scalar("SELECT pool_id FROM clan_race_pool_members WHERE clan_id='$newClan'").startsWith("starter-"))
            advance("2026-11-02T06:00:00Z")
            assertEquals(1,ClanRaceSettlement(restarted,{now}).settleDueWeeks())
            assertEquals("6",scalar("SELECT jsonb_array_length(payload::jsonb->'winners') FROM clan_race_settlements"))
            assertEquals("6",scalar("SELECT count(*) FROM clan_race_entitlements"))
            assertEquals(0,ClanRaceSettlement(restarted,{now}).settleDueWeeks())
        }
    }
}
