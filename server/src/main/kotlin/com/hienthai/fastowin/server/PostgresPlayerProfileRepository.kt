package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot
import com.hienthai.fastowin.protocol.GameModeStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.rankedTierFor
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
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.protocol.SeasonSnapshot
import com.hienthai.fastowin.protocol.SeasonRewardReceiptSnapshot
import com.hienthai.fastowin.protocol.SeasonHistoryEntrySnapshot
import com.hienthai.fastowin.protocol.SeasonTierRewardSnapshot
import com.hienthai.fastowin.protocol.STANDARD_SEASON_TIER_REWARDS
import com.hienthai.fastowin.protocol.SHOP_ITEMS
import com.hienthai.fastowin.protocol.seasonCosmeticReward
import com.hienthai.fastowin.protocol.seasonTierRewards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Date
import java.sql.Connection
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
    override suspend fun findAppearance(playerId: String): PlayerAppearance? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT p.avatar_url, COALESCE(s.equipped_frame_id, 'frame_default') AS frame_id
                FROM profiles p
                LEFT JOIN player_stats s ON s.user_id = p.user_id
                WHERE p.user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.executeQuery().use { result ->
                    if (!result.next()) null else PlayerAppearance(
                        avatarId = result.getString("avatar_url"),
                        frameId = result.getString("frame_id")
                    )
                }
            }
        }
    }

    override suspend fun findByPlayerCode(playerCode: String): PlayerProfileSnapshot? = withContext(Dispatchers.IO) {
        val userId = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT user_id FROM profiles WHERE player_code = ?").use { statement ->
                statement.setString(1, playerCode)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString("user_id") else null
                }
            }
        }
        if (userId != null) findByPlayerId(userId) else null
    }

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
                            ELSE s.reaction_time_total_ms / s.reaction_samples END AS average_reaction_ms,
                       cm.clan_id, c.name AS clan_name
                FROM profiles p
                LEFT JOIN player_stats s ON s.user_id = p.user_id
                LEFT JOIN clan_members cm ON cm.user_id = p.user_id
                LEFT JOIN clans c ON c.id = cm.clan_id
                WHERE p.user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (!result.next()) return@withContext null
                    PlayerProfileSnapshot(
                        userId = playerId,
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
                        ),
                        clanId = result.getString("clan_id"),
                        clanName = result.getString("clan_name")
                    )
                }
            }

            val recentMatches = connection.prepareStatement(
                """
                SELECT m.id, m.room_name, m.game_mode, m.match_type, m.ended_at,
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
                                eloChange = result.getInt("elo_change"),
                                matchType = MatchType.valueOf(result.getString("match_type"))
                            )
                        )
                    }
                }
            }
            val modeStatistics = connection.prepareStatement(
                """
                SELECT m.game_mode,
                       COUNT(*) AS total_matches,
                       COUNT(*) FILTER (WHERE mine.outcome = 'WIN') AS wins,
                       COUNT(*) FILTER (WHERE mine.outcome = 'LOSS') AS losses,
                       COUNT(*) FILTER (WHERE mine.outcome = 'DRAW') AS draws,
                       COALESCE(MAX(mine.score), 0) AS highest_score,
                       COALESCE(ROUND(AVG(mine.score)), 0) AS average_score
                FROM match_players mine
                JOIN matches m ON m.id = mine.match_id
                WHERE mine.user_id = ?
                GROUP BY m.game_mode
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(
                            GameModeStatisticsSnapshot(
                                gameMode = ProtocolGameMode.valueOf(result.getString("game_mode")),
                                totalMatches = result.getInt("total_matches"),
                                wins = result.getInt("wins"),
                                losses = result.getInt("losses"),
                                draws = result.getInt("draws"),
                                highestScore = result.getInt("highest_score"),
                                averageScore = result.getInt("average_score")
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
                       COALESCE(gold, 0) AS gold,
                       COALESCE(gems, 0) AS gems,
                       COALESCE(equipped_frame_id, 'frame_default') AS equipped_frame_id,
                       COALESCE(equipped_title_id, 'title_rookie') AS equipped_title_id,
                       COALESCE(equipped_card_back_id, 'card_back_default') AS equipped_card_back_id,
                       COALESCE(equipped_board_skin_id, 'board_skin_default') AS equipped_board_skin_id,
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
                            gold = result.getInt("gold"),
                            gems = result.getInt("gems"),
                            equippedFrameId = result.getString("equipped_frame_id"),
                            equippedTitleId = result.getString("equipped_title_id"),
                            equippedCardBackId = result.getString("equipped_card_back_id"),
                            equippedBoardSkinId = result.getString("equipped_board_skin_id"),
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
                SELECT mission_code, progress, target, reward_xp, reward_gold, reward_gems, claimed_at
                FROM user_missions
                WHERE user_id = ? AND period_start IN (?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setDate(2, Date.valueOf(today))
                statement.setDate(3, Date.valueOf(missionPeriodStart(
                    MISSION_DEFINITIONS.first { it.period == MissionPeriod.WEEKLY },
                    today
                )))
                statement.executeQuery().use { result ->
                    buildMap {
                        while (result.next()) {
                            put(
                                result.getString("mission_code"),
                                StoredMission(
                                    progress = result.getInt("progress"),
                                    target = result.getInt("target"),
                                    rewardXp = result.getInt("reward_xp"),
                                    rewardGold = result.getInt("reward_gold"),
                                    rewardGems = result.getInt("reward_gems"),
                                    rewardClaimed = result.getTimestamp("claimed_at") != null
                                )
                            )
                        }
                    }
                }
            }

            val ownedCosmetics = connection.prepareStatement(
                """
                SELECT cosmetic_id FROM player_cosmetics WHERE user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildSet {
                        while (result.next()) add(result.getString("cosmetic_id"))
                    }
                }
            }
            fun mission(definition: MissionDefinition): MissionSnapshot {
                val stored = storedMissions[definition.code]
                val progress = (stored?.progress ?: 0).coerceAtMost(definition.target)
                return MissionSnapshot(
                    code = definition.code,
                    title = definition.title,
                    progress = progress,
                    target = definition.target,
                    completed = progress >= definition.target,
                    rewardXp = stored?.rewardXp?.takeIf { it > 0 } ?: definition.rewardXp,
                    rewardGold = stored?.rewardGold?.takeIf { it > 0 } ?: definition.rewardGold,
                    rewardGems = stored?.rewardGems?.takeIf { it > 0 } ?: definition.rewardGems,
                    rewardClaimed = stored?.rewardClaimed == true,
                    difficulty = definition.difficulty
                )
            }
            val season = connection.prepareStatement(
                """
                SELECT s.season_number, s.name, s.ends_at, s.reward_description,
                       COALESCE(sr.rating, ?) AS rating,
                       COALESCE(sr.placement_matches, 0) AS placement_matches,
                       COALESCE(sr.peak_rating, ?) AS peak_rating
                FROM seasons s
                LEFT JOIN season_ratings sr ON sr.season_id = s.id AND sr.user_id = ?
                WHERE CURRENT_TIMESTAMP >= s.starts_at AND CURRENT_TIMESTAMP < s.ends_at
                ORDER BY s.starts_at DESC
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.setInt(1, SEASON_INITIAL_RATING)
                statement.setInt(2, SEASON_INITIAL_RATING)
                statement.setObject(3, userId)
                statement.executeQuery().use { result ->
                    if (!result.next()) null else {
                        val rating = result.getInt("rating")
                        val placementMatches = result.getInt("placement_matches")
                        val seasonName = result.getString("name")
                        SeasonSnapshot(
                            name = seasonName,
                            tier = if (placementMatches < PLACEMENT_MATCHES_REQUIRED) {
                                "Đang phân hạng"
                            } else {
                                ratingTier(rating)
                            },
                            rating = rating,
                            endsAtEpochMillis = result.getTimestamp("ends_at").time,
                            rewardDescription = result.getString("reward_description"),
                            placementMatchesPlayed = placementMatches,
                            peakRating = result.getInt("peak_rating"),
                            tierRewards = seasonTierRewards(result.getInt("season_number"), seasonName)
                        )
                    }
                }
            }
            val latestSeasonReward = connection.prepareStatement(
                """
                SELECT s.season_number, s.name, src.tier, src.peak_rating, src.reward_gold,
                       src.reward_gems, src.reward_cosmetic_id, src.reward_cosmetic_type,
                       src.awarded_at, src.viewed_at
                FROM season_reward_claims src
                JOIN seasons s ON s.id = src.season_id
                WHERE src.user_id = ?
                ORDER BY src.awarded_at DESC
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (!result.next()) null else {
                        val seasonName = result.getString("name")
                        val tier = com.hienthai.fastowin.protocol.RankedTier.valueOf(result.getString("tier"))
                        val cosmetic = seasonCosmeticReward(result.getInt("season_number"), seasonName, tier)
                        SeasonRewardReceiptSnapshot(
                            seasonNumber = result.getInt("season_number"),
                            seasonName = seasonName,
                            tier = tier,
                            peakRating = result.getInt("peak_rating"),
                            gold = result.getInt("reward_gold"),
                            gems = result.getInt("reward_gems"),
                            awardedAtEpochMillis = result.getTimestamp("awarded_at").time,
                            cosmetic = cosmetic.copy(
                                id = result.getString("reward_cosmetic_id"),
                                type = CosmeticType.valueOf(result.getString("reward_cosmetic_type"))
                            ),
                            acknowledged = result.getTimestamp("viewed_at") != null
                        )
                    }
                }
            }
            val seasonHistory = connection.prepareStatement(
                """
                SELECT s.season_number, s.name, s.ends_at,
                       sr.rating, sr.peak_rating, sr.matches_played, sr.placement_matches,
                       sla.final_rank,
                       src.tier, src.reward_gold, src.reward_gems,
                       src.reward_cosmetic_id, src.reward_cosmetic_type,
                       src.awarded_at, src.viewed_at
                FROM season_ratings sr
                JOIN seasons s ON s.id = sr.season_id
                LEFT JOIN season_leaderboard_archive sla
                  ON sla.season_id = sr.season_id AND sla.user_id = sr.user_id
                LEFT JOIN season_reward_claims src
                  ON src.season_id = sr.season_id AND src.user_id = sr.user_id
                WHERE sr.user_id = ?
                  AND s.ends_at <= CURRENT_TIMESTAMP
                ORDER BY s.season_number DESC
                LIMIT 50
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            val seasonNumber = result.getInt("season_number")
                            val seasonName = result.getString("name")
                            val finalRank = result.getInt("final_rank").takeUnless { result.wasNull() }
                            val tierName = result.getString("tier")
                            val reward = tierName?.let {
                                val tier = com.hienthai.fastowin.protocol.RankedTier.valueOf(it)
                                val cosmetic = seasonCosmeticReward(seasonNumber, seasonName, tier).copy(
                                    id = result.getString("reward_cosmetic_id"),
                                    type = CosmeticType.valueOf(result.getString("reward_cosmetic_type"))
                                )
                                SeasonRewardReceiptSnapshot(
                                    seasonNumber = seasonNumber,
                                    seasonName = seasonName,
                                    tier = tier,
                                    peakRating = result.getInt("peak_rating"),
                                    gold = result.getInt("reward_gold"),
                                    gems = result.getInt("reward_gems"),
                                    awardedAtEpochMillis = result.getTimestamp("awarded_at").time,
                                    cosmetic = cosmetic,
                                    acknowledged = result.getTimestamp("viewed_at") != null
                                )
                            }
                            add(
                                SeasonHistoryEntrySnapshot(
                                    seasonNumber = seasonNumber,
                                    seasonName = seasonName,
                                    endedAtEpochMillis = result.getTimestamp("ends_at").time,
                                    finalRating = result.getInt("rating"),
                                    peakRating = result.getInt("peak_rating"),
                                    finalRank = finalRank,
                                    matchesPlayed = result.getInt("matches_played"),
                                    placementMatchesPlayed = result.getInt("placement_matches"),
                                    placementMatchesRequired = PLACEMENT_MATCHES_REQUIRED,
                                    reward = reward
                                )
                            )
                        }
                    }
                }
            }
            val seasonCosmetics = connection.prepareStatement(
                """
                SELECT s.season_number, s.name, src.tier, src.reward_cosmetic_id,
                       src.reward_cosmetic_type
                FROM season_reward_claims src
                JOIN seasons s ON s.id = src.season_id
                WHERE src.user_id = ?
                ORDER BY src.awarded_at DESC
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            val cosmeticId = result.getString("reward_cosmetic_id")
                            if (cosmeticId !in ownedCosmetics) continue
                            val type = CosmeticType.valueOf(result.getString("reward_cosmetic_type"))
                            val tier = com.hienthai.fastowin.protocol.RankedTier.valueOf(result.getString("tier"))
                            val reward = seasonCosmeticReward(
                                result.getInt("season_number"),
                                result.getString("name"),
                                tier
                            )
                            add(OwnedSeasonCosmetic(cosmeticId, reward.name, type))
                        }
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
            ).toMutableSet().apply {
                addAll(seasonCosmetics.filter { it.type == CosmeticType.FRAME }.map { it.id })
            }
            val unlockedTitles = unlockedTitleIds(
                base.statistics.wins,
                achievementCodes,
                progressionRow.bestDailyCheckInStreak
            ).toMutableSet().apply {
                addAll(seasonCosmetics.filter { it.type == CosmeticType.TITLE }.map { it.id })
            }
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
                cosmetic("frame_silver", "Khung Bạc", CosmeticType.FRAME, "frame_silver" in unlockedFrames, equippedFrameId),
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
            ) + seasonCosmetics.map { owned ->
                cosmetic(
                    owned.id,
                    owned.name,
                    owned.type,
                    unlocked = true,
                    equippedId = if (owned.type == CosmeticType.FRAME) equippedFrameId else equippedTitleId
                )
            } + SHOP_ITEMS.filter { it.id in ownedCosmetics }.map { owned ->
                cosmetic(
                    id = owned.id,
                    name = owned.name,
                    type = owned.type,
                    unlocked = true,
                    equippedId = when (owned.type) {
                        CosmeticType.CARD_BACK -> progressionRow.equippedCardBackId
                        CosmeticType.BOARD_SKIN -> progressionRow.equippedBoardSkinId
                        else -> null
                    }
                )
            }
            base.copy(
                recentMatches = recentMatches,
                achievements = achievements,
                modeStatistics = modeStatistics,
                progression = PlayerProgressionSnapshot(
                    level = level,
                    experiencePoints = experiencePoints,
                    gold = progressionRow.gold,
                    gems = progressionRow.gems,
                    currentLevelExperience = experiencePoints % EXPERIENCE_PER_LEVEL,
                    nextLevelExperience = EXPERIENCE_PER_LEVEL,
                    dailyMissions = MISSION_DEFINITIONS.filter { it.period == MissionPeriod.DAILY }.map(::mission),
                    weeklyMissions = MISSION_DEFINITIONS.filter { it.period == MissionPeriod.WEEKLY }.map(::mission),
                    dailyCheckIn = DailyCheckInSnapshot(
                        claimedToday = checkInDecision.claimedToday,
                        cycleDay = checkInDecision.cycleDay,
                        todayRewardXp = checkInDecision.rewardXp,
                        todayRewardGold = checkInDecision.rewardGold,
                        todayRewardGems = checkInDecision.rewardGems,
                        nextRewardXp = nextDailyCheckInXpReward(checkInDecision.cycleDay),
                        nextRewardGold = nextDailyCheckInGoldReward(checkInDecision.cycleDay),
                        nextRewardGems = nextDailyCheckInGemReward(checkInDecision.cycleDay),
                        currentStreak = activeCheckInStreak,
                        bestStreak = progressionRow.bestDailyCheckInStreak,
                        totalCheckIns = progressionRow.totalDailyCheckIns,
                        lastCheckInDate = progressionRow.lastDailyCheckInDate?.toString(),
                        todayDate = today.toString(),
                        historyDates = recentCheckInDates
                    ),
                    cosmetics = cosmetics,
                    season = season,
                    latestSeasonReward = latestSeasonReward,
                    seasonHistory = seasonHistory
                )
            )
        }
    }

    override suspend fun settleCompletedSeasonRewards(playerId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = UUID.fromString(playerId)
                val userExists = connection.prepareStatement("SELECT 1 FROM users WHERE id = ? FOR UPDATE").use { statement ->
                    statement.setObject(1, userId)
                    statement.executeQuery().use { it.next() }
                }
                if (!userExists) {
                    connection.rollback()
                    return@withContext false
                }
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

                val completedSeasons = connection.prepareStatement(
                    """
                    SELECT s.id, s.season_number, s.name, sr.peak_rating
                    FROM season_ratings sr
                    JOIN seasons s ON s.id = sr.season_id
                    WHERE sr.user_id = ?
                      AND s.ends_at <= CURRENT_TIMESTAMP
                      AND sr.placement_matches >= ?
                      AND NOT EXISTS (
                          SELECT 1 FROM season_reward_claims src
                          WHERE src.season_id = s.id AND src.user_id = sr.user_id
                      )
                    ORDER BY s.ends_at ASC
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.setInt(2, PLACEMENT_MATCHES_REQUIRED)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) add(
                                CompletedSeasonReward(
                                    seasonId = result.getObject("id", UUID::class.java),
                                    seasonNumber = result.getInt("season_number"),
                                    seasonName = result.getString("name"),
                                    peakRating = result.getInt("peak_rating")
                                )
                            )
                        }
                    }
                }

                var totalGold = 0
                var totalGems = 0
                completedSeasons.forEach { completed ->
                    val reward = seasonRewardForPeakRating(completed.peakRating)
                    val cosmetic = seasonCosmeticReward(
                        completed.seasonNumber,
                        completed.seasonName,
                        reward.tier
                    )
                    val inserted = connection.prepareStatement(
                        """
                        INSERT INTO season_reward_claims (
                            season_id, user_id, tier, peak_rating, reward_gold, reward_gems,
                            reward_cosmetic_id, reward_cosmetic_type
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (season_id, user_id) DO NOTHING
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, completed.seasonId)
                        statement.setObject(2, userId)
                        statement.setString(3, reward.tier.name)
                        statement.setInt(4, completed.peakRating)
                        statement.setInt(5, reward.gold)
                        statement.setInt(6, reward.gems)
                        statement.setString(7, cosmetic.id)
                        statement.setString(8, cosmetic.type.name)
                        statement.executeUpdate() == 1
                    }
                    if (inserted) {
                        connection.prepareStatement(
                            """
                            INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
                            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                            ON CONFLICT (user_id, cosmetic_id) DO NOTHING
                            """.trimIndent()
                        ).use { statement ->
                            statement.setObject(1, userId)
                            statement.setString(2, cosmetic.id)
                            statement.setString(3, cosmetic.type.name)
                            statement.executeUpdate()
                        }
                        insertWalletTransaction(
                            connection = connection,
                            userId = userId,
                            sourceType = "SEASON_REWARD",
                            sourceId = completed.seasonId.toString(),
                            gold = reward.gold,
                            gems = reward.gems,
                            xp = 0
                        )
                        totalGold += reward.gold
                        totalGems += reward.gems
                    }
                }

                if (totalGold > 0 || totalGems > 0) {
                    connection.prepareStatement(
                        """
                        UPDATE player_stats
                        SET gold = gold + ?, gems = gems + ?, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setInt(1, totalGold)
                        statement.setInt(2, totalGems)
                        statement.setObject(3, userId)
                        check(statement.executeUpdate() == 1)
                    }
                }
                connection.commit()
                totalGold > 0 || totalGems > 0
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun acknowledgeSeasonReward(
        playerId: String,
        seasonNumber: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (seasonNumber <= 0) return@withContext false
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE season_reward_claims src
                SET viewed_at = COALESCE(src.viewed_at, CURRENT_TIMESTAMP)
                FROM seasons s
                WHERE src.season_id = s.id
                  AND src.user_id = ?
                  AND s.season_number = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.setInt(2, seasonNumber)
                statement.executeUpdate() == 1
            }
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
                                gold = gold + ?,
                                gems = gems + ?,
                                current_daily_check_in_streak = ?,
                                best_daily_check_in_streak = GREATEST(best_daily_check_in_streak, ?),
                                total_daily_check_ins = total_daily_check_ins + 1,
                                last_daily_check_in_date = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ?
                            """.trimIndent()
                        ).use { statement ->
                            statement.setInt(1, decision.rewardXp)
                            statement.setInt(2, decision.rewardGold)
                            statement.setInt(3, decision.rewardGems)
                            statement.setInt(4, decision.resultingStreak)
                            statement.setInt(5, decision.resultingStreak)
                            statement.setDate(6, Date.valueOf(today))
                            statement.setObject(7, userId)
                            check(statement.executeUpdate() == 1)
                        }
                        connection.prepareStatement(
                            """
                            INSERT INTO daily_check_ins (
                                user_id, check_in_date, cycle_day, reward_xp, reward_gold,
                                reward_gems, streak_after
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        ).use { statement ->
                            statement.setObject(1, userId)
                            statement.setDate(2, Date.valueOf(today))
                            statement.setInt(3, decision.cycleDay)
                            statement.setInt(4, decision.rewardXp)
                            statement.setInt(5, decision.rewardGold)
                            statement.setInt(6, decision.rewardGems)
                            statement.setInt(7, decision.resultingStreak)
                            statement.executeUpdate()
                        }
                        insertWalletTransaction(
                            connection = connection,
                            userId = userId,
                            sourceType = "DAILY_CHECK_IN",
                            sourceId = today.toString(),
                            gold = decision.rewardGold,
                            gems = decision.rewardGems,
                            xp = decision.rewardXp
                        )
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
                        DailyCheckInClaimResult(
                            claimed = true,
                            rewardXp = decision.rewardXp,
                            rewardGold = decision.rewardGold,
                            rewardGems = decision.rewardGems
                        )
                    }
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                }
            }
        }

    override suspend fun claimMissionReward(
        playerId: String,
        missionCode: String
    ): MissionRewardClaimResult? = withContext(Dispatchers.IO) {
        val definition = missionDefinition(missionCode)
            ?: return@withContext MissionRewardClaimResult(MissionRewardClaimStatus.INVALID_MISSION)
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = UUID.fromString(playerId)
                val periodStart = Date.valueOf(missionPeriodStart(definition, currentCheckInDate()))
                val stored = connection.prepareStatement(
                    """
                    SELECT progress, target, reward_xp, reward_gold, reward_gems, claimed_at
                    FROM user_missions
                    WHERE user_id = ? AND mission_code = ? AND period_start = ?
                    FOR UPDATE
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.setString(2, definition.code)
                    statement.setDate(3, periodStart)
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else StoredMission(
                            progress = result.getInt("progress"),
                            target = result.getInt("target"),
                            rewardXp = result.getInt("reward_xp"),
                            rewardGold = result.getInt("reward_gold"),
                            rewardGems = result.getInt("reward_gems"),
                            rewardClaimed = result.getTimestamp("claimed_at") != null
                        )
                    }
                }
                val outcome = when {
                    stored == null || stored.progress < stored.target ->
                        MissionRewardClaimResult(MissionRewardClaimStatus.NOT_COMPLETED)
                    stored.rewardClaimed ->
                        MissionRewardClaimResult(MissionRewardClaimStatus.ALREADY_CLAIMED)
                    else -> {
                        val rewardXp = stored.rewardXp.takeIf { it > 0 } ?: definition.rewardXp
                        val rewardGold = stored.rewardGold.takeIf { it > 0 } ?: definition.rewardGold
                        val rewardGems = stored.rewardGems.takeIf { it > 0 } ?: definition.rewardGems
                        connection.prepareStatement(
                            """
                            UPDATE user_missions
                            SET claimed_at = CURRENT_TIMESTAMP
                            WHERE user_id = ? AND mission_code = ? AND period_start = ?
                              AND claimed_at IS NULL AND progress >= target
                            """.trimIndent()
                        ).use { statement ->
                            statement.setObject(1, userId)
                            statement.setString(2, definition.code)
                            statement.setDate(3, periodStart)
                            check(statement.executeUpdate() == 1)
                        }
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
                            statement.setInt(1, rewardXp)
                            statement.setInt(2, rewardGold)
                            statement.setInt(3, rewardGems)
                            statement.setObject(4, userId)
                            check(statement.executeUpdate() == 1)
                        }
                        insertWalletTransaction(
                            connection = connection,
                            userId = userId,
                            sourceType = "MISSION",
                            sourceId = "${definition.code}:$periodStart",
                            gold = rewardGold,
                            gems = rewardGems,
                            xp = rewardXp
                        )
                        MissionRewardClaimResult(
                            MissionRewardClaimStatus.CLAIMED,
                            rewardXp,
                            rewardGold,
                            rewardGems
                        )
                    }
                }
                connection.commit()
                outcome
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun updateFcmToken(playerId: String, token: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE users SET fcm_token = ? WHERE id = ?").use { statement ->
                statement.setString(1, token)
                statement.setObject(2, UUID.fromString(playerId))
                statement.executeUpdate() > 0
            }
        }
    }

    override suspend fun findFcmToken(playerId: String): String? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT fcm_token FROM users WHERE id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString("fcm_token") else null
                }
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
    override suspend fun updateAvatarData(playerId: String, base64: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE profiles SET avatar_data = ? WHERE user_id = ?").use { statement ->
                statement.setString(1, base64)
                statement.setObject(2, UUID.fromString(playerId))
                statement.executeUpdate() > 0
            }
        }
    }

    override suspend fun getAvatarData(playerId: String): String? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT avatar_data FROM profiles WHERE user_id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString("avatar_data") else null
                }
            }
        }
    }


    private fun ratingTier(rating: Int): String = rankedTierFor(rating).displayName

    private fun currentCheckInDate(): LocalDate = LocalDate.now(clock)

    private companion object {
        const val EXPERIENCE_PER_LEVEL = 100
        const val PLACEMENT_MATCHES_REQUIRED = 5
        const val SEASON_INITIAL_RATING = 1_000
    }
    override suspend fun loadWalletHistory(
        playerId: String,
        limit: Int
    ): List<WalletTransactionSnapshot> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, source_type, source_id, gold_delta, gems_delta, xp_delta, created_at
                FROM wallet_transactions
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.setInt(2, limit.coerceIn(1, 100))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                WalletTransactionSnapshot(
                                    id = result.getObject("id", UUID::class.java).toString(),
                                    sourceType = result.getString("source_type"),
                                    sourceId = result.getString("source_id"),
                                    goldDelta = result.getInt("gold_delta"),
                                    gemsDelta = result.getInt("gems_delta"),
                                    xpDelta = result.getInt("xp_delta"),
                                    createdAtEpochMillis = result.getTimestamp("created_at").toInstant().toEpochMilli()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun applyWalletTransaction(
        playerId: String,
        sourceType: String,
        sourceId: String,
        goldDelta: Int,
        gemsDelta: Int,
        xpDelta: Int
    ): WalletMutationStatus = withContext(Dispatchers.IO) {
        require(goldDelta != 0 || gemsDelta != 0 || xpDelta != 0) { "Wallet transaction must change a balance" }
        require(sourceType.length in 1..32 && sourceId.length in 1..128) { "Invalid wallet transaction source" }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = UUID.fromString(playerId)
                val balances = connection.prepareStatement(
                    "SELECT gold, gems, experience_points FROM player_stats WHERE user_id = ? FOR UPDATE"
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else Triple(
                            result.getInt("gold"),
                            result.getInt("gems"),
                            result.getInt("experience_points")
                        )
                    }
                } ?: run {
                    connection.rollback()
                    return@withContext WalletMutationStatus.PLAYER_NOT_FOUND
                }
                if (
                    balances.first + goldDelta < 0 ||
                    balances.second + gemsDelta < 0 ||
                    balances.third + xpDelta < 0
                ) {
                    connection.rollback()
                    return@withContext WalletMutationStatus.INSUFFICIENT_FUNDS
                }
                val inserted = connection.prepareStatement(
                    """
                    INSERT INTO wallet_transactions (
                        id, user_id, source_type, source_id, gold_delta, gems_delta, xp_delta
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, source_type, source_id) DO NOTHING
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, userId)
                    statement.setString(3, sourceType)
                    statement.setString(4, sourceId)
                    statement.setInt(5, goldDelta)
                    statement.setInt(6, gemsDelta)
                    statement.setInt(7, xpDelta)
                    statement.executeUpdate()
                }
                if (inserted == 0) {
                    connection.rollback()
                    return@withContext WalletMutationStatus.DUPLICATE
                }
                connection.prepareStatement(
                    """
                    UPDATE player_stats
                    SET gold = gold + ?, gems = gems + ?, experience_points = experience_points + ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setInt(1, goldDelta)
                    statement.setInt(2, gemsDelta)
                    statement.setInt(3, xpDelta)
                    statement.setObject(4, userId)
                    check(statement.executeUpdate() == 1)
                }
                connection.commit()
                WalletMutationStatus.APPLIED
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun grantStorePurchase(
        playerId: String,
        store: String,
        productId: String,
        transactionId: String,
        gems: Int
    ): StorePurchaseGrantStatus = withContext(Dispatchers.IO) {
        require(store.length in 1..16 && productId.length in 1..128 && transactionId.length in 1..256)
        require(gems > 0)
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = UUID.fromString(playerId)
                val userExists = connection.prepareStatement("SELECT 1 FROM users WHERE id = ? FOR UPDATE").use { statement ->
                    statement.setObject(1, userId)
                    statement.executeQuery().use { it.next() }
                }
                if (!userExists) {
                    connection.rollback()
                    return@withContext StorePurchaseGrantStatus.PLAYER_NOT_FOUND
                }
                val insertedPurchase = connection.prepareStatement(
                    """
                    INSERT INTO store_purchases (
                        id, user_id, store, product_id, transaction_id, gems_granted
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (store, transaction_id) DO NOTHING
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, userId)
                    statement.setString(3, store)
                    statement.setString(4, productId)
                    statement.setString(5, transactionId)
                    statement.setInt(6, gems)
                    statement.executeUpdate()
                }
                if (insertedPurchase == 0) {
                    val belongsToPlayer = connection.prepareStatement(
                        """
                        SELECT user_id, product_id, gems_granted
                        FROM store_purchases
                        WHERE store = ? AND transaction_id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setString(1, store)
                        statement.setString(2, transactionId)
                        statement.executeQuery().use { result ->
                            result.next() &&
                                result.getObject("user_id", UUID::class.java) == userId &&
                                result.getString("product_id") == productId &&
                                result.getInt("gems_granted") == gems
                        }
                    }
                    connection.rollback()
                    return@withContext if (belongsToPlayer) {
                        StorePurchaseGrantStatus.ALREADY_GRANTED
                    } else {
                        StorePurchaseGrantStatus.TOKEN_ALREADY_USED
                    }
                }
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
                connection.prepareStatement(
                    """
                    INSERT INTO wallet_transactions (
                        id, user_id, source_type, source_id, gems_delta
                    ) VALUES (?, ?, 'STORE_PURCHASE', ?, ?)
                    ON CONFLICT (user_id, source_type, source_id) DO NOTHING
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, userId)
                    statement.setString(3, "$store:$transactionId")
                    statement.setInt(4, gems)
                    check(statement.executeUpdate() == 1)
                }
                connection.prepareStatement(
                    """
                    UPDATE player_stats
                    SET gems = gems + ?, updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setInt(1, gems)
                    statement.setObject(2, userId)
                    check(statement.executeUpdate() == 1)
                }
                connection.commit()
                StorePurchaseGrantStatus.GRANTED
            } catch (error: Throwable) {
                connection.rollback()
                System.err.println("Could not grant store purchase: ${error.message}")
                StorePurchaseGrantStatus.FAILED
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun buyCosmetic(playerId: String, cosmeticId: String, cosmeticType: String, price: Int): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updateGold = connection.prepareStatement("UPDATE player_stats SET gold = gold - ? WHERE user_id = ? AND gold >= ?")
                updateGold.setInt(1, price)
                updateGold.setObject(2, java.util.UUID.fromString(playerId))
                updateGold.setInt(3, price)
                if (updateGold.executeUpdate() == 0) {
                    connection.rollback()
                    return@withContext false
                }

                val insertCosmetic = connection.prepareStatement(
                    """
                    INSERT INTO player_cosmetics (user_id, cosmetic_type, cosmetic_id, acquired_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (user_id, cosmetic_id) DO NOTHING
                    """.trimIndent()
                )
                insertCosmetic.setObject(1, java.util.UUID.fromString(playerId))
                insertCosmetic.setString(2, cosmeticType)
                insertCosmetic.setString(3, cosmeticId)
                insertCosmetic.setTimestamp(4, java.sql.Timestamp.from(java.time.Instant.now()))
                if (insertCosmetic.executeUpdate() == 0) {
                    connection.rollback()
                    return@withContext false
                }

                insertWalletTransaction(
                    connection = connection,
                    userId = UUID.fromString(playerId),
                    sourceType = "COSMETIC_PURCHASE",
                    sourceId = cosmeticId,
                    gold = -price,
                    gems = 0,
                    xp = 0
                )

                connection.commit()
                true
            } catch (e: Exception) {
                connection.rollback()
                false
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun equipCosmetic(playerId: String, cosmeticId: String, cosmeticType: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val column = when (cosmeticType) {
                "FRAME" -> "equipped_frame_id"
                "TITLE" -> "equipped_title_id"
                "AVATAR" -> "equipped_avatar_id"
                "CARD_BACK" -> "equipped_card_back_id"
                "BOARD_SKIN" -> "equipped_board_skin_id"
                else -> return@withContext false
            }
            connection.prepareStatement("UPDATE player_stats SET $column = ? WHERE user_id = ?").use { statement ->
                statement.setString(1, cosmeticId)
                statement.setObject(2, java.util.UUID.fromString(playerId))
                statement.executeUpdate() > 0
            }
        }
    }

    private fun insertWalletTransaction(
        connection: Connection,
        userId: UUID,
        sourceType: String,
        sourceId: String,
        gold: Int,
        gems: Int,
        xp: Int
    ) {
        if (gold == 0 && gems == 0 && xp == 0) return
        connection.prepareStatement(
            """
            INSERT INTO wallet_transactions (
                id, user_id, source_type, source_id, gold_delta, gems_delta, xp_delta
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, userId)
            statement.setString(3, sourceType)
            statement.setString(4, sourceId)
            statement.setInt(5, gold)
            statement.setInt(6, gems)
            statement.setInt(7, xp)
            statement.executeUpdate()
        }
    }

}

