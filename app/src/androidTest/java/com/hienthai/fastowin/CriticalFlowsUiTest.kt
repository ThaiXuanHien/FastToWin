package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.ResultShareContent
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.GameModeStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.platform.StoreBillingState
import com.hienthai.fastowin.platform.StoreProductPrice
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.state.AuthState
import com.hienthai.fastowin.state.AvailableRoom
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.state.PracticeChallenge
import com.hienthai.fastowin.state.createPracticeChallenge
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.screens.ShopScreen
import com.hienthai.fastowin.ui.screens.PracticeLauncherDialog
import com.hienthai.fastowin.ui.screens.TournamentScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

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
                    onRegister = { _, _, _, _ -> },
                    onUpgradeGuest = { _, _ -> },
                    onRequestPasswordReset = {},
                    onConfirmPasswordReset = { _, _, _ -> },
                    onRequestEmailVerification = {},
                    onConfirmEmailVerification = {},
                    onBack = {},
                    onCancelUpgrade = {},
                    onCancelEmailVerification = {}
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
        var selectedMatchType: MatchType? = null
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = homeState(),
                    onStartMatchmaking = { mode, matchType ->
                        selectedMode = mode
                        selectedMatchType = matchType
                    }
                )
            }
        }

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("home_quick_match").performClick()
        composeRule.onNodeWithText("Đấu xếp hạng").performClick()
        composeRule.onNodeWithText("Cổ điển").performClick()

        composeRule.runOnIdle {
            assertEquals(GameMode.ORDER, selectedMode)
            assertEquals(MatchType.RANKED, selectedMatchType)
        }
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
        composeRule.onNodeWithText("Điểm danh 7 ngày").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_check_in_claim").performClick()

        composeRule.runOnIdle { assertEquals(1, claims) }
    }

    @Test
    fun practiceLauncher_opensAValidSharedChallengeCode() {
        var openedChallenge: PracticeChallenge? = null
        val expected = createPracticeChallenge(GameMode.RANDOM_TARGET, seed = 0x12345678)
        composeRule.setContent {
            FastToWinTheme {
                PracticeLauncherDialog(
                    onDismiss = {},
                    onStartNew = {},
                    onOpenChallenge = { openedChallenge = it },
                    playerLevel = 12
                )
            }
        }

        composeRule.onNodeWithTag("challenge_input").performTextInput(expected.code.lowercase())
        composeRule.onNodeWithTag("challenge_open").performClick()

        composeRule.runOnIdle { assertEquals(expected, openedChallenge) }
    }

    @Test
    fun practiceLauncher_keepsDifficultyUnlockProgression() {
        var openedChallenge: PracticeChallenge? = null
        val locked = createPracticeChallenge(GameMode.COMBO, seed = 1234)
        composeRule.setContent {
            FastToWinTheme {
                PracticeLauncherDialog(
                    onDismiss = {},
                    onStartNew = {},
                    onOpenChallenge = { openedChallenge = it },
                    playerLevel = 1
                )
            }
        }

        composeRule.onNodeWithTag("challenge_input").performTextInput(locked.code)
        composeRule.onNodeWithTag("challenge_open").performClick()

        composeRule.onNodeWithText("Chế độ Combo mở khóa ở cấp 12.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, openedChallenge) }
    }

    @Test
    fun tournament_createSubmitsNameAndMode() {
        var submittedName: String? = null
        var submittedMode: GameMode? = null
        var submittedMaxPlayers: Int? = null
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = GameState(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        player = PlayerState("Hiền", id = "player-hien"),
                        profile = PlayerProfileSnapshot(
                            userId = "player-hien",
                            displayName = "Hiền",
                            playerCode = "HIEN001",
                            progression = PlayerProgressionSnapshot(level = 20)
                        )
                    ),
                    onBack = {},
                    onCreate = { name, mode, _, maxPlayers ->
                        submittedName = name
                        submittedMode = mode
                        submittedMaxPlayers = maxPlayers
                    },
                    onInvite = {},
                    onRespondInvitation = { _, _ -> },
                    onStart = {},
                    onLeave = {},
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithTag("tournament_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("tournament_name").performTextInput("Cúp cuối tuần")
        composeRule.onNodeWithTag("tournament_size_16").performClick()
        composeRule.onNodeWithTag("create_tournament").performClick()

        composeRule.runOnIdle {
            assertEquals("Cúp cuối tuần", submittedName)
            assertEquals(GameMode.ORDER, submittedMode)
            assertEquals(16, submittedMaxPlayers)
        }
    }

    @Test
    fun profile_doesNotShowDailyCheckInSection() {
        composeRule.setContent {
            FastToWinTheme {
                ProfileScreen(
                    serverUrl = "",
                    state = GameState(
                        profile = PlayerProfileSnapshot(
                            userId = "player-hien",
                            displayName = "Hiền",
                            playerCode = "HIEN001",
                            progression = PlayerProgressionSnapshot(
                                dailyMissions = listOf(
                                    MissionSnapshot(
                                        code = "DAILY_PLAY_3",
                                        title = "Chơi 3 trận hôm nay",
                                        progress = 3,
                                        target = 3,
                                        completed = true,
                                        rewardXp = 20
                                    )
                                ),
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
                    onClaimMissionReward = {},
                    onSave = { _, _ -> },
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
                    onLogout = {}
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag("daily_check_in_milestones").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("daily_check_in_calendar").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("profile_daily_check_in_strip").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("profile_section_missions").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profile_showsStatisticsForEachPlayedGameMode() {
        val profile = PlayerProfileSnapshot(
            userId = "player-hien",
            displayName = "Hiền",
            playerCode = "HIEN001",
            modeStatistics = listOf(
                GameModeStatisticsSnapshot(
                    gameMode = ProtocolGameMode.ORDER,
                    totalMatches = 10,
                    wins = 6,
                    losses = 3,
                    draws = 1,
                    highestScore = 50,
                    averageScore = 34
                )
            )
        )
        composeRule.setContent {
            FastToWinTheme {
                ProfileSectionScreen(
                    state = GameState(profile = profile),
                    profile = profile,
                    section = ProfileSection.STATISTICS,
                    isExternalProfile = false,
                    canEdit = true,
                    onBack = {},
                    onRefresh = {},
                    onOpenMatchDetail = {},
                    onCloseMatchDetail = {},
                    onEquipCosmetics = { _, _ -> },
                    onClaimMissionReward = {},
                    onSave = { _, _ -> },
                    onOpenNotifications = {}
                )
            }
        }

        composeRule.onNodeWithTag("mode_statistics:ORDER").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("60% thắng").assertIsDisplayed()
        composeRule.onNodeWithText("10 trận • 6 thắng • 3 thua • 1 hòa").assertIsDisplayed()
        composeRule.onNodeWithText("Điểm cao 50 • Trung bình 34").assertIsDisplayed()
    }

    @Test
    fun shop_usesGameFriendlyLabelsAndGemCopy() {
        var purchasedProductId: String? = null
        val gemPackage = GemPackageSnapshot("fasttowin_gems_80", "Gói Tân binh", 80)
        composeRule.setContent {
            FastToWinTheme {
                ShopScreen(
                    progression = PlayerProgressionSnapshot(gold = 2_000, gems = 25),
                    onBuy = {},
                    onEquip = {},
                    onClose = {},
                    gemPackages = listOf(gemPackage),
                    billingState = StoreBillingState(
                        platform = StorePlatform.GOOGLE_PLAY,
                        isReady = true,
                        isSandboxFallback = true,
                        prices = mapOf(
                            gemPackage.productId to StoreProductPrice(gemPackage.productId, "Sandbox")
                        )
                    ),
                    onBuyGems = { purchasedProductId = it }
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Mặt bài").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Bàn số").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("shop_tab:FRAME").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("shop_tab:EMOJI").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("shop_tab:GEMS").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Kho Gem").assertIsDisplayed()
        composeRule.onNodeWithText("Gói Tân binh").assertIsDisplayed()
        composeRule.onNodeWithText("Sandbox").performClick()
        composeRule.runOnIdle { assertEquals(gemPackage.productId, purchasedProductId) }
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
        composeRule.onNodeWithTag("match_type:CASUAL").performClick()
        composeRule.onNodeWithTag("game_mode:ORDER").performClick()
        composeRule.onNodeWithTag("create_room_name").performTextInput("Phòng của Hiền")
        composeRule.onNodeWithTag("create_room_privacy_toggle").performClick()
        composeRule.onNodeWithTag("create_room_password").performTextInput("12345678")
        composeRule.onNodeWithTag("create_room_submit").performClick()

        composeRule.runOnIdle { assertEquals("Phòng của Hiền" to "12345678", createdRoom) }
    }

    @Test
    fun roomBrowser_createsPublicRoomWithoutPassword() {
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
        composeRule.onNodeWithTag("match_type:CASUAL").performClick()
        composeRule.onNodeWithTag("game_mode:ORDER").performClick()
        composeRule.onNodeWithTag("create_room_name").performTextInput("Phòng công khai")
        composeRule.onNodeWithTag("create_room_submit").performClick()

        composeRule.runOnIdle { assertEquals("Phòng công khai" to "", createdRoom) }
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
    fun roomLink_joinsPublicRoomAfterFreshRoomListLoads() {
        var joinedRoom: Pair<String, String>? = null
        var resolved = false
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = roomBrowserState().copy(
                        availableRooms = roomBrowserState().availableRooms.map { it.copy(requiresPassword = false) },
                        roomListVersion = 2,
                        pendingRoomLinkId = "room-1",
                        pendingRoomLinkListVersion = 1
                    ),
                    onJoinRoom = { roomId, password -> joinedRoom = roomId to password },
                    onResolveRoomLink = { resolved = true }
                )
            }
        }

        composeRule.waitUntil { joinedRoom != null }
        composeRule.runOnIdle {
            assertTrue(resolved)
            assertEquals("room-1" to "", joinedRoom)
        }
    }

    @Test
    fun roomLink_promptsForPasswordWithoutPuttingItInTheLink() {
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = roomBrowserState().copy(
                        roomListVersion = 2,
                        pendingRoomLinkId = "room-1",
                        pendingRoomLinkListVersion = 1
                    )
                )
            }
        }

        composeRule.onNodeWithTag("join_room_password").assertIsDisplayed()
    }

    @Test
    fun roomWaiting_sharesTheCurrentRoomIdAndName() {
        var sharedRoom: Pair<String, String>? = null
        composeRule.setContent {
            FastToWinTheme {
                TestLobby(
                    state = GameState(
                        lobbyStage = LobbyStage.ROOM_WAITING,
                        connectionStatus = ConnectionStatus.CONNECTED,
                        currentRoomId = "room-123",
                        currentRoomName = "Phòng của Hiền",
                        player = PlayerState("Hiền")
                    ),
                    onShareRoom = { roomId, roomName ->
                        sharedRoom = roomId to roomName
                        Result.success(Unit)
                    }
                )
            }
        }

        composeRule.onNodeWithTag("share_room").performClick()
        composeRule.runOnIdle { assertEquals("room-123" to "Phòng của Hiền", sharedRoom) }
    }

    @Test
    fun game_rendersAllFiftyNumbersWithoutScrolling() {
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
        composeRule.onNodeWithTag("avatar_frame:frame_gold").assertIsDisplayed()
        composeRule.onNodeWithTag("avatar_frame:frame_persistent").assertIsDisplayed()
        composeRule.onNodeWithTag("game_number_1").performClick()
        composeRule.onNodeWithTag("game_number_50").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(50, gameState().numbers.size)
            assertEquals(listOf(1), clickedNumbers)
        }
    }

    @Test
    fun game_updatesWrongSelectionMetricWhenSnapshotChanges() {
        val state = mutableStateOf(gameState())
        composeRule.setContent {
            FastToWinTheme {
                GameScreen(
                    state = state.value,
                    onNumberClick = {},
                    onFinish = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("Đúng 0  ·  Sai 0").assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                player = state.value.player.copy(wrongSelections = 1)
            )
        }
        composeRule.onNodeWithText("Đúng 0  ·  Sai 1").assertIsDisplayed()
    }

    @Test
    fun game_sendsSelectedEmoji() {
        var sentEmoji: String? = null
        composeRule.setContent {
            FastToWinTheme {
                GameScreen(
                    state = gameState(),
                    onNumberClick = {},
                    onFinish = {},
                    onSendEmoji = { sentEmoji = it },
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithTag("open_emoji_menu").performClick()
        composeRule.onNodeWithTag("send_emoji:😂").performClick()
        composeRule.runOnIdle { assertEquals("😂", sentEmoji) }
    }

    @Test
    fun result_showsSummaryAndReturnsToLobby() {
        var returnedToLobby = false
        var sharedResult: ResultShareContent? = null
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
                    onShareResult = { sharedResult = it },
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithTag("result_screen").assertIsDisplayed()
        composeRule.onNodeWithText("CHIẾN THẮNG!").assertIsDisplayed()
        composeRule.onNodeWithText("Phân tích nhịp chơi").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("share_result").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("CHIẾN THẮNG", sharedResult?.result)
            assertEquals("Hiền", sharedResult?.playerName)
            assertEquals(500, sharedResult?.playerScore)
            assertEquals("Hiếu", sharedResult?.opponentName)
            assertEquals(420, sharedResult?.opponentScore)
            assertEquals("Cổ điển", sharedResult?.gameMode)
            assertEquals("42s", sharedResult?.duration)
            assertEquals("100%", sharedResult?.accuracy)
        }
        composeRule.onNodeWithText("Về sảnh").performScrollTo().performClick()

        composeRule.runOnIdle { assertTrue(returnedToLobby) }
    }

    @Test
    fun result_unequalScoreNeverDisplaysDrawWhenWinnerIsMissing() {
        composeRule.setContent {
            FastToWinTheme {
                ResultScreen(
                    state = GameState(
                        isGameOver = true,
                        player = PlayerState("Hiền", id = "player-hien", score = 21),
                        opponent = PlayerState("Hiếu", id = "player-hieu", score = 29),
                        winnerPlayerId = null
                    ),
                    onRestart = {},
                    onRematch = {},
                    onCancelRematch = {},
                    onDeclineRematch = {},
                    onConnectOpponent = {},
                    onBlockOpponent = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("THUA CUỘC").assertIsDisplayed()
    }

    @Test
    fun result_forfeitShowsLossInsteadOfReturningStraightToLobby() {
        composeRule.setContent {
            FastToWinTheme {
                ResultScreen(
                    state = GameState(
                        isGameOver = true,
                        didForfeitLastMatch = true,
                        hasOpponent = true,
                        player = PlayerState("Hiền", id = "player-hien", score = 21),
                        opponent = PlayerState("Hiếu", id = "player-hieu", score = 29),
                        winnerPlayerId = "player-hieu"
                    ),
                    onRestart = {},
                    onRematch = {},
                    onCancelRematch = {},
                    onDeclineRematch = {},
                    onConnectOpponent = {},
                    onBlockOpponent = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("THUA CUỘC").assertIsDisplayed()
        composeRule.onNodeWithText("Bạn đã chủ động rời trận và bị xử thua.").assertIsDisplayed()
        composeRule.onNodeWithText("Mời đấu lại").assertIsEnabled()
    }

    @Test
    fun result_rematchInviteDisablesUntilOpponentResponds() {
        val state = mutableStateOf(resultState())
        composeRule.setContent {
            FastToWinTheme {
                ResultScreen(
                    state = state.value,
                    onRestart = {},
                    onRematch = {
                        state.value = state.value.copy(isRematchActionPending = true)
                    },
                    onCancelRematch = {},
                    onDeclineRematch = {},
                    onConnectOpponent = {},
                    onBlockOpponent = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithTag("result_rematch_action")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("result_rematch_action").assertIsNotEnabled()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                isRematchActionPending = false,
                isRematchRequestedByMe = true
            )
        }
        composeRule.onNodeWithTag("result_rematch_action").assertIsNotEnabled()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                isRematchRequestedByMe = false,
                rematchNotice = "Đối thủ đã từ chối đấu lại."
            )
        }
        composeRule.onNodeWithTag("result_rematch_action").assertIsEnabled()

        composeRule.onNodeWithTag("result_rematch_action").performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                isRematchActionPending = false,
                isRematchRequestedByMe = true,
                rematchExpiresAtEpochMillis = Long.MAX_VALUE
            )
        }
        composeRule.onNodeWithTag("result_rematch_action").assertIsNotEnabled()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                isRematchRequestedByMe = false,
                rematchExpiresAtEpochMillis = null,
                rematchNotice = "Yêu cầu đấu lại đã hết thời gian."
            )
        }
        composeRule.onNodeWithText("Yêu cầu đấu lại đã hết thời gian.").assertIsDisplayed()
        composeRule.onNodeWithTag("result_rematch_action").assertIsEnabled()
    }

    @Test
    fun result_receivedRematchInviteShowsImmediateDialog() {
        var accepted = false
        var declined = false
        composeRule.setContent {
            FastToWinTheme {
                ResultScreen(
                    state = resultState().copy(isRematchRequestedByOpponent = true),
                    onRestart = {},
                    onRematch = { accepted = true },
                    onCancelRematch = {},
                    onDeclineRematch = { declined = true },
                    onConnectOpponent = {},
                    onBlockOpponent = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("MỜI ĐẤU LẠI").assertIsDisplayed()
        composeRule.onNodeWithText("Hiếu muốn đấu lại với bạn.").assertIsDisplayed()
        composeRule.onNodeWithTag("accept_rematch").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertTrue(accepted)
            assertTrue(!declined)
        }
    }

    @Test
    fun result_shareCreatesPngForSystemShareSheet() {
        val shareDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "shared_results"
        )
        shareDirectory.listFiles()?.filter { it.isFile }?.forEach(File::delete)
        composeRule.setContent {
            FastToWinTheme {
                ResultScreen(
                    state = resultState(),
                    onRestart = {},
                    onRematch = {},
                    onCancelRematch = {},
                    onDeclineRematch = {},
                    onConnectOpponent = {},
                    onBlockOpponent = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        composeRule.onNodeWithTag("share_result").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shareDirectory.listFiles()?.any { it.extension == "png" && it.length() > 0L } == true
        }
    }

    private fun homeState() = GameState(
        lobbyStage = LobbyStage.SELECT_MODE,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hiền"),
        profile = PlayerProfileSnapshot(
            userId = "player-hien",
            displayName = "Hiền",
            playerCode = "HIEN001",
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
        player = PlayerState("Hiền", score = 0, avatarId = "crown", frameId = "frame_gold"),
        opponent = PlayerState("Hiếu", score = 0, avatarId = "target", frameId = "frame_persistent")
    )

    private fun resultState() = GameState(
        isGameOver = true,
        hasOpponent = true,
        lastMatchDurationMillis = 42_000,
        player = PlayerState(
            "Hiền",
            score = 500,
            correctSelections = 50,
            wrongSelections = 0,
            averageReactionMillis = 650,
            fastestSegmentStart = 21,
            fastestSegmentEnd = 30,
            fastestSegmentAverageMillis = 480,
            slowestSegmentStart = 41,
            slowestSegmentEnd = 50,
            slowestSegmentAverageMillis = 820
        ),
        opponent = PlayerState("Hiếu", score = 420)
    )
}

@androidx.compose.runtime.Composable
private fun TestLobby(
    state: GameState,
    onStartMatchmaking: (GameMode, MatchType) -> Unit = { _, _ -> },
    onCreateRoom: (String, String) -> Unit = { _, _ -> },
    onJoinRoom: (String, String) -> Unit = { _, _ -> },
    onClaimDailyCheckIn: () -> Unit = {},
    onShareRoom: (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    onResolveRoomLink: (String?) -> Unit = {}
) {
    LobbyScreen(
        state = state,
        onModeSelected = {},
        onStartMatchmaking = onStartMatchmaking,
        onCancelMatchmaking = {},
        onOpenRoomBrowser = {},
        onCreateRoom = { _, _, name, password -> onCreateRoom(name, password) },
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
        onOpenNotifications = {},
        onOpenClan = {},
        onOpenPractice = {},
        onOpenTournament = {},
        onOpenShop = {},
        onShareRoom = onShareRoom,
        onResolveRoomLink = onResolveRoomLink,
        onClaimDailyCheckIn = onClaimDailyCheckIn
    )
}
