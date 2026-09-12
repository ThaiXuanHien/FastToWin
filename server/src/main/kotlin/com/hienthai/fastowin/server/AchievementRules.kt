package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MissionDifficulty
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

internal data class AchievementReward(
    val gold: Int,
    val gems: Int,
    val xp: Int
)

internal data class AchievementDefinition(
    val code: String,
    val title: String,
    val description: String,
    val titleKey: String,
    val descriptionKey: String,
    val difficulty: MissionDifficulty,
    val target: Int,
    val reward: AchievementReward,
    val frameId: String? = null,
    val titleId: String? = null
)

private val ACHIEVEMENT_REWARDS = mapOf(
    MissionDifficulty.EASY to AchievementReward(gold = 100, gems = 0, xp = 20),
    MissionDifficulty.NORMAL to AchievementReward(gold = 250, gems = 0, xp = 50),
    MissionDifficulty.HARD to AchievementReward(gold = 500, gems = 1, xp = 100),
    MissionDifficulty.ELITE to AchievementReward(gold = 1_000, gems = 3, xp = 200)
)

internal val ACHIEVEMENT_DEFINITIONS = listOf(
    achievement("FIRST_WIN", legacyFallback("Khai Chiến"), legacyFallback("Thắng trận online đầu tiên."), MissionDifficulty.EASY, 1, titleId = "title_first_battle"),
    achievement("WINS_10", legacyFallback("Cao Thủ"), legacyFallback("Thắng 10 trận online."), MissionDifficulty.NORMAL, 10, "frame_warrior", "title_master"),
    achievement("WINS_50", legacyFallback("Bách Chiến"), legacyFallback("Thắng 50 trận online."), MissionDifficulty.HARD, 50, "frame_veteran", "title_veteran"),
    achievement("RANKED_WINS_100", legacyFallback("Kẻ Chinh Phục"), legacyFallback("Thắng 100 trận đấu hạng."), MissionDifficulty.ELITE, 100, "frame_diamond", "title_conqueror"),
    achievement("WIN_STREAK_10", legacyFallback("Bất Bại"), legacyFallback("Đạt chuỗi thắng 10 trận."), MissionDifficulty.ELITE, 10, "frame_unyielding", "title_undefeated"),
    achievement("PERFECT_MATCH_1", legacyFallback("Nhất Kích"), legacyFallback("Thắng một trận không chọn sai."), MissionDifficulty.EASY, 1, titleId = "title_one_strike"),
    achievement("PERFECT_MATCHES_10", legacyFallback("Quán Quân"), legacyFallback("Thắng 10 trận không chọn sai."), MissionDifficulty.HARD, 10, frameId = "frame_champion"),
    achievement("ACCURACY_90_TEN", legacyFallback("Mắt Thần"), legacyFallback("Đạt ít nhất 90% chính xác trong 10 trận."), MissionDifficulty.NORMAL, 10, titleId = "title_divine_eye"),
    achievement("RESPONSE_2500_TEN", legacyFallback("Phản Xạ Vàng"), legacyFallback("Phản ứng trung bình dưới 2,5 giây trong 10 trận."), MissionDifficulty.HARD, 10, "frame_speed_shadow", "title_golden_reflex"),
    achievement("RESPONSE_1500_TEN", legacyFallback("Thần Tốc"), legacyFallback("Phản ứng trung bình dưới 1,5 giây trong 10 trận."), MissionDifficulty.ELITE, 10, "frame_lightning", "title_godspeed"),
    achievement("CHECKIN_STREAK_7", legacyFallback("Liệt Hỏa"), legacyFallback("Điểm danh liên tiếp 7 ngày."), MissionDifficulty.EASY, 7, frameId = "frame_wildfire"),
    achievement("CHECKIN_STREAK_30", legacyFallback("Bất Diệt"), legacyFallback("Điểm danh liên tiếp 30 ngày."), MissionDifficulty.HARD, 30, frameId = "frame_immortal"),
    achievement("CHECKINS_50", legacyFallback("Bền Bỉ"), legacyFallback("Điểm danh tổng cộng 50 lần."), MissionDifficulty.NORMAL, 50),
    achievement("CHECKINS_100", legacyFallback("Chí Tôn"), legacyFallback("Điểm danh tổng cộng 100 lần."), MissionDifficulty.ELITE, 100, frameId = "frame_supreme"),
    achievement("PLAYER_LEVEL_30", legacyFallback("Chiến Thần"), legacyFallback("Đạt cấp người chơi 30."), MissionDifficulty.HARD, 30, "frame_legend", "title_war_god"),
    achievement("CLAN_JOINED", legacyFallback("Vinh Quang"), legacyFallback("Tham gia một bang."), MissionDifficulty.EASY, 1, frameId = "frame_glory"),
    achievement("CLAN_GOLD_10000", legacyFallback("Long Uy"), legacyFallback("Quyên góp tổng cộng 10.000 Vàng."), MissionDifficulty.HARD, 10_000, frameId = "frame_dragon_might"),
    achievement("CLAN_GEMS_50", legacyFallback("Đế Vương"), legacyFallback("Quyên góp tổng cộng 50 Gem."), MissionDifficulty.ELITE, 50, frameId = "frame_emperor"),
    achievement("CLAN_QUESTS_10", legacyFallback("Trụ Cột"), legacyFallback("Nhận thưởng 10 nhiệm vụ bang."), MissionDifficulty.HARD, 10, titleId = "title_pillar"),
    achievement("CLAN_LEVEL_10", legacyFallback("Vô Song"), legacyFallback("Thuộc bang khi bang đạt cấp 10."), MissionDifficulty.ELITE, 10, frameId = "frame_peerless")
)

