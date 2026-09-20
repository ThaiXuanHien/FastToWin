@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import com.hienthai.fastowin.platform.AppNavigationBridge

internal class WebAppNavigationBridge : AppNavigationBridge {
    override val initialRoute: String = currentBrowserRoute()

    init {
        prepareBrowserHistory()
    }

    override fun observe(onRouteRequested: (String) -> Unit): () -> Unit {
        val listenerId = addBrowserRouteListener(onRouteRequested)
        return { removeBrowserRouteListener(listenerId) }
    }

    override fun publish(route: String) {
        publishBrowserRoute(normalizeRoute(route))
    }

    override fun replace(route: String) {
        replaceBrowserRoute(normalizeRoute(route))
    }

    override fun goBack(): Boolean = goBackInBrowserHistory()

    override fun publicUrl(route: String): String = browserPublicUrl(normalizeRoute(route))
}

private fun normalizeRoute(route: String): String {
    val path = route.trim().substringBefore('?').substringBefore('#')
    return when {
        path.isBlank() || path == "/" -> "/"
        path.startsWith('/') -> path
        else -> "/$path"
    }
}

private fun currentBrowserRoute(): String = js(
    "window.location.pathname || '/'"
)

private fun prepareBrowserHistory(): Unit = js(
    "window.FASTTOWIN_PWA.navigation.prepare()"
)

private fun addBrowserRouteListener(onRoute: (String) -> Unit): Int = js(
    "window.FASTTOWIN_PWA.navigation.addRouteListener(onRoute)"
)

private fun removeBrowserRouteListener(listenerId: Int): Unit = js(
    "window.FASTTOWIN_PWA.navigation.removeRouteListener(listenerId)"
)

private fun publishBrowserRoute(route: String): Unit = js(
    "window.FASTTOWIN_PWA.navigation.publish(route)"
)

private fun replaceBrowserRoute(route: String): Unit = js(
    "window.FASTTOWIN_PWA.navigation.replace(route)"
)

private fun goBackInBrowserHistory(): Boolean = js(
    "window.FASTTOWIN_PWA.navigation.goBack()"
)

private fun browserPublicUrl(route: String): String = js(
    "window.FASTTOWIN_PWA.navigation.publicUrl(route)"
)
