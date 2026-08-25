package com.hienthai.fastowin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.then
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.ResultShareContent
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.PracticeScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.screens.SettingsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ArcadeProfilePracticeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun practice_smallPhoneLargeText_keepsFiftyNumberBoardAndBackReachable() {
        var wentBack = false
        setAdaptiveContent(320.dp, 568.dp, 1.4f) {
            PracticeScreen(
                mode = GameMode.ORDER,
                preferences = quietPreferences(),
                onBack = { wentBack = true }
            )
        }

        composeRule.onNodeWithTag("number_grid")
            .assertIsDisplayed()
            .performScrollToNode(hasTestTag("game_number_50"))
        composeRule.onNodeWithTag("game_number_50").assertIsDisplayed()
        composeRule.onNodeWithText("KẾT THÚC").performClick()
        composeRule.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun practice_landscape_keepsNumberBoardUsable() {
        setAdaptiveContent(700.dp, 320.dp, 1f) {
            PracticeScreen(
                mode = GameMode.ORDER,
                preferences = quietPreferences(),
                onBack = {}
            )
        }

        composeRule.onNodeWithTag("number_grid")
            .assertIsDisplayed()
            .performScrollToNode(hasTestTag("game_number_50"))
        composeRule.onNodeWithTag("game_number_50").assertIsDisplayed()
    }

    @Test
    fun result_smallPhoneLargeText_keepsAnalysisShareAndLobbyActionsReachable() {
        var returnedToLobby = false
        setAdaptiveContent(320.dp, 568.dp, 1.4f) {
            ResultScreen(
                state = resultState(),
                onRestart = { returnedToLobby = true },
                onRematch = {},
                onCancelRematch = {},
                onDeclineRematch = {},
                onConnectOpponent = {},
                onBlockOpponent = {},
                onShareResult = {},
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithTag("result_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Phân tích nhịp chơi").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("share_result").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Về sảnh").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(returnedToLobby) }
    }

    @Test
    fun result_teamMatch_sharesAggregatedTeamScores() {
        var sharedResult: ResultShareContent? = null
        setAdaptiveContent(360.dp, 720.dp, 1f) {
            ResultScreen(
                state = GameState(
                    isGameOver = true,
                    gameMode = GameMode.TEAM_2V2,
                    winnerPlayerId = "player-hien",
                    player = PlayerState(
                        name = "Hiền",
                        id = "player-hien",
                        score = 10,
                        correctSelections = 10,
                        wrongSelections = 1
                    ),
                    teammates = listOf(
                        PlayerState("Lan", id = "player-lan", score = 12, correctSelections = 12, wrongSelections = 2)
                    ),
                    opponent = PlayerState("Hiếu", id = "player-hieu", score = 9),
                    opponents = listOf(
                        PlayerState("Hiếu", id = "player-hieu", score = 9),
                        PlayerState("Minh", id = "player-minh", score = 8)
                    )
                ),
                onRestart = {},
                onRematch = {},
                onCancelRematch = {},
                onDeclineRematch = {},
                onConnectOpponent = {},
                onBlockOpponent = {},
                onShareResult = { sharedResult = it },
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithTag("share_result").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("Đội của bạn", sharedResult?.playerName)
            assertEquals(22, sharedResult?.playerScore)
            assertEquals("Đội đối thủ", sharedResult?.opponentName)
            assertEquals(17, sharedResult?.opponentScore)
            assertEquals("88%", sharedResult?.accuracy)
        }
    }

    @Test
    fun settings_smallPhoneLargestText_keepsActionsScrollableAndInteractive() {
        val preferences = mutableStateOf(quietPreferences())
        var openedTutorial = false
        setAdaptiveContent(320.dp, 568.dp, 1.6f) {
            SettingsScreen(
                preferences = preferences.value,
                onPreferencesChange = { preferences.value = it },
                onPreviewSound = {},
                onOpenTutorial = { openedTutorial = true },
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Âm thanh hiệu ứng").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Xem lại hướng dẫn chơi").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(openedTutorial) }
        composeRule.onNodeWithText("Khôi phục cài đặt mặc định").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profile_smallPhoneLargeText_opensSessionsAndSecurityDialogs() {
        setAdaptiveContent(320.dp, 568.dp, 1.4f) {
            ProfileScreen(
                serverUrl = "",
                state = GameState(profile = profileFixture()),
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = {},
                onSave = { _, _ -> },
                onUploadAvatar = {},
                canEdit = true,
                isAccountLoading = false,
                accountError = null,
                accountNotice = null,
                accountSessions = listOf(accountSession()),
                areSessionsLoading = false,
                onChangePassword = { _, _ -> },
                onDeleteAccount = {},
                onClearAccountFeedback = {},
                onLoadSessions = {},
                onRevokeSession = {},
                onRevokeAllSessions = {},
                onLogout = {},
                showBackButton = false
            )
        }

        composeRule.onNodeWithTag("profile_sessions")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Android • Thiết bị này").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Đóng").performClick()

        composeRule.onNodeWithTag("profile_security")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Đổi mật khẩu").assertIsDisplayed()
        composeRule.onNodeWithText("Mật khẩu hiện tại").assertIsDisplayed()
    }

    @Test
    fun profileEdit_smallPhoneLargeText_updatesDisplayNameWithoutLosingActions() {
        var savedName: String? = null
        setAdaptiveContent(320.dp, 568.dp, 1.4f) {
            ProfileScreen(
                serverUrl = "",
                state = GameState(profile = profileFixture()),
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = {},
                onSave = { name, _ -> savedName = name },
                onUploadAvatar = {},
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
                onLogout = {},
                showBackButton = false
            )
        }

        composeRule.onNodeWithTag("profile_edit").performClick()
        val displayNameField = composeRule.onNodeWithTag("profile_display_name")
        displayNameField.performTextClearance()
        displayNameField.performTextInput("Hiền mới")
        composeRule.onNodeWithText("Lưu").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("Hiền mới", savedName) }
    }

    private fun setAdaptiveContent(
        width: Dp,
        height: Dp,
        fontScale: Float,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then
                    DeviceConfigurationOverride.FontScale(fontScale)
            ) {
                FastToWinTheme { content() }
            }
        }
    }

    private fun quietPreferences() = AppPreferences(
        soundEnabled = false,
        vibrationEnabled = false,
        visualEffectsEnabled = false
    )

    private fun profileFixture() = PlayerProfileSnapshot(
        userId = "arcade-profile",
        displayName = "Hiền",
        playerCode = "HIEN001",
        progression = PlayerProgressionSnapshot(
            level = 12,
            currentLevelExperience = 68,
            nextLevelExperience = 100,
            gold = 1_250,
            gems = 68,
            dailyCheckIn = DailyCheckInSnapshot(
                currentStreak = 5,
                bestStreak = 8,
                totalCheckIns = 42
            )
        )
    )

    private fun accountSession() = AccountSessionSnapshot(
        sessionId = "current-session",
        devicePlatform = "android",
        createdAtEpochMillis = 1_700_000_000_000L,
        lastSeenAtEpochMillis = 1_800_000_000_000L,
        expiresAtEpochMillis = 1_900_000_000_000L,
        isCurrent = true
    )

    private fun resultState() = GameState(
        isGameOver = true,
        winnerPlayerId = "player-hien",
        player = PlayerState("Hiền", id = "player-hien", score = 29),
        opponent = PlayerState("Hiếu", id = "player-hieu", score = 21),
        profile = profileFixture()
    )
}