internal fun achievementDefinition(code: String): AchievementDefinition? =
    ACHIEVEMENT_DEFINITIONS.firstOrNull { it.code == code }

internal val AchievementDefinition.isClanAchievement: Boolean
    get() = code.startsWith("CLAN_")

internal val MATCH_ACHIEVEMENT_CODES = setOf(
    "FIRST_WIN", "WINS_10", "WINS_50", "RANKED_WINS_100", "WIN_STREAK_10",
    "PERFECT_MATCH_1", "PERFECT_MATCHES_10", "ACCURACY_90_TEN",
    "RESPONSE_2500_TEN", "RESPONSE_1500_TEN"
)

internal val CHECK_IN_ACHIEVEMENT_CODES = setOf(
    "CHECKIN_STREAK_7", "CHECKIN_STREAK_30", "CHECKINS_50", "CHECKINS_100"
)

internal fun achievementProgress(connection: Connection, userId: UUID): Map<String, Int> {
    val stats = connection.prepareStatement(
        """
        SELECT wins, best_win_streak, experience_points,
               best_daily_check_in_streak, total_daily_check_ins
        FROM player_stats
        WHERE user_id = ?
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, userId)
        statement.executeQuery().use { result ->
            if (!result.next()) AchievementStats() else AchievementStats(
                wins = result.getInt("wins"),
                bestWinStreak = result.getInt("best_win_streak"),
                experiencePoints = result.getInt("experience_points"),
                bestCheckInStreak = result.getInt("best_daily_check_in_streak"),
                totalCheckIns = result.getInt("total_daily_check_ins")
            )
        }
    }
    val rankedWins = connection.countForUser(
        """
        SELECT COUNT(*)
        FROM match_players mp
        JOIN matches m ON m.id = mp.match_id
        WHERE mp.user_id = ? AND mp.outcome = 'WIN' AND m.match_type = 'RANKED'
        """.trimIndent(),
        userId
    )
    val perfectMatches = connection.countForUser(
        """
        SELECT COUNT(*)
        FROM match_players mp
        WHERE mp.user_id = ?
          AND mp.outcome = 'WIN'
          AND EXISTS (
              SELECT 1 FROM match_events accepted
              WHERE accepted.match_id = mp.match_id
                AND accepted.user_id = mp.user_id
                AND accepted.result = 'ACCEPTED'
          )
          AND NOT EXISTS (
              SELECT 1 FROM match_events rejected
              WHERE rejected.match_id = mp.match_id
                AND rejected.user_id = mp.user_id
                AND rejected.result = 'REJECTED'
          )
        """.trimIndent(),
        userId
    )
    val accurateMatches = connection.countForUser(
        """
        SELECT COUNT(*)
        FROM (
            SELECT match_id
            FROM match_events
            WHERE user_id = ?
            GROUP BY match_id
            HAVING COUNT(*) FILTER (WHERE result = 'ACCEPTED') > 0
               AND COUNT(*) FILTER (WHERE result = 'ACCEPTED') * 100 >= COUNT(*) * 90
        ) accurate
        """.trimIndent(),
        userId
    )
    val (response2500Matches, response1500Matches) = connection.prepareStatement(
        """
        WITH eligible_matches AS (
            SELECT match_id
            FROM match_players
            WHERE user_id = ?
        ), accepted_events AS (
            SELECT e.match_id,
                   e.user_id,
                   e.occurred_at,
                   LAG(e.occurred_at, 1, m.started_at)
                       OVER (PARTITION BY e.match_id ORDER BY e.sequence) AS previous_at
            FROM match_events e
            JOIN eligible_matches eligible ON eligible.match_id = e.match_id
            JOIN matches m ON m.id = e.match_id
            WHERE e.result = 'ACCEPTED'
        ), reaction_matches AS (
            SELECT match_id,
                   AVG(EXTRACT(EPOCH FROM (occurred_at - previous_at)) * 1000) AS average_ms
            FROM accepted_events
            WHERE user_id = ?
            GROUP BY match_id
        )
        SELECT COUNT(*) FILTER (WHERE average_ms < 2500) AS response_2500,
               COUNT(*) FILTER (WHERE average_ms < 1500) AS response_1500
        FROM reaction_matches
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, userId)
        statement.setObject(2, userId)
        statement.executeQuery().use { result ->
            result.next()
            result.getInt("response_2500") to result.getInt("response_1500")
        }
    }
    val rawProgress = mapOf(
        "FIRST_WIN" to stats.wins,
        "WINS_10" to stats.wins,
        "WINS_50" to stats.wins,
        "RANKED_WINS_100" to rankedWins,
        "WIN_STREAK_10" to stats.bestWinStreak,
        "PERFECT_MATCH_1" to perfectMatches,
        "PERFECT_MATCHES_10" to perfectMatches,
        "ACCURACY_90_TEN" to accurateMatches,
        "RESPONSE_2500_TEN" to response2500Matches,
        "RESPONSE_1500_TEN" to response1500Matches,
        "CHECKIN_STREAK_7" to stats.bestCheckInStreak,
        "CHECKIN_STREAK_30" to stats.bestCheckInStreak,
        "CHECKINS_50" to stats.totalCheckIns,
        "CHECKINS_100" to stats.totalCheckIns,
        "PLAYER_LEVEL_30" to (stats.experiencePoints / 100 + 1)
    )
    return ACHIEVEMENT_DEFINITIONS.associate { definition ->
        definition.code to (rawProgress[definition.code] ?: 0).coerceIn(0, definition.target)
    }
}

