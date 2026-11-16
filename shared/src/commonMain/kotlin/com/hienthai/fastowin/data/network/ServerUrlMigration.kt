package com.hienthai.fastowin.data.network

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
    val candidates = previousServerUrls.filter { it != serverUrl }
    if (candidates.isEmpty()) return
    if (authStore.load(serverUrl) == null) {
        candidates.firstNotNullOfOrNull { old -> authStore.load(old)?.let { old to it } }?.let { (old, session) ->
            authStore.save(serverUrl, session)
            authStore.clear(old)
        }
    }
    if (resumeStore.load(serverUrl) == null) {
        candidates.firstNotNullOfOrNull { old -> resumeStore.load(old)?.let { old to it } }?.let { (old, token) ->
            resumeStore.save(serverUrl, token)
            resumeStore.clear(old)
        }
    }
}
