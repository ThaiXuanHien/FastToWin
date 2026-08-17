package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.state.AuthState
import com.hienthai.fastowin.state.AvailableRoom
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CriticalFlowsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun login_acceptsKeyboardInputAndSubmitsCredentials() {
        var submittedEmail: String? = null
        var submittedPassword: String? = null
        composeRule.setContent {
            FastToWinTheme {
                AuthScreen(
                    state = AuthState(stage = AuthStage.LOGIN),
                    onOpenLogin = {},
                    onOpenRegister = {},
                    onOpenPasswordReset = {},
                    onPlayAsGuest = {},
                    onLogin = { email, password ->
                        submittedEmail = email
                        submittedPassword = password
                    },
                    onRegister = { _, _, _ -> },
                    onUpgradeGuest = { _, _ -> },
                    onRequestPasswordReset = {},
                    onConfirmPasswordReset = { _, _, _ -> },
                    onBack = {},
                    onCancelUpgrade = {}
                )
            }
        }

        composeRule.onNodeWithTag("auth_email").performTextInput("hien@example.com")
        composeRule.onNodeWithTag("auth_password").performTextInput("12345678")
        composeRule.onNodeWithTag("auth_login_submit").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("hien@example.com", submittedEmail)
            assertEquals("12345678", submittedPassword)
        }
    }

    @Test
    fun home_quickMatchOpensModePickerAndChoosesOrderMode() {
        var selectedMode: GameMode? = null
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = homeState(),
                    onStartMatchmaking = { selectedMode = it }
                )
            }
        }

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("home_quick_match").performClick()
        composeRule.onNodeWithText("Đua thứ tự").performClick()

        composeRule.runOnIdle { assertEquals(GameMode.ORDER, selectedMode) }
    }

    @Test
    fun home_dailyCheckInShowsSevenDayRewardsAndClaimsToday() {
        var claims = 0
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = homeState(),
                    onClaimDailyCheckIn = { claims++ }
                )
            }
        }

        composeRule.onNodeWithTag("daily_check_in_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Điểm danh ngày 1/7").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_check_in_claim").performClick()

        composeRule.runOnIdle { assertEquals(1, claims) }
    }

    @Test
    fun profile_showsDailyCheckInMilestonesInIncreasingDifficulty() {
        composeRule.setContent {
            FastToWinTheme {
                ProfileScreen(
                    state = GameState(
                        profile = PlayerProfileSnapshot(
                            displayName = "Hiền",
                            playerCode = "HIEN001",
                            progression = PlayerProgressionSnapshot(
                                dailyCheckIn = DailyCheckInSnapshot(
                                    bestStreak = 30,
                                    totalCheckIns = 50,
                                    todayDate = "2026-08-17",
                                    historyDates = listOf("2026-08-15", "2026-08-16", "2026-08-17")
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onRefresh = {},
                    onOpenMatchDetail = {},
                    onCloseMatchDetail = {},
                    onEquipCosmetics = { _, _ -> },
                    onSave = { _, _ -> },
                    canEdit = true,
                    isAccountLoading = false,
                    accountError = null,
                    accountNotice = null,
                    accountSessions = emptyList(),
                    areSessionsLoading = false,
                    onChangePassword = { _, _ -> },
                    onDeleteAccount = {},
                    onClearAccountFeedback = {},
                    onLoadSessions = {},
                    onRevokeSession = {},
                    onRevokeAllSessions = {},
                    onLogout = {}
                )
            }
        }

        composeRule.onNodeWithTag("daily_check_in_milestones").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("7/7").assertIsDisplayed()
        composeRule.onNodeWithText("30/30").assertIsDisplayed()
        composeRule.onNodeWithText("50/50").assertIsDisplayed()
        composeRule.onNodeWithText("50/100").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_check_in_calendar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tháng 8/2026").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tháng trước").performClick()
        composeRule.onNodeWithText("Tháng 7/2026").assertIsDisplayed()
    }

    @Test
    fun roomBrowser_createsPrivateRoomFromKeyboardInput() {
        var createdRoom: Pair<String, String>? = null
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = roomBrowserState(),
                    onCreateRoom = { name, password -> createdRoom = name to password }
                )
            }
        }

        composeRule.onNodeWithTag("create_room_open").performClick()
        composeRule.onNodeWithTag("create_room_name").performTextInput("Phòng của Hiền")
        composeRule.onNodeWithTag("create_room_password").performTextInput("12345678")
        composeRule.onNodeWithTag("create_room_submit").performClick()

        composeRule.runOnIdle { assertEquals("Phòng của Hiền" to "12345678", createdRoom) }
    }

    @Test
    fun roomBrowser_joinsPasswordProtectedRoom() {
        var joinedRoom: Pair<String, String>? = null
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = roomBrowserState(),
                    onJoinRoom = { roomId, password -> joinedRoom = roomId to password }
                )
            }
        }

        composeRule.onNodeWithTag("room_item:room-1").performClick()
        composeRule.onNodeWithTag("join_room_password").performTextInput("matkhau")
        composeRule.onNodeWithTag("join_room_submit").performClick()

        composeRule.runOnIdle { assertEquals("room-1" to "matkhau", joinedRoom) }
    }

    @Test
    fun game_rendersAndScrollsAcrossAllFiftyNumbers() {
        val clickedNumbers = mutableListOf<Int>()
        composeRule.setContent {
            FastToWinTheme {
                GameScreen(
                    state = gameState(),
                    onNumberClick = clickedNumbers::add,
                    onFinish = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithTag("game_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("game_number_1").performClick()
        composeRule.onNodeWithTag("number_grid").performScrollToNode(hasTestTag("game_number_50"))
        composeRule.onNodeWithTag("game_number_50").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(50, gameState().numbers.size)
            assertEquals(listOf(1), clickedNumbers)
        }
    }

    @Test
    fun result_showsSummaryAndReturnsToLobby() {
        var returnedToLobby = false
        composeRule.setContent {
            FastToWinTheme {
                ResultScreen(
                    state = resultState(),
                    onRestart = { returnedToLobby = true },
                    onRematch = {},
                    onCancelRematch = {},
                    onDeclineRematch = {},
                    onConnectOpponent = {},
                    onBlockOpponent = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithTag("result_screen").assertIsDisplayed()
        composeRule.onNodeWithText("CHIẾN THẮNG!").assertIsDisplayed()
        composeRule.onNodeWithText("Về sảnh").performScrollTo().performClick()

        composeRule.runOnIdle { assertTrue(returnedToLobby) }
    }

    private fun homeState() = GameState(
        lobbyStage = LobbyStage.SELECT_MODE,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hiền"),
        profile = PlayerProfileSnapshot(
            "Hiền",
            "HIEN001",
            progression = PlayerProgressionSnapshot(
                dailyCheckIn = DailyCheckInSnapshot(
                    claimedToday = false,
                    cycleDay = 1,
                    todayRewardXp = 5
                )
            )
        )
    )

    private fun roomBrowserState() = GameState(
        lobbyStage = LobbyStage.ROOM_BROWSER,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hiền"),
        availableRooms = listOf(
            AvailableRoom(
                id = "room-1",
                name = "Phòng thử nghiệm",
                hostName = "Hiếu",
                gameMode = GameMode.ORDER,
                requiresPassword = true,
                lastSeenAtMillis = 1L
            )
        )
    )

    private fun gameState() = GameState(
        numbers = (1..50).toList(),
        currentTarget = 1,
        connectionStatus = ConnectionStatus.CONNECTED,
        currentRoomName = "Phòng kiểm thử",
        player = PlayerState("Hiền", score = 0),
        opponent = PlayerState("Hiếu", score = 0)
    )

    private fun resultState() = GameState(
        isGameOver = true,
        lastMatchDurationMillis = 42_000,
        player = PlayerState(
            "Hiền",
            score = 500,
            correctSelections = 50,
            wrongSelections = 0,
            averageReactionMillis = 650
        ),
        opponent = PlayerState("Hiếu", score = 420)
    )
}

@androidx.compose.runtime.Composable
private fun TestLobby(
    state: GameState,
    onStartMatchmaking: (GameMode) -> Unit = {},
    onCreateRoom: (String, String) -> Unit = { _, _ -> },
    onJoinRoom: (String, String) -> Unit = { _, _ -> },
    onClaimDailyCheckIn: () -> Unit = {}
) {
    LobbyScreen(
        state = state,
        onModeSelected = {},
        onStartMatchmaking = onStartMatchmaking,
        onCancelMatchmaking = {},
        onOpenRoomBrowser = {},
        onCreateRoom = onCreateRoom,
        onJoinRoom = onJoinRoom,
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
        isGuest = false,
        onUpgradeGuest = {},
        onOpenSettings = {},
        onOpenNotifications = {},
        onOpenPractice = {},
        onClaimDailyCheckIn = onClaimDailyCheckIn,
        sessionStartedAtMillis = 1L
    )
}
