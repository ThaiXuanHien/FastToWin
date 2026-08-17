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
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_FRAME_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_TITLE_TARGET
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.SeasonSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Date
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

private val DAILY_CHECK_IN_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")

class PostgresPlayerProfileRepository(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.system(DAILY_CHECK_IN_ZONE)
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
                       COALESCE(equipped_title_id, 'title_rookie') AS equipped_title_id,
                       COALESCE(current_daily_check_in_streak, 0) AS current_daily_check_in_streak,
                       COALESCE(best_daily_check_in_streak, 0) AS best_daily_check_in_streak,
                       COALESCE(total_daily_check_ins, 0) AS total_daily_check_ins,
                       last_daily_check_in_date
                FROM player_stats
                WHERE user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        ProgressionRow(
                            experiencePoints = result.getInt("experience_points"),
                            equippedFrameId = result.getString("equipped_frame_id"),
                            equippedTitleId = result.getString("equipped_title_id"),
                            currentDailyCheckInStreak = result.getInt("current_daily_check_in_streak"),
                            bestDailyCheckInStreak = result.getInt("best_daily_check_in_streak"),
                            totalDailyCheckIns = result.getInt("total_daily_check_ins"),
                            lastDailyCheckInDate = result.getDate("last_daily_check_in_date")?.toLocalDate()
                        )
                    } else {
                        ProgressionRow()
                    }
                }
            }
            val today = currentCheckInDate()
            val recentCheckInDates = connection.prepareStatement(
                """
                SELECT check_in_date
                FROM daily_check_ins
                WHERE user_id = ?
                  AND check_in_date >= ?
                  AND check_in_date <= ?
                ORDER BY check_in_date
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setDate(2, Date.valueOf(today.minusMonths(11).withDayOfMonth(1)))
                statement.setDate(3, Date.valueOf(today))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.getDate("check_in_date").toLocalDate().toString())
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
            val experiencePoints = progressionRow.experiencePoints
            val level = experiencePoints / EXPERIENCE_PER_LEVEL + 1
            val achievementCodes = achievements.mapTo(mutableSetOf()) { it.code }
            val unlockedFrames = unlockedFrameIds(
                level,
                achievementCodes,
                progressionRow.totalDailyCheckIns
            )
            val unlockedTitles = unlockedTitleIds(
                base.statistics.wins,
                achievementCodes,
                progressionRow.bestDailyCheckInStreak
            )
            val unlockedAvatars = unlockedAvatarIds(progressionRow.totalDailyCheckIns)
            val equippedFrameId = progressionRow.equippedFrameId.takeIf(unlockedFrames::contains) ?: "frame_default"
            val equippedTitleId = progressionRow.equippedTitleId.takeIf(unlockedTitles::contains) ?: "title_rookie"
            val checkInDecision = dailyCheckInDecision(
                progressionRow.currentDailyCheckInStreak,
                progressionRow.lastDailyCheckInDate,
                today
            )
            val activeCheckInStreak = when (progressionRow.lastDailyCheckInDate) {
                today, today.minusDays(1) -> progressionRow.currentDailyCheckInStreak
                else -> 0
            }
            fun cosmetic(id: String, name: String, type: CosmeticType, unlocked: Boolean, equippedId: String?) =
                CosmeticSnapshot(id, name, type, unlocked, unlocked && id == equippedId)
            val cosmetics = listOf(
                cosmetic("frame_default", "Khung cơ bản", CosmeticType.FRAME, true, equippedFrameId),
                cosmetic("frame_bronze", "Khung Đồng", CosmeticType.FRAME, "frame_bronze" in unlockedFrames, equippedFrameId),
                cosmetic("frame_gold", "Khung Vàng", CosmeticType.FRAME, "frame_gold" in unlockedFrames, equippedFrameId),
                cosmetic("frame_perfect", "Khung Hoàn hảo", CosmeticType.FRAME, "frame_perfect" in unlockedFrames, equippedFrameId),
                cosmetic("frame_persistent", "Khung Bền bỉ", CosmeticType.FRAME, "frame_persistent" in unlockedFrames, equippedFrameId),
                cosmetic("title_rookie", "Tân binh", CosmeticType.TITLE, true, equippedTitleId),
                cosmetic("title_champion", "Nhà vô địch", CosmeticType.TITLE, "title_champion" in unlockedTitles, equippedTitleId),
                cosmetic("title_speed", "Tia chớp", CosmeticType.TITLE, "title_speed" in unlockedTitles, equippedTitleId),
                cosmetic("title_diligent", "Chuyên cần", CosmeticType.TITLE, "title_diligent" in unlockedTitles, equippedTitleId),
                cosmetic(
                    DAILY_CHECK_IN_AVATAR_ID,
                    "Ảnh đại diện Điểm danh",
                    CosmeticType.AVATAR,
                    DAILY_CHECK_IN_AVATAR_ID in unlockedAvatars,
                    base.avatarId
                )
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
                    dailyCheckIn = DailyCheckInSnapshot(
                        claimedToday = checkInDecision.claimedToday,
                        cycleDay = checkInDecision.cycleDay,
                        todayRewardXp = checkInDecision.rewardXp,
                        nextRewardXp = nextDailyCheckInReward(checkInDecision.cycleDay),
                        currentStreak = activeCheckInStreak,
                        bestStreak = progressionRow.bestDailyCheckInStreak,
                        totalCheckIns = progressionRow.totalDailyCheckIns,
                        lastCheckInDate = progressionRow.lastDailyCheckInDate?.toString(),
                        todayDate = today.toString(),
                        historyDates = recentCheckInDates
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

    override suspend fun claimDailyCheckIn(playerId: String): DailyCheckInClaimResult? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val userId = UUID.fromString(playerId)
                    connection.prepareStatement(
                        "INSERT INTO player_stats (user_id, updated_at) VALUES (?, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (user_id) DO NOTHING"
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                    val stored = connection.prepareStatement(
                        """
                        SELECT current_daily_check_in_streak, last_daily_check_in_date
                        FROM player_stats
                        WHERE user_id = ?
                        FOR UPDATE
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeQuery().use { result ->
                            if (!result.next()) error("Missing player stats for $playerId")
                            result.getInt("current_daily_check_in_streak") to
                                result.getDate("last_daily_check_in_date")?.toLocalDate()
                        }
                    }
                    val today = currentCheckInDate()
                    val decision = dailyCheckInDecision(stored.first, stored.second, today)
                    if (decision.claimedToday) {
                        connection.commit()
                        DailyCheckInClaimResult(claimed = false, rewardXp = 0)
                    } else {
                        connection.prepareStatement(
                            """
                            UPDATE player_stats
                            SET experience_points = experience_points + ?,
                                current_daily_check_in_streak = ?,
                                best_daily_check_in_streak = GREATEST(best_daily_check_in_streak, ?),
                                total_daily_check_ins = total_daily_check_ins + 1,
                                last_daily_check_in_date = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ?
                            """.trimIndent()
                        ).use { statement ->
                            statement.setInt(1, decision.rewardXp)
                            statement.setInt(2, decision.resultingStreak)
                            statement.setInt(3, decision.resultingStreak)
                            statement.setDate(4, Date.valueOf(today))
                            statement.setObject(5, userId)
                            check(statement.executeUpdate() == 1)
                        }
                        connection.prepareStatement(
                            """
                            INSERT INTO daily_check_ins (
                                user_id, check_in_date, cycle_day, reward_xp, streak_after
                            ) VALUES (?, ?, ?, ?, ?)
                            """.trimIndent()
                        ).use { statement ->
                            statement.setObject(1, userId)
                            statement.setDate(2, Date.valueOf(today))
                            statement.setInt(3, decision.cycleDay)
                            statement.setInt(4, decision.rewardXp)
                            statement.setInt(5, decision.resultingStreak)
                            statement.executeUpdate()
                        }
                        if (decision.resultingStreak >= DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET) {
                            connection.prepareStatement(
                                """
                                INSERT INTO user_achievements (
                                    user_id, achievement_code, unlocked_at, match_id
                                ) VALUES (?, 'DAILY_STREAK_7', CURRENT_TIMESTAMP, NULL)
                                ON CONFLICT (user_id, achievement_code) DO NOTHING
                                """.trimIndent()
                            ).use { statement ->
                                statement.setObject(1, userId)
                                statement.executeUpdate()
                            }
                        }
                        connection.commit()
                        DailyCheckInClaimResult(claimed = true, rewardXp = decision.rewardXp)
                    }
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                }
            }
        }

    override suspend fun updateProfile(
        playerId: String,
        displayName: String,
        avatarId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (avatarId == DAILY_CHECK_IN_AVATAR_ID) {
            val unlocked = dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT total_daily_check_ins FROM player_stats WHERE user_id = ?"
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(playerId))
                    statement.executeQuery().use { result ->
                        result.next() && result.getInt("total_daily_check_ins") >= DAILY_CHECK_IN_AVATAR_TARGET
                    }
                }
            }
            if (!unlocked) return@withContext false
        }
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

    private fun currentCheckInDate(): LocalDate = LocalDate.now(clock)

    private companion object {
        const val EXPERIENCE_PER_LEVEL = 100
    }
}

