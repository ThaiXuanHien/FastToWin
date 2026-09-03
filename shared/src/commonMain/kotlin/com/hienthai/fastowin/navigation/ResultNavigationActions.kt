package com.hienthai.fastowin.navigation

/** Keeps an explicit room exit separate from platform/browser Back navigation. */
class ResultNavigationActions(
    leaveRoom: () -> Unit,
    openTournament: () -> Unit,
    navigateBack: (() -> Unit) -> Unit,
    isTournamentMatch: Boolean
) {
    // Always leave the room: browser history may point to Friends or a profile,
    // while leaveRoom resets the finished match and opens the room browser.
    val returnToLobby: () -> Unit = leaveRoom

    val back: () -> Unit = {
        navigateBack(if (isTournamentMatch) openTournament else leaveRoom)
    }
}
