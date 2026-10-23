package com.hienthai.fastowin.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.FileInputStream
import java.util.UUID

/** Billing credentials never replace Firebase's application default credentials. */
internal fun googlePlayBillingCredentials(values: Map<String, String>): GoogleCredentials? {
    val inline = values["GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"]?.takeIf(String::isNotBlank)
    val billingFile = values["GOOGLE_PLAY_CREDENTIALS_FILE"]?.takeIf(String::isNotBlank)
    val legacyFile = values["GOOGLE_APPLICATION_CREDENTIALS"]?.takeIf(String::isNotBlank)
    if (inline == null && billingFile == null && legacyFile == null) return null
    return try {
        val credentials = when {
            inline != null -> inline.byteInputStream().use { ServiceAccountCredentials.fromStream(it) }
            billingFile != null -> FileInputStream(billingFile).use { ServiceAccountCredentials.fromStream(it) }
            else -> FileInputStream(legacyFile!!).use { GoogleCredentials.fromStream(it) }
        }
        credentials.createScoped("https://www.googleapis.com/auth/androidpublisher")
    } catch (_: Exception) {
        // Provider parsers may include fragments of the private JSON in their exception messages.
        throw IllegalStateException("Google Play Billing credentials are invalid")
    }
}

internal fun googlePlayTestPlayerIds(values: Map<String, String>): Set<String> {
    val value = values["GOOGLE_PLAY_TEST_PLAYER_IDS"]?.takeIf(String::isNotBlank) ?: return emptySet()
    return value.split(',').map { id ->
        val canonical = id.trim().lowercase()
        require(runCatching { UUID.fromString(canonical).toString() == canonical }.getOrDefault(false)) {
            "Google Play test player IDs must be explicit player UUIDs"
        }
        canonical
    }.toSet()
}
