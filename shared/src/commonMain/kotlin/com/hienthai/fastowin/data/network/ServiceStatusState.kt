package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ServiceStatusResponse
import com.hienthai.fastowin.protocol.MaintenanceState
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.state.ServerClock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Latest reachability and last policy are deliberately distinct. One failed poll is not "offline":
 * a request in flight while the phone suspended the app, or the first one after it resumes, often
 * fails. The state turns unreachable only after [OFFLINE_AFTER_FAILURES] failures in a row, and a
 * first failure is re-checked quickly.
 */
data class ServiceStatusState(
    val status: ServiceStatusResponse? = null,
    val reachable: Boolean? = null,
    val consecutiveFailures: Int = 0,
    val serverClock: ServerClock? = null
) {
    fun received(value: ServiceStatusResponse?, receivedAt: TimeMark = TimeSource.Monotonic.markNow()): ServiceStatusState = if (value != null) {
        ServiceStatusState(value, true, 0, value.serverTimeEpochMillis?.let { ServerClock(it, receivedAt) })
    } else {
        val failures = consecutiveFailures + 1
        copy(reachable = if (failures >= OFFLINE_AFTER_FAILURES) false else reachable, consecutiveFailures = failures)
    }
    val pollSeconds: Int get() = when {
        consecutiveFailures in 1 until OFFLINE_AFTER_FAILURES -> RECHECK_AFTER_FAILURE_SECONDS
        reachable == false -> 5
        else -> status?.pollAfterSeconds?.coerceIn(5, 300) ?: 10
    }
    val fullyClosed: Boolean get() = status?.state == MaintenanceState.MAINTENANCE || status?.maintenance == true
    // New online intents stay fail-closed on the first failed check (maintenance cannot be confirmed),
    // even though the offline notice waits for a second failure.
    val newIntentAllowed: Boolean get() = reachable == true && consecutiveFailures == 0 && !fullyClosed &&
        status?.state in setOf(MaintenanceState.ACTIVE, MaintenanceState.ANNOUNCING)
    val showBanner: Boolean get() = status?.state in setOf(MaintenanceState.ANNOUNCING, MaintenanceState.DRAINING)
}

/** English is the catalog fallback for other application languages. Runtime copy is plain text. */
fun ServiceStatusResponse.localizedMaintenanceMessage(language: AppLanguage): String? =
    (if (language == AppLanguage.VIETNAMESE) listOf(messageVi, messageEn, message)
    else listOf(messageEn, messageVi, message)).firstOrNull { !it.isNullOrBlank() }

fun maintenanceCountdownSeconds(transitionAt: Long?, now: Long): Long? =
    transitionAt?.let { ((it - now).coerceAtLeast(0) + 999) / 1000 }

fun maintenanceBangkokTime(epochMillis: Long): String =
    kotlin.time.Instant.fromEpochMilliseconds(epochMillis + 7 * 3600000L).toString().take(16).replace('T', ' ')

internal const val OFFLINE_AFTER_FAILURES = 2
internal const val RECHECK_AFTER_FAILURE_SECONDS = 2