private data class ProgressionRow(
    val experiencePoints: Int = 0,
    val gold: Int = 0,
    val gems: Int = 0,
    val equippedFrameId: String = "frame_default",
    val equippedTitleId: String = "title_rookie",
    val equippedCardBackId: String = "card_back_default",
    val equippedBoardSkinId: String = "board_skin_default",
    val currentDailyCheckInStreak: Int = 0,
    val bestDailyCheckInStreak: Int = 0,
    val totalDailyCheckIns: Int = 0,
    val lastDailyCheckInDate: LocalDate? = null
)

private data class CompletedSeasonReward(
    val seasonId: UUID,
    val seasonNumber: Int,
    val seasonName: String,
    val peakRating: Int
)

private data class OwnedSeasonCosmetic(
    val id: String,
    val name: String,
    val type: CosmeticType
)

internal fun seasonRewardForPeakRating(peakRating: Int): SeasonTierRewardSnapshot =
    STANDARD_SEASON_TIER_REWARDS.lastOrNull { peakRating >= it.tier.minimumRating }
        ?: STANDARD_SEASON_TIER_REWARDS.first()

private data class StoredMission(
    val progress: Int,
    val target: Int,
    val rewardXp: Int,
    val rewardGold: Int,
    val rewardGems: Int,
    val rewardClaimed: Boolean
)

internal fun unlockedFrameIds(
    level: Int,
    achievementCodes: Set<String>,
    totalDailyCheckIns: Int = 0
): Set<String> = buildSet {
    add("frame_default")
    if (level >= 3) add("frame_bronze")
    if (level >= 6) add("frame_silver")
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
