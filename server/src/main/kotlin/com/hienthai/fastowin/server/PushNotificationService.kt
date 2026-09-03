package com.hienthai.fastowin.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.WebpushConfig
import com.google.firebase.messaging.WebpushFcmOptions
import com.google.firebase.messaging.WebpushNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.time.Clock
import java.time.ZoneId

interface PushNotificationService {
    suspend fun sendNotification(
        fcmToken: String,
        title: String,
        body: String,
        destinationPath: String = "/notifications"
    ): PushDeliveryStatus
}

enum class PushDeliveryStatus {
    SENT,
    INVALID_TOKEN,
    FAILED
}

object NoOpPushNotificationService : PushNotificationService {
    override suspend fun sendNotification(
        fcmToken: String,
        title: String,
        body: String,
        destinationPath: String
    ): PushDeliveryStatus = PushDeliveryStatus.FAILED
}

class FirebasePushNotificationService(
    private val webBaseUrl: String? = configuredWebBaseUrl()
) : PushNotificationService {
    init {
        if (FirebaseApp.getApps().isEmpty()) {
            runCatching {
                val credentialFile = configuredFirebaseCredentialFile()
                FileInputStream(credentialFile).use { serviceAccount ->
                    val options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build()
                    FirebaseApp.initializeApp(options)
                }
                println("Firebase Admin initialized.")
            }.onFailure { error ->
                System.err.println(
                    "Firebase Admin is unavailable; push notifications are disabled: ${error.message}"
                )
            }
        }
    }

    override suspend fun sendNotification(
        fcmToken: String,
        title: String,
        body: String,
        destinationPath: String
    ): PushDeliveryStatus {
        if (FirebaseApp.getApps().isEmpty() || fcmToken.isBlank()) {
            return PushDeliveryStatus.FAILED
        }
        return withContext(Dispatchers.IO) {
            try {
                val message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .apply {
                        webPushConfig(title, body, destinationPath)?.let { setWebpushConfig(it) }
                    }
                    .build()
                FirebaseMessaging.getInstance().send(message)
                PushDeliveryStatus.SENT
            } catch (error: FirebaseMessagingException) {
                System.err.println("Could not send push notification: ${error.message}")
                if (error.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {
                    PushDeliveryStatus.INVALID_TOKEN
                } else {
                    PushDeliveryStatus.FAILED
                }
            } catch (error: Exception) {
                System.err.println("Could not send push notification: ${error.message}")
                PushDeliveryStatus.FAILED
            }
        }
    }

    private fun webPushConfig(
        title: String,
        body: String,
        destinationPath: String
    ): WebpushConfig? {
        val baseUrl = webBaseUrl ?: return null
        val path = destinationPath.trim().let { if (it.startsWith('/')) it else "/$it" }
        return WebpushConfig.builder()
            .putHeader("Urgency", "high")
            .putData("destination", path)
            .setNotification(
                WebpushNotification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .setIcon("$baseUrl/icons/icon-192.png")
                    .setBadge("$baseUrl/icons/icon-192.png")
                    .setLanguage("vi")
                    .build()
            )
            .setFcmOptions(WebpushFcmOptions.withLink("$baseUrl$path"))
            .build()
    }

    private companion object {
        fun configuredWebBaseUrl(): String? {
            val value = System.getenv("FASTTOWIN_WEB_BASE_URL")
            ?.trim()
            ?.trimEnd('/')
                ?.takeIf(String::isNotEmpty)
                ?: return null
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            val isSecure = uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            val isLocalDev = uri.scheme.equals("http", ignoreCase = true) &&
                uri.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")
            return value.takeIf { isSecure || isLocalDev }
        }
    }
}

private fun configuredFirebaseCredentialFile(): File {
    val configuredCredential = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (configuredCredential != null) return File(configuredCredential)

    return sequenceOf(
        File("firebase-adminsdk.json"),
        File("server/firebase-adminsdk.json")
    ).firstOrNull(File::isFile) ?: File("firebase-adminsdk.json")
}

fun interface PushReminderService {
    suspend fun sendDueReminders(): Int
}

object NoOpPushReminderService : PushReminderService {
    override suspend fun sendDueReminders(): Int = 0
}

class DailyPushReminderService(
    private val playerProfileRepository: PlayerProfileRepository,
    private val pushNotificationService: PushNotificationService,
    private val zoneId: ZoneId = configuredPushZone(),
    private val reminderHour: Int = configuredReminderHour(),
    private val clock: Clock = Clock.systemUTC()
) : PushReminderService {
    override suspend fun sendDueReminders(): Int {
        val localNow = clock.instant().atZone(zoneId)
        if (localNow.hour < reminderHour) return 0

        val reminderDate = localNow.toLocalDate()
        val reminderKey = "daily-check-in:$reminderDate"
        var delivered = 0
        playerProfileRepository.loadDailyPushReminderTargets(reminderDate.toString()).forEach { target ->
            val deliveryStatus = pushNotificationService.sendNotification(
                fcmToken = target.fcmToken,
                title = "Đừng quên điểm danh",
                body = "Vào nhận Vàng, XP và giữ chuỗi chuyên cần hôm nay nhé!",
                destinationPath = "/account/check-in"
            )
            when (deliveryStatus) {
                PushDeliveryStatus.SENT -> {
                    if (playerProfileRepository.markPushReminderDelivered(target.playerId, reminderKey)) {
                        delivered++
                    }
                }
                PushDeliveryStatus.INVALID_TOKEN -> {
                    playerProfileRepository.clearFcmToken(target.playerId, target.fcmToken)
                }
                PushDeliveryStatus.FAILED -> Unit
            }
        }
        return delivered
    }

    private companion object {
        fun configuredPushZone(): ZoneId = runCatching {
            ZoneId.of(System.getenv("FASTTOWIN_PUSH_ZONE")?.trim().orEmpty())
        }.getOrDefault(ZoneId.of("Asia/Ho_Chi_Minh"))

        fun configuredReminderHour(): Int = System.getenv("FASTTOWIN_DAILY_PUSH_HOUR")
            ?.toIntOrNull()
            ?.coerceIn(0, 23)
            ?: 19
    }
}
