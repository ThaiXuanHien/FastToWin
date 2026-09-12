package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class PostgresLeaderboardRepository(
    private val dataSource: DataSource
) : LeaderboardRepository {
    override suspend fun load(currentPlayerId: String, limit: Int): LeaderboardSnapshot =
        withContext(Dispatchers.IO) {
            val currentId = UUID.fromString(currentPlayerId)
            val topPlayers = mutableListOf<LeaderboardEntrySnapshot>()
            var currentPlayer: LeaderboardEntrySnapshot? = null
            val seasonTopPlayers = mutableListOf<LeaderboardEntrySnapshot>()
            var seasonCurrentPlayer: LeaderboardEntrySnapshot? = null
            var seasonName: String? = null
            val previousSeasonTopPlayers = mutableListOf<LeaderboardEntrySnapshot>()
            var previousSeasonCurrentPlayer: LeaderboardEntrySnapshot? = null
            var previousSeasonName: String? = null
            var goldLeaderboard = RankedPlayers()
            var gemLeaderboard = RankedPlayers()
            var levelClanLeaderboard = RankedClans()
            var goldClanLeaderboard = RankedClans()
            var gemClanLeaderboard = RankedClans()
            val safeLimit = limit.coerceIn(1, MAX_LEADERBOARD_SIZE)

            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    WITH ranked AS (
                        SELECT p.user_id, p.display_name, p.player_code, p.avatar_url,
                               s.wins, s.total_matches, s.highest_score, s.elo_rating,
                               COALESCE(s.equipped_frame_id, 'frame_default') AS frame_id,
                               ROW_NUMBER() OVER (
                                   ORDER BY s.elo_rating DESC,
                                            s.wins DESC,
                                            (s.wins::NUMERIC / NULLIF(s.total_matches, 0)) DESC NULLS LAST,
                                            s.highest_score DESC,
                                            s.updated_at ASC,
                                            p.user_id ASC
                               ) AS rank
                        FROM player_stats s
                        JOIN profiles p ON p.user_id = s.user_id
                        JOIN users u ON u.id = s.user_id
                        WHERE s.total_matches > 0
                          AND u.status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1 FROM rating_history rh WHERE rh.user_id = s.user_id
                          )
                    )
                    SELECT * FROM ranked WHERE rank <= ? OR user_id = ? ORDER BY rank
                    """.trimIndent()
                ).use { statement ->
                    statement.setInt(1, safeLimit)
                    statement.setObject(2, currentId)
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            val entry = LeaderboardEntrySnapshot(
                                rank = result.getInt("rank"),
                                displayName = result.getString("display_name"),
                                playerCode = result.getString("player_code"),
                                wins = result.getInt("wins"),
                                totalMatches = result.getInt("total_matches"),
                                highestScore = result.getInt("highest_score"),
                                eloRating = result.getInt("elo_rating"),
                                userId = result.getObject("user_id", UUID::class.java).toString(),
                                avatarId = result.getString("avatar_url"),
                                frameId = result.getString("frame_id")
                            )
                            if (result.getObject("user_id", UUID::class.java) == currentId) currentPlayer = entry
                            if (entry.rank <= safeLimit) topPlayers += entry
                        }
                    }
                }
                goldLeaderboard = connection.loadAssetLeaderboard(currentId, safeLimit, PlayerAssetMetric.GOLD)
                gemLeaderboard = connection.loadAssetLeaderboard(currentId, safeLimit, PlayerAssetMetric.GEMS)
                levelClanLeaderboard = connection.loadClanLeaderboard(currentId, safeLimit, ClanMetric.LEVEL)
                goldClanLeaderboard = connection.loadClanLeaderboard(currentId, safeLimit, ClanMetric.GOLD)
                gemClanLeaderboard = connection.loadClanLeaderboard(currentId, safeLimit, ClanMetric.GEMS)
                
                connection.prepareStatement(
                    "SELECT id, name FROM seasons WHERE CURRENT_TIMESTAMP >= starts_at AND CURRENT_TIMESTAMP < ends_at ORDER BY starts_at DESC LIMIT 1"
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            val seasonId = result.getObject("id", UUID::class.java)
                            seasonName = result.getString("name")
                            connection.prepareStatement(
                                """
                                WITH ranked AS (
                                    SELECT p.user_id, p.display_name, p.player_code, p.avatar_url,
                                           ps.wins, ps.total_matches, ps.highest_score,
                                           sr.rating AS elo_rating,
                                           COALESCE(ps.equipped_frame_id, 'frame_default') AS frame_id,
                                           ROW_NUMBER() OVER (
                                               ORDER BY sr.rating DESC, sr.matches_played ASC, sr.updated_at ASC, p.user_id
                                           ) AS rank
                                    FROM season_ratings sr
                                    JOIN profiles p ON p.user_id = sr.user_id
                                    JOIN player_stats ps ON ps.user_id = sr.user_id
                                    JOIN users u ON u.id = sr.user_id
                                    WHERE sr.season_id = ? AND sr.placement_matches >= 5 AND u.status = 'ACTIVE'
                                )
                                SELECT * FROM ranked WHERE rank <= ? OR user_id = ? ORDER BY rank
                                """.trimIndent()
                            ).use { seasonStatement ->
                                seasonStatement.setObject(1, seasonId)
                                seasonStatement.setInt(2, safeLimit)
                                seasonStatement.setObject(3, currentId)
                                seasonStatement.executeQuery().use { seasonResult ->
                                    while (seasonResult.next()) {
                                        val entry = LeaderboardEntrySnapshot(
                                            rank = seasonResult.getInt("rank"),
                                            displayName = seasonResult.getString("display_name"),
                                            playerCode = seasonResult.getString("player_code"),
                                            wins = seasonResult.getInt("wins"),
                                            totalMatches = seasonResult.getInt("total_matches"),
                                            highestScore = seasonResult.getInt("highest_score"),
                                            eloRating = seasonResult.getInt("elo_rating"),
                                            userId = seasonResult.getObject("user_id", UUID::class.java).toString(),
                                            avatarId = seasonResult.getString("avatar_url"),
                                            frameId = seasonResult.getString("frame_id")
                                        )
                                        if (seasonResult.getObject("user_id", UUID::class.java) == currentId) seasonCurrentPlayer = entry
                                        if (entry.rank <= limit) seasonTopPlayers += entry
                                    }
                                }
                            }
                        }
                    }
                }

                connection.prepareStatement(
                    "SELECT id, name FROM seasons WHERE closed_at IS NOT NULL ORDER BY ends_at DESC LIMIT 1"
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            val seasonId = result.getObject("id", UUID::class.java)
                            previousSeasonName = result.getString("name")
                            connection.prepareStatement(
                                """
                                SELECT user_id, final_rank AS rank, display_name, player_code,
                                       avatar_url, frame_id, wins, total_matches, highest_score,
                                       rating AS elo_rating
                                FROM season_leaderboard_archive
                                WHERE season_id = ? AND (final_rank <= ? OR user_id = ?)
                                ORDER BY final_rank
                                """.trimIndent()
                            ).use { archiveStatement ->
                                archiveStatement.setObject(1, seasonId)
                                archiveStatement.setInt(2, safeLimit)
                                archiveStatement.setObject(3, currentId)
                                archiveStatement.executeQuery().use { archiveResult ->
                                    while (archiveResult.next()) {
                                        val entry = LeaderboardEntrySnapshot(
                                            rank = archiveResult.getInt("rank"),
                                            displayName = archiveResult.getString("display_name"),
                                            playerCode = archiveResult.getString("player_code"),
                                            wins = archiveResult.getInt("wins"),
                                            totalMatches = archiveResult.getInt("total_matches"),
                                            highestScore = archiveResult.getInt("highest_score"),
                                            eloRating = archiveResult.getInt("elo_rating"),
                                            userId = archiveResult.getObject("user_id", UUID::class.java).toString(),
                                            avatarId = archiveResult.getString("avatar_url"),
                                            frameId = archiveResult.getString("frame_id")
                                        )
                                        if (archiveResult.getObject("user_id", UUID::class.java) == currentId) {
                                            previousSeasonCurrentPlayer = entry
                                        }
                                        if (entry.rank <= limit) previousSeasonTopPlayers += entry
                                    }
                                }
                            }
                        }
                    }
                }
            }
            LeaderboardSnapshot(
                topPlayers = topPlayers,
                currentPlayer = currentPlayer,
                seasonName = seasonName,
                seasonTopPlayers = seasonTopPlayers,
                seasonCurrentPlayer = seasonCurrentPlayer,
                previousSeasonName = previousSeasonName,
                previousSeasonTopPlayers = previousSeasonTopPlayers,
                previousSeasonCurrentPlayer = previousSeasonCurrentPlayer,
                topClans = levelClanLeaderboard.top,
                currentClan = levelClanLeaderboard.current,
                topGoldPlayers = goldLeaderboard.top,
                currentGoldPlayer = goldLeaderboard.current,
                topGemPlayers = gemLeaderboard.top,
                currentGemPlayer = gemLeaderboard.current,
                topLevelClans = levelClanLeaderboard.top,
                currentLevelClan = levelClanLeaderboard.current,
                topGoldClans = goldClanLeaderboard.top,
                currentGoldClan = goldClanLeaderboard.current,
                topGemClans = gemClanLeaderboard.top,
                currentGemClan = gemClanLeaderboard.current
            )
        }

    private fun Connection.loadAssetLeaderboard(
        currentPlayerId: UUID,
        limit: Int,
        metric: PlayerAssetMetric
    ): RankedPlayers {
        val entries = mutableListOf<LeaderboardEntrySnapshot>()
        var current: LeaderboardEntrySnapshot? = null
        prepareStatement(
            """
            WITH ranked AS (
                SELECT p.user_id, p.display_name, p.player_code, p.avatar_url,
                       s.wins, s.total_matches, s.highest_score, s.elo_rating,
                       s.lifetime_earned_gold, s.lifetime_earned_gems,
                       COALESCE(s.equipped_frame_id, 'frame_default') AS frame_id,
                       ROW_NUMBER() OVER (
                           ORDER BY ${metric.valueColumn} DESC,
                                    ${metric.reachedAtColumn} ASC NULLS LAST,
                                    p.user_id ASC
                       ) AS rank
                FROM player_stats s
                JOIN profiles p ON p.user_id = s.user_id
                JOIN users u ON u.id = s.user_id
                WHERE ${metric.valueColumn} > 0 AND u.status = 'ACTIVE'
            )
            SELECT * FROM ranked WHERE rank <= ? OR user_id = ? ORDER BY rank
            """.trimIndent()
        ).use { statement ->
            statement.setInt(1, limit)
            statement.setObject(2, currentPlayerId)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val userId = result.getObject("user_id", UUID::class.java)
                    val entry = LeaderboardEntrySnapshot(
                        rank = result.getInt("rank"),
                        displayName = result.getString("display_name"),
                        playerCode = result.getString("player_code"),
                        wins = result.getInt("wins"),
                        totalMatches = result.getInt("total_matches"),
                        highestScore = result.getInt("highest_score"),
                        eloRating = result.getInt("elo_rating"),
                        userId = userId.toString(),
                        avatarId = result.getString("avatar_url"),
                        frameId = result.getString("frame_id"),
                        lifetimeEarnedGold = result.getLong("lifetime_earned_gold"),
                        lifetimeEarnedGems = result.getLong("lifetime_earned_gems")
                    )
                    if (userId == currentPlayerId) current = entry
                    if (entry.rank <= limit) entries += entry
                }
            }
        }
        return RankedPlayers(top = entries, current = current)
    }

    private fun Connection.loadClanLeaderboard(
        currentPlayerId: UUID,
        limit: Int,
        metric: ClanMetric
    ): RankedClans {
        val entries = mutableListOf<ClanLeaderboardEntrySnapshot>()
        var current: ClanLeaderboardEntrySnapshot? = null
        val currentClanId = currentClanId(currentPlayerId)
        prepareStatement(
            """
            WITH clan_stats AS (
                SELECT c.id, c.name, c.level, c.experience_points,
                       c.donated_gold, c.donated_gems, c.level_reached_at,
                       COUNT(m.user_id) AS member_count,
                       COALESCE(SUM(ps.elo_rating), 0) AS total_elo
                FROM clans c
                LEFT JOIN clan_members m ON m.clan_id = c.id
                LEFT JOIN player_stats ps ON ps.user_id = m.user_id
                GROUP BY c.id
            ),
            ranked AS (
                SELECT *, ROW_NUMBER() OVER (ORDER BY ${metric.orderBy}) AS rank
                FROM clan_stats
            )
            SELECT * FROM ranked
            WHERE rank <= ? OR id = ?
            ORDER BY rank
            """.trimIndent()
        ).use { statement ->
            statement.setInt(1, limit)
            statement.setObject(2, currentClanId)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val clanUuid = result.getObject("id", UUID::class.java)
                    val entry = ClanLeaderboardEntrySnapshot(
                        rank = result.getInt("rank"),
                        clanId = clanUuid.toString(),
                        clanName = result.getString("name"),
                        totalElo = result.getInt("total_elo"),
                        memberCount = result.getInt("member_count"),
                        level = result.getInt("level"),
                        experiencePoints = result.getLong("experience_points"),
                        donatedGold = result.getLong("donated_gold"),
                        donatedGems = result.getLong("donated_gems")
                    )
                    if (clanUuid == currentClanId) current = entry
                    if (entry.rank <= limit) entries += entry
                }
            }
        }
        return RankedClans(top = entries, current = current)
    }

    private fun Connection.currentClanId(playerId: UUID): UUID? =
        prepareStatement("SELECT clan_id FROM clan_members WHERE user_id = ?").use { statement ->
            statement.setObject(1, playerId)
            statement.executeQuery().use { result ->
                if (result.next()) result.getObject("clan_id", UUID::class.java) else null
            }
        }

    private data class RankedPlayers(
        val top: List<LeaderboardEntrySnapshot> = emptyList(),
        val current: LeaderboardEntrySnapshot? = null
    )

    private data class RankedClans(
        val top: List<ClanLeaderboardEntrySnapshot> = emptyList(),
        val current: ClanLeaderboardEntrySnapshot? = null
    )

    private enum class PlayerAssetMetric(val valueColumn: String, val reachedAtColumn: String) {
        GOLD("s.lifetime_earned_gold", "s.lifetime_earned_gold_reached_at"),
        GEMS("s.lifetime_earned_gems", "s.lifetime_earned_gems_reached_at")
    }

    private enum class ClanMetric(val orderBy: String) {
        LEVEL("level DESC, experience_points DESC, level_reached_at ASC, id ASC"),
        GOLD("donated_gold DESC, level DESC, id ASC"),
        GEMS("donated_gems DESC, level DESC, id ASC")
    }

    private companion object {
        const val MAX_LEADERBOARD_SIZE = 100
    }
}
