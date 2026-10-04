package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ServiceStatusResponse
import com.hienthai.fastowin.protocol.MaintenanceState
import com.hienthai.fastowin.localization.AppLanguage

/** Latest reachability and last policy are deliberately distinct. */
data class ServiceStatusState(val status: ServiceStatusResponse? = null, val reachable: Boolean? = null) {
    fun received(value: ServiceStatusResponse?) = ServiceStatusState(value ?: status, value != null)
    val pollSeconds: Int get() = if (reachable == false) 5 else status?.pollAfterSeconds?.coerceIn(5, 300) ?: 10
    val fullyClosed: Boolean get() = status?.state == MaintenanceState.MAINTENANCE || status?.maintenance == true
    val newIntentAllowed: Boolean get() = reachable == true && !fullyClosed &&
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
