package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.server.AuthResult
import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.PostgresAuthRepository
import com.hienthai.fastowin.server.PostgresNotificationRepository
import com.hienthai.fastowin.server.PostgresPlayerProfileRepository
import com.hienthai.fastowin.protocol.PushPreferencesSnapshot
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.AdminAnnouncementState
import com.hienthai.fastowin.server.admin.AuditEntry
import com.hienthai.fastowin.server.admin.AdminException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PostgresAnnouncementRepositoryTest {
    @Test fun `postgres freezes one recipient and keeps its facts after user deletion while legacy preferences stay separate`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"; maximumPoolSize = 2
        }).use { ds ->
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            val auth = AuthenticationService(PostgresAuthRepository(ds), PasswordHasher(iterations = 1_000), nowMillis = { 1_800_000_000_000L })
            val user = assertIs<AuthResult.Success>(auth.register("announce-facts-${UUID.randomUUID()}@example.com", "password123", "Player", "android")).session
            val id = UUID.randomUUID().toString()
            val definition = AnnouncementRepositoryTest().fixtureDefinition(id)
            val repo = PostgresNotificationRepository(ds)
            var deleted = false
            try {
                ds.connection.use { connection ->
                    connection.prepareStatement("UPDATE users SET notification_language='vi' WHERE id=?").use { statement ->
                        statement.setObject(1, UUID.fromString(user.userId)); statement.executeUpdate()
                    }
                }
                repo.announcements.transaction {
                    assertEquals(AnnouncementPreference(user.userId), preference(user.userId))
                    assertEquals("vi", registeredPlayers().single { it.userId == user.userId }.notificationLanguage)
                    putDefinition(definition)
                    putRecipient(AnnouncementRecipient(id, user.userId, storedAtEpochMillis = 100))
                    putRecipient(AnnouncementRecipient(id, user.userId))
                    putPreference(AnnouncementPreference(user.userId, adminEnabled = true))
                    notifications.create(user.userId, listOf(definition.notification("vi", 100)))
                }
                repo.announcements.transaction(admin = true) {
                    putDefinition(definition.copy(version = 2, status = AnnouncementStatus.CANCELLED,
                        updatedAtEpochMillis = 200, cancelledBy = UUID.randomUUID().toString(),
                        cancelledAtEpochMillis = 200, cancellationReason = "Cancelled"))
                }
                repo.announcements.transaction {
                    assertEquals(AdminAnnouncementState.CANCELLED,
                        notifications.load(user.userId, includeDismissed = true).single().announcement?.state)
                }
                val wire = ProtocolJson.encodeToString(repo.loadNotifications(user.userId).single())
                assertEquals("Tiêu đề", repo.loadNotifications(user.userId).single().title)
                assertTrue(!wire.contains("createdBy") && !wire.contains("cancellationReason") && !wire.contains("selectedUserIds"))
                assertTrue(PostgresPlayerProfileRepository(ds).updatePushPreferences(user.userId, PushPreferencesSnapshot()))
                repo.announcements.transaction {
                    assertEquals(true, preference(user.userId).adminEnabled)
                    assertEquals(false, preference(user.userId).eventEnabled)
                    assertEquals(1, recipients(id, 100, 0).size)
                    assertEquals(100L, recipient(id, user.userId)?.storedAtEpochMillis)
                    assertEquals(1L, recipientCounts(id).frozen)
                    assertEquals(1L, recipientCounts(id).stored)
                    assertEquals(1L, notifications.count(user.userId, includeDismissed = true, announcementOnly = true))
                    assertEquals(id, notifications.load(user.userId, 1, 0, true, true, "admin-announcement:$id").single().announcement?.id)
                }
                ds.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id=?").use { statement ->
                        statement.setObject(1, UUID.fromString(user.userId)); statement.executeUpdate()
                    }
                }
                deleted = true
                repo.announcements.transaction { assertEquals(1, recipients(id, 100, 0).size) }
                ds.connection.use { connection ->
                    connection.prepareStatement("SELECT count(*) FROM user_notifications WHERE user_id=?").use { statement ->
                        statement.setObject(1, UUID.fromString(user.userId))
                        statement.executeQuery().use { result -> result.next(); assertEquals(0, result.getInt(1)) }
                    }
                }
            } finally {
                try {
                    if (!deleted) ds.connection.use { connection ->
                        connection.prepareStatement("DELETE FROM users WHERE id=?").use { statement ->
                            statement.setObject(1, UUID.fromString(user.userId)); statement.executeUpdate()
                        }
                    }
                } finally { deleteAnnouncementFixture(ds, id) }
            }
        }
    }
    @Test fun `postgres materialization and checkpoint commit atomically and reload with old notification reader`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"; maximumPoolSize = 2
        }).use { ds ->
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            val auth = AuthenticationService(PostgresAuthRepository(ds), PasswordHasher(iterations = 1_000), nowMillis = { 1_800_000_000_000L })
            val user = assertIs<AuthResult.Success>(auth.register("announce-store-${UUID.randomUUID()}@example.com", "password123", "Player", "android")).session
            val id = UUID.randomUUID().toString()
            val definition = AnnouncementRepositoryTest().fixtureDefinition(id)
            val repo = PostgresNotificationRepository(ds)
            try {
                repo.announcements.transaction {
                    putDefinition(definition)
                    putRecipient(AnnouncementRecipient(id, user.userId))
                }
                try {
                    repo.announcements.transaction {
                        putRecipient(AnnouncementRecipient(id, user.userId, storedAtEpochMillis = 100))
                        notifications.create(user.userId, listOf(definition.notification("vi", 100)))
                        error("rollback")
                    }
                } catch (_: IllegalStateException) { }
                assertTrue(repo.loadNotifications(user.userId).isEmpty())
                repo.announcements.transaction { assertNull(recipient(id, user.userId)?.storedAtEpochMillis) }
                assertFailsWith<AdminException> {
                    repo.announcements.transaction(admin = true) {
                        putRecipient(AnnouncementRecipient(id, user.userId, storedAtEpochMillis = 100))
                        notifications.create(user.userId, listOf(definition.notification("vi", 100)))
                        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                            "ANNOUNCEMENT_TEST", id, null, null, null, null, 100))
                    }
                }
                assertTrue(repo.loadNotifications(user.userId).isEmpty())
                repo.announcements.transaction { assertNull(recipient(id, user.userId)?.storedAtEpochMillis) }
                repo.announcements.transaction {
                    putRecipient(AnnouncementRecipient(id, user.userId, storedAtEpochMillis = 100))
                    notifications.create(user.userId, listOf(definition.notification("vi", 100)))
                    notifications.create(user.userId, listOf(definition.notification("vi", 100)))
                }
                val reloaded = PostgresNotificationRepository(ds).loadNotifications(user.userId).single()
                assertEquals("Tiêu đề", reloaded.title)
                assertEquals(id, reloaded.announcement?.id)
                ds.connection.use { connection ->
                    connection.prepareStatement("SELECT count(*) FROM user_notifications WHERE user_id=? AND notification_id=?").use { statement ->
                        statement.setObject(1, UUID.fromString(user.userId)); statement.setString(2, "admin-announcement:$id")
                        statement.executeQuery().use { result -> result.next(); assertEquals(1, result.getInt(1)) }
                    }
                }
                repo.markNotificationsRead(user.userId, reloaded.id, 200)
                repo.announcements.transaction { assertEquals(200, recipient(id, user.userId)?.readAtEpochMillis) }
                PostgresNotificationRepository(ds).dismissNotifications(user.userId, reloaded.id, 300)
                repo.announcements.transaction { assertEquals(300, recipient(id, user.userId)?.dismissedAtEpochMillis) }
                assertTrue(repo.loadNotifications(user.userId).isEmpty())
                repo.announcements.transaction {
                    assertEquals(1L, notifications.count(user.userId, includeDismissed = true, announcementOnly = true))
                    assertEquals(1L, recipientCounts(id).read)
                    assertEquals(1L, recipientCounts(id).dismissed)
                }
            } finally {
                try {
                    ds.connection.use { connection ->
                        connection.prepareStatement("DELETE FROM users WHERE id=?").use { statement ->
                            statement.setObject(1, UUID.fromString(user.userId)); statement.executeUpdate()
                        }
                    }
                } finally { deleteAnnouncementFixture(ds, id) }
            }
        }
    }

    private fun deleteAnnouncementFixture(ds: HikariDataSource, id: String) {
        ds.connection.use { connection ->
            connection.autoCommit = false
            try {
                for (table in listOf("admin_announcement_push_attempts", "admin_announcement_recipients", "admin_announcements")) {
                    val column = if (table == "admin_announcements") "id" else "announcement_id"
                    connection.prepareStatement("DELETE FROM $table WHERE $column=?").use { statement ->
                        statement.setObject(1, UUID.fromString(id)); statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (error: Throwable) { connection.rollback(); throw error }
        }
    }
}
