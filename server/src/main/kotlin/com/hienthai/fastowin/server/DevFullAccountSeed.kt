package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.SHOP_ITEMS
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.UUID

private const val DEFAULT_DEV_EMAIL = "fulltest@fasttowin.dev"
private const val DEFAULT_DEV_PASSWORD = "12345678"
private const val DEFAULT_DEV_DISPLAY_NAME = "Full Test"
private val DEV_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")

fun main() {
    val environment = System.getenv("FASTTOWIN_ENV")?.lowercase() ?: "dev"
    require(environment == "dev") { "The full account seed is available only in FASTTOWIN_ENV=dev." }
    val settings = DatabaseSettings.fromEnvironment(environment)
        ?: error("DATABASE_URL is required to seed the development account.")
    val email = (System.getenv("FASTTOWIN_DEV_ACCOUNT_EMAIL") ?: DEFAULT_DEV_EMAIL).trim().lowercase()
    val password = System.getenv("FASTTOWIN_DEV_ACCOUNT_PASSWORD") ?: DEFAULT_DEV_PASSWORD
    val displayName = (System.getenv("FASTTOWIN_DEV_ACCOUNT_NAME") ?: DEFAULT_DEV_DISPLAY_NAME).trim()
    require(email.contains('@') && email.length <= 254) { "FASTTOWIN_DEV_ACCOUNT_EMAIL is invalid." }
    require(password.length in 8..128) { "FASTTOWIN_DEV_ACCOUNT_PASSWORD must contain 8-128 characters." }
    require(displayName.length in 1..32) { "FASTTOWIN_DEV_ACCOUNT_NAME must contain 1-32 characters." }

    HikariDataSource(HikariConfig().apply {
        jdbcUrl = settings.url
        username = settings.user
        this.password = settings.password
        maximumPoolSize = 2
        minimumIdle = 1
        poolName = "fasttowin-dev-seed"
    }).use { dataSource ->
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        val passwordHash = PasswordHasher().hash(password)
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = seedFullDevelopmentAccount(connection, email, passwordHash, displayName)
                connection.commit()
                println("[FastToWin] Full development account is ready.")
                println("Email: $email")
                println("Password: $password")
                println("Player ID: $userId")
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }
}

internal fun seedFullDevelopmentAccount(
    connection: Connection,
    email: String,
    passwordHash: String,
    displayName: String
): UUID {
    val now = Instant.now()
    val today = LocalDate.now(DEV_ZONE)
    val userId = connection.findUuid(
        "SELECT id FROM users WHERE email_normalized = ?"
    ) { it.setString(1, email) } ?: stableUuid("account:$email")
    val playerCode = connection.findString("SELECT player_code FROM profiles WHERE user_id = ?") {
        it.setObject(1, userId)
    } ?: "DEV${userId.toString().replace("-", "").take(9).uppercase()}"

    connection.update(
        """
        INSERT INTO users (
            id, account_type, status, email_normalized, password_hash,
            email_verified_at, created_at, updated_at
        ) VALUES (?, 'REGISTERED', 'ACTIVE', ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            account_type = 'REGISTERED', status = 'ACTIVE', email_normalized = EXCLUDED.email_normalized,
            password_hash = EXCLUDED.password_hash, email_verified_at = EXCLUDED.email_verified_at,
            updated_at = EXCLUDED.updated_at
        """.trimIndent()
    ) {
        it.setObject(1, userId)
        it.setString(2, email)
        it.setString(3, passwordHash)
        it.setTimestamp(4, Timestamp.from(now))
        it.setTimestamp(5, Timestamp.from(now.minusSeconds(120L * 86_400L)))
        it.setTimestamp(6, Timestamp.from(now))
    }
    connection.update(
        """
        INSERT INTO profiles (user_id, display_name, player_code, avatar_url, created_at, updated_at)
        VALUES (?, ?, ?, 'crown', ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET
            display_name = EXCLUDED.display_name, avatar_url = EXCLUDED.avatar_url,
            updated_at = EXCLUDED.updated_at
        """.trimIndent()
    ) {
        it.setObject(1, userId)
        it.setString(2, displayName)
        it.setString(3, playerCode)
        it.setTimestamp(4, Timestamp.from(now.minusSeconds(120L * 86_400L)))
        it.setTimestamp(5, Timestamp.from(now))
    }
    seedPlayerStats(connection, userId, today)
    seedAchievements(connection, userId, now)
    seedMissions(connection, userId, today, now)
    seedDailyCheckIns(connection, userId, today)
    seedShopCosmetics(connection, userId, now)
    seedSeasonData(connection, userId, displayName, playerCode, now)
    val supportPlayers = seedSupportPlayers(connection, passwordHash, now)
    seedMatches(connection, userId, displayName, supportPlayers, now)
    seedSocialData(connection, userId, supportPlayers, now)
    seedClan(connection, userId, supportPlayers.first().id, now)
    seedNotifications(connection, userId, now)
    seedWalletHistory(connection, userId, now)
    return userId
}