internal fun completedAchievementCodes(
    progress: Map<String, Int>,
    eligibleCodes: Set<String>
): List<String> = ACHIEVEMENT_DEFINITIONS
    .filter { it.code in eligibleCodes && progress.getOrDefault(it.code, 0) >= it.target }
    .map(AchievementDefinition::code)

internal fun grantNewAchievements(
    connection: Connection,
    userId: UUID,
    candidates: Collection<String>,
    occurredAt: Instant,
    matchId: UUID?
): List<String> {
    connection.prepareStatement(
        """
        INSERT INTO player_stats (user_id, updated_at)
        VALUES (?, CURRENT_TIMESTAMP)
        ON CONFLICT (user_id) DO NOTHING
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, userId)
        statement.executeUpdate()
    }
    val unlocked = mutableListOf<String>()
    candidates.distinct().mapNotNull(::achievementDefinition).forEach { definition ->
        val inserted = connection.prepareStatement(
            """
            INSERT INTO user_achievements (user_id, achievement_code, unlocked_at, match_id)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, achievement_code) DO NOTHING
            RETURNING achievement_code
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setString(2, definition.code)
            statement.setTimestamp(3, Timestamp.from(occurredAt))
            statement.setObject(4, matchId)
            statement.executeQuery().use { result -> result.next() }
        }
        if (!inserted) return@forEach

        connection.prepareStatement(
            """
            UPDATE player_stats
            SET experience_points = experience_points + ?,
                gold = gold + ?,
                gems = gems + ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setInt(1, definition.reward.xp)
            statement.setInt(2, definition.reward.gold)
            statement.setInt(3, definition.reward.gems)
            statement.setObject(4, userId)
            check(statement.executeUpdate() == 1) { "Missing player stats for $userId" }
        }
        connection.prepareStatement(
            """
            INSERT INTO wallet_transactions (
                id, user_id, source_type, source_id, gold_delta, gems_delta, xp_delta, created_at
            ) VALUES (?, ?, 'ACHIEVEMENT', ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, source_type, source_id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, userId)
            statement.setString(3, definition.code)
            statement.setInt(4, definition.reward.gold)
            statement.setInt(5, definition.reward.gems)
            statement.setInt(6, definition.reward.xp)
            statement.setTimestamp(7, Timestamp.from(occurredAt))
            check(statement.executeUpdate() == 1) { "Missing wallet reward for ${definition.code}" }
        }
        grantAchievementCosmetic(connection, userId, definition.frameId, "FRAME", occurredAt)
        grantAchievementCosmetic(connection, userId, definition.titleId, "TITLE", occurredAt)
        unlocked += definition.code
    }
    return unlocked
}

