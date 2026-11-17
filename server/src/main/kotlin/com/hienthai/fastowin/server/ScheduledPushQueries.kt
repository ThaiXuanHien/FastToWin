package com.hienthai.fastowin.server

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate

internal const val GIFT_PUSH_WINDOW_MILLIS = 2L * 24 * 60 * 60 * 1000
internal const val SEASON_ENDING_WINDOW_MILLIS = 24L * 60 * 60 * 1000

internal data class ScheduledPushQuery(
    val sql: String,
    val parameters: List<Any>,
    val argumentName: String? = null
)

/**
 * Each query selects (id, fcm_token, notification_language, reminder_key, arg) for active players
 * who have a device token, have the category switched on and have no delivery under that key yet,
 * so a reminder reaches each player at most once whatever the scheduler interval.
 */
internal fun scheduledPushQuery(kind: ScheduledPushKind, nowMillis: Long, localDate: LocalDate): ScheduledPushQuery {
    val now = Timestamp(nowMillis)
    fun eligible(column: String, keyExpression: String) = """
        u.status = 'ACTIVE' AND u.fcm_token IS NOT NULL AND BTRIM(u.fcm_token) <> ''
          AND u.$column = TRUE
          AND NOT EXISTS (
              SELECT 1 FROM push_reminder_deliveries d WHERE d.user_id = u.id AND d.reminder_key = $keyExpression
          )
    """.trimIndent()
    return when (kind) {
        // Checked in yesterday with a streak worth keeping, but not yet today.
        ScheduledPushKind.STREAK_AT_RISK -> ScheduledPushQuery(
            """
            SELECT u.id, u.fcm_token, u.notification_language, ('streak:' || ?) AS reminder_key, y.streak_after::text AS arg
            FROM users u
            JOIN daily_check_ins y ON y.user_id = u.id AND y.check_in_date = ?
            WHERE y.streak_after >= 2
              AND NOT EXISTS (SELECT 1 FROM daily_check_ins t WHERE t.user_id = u.id AND t.check_in_date = ?)
              AND ${eligible("push_daily_check_in_enabled", "('streak:' || ?)")}
            """.trimIndent(),
            listOf(localDate.toString(), Date.valueOf(localDate.minusDays(1)), Date.valueOf(localDate), localDate.toString()),
            argumentName = "streak"
        )
        // Registered players of a scheduled tournament whose check-in window is open.
        ScheduledPushKind.TOURNAMENT_CHECK_IN -> ScheduledPushQuery(
            """
            SELECT u.id, u.fcm_token, u.notification_language, ('tournament-check-in:' || t.tournament_id) AS reminder_key, NULL AS arg
            FROM tournaments t
            CROSS JOIN LATERAL unnest(t.player_ids) AS p(user_id)
            JOIN users u ON u.id = p.user_id
            WHERE t.status = 'LOBBY'
              AND jsonb_typeof(t.snapshot_json -> 'schedule') = 'object'
              AND (t.snapshot_json -> 'schedule' ->> 'checkInOpensAtEpochMillis')::bigint <= ?
              AND (t.snapshot_json -> 'schedule' ->> 'startsAtEpochMillis')::bigint > ?
              AND ${eligible("push_tournament_reminders_enabled", "('tournament-check-in:' || t.tournament_id)")}
            """.trimIndent(),
            listOf(nowMillis, nowMillis)
        )
        // Unclaimed gifts released in the last two days, so old gifts never fire on rollout.
        ScheduledPushKind.GIFT_MAILBOX -> ScheduledPushQuery(
            """
            SELECT u.id, u.fcm_token, u.notification_language, ('gift:' || c.id) AS reminder_key, NULL AS arg
            FROM admin_gift_recipients r
            JOIN admin_gift_campaigns c ON c.id = r.campaign_id
            JOIN users u ON u.id = r.user_id
            WHERE c.state = 'PUBLISHED' AND r.delivery = 'DELIVERED' AND r.claimed_at IS NULL
              AND c.available_at <= ? AND c.available_at > ? AND c.expires_at > ?
              AND ${eligible("push_gifts_enabled", "('gift:' || c.id)")}
            """.trimIndent(),
            listOf(now, Timestamp(nowMillis - GIFT_PUSH_WINDOW_MILLIS), now)
        )
        // Players who played the open season that ends within 24 hours.
        ScheduledPushKind.SEASON_ENDING -> ScheduledPushQuery(
            """
            SELECT u.id, u.fcm_token, u.notification_language, ('season-end:' || s.id) AS reminder_key, s.name AS arg
            FROM seasons s
            JOIN season_ratings sr ON sr.season_id = s.id AND sr.matches_played > 0
            JOIN users u ON u.id = sr.user_id
            WHERE s.closed_at IS NULL AND s.ends_at > ? AND s.ends_at <= ?
              AND ${eligible("push_season_updates_enabled", "('season-end:' || s.id)")}
            """.trimIndent(),
            listOf(now, Timestamp(nowMillis + SEASON_ENDING_WINDOW_MILLIS)),
            argumentName = "season"
        )
        // Clan members while this week's published race is in its last hours (the caller checks the time).
        ScheduledPushKind.CLAN_RACE_ENDING -> ScheduledPushQuery(
            """
            SELECT u.id, u.fcm_token, u.notification_language, ('clan-race-end:' || cfg.week_start) AS reminder_key, NULL AS arg
            FROM clan_race_configs cfg
            CROSS JOIN clan_members m
            JOIN users u ON u.id = m.user_id
            WHERE cfg.week_start = ? AND cfg.state = 'PUBLISHED'
              AND ${eligible("push_clan_activity_enabled", "('clan-race-end:' || cfg.week_start)")}
            """.trimIndent(),
            listOf(Date.valueOf(localDate))
        )
    }
}
