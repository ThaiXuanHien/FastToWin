package com.hienthai.fastowin

import com.hienthai.fastowin.platform.AppNavigationBridge
import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigationIntentTest {
    @Test
    fun `browser route is published before the destination screen mutates`() {
        val events = mutableListOf<String>()
        val bridge = RecordingNavigationBridge(events)

        navigateToRoute(bridge, "/leaderboard") {
            events += "paint:leaderboard"
        }

        assertEquals(
            listOf("route:/leaderboard", "paint:leaderboard"),
            events
        )
    }
}

private class RecordingNavigationBridge(
    private val events: MutableList<String>
) : AppNavigationBridge {
    override val initialRoute: String? = null

    override fun observe(onRouteRequested: (String) -> Unit): () -> Unit = {}

    override fun publish(route: String) {
        events += "route:$route"
    }

    override fun goBack(): Boolean = false
}
