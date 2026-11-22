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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.ZoneId

interface PushNotificationService {
    suspend fun sendNotification(
        fcmToken: String,
        language: AppLanguage,
        content: LocalizedPushContent,
        destinationPath: String = "/notifications"
    ): PushDeliveryStatus

    /**
     * SCL-02: sends up to [MAX_PUSH_BATCH] notifications and returns one status per request,
     * in order. The default sends one by one; Firebase sends a whole batch in one call.
     */
    suspend fun sendNotifications(requests: List<PushRequest>): List<PushDeliveryStatus> =
        requests.map { sendNotification(it.fcmToken, it.language, it.content, it.destinationPath) }

    val announcementConfigured: Boolean get() = false
    suspend fun sendAnnouncement(
        fcmToken: String, languageTag: String, titleVi: String, titleEn: String,
        bodyVi: String, bodyEn: String, destinationPath: String = "/notifications"
    ): AnnouncementPushStatus = AnnouncementPushStatus.UNKNOWN
}

enum class AnnouncementPushStatus { ACCEPTED, FAILED, UNKNOWN }

data class PushRequest(
    val fcmToken: String,
    val language: AppLanguage,
    val content: LocalizedPushContent,
    val destinationPath: String = "/notifications"
)

/** Firebase accepts at most 500 messages per sendEach call. */
const val MAX_PUSH_BATCH = 500

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
        ?.let { resolveAppLanguage(savedCode = "system", systemTags = listOf(it)) }
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

