package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.state.createPracticeChallenge
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.PracticeScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class LocalizedGameplayUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeUsesEnglishCopy() {
        setLocalizedContent(AppLanguage.ENGLISH) {
            LocalizedLobbyScreen(state = lobbyState(LobbyStage.SELECT_MODE), isGuest = false)
        }

        composeRule.onNodeWithText("PLAY NOW").assertIsDisplayed()
        composeRule.onNodeWithText("Explore").assertIsDisplayed()
    }

    @Test
    fun roomBrowserUsesGermanCopy() {
        setLocalizedContent(AppLanguage.GERMAN) {
            LocalizedLobbyScreen(state = lobbyState(LobbyStage.ROOM_BROWSER), isGuest = false)
        }

        composeRule.onNodeWithText("ÖFFENTLICHE RÄUME").assertIsDisplayed()
        composeRule.onNodeWithText("Raum erstellen", ignoreCase = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gameUsesJapaneseCopy() {
        setLocalizedContent(AppLanguage.JAPANESE) {
            GameScreen(
                state = activeGameState(),
                onNumberClick = {},
                onFinish = {},
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithText("次の数字").assertIsDisplayed()
        composeRule.onNodeWithText("クラシック · ランク戦").assertIsDisplayed()
    }

    @Test
    fun resultUsesRussianCopy() {
        setLocalizedContent(AppLanguage.RUSSIAN) {
            ResultScreen(
                state = finishedGameState(),
                onRestart = {},
                onRematch = {},
                onCancelRematch = {},
                onDeclineRematch = {},
                onConnectOpponent = {},
                onBlockOpponent = {},
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithText("ПОБЕДА!").assertIsDisplayed()
        composeRule.onNodeWithText("Результат матча").assertIsDisplayed()
    }

    @Test
    fun resultShowsLocalizedEnglishRematchFailure() {
        setLocalizedContent(AppLanguage.ENGLISH) {
            ResultScreen(
                state = finishedGameState().copy(
                    isRematchActionPending = false,
                    rematchNotice = "The action could not be completed. Please try again.",
                    rematchNoticeErrorCode = "OPPONENT_LEFT",
                ),
                onRestart = {},
                onRematch = {},
                onCancelRematch = {},
                onDeclineRematch = {},
                onConnectOpponent = {},
                onBlockOpponent = {},
                preferences = quietPreferences(),
            )
        }

        composeRule.onNodeWithText("The action could not be completed. Please try again.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Đối thủ đã rời phòng.").assertDoesNotExist()
    }

    @Test
    fun switchingPracticeToThaiKeepsBoardProgress() {
        val language = mutableStateOf(AppLanguage.VIETNAMESE)
        val challenge = createPracticeChallenge(GameMode.ORDER, seed = 0x12345678)
        composeRule.setContent {
            ProvideLocalization(language.value) {
                FastToWinTheme {
                    PracticeScreen(
                        mode = GameMode.ORDER,
                        challenge = challenge,
                        preferences = quietPreferences(),
                        onBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("game_number_1").performClick()
        composeRule.onNodeWithTag("practice_score").assertTextEquals("10")
        composeRule.onNodeWithTag("practice_target").assertTextEquals("2")

        composeRule.runOnIdle { language.value = AppLanguage.THAI }

        composeRule.onNodeWithText("คะแนน").assertIsDisplayed()
        composeRule.onNodeWithTag("practice_score").assertTextEquals("10")
        composeRule.onNodeWithTag("practice_target").assertTextEquals("2")
    }

    private fun setLocalizedContent(language: AppLanguage, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalization(language) {
                FastToWinTheme { content() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LocalizedLobbyScreen(state: GameState, isGuest: Boolean) {
        LobbyScreen(
            state = state,
            onModeSelected = {},
            onStartMatchmaking = { _, _ -> },
            onCancelMatchmaking = {},
            onOpenRoomBrowser = {},
            onCreateRoom = { _, _, _, _ -> },
            onJoinRoom = { _, _ -> },
            onLeaveRoom = {},
            onSetReady = {},
            onKickOpponent = {},
            onRefreshRooms = {},
            onOpenProfile = {},
            onOpenLeaderboard = {},
            onOpenFriends = {},
            onOpenFriendProfile = {},
            onBackToMode = {},
            onLogout = {},
            isGuest = isGuest,
            onUpgradeGuest = {},
            onOpenNotifications = {},
            onOpenClan = {},
            onOpenPractice = {},
            onOpenTournament = {},
            onOpenShop = {},
            onShareRoom = { _, _ -> Result.success(Unit) },
            onResolveRoomLink = {},
            onClaimDailyCheckIn = {}
        )
    }

    private fun lobbyState(stage: LobbyStage) = GameState(
        lobbyStage = stage,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hien", id = "hien")
    )

    private fun activeGameState() = GameState(
        isMatchStarted = true,
        numbers = (1..50).toList(),
        currentTarget = 1,
        matchType = com.hienthai.fastowin.protocol.MatchType.RANKED,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hien", id = "hien"),
        opponent = PlayerState("Hieu", id = "hieu")
    )

    private fun finishedGameState() = GameState(
        isGameOver = true,
        winnerPlayerId = "hien",
        player = PlayerState("Hien", id = "hien", score = 30),
        opponent = PlayerState("Hieu", id = "hieu", score = 20)
    )

    private fun quietPreferences() = AppPreferences(
        soundEnabled = false,
        vibrationEnabled = false,
        visualEffectsEnabled = false
    )
}
