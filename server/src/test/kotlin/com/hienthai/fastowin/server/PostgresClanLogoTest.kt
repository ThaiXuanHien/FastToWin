package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PostgresClanLogoTest {
    @Test
    fun `repository rejects locked malformed and foreign changes without mutating clan`() = runTest { database { fixture ->
        val owner = fixture.player()
        val member = fixture.player()
        val outsider = fixture.player()
        val clanId = fixture.clan(owner, "Original clan", "shield", experience = 0, storedLevel = 50)
        fixture.execute("INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', CURRENT_TIMESTAMP)", clanId, member)

        assertEquals(ClanLogoUpdateStatus.LOCKED, fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "sword"))
        listOf("unknown", "Dragon", " dragon", "dragon ", "").forEach { logo ->
            assertEquals(ClanLogoUpdateStatus.INVALID_LOGO, fixture.repository.updateLogoId(clanId.toString(), owner.toString(), logo))
        }
        listOf(member, outsider).forEach { actor ->
            assertEquals(ClanLogoUpdateStatus.NOT_OWNER, fixture.repository.updateLogoId(clanId.toString(), actor.toString(), "flag"))
        }
        val clan = fixture.repository.getClanById(clanId.toString())!!
        assertEquals("Original clan", clan.name)
        assertEquals("shield", clan.logoId)
        assertEquals(ClanLogoUpdateStatus.CLAN_NOT_FOUND,
            fixture.repository.updateLogoId(UUID.randomUUID().toString(), owner.toString(), "shield"))
    } }

    @Test
    fun `logo changes use XP boundary and retain grandfathered equipped logo`() = runTest { database { fixture ->
        val owner = fixture.player()
        val clanId = fixture.clan(owner, "Grandfathered", "dragon", experience = 0)
        assertEquals("dragon", fixture.repository.getClanById(clanId.toString())?.logoId)
        assertEquals(ClanLogoUpdateStatus.UPDATED, fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "dragon"))
        assertEquals(ClanLogoUpdateStatus.LOCKED, fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "sword"))
        assertEquals("dragon", fixture.repository.getClanById(clanId.toString())?.logoId)
        assertEquals(ClanLogoUpdateStatus.UPDATED, fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "flag"))
        fixture.execute("UPDATE clans SET experience_points = 2499, level = 50 WHERE id = ?", clanId)
        assertEquals(ClanLogoUpdateStatus.LOCKED, fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "sword"))
        fixture.execute("UPDATE clans SET experience_points = 2500, level = 1 WHERE id = ?", clanId)
        assertEquals(ClanLogoUpdateStatus.UPDATED, fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "sword"))
        assertEquals("sword", fixture.repository.getClanById(clanId.toString())?.logoId)
        fixture.execute("UPDATE clans SET logo_id = 'legacy_unknown', experience_points = 0 WHERE id = ?", clanId)
        assertEquals("legacy_unknown", fixture.repository.getClanById(clanId.toString())?.logoId)
        assertEquals(ClanLogoUpdateStatus.INVALID_LOGO,
            fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "legacy_unknown"))
    } }

    @Test
    fun `logo authorization waits for clan lock and reads committed owner and experience`() = runTest { database { fixture ->
        val owner = fixture.player()
        val successor = fixture.player()
        val clanId = fixture.clan(owner, "Atomic", "shield", experience = 2500)
        coroutineScope {
            fixture.dataSource.connection.use { locked ->
                locked.autoCommit = false
                locked.prepareStatement("UPDATE clans SET experience_points = 0 WHERE id = ?").use {
                    it.setObject(1, clanId)
                    it.executeUpdate()
                }
                val started = CompletableDeferred<Unit>()
                val pending = async(Dispatchers.IO) {
                    started.complete(Unit)
                    fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "sword")
                }
                started.await()
                delay(100)
                assertFalse(pending.isCompleted)
                locked.commit()
                assertEquals(ClanLogoUpdateStatus.LOCKED, pending.await())
            }
            fixture.dataSource.connection.use { locked ->
                locked.autoCommit = false
                locked.prepareStatement("UPDATE clans SET owner_id = ? WHERE id = ?").use {
                    it.setObject(1, successor)
                    it.setObject(2, clanId)
                    it.executeUpdate()
                }
                val started = CompletableDeferred<Unit>()
                val pending = async(Dispatchers.IO) {
                    started.complete(Unit)
                    fixture.repository.updateLogoId(clanId.toString(), owner.toString(), "flag")
                }
                started.await()
                delay(100)
                assertFalse(pending.isCompleted)
                locked.commit()
                assertEquals(ClanLogoUpdateStatus.NOT_OWNER, pending.await())
            }
        }
        assertEquals("shield", fixture.repository.getClanById(clanId.toString())?.logoId)
    } }

    @Test
    fun `every personal ranking hydrates current clan identity for top and outside cutoff`() = runTest { database { fixture ->
        val first = fixture.player()
        val current = fixture.player()
        val firstClan = fixture.clan(first, "Alpha current", "dragon")
        val currentClan = fixture.clan(current, "Bravo current", "kirin")
        fixture.rankings(first, current)
        val repository = PostgresLeaderboardRepository(fixture.dataSource)

        fun assertClan(entry: LeaderboardEntrySnapshot?, id: UUID, name: String, logo: String) {
            requireNotNull(entry)
            assertEquals(id.toString(), entry.clanId)
            assertEquals(name, entry.clanName)
            assertEquals(logo, entry.clanLogoId)
        }
        val ranking = repository.load(current.toString(), 1)
        listOf(ranking.topPlayers, ranking.seasonTopPlayers, ranking.previousSeasonTopPlayers,
            ranking.topGoldPlayers, ranking.topGemPlayers).forEach { top ->
            assertEquals(1, top.single().rank)
            assertEquals(first.toString(), top.single().userId)
            assertClan(top.single(), firstClan, "Alpha current", "dragon")
        }
        listOf(ranking.currentPlayer, ranking.seasonCurrentPlayer, ranking.previousSeasonCurrentPlayer,
            ranking.currentGoldPlayer, ranking.currentGemPlayer).forEach { entry ->
            assertEquals(2, entry?.rank)
            assertClan(entry, currentClan, "Bravo current", "kirin")
        }
        listOf(ranking.topClans, ranking.topLevelClans, ranking.topGoldClans, ranking.topGemClans).forEach { top ->
            assertEquals("dragon", top.single().logoId)
        }
        listOf(ranking.currentClan, ranking.currentLevelClan, ranking.currentGoldClan, ranking.currentGemClan).forEach { entry ->
            assertEquals("kirin", entry?.logoId)
        }

        fixture.execute("UPDATE clans SET name = 'Renamed', logo_id = 'flag' WHERE id = ?", firstClan)
        fixture.execute("DELETE FROM clan_members WHERE user_id = ?", current)
        val refreshed = repository.load(current.toString(), 1)
        listOf(refreshed.topPlayers, refreshed.seasonTopPlayers, refreshed.previousSeasonTopPlayers,
            refreshed.topGoldPlayers, refreshed.topGemPlayers).forEach { top ->
            assertClan(top.single(), firstClan, "Renamed", "flag")
        }
        listOf(refreshed.currentPlayer, refreshed.seasonCurrentPlayer, refreshed.previousSeasonCurrentPlayer,
            refreshed.currentGoldPlayer, refreshed.currentGemPlayer).forEach { entry ->
            requireNotNull(entry)
            assertNull(entry.clanId)
            assertNull(entry.clanName)
            assertNull(entry.clanLogoId)
            assertEquals(2, entry.rank)
        }
    } }

    private class Fixture(val dataSource: HikariDataSource) {
        val repository = PostgresClanRepository(dataSource)

        suspend fun player(): UUID = UUID.fromString(
            PostgresGuestIdentityRepository(dataSource).resolveGuest("Logo test", null, 1_900_000_000_000L).playerId
        )

        fun clan(owner: UUID, name: String, logo: String, experience: Long = 0, storedLevel: Int = 1): UUID {
            val clan = UUID.randomUUID()
            execute("INSERT INTO clans (id, name, description, owner_id, logo_id, experience_points, level, created_at) VALUES (?, ?, '', ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                clan, name, owner, logo, experience, storedLevel)
            execute("INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'LEADER', CURRENT_TIMESTAMP)", clan, owner)
            return clan
        }

        fun rankings(first: UUID, current: UUID) {
            val match = UUID.randomUUID()
            execute("INSERT INTO matches (id, room_name, game_mode, started_at, ended_at) VALUES (?, 'Logos', 'ORDER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", match)
            execute("UPDATE seasons SET starts_at = CURRENT_TIMESTAMP - INTERVAL '1 day', ends_at = CURRENT_TIMESTAMP + INTERVAL '1 day'")
            val season = dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id FROM seasons ORDER BY season_number LIMIT 1").use { result -> result.next(); result.getObject(1, UUID::class.java) }
                }
            }
            val previous = UUID.randomUUID()
            execute("INSERT INTO seasons (id, name, starts_at, ends_at, reward_description, season_number, closed_at) VALUES (?, 'Previous', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days', '', 900, CURRENT_TIMESTAMP)", previous)
            listOf(first, current).forEachIndexed { index, user ->
                val rating = 1500 - index * 100
                execute("INSERT INTO player_stats (user_id, total_matches, wins, elo_rating, lifetime_earned_gold, lifetime_earned_gems, updated_at) VALUES (?, 10, 5, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (user_id) DO UPDATE SET total_matches = 10, wins = 5, elo_rating = EXCLUDED.elo_rating, lifetime_earned_gold = EXCLUDED.lifetime_earned_gold, lifetime_earned_gems = EXCLUDED.lifetime_earned_gems",
                    user, rating, 500 - index * 100, 50 - index * 10)
                execute("INSERT INTO rating_history (match_id, user_id, rating_before, rating_after, rating_change, created_at) VALUES (?, ?, 1000, ?, 100, CURRENT_TIMESTAMP)", match, user, rating)
                execute("INSERT INTO season_ratings (season_id, user_id, rating, matches_played, placement_matches, peak_rating, updated_at) VALUES (?, ?, ?, 10, 5, ?, CURRENT_TIMESTAMP)", season, user, rating, rating)
                execute("INSERT INTO season_leaderboard_archive (season_id, user_id, final_rank, display_name, player_code, rating, season_matches) SELECT ?, user_id, ?, 'Archived name', player_code, ?, 10 FROM profiles WHERE user_id = ?", previous, index + 1, rating, user)
                execute("UPDATE clans SET level = ?, experience_points = ?, donated_gold = ?, donated_gems = ? WHERE owner_id = ?",
                    3 - index, 2500 - index * 1000, 500 - index * 100, 50 - index * 10, user)
            }
        }

        fun execute(sql: String, vararg arguments: Any) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    arguments.forEachIndexed { index, argument -> statement.setObject(index + 1, argument) }
                    statement.executeUpdate()
                }
            }
        }
    }

    private suspend fun database(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        val username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        val password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        val schema = "clan_logo_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        fun source(jdbc: String) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = jdbc
            this.username = username
            this.password = password
            maximumPoolSize = 3
        })
        source(url).use { admin ->
            admin.connection.use { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                val schemaUrl = url + if ('?' in url) "&currentSchema=$schema" else "?currentSchema=$schema"
                source(schemaUrl).use { dataSource ->
                    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                        .schemas(schema).defaultSchema(schema).load().migrate()
                    block(Fixture(dataSource))
                }
            } finally {
                admin.connection.use { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
