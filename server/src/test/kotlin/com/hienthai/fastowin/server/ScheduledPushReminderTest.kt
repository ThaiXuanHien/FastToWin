package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.protocol.PushPreferencesSnapshot
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.sql.Date
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScheduledPushReminderTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `streak reminder waits for the evening and clan race reminder only runs in the last two hours`() {
        val service = ScheduledPushReminderService(NoOpPlayerProfileRepository, NoOpPushNotificationService, zone)
        // Monday 2026-11-16 10:00 in Vietnam: early in the race week.
        val monday = Instant.parse("2026-11-16T03:00:00Z").toEpochMilli()
        assertFalse(ScheduledPushKind.STREAK_AT_RISK in service.dueKinds(monday, localHour = 10))
        assertTrue(ScheduledPushKind.STREAK_AT_RISK in service.dueKinds(monday, localHour = 21))
        assertFalse(ScheduledPushKind.CLAN_RACE_ENDING in service.dueKinds(monday, localHour = 10))
        // Sunday 23:00 in Vietnam: one hour before the race week closes.
        val sundayLate = Instant.parse("2026-11-22T16:00:00Z").toEpochMilli()
        assertTrue(ScheduledPushKind.CLAN_RACE_ENDING in service.dueKinds(sundayLate, localHour = 23))
    }

    @Test
    fun `every scheduled query runs on postgres and streak reminders reach each player once`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val today = LocalDate.of(2026, 11, 17)
            val nowMillis = today.atTime(21, 30).atZone(zone).toInstant().toEpochMilli()
            val repository = PostgresPlayerProfileRepository(dataSource, Clock.fixed(Instant.ofEpochMilli(nowMillis), zone))
            val auth = AuthenticationService(PostgresAuthRepository(dataSource), PasswordHasher(iterations = 1_000), nowMillis = { nowMillis })
            val session = assertIs<AuthResult.Success>(
                auth.register("streak-push-${UUID.randomUUID()}@example.com", "password123", "Streak", "android")
            ).session
            try {
                assertTrue(repository.updateFcmToken(session.userId, "token-${UUID.randomUUID()}", "vi"))
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "INSERT INTO daily_check_ins (user_id, check_in_date, cycle_day, reward_xp, streak_after) VALUES (?, ?, 3, 10, 3)"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(session.userId))
                        statement.setDate(2, Date.valueOf(today.minusDays(1)))
                        statement.executeUpdate()
                    }
                }

                // Syntax and parameter binding for every kind, against the real schema.
                ScheduledPushKind.entries.forEach { kind ->
                    repository.loadScheduledPushTargets(kind, nowMillis, today.toString(), 50)
                }

                val due = repository.loadScheduledPushTargets(ScheduledPushKind.STREAK_AT_RISK, nowMillis, today.toString(), 2_000)
                    .single { it.target.playerId == session.userId }
                assertEquals("streak:$today", due.reminderKey)
                assertEquals(mapOf("streak" to "3"), due.arguments)
                assertEquals(AppLanguage.VIETNAMESE, due.target.language)

                assertTrue(repository.markPushReminderDelivered(session.userId, due.reminderKey))
                assertTrue(repository.loadScheduledPushTargets(ScheduledPushKind.STREAK_AT_RISK, nowMillis, today.toString(), 2_000)
                    .none { it.target.playerId == session.userId })

                // Switching the category off removes the player even before any delivery.
                val tomorrow = today.plusDays(1)
                assertTrue(repository.updatePushPreferences(session.userId, PushPreferencesSnapshot(dailyCheckInEnabled = false)))
                assertTrue(repository.loadScheduledPushTargets(ScheduledPushKind.STREAK_AT_RISK, nowMillis, tomorrow.toString(), 2_000)
                    .none { it.target.playerId == session.userId })
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(session.userId))
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