private fun seedPlayerStats(connection: Connection, userId: UUID, today: LocalDate) {
    connection.update(
        """
        INSERT INTO player_stats (
            user_id, total_matches, wins, losses, draws, highest_score,
            current_win_streak, best_win_streak, correct_selections, wrong_selections,
            reaction_time_total_ms, reaction_samples, elo_rating, experience_points,
            equipped_frame_id, equipped_title_id, current_daily_check_in_streak,
            best_daily_check_in_streak, total_daily_check_ins, last_daily_check_in_date,
            gold, gems, equipped_card_back_id, equipped_board_skin_id, updated_at
        ) VALUES (?, 120, 82, 28, 10, 50, 8, 18, 4980, 72, 612000, 4980,
                  2350, 9900, 'season_900003_challenger', 'title_diligent',
                  120, 120, 120, ?, 999999, 9999, 'card_back_diamond',
                  'board_skin_forest', CURRENT_TIMESTAMP)
        ON CONFLICT (user_id) DO UPDATE SET
            total_matches = EXCLUDED.total_matches, wins = EXCLUDED.wins,
            losses = EXCLUDED.losses, draws = EXCLUDED.draws,
            highest_score = EXCLUDED.highest_score,
            current_win_streak = EXCLUDED.current_win_streak,
            best_win_streak = EXCLUDED.best_win_streak,
            correct_selections = EXCLUDED.correct_selections,
            wrong_selections = EXCLUDED.wrong_selections,
            reaction_time_total_ms = EXCLUDED.reaction_time_total_ms,
            reaction_samples = EXCLUDED.reaction_samples,
            elo_rating = EXCLUDED.elo_rating,
            experience_points = EXCLUDED.experience_points,
            equipped_frame_id = EXCLUDED.equipped_frame_id,
            equipped_title_id = EXCLUDED.equipped_title_id,
            current_daily_check_in_streak = EXCLUDED.current_daily_check_in_streak,
            best_daily_check_in_streak = EXCLUDED.best_daily_check_in_streak,
            total_daily_check_ins = EXCLUDED.total_daily_check_ins,
            last_daily_check_in_date = EXCLUDED.last_daily_check_in_date,
            gold = EXCLUDED.gold, gems = EXCLUDED.gems,
            equipped_card_back_id = EXCLUDED.equipped_card_back_id,
            equipped_board_skin_id = EXCLUDED.equipped_board_skin_id,
            updated_at = CURRENT_TIMESTAMP
        """.trimIndent()
    ) {
        it.setObject(1, userId)
        it.setDate(2, Date.valueOf(today))
    }
}

private fun seedAchievements(connection: Connection, userId: UUID, now: Instant) {
    connection.update(
        """
        INSERT INTO user_achievements (user_id, achievement_code, unlocked_at, match_id)
        SELECT ?, code, ?, NULL FROM achievements
        ON CONFLICT (user_id, achievement_code) DO UPDATE SET unlocked_at = EXCLUDED.unlocked_at
        """.trimIndent()
    ) {
        it.setObject(1, userId)
        it.setTimestamp(2, Timestamp.from(now.minusSeconds(30L * 86_400L)))
    }
}

