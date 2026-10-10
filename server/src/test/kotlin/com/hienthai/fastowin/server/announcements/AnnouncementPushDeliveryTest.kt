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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AnnouncementPushDeliveryTest {
    private class Fixture {
        var now = 1_800_000_000_000L
        val admins = InMemoryAdminRepository()
        val notifications = InMemoryNotificationRepository(admins)
        val userId = UUID.randomUUID().toString()
        val id = UUID.randomUUID().toString()
        suspend fun setup(consent: Boolean = true, token: String? = "fake-token", pushEnabled: Boolean = true,
                          starts: Long = now - 1, ends: Long = now + 10_000, extraUserId: String? = null) {
            admins.seedUser(UserDetail(userId, "Player", "p@example.com", "REGISTERED", "ACTIVE", now,
                1, 0, 0, 0, 1000, null, null, null))
            if (token != null) admins.seedAnnouncementPushTarget(userId, token, "vi")
            val value = AnnouncementRepositoryTest().fixtureDefinition(id).copy(
                startsAtEpochMillis = starts, endsAtEpochMillis = ends, pushEnabled = pushEnabled,
                bodyVi = "😀" + "a".repeat(300))
            notifications.announcements.transaction {
                putDefinition(value)
                putRecipient(AnnouncementRecipient(id, userId))
                extraUserId?.let { putRecipient(AnnouncementRecipient(id, it, storedAtEpochMillis = now)) }
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
            assertEquals(1, delivery.deliverDueBatch())
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
    @Test fun `round1 skipped outcomes consume batch capacity`() = runTest {
        val second = UUID.randomUUID().toString()
        val f = Fixture(); f.setup(pushEnabled = false, extraUserId = second)
        val delivery = AnnouncementPushDelivery(f.notifications.announcements, fake { _, _ -> error("must not send") }, { f.now })
        delivery.deliverDueBatch(1)
        f.notifications.announcements.transaction {
            assertEquals(1, listOf(f.userId, second).count { pushAttempt(f.id, it) != null })
        }
        delivery.deliverDueBatch(1)
        f.notifications.announcements.transaction {
            assertEquals(2, listOf(f.userId, second).count { pushAttempt(f.id, it) != null })
        }
    }

    @Test fun `round1 pending sends are not preclaimed or sent after stale recovery`() = runTest {
        val second = UUID.randomUUID().toString()
        val f = Fixture(); f.setup(ends = f.now + 500_000, extraUserId = second)
        f.admins.seedUser(UserDetail(second, "Second", "second@example.com", "REGISTERED", "ACTIVE", f.now,
            1, 0, 0, 0, 1000, null, null, null))
        f.admins.seedAnnouncementPushTarget(second, "second-token", "en")
        f.notifications.announcements.transaction {
            putPreference(AnnouncementPreference(second, 1, adminEnabled = true))
        }
        var calls = 0
        lateinit var delivery: AnnouncementPushDelivery
        delivery = AnnouncementPushDelivery(f.notifications.announcements, fake { _, _ ->
            calls++
            if (calls == 1) {
                f.notifications.announcements.transaction {
                    assertEquals(1, listOf(f.userId, second).count { pushAttempt(f.id, it) != null }, "only in-flight send may be claimed")
                }
                f.now += 120_001
                assertEquals(1, delivery.recoverStale())
            }
            AnnouncementPushStatus.ACCEPTED
        }, { f.now })
        delivery.deliverDueBatch(2)
        assertEquals(2, calls)
        f.notifications.announcements.transaction {
            val outcomes = listOf(f.userId, second).map { pushAttempt(f.id, it)!!.outcome }
            assertEquals(1, outcomes.count { it == AnnouncementPushOutcome.UNKNOWN })
            assertEquals(1, outcomes.count { it == AnnouncementPushOutcome.ACCEPTED })
        }
    }


    @Test fun `round1 recovered claim cannot start external IO after commit pause`() = runTest {
        val f = Fixture(); f.setup(ends = f.now + 500_000)
        val underlying = f.notifications.announcements
        var externalCalls = 0
        val provider = fake { _, _ -> externalCalls++; AnnouncementPushStatus.ACCEPTED }
        var recovered = false
        val paused = object : AnnouncementRepository {
            override suspend fun <T> transaction(admin: Boolean, block: AnnouncementTransaction.() -> T): T {
                val result = underlying.transaction(admin, block)
                if (!recovered && underlying.transaction { pushAttempt(f.id, f.userId)?.outcome } == AnnouncementPushOutcome.ATTEMPTING) {
                    recovered = true
                    f.now += 120_001
                    assertEquals(1, AnnouncementPushDelivery(underlying, provider, { f.now }).recoverStale())
                }
                return result
            }
        }
        assertEquals(1, AnnouncementPushDelivery(paused, provider, { f.now }).deliverDueBatch())
        assertTrue(recovered)
        assertEquals(0, externalCalls, "recovered UNKNOWN must not call provider")
        underlying.transaction { assertEquals(AnnouncementPushOutcome.UNKNOWN, pushAttempt(f.id, f.userId)?.outcome) }
    }

    @Test fun `round1 materialization runs while push is awaiting deadline`() = runTest {
        val f = Fixture(); f.setup(ends = f.now + 500_000)
        val futureId = UUID.randomUUID().toString()
        f.notifications.announcements.transaction {
            putDefinition(AnnouncementRepositoryTest().fixtureDefinition(futureId).copy(
                startsAtEpochMillis = f.now + 5_000, endsAtEpochMillis = f.now + 500_000))
            putRecipient(AnnouncementRecipient(futureId, f.userId))
        }
        val delivery = AnnouncementPushDelivery(f.notifications.announcements, fake { _, _ ->
            delay(30_000); AnnouncementPushStatus.ACCEPTED
        }, { f.now })
        backgroundScope.startAnnouncementDelivery(AnnouncementService(f.notifications.announcements, null, nowMillis = { f.now }), delivery)
        testScheduler.runCurrent()
        f.now += 5_000
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        f.notifications.announcements.transaction {
            assertEquals(f.now, recipient(futureId, f.userId)!!.storedAtEpochMillis)
        }
    }


    @Test fun `round1 Firebase never completed future reaches twenty second unknown deadline`() = runTest {
        val f = Fixture(); f.setup()
        val future = com.google.api.core.SettableApiFuture.create<String>()
        val provider = com.hienthai.fastowin.server.FirebasePushNotificationService(null) { future }
        assertEquals(1, AnnouncementPushDelivery(f.notifications.announcements, provider, { f.now }).deliverDueBatch())
        assertEquals(20_000, testScheduler.currentTime)
        assertTrue(future.isCancelled)
        f.notifications.announcements.transaction {
            assertEquals(AnnouncementPushOutcome.UNKNOWN, pushAttempt(f.id, f.userId)?.outcome)
        }
        assertEquals(0, AnnouncementPushDelivery(f.notifications.announcements, provider, { f.now }).deliverDueBatch())
    }

    @Test fun `round1 Firebase pending future times out and settles unknown without completion`() = runTest {
        val f = Fixture(); f.setup()
        val future = com.google.api.core.SettableApiFuture.create<String>()
        val entered = java.util.concurrent.CountDownLatch(1)
        val provider = com.hienthai.fastowin.server.FirebasePushNotificationService(null) { entered.countDown(); future }
        val result = java.util.concurrent.CompletableFuture.supplyAsync {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(100) {
                    AnnouncementPushDelivery(f.notifications.announcements, provider, { f.now }).deliverDueBatch()
                }
            }
        }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            try { result.get(2, java.util.concurrent.TimeUnit.SECONDS) }
            catch (_: java.util.concurrent.TimeoutException) { kotlin.test.fail("deadline blocked on unfinished Firebase future") }
            f.notifications.announcements.transaction {
                assertEquals(AnnouncementPushOutcome.UNKNOWN, pushAttempt(f.id, f.userId)?.outcome)
            }
        } finally { future.set("controlled-release"); result.get(5, java.util.concurrent.TimeUnit.SECONDS) }
    }

}
