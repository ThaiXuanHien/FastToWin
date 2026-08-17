package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.MatchDetailSnapshot
import com.hienthai.fastowin.protocol.MatchEventSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.SeasonSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

class PostgresPlayerProfileRepository(
    private val dataSource: DataSource
) : PlayerProfileRepository {
    override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val userId = UUID.fromString(playerId)
            val base = connection.prepareStatement(
                """
                SELECT p.display_name, p.player_code, p.avatar_url,
                       COALESCE(s.total_matches, 0) AS total_matches,
                       COALESCE(s.wins, 0) AS wins,
                       COALESCE(s.losses, 0) AS losses,
                       COALESCE(s.draws, 0) AS draws,
                       COALESCE(s.highest_score, 0) AS highest_score,
                       COALESCE(s.current_win_streak, 0) AS current_win_streak,
                       COALESCE(s.best_win_streak, 0) AS best_win_streak,
                       COALESCE(s.correct_selections, 0) AS correct_selections,
                       COALESCE(s.wrong_selections, 0) AS wrong_selections,
                       COALESCE(s.elo_rating, 1000) AS elo_rating,
                       CASE WHEN COALESCE(s.reaction_samples, 0) = 0 THEN 0
                            ELSE s.reaction_time_total_ms / s.reaction_samples END AS average_reaction_ms
                FROM profiles p
                LEFT JOIN player_stats s ON s.user_id = p.user_id
                WHERE p.user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (!result.next()) return@withContext null
                    PlayerProfileSnapshot(
                        displayName = result.getString("display_name"),
                        playerCode = result.getString("player_code"),
                        avatarId = result.getString("avatar_url"),
                        statistics = PlayerStatisticsSnapshot(
                            totalMatches = result.getInt("total_matches"),
                            wins = result.getInt("wins"),
                            losses = result.getInt("losses"),
                            draws = result.getInt("draws"),
                            highestScore = result.getInt("highest_score"),
                            currentWinStreak = result.getInt("current_win_streak"),
                            bestWinStreak = result.getInt("best_win_streak"),
                            correctSelections = result.getInt("correct_selections"),
                            wrongSelections = result.getInt("wrong_selections"),
                            averageReactionMillis = result.getLong("average_reaction_ms"),
                            eloRating = result.getInt("elo_rating")
                        )
                    )
                }
            }

            val recentMatches = connection.prepareStatement(
                """
                SELECT m.id, m.room_name, m.game_mode, m.ended_at,
                       mine.score AS player_score, mine.outcome,
                       COALESCE(rh.rating_change, 0) AS elo_change,
                       COALESCE(opponent.display_name, 'Đối thủ') AS opponent_name,
                       COALESCE(opponent.score, 0) AS opponent_score
                FROM match_players mine
                JOIN matches m ON m.id = mine.match_id
                LEFT JOIN rating_history rh ON rh.match_id = mine.match_id AND rh.user_id = mine.user_id
                LEFT JOIN match_players opponent ON opponent.match_id = mine.match_id AND opponent.user_id <> mine.user_id
                WHERE mine.user_id = ?
                ORDER BY m.ended_at DESC
                LIMIT 20
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(
                            MatchHistorySnapshot(
                                matchId = result.getObject("id", UUID::class.java).toString(),
                                roomName = result.getString("room_name"),
                                gameMode = ProtocolGameMode.valueOf(result.getString("game_mode")),
                                opponentName = result.getString("opponent_name"),
                                playerScore = result.getInt("player_score"),
                                opponentScore = result.getInt("opponent_score"),
                                outcome = MatchHistoryOutcome.valueOf(result.getString("outcome")),
                                endedAtEpochMillis = result.getTimestamp("ended_at").time,
                                eloChange = result.getInt("elo_change")
                            )
                        )
                    }
                }
            }
            val achievements = connection.prepareStatement(
                """
                SELECT a.code, a.title, a.description, ua.unlocked_at
                FROM user_achievements ua
                JOIN achievements a ON a.code = ua.achievement_code
                WHERE ua.user_id = ?
                ORDER BY a.sort_order
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(
                            AchievementSnapshot(
                                code = result.getString("code"),
                                title = result.getString("title"),
                                description = result.getString("description"),
                                unlockedAtEpochMillis = result.getTimestamp("unlocked_at").time
                            )
                        )
                    }
                }
            }
            val progressionRow = connection.prepareStatement(
                """
                SELECT COALESCE(experience_points, 0) AS experience_points,
                       COALESCE(equipped_frame_id, 'frame_default') AS equipped_frame_id,
                       COALESCE(equipped_title_id, 'title_rookie') AS equipped_title_id
                FROM player_stats
                WHERE user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        Triple(
                            result.getInt("experience_points"),
                            result.getString("equipped_frame_id"),
                            result.getString("equipped_title_id")
                        )
                    } else {
                        Triple(0, "frame_default", "title_rookie")
                    }
                }
            }
            val storedMissions = connection.prepareStatement(
                """
                SELECT mission_code, progress, target
                FROM user_missions
                WHERE user_id = ?
                  AND period_start IN (CURRENT_DATE, date_trunc('week', CURRENT_DATE)::date)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildMap {
                        while (result.next()) {
                            put(result.getString("mission_code"), result.getInt("progress") to result.getInt("target"))
                        }
                    }
                }
            }
            fun mission(code: String, title: String, target: Int): MissionSnapshot {
                val stored = storedMissions[code]
                val progress = (stored?.first ?: 0).coerceAtMost(target)
                return MissionSnapshot(code, title, progress, target, progress >= target)
            }
            val season = connection.prepareStatement(
                """
                SELECT s.name, s.ends_at, s.reward_description,
                       COALESCE(sr.rating, ?) AS rating
                FROM seasons s
                LEFT JOIN season_ratings sr ON sr.season_id = s.id AND sr.user_id = ?
                WHERE CURRENT_TIMESTAMP >= s.starts_at AND CURRENT_TIMESTAMP < s.ends_at
                ORDER BY s.starts_at DESC
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.setInt(1, base.statistics.eloRating)
                statement.setObject(2, userId)
                statement.executeQuery().use { result ->
                    if (!result.next()) null else {
                        val rating = result.getInt("rating")
                        SeasonSnapshot(
                            name = result.getString("name"),
                            tier = ratingTier(rating),
                            rating = rating,
                            endsAtEpochMillis = result.getTimestamp("ends_at").time,
                            rewardDescription = result.getString("reward_description")
                        )
                    }
                }
            }
            val experiencePoints = progressionRow.first
            val level = experiencePoints / EXPERIENCE_PER_LEVEL + 1
            val achievementCodes = achievements.mapTo(mutableSetOf()) { it.code }
            val unlockedFrames = unlockedFrameIds(level, achievementCodes)
            val unlockedTitles = setOf("title_rookie") + buildSet {
                if (base.statistics.wins >= 10) add("title_champion")
                if ("SPEED_50" in achievementCodes) add("title_speed")
            }
            val equippedFrameId = progressionRow.second.takeIf(unlockedFrames::contains) ?: "frame_default"
            val equippedTitleId = progressionRow.third.takeIf(unlockedTitles::contains) ?: "title_rookie"
            fun cosmetic(id: String, name: String, type: CosmeticType, unlocked: Boolean, equippedId: String) =
                CosmeticSnapshot(id, name, type, unlocked, unlocked && id == equippedId)
            val cosmetics = listOf(
                cosmetic("frame_default", "Khung cơ bản", CosmeticType.FRAME, true, equippedFrameId),
                cosmetic("frame_bronze", "Khung Đồng", CosmeticType.FRAME, "frame_bronze" in unlockedFrames, equippedFrameId),
                cosmetic("frame_gold", "Khung Vàng", CosmeticType.FRAME, "frame_gold" in unlockedFrames, equippedFrameId),
                cosmetic("frame_perfect", "Khung Hoàn hảo", CosmeticType.FRAME, "frame_perfect" in unlockedFrames, equippedFrameId),
                cosmetic("title_rookie", "Tân binh", CosmeticType.TITLE, true, equippedTitleId),
                cosmetic("title_champion", "Nhà vô địch", CosmeticType.TITLE, "title_champion" in unlockedTitles, equippedTitleId),
                cosmetic("title_speed", "Tia chớp", CosmeticType.TITLE, "title_speed" in unlockedTitles, equippedTitleId)
            )
            base.copy(
                recentMatches = recentMatches,
                achievements = achievements,
                progression = PlayerProgressionSnapshot(
                    level = level,
                    experiencePoints = experiencePoints,
                    currentLevelExperience = experiencePoints % EXPERIENCE_PER_LEVEL,
                    nextLevelExperience = EXPERIENCE_PER_LEVEL,
                    dailyMissions = listOf(
                        mission("DAILY_PLAY_3", "Chơi 3 trận hôm nay", 3),
                        mission("DAILY_WIN_1", "Thắng 1 trận hôm nay", 1)
                    ),
                    weeklyMissions = listOf(
                        mission("WEEKLY_CORRECT_100", "Chọn đúng 100 số trong tuần", 100),
                        mission("WEEKLY_PERFECT_1", "Thắng 1 trận không bấm sai", 1)
                    ),
                    cosmetics = cosmetics,
                    season = season
                )
            )
        }
    }

    override suspend fun findMatchDetail(playerId: String, matchId: String): MatchDetailSnapshot? {
        val summary = findByPlayerId(playerId)?.recentMatches?.firstOrNull { it.matchId == matchId }
            ?: return null
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                val playerUuid = UUID.fromString(playerId)
                val matchUuid = UUID.fromString(matchId)
                val durationMillis = connection.prepareStatement(
                    "SELECT started_at, ended_at FROM matches WHERE id = ?"
                ).use { statement ->
                    statement.setObject(1, matchUuid)
                    statement.executeQuery().use { result ->
                        if (!result.next()) return@withContext null
                        (result.getTimestamp("ended_at").time - result.getTimestamp("started_at").time)
                            .coerceAtLeast(0L)
                    }
                }
                val events = connection.prepareStatement(
                    """
                    SELECT e.sequence, e.user_id, mp.display_name, e.number, e.expected_number,
                           e.result, e.occurred_at
                    FROM match_events e
                    JOIN match_players mp ON mp.match_id = e.match_id AND mp.user_id = e.user_id
                    WHERE e.match_id = ?
                    ORDER BY e.sequence
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, matchUuid)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                val eventPlayerId = result.getObject("user_id", UUID::class.java)
                                add(MatchEventSnapshot(
                                    sequence = result.getInt("sequence"),
                                    playerName = result.getString("display_name"),
                                    isCurrentPlayer = eventPlayerId == playerUuid,
                                    number = result.getInt("number"),
                                    expectedNumber = result.getInt("expected_number"),
                                    accepted = result.getString("result") == "ACCEPTED",
                                    occurredAtEpochMillis = result.getTimestamp("occurred_at").time
                                ))
                            }
                        }
                    }
                }
                MatchDetailSnapshot(summary = summary, durationMillis = durationMillis, events = events)
            }
        }
    }

    override suspend fun equipCosmetics(playerId: String, frameId: String, titleId: String): Boolean {
        val profile = findByPlayerId(playerId) ?: return false
        val unlocked = profile.progression.cosmetics.filter(CosmeticSnapshot::unlocked)
        if (unlocked.none { it.type == CosmeticType.FRAME && it.id == frameId }) return false
        if (unlocked.none { it.type == CosmeticType.TITLE && it.id == titleId }) return false
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO player_stats (user_id, equipped_frame_id, equipped_title_id, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id) DO UPDATE SET
                        equipped_frame_id = EXCLUDED.equipped_frame_id,
                        equipped_title_id = EXCLUDED.equipped_title_id,
                        updated_at = EXCLUDED.updated_at
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(playerId))
                    statement.setString(2, frameId)
                    statement.setString(3, titleId)
                    statement.executeUpdate() == 1
                }
            }
        }
    }

    override suspend fun updateProfile(
        playerId: String,
        displayName: String,
        avatarId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE profiles
                SET display_name = ?, avatar_url = ?, updated_at = NOW()
                WHERE user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, displayName)
                statement.setString(2, avatarId)
                statement.setObject(3, UUID.fromString(playerId))
                statement.executeUpdate() == 1
            }
        }
    }

    private fun ratingTier(rating: Int): String = when {
        rating >= 1_800 -> "Kim cương"
        rating >= 1_500 -> "Bạch kim"
        rating >= 1_300 -> "Vàng"
        rating >= 1_100 -> "Bạc"
        else -> "Đồng"
    }

    private companion object {
        const val EXPERIENCE_PER_LEVEL = 100
    }
}

internal fun unlockedFrameIds(level: Int, achievementCodes: Set<String>): Set<String> = buildSet {
    add("frame_default")
    if (level >= 3) add("frame_bronze")
    if (level >= 10) add("frame_gold")
    if (level >= 15 && "PERFECT_GAME" in achievementCodes) add("frame_perfect")
}
