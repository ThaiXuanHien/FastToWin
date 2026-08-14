package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationRepositoryTest {
    @Test
    fun `notification read and dismissal state persists idempotently`() = runTest {
        val repository = InMemoryNotificationRepository()
        val userId = UUID.randomUUID().toString()
        val first = notification("achievement:first", 1_000L)
        val second = notification("mission:daily:test", 2_000L)

        repository.createNotifications(userId, listOf(first, second, first))
        assertEquals(listOf(second.id, first.id), repository.loadNotifications(userId).map { it.id })

        repository.markNotificationsRead(userId, first.id, 3_000L)
        assertTrue(repository.loadNotifications(userId).single { it.id == first.id }.isRead)

        repository.dismissNotifications(userId, first.id, 4_000L)
        repository.createNotifications(userId, listOf(first))
        assertEquals(listOf(second.id), repository.loadNotifications(userId).map { it.id })

        repository.dismissNotifications(userId, null, 5_000L)
        assertTrue(repository.loadNotifications(userId).isEmpty())
    }

    @Test
    fun `postgres stores notifications and active room invitations`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val auth = AuthenticationService(
                PostgresAuthRepository(dataSource),
                PasswordHasher(iterations = 1_000),
                nowMillis = { NOW }
            )
            val first = assertIs<AuthResult.Success>(auth.register(
                "notification-a-${UUID.randomUUID()}@example.com", PASSWORD, "Notify A", "android"
            )).session
            val second = assertIs<AuthResult.Success>(auth.register(
                "notification-b-${UUID.randomUUID()}@example.com", PASSWORD, "Notify B", "ios"
            )).session
            val repository = PostgresNotificationRepository(dataSource)
            val invitation = StoredRoomInvitation(
                id = UUID.randomUUID().toString(),
                inviterId = first.userId,
                inviteeId = second.userId,
                roomId = UUID.randomUUID().toString(),
                inviterDisplayName = "Notify A",
                roomName = "Persistent room",
                expiresAtMillis = NOW + 60_000L
            )
            try {
                val notification = notification("achievement:postgres", NOW)
                repository.createNotifications(second.userId, listOf(notification, notification))
                assertEquals(1, repository.loadNotifications(second.userId).size)
                repository.markNotificationsRead(second.userId, notification.id, NOW + 1)
                assertTrue(repository.loadNotifications(second.userId).single().isRead)

                repository.saveRoomInvitation(invitation)
                assertEquals(invitation, repository.loadActiveRoomInvitations(NOW).single())
                assertTrue(repository.loadActiveRoomInvitations(NOW + 60_001L).isEmpty())

                repository.dismissNotifications(second.userId, notification.id, NOW + 2)
                assertTrue(repository.loadNotifications(second.userId).isEmpty())
                repository.deleteRoomInvitation(invitation.id)
                assertTrue(repository.loadActiveRoomInvitations(NOW).isEmpty())
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id IN (?, ?)").use { statement ->
                        statement.setObject(1, UUID.fromString(first.userId))
                        statement.setObject(2, UUID.fromString(second.userId))
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private fun notification(id: String, createdAt: Long) = NotificationSnapshot(
        id = id,
        kind = NotificationKind.ACHIEVEMENT,
        title = "Thành tích",
        message = "Bạn vừa mở khóa thành tích.",
        createdAtEpochMillis = createdAt,
        destination = NotificationDestination.PROFILE
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val PASSWORD = "password123"
    }
}
