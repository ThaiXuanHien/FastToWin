package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PushReminderServiceTest {
    @Test
    fun `daily reminder sends once after configured hour`() = runTest {
        val deliveredKeys = mutableSetOf<String>()
        val repository = reminderRepository(deliveredKeys)
        val sent = mutableListOf<RecordedPush>()
        val push = recordingPushService(sent)
        val service = DailyPushReminderService(
            playerProfileRepository = repository,
            pushNotificationService = push,
            zoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
            reminderHour = 19,
            clock = Clock.fixed(Instant.parse("2026-09-02T13:00:00Z"), ZoneId.of("UTC"))
        )

        assertEquals(1, service.sendDueReminders())
        assertEquals(0, service.sendDueReminders())
        assertEquals(listOf("/account/check-in"), sent.map(RecordedPush::destination))
        assertEquals(listOf(AppLanguage.ENGLISH), sent.map(RecordedPush::language))
        assertEquals(TextKey.PushDailyCheckInTitle, sent.single().titleKey)
        assertEquals(TextKey.PushDailyCheckInMessage, sent.single().bodyKey)
        assertEquals(setOf("daily-check-in:2026-09-02"), deliveredKeys)
    }

    @Test
    fun `daily reminder waits until configured hour`() = runTest {
        val sent = mutableListOf<RecordedPush>()
        val service = DailyPushReminderService(
            playerProfileRepository = reminderRepository(mutableSetOf()),
            pushNotificationService = recordingPushService(sent),
            zoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
            reminderHour = 19,
            clock = Clock.fixed(Instant.parse("2026-09-02T10:00:00Z"), ZoneId.of("UTC"))
        )

        assertEquals(0, service.sendDueReminders())
        assertEquals(0, sent.size)
    }

    @Test
    fun `daily reminder uses the Bangkok check-in date with a UTC delivery schedule`() = runTest {
        val checkInDate = LocalDate.of(2026, 9, 3)
        val playerLastCheckIn = checkInDate.minusDays(1)
        val datesQueried = mutableListOf<String>()
        val deliveredKeys = mutableSetOf<String>()
        val baseRepository = reminderRepository(deliveredKeys)
        val repository = object : PlayerProfileRepository by baseRepository {
            override suspend fun loadDailyPushReminderTargets(reminderDate: String, limit: Int): List<PushReminderTarget> {
                datesQueried += reminderDate
                if (playerLastCheckIn >= LocalDate.parse(reminderDate)) return emptyList()
                return baseRepository.loadDailyPushReminderTargets(reminderDate, limit)
            }
        }
        val sent = mutableListOf<RecordedPush>()
        val utc = ZoneId.of("UTC")

        suspend fun runAt(time: String): Int = DailyPushReminderService(
            repository, recordingPushService(sent), utc, 19, Clock.fixed(Instant.parse(time), utc)
        ).sendDueReminders()

        assertEquals(0, runAt("2026-09-02T18:59:00Z"))
        assertEquals(1, runAt("2026-09-02T19:00:00Z"))
        assertEquals(0, runAt("2026-09-02T20:00:00Z"))
        assertEquals(List(2) { checkInDate.toString() }, datesQueried)
        assertEquals(setOf("daily-check-in:$checkInDate"), deliveredKeys)
        assertEquals(1, sent.size)
    }

    @Test
    fun `daily reminder clears an invalid token without marking delivery`() = runTest {
        val deliveredKeys = mutableSetOf<String>()
        var clearedToken: String? = null
        val baseRepository = reminderRepository(deliveredKeys)
        val repository = object : PlayerProfileRepository by baseRepository {
            override suspend fun clearFcmToken(
                playerId: String,
                expectedToken: String
            ): Boolean {
                clearedToken = expectedToken
                return true
            }
        }
        val service = DailyPushReminderService(
            playerProfileRepository = repository,
            pushNotificationService = object : PushNotificationService {
                override suspend fun sendNotification(
                    fcmToken: String,
                    language: AppLanguage,
                    content: LocalizedPushContent,
                    destinationPath: String
                ): PushDeliveryStatus = PushDeliveryStatus.INVALID_TOKEN
            },
            zoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
            reminderHour = 19,
            clock = Clock.fixed(Instant.parse("2026-09-02T13:00:00Z"), ZoneId.of("UTC"))
        )

        assertEquals(0, service.sendDueReminders())
        assertEquals("token-1", clearedToken)
        assertFalse("daily-check-in:2026-09-02" in deliveredKeys)
    }

    @Test
    fun `daily reminders go out in batches of at most 500 and keep per-target outcomes`() = runTest {
        val targets = List(1_201) { PushReminderTarget("player-$it", if (it == 600) "stale-token" else "token-$it", AppLanguage.ENGLISH) }
        val delivered = mutableListOf<String>()
        val cleared = mutableListOf<String>()
        val repository = object : PlayerProfileRepository by reminderRepository(mutableSetOf()) {
            override suspend fun loadDailyPushReminderTargets(reminderDate: String, limit: Int) =
                targets.filter { it.playerId !in delivered && it.fcmToken !in cleared }.take(limit)
            override suspend fun markPushReminderDelivered(playerId: String, reminderKey: String) = delivered.add(playerId)
            override suspend fun clearFcmToken(playerId: String, expectedToken: String) = cleared.add(expectedToken)
        }
        val batchSizes = mutableListOf<Int>()
        val push = object : PushNotificationService {
            override suspend fun sendNotification(fcmToken: String, language: AppLanguage, content: LocalizedPushContent, destinationPath: String) =
                error("batched delivery must not fall back to single sends")
            override suspend fun sendNotifications(requests: List<PushRequest>): List<PushDeliveryStatus> {
                batchSizes += requests.size
                return requests.map { if (it.fcmToken == "stale-token") PushDeliveryStatus.INVALID_TOKEN else PushDeliveryStatus.SENT }
            }
        }
        val service = DailyPushReminderService(repository, push, ZoneId.of("Asia/Ho_Chi_Minh"), 19,
            Clock.fixed(Instant.parse("2026-09-02T13:00:00Z"), ZoneId.of("UTC")))

        assertEquals(1_200, service.sendDueReminders())
        assertEquals(listOf(500, 500, 201), batchSizes)
        assertEquals(listOf("stale-token"), cleared)
        assertFalse("player-600" in delivered)
    }

    @Test
    fun `a page of failing tokens ends the run instead of looping`() = runTest {
        var loads = 0
        val repository = object : PlayerProfileRepository by reminderRepository(mutableSetOf()) {
            override suspend fun loadDailyPushReminderTargets(reminderDate: String, limit: Int): List<PushReminderTarget> {
                loads++
                return List(limit) { PushReminderTarget("player-$it", "token-$it", AppLanguage.ENGLISH) }
            }
        }
        val push = object : PushNotificationService {
            override suspend fun sendNotification(fcmToken: String, language: AppLanguage, content: LocalizedPushContent, destinationPath: String) =
                PushDeliveryStatus.FAILED
        }
        val service = DailyPushReminderService(repository, push, ZoneId.of("Asia/Ho_Chi_Minh"), 19,
            Clock.fixed(Instant.parse("2026-09-02T13:00:00Z"), ZoneId.of("UTC")))

        assertEquals(0, service.sendDueReminders())
        assertEquals(1, loads)
    }

    private fun reminderRepository(deliveredKeys: MutableSet<String>) = object : PlayerProfileRepository {
        override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = null

        override suspend fun updateProfile(
            playerId: String,
            displayName: String,
            avatarId: String?
        ): Boolean = false

        override suspend fun loadDailyPushReminderTargets(
            reminderDate: String,
            limit: Int
        ): List<PushReminderTarget> = if ("daily-check-in:$reminderDate" in deliveredKeys) {
            emptyList()
        } else {
            listOf(PushReminderTarget("player-1", "token-1", AppLanguage.ENGLISH))
        }

        override suspend fun markPushReminderDelivered(
            playerId: String,
            reminderKey: String
        ): Boolean = deliveredKeys.add(reminderKey)
    }

    private fun recordingPushService(sent: MutableList<RecordedPush>) = object : PushNotificationService {
        override suspend fun sendNotification(
            fcmToken: String,
            language: AppLanguage,
            content: LocalizedPushContent,
            destinationPath: String
        ): PushDeliveryStatus {
            sent += RecordedPush(language, content.titleKey, content.bodyKey, destinationPath)
            return PushDeliveryStatus.SENT
        }
    }

    private data class RecordedPush(
        val language: AppLanguage,
        val titleKey: TextKey,
        val bodyKey: TextKey,
        val destination: String
    )
}
