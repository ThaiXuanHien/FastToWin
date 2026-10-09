package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.server.InMemoryNotificationRepository
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import com.hienthai.fastowin.server.admin.AuditEntry
import com.hienthai.fastowin.server.admin.PageRequest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AnnouncementRepositoryTest {
    @Test fun `coordinated memory publish hides partial admin and notification state from concurrent readers`() {
        val adminRepository = InMemoryAdminRepository()
        val notifications = InMemoryNotificationRepository(adminRepository)
        val id = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val snapshot = fixtureDefinition(id).notification("en", 100)
        val audit = AuditEntry(UUID.randomUUID().toString(), null, "ANNOUNCEMENT_TEST", id, null, null, null, null, 100)
        val insideCommit = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val writer = CompletableFuture.runAsync {
            runBlocking {
                notifications.withCopiedNotifications { copy, publishNotifications ->
                    copy[userId to snapshot.id] = InMemoryNotificationRepository.StoredNotification(snapshot)
                    adminRepository.coordinatedTransaction(publish = {
                        publishNotifications()
                        insideCommit.countDown()
                        check(releaseCommit.await(5, TimeUnit.SECONDS))
                    }) { appendAudit(audit) }
                }
            }
        }
        try {
            assertTrue(insideCommit.await(5, TimeUnit.SECONDS))
            val readersStarted = CountDownLatch(2)
            val adminReader = CompletableFuture.supplyAsync {
                readersStarted.countDown()
                runBlocking { adminRepository.transaction { audit(PageRequest()).items.size } }
            }
            val inboxReader = CompletableFuture.supplyAsync {
                readersStarted.countDown()
                runBlocking { notifications.loadNotifications(userId).size }
            }
            assertTrue(readersStarted.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)
            assertTrue(!adminReader.isDone && !inboxReader.isDone)
            releaseCommit.countDown()
            writer.get(5, TimeUnit.SECONDS)
            assertEquals(1, adminReader.get(5, TimeUnit.SECONDS))
            assertEquals(1, inboxReader.get(5, TimeUnit.SECONDS))
        } finally {
            releaseCommit.countDown()
            writer.get(5, TimeUnit.SECONDS)
        }
    }

    @Test fun `memory announcement success publishes audit with definition and notification`() = runTest {
        val adminRepository = InMemoryAdminRepository()
        val notifications = InMemoryNotificationRepository(adminRepository)
        val id = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        notifications.announcements.transaction {
            putDefinition(fixtureDefinition(id))
            putRecipient(AnnouncementRecipient(id, userId, storedAtEpochMillis = 100))
            this.notifications.create(userId, listOf(fixtureDefinition(id).notification("en", 100)))
            admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), null, "ANNOUNCEMENT_TEST", id, null, null, null, null, 100))
        }
        assertEquals(1, adminRepository.transaction { audit(PageRequest()).items.size })
        assertEquals(id, notifications.announcements.transaction { definition(id)?.id })
        assertEquals(id, notifications.loadNotifications(userId).single().announcement?.id)
    }
    @Test fun `cancelled memory transaction leaves no copied state`() = runTest {
        val adminRepository = InMemoryAdminRepository()
        val notifications = InMemoryNotificationRepository(adminRepository)
        val id = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val work = async(Dispatchers.Default) {
            val job = currentCoroutineContext().job
            notifications.announcements.transaction {
                putDefinition(fixtureDefinition(id))
                putRecipient(AnnouncementRecipient(id, userId, storedAtEpochMillis = 100))
                this.notifications.create(userId, listOf(fixtureDefinition(id).notification("en", 100)))
                admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), null, "ANNOUNCEMENT_TEST", id, null, null, null, null, 100))
                job.cancel()
            }
        }
        try { work.await() } catch (_: CancellationException) { }
        assertTrue(notifications.loadNotifications(userId).isEmpty())
        notifications.announcements.transaction { assertNull(definition(id)) }
        adminRepository.transaction { assertTrue(audit(PageRequest()).items.isEmpty()) }
    }
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