private data class ProgressionRow(
    val experiencePoints: Int = 0,
    val equippedFrameId: String = "frame_default",
    val equippedTitleId: String = "title_rookie",
    val currentDailyCheckInStreak: Int = 0,
    val bestDailyCheckInStreak: Int = 0,
    val totalDailyCheckIns: Int = 0,
    val lastDailyCheckInDate: LocalDate? = null
)

internal fun unlockedFrameIds(
    level: Int,
    achievementCodes: Set<String>,
    totalDailyCheckIns: Int = 0
): Set<String> = buildSet {
    add("frame_default")
    if (level >= 3) add("frame_bronze")
    if (level >= 10) add("frame_gold")
    if (level >= 15 && "PERFECT_GAME" in achievementCodes) add("frame_perfect")
    if (totalDailyCheckIns >= DAILY_CHECK_IN_FRAME_TARGET) add("frame_persistent")
}

internal fun unlockedTitleIds(
    wins: Int,
    achievementCodes: Set<String>,
    bestDailyCheckInStreak: Int = 0
): Set<String> = buildSet {
    add("title_rookie")
    if (wins >= 10) add("title_champion")
    if ("SPEED_50" in achievementCodes) add("title_speed")
    if (bestDailyCheckInStreak >= DAILY_CHECK_IN_TITLE_TARGET) add("title_diligent")
}

internal fun unlockedAvatarIds(totalDailyCheckIns: Int): Set<String> = buildSet {
    if (totalDailyCheckIns >= DAILY_CHECK_IN_AVATAR_TARGET) add(DAILY_CHECK_IN_AVATAR_ID)
}
