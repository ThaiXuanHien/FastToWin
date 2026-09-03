package com.hienthai.fastowin.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ResultNavigationActionsTest {
    @Test
    fun returnToLobby_leavesRoomWithoutFollowingBrowserHistory() {
        for (previousRoute in listOf("/friends", "/account", "/notifications")) {
            var route = "/room/test-room"
            var leaveCalls = 0
            var backCalls = 0
            val actions = ResultNavigationActions(
                leaveRoom = { leaveCalls++; route = "/rooms" },
                openTournament = { error("Not a tournament") },
                navigateBack = { backCalls++; route = previousRoute },
                isTournamentMatch = false
            )

            actions.returnToLobby()

            assertEquals("/rooms", route, "Previous route: $previousRoute")
            assertEquals(1, leaveCalls)
            assertEquals(0, backCalls)
        }
    }

    @Test
    fun headerBack_preservesBrowserNavigation() {
        var route = "/room/test-room"
        val actions = ResultNavigationActions(
            leaveRoom = { error("Browser handled Back") },
            openTournament = { error("Browser handled Back") },
            navigateBack = { route = "/friends" },
            isTournamentMatch = false
        )

        actions.back()

        assertEquals("/friends", route)
    }

    @Test
    fun backWithoutBrowserHistory_leavesOrdinaryRoom() {
        var leaveCalls = 0
        val actions = ResultNavigationActions(
            leaveRoom = { leaveCalls++ },
            openTournament = { error("Not a tournament") },
            navigateBack = { fallback -> fallback() },
            isTournamentMatch = false
        )

        actions.back()

        assertEquals(1, leaveCalls)
    }

    @Test
    fun tournamentBackWithoutBrowserHistory_returnsToTournament() {
        var tournamentCalls = 0
        val actions = ResultNavigationActions(
            leaveRoom = { error("Tournament result must return to its bracket") },
            openTournament = { tournamentCalls++ },
            navigateBack = { fallback -> fallback() },
            isTournamentMatch = true
        )

        actions.back()

        assertEquals(1, tournamentCalls)
    }
}
