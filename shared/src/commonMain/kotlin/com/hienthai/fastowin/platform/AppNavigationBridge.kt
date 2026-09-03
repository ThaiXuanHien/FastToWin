package com.hienthai.fastowin.platform

/**
 * Optional navigation bridge used by hosts that expose an address bar and native history.
 * Android and iOS use [NoOpAppNavigationBridge], while the web host maps app screens to URLs.
 */
interface AppNavigationBridge {
    val initialRoute: String?

    fun observe(onRouteRequested: (String) -> Unit): () -> Unit

    fun publish(route: String)

    fun goBack(): Boolean

    fun publicUrl(route: String): String? = null
}

object NoOpAppNavigationBridge : AppNavigationBridge {
    override val initialRoute: String? = null

    override fun observe(onRouteRequested: (String) -> Unit): () -> Unit = {}

    override fun publish(route: String) = Unit

    override fun goBack(): Boolean = false
}
