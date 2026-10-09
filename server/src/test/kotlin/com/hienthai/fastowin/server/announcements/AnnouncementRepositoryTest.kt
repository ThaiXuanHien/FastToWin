package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.server.InMemoryNotificationRepository
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AnnouncementRepositoryTest {
    @Test fun `memory announcement rollback does not expose notification or recipient`() = runTest {
        val notifications = InMemoryNotificationRepository(InMemoryAdminRepository())
        val id = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        try {
            notifications.announcements.transaction {
                putDefinition(fixtureDefinition(id))
                putRecipient(AnnouncementRecipient(id, userId, storedAtEpochMillis = 100))
                this.notifications.create(userId, listOf(fixtureDefinition(id).notification("vi", 100)))
                error("rollback")
            }
        } catch (_: IllegalStateException) { }
        assertTrue(notifications.loadNotifications(userId).isEmpty())
        notifications.announcements.transaction {
            assertNull(definition(id))
            assertNull(recipient(id, userId))
        }
    }

    @Test fun `memory legacy read and dismissal update durable announcement receipt`() = runTest {
        val notifications = InMemoryNotificationRepository(InMemoryAdminRepository())
        val id = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        notifications.announcements.transaction {
            putDefinition(fixtureDefinition(id))
            putRecipient(AnnouncementRecipient(id, userId, storedAtEpochMillis = 100))
            this.notifications.create(userId, listOf(fixtureDefinition(id).notification("en", 100)))
        }
        assertEquals("Title", notifications.loadNotifications(userId).single().title)
        notifications.markNotificationsRead(userId, "admin-announcement:$id", 200)
        notifications.announcements.transaction { assertEquals(200, recipient(id, userId)?.readAtEpochMillis) }
        notifications.dismissNotifications(userId, "admin-announcement:$id", 300)
        notifications.announcements.transaction {
            assertEquals(300, recipient(id, userId)?.dismissedAtEpochMillis)
            assertEquals(1L, this.notifications.count(userId, includeDismissed = true, announcementOnly = true))
            assertEquals("Title", this.notifications.load(userId, includeDismissed = true, announcementOnly = true).single().title)
            assertEquals(1L, recipientCounts(id).frozen)
            assertEquals(1L, recipientCounts(id).read)
        }
        assertTrue(notifications.loadNotifications(userId).isEmpty())
    }

    @Test fun `published copy is frozen and repeated read keeps first timestamp`() = runTest {
        val notifications = InMemoryNotificationRepository(InMemoryAdminRepository())
        val id = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val definition = fixtureDefinition(id)
        notifications.announcements.transaction {
            putDefinition(definition)
            putRecipient(AnnouncementRecipient(id, userId, storedAtEpochMillis = 100))
            this.notifications.create(userId, listOf(definition.notification("en", 100)))
        }
        assertFailsWith<com.hienthai.fastowin.server.admin.AdminException> {
            notifications.announcements.transaction { putDefinition(definition.copy(version = 2, titleEn = "Changed")) }
        }
        notifications.markNotificationsRead(userId, "admin-announcement:$id", 200)
        notifications.markNotificationsRead(userId, "admin-announcement:$id", 300)
        notifications.announcements.transaction { assertEquals(200, recipient(id, userId)?.readAtEpochMillis) }
    }

    @Test fun `separate consent accepts same value retry and attempt identity cannot be replaced`() = runTest {
        val notifications = InMemoryNotificationRepository(InMemoryAdminRepository())
        val id = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val attemptId = UUID.randomUUID().toString()
        notifications.announcements.transaction {
            putDefinition(fixtureDefinition(id))
            putRecipient(AnnouncementRecipient(id, userId, storedAtEpochMillis = 100))
            putPreference(AnnouncementPreference(userId, 1, adminEnabled = true))
            putPreference(AnnouncementPreference(userId, 1, adminEnabled = true))
            putPushAttempt(AnnouncementPushAttempt(id, userId, attemptId, AnnouncementPushOutcome.ATTEMPTING, 100))
            putPushAttempt(AnnouncementPushAttempt(id, userId, attemptId, AnnouncementPushOutcome.ACCEPTED, 100, 200))
        }
        assertFailsWith<com.hienthai.fastowin.server.admin.AdminException> {
            notifications.announcements.transaction { putPreference(AnnouncementPreference(userId, 1, eventEnabled = true)) }
        }
        assertFailsWith<com.hienthai.fastowin.server.admin.AdminException> {
            notifications.announcements.transaction {
                putPushAttempt(AnnouncementPushAttempt(id, userId, UUID.randomUUID().toString(), AnnouncementPushOutcome.ATTEMPTING, 300))
            }
        }
        notifications.announcements.transaction {
            assertEquals(AnnouncementPreference(userId, 1, adminEnabled = true), preference(userId))
            assertEquals(attemptId, pushAttempt(id, userId)?.attemptId)
            assertEquals(1L, recipientCounts(id).pushAccepted)
        }
    }

    @Test fun `published recipient set cannot grow in a later transaction`() = runTest {
        val notifications = InMemoryNotificationRepository(InMemoryAdminRepository())
        val id = UUID.randomUUID().toString()
        val first = UUID.randomUUID().toString()
        notifications.announcements.transaction {
            putDefinition(fixtureDefinition(id))
            putRecipient(AnnouncementRecipient(id, first))
        }
        assertFailsWith<com.hienthai.fastowin.server.admin.AdminException> {
            notifications.announcements.transaction { putRecipient(AnnouncementRecipient(id, UUID.randomUUID().toString())) }
        }
        notifications.announcements.transaction { assertEquals(1L, recipientCounts(id).frozen) }
    }

    internal fun fixtureDefinition(id: String) = AnnouncementDefinition(
        id, 1, AnnouncementStatus.PUBLISHED, "Tiêu đề", "Title", "Nội dung", "Body",
        AdminAnnouncementCategory.GENERAL, 1, 50, 500, true, false, AdminAnnouncementAction.NONE,
        audience = AnnouncementAudience.ALL_REGISTERED, createdBy = UUID.randomUUID().toString(),
        createdAtEpochMillis = 1, updatedAtEpochMillis = 2, publishedAtEpochMillis = 2
    )
}
