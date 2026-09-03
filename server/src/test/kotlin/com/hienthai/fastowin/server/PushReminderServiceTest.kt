package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PushReminderServiceTest {
    @Test
    fun `daily reminder sends once after configured hour`() = runTest {
        val deliveredKeys = mutableSetOf<String>()
        val repository = reminderRepository(deliveredKeys)
        val sentDestinations = mutableListOf<String>()
        val push = recordingPushService(sentDestinations)
        val service = DailyPushReminderService(
            playerProfileRepository = repository,
            pushNotificationService = push,
            zoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
            reminderHour = 19,
            clock = Clock.fixed(Instant.parse("2026-09-02T13:00:00Z"), ZoneId.of("UTC"))
        )

        assertEquals(1, service.sendDueReminders())
        assertEquals(0, service.sendDueReminders())
        assertEquals(listOf("/account/check-in"), sentDestinations)
        assertEquals(setOf("daily-check-in:2026-09-02"), deliveredKeys)
    }

    @Test
    fun `daily reminder waits until configured hour`() = runTest {
        val sentDestinations = mutableListOf<String>()
        val service = DailyPushReminderService(
            playerProfileRepository = reminderRepository(mutableSetOf()),
            pushNotificationService = recordingPushService(sentDestinations),
            zoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
            reminderHour = 19,
            clock = Clock.fixed(Instant.parse("2026-09-02T10:00:00Z"), ZoneId.of("UTC"))
        )

        assertEquals(0, service.sendDueReminders())
        assertEquals(0, sentDestinations.size)
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
                    title: String,
                    body: String,
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
            listOf(PushReminderTarget("player-1", "token-1"))
        }

        override suspend fun markPushReminderDelivered(
            playerId: String,
            reminderKey: String
        ): Boolean = deliveredKeys.add(reminderKey)
    }

    private fun recordingPushService(destinations: MutableList<String>) = object : PushNotificationService {
        override suspend fun sendNotification(
            fcmToken: String,
            title: String,
            body: String,
            destinationPath: String
        ): PushDeliveryStatus {
            destinations += destinationPath
            return PushDeliveryStatus.SENT
        }
    }
}
