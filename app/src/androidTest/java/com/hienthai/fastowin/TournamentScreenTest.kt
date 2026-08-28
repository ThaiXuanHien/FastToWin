package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.TournamentHubSnapshot
import com.hienthai.fastowin.protocol.TournamentInvitationSnapshot
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentPlayerSnapshot
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.TournamentScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TournamentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invitation_acceptsSelectedTournament() {
        var response: Pair<String, Boolean>? = null
        val invitation = TournamentInvitationSnapshot(
            invitationId = "invite-1",
            tournamentId = "tournament-1",
            tournamentName = "Cúp Tốc Chiến",
            hostPlayerId = "host-1",
            hostDisplayName = "Minh",
            gameMode = ProtocolGameMode.ORDER,
            expiresAtEpochMillis = Long.MAX_VALUE
        )

        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = GameState(
                        player = PlayerState("Hiền", id = "player-hien"),
                        tournamentHub = TournamentHubSnapshot(invitations = listOf(invitation))
                    ),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int -> },
                    onInvite = {},
                    onRespondInvitation = { id, accepted -> response = id to accepted },
                    onStart = {},
                    onLeave = {},
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithText("Cúp Tốc Chiến").assertIsDisplayed()
        composeRule.onNodeWithText("THAM GIA").performClick()
        composeRule.runOnIdle { assertEquals("invite-1" to true, response) }
    }

    @Test
    fun host_cannotStartTournamentUntilLobbyIsFull() {
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = tournamentLobbyState(playerCount = 3),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int -> },
                    onInvite = {},
                    onRespondInvitation = { _, _ -> },
                    onStart = {},
                    onLeave = {},
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithTag("start_tournament")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun host_startsTournamentWhenFourPlayersAreOnline() {
        var starts = 0
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = tournamentLobbyState(playerCount = 4),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int -> },
                    onInvite = {},
                    onRespondInvitation = { _, _ -> },
                    onStart = { starts++ },
                    onLeave = {},
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithTag("start_tournament").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, starts) }
    }

    private fun tournamentLobbyState(playerCount: Int): GameState {
        val playerIds = listOf("player-hien", "player-2", "player-3", "player-4")
        val players = playerIds.take(playerCount).mapIndexed { index, id ->
            TournamentPlayerSnapshot(
                playerId = id,
                displayName = if (index == 0) "Hiền" else "Người chơi ${index + 1}",
                isHost = index == 0,
                isOnline = true
            )
        }
        return GameState(
            player = PlayerState("Hiền", id = "player-hien"),
            tournamentHub = TournamentHubSnapshot(
                activeTournament = TournamentSnapshot(
                    tournamentId = "tournament-1",
                    name = "Cúp Tốc Chiến",
                    hostPlayerId = "player-hien",
                    gameMode = ProtocolGameMode.ORDER,
                    phase = TournamentPhase.LOBBY,
                    entryFee = 100,
                    prizePool = 400,
                    players = players,
                    createdAtEpochMillis = 1L
                )
            )
        )
    }
}