class FirebasePushNotificationService internal constructor(
    private val webBaseUrl: String?,
    private val announcementTransport: ((Message) -> com.google.api.core.ApiFuture<String>)?
) : PushNotificationService {
    constructor(webBaseUrl: String? = configuredWebBaseUrl()) : this(webBaseUrl, null)
    override val announcementConfigured: Boolean get() = announcementTransport != null || FirebaseApp.getApps().isNotEmpty()

    override suspend fun sendAnnouncement(
        fcmToken: String, languageTag: String, titleVi: String, titleEn: String,
        bodyVi: String, bodyEn: String, destinationPath: String
    ): AnnouncementPushStatus {
        if (!announcementConfigured || fcmToken.isBlank()) return AnnouncementPushStatus.FAILED
        val vietnamese = languageTag.lowercase().startsWith("vi")
        val rendered = RenderedPushNotification(
            if (vietnamese) titleVi else titleEn,
            if (vietnamese) bodyVi else bodyEn,
            if (vietnamese) "vi" else "en"
        )
        return try {
            val destination = normalizePushDestination(destinationPath)
            val message = Message.builder().setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(rendered.title).setBody(rendered.body).build())
                .putData("destination", destination)
                .setApnsConfig(ApnsConfig.builder().putHeader("apns-priority", "10")
                    .setAps(Aps.builder().setSound("default").build()).build())
                .apply { webPushConfig(rendered, destination)?.let { setWebpushConfig(it) } }
                .build()
            (announcementTransport?.invoke(message) ?: FirebaseMessaging.getInstance().sendAsync(message)).awaitAnnouncementResponse()
            AnnouncementPushStatus.ACCEPTED
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: FirebaseMessagingException) {
            when (error.messagingErrorCode) {
                MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT -> AnnouncementPushStatus.FAILED
                else -> AnnouncementPushStatus.UNKNOWN
            }
        } catch (_: Exception) { AnnouncementPushStatus.UNKNOWN }
    }
    init {
        if (announcementTransport == null && FirebaseApp.getApps().isEmpty()) {
            runCatching {
                configuredFirebaseCredentialStream().use { serviceAccount ->
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
        return withContext(Dispatchers.IO) {
            try {
                FirebaseMessaging.getInstance().send(message(PushRequest(fcmToken, language, content, destinationPath)))
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

    override suspend fun sendNotifications(requests: List<PushRequest>): List<PushDeliveryStatus> {
        require(requests.size <= MAX_PUSH_BATCH) { "At most $MAX_PUSH_BATCH push messages per batch." }
        if (requests.isEmpty()) return emptyList()
        if (FirebaseApp.getApps().isEmpty()) return requests.map { PushDeliveryStatus.FAILED }
        val sendable = requests.withIndex().filter { it.value.fcmToken.isNotBlank() }
        val statuses = MutableList(requests.size) { PushDeliveryStatus.FAILED }
        if (sendable.isEmpty()) return statuses
        return withContext(Dispatchers.IO) {
            try {
                val responses = FirebaseMessaging.getInstance().sendEach(sendable.map { message(it.value) }).responses
                responses.forEachIndexed { position, response ->
                    statuses[sendable[position].index] = when {
                        response.isSuccessful -> PushDeliveryStatus.SENT
                        response.exception?.messagingErrorCode == MessagingErrorCode.UNREGISTERED -> PushDeliveryStatus.INVALID_TOKEN
                        else -> PushDeliveryStatus.FAILED
                    }
                }
                statuses
            } catch (error: FirebaseMessagingException) {
                System.err.println("Could not send push batch: ${error.message}")
                statuses
            } catch (error: Exception) {
                System.err.println("Could not send push batch: ${error.message}")
                statuses
            }
        }
    }

    private fun message(request: PushRequest): Message {
        val rendered = renderPushNotification(language = request.language, content = request.content)
        val destination = normalizePushDestination(request.destinationPath)
        return Message.builder()
            .setToken(request.fcmToken)
            .setNotification(Notification.builder().setTitle(rendered.title).setBody(rendered.body).build())
            .putData("destination", destination)
            .setApnsConfig(
                ApnsConfig.builder()
                    .putHeader("apns-priority", "10")
                    .setAps(Aps.builder().setSound("default").build())
                    .build()
            )
            .apply { webPushConfig(rendered, destination)?.let { setWebpushConfig(it) } }
            .build()
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

/** Only completed futures are read; cancellation never waits for the provider's executor. */
private suspend fun <T> com.google.api.core.ApiFuture<T>.awaitAnnouncementResponse(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel(true) }
        addListener({
            try { continuation.resumeWith(Result.success(get())) }
            catch (error: java.util.concurrent.ExecutionException) {
                continuation.resumeWith(Result.failure(error.cause ?: error))
            } catch (error: Exception) { continuation.resumeWith(Result.failure(error)) }
        }, java.util.concurrent.Executor { it.run() })
    }

internal fun normalizePushDestination(destinationPath: String): String {
    val normalized = destinationPath.trim().substringBefore('?').substringBefore('#')
        .let { if (it.startsWith('/')) it else "/$it" }
    return normalized.takeIf { it.length in 2..256 } ?: "/notifications"
}

internal sealed interface FirebaseCredentialSource {
    data class InlineJson(val value: String) : FirebaseCredentialSource
    data class FilePath(val value: String) : FirebaseCredentialSource
}

internal fun resolveFirebaseCredentialSource(
    environment: Map<String, String> = System.getenv()
): FirebaseCredentialSource {
    val inlineJson = environment["FASTTOWIN_FIREBASE_SERVICE_ACCOUNT_JSON"]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (inlineJson != null) return FirebaseCredentialSource.InlineJson(inlineJson)

    val configuredCredential = environment["GOOGLE_APPLICATION_CREDENTIALS"]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (configuredCredential != null) return FirebaseCredentialSource.FilePath(configuredCredential)

    val fallback = sequenceOf(
        File("firebase-adminsdk.json"),
        File("server/firebase-adminsdk.json")
    ).firstOrNull(File::isFile) ?: File("firebase-adminsdk.json")
    return FirebaseCredentialSource.FilePath(fallback.path)
}

private fun configuredFirebaseCredentialStream(): InputStream =
    when (val source = resolveFirebaseCredentialSource()) {
        is FirebaseCredentialSource.InlineJson -> ByteArrayInputStream(
            source.value.toByteArray(StandardCharsets.UTF_8)
        )
        is FirebaseCredentialSource.FilePath -> FileInputStream(source.value)
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
        val now = clock.instant()
        if (now.atZone(zoneId).hour < reminderHour) return 0

        val reminderDate = dailyCheckInDate(now)
        val reminderKey = "daily-check-in:$reminderDate"
        var delivered = 0
        val content = LocalizedPushContent(titleKey = TextKey.PushDailyCheckInTitle, bodyKey = TextKey.PushDailyCheckInMessage)
        // SCL-02: one Firebase call per 500 targets instead of one per player, and several pages per
        // run so 100k players finish in minutes rather than one page every 15 minutes. A page with no
        // progress (only failing tokens left) ends the run so failures cannot loop.
        repeat(MAX_PAGES_PER_RUN) {
            val page = playerProfileRepository.loadDailyPushReminderTargets(reminderDate.toString(), MAX_PUSH_BATCH)
            if (page.isEmpty()) return delivered
            val progress = sendPage(page, content, reminderKey)
            delivered += progress.delivered
            if (page.size < MAX_PUSH_BATCH || progress.delivered + progress.cleared == 0) return delivered
            delay(PAGE_PAUSE_MILLIS)
        }
        return delivered
    }

    private data class PageProgress(val delivered: Int, val cleared: Int)

    private suspend fun sendPage(page: List<PushReminderTarget>, content: LocalizedPushContent, reminderKey: String): PageProgress {
        var delivered = 0
        var cleared = 0
        page.chunked(MAX_PUSH_BATCH).forEach { batch ->
            val statuses = pushNotificationService.sendNotifications(
                batch.map { PushRequest(it.fcmToken, it.language, content, "/account/check-in") }
            )
            batch.zip(statuses).forEach { (target, deliveryStatus) ->
                when (deliveryStatus) {
                    PushDeliveryStatus.SENT -> {
                        if (playerProfileRepository.markPushReminderDelivered(target.playerId, reminderKey)) {
                            delivered++
                        }
                    }
                    PushDeliveryStatus.INVALID_TOKEN -> {
                        if (playerProfileRepository.clearFcmToken(target.playerId, target.fcmToken)) cleared++
                    }
                    PushDeliveryStatus.FAILED -> Unit
                }
            }
        }
        return PageProgress(delivered, cleared)
    }

    private companion object {
        /** Up to 20,000 reminders per run; the loop runs again 15 minutes later. */
        const val MAX_PAGES_PER_RUN = 40
        const val PAGE_PAUSE_MILLIS = 200L
        fun configuredPushZone(): ZoneId = runCatching {
            ZoneId.of(System.getenv("FASTTOWIN_PUSH_ZONE")?.trim().orEmpty())
        }.getOrDefault(ZoneId.of("Asia/Ho_Chi_Minh"))

        fun configuredReminderHour(): Int = System.getenv("FASTTOWIN_DAILY_PUSH_HOUR")
            ?.toIntOrNull()
            ?.coerceIn(0, 23)
            ?: 19
    }
}
