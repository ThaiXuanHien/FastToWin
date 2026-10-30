package com.hienthai.fastowin

import com.hienthai.fastowin.platform.AppNavigationBridge
import com.hienthai.fastowin.platform.NoOpAppNavigationBridge
import kotlin.test.Test
import kotlin.test.assertEquals

class GuestUpgradeNavigationTest {
    @Test fun cancellationReplacesGatedRouteBeforeReturningToGame() {
        for (route in listOf("/account", "/account/wallet", "/friends", "/friends/player/statistics", "/clan", "/account/?from=login")) {
            val bridge = UpgradeNavigationBridge(initialRoute = route, currentRoute = route)
            cancelGuestUpgradeNavigation(bridge) { bridge.events += "cancel" }
            assertEquals(listOf("replace:/", "cancel"), bridge.events, route)
        }
    }

    @Test fun cancellationKeepsTheLatestBackDestinationInsteadOfTheInitialAccountPath() {
        val bridge = UpgradeNavigationBridge(initialRoute = "/account", currentRoute = "/rooms")
        cancelGuestUpgradeNavigation(bridge) { bridge.events += "cancel" }
        assertEquals(listOf("cancel"), bridge.events)
        assertEquals("/rooms", bridge.currentRoute)
    }

    @Test fun cancellationUsesLatestProtectedDestinationInsteadOfInitialRoomsPath() {
        val bridge = UpgradeNavigationBridge(initialRoute = "/rooms", currentRoute = "/account")
        cancelGuestUpgradeNavigation(bridge) { bridge.events += "cancel" }
        assertEquals(listOf("replace:/", "cancel"), bridge.events)
    }

    @Test fun nativeCancellationDoesNotRequireHostHistory() {
        var cancelled = false
        cancelGuestUpgradeNavigation(NoOpAppNavigationBridge) { cancelled = true }
        assertEquals(true, cancelled)
    }
}

private class UpgradeNavigationBridge(
    override val initialRoute: String?,
    override var currentRoute: String?
) : AppNavigationBridge {
    val events = mutableListOf<String>()
    override fun observe(onRouteRequested: (String) -> Unit): () -> Unit = {}
    override fun publish(route: String) { events += "push:$route" }
    override fun replace(route: String) { events += "replace:$route"; currentRoute = route }
    override fun goBack(): Boolean = false
}