private fun grantAchievementCosmetic(
    connection: Connection,
    userId: UUID,
    cosmeticId: String?,
    cosmeticType: String,
    occurredAt: Instant
) {
    if (cosmeticId == null) return
    connection.prepareStatement(
        """
        INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (user_id, cosmetic_id) DO NOTHING
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, userId)
        statement.setString(2, cosmeticId)
        statement.setString(3, cosmeticType)
        statement.setTimestamp(4, Timestamp.from(occurredAt))
        statement.executeUpdate()
    }
}

private fun Connection.countForUser(sql: String, userId: UUID): Int =
    prepareStatement(sql).use { statement ->
        statement.setObject(1, userId)
        statement.executeQuery().use { result ->
            result.next()
            result.getInt(1)
        }
    }

private data class AchievementStats(
    val wins: Int = 0,
    val bestWinStreak: Int = 0,
    val experiencePoints: Int = 0,
    val bestCheckInStreak: Int = 0,
    val totalCheckIns: Int = 0
)

private fun achievement(
    code: String,
    title: String,
    description: String,
    difficulty: MissionDifficulty,
    target: Int,
    frameId: String? = null,
    titleId: String? = null
): AchievementDefinition = AchievementDefinition(
    code = code,
    title = title,
    description = description,
    titleKey = "Achievement${code.toPascalCase()}Title",
    descriptionKey = "Achievement${code.toPascalCase()}Description",
    difficulty = difficulty,
    target = target,
    reward = ACHIEVEMENT_REWARDS.getValue(difficulty),
    frameId = frameId,
    titleId = titleId
)

private fun String.toPascalCase(): String =
    lowercase().split('_').joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
