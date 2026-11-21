package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.platform.PublicEndpoints
import kotlinx.coroutines.CancellationException

/**
 * APP-01: moves a stored account session and guest resume token from a previous production
 * URL to the current one. The backend and its refresh tokens are unchanged by a domain
 * move, so the old credentials stay valid; only the storage key changes.
 */
internal fun migrateServerScopedSessions(
    serverUrl: String,
    previousServerUrls: List<String>,
    authStore: AuthSessionStore,
    resumeStore: ResumeTokenStore
) {
    if (serverUrl != PublicEndpoints.productionGameServerUrl) return

    val candidates = previousServerUrls.filter { it != serverUrl }
    if (candidates.isEmpty()) return

    migrateStoredValue(serverUrl, candidates, authStore::load, authStore::save, authStore::clear)
    migrateStoredValue(serverUrl, candidates, resumeStore::load, resumeStore::save, resumeStore::clear)
}

private fun <T : Any> migrateStoredValue(
    destination: String,
    candidates: List<String>,
    load: (String) -> T?,
    save: (String, T) -> Unit,
    clear: (String) -> Unit
) {
    try {
        if (load(destination) != null) return
        val (source, value) = candidates.firstNotNullOfOrNull { old -> load(old)?.let { old to it } } ?: return
        save(destination, value)
        clear(source)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Migration is optional during startup. A failed save leaves the source for a later retry.
    }
}
