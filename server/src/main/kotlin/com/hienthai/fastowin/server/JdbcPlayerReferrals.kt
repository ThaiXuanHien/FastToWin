package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.protocol.REFERRAL_INVITER_GOLD
import com.hienthai.fastowin.protocol.REFERRAL_MONTHLY_LIMIT
import com.hienthai.fastowin.protocol.REFERRAL_NEW_PLAYER_GOLD
import com.hienthai.fastowin.protocol.ReferralSummary
import com.hienthai.fastowin.server.announcements.JdbcNotificationPersistence
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal class InvalidReferralCodeException : RuntimeException()

internal class JdbcPlayerReferrals(private val connection: Connection) {
    fun attribute(inviteeId: UUID, code: String) {
        val inserted = connection.prepareStatement(
            """
            INSERT INTO player_referrals (invitee_id, inviter_id)
            SELECT ?, u.id
            FROM profiles p JOIN users u ON u.id = p.user_id
            WHERE p.player_code = ? AND u.id <> ? AND u.status = 'ACTIVE'
              AND u.account_type = 'REGISTERED' AND u.email_verified_at IS NOT NULL
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, inviteeId)
            statement.setString(2, code)
            statement.setObject(3, inviteeId)
            statement.executeUpdate()
        }
        if (inserted != 1) throw InvalidReferralCodeException()
    }

    fun summary(inviterId: UUID, nowMillis: Long): ReferralSummary? =
        connection.prepareStatement(
            """
            SELECT p.player_code,
                   count(r.id) FILTER (WHERE r.completed_at IS NULL) AS pending,
                   count(r.id) FILTER (WHERE r.inviter_rewarded) AS rewarded,
                   count(r.id) FILTER (
                       WHERE r.completed_at IS NOT NULL AND NOT r.inviter_rewarded
                   ) AS completed_without_bonus,
                   COALESCE(m.reward_count, 0) AS rewarded_this_month
            FROM profiles p
            JOIN users u ON u.id = p.user_id
            LEFT JOIN player_referrals r ON r.inviter_id = p.user_id
            LEFT JOIN player_referral_monthly_rewards m ON m.inviter_id = p.user_id AND m.reward_month = ?
            WHERE p.user_id = ? AND u.status = 'ACTIVE' AND u.email_verified_at IS NOT NULL
            GROUP BY p.player_code, m.reward_count
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, rewardMonth(nowMillis))
            statement.setObject(2, inviterId)
            statement.executeQuery().use { result ->
                if (!result.next()) null else ReferralSummary(
                    code = result.getString("player_code"),
                    pendingCount = result.getInt("pending"),
                    rewardedCount = result.getInt("rewarded"),
                    rewardedThisMonth = result.getInt("rewarded_this_month"),
                    completedWithoutInviterBonusCount = result.getInt("completed_without_bonus")
                )
            }
        }

