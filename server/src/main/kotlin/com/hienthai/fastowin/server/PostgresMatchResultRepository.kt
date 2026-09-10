package com.hienthai.fastowin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hienthai.fastowin.protocol.MatchType
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.math.pow

class PostgresMatchResultRepository(
    private val dataSource: DataSource
) : MatchResultRepository {
    override suspend fun save(match: CompletedMatch): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                if (insertMatch(connection, match)) {
                    insertPlayers(connection, match)
                    insertEvents(connection, match)
                    if (match.matchType == MatchType.RANKED) updateEloRatings(connection, match)
                    updateStats(connection, match)
                    unlockAchievements(connection, match)
                    updateMissions(connection, match)
                    if (match.matchType == MatchType.RANKED) updateClanWarsAndQuests(connection, match)
                }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    private fun insertMatch(connection: Connection, match: CompletedMatch): Boolean =
        connection.prepareStatement(
            """
            INSERT INTO matches (id, room_name, game_mode, started_at, ended_at, winner_user_id, match_type)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.fromString(match.matchId))
            statement.setString(2, match.roomName)
            statement.setString(3, match.gameMode.name)
            statement.setTimestamp(4, match.startedAtMillis.toTimestamp())
            statement.setTimestamp(5, match.endedAtMillis.toTimestamp())
            statement.setObject(6, match.winnerPlayerId?.let(UUID::fromString))
            statement.setString(7, match.matchType.name)
            statement.executeUpdate() == 1
        }

    private fun insertPlayers(connection: Connection, match: CompletedMatch) {
        connection.prepareStatement(
            """
            INSERT INTO match_players (match_id, user_id, display_name, score, outcome)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            match.players.forEach { player ->
                statement.setObject(1, UUID.fromString(match.matchId))
                statement.setObject(2, UUID.fromString(player.playerId))
                statement.setString(3, player.displayName)
                statement.setInt(4, player.score)
                statement.setString(5, player.outcome.name)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun insertEvents(connection: Connection, match: CompletedMatch) {
        if (match.events.isEmpty()) return
        connection.prepareStatement(
            """
            INSERT INTO match_events (
                match_id, user_id, request_id, number, expected_number, result, occurred_at, sequence
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            match.events.forEach { event ->
                statement.setObject(1, UUID.fromString(match.matchId))
                statement.setObject(2, UUID.fromString(event.playerId))
                statement.setString(3, event.requestId)
                statement.setInt(4, event.number)
                statement.setInt(5, event.expectedNumber)
                statement.setString(6, event.result.name)
                statement.setTimestamp(7, event.occurredAtMillis.toTimestamp())
                statement.setInt(8, event.sequence)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun updateStats(connection: Connection, match: CompletedMatch) {
        val metricsByPlayer = calculateSelectionMetrics(match)
        val rewardsByPlayer = match.players.associate { player ->
            player.playerId to RewardEconomy.matchReward(
                matchType = match.matchType,
                outcome = player.outcome,
                intentionalLeave = player.intentionalLeave
            )
        }
        connection.prepareStatement(
            """
            INSERT INTO player_stats (
                user_id, total_matches, wins, losses, draws, highest_score,
                current_win_streak, best_win_streak, correct_selections, wrong_selections,
                reaction_time_total_ms, reaction_samples, experience_points, gold, updated_at
            )
            VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                total_matches = player_stats.total_matches + 1,
                wins = player_stats.wins + EXCLUDED.wins,
                losses = player_stats.losses + EXCLUDED.losses,
                draws = player_stats.draws + EXCLUDED.draws,
                highest_score = GREATEST(player_stats.highest_score, EXCLUDED.highest_score),
                current_win_streak = CASE
                    WHEN EXCLUDED.wins = 1 THEN player_stats.current_win_streak + 1
                    ELSE 0
                END,
                best_win_streak = CASE
                    WHEN EXCLUDED.wins = 1 THEN GREATEST(
                        player_stats.best_win_streak,
                        player_stats.current_win_streak + 1
                    )
                    ELSE player_stats.best_win_streak
                END,
                correct_selections = player_stats.correct_selections + EXCLUDED.correct_selections,
                wrong_selections = player_stats.wrong_selections + EXCLUDED.wrong_selections,
                reaction_time_total_ms = player_stats.reaction_time_total_ms + EXCLUDED.reaction_time_total_ms,
                reaction_samples = player_stats.reaction_samples + EXCLUDED.reaction_samples,
                experience_points = player_stats.experience_points + EXCLUDED.experience_points,
                gold = player_stats.gold + EXCLUDED.gold,
                updated_at = EXCLUDED.updated_at
            """.trimIndent()
        ).use { statement ->
            match.players.forEach { player ->
                val won = if (player.outcome == MatchOutcome.WIN) 1 else 0
                val metrics = metricsByPlayer[player.playerId] ?: SelectionMetrics()
                val reward = requireNotNull(rewardsByPlayer[player.playerId])
                statement.setObject(1, UUID.fromString(player.playerId))
                statement.setInt(2, won)
                statement.setInt(3, if (player.outcome == MatchOutcome.LOSS) 1 else 0)
                statement.setInt(4, if (player.outcome == MatchOutcome.DRAW) 1 else 0)
                statement.setInt(5, player.score)
                statement.setInt(6, won)
                statement.setInt(7, won)
                statement.setLong(8, metrics.correct.toLong())
                statement.setLong(9, metrics.wrong.toLong())
                statement.setLong(10, metrics.reactionTimeTotalMillis)
                statement.setLong(11, metrics.reactionSamples.toLong())
                statement.setInt(12, reward.xp)
                statement.setInt(13, reward.gold)
                statement.setTimestamp(14, match.endedAtMillis.toTimestamp())
                statement.addBatch()
            }
            statement.executeBatch()
        }
        val rewardedPlayers = match.players.mapNotNull { player ->
            val reward = requireNotNull(rewardsByPlayer[player.playerId])
            if (reward.gold == 0 && reward.xp == 0) null else player to reward
        }
        if (rewardedPlayers.isNotEmpty()) {
            connection.prepareStatement(
                """
                INSERT INTO wallet_transactions (
                    id, user_id, source_type, source_id, gold_delta, gems_delta, xp_delta, created_at
                ) VALUES (?, ?, 'MATCH', ?, ?, 0, ?, ?)
                ON CONFLICT (user_id, source_type, source_id) DO NOTHING
                """.trimIndent()
            ).use { statement ->
                rewardedPlayers.forEach { (player, reward) ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, UUID.fromString(player.playerId))
                    statement.setString(3, match.matchId)
                    statement.setInt(4, reward.gold)
                    statement.setInt(5, reward.xp)
                    statement.setTimestamp(6, match.endedAtMillis.toTimestamp())
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun updateSeasonRatings(
        connection: Connection,
        match: CompletedMatch
    ) {
        val seasonId = connection.prepareStatement(
            "SELECT id FROM seasons WHERE ? >= starts_at AND ? < ends_at ORDER BY starts_at DESC LIMIT 1"
        ).use { statement ->
            val now = match.endedAtMillis.toTimestamp()
            statement.setTimestamp(1, now)
            statement.setTimestamp(2, now)
            statement.executeQuery().use { result -> if (result.next()) result.getObject(1, UUID::class.java) else null }
        } ?: return
        val now = match.endedAtMillis.toTimestamp()
        connection.prepareStatement(
            """
                INSERT INTO season_ratings (
                    season_id, user_id, rating, matches_played, placement_matches, peak_rating, updated_at
                )
            VALUES (?, ?, ?, 0, 0, ?, ?)
            ON CONFLICT (season_id, user_id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            match.players.forEach { player ->
                statement.setObject(1, seasonId)
                statement.setObject(2, UUID.fromString(player.playerId))
                statement.setInt(3, SEASON_INITIAL_RATING)
                statement.setInt(4, SEASON_INITIAL_RATING)
                statement.setTimestamp(5, now)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        val seasonRatings = mutableMapOf<String, Int>()
        val sortedPlayerIds = match.players.map { it.playerId }.sorted()
        connection.prepareStatement(
            """
            SELECT user_id, rating
            FROM season_ratings
            WHERE season_id = ? AND user_id IN (?, ?)
            ORDER BY user_id
            FOR UPDATE
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, seasonId)
            statement.setObject(2, UUID.fromString(sortedPlayerIds[0]))
            statement.setObject(3, UUID.fromString(sortedPlayerIds[1]))
            statement.executeQuery().use { result ->
                while (result.next()) {
                    seasonRatings[result.getObject("user_id", UUID::class.java).toString()] = result.getInt("rating")
                }
            }
        }
        val first = match.players[0]
        val second = match.players[1]
        val (firstAfter, secondAfter) = updatedEloRatings(
            seasonRatings.getValue(first.playerId),
            seasonRatings.getValue(second.playerId),
            first.outcome.toEloScore()
        )
        connection.prepareStatement(
            """
            UPDATE season_ratings
            SET rating = ?,
                matches_played = matches_played + 1,
                placement_matches = LEAST(5, placement_matches + 1),
                peak_rating = GREATEST(peak_rating, ?),
                updated_at = ?
            WHERE season_id = ? AND user_id = ?
            """.trimIndent()
        ).use { statement ->
            listOf(first.playerId to firstAfter, second.playerId to secondAfter).forEach { (playerId, rating) ->
                statement.setInt(1, rating)
                statement.setInt(2, rating)
                statement.setTimestamp(3, now)
                statement.setObject(4, seasonId)
                statement.setObject(5, UUID.fromString(playerId))
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun updateEloRatings(connection: Connection, match: CompletedMatch) {
        if (match.players.size != 2) return
        val now = match.endedAtMillis.toTimestamp()
        connection.prepareStatement(
            "INSERT INTO player_stats (user_id, updated_at) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING"
        ).use { statement ->
            match.players.forEach { player ->
                statement.setObject(1, UUID.fromString(player.playerId))
                statement.setTimestamp(2, now)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        val ratings = mutableMapOf<String, Int>()
        val sortedPlayerIds = match.players.map { it.playerId }.sorted()
        connection.prepareStatement(
            "SELECT user_id, elo_rating FROM player_stats WHERE user_id IN (?, ?) ORDER BY user_id FOR UPDATE"
        ).use { statement ->
            statement.setObject(1, UUID.fromString(sortedPlayerIds[0]))
            statement.setObject(2, UUID.fromString(sortedPlayerIds[1]))
            statement.executeQuery().use { result ->
                while (result.next()) {
                    ratings[result.getObject("user_id", UUID::class.java).toString()] = result.getInt("elo_rating")
                }
            }
        }

        val first = match.players[0]
        val second = match.players[1]
        val firstBefore = ratings.getValue(first.playerId)
        val secondBefore = ratings.getValue(second.playerId)
        val (firstAfter, secondAfter) = updatedEloRatings(
            firstBefore,
            secondBefore,
            first.outcome.toEloScore()
        )

        connection.prepareStatement("UPDATE player_stats SET elo_rating = ?, updated_at = ? WHERE user_id = ?").use { statement ->
            listOf(first.playerId to firstAfter, second.playerId to secondAfter).forEach { (playerId, rating) ->
                statement.setInt(1, rating)
                statement.setTimestamp(2, now)
                statement.setObject(3, UUID.fromString(playerId))
                statement.addBatch()
            }
            statement.executeBatch()
        }
        connection.prepareStatement(
            """
            INSERT INTO rating_history (match_id, user_id, rating_before, rating_after, rating_change, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            listOf(
                Triple(first, firstBefore, firstAfter),
                Triple(second, secondBefore, secondAfter)
            ).forEach { (player, before, after) ->
                statement.setObject(1, UUID.fromString(match.matchId))
                statement.setObject(2, UUID.fromString(player.playerId))
                statement.setInt(3, before)
                statement.setInt(4, after)
                statement.setInt(5, after - before)
                statement.setTimestamp(6, now)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        updateSeasonRatings(connection, match)
    }

    private fun MatchOutcome.toEloScore(): Double = when (this) {
        MatchOutcome.WIN -> 1.0
        MatchOutcome.DRAW -> 0.5
        MatchOutcome.LOSS -> 0.0
    }

    private fun expectedScore(rating: Int, opponentRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating) / 400.0))

    private fun updatedEloRatings(firstBefore: Int, secondBefore: Int, firstScore: Double): Pair<Int, Int> {
        val firstExpected = expectedScore(firstBefore, secondBefore)
        val firstChange = kotlin.math.round(ELO_K_FACTOR * (firstScore - firstExpected)).toInt()
        val firstAfter = (firstBefore + firstChange).coerceAtLeast(MIN_ELO)
        val secondAfter = (secondBefore - (firstAfter - firstBefore)).coerceAtLeast(MIN_ELO)
        return firstAfter to secondAfter
    }

    private fun calculateSelectionMetrics(match: CompletedMatch): Map<String, SelectionMetrics> {
        val metrics = match.players.associate { it.playerId to SelectionMetrics() }.toMutableMap()
        var targetAvailableAtMillis = match.startedAtMillis
        match.events.sortedBy(MatchSelectionEvent::sequence).forEach { event ->
            val current = metrics.getOrPut(event.playerId) { SelectionMetrics() }
            when (event.result) {
                SelectionResult.REJECTED -> current.wrong++
                SelectionResult.ACCEPTED -> {
                    current.correct++
                    current.reactionTimeTotalMillis +=
                        (event.occurredAtMillis - targetAvailableAtMillis).coerceAtLeast(0L)
                    current.reactionSamples++
                    targetAvailableAtMillis = event.occurredAtMillis
                }
            }
        }
        return metrics
    }

    private fun unlockAchievements(connection: Connection, match: CompletedMatch) {
        val metricsByPlayer = calculateSelectionMetrics(match)
        val matchDuration = (match.endedAtMillis - match.startedAtMillis).coerceAtLeast(0L)
        connection.prepareStatement(
            "SELECT wins, current_win_streak FROM player_stats WHERE user_id = ?"
        ).use { statsStatement ->
            connection.prepareStatement(
                """
                INSERT INTO user_achievements (user_id, achievement_code, unlocked_at, match_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, achievement_code) DO NOTHING
                """.trimIndent()
            ).use { insertStatement ->
                match.players.forEach { player ->
                    val playerId = UUID.fromString(player.playerId)
                    statsStatement.setObject(1, playerId)
                    val (wins, streak) = statsStatement.executeQuery().use { result ->
                        check(result.next()) { "Missing player stats for ${player.playerId}" }
                        result.getInt("wins") to result.getInt("current_win_streak")
                    }
                    val metrics = metricsByPlayer[player.playerId] ?: SelectionMetrics()
                    val unlocked = buildSet {
                        if (wins >= 1) add("FIRST_WIN")
                        if (wins >= 10) add("WIN_10")
                        if (streak >= 5) add("STREAK_5")
                        if (player.isPerfectWinner(metrics)) add("PERFECT_GAME")
                        if (qualifiesForSpeed50(player.outcome, metrics.correct, matchDuration)) {
                            add("SPEED_50")
                        }
                    }
                    unlocked.forEach { achievementCode ->
                        insertStatement.setObject(1, playerId)
                        insertStatement.setString(2, achievementCode)
                        insertStatement.setTimestamp(3, match.endedAtMillis.toTimestamp())
                        insertStatement.setObject(4, UUID.fromString(match.matchId))
                        insertStatement.addBatch()
                    }
                }
                insertStatement.executeBatch()
            }
        }
    }

    private fun updateMissions(connection: Connection, match: CompletedMatch) {
        val metricsByPlayer = calculateSelectionMetrics(match)
        val matchDate = missionDateAt(match.endedAtMillis)
        val activeDefinitions =
            MissionRotation.forPeriod(MissionPeriod.DAILY, matchDate) +
                MissionRotation.forPeriod(MissionPeriod.WEEKLY, matchDate)
        connection.prepareStatement(
            """
            INSERT INTO user_missions (
                user_id, mission_code, period_start, progress, target, reward_xp,
                reward_gold, reward_gems, completed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? >= ? THEN CAST(? AS TIMESTAMPTZ) ELSE NULL END)
            ON CONFLICT (user_id, mission_code, period_start) DO UPDATE SET
                progress = LEAST(user_missions.target, user_missions.progress + EXCLUDED.progress),
                target = EXCLUDED.target,
                reward_xp = EXCLUDED.reward_xp,
                reward_gold = EXCLUDED.reward_gold,
                reward_gems = EXCLUDED.reward_gems,
                completed_at = CASE
                    WHEN user_missions.completed_at IS NOT NULL THEN user_missions.completed_at
                    WHEN user_missions.progress + EXCLUDED.progress >= user_missions.target THEN CURRENT_TIMESTAMP
                    ELSE NULL
                END
            """.trimIndent()
        ).use { statement ->
            match.players.forEach { player ->
                val metrics = metricsByPlayer[player.playerId] ?: SelectionMetrics()
                val currentStreak = currentWinStreak(connection, player.playerId)
                val missions = activeDefinitions.map { definition ->
                    MissionProgress(
                        definition = definition,
                        periodStart = java.sql.Date.valueOf(missionPeriodStart(definition, matchDate)),
                        increment = missionIncrement(
                            definition = definition,
                            matchType = match.matchType,
                            player = player,
                            metrics = metrics,
                            currentStreak = currentStreak
                        )
                    )
                }
                missions.filter { it.increment > 0 }.forEach { mission ->
                    statement.setObject(1, UUID.fromString(player.playerId))
                    statement.setString(2, mission.definition.code)
                    statement.setDate(3, mission.periodStart)
                    statement.setInt(4, mission.increment)
                    statement.setInt(5, mission.definition.target)
                    statement.setInt(6, mission.definition.rewardXp)
                    statement.setInt(7, mission.definition.rewardGold)
                    statement.setInt(8, mission.definition.rewardGems)
                    statement.setInt(9, mission.increment)
                    statement.setInt(10, mission.definition.target)
                    statement.setTimestamp(11, match.endedAtMillis.toTimestamp())
                    statement.addBatch()
                }
            }
            statement.executeBatch()
        }
    }

    private fun currentWinStreak(connection: Connection, playerId: String): Int =
        connection.prepareStatement("SELECT current_win_streak FROM player_stats WHERE user_id = ?").use { statement ->
            statement.setObject(1, UUID.fromString(playerId))
            statement.executeQuery().use { result ->
                check(result.next()) { "Missing player stats for $playerId" }
                result.getInt("current_win_streak")
            }
        }

    private fun missionIncrement(
        definition: MissionDefinition,
        matchType: MatchType,
        player: CompletedMatchPlayer,
        metrics: SelectionMetrics,
        currentStreak: Int
    ): Int {
        if (player.intentionalLeave) return 0
        val won = player.outcome == MatchOutcome.WIN
        val perfectWin = player.isPerfectWinner(metrics)
        val totalSelections = metrics.correct.toLong() + metrics.wrong.toLong()
        val hasNinetyPercentAccuracy =
            totalSelections > 0 && metrics.correct.toLong() * 100 >= totalSelections * 90
        return when (definition.code) {
            "DAILY_PLAY_1", "DAILY_PLAY_3", "WEEKLY_PLAY_15" -> 1
            "DAILY_CASUAL_2" -> if (matchType == MatchType.CASUAL) 1 else 0
            "DAILY_RANKED_2" -> if (matchType == MatchType.RANKED) 1 else 0
            "DAILY_WIN_1", "WEEKLY_WIN_5" -> if (won) 1 else 0
            "WEEKLY_RANKED_WIN_3" -> if (matchType == MatchType.RANKED && won) 1 else 0
            "DAILY_CORRECT_100", "WEEKLY_CORRECT_500" -> metrics.correct
            "DAILY_ACCURACY_90" -> if (hasNinetyPercentAccuracy) 1 else 0
            "DAILY_PERFECT_WIN_1", "WEEKLY_PERFECT_3" -> if (perfectWin) 1 else 0
            "WEEKLY_STREAK_3" -> if (currentStreak >= definition.target) definition.target else 0
            "DAILY_CHECK_IN", "DAILY_DONATE_GOLD_500",
            "WEEKLY_DONATE_GOLD_2000", "WEEKLY_DONATE_GEMS_5" -> 0
            else -> 0
        }
    }

    private data class SelectionMetrics(
        var correct: Int = 0,
        var wrong: Int = 0,
        var reactionTimeTotalMillis: Long = 0,
        var reactionSamples: Int = 0
    )

    private data class MissionProgress(
        val definition: MissionDefinition,
        val periodStart: java.sql.Date,
        val increment: Int
    )

    private fun CompletedMatchPlayer.isPerfectWinner(metrics: SelectionMetrics): Boolean =
        qualifiesForPerfectGame(outcome, metrics.correct, metrics.wrong)

    private fun Long.toTimestamp(): Timestamp = Timestamp.from(Instant.ofEpochMilli(this))

    private fun updateClanWarsAndQuests(connection: Connection, match: CompletedMatch) {
        if (match.matchType != MatchType.RANKED) return
        
        for (player in match.players) {
            val isWinner = player.outcome == MatchOutcome.WIN
            val isDraw = player.outcome == MatchOutcome.DRAW
            
            if (isDraw) continue
            
            val trophiesChange = if (isWinner) 5 else -2
            val questProgress = if (isWinner) 1 else 0

            val userUuid = UUID.fromString(player.playerId)
            
            var clanId: UUID? = null
            connection.prepareStatement("SELECT clan_id FROM clan_members WHERE user_id = ?").use {
                it.setObject(1, userUuid)
                it.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString("clan_id")?.let { id -> clanId = UUID.fromString(id) }
                    }
                }
            }
            
            if (clanId != null) {
                // Update Clan Trophies
                connection.prepareStatement("UPDATE clans SET trophies = GREATEST(0, trophies + ?) WHERE id = ?").use {
                    it.setInt(1, trophiesChange)
                    it.setObject(2, clanId)
                    it.executeUpdate()
                }
                
                // Update Clan Quest
                if (questProgress > 0) {
                    connection.prepareStatement("UPDATE clans SET quest_progress = quest_progress + ? WHERE id = ?").use {
                        it.setInt(1, questProgress)
                        it.setObject(2, clanId)
                        it.executeUpdate()
                    }
                    
                    connection.prepareStatement("UPDATE clan_members SET quest_contribution = quest_contribution + ? WHERE clan_id = ? AND user_id = ?").use {
                        it.setInt(1, questProgress)
                        it.setObject(2, clanId)
                        it.setObject(3, userUuid)
                        it.executeUpdate()
                    }
                }
            }
        }
    }

    private companion object {
        const val ELO_K_FACTOR = 32.0
        const val MIN_ELO = 100
        const val SEASON_INITIAL_RATING = 1_000
    }
}

internal fun qualifiesForPerfectGame(
    outcome: MatchOutcome,
    correctSelections: Int,
    wrongSelections: Int
): Boolean = outcome == MatchOutcome.WIN && correctSelections > 0 && wrongSelections == 0

internal fun qualifiesForSpeed50(
    outcome: MatchOutcome,
    correctSelections: Int,
    matchDurationMillis: Long
): Boolean =
    outcome == MatchOutcome.WIN &&
        correctSelections >= 50 &&
        matchDurationMillis <= 30_000L
