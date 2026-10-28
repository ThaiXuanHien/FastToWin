package com.hienthai.fastowin.navigation

internal data class AppRouteRequest(val route: String, val revision: Long)

/** UI-thread owner for browser requests, independent of Compose snapshot/cancellation timing. */
internal class AppRouteRequests {
    var revision: Long = 0
        private set
    var pending: AppRouteRequest? = null
        private set

    fun request(route: String): AppRouteRequest {
        val request = AppRouteRequest(route, ++revision)
        pending = request
        return request
    }

    fun isCurrent(request: AppRouteRequest): Boolean = pending === request

    fun acknowledge(request: AppRouteRequest, appliedRoute: String): Boolean =
        request.route == appliedRoute && clear(request)

    fun clear(request: AppRouteRequest): Boolean {
        if (!isCurrent(request)) return false
        pending = null
        return true
    }

    fun canPublish(expectedRevision: Long): Boolean = pending == null && revision == expectedRevision

    fun supersede() {
        revision++
        pending = null
    }
}
