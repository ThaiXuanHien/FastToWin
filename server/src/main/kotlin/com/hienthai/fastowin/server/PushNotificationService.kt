package com.hienthai.fastowin.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.WebpushConfig
import com.google.firebase.messaging.WebpushFcmOptions
import com.google.firebase.messaging.WebpushNotification
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.resolveAppLanguage
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
        language: AppLanguage,
        content: LocalizedPushContent,
        destinationPath: String = "/notifications"
    ): PushDeliveryStatus
}

data class LocalizedPushContent(
    val titleKey: TextKey,
    val bodyKey: TextKey,
    val titleArguments: Map<String, String> = emptyMap(),
    val bodyArguments: Map<String, String> = emptyMap()
)

internal data class RenderedPushNotification(
    val title: String,
    val body: String,
    val languageTag: String
)

internal fun resolveNotificationLanguage(languageTag: String?): AppLanguage =
    languageTag
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { resolveAppLanguage(savedCode = "", systemTags = listOf(it)) }
        ?: AppLanguage.ENGLISH

internal fun renderPushNotification(
    language: AppLanguage,
    content: LocalizedPushContent
): RenderedPushNotification {
    val localization = LocalizationService(language)
    return RenderedPushNotification(
        title = localization.text(content.titleKey, content.titleArguments),
        body = localization.text(content.bodyKey, content.bodyArguments),
        languageTag = language.languageTag
    )
}

enum class PushDeliveryStatus {
    SENT,
    INVALID_TOKEN,
    FAILED
}

object NoOpPushNotificationService : PushNotificationService {
    override suspend fun sendNotification(
        fcmToken: String,
        language: AppLanguage,
        content: LocalizedPushContent,
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
        language: AppLanguage,
        content: LocalizedPushContent,
        destinationPath: String
    ): PushDeliveryStatus {
        if (FirebaseApp.getApps().isEmpty() || fcmToken.isBlank()) {
            return PushDeliveryStatus.FAILED
        }
        val renderedContent = renderPushNotification(
            language = language,
            content = content
        )
        return withContext(Dispatchers.IO) {
            try {
                val destination = normalizePushDestination(destinationPath)
                val message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(
                        Notification.builder()
                            .setTitle(renderedContent.title)
                            .setBody(renderedContent.body)
                            .build()
                    )
                    .putData("destination", destination)
                    .setApnsConfig(
                        ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .setAps(Aps.builder().setSound("default").build())
                            .build()
                    )
                    .apply {
                        webPushConfig(renderedContent, destination)?.let { setWebpushConfig(it) }
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
        content: RenderedPushNotification,
        destinationPath: String
    ): WebpushConfig? {
        val baseUrl = webBaseUrl ?: return null
        val path = normalizePushDestination(destinationPath)
        return WebpushConfig.builder()
            .putHeader("Urgency", "high")
            .putData("destination", path)
            .setNotification(
                WebpushNotification.builder()
                    .setTitle(content.title)
                    .setBody(content.body)
                    .setIcon("$baseUrl/icons/icon-192.png")
                    .setBadge("$baseUrl/icons/icon-192.png")
                    .setLanguage(content.languageTag)
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

internal fun normalizePushDestination(destinationPath: String): String {
    val normalized = destinationPath.trim().substringBefore('?').substringBefore('#')
        .let { if (it.startsWith('/')) it else "/$it" }
    return normalized.takeIf { it.length in 2..256 } ?: "/notifications"
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
                language = target.language,
                content = LocalizedPushContent(
                    titleKey = TextKey.PushDailyCheckInTitle,
                    bodyKey = TextKey.PushDailyCheckInMessage
                ),
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