private fun seedMissions(connection: Connection, userId: UUID, today: LocalDate, now: Instant) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val missions = listOf(
        SeedMission("DAILY_PLAY_3", today, 3, 3, 20, 100, 0, true),
        SeedMission("DAILY_WIN_1", today, 1, 1, 25, 150, 0, false),
        SeedMission("WEEKLY_CORRECT_100", monday, 100, 100, 75, 400, 0, true),
        SeedMission("WEEKLY_PERFECT_1", monday, 1, 1, 120, 600, 2, false)
    )
    connection.prepareStatement(
        """
        INSERT INTO user_missions (
            user_id, mission_code, period_start, progress, target, completed_at,
            reward_xp, reward_gold, reward_gems, claimed_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, mission_code, period_start) DO UPDATE SET
            progress = EXCLUDED.progress, target = EXCLUDED.target,
            completed_at = EXCLUDED.completed_at, reward_xp = EXCLUDED.reward_xp,
            reward_gold = EXCLUDED.reward_gold, reward_gems = EXCLUDED.reward_gems,
            claimed_at = EXCLUDED.claimed_at
        """.trimIndent()
    ).use { statement ->
        missions.forEach { mission ->
            statement.setObject(1, userId)
            statement.setString(2, mission.code)
            statement.setDate(3, Date.valueOf(mission.periodStart))
            statement.setInt(4, mission.progress)
            statement.setInt(5, mission.target)
            statement.setTimestamp(6, Timestamp.from(now.minusSeconds(3_600)))
            statement.setInt(7, mission.rewardXp)
            statement.setInt(8, mission.rewardGold)
            statement.setInt(9, mission.rewardGems)
            statement.setTimestamp(10, if (mission.claimed) Timestamp.from(now.minusSeconds(1_800)) else null)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun seedDailyCheckIns(connection: Connection, userId: UUID, today: LocalDate) {
    connection.prepareStatement(
        """
        INSERT INTO daily_check_ins (
            user_id, check_in_date, cycle_day, reward_xp, streak_after,
            reward_gold, reward_gems
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, check_in_date) DO UPDATE SET
            cycle_day = EXCLUDED.cycle_day, reward_xp = EXCLUDED.reward_xp,
            streak_after = EXCLUDED.streak_after, reward_gold = EXCLUDED.reward_gold,
            reward_gems = EXCLUDED.reward_gems
        """.trimIndent()
    ).use { statement ->
        repeat(120) { index ->
            val streak = index + 1
            val cycleDay = index % 7 + 1
            statement.setObject(1, userId)
            statement.setDate(2, Date.valueOf(today.minusDays((119 - index).toLong())))
            statement.setInt(3, cycleDay)
            statement.setInt(4, if (cycleDay == 7) 40 else 20)
            statement.setInt(5, streak)
            statement.setInt(6, if (cycleDay == 7) 350 else 100)
            statement.setInt(7, if (cycleDay == 7) 1 else 0)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun seedShopCosmetics(connection: Connection, userId: UUID, now: Instant) {
    connection.prepareStatement(
        """
        INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (user_id, cosmetic_id) DO UPDATE SET cosmetic_type = EXCLUDED.cosmetic_type
        """.trimIndent()
    ).use { statement ->
        SHOP_ITEMS.forEach { item ->
            statement.setObject(1, userId)
            statement.setString(2, item.id)
            statement.setString(3, item.type.name)
            statement.setTimestamp(4, Timestamp.from(now.minusSeconds(20L * 86_400L)))
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun seedSeasonData(
    connection: Connection,
    userId: UUID,
    displayName: String,
    playerCode: String,
    now: Instant
) {
    val seasons = listOf(
        SeedSeason(900001, "[DEV] Mùa Bứt Phá", 1_380, 1_420, 18, 24, "GOLD", 1_000, 1, "FRAME"),
        SeedSeason(900002, "[DEV] Mùa Kim Cương", 1_860, 1_940, 6, 31, "DIAMOND", 2_500, 5, "FRAME"),
        SeedSeason(900003, "[DEV] Mùa Thách Đấu", 2_430, 2_510, 1, 40, "CHALLENGER", 6_000, 12, "FRAME")
    )
    seasons.forEachIndexed { index, season ->
        val seasonId = stableUuid("season:${season.number}")
        val end = now.minusSeconds(((seasons.size - index) * 45L + 10L) * 86_400L)
        val start = end.minusSeconds(90L * 86_400L)
        connection.update(
            """
            INSERT INTO seasons (
                id, name, starts_at, ends_at, reward_description, season_number, closed_at
            ) VALUES (?, ?, ?, ?, 'Thưởng đầy đủ cho tài khoản kiểm thử', ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name, starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at,
                reward_description = EXCLUDED.reward_description, closed_at = EXCLUDED.closed_at
            """.trimIndent()
        ) {
            it.setObject(1, seasonId)
            it.setString(2, season.name)
            it.setTimestamp(3, Timestamp.from(start))
            it.setTimestamp(4, Timestamp.from(end))
            it.setInt(5, season.number)
            it.setTimestamp(6, Timestamp.from(end))
        }
        connection.update(
            """
            INSERT INTO season_ratings (
                season_id, user_id, rating, matches_played, updated_at,
                placement_matches, peak_rating
            ) VALUES (?, ?, ?, ?, ?, 5, ?)
            ON CONFLICT (season_id, user_id) DO UPDATE SET
                rating = EXCLUDED.rating, matches_played = EXCLUDED.matches_played,
                placement_matches = EXCLUDED.placement_matches,
                peak_rating = EXCLUDED.peak_rating, updated_at = EXCLUDED.updated_at
            """.trimIndent()
        ) {
            it.setObject(1, seasonId)
            it.setObject(2, userId)
            it.setInt(3, season.finalRating)
            it.setInt(4, season.matches)
            it.setTimestamp(5, Timestamp.from(end))
            it.setInt(6, season.peakRating)
        }
        connection.update(
            """
            INSERT INTO season_leaderboard_archive (
                season_id, user_id, final_rank, display_name, player_code, avatar_url,
                frame_id, wins, total_matches, highest_score, rating, season_matches, archived_at
            ) VALUES (?, ?, ?, ?, ?, 'crown', ?, 20, ?, 50, ?, ?, ?)
            ON CONFLICT (season_id, user_id) DO UPDATE SET
                final_rank = EXCLUDED.final_rank, display_name = EXCLUDED.display_name,
                player_code = EXCLUDED.player_code, frame_id = EXCLUDED.frame_id,
                rating = EXCLUDED.rating, season_matches = EXCLUDED.season_matches,
                archived_at = EXCLUDED.archived_at
            """.trimIndent()
        ) {
            it.setObject(1, seasonId)
            it.setObject(2, userId)
            it.setInt(3, season.rank)
            it.setString(4, displayName)
            it.setString(5, playerCode)
            it.setString(6, "season_${season.number}_${season.tier.lowercase()}")
            it.setInt(7, season.matches)
            it.setInt(8, season.finalRating)
            it.setInt(9, season.matches)
            it.setTimestamp(10, Timestamp.from(end))
        }
        val cosmeticId = "season_${season.number}_${season.tier.lowercase()}"
        connection.update(
            """
            INSERT INTO season_reward_claims (
                season_id, user_id, tier, peak_rating, reward_gold, reward_gems,
                awarded_at, reward_cosmetic_id, reward_cosmetic_type, viewed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (season_id, user_id) DO UPDATE SET
                tier = EXCLUDED.tier, peak_rating = EXCLUDED.peak_rating,
                reward_gold = EXCLUDED.reward_gold, reward_gems = EXCLUDED.reward_gems,
                reward_cosmetic_id = EXCLUDED.reward_cosmetic_id,
                reward_cosmetic_type = EXCLUDED.reward_cosmetic_type,
                viewed_at = EXCLUDED.viewed_at
            """.trimIndent()
        ) {
            it.setObject(1, seasonId)
            it.setObject(2, userId)
            it.setString(3, season.tier)
            it.setInt(4, season.peakRating)
            it.setInt(5, season.gold)
            it.setInt(6, season.gems)
            it.setTimestamp(7, Timestamp.from(end.plusSeconds(60)))
            it.setString(8, cosmeticId)
            it.setString(9, season.cosmeticType)
            it.setTimestamp(10, Timestamp.from(end.plusSeconds(120)))
        }
        connection.update(
            """
            INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, cosmetic_id) DO UPDATE SET cosmetic_type = EXCLUDED.cosmetic_type
            """.trimIndent()
        ) {
            it.setObject(1, userId)
            it.setString(2, cosmeticId)
            it.setString(3, season.cosmeticType)
            it.setTimestamp(4, Timestamp.from(end.plusSeconds(60)))
        }
    }

    connection.findUuid(
        """
        SELECT id FROM seasons
        WHERE starts_at <= CURRENT_TIMESTAMP AND ends_at > CURRENT_TIMESTAMP
        ORDER BY starts_at DESC LIMIT 1
        """.trimIndent()
    ) {}?.let { currentSeasonId ->
        connection.update(
            """
            INSERT INTO season_ratings (
                season_id, user_id, rating, matches_played, updated_at,
                placement_matches, peak_rating
            ) VALUES (?, ?, 2450, 35, CURRENT_TIMESTAMP, 5, 2550)
            ON CONFLICT (season_id, user_id) DO UPDATE SET
                rating = 2450, matches_played = 35, placement_matches = 5,
                peak_rating = 2550, updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
        ) {
            it.setObject(1, currentSeasonId)
            it.setObject(2, userId)
        }
    }
}

private fun seedSupportPlayers(
    connection: Connection,
    passwordHash: String,
    now: Instant
): List<SeedPlayer> = listOf(
    SeedPlayer(stableUuid("support:one"), "Đối thủ Dev", "DEVOPP001", "target"),
    SeedPlayer(stableUuid("support:two"), "Bạn Dev", "DEVFRIEND1", "rocket")
).onEachIndexed { index, player ->
    connection.update(
        """
        INSERT INTO users (
            id, account_type, status, email_normalized, password_hash,
            email_verified_at, created_at, updated_at
        ) VALUES (?, 'REGISTERED', 'ACTIVE', ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
        """.trimIndent()
    ) {
        it.setObject(1, player.id)
        it.setString(2, "support${index + 1}@fasttowin.dev")
        it.setString(3, passwordHash)
        it.setTimestamp(4, Timestamp.from(now))
        it.setTimestamp(5, Timestamp.from(now.minusSeconds(100L * 86_400L)))
        it.setTimestamp(6, Timestamp.from(now))
    }
    connection.update(
        """
        INSERT INTO profiles (user_id, display_name, player_code, avatar_url, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET
            display_name = EXCLUDED.display_name, player_code = EXCLUDED.player_code,
            avatar_url = EXCLUDED.avatar_url, updated_at = EXCLUDED.updated_at
        """.trimIndent()
    ) {
        it.setObject(1, player.id)
        it.setString(2, player.name)
        it.setString(3, player.code)
        it.setString(4, player.avatarId)
        it.setTimestamp(5, Timestamp.from(now.minusSeconds(100L * 86_400L)))
        it.setTimestamp(6, Timestamp.from(now))
    }
    connection.update(
        """
        INSERT INTO player_stats (
            user_id, total_matches, wins, losses, draws, highest_score,
            elo_rating, experience_points, equipped_frame_id, updated_at
        ) VALUES (?, 40, 18, 19, 3, 50, ?, 1200, 'frame_gold', CURRENT_TIMESTAMP)
        ON CONFLICT (user_id) DO UPDATE SET elo_rating = EXCLUDED.elo_rating,
            experience_points = EXCLUDED.experience_points,
            equipped_frame_id = EXCLUDED.equipped_frame_id,
            updated_at = CURRENT_TIMESTAMP
        """.trimIndent()
    ) {
        it.setObject(1, player.id)
        it.setInt(2, 1_450 + index * 150)
    }
}

private fun seedMatches(
    connection: Connection,
    userId: UUID,
    displayName: String,
    opponents: List<SeedPlayer>,
    now: Instant
) {
    val modes = listOf("ORDER", "RANDOM_TARGET", "TIME_BONUS", "SPEED_UP", "SURVIVAL", "COMBO", "TIME_ATTACK")
    modes.forEachIndexed { index, mode ->
        val matchId = stableUuid("match:$userId:$mode")
        val opponent = opponents[index % opponents.size]
        val outcome = when (index % 4) {
            1 -> "LOSS"
            2 -> "DRAW"
            else -> "WIN"
        }
        val opponentOutcome = when (outcome) { "WIN" -> "LOSS"; "LOSS" -> "WIN"; else -> "DRAW" }
        val playerScore = when (outcome) { "WIN" -> 50; "LOSS" -> 38; else -> 45 }
        val opponentScore = when (outcome) { "WIN" -> 42; "LOSS" -> 50; else -> 45 }
        val endedAt = now.minusSeconds((index + 1L) * 86_400L)
        val ranked = index % 2 == 0
        connection.update(
            """
            INSERT INTO matches (
                id, room_name, game_mode, started_at, ended_at, winner_user_id, match_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                room_name = EXCLUDED.room_name, game_mode = EXCLUDED.game_mode,
                started_at = EXCLUDED.started_at, ended_at = EXCLUDED.ended_at,
                winner_user_id = EXCLUDED.winner_user_id, match_type = EXCLUDED.match_type
            """.trimIndent()
        ) {
            it.setObject(1, matchId)
            it.setString(2, "[DEV] $mode")
            it.setString(3, mode)
            it.setTimestamp(4, Timestamp.from(endedAt.minusSeconds(90)))
            it.setTimestamp(5, Timestamp.from(endedAt))
            it.setObject(6, when (outcome) { "WIN" -> userId; "LOSS" -> opponent.id; else -> null })
            it.setString(7, if (ranked) "RANKED" else "CASUAL")
        }
        connection.prepareStatement(
            """
            INSERT INTO match_players (match_id, user_id, display_name, score, outcome)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (match_id, user_id) DO UPDATE SET
                display_name = EXCLUDED.display_name, score = EXCLUDED.score, outcome = EXCLUDED.outcome
            """.trimIndent()
        ).use { statement ->
            listOf(
                arrayOf<Any>(userId, displayName, playerScore, outcome),
                arrayOf<Any>(opponent.id, opponent.name, opponentScore, opponentOutcome)
            ).forEach { row ->
                statement.setObject(1, matchId)
                statement.setObject(2, row[0])
                statement.setString(3, row[1] as String)
                statement.setInt(4, row[2] as Int)
                statement.setString(5, row[3] as String)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        if (ranked) {
            val after = 2_350 - index * 12
            connection.update(
                """
                INSERT INTO rating_history (
                    match_id, user_id, rating_before, rating_after, rating_change, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (match_id, user_id) DO UPDATE SET
                    rating_before = EXCLUDED.rating_before, rating_after = EXCLUDED.rating_after,
                    rating_change = EXCLUDED.rating_change, created_at = EXCLUDED.created_at
                """.trimIndent()
            ) {
                it.setObject(1, matchId)
                it.setObject(2, userId)
                it.setInt(3, after - 16)
                it.setInt(4, after)
                it.setInt(5, 16)
                it.setTimestamp(6, Timestamp.from(endedAt))
            }
        }
        connection.prepareStatement(
            """
            INSERT INTO match_events (
                match_id, user_id, request_id, number, expected_number,
                result, occurred_at, sequence
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (match_id, user_id, request_id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            repeat(5) { eventIndex ->
                val number = eventIndex + 1
                statement.setObject(1, matchId)
                statement.setObject(2, userId)
                statement.setString(3, "dev-$mode-$number")
                statement.setInt(4, number)
                statement.setInt(5, number)
                statement.setString(6, "ACCEPTED")
                statement.setTimestamp(7, Timestamp.from(endedAt.minusSeconds((5 - eventIndex).toLong())))
                statement.setInt(8, number)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}

private fun seedSocialData(
    connection: Connection,
    userId: UUID,
    players: List<SeedPlayer>,
    now: Instant
) {
    connection.update(
        """
        INSERT INTO friendships (id, requester_id, addressee_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'ACCEPTED', ?, ?)
        ON CONFLICT (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))
        DO UPDATE SET status = 'ACCEPTED', updated_at = EXCLUDED.updated_at
        """.trimIndent()
    ) {
        it.setObject(1, stableUuid("friend:$userId:${players[0].id}"))
        it.setObject(2, userId)
        it.setObject(3, players[0].id)
        it.setTimestamp(4, Timestamp.from(now.minusSeconds(10L * 86_400L)))
        it.setTimestamp(5, Timestamp.from(now))
    }
    connection.update(
        """
        INSERT INTO friendships (id, requester_id, addressee_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'PENDING', ?, ?)
        ON CONFLICT (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))
        DO UPDATE SET status = 'PENDING', requester_id = EXCLUDED.requester_id,
            addressee_id = EXCLUDED.addressee_id, updated_at = EXCLUDED.updated_at
        """.trimIndent()
    ) {
        it.setObject(1, stableUuid("friend:${players[1].id}:$userId"))
        it.setObject(2, players[1].id)
        it.setObject(3, userId)
        it.setTimestamp(4, Timestamp.from(now.minusSeconds(3_600)))
        it.setTimestamp(5, Timestamp.from(now))
    }
}

private fun seedClan(connection: Connection, userId: UUID, memberId: UUID, now: Instant) {
    val clanId = stableUuid("clan:full-account")
    connection.update("DELETE FROM clan_members WHERE user_id IN (?, ?) AND clan_id <> ?") {
        it.setObject(1, userId)
        it.setObject(2, memberId)
        it.setObject(3, clanId)
    }
    connection.update(
        """
        INSERT INTO clans (
            id, name, description, owner_id, trophies, created_at, logo_id,
            quest_progress, quest_target, quest_reward_gold, quest_reward_xp, quest_reward_gems
        ) VALUES (?, '[DEV] Toàn Năng', 'Bang hội đầy đủ dữ liệu để kiểm thử.', ?, 12500, ?,
                  'crossed_swords', 500, 500, 1000, 100, 3)
        ON CONFLICT (id) DO UPDATE SET
            description = EXCLUDED.description, owner_id = EXCLUDED.owner_id,
            trophies = EXCLUDED.trophies, logo_id = EXCLUDED.logo_id,
            quest_progress = EXCLUDED.quest_progress, quest_target = EXCLUDED.quest_target,
            quest_reward_gold = EXCLUDED.quest_reward_gold,
            quest_reward_xp = EXCLUDED.quest_reward_xp,
            quest_reward_gems = EXCLUDED.quest_reward_gems
        """.trimIndent()
    ) {
        it.setObject(1, clanId)
        it.setObject(2, userId)
        it.setTimestamp(3, Timestamp.from(now.minusSeconds(90L * 86_400L)))
    }
    connection.prepareStatement(
        """
        INSERT INTO clan_members (
            clan_id, user_id, role, joined_at, quest_contribution, quest_reward_claimed
        ) VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (clan_id, user_id) DO UPDATE SET
            role = EXCLUDED.role, quest_contribution = EXCLUDED.quest_contribution,
            quest_reward_claimed = EXCLUDED.quest_reward_claimed
        """.trimIndent()
    ).use { statement ->
        listOf(userId to "LEADER", memberId to "MEMBER").forEachIndexed { index, row ->
            statement.setObject(1, clanId)
            statement.setObject(2, row.first)
            statement.setString(3, row.second)
            statement.setTimestamp(4, Timestamp.from(now.minusSeconds((90L - index) * 86_400L)))
            statement.setInt(5, if (index == 0) 450 else 50)
            statement.setBoolean(6, false)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun seedNotifications(connection: Connection, userId: UUID, now: Instant) {
    val notifications = listOf(
        arrayOf("dev-achievement", "ACHIEVEMENT", "Đã mở mọi thành tích", "Tài khoản test đã sẵn sàng.", "PROFILE"),
        arrayOf("dev-cosmetic", "COSMETIC", "Bộ sưu tập hoàn chỉnh", "Tất cả vật phẩm test đã được mở khóa.", "PROFILE"),
        arrayOf("dev-mission", "MISSION", "Nhiệm vụ sẵn sàng", "Có nhiệm vụ hoàn thành đang chờ nhận thưởng.", "PROFILE")
    )
    connection.prepareStatement(
        """
        INSERT INTO user_notifications (
            user_id, notification_id, kind, title, message, destination, created_at, read_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, notification_id) DO UPDATE SET
            kind = EXCLUDED.kind, title = EXCLUDED.title, message = EXCLUDED.message,
            destination = EXCLUDED.destination, created_at = EXCLUDED.created_at,
            read_at = EXCLUDED.read_at, dismissed_at = NULL
        """.trimIndent()
    ).use { statement ->
        notifications.forEachIndexed { index, row ->
            statement.setObject(1, userId)
            statement.setString(2, row[0])
            statement.setString(3, row[1])
            statement.setString(4, row[2])
            statement.setString(5, row[3])
            statement.setString(6, row[4])
            statement.setTimestamp(7, Timestamp.from(now.minusSeconds((index + 1L) * 1_800L)))
            statement.setTimestamp(8, if (index == 0) Timestamp.from(now.minusSeconds(900)) else null)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun seedWalletHistory(connection: Connection, userId: UUID, now: Instant) {
    val entries = listOf(
        SeedWallet("DEV_SEED", "initial-balance", 50_000, 500, 5_000),
        SeedWallet("DAILY_CHECK_IN", "dev-check-in", 350, 1, 40),
        SeedWallet("MISSION", "dev-mission", 600, 2, 120),
        SeedWallet("MATCH", "dev-match", 100, 0, 30),
        SeedWallet("CLAN_QUEST", "dev-clan-quest", 1_000, 3, 100)
    )
    connection.prepareStatement(
        """
        INSERT INTO wallet_transactions (
            id, user_id, source_type, source_id, gold_delta, gems_delta, xp_delta, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, source_type, source_id) DO UPDATE SET
            gold_delta = EXCLUDED.gold_delta, gems_delta = EXCLUDED.gems_delta,
            xp_delta = EXCLUDED.xp_delta, created_at = EXCLUDED.created_at
        """.trimIndent()
    ).use { statement ->
        entries.forEachIndexed { index, entry ->
            statement.setObject(1, stableUuid("wallet:$userId:${entry.sourceType}:${entry.sourceId}"))
            statement.setObject(2, userId)
            statement.setString(3, entry.sourceType)
            statement.setString(4, entry.sourceId)
            statement.setInt(5, entry.gold)
            statement.setInt(6, entry.gems)
            statement.setInt(7, entry.xp)
            statement.setTimestamp(8, Timestamp.from(now.minusSeconds((index + 1L) * 86_400L)))
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun Connection.update(sql: String, bind: (PreparedStatement) -> Unit) {
    prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeUpdate()
    }
}

private fun Connection.findUuid(sql: String, bind: (PreparedStatement) -> Unit): UUID? =
    prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeQuery().use { result ->
            if (result.next()) result.getObject(1, UUID::class.java) else null
        }
    }

private fun Connection.findString(sql: String, bind: (PreparedStatement) -> Unit): String? =
    prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
    }

private fun stableUuid(key: String): UUID =
    UUID.nameUUIDFromBytes("fasttowin-dev:$key".toByteArray(StandardCharsets.UTF_8))

private data class SeedMission(
    val code: String,
    val periodStart: LocalDate,
    val progress: Int,
    val target: Int,
    val rewardXp: Int,
    val rewardGold: Int,
    val rewardGems: Int,
    val claimed: Boolean
)

private data class SeedSeason(
    val number: Int,
    val name: String,
    val finalRating: Int,
    val peakRating: Int,
    val rank: Int,
    val matches: Int,
    val tier: String,
    val gold: Int,
    val gems: Int,
    val cosmeticType: String
)

private data class SeedPlayer(val id: UUID, val name: String, val code: String, val avatarId: String)

private data class SeedWallet(
    val sourceType: String,
    val sourceId: String,
    val gold: Int,
    val gems: Int,
    val xp: Int
)
