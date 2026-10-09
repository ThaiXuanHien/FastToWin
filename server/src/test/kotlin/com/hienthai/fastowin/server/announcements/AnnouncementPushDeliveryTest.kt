package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.server.AnnouncementPushStatus
import com.hienthai.fastowin.server.InMemoryNotificationRepository
import com.hienthai.fastowin.server.LocalizedPushContent
import com.hienthai.fastowin.server.PushDeliveryStatus
import com.hienthai.fastowin.server.PushNotificationService
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import com.hienthai.fastowin.server.admin.UserDetail
import com.hienthai.fastowin.server.admin.PageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnnouncementPushDeliveryTest {
    private class Fixture {
        var now = 1_800_000_000_000L
        val admins = InMemoryAdminRepository()
        val notifications = InMemoryNotificationRepository(admins)
        val userId = UUID.randomUUID().toString()
        val id = UUID.randomUUID().toString()
        suspend fun setup(consent: Boolean = true, token: String? = "fake-token", pushEnabled: Boolean = true,
                          starts: Long = now - 1, ends: Long = now + 10_000) {
            admins.seedUser(UserDetail(userId, "Player", "p@example.com", "REGISTERED", "ACTIVE", now,
                1, 0, 0, 0, 1000, null, null, null))
            if (token != null) admins.seedAnnouncementPushTarget(userId, token, "vi")
            val value = AnnouncementRepositoryTest().fixtureDefinition(id).copy(
                startsAtEpochMillis = starts, endsAtEpochMillis = ends, pushEnabled = pushEnabled,
                bodyVi = "😀" + "a".repeat(300))
            notifications.announcements.transaction {
                putDefinition(value)
                putRecipient(AnnouncementRecipient(id, userId))
                putPreference(AnnouncementPreference(userId, 1, adminEnabled = consent))
            }
            AnnouncementService(notifications.announcements, null, nowMillis = { now }).deliverDueBatch()
        }
    }
    private fun fake(configured: Boolean = true, send: suspend (String, String) -> AnnouncementPushStatus): PushNotificationService =
        object : PushNotificationService {
            override val announcementConfigured = configured
            override suspend fun sendNotification(fcmToken: String, language: AppLanguage, content: LocalizedPushContent,
                                                  destinationPath: String) = PushDeliveryStatus.FAILED
            override suspend fun sendAnnouncement(fcmToken: String, languageTag: String, titleVi: String, titleEn: String,
                                                  bodyVi: String, bodyEn: String, destinationPath: String): AnnouncementPushStatus = send(languageTag, bodyVi)
        }

    @Test fun `committed in-app copy precedes one accepted push attempt`() = runTest {
        val now = 1_800_000_000_000L
        val admins = InMemoryAdminRepository()
        val userId = UUID.randomUUID().toString()
        admins.seedUser(UserDetail(userId, "Player", "p@example.com", "REGISTERED", "ACTIVE", now, 1, 0, 0, 0, 1000, null, null, null))
        admins.seedAnnouncementPushTarget(userId, "fake-token", "vi")
        val notifications = InMemoryNotificationRepository(admins)
        val id = UUID.randomUUID().toString()
        val value = AnnouncementRepositoryTest().fixtureDefinition(id).copy(startsAtEpochMillis = now - 1,
            endsAtEpochMillis = now + 10_000, pushEnabled = true)
        notifications.announcements.transaction {
            putDefinition(value)
            putRecipient(AnnouncementRecipient(id, userId))
            putPreference(AnnouncementPreference(userId, 1, adminEnabled = true))
        }
        assertEquals(1, AnnouncementService(notifications.announcements, null, nowMillis = { now }).deliverDueBatch())
        var calls = 0
        val fake = object : PushNotificationService {
            override val announcementConfigured = true
            override suspend fun sendNotification(fcmToken: String, language: AppLanguage, content: LocalizedPushContent,
                                                  destinationPath: String) = PushDeliveryStatus.FAILED
            override suspend fun sendAnnouncement(fcmToken: String, languageTag: String, titleVi: String, titleEn: String,
                                                  bodyVi: String, bodyEn: String, destinationPath: String): AnnouncementPushStatus {
                calls++
                assertEquals(1, notifications.loadNotifications(userId).size)
                return AnnouncementPushStatus.ACCEPTED
            }
        }
        val delivery = AnnouncementPushDelivery(notifications.announcements, fake, { now })
        assertEquals(1, delivery.deliverDueBatch())
        assertEquals(0, delivery.deliverDueBatch())
        assertEquals(1, calls)
        notifications.announcements.transaction {
            assertEquals(AnnouncementPushOutcome.ACCEPTED, pushAttempt(id, userId)?.outcome)
        }
    }

    @Test fun `definitive failure and ambiguous exception each remain one attempt`() = runTest {
        for (ambiguous in listOf(false, true)) {
            val f = Fixture(); f.setup()
            var calls = 0
            val delivery = AnnouncementPushDelivery(f.notifications.announcements, fake { language, body ->
                calls++
                assertEquals("vi", language)
                assertEquals(240, body.length)
                assertTrue(body.startsWith("😀"))
                if (ambiguous) throw IllegalStateException("provider result unavailable")
                AnnouncementPushStatus.FAILED
            }, { f.now })
            assertEquals(1, delivery.deliverDueBatch())
            assertEquals(0, delivery.deliverDueBatch())
            assertEquals(1, calls)
            f.notifications.announcements.transaction {
                assertEquals(if (ambiguous) AnnouncementPushOutcome.UNKNOWN else AnnouncementPushOutcome.FAILED,
                    pushAttempt(f.id, f.userId)?.outcome)
            }
            assertEquals(if (ambiguous) 1 else 0, f.admins.transaction { audit(PageRequest()).items.count { it.action == "ANNOUNCEMENT_PUSH_UNKNOWN" } })
        }
    }

    @Test fun `timeout and interruption become unknown without automatic resend`() = runTest {
        val timed = Fixture(); timed.setup()
        var calls = 0
        val timeout = AnnouncementPushDelivery(timed.notifications.announcements, fake { _, _ ->
            calls++; delay(30_000); AnnouncementPushStatus.ACCEPTED
        }, { timed.now })
        assertEquals(1, timeout.deliverDueBatch())
        assertEquals(0, timeout.deliverDueBatch())
        assertEquals(1, calls)
        timed.notifications.announcements.transaction { assertEquals(AnnouncementPushOutcome.UNKNOWN, pushAttempt(timed.id, timed.userId)?.outcome) }

        val interrupted = Fixture(); interrupted.setup()
        val delivery = AnnouncementPushDelivery(interrupted.notifications.announcements, fake { _, _ ->
            throw CancellationException("interrupted")
        }, { interrupted.now })
        assertFailsWith<CancellationException> { delivery.deliverDueBatch() }
        assertEquals(0, delivery.deliverDueBatch())
        interrupted.notifications.announcements.transaction {
            assertEquals(AnnouncementPushOutcome.UNKNOWN, pushAttempt(interrupted.id, interrupted.userId)?.outcome)
        }
    }

    @Test fun `stale committed claim recovers with system audit and never sends`() = runTest {
        val f = Fixture(); f.setup()
        f.notifications.announcements.transaction {
            putPushAttempt(AnnouncementPushAttempt(f.id, f.userId, UUID.randomUUID().toString(),
                AnnouncementPushOutcome.ATTEMPTING, f.now - 120_001))
        }
        var calls = 0
        val delivery = AnnouncementPushDelivery(f.notifications.announcements, fake { _, _ -> calls++; AnnouncementPushStatus.ACCEPTED }, { f.now })
        assertEquals(1, delivery.recoverStale())
        assertEquals(0, delivery.deliverDueBatch())
        assertEquals(0, calls)
        f.notifications.announcements.transaction { assertEquals(AnnouncementPushOutcome.UNKNOWN, pushAttempt(f.id, f.userId)?.outcome) }
        assertEquals(1, f.admins.transaction { audit(PageRequest()).items.count { it.action == "ANNOUNCEMENT_PUSH_UNKNOWN" } })
    }

    @Test fun `disabled opt-out no-token unavailable and expired never call provider`() = runTest {
        val cases = listOf(
            Triple(false, true, AnnouncementPushOutcome.DISABLED),
            Triple(true, false, AnnouncementPushOutcome.OPTED_OUT)
        )
        for ((enabled, consent, expected) in cases) {
            val f = Fixture(); f.setup(consent = consent, pushEnabled = enabled)
            val delivery = AnnouncementPushDelivery(f.notifications.announcements, fake { _, _ -> error("must not send") }, { f.now })
            assertEquals(0, delivery.deliverDueBatch())
            f.notifications.announcements.transaction { assertEquals(expected, pushAttempt(f.id, f.userId)?.outcome) }
        }
        val noToken = Fixture(); noToken.setup(token = null)
        AnnouncementPushDelivery(noToken.notifications.announcements, fake { _, _ -> error("must not send") }, { noToken.now }).deliverDueBatch()
        noToken.notifications.announcements.transaction { assertEquals(AnnouncementPushOutcome.NO_TOKEN, pushAttempt(noToken.id, noToken.userId)?.outcome) }

        val unavailable = Fixture(); unavailable.setup()
        AnnouncementPushDelivery(unavailable.notifications.announcements, fake(false) { _, _ -> error("must not send") }, { unavailable.now }).deliverDueBatch()
        unavailable.notifications.announcements.transaction { assertEquals(AnnouncementPushOutcome.UNAVAILABLE, pushAttempt(unavailable.id, unavailable.userId)?.outcome) }

        val expired = Fixture(); expired.setup(starts = 1_800_000_000_000L - 10_000, ends = 1_800_000_000_000L - 1)
        AnnouncementPushDelivery(expired.notifications.announcements, fake { _, _ -> error("must not send") }, { expired.now }).deliverDueBatch()
        expired.notifications.announcements.transaction { assertEquals(AnnouncementPushOutcome.EXPIRED, pushAttempt(expired.id, expired.userId)?.outcome) }
    }
}
