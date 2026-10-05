package com.hienthai.fastowin.server.analytics

import java.time.Instant
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.FileInputStream
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

class FinancialSyncService(private val repository: PostgresFinancialSyncRepository,
    private val source: FinancialSource?, private val collectionStart: Instant?) {
    suspend fun initialize() = withContext(Dispatchers.IO) { repository.markUnavailable() }
    suspend fun runBatch(now: Instant = Instant.now()): Int = withContext(Dispatchers.IO) {
        if (source == null || collectionStart == null) return@withContext 0
        var processed = 0
        try {
            while (processed < 25 && repository.syncNext(source, collectionStart, now)) processed++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // No provider/SQL response or credentials in logs. Next tick retries the durable due row.
            repository.markUnavailable()
        }
        processed
    }
}

internal fun configuredFinancialSource(values: Map<String, String> = System.getenv(),
    endpoint: URI = URI.create("https://androidpublisher.googleapis.com/")): Pair<FinancialSource, Instant>? {
    if (values["FINANCIAL_GOOGLE_ENABLED"] != "true") return null
    return try {
        val start = Instant.parse(values.getValue("FINANCIAL_COLLECTION_START"))
        require(start <= Instant.now())
        val packageName = values.getValue("GOOGLE_PLAY_PACKAGE_NAME")
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
        val credentials = FileInputStream(values.getValue("GOOGLE_APPLICATION_CREDENTIALS")).use {
            ServiceAccountCredentials.fromStream(it).createScoped("https://www.googleapis.com/auth/androidpublisher")
        }
        GoogleOrdersFinancialSource(packageName, credentials, endpoint = endpoint) to start
    } catch (_: Exception) { null }
}
