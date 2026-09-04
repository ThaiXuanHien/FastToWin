package com.hienthai.fastowin.platform

/**
 * Push bridge owned by the Swift host. Keeping Firebase and APNs in iosApp avoids
 * coupling the shared Kotlin module to a particular Apple notification SDK.
 */
class IosHostPushBridge(
    private val enableNative: () -> Unit,
    private val disableNative: () -> Unit
) : AppPushBridge {
    private var currentStatus = AppPushStatus.PROMPT
    private var currentToken: String? = null
    private var statusObserver: ((AppPushStatus) -> Unit)? = null
    private var tokenObserver: ((String) -> Unit)? = null

    override val status: AppPushStatus
        get() = currentStatus

    override fun observe(
        onStatusChanged: (AppPushStatus) -> Unit,
        onTokenChanged: (String) -> Unit
    ): () -> Unit {
        statusObserver = onStatusChanged
        tokenObserver = onTokenChanged
        onStatusChanged(currentStatus)
        currentToken?.let(onTokenChanged)
        return {
            if (statusObserver === onStatusChanged) statusObserver = null
            if (tokenObserver === onTokenChanged) tokenObserver = null
        }
    }

    override fun enable() {
        updateStatus(AppPushStatus.REQUESTING)
        enableNative()
    }

    override fun disable() {
        disableNative()
    }

    fun markPrompt() = updateStatus(AppPushStatus.PROMPT)

    fun markEnabled() = updateStatus(AppPushStatus.ENABLED)

    fun markDisabled() = updateStatus(AppPushStatus.DISABLED)

    fun markDenied() = updateStatus(AppPushStatus.DENIED)

    fun markError() = updateStatus(AppPushStatus.ERROR)

    fun updateToken(token: String) {
        currentToken = token.trim()
        tokenObserver?.invoke(currentToken.orEmpty())
    }

    private fun updateStatus(value: AppPushStatus) {
        currentStatus = value
        statusObserver?.invoke(value)
    }
}

/** Receives routes from APNs notification taps before or after Compose starts. */
object IosAppNavigationBridge : AppNavigationBridge {
    private var routeObserver: ((String) -> Unit)? = null
    private var pendingRoute: String? = null

    override val initialRoute: String?
        get() = pendingRoute.also { pendingRoute = null }

    override fun observe(onRouteRequested: (String) -> Unit): () -> Unit {
        routeObserver = onRouteRequested
        pendingRoute?.let {
            pendingRoute = null
            onRouteRequested(it)
        }
        return {
            if (routeObserver === onRouteRequested) routeObserver = null
        }
    }

    override fun publish(route: String) = Unit

    override fun goBack(): Boolean = false

    fun openRoute(route: String): Boolean {
        val normalized = normalizeRoute(route) ?: return false
        val observer = routeObserver
        if (observer == null) {
            pendingRoute = normalized
        } else {
            observer(normalized)
        }
        return true
    }

    private fun normalizeRoute(route: String): String? {
        val path = route.trim().substringBefore('?').substringBefore('#')
        if (path.isBlank() || path.length > 256) return null
        return if (path.startsWith('/')) path else "/$path"
    }
}