    fun completeForMatch(match: CompletedMatch): Set<String> {
        if (!match.referralEligible || match.players.any(CompletedMatchPlayer::intentionalLeave)) return emptySet()
        val participatingPlayers = match.events.filter { it.result == SelectionResult.ACCEPTED }
            .mapTo(mutableSetOf(), MatchSelectionEvent::playerId)
        return buildSet {
            match.players.asSequence().map(CompletedMatchPlayer::playerId)
                .filter { it in participatingPlayers }.map(UUID::fromString).sorted().forEach { inviteeId ->
                val referral = complete(inviteeId, match) ?: return@forEach
                if (grant(inviteeId, referral.id, REFERRAL_NEW_PLAYER_GOLD, match.endedAtMillis)) {
                    add(inviteeId.toString())
                }
                val inviterId = referral.activeInviterId ?: return@forEach
                if (reserveMonthlyReward(inviterId, rewardMonth(match.endedAtMillis))) {
                    if (grant(inviterId, referral.id, REFERRAL_INVITER_GOLD, match.endedAtMillis)) {
                        add(inviterId.toString())
                    }
                    connection.prepareStatement(
                        "UPDATE player_referrals SET inviter_rewarded = TRUE WHERE id = ?"
                    ).use { statement ->
                        statement.setObject(1, referral.id)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private fun complete(inviteeId: UUID, match: CompletedMatch): CompletedReferral? =
        connection.prepareStatement(
            """
            UPDATE player_referrals r SET completed_at = ?
            FROM users invitee
            WHERE r.invitee_id = ? AND r.completed_at IS NULL AND invitee.id = r.invitee_id
              AND invitee.status = 'ACTIVE' AND invitee.email_verified_at <= ?
            RETURNING r.id, (
                SELECT inviter.id FROM users inviter
                WHERE inviter.id = r.inviter_id AND inviter.status = 'ACTIVE'
                  AND inviter.email_verified_at IS NOT NULL
            ) AS active_inviter_id
            """.trimIndent()
        ).use { statement ->
            statement.setTimestamp(1, Timestamp(match.endedAtMillis))
            statement.setObject(2, inviteeId)
            statement.setTimestamp(3, Timestamp(match.startedAtMillis))
            statement.executeQuery().use { result ->
                if (!result.next()) null else CompletedReferral(
                    result.getObject("id", UUID::class.java),
                    result.getObject("active_inviter_id", UUID::class.java)
                )
            }
        }

    private fun reserveMonthlyReward(inviterId: UUID, month: LocalDate): Boolean =
        connection.prepareStatement(
            """
            INSERT INTO player_referral_monthly_rewards (inviter_id, reward_month, reward_count)
            VALUES (?, ?, 1)
            ON CONFLICT (inviter_id, reward_month) DO UPDATE
            SET reward_count = player_referral_monthly_rewards.reward_count + 1
            WHERE player_referral_monthly_rewards.reward_count < ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, inviterId)
            statement.setObject(2, month)
            statement.setInt(3, REFERRAL_MONTHLY_LIMIT)
            statement.executeUpdate() == 1
        }

    private fun grant(userId: UUID, referralId: UUID, gold: Int, nowMillis: Long): Boolean {
        val credited = connection.prepareStatement(
            """
            WITH reward AS (
                INSERT INTO wallet_transactions (id, user_id, source_type, source_id, gold_delta, created_at)
                VALUES (?, ?, 'REFERRAL', ?, ?, ?)
                ON CONFLICT (user_id, source_type, source_id) DO NOTHING
                RETURNING user_id, gold_delta, created_at
            )
            INSERT INTO player_stats (user_id, gold, updated_at)
            SELECT user_id, gold_delta, created_at FROM reward
            ON CONFLICT (user_id) DO UPDATE
            SET gold = player_stats.gold + EXCLUDED.gold,
                updated_at = GREATEST(player_stats.updated_at, EXCLUDED.updated_at)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, userId)
            statement.setString(3, referralId.toString())
            statement.setInt(4, gold)
            statement.setTimestamp(5, Timestamp(nowMillis))
            statement.executeUpdate() == 1
        }
        if (credited) {
            JdbcNotificationPersistence(connection).create(userId.toString(), listOf(NotificationSnapshot(
                id = "referral:$referralId",
                kind = NotificationKind.ACHIEVEMENT,
                title = vietnamese.text(TextKey.ReferralRewardTitle),
                message = vietnamese.text(TextKey.ReferralRewardMessage, mapOf("gold" to gold)),
                createdAtEpochMillis = nowMillis,
                destination = NotificationDestination.FRIENDS,
                titleKey = TextKey.ReferralRewardTitle.name,
                messageKey = TextKey.ReferralRewardMessage.name,
                messageArgs = mapOf("gold" to gold.toString())
            )))
        }
        return credited
    }

    private data class CompletedReferral(val id: UUID, val activeInviterId: UUID?)

    private companion object {
        val zone: ZoneId = ZoneId.of("Asia/Bangkok")
        val vietnamese = LocalizationService(AppLanguage.VIETNAMESE)

        fun rewardMonth(millis: Long): LocalDate =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().withDayOfMonth(1)
    }
}
