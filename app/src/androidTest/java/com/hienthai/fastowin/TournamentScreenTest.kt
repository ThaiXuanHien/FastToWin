package com.hienthai.fastowin

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.PublicTournamentSummary
import com.hienthai.fastowin.protocol.TournamentVisibility
import com.hienthai.fastowin.protocol.TournamentFeeFilter
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.PublicTournamentFilters
import com.hienthai.fastowin.protocol.TournamentHubSnapshot
import com.hienthai.fastowin.protocol.TournamentInvitationSnapshot
import com.hienthai.fastowin.protocol.TournamentMatchSnapshot
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentPlayerSnapshot
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.TournamentScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TournamentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun paidJoinRequiresExplicitConfirmation() {
        var joined: String? = null
        setDiscoveryContent(publicTournamentState(100), onJoin = { joined = it })
        composeRule.onNodeWithTag("public_tournament_join_tournament-1").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Tham gia Weekend Cup với phí 100 Vàng? Quỹ thưởng hiện tại: 400 Vàng. Phí sẽ được trừ khi bạn tham gia."
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertNull(joined) }
        composeRule.onNodeWithTag("confirm_public_tournament_join").performClick()
        composeRule.runOnIdle { assertEquals("tournament-1", joined) }
    }

    @Test
    fun cancellingPaidJoinDoesNotJoin() {
        var joined: String? = null
        setDiscoveryContent(publicTournamentState(100), onJoin = { joined = it })
        composeRule.onNodeWithTag("public_tournament_join_tournament-1").performScrollTo().performClick()
        composeRule.onNodeWithTag("cancel_public_tournament_join").performClick()
        composeRule.onNodeWithTag("confirm_public_tournament_join").assertDoesNotExist()
        composeRule.runOnIdle { assertNull(joined) }
    }

    @Test
    fun paidConfirmationUsesUpdatedSameIdNameFeeAndPrize() {
        val state = mutableStateOf(publicTournamentState(100))
        var joined: String? = null
        setMutableDiscoveryContent(state) { joined = it }
        composeRule.onNodeWithTag("public_tournament_join_tournament-1").performScrollTo().performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(publicTournaments = listOf(
                state.value.publicTournaments.single().copy(name = "Updated Cup", entryFee = 150, prizePool = 900)
            ))
        }
        composeRule.onNodeWithText(
            "Tham gia Updated Cup với phí 150 Vàng? Quỹ thưởng hiện tại: 900 Vàng. Phí sẽ được trừ khi bạn tham gia."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_public_tournament_join").performClick()
        composeRule.runOnIdle { assertEquals("tournament-1", joined) }
    }

    @Test
    fun paidConfirmationCannotJoinWhileRefreshingJoiningOrDisconnected() {
        val initialState = publicTournamentState(100)
        val state = mutableStateOf(initialState)
        var joined: String? = null
        setMutableDiscoveryContent(state) { joined = it }
        composeRule.onNodeWithTag("public_tournament_join_tournament-1").performScrollTo().performClick()
        listOf(
            initialState.copy(isPublicTournamentsLoading = true),
            initialState.copy(isTournamentLoading = true),
            initialState.copy(connectionStatus = ConnectionStatus.RECONNECTING)
        ).forEach { unavailableState ->
            composeRule.runOnIdle { state.value = unavailableState }
            composeRule.onNodeWithTag("confirm_public_tournament_join").assertIsNotEnabled().performClick()
            composeRule.runOnIdle { assertNull(joined) }
        }
        composeRule.runOnIdle { state.value = initialState }
        composeRule.onNodeWithTag("confirm_public_tournament_join").performClick()
        composeRule.runOnIdle { assertEquals("tournament-1", joined) }
    }

    @Test
    fun removingPendingListingDismissesConfirmationWithoutJoining() {
        assertPendingListingInvalidated(emptyList())
    }

    @Test
    fun pendingListingBecomingFreeDismissesConfirmationWithoutJoining() {
        assertPendingListingInvalidated(publicTournamentState(0).publicTournaments)
    }

    private fun assertPendingListingInvalidated(replacement: List<PublicTournamentSummary>) {
        val initialState = publicTournamentState(100)
        val state = mutableStateOf(initialState)
        var joined: String? = null
        setMutableDiscoveryContent(state) { joined = it }
        composeRule.onNodeWithTag("public_tournament_join_tournament-1").performScrollTo().performClick()
        composeRule.runOnIdle { state.value = initialState.copy(publicTournaments = replacement) }
        composeRule.onNodeWithTag("confirm_public_tournament_join").assertDoesNotExist()
        composeRule.runOnIdle {
            assertNull(joined)
            state.value = initialState
        }
        composeRule.onNodeWithTag("confirm_public_tournament_join").assertDoesNotExist()
    }

    private fun setMutableDiscoveryContent(
        state: androidx.compose.runtime.State<GameState>,
        onJoin: (String) -> Unit
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(375.dp, 832.dp)) then
                DeviceConfigurationOverride.FontScale(1.4f)) {
                FastToWinTheme { TournamentScreen(
                    state = state.value, onBack = {}, onCreate = { _, _, _, _, _ -> },
                    onInvite = {}, onRespondInvitation = { _, _ -> }, onStart = {}, onLeave = {},
                    onOpenFriendProfile = {}, onJoinPublic = onJoin
                ) }
            }
        }
    }

    @Test
    fun freeJoinDoesNotRequireConfirmation() {
        var joined: String? = null
        setDiscoveryContent(publicTournamentState(0), onJoin = { joined = it })
        composeRule.onNodeWithTag("public_tournament_join_tournament-1").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("tournament-1", joined) }
        composeRule.onNodeWithTag("confirm_public_tournament_join").assertDoesNotExist()
    }

    @Test
    fun offlineFriendCanBeInvitedFromLobby() {
        var invited: String? = null
        setDiscoveryContent(tournamentLobbyState(1).copy(social = FriendsSnapshot(friends = listOf(
            FriendSnapshot("friend-1", "Offline friend", "ABC123")
        ))), onInvite = { invited = it })
        composeRule.onNodeWithTag("tournament_invite_friend-1").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("friend-1", invited) }
        composeRule.onNodeWithTag("public_tournament_search").assertDoesNotExist()
    }

    @Test
    fun hostWithNoEligibleFriendsGetsGuidance() {
        setDiscoveryContent(tournamentLobbyState(1))
        composeRule.onNodeWithTag("tournament_invite_empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun createDefaultsToPublicAndCanSelectPrivate() {
        val created = mutableListOf<TournamentVisibility>()
        setDiscoveryContent(GameState(), onCreate = { created += it })
        composeRule.onNodeWithTag("tournament_visibility_public").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("tournament_name").performScrollTo().performTextInput("Weekend Cup")
        composeRule.onNodeWithTag("create_tournament").performScrollTo().performClick()
        composeRule.onNodeWithTag("tournament_visibility_private").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("create_tournament").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(TournamentVisibility.PUBLIC, TournamentVisibility.PRIVATE), created) }
    }

    @Test
    fun compactLargeTextFiltersPreserveSelectionsAndRefresh() {
        var filters = PublicTournamentFilters()
        var refreshes = 0
        setDiscoveryContent(publicTournamentState(0), onFilters = { filters = it }, onRefresh = { refreshes++ })
        composeRule.onNodeWithTag("public_tournament_search").performScrollTo().performTextInput("Cup")
        composeRule.onNodeWithTag("public_tournament_filter_mode_ORDER").performScrollTo().performClick()
        composeRule.onNodeWithTag("public_tournament_filter_size_8").performScrollTo().performClick()
        val feeChip = composeRule.onNodeWithTag("public_tournament_filter_fee_FREE").performScrollTo()
        val before = feeChip.fetchSemanticsNode().boundsInRoot
        feeChip.performClick().assertIsSelected()
        val after = feeChip.fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("public_tournament_refresh").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(PublicTournamentFilters("Cup", ProtocolGameMode.ORDER, 8, TournamentFeeFilter.FREE), filters)
            assertEquals(1, refreshes)
            assertEquals(before.width, after.width, 0.1f)
            assertEquals(before.height, after.height, 0.1f)
        }
    }

    @Test
    fun loadingAndReconnectStatesDoNotClaimAnEmptyList() {
        var state by mutableStateOf(GameState(connectionStatus = ConnectionStatus.CONNECTED, isPublicTournamentsLoading = true))
        composeRule.setContent { FastToWinTheme { TournamentScreen(
            state = state, onBack = {}, onCreate = { _, _, _, _, _ -> }, onInvite = {},
            onRespondInvitation = { _, _ -> }, onStart = {}, onLeave = {}, onOpenFriendProfile = {}
        ) } }
        composeRule.onNodeWithTag("public_tournament_loading").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("public_tournament_empty").assertDoesNotExist()
        composeRule.runOnIdle { state = state.copy(isPublicTournamentsLoading = false, connectionStatus = ConnectionStatus.RECONNECTING) }
        composeRule.onNodeWithTag("public_tournament_reconnect").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(connectionStatus = ConnectionStatus.CONNECTED) }
        composeRule.onNodeWithTag("public_tournament_empty").performScrollTo().assertIsDisplayed()
    }

    private fun publicTournamentState(fee: Int) = GameState(
        connectionStatus = ConnectionStatus.CONNECTED,
        publicTournaments = listOf(PublicTournamentSummary(
            "tournament-1", "Weekend Cup", "host-1", "Minh", ProtocolGameMode.ORDER, 1, 4, fee, fee * 4, 1L
        ))
    )

    private fun setDiscoveryContent(
        initialState: GameState,
        onJoin: (String) -> Unit = {},
        onInvite: (String) -> Unit = {},
        onCreate: (TournamentVisibility) -> Unit = {},
        onFilters: (PublicTournamentFilters) -> Unit = {},
        onRefresh: () -> Unit = {}
    ) {
        composeRule.setContent {
            var state by androidx.compose.runtime.remember { mutableStateOf(initialState) }
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(375.dp, 832.dp)) then
                DeviceConfigurationOverride.FontScale(1.4f)) {
                FastToWinTheme { TournamentScreen(
                    state = state, onBack = {}, onCreate = { _, _, _, _, visibility -> onCreate(visibility) },
                    onInvite = onInvite, onRespondInvitation = { _, _ -> }, onStart = {}, onLeave = {},
                    onOpenFriendProfile = {}, onJoinPublic = onJoin, onRefreshPublic = onRefresh,
                    onPublicFiltersChange = { state = state.copy(publicTournamentFilters = it); onFilters(it) }
                ) }
            }
        }
    }

    @Test
    fun compactTournamentContentUsesTheSameWideGutterAsOtherScreens() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(384.dp, 832.dp))
            ) {
                FastToWinTheme {
                    TournamentScreen(
                        state = GameState(player = PlayerState("Hiền", id = "player-hien")),
                        onBack = {},
                        onCreate = { _, _, _, _, _ -> },
                        onInvite = {},
                        onRespondInvitation = { _, _ -> },
                        onStart = {},
                        onLeave = {},
                        onOpenFriendProfile = {}
                    )
                }
            }
        }

        val screenWidth = composeRule.onNodeWithTag("tournament_screen")
            .fetchSemanticsNode().boundsInRoot.width
        val contentWidth = composeRule.onNodeWithTag("tournament_content")
            .fetchSemanticsNode().boundsInRoot.width

        composeRule.runOnIdle { assertTrue(contentWidth >= screenWidth * 0.9f) }
    }

    @Test
    fun entryFeesUseEqualThreeColumnRowsAndShowGoldForUnselectedOptions() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(384.dp, 832.dp))
            ) {
                FastToWinTheme {
                    TournamentScreen(
                        state = GameState(player = PlayerState("Hiền", id = "player-hien")),
                        onBack = {},
                        onCreate = { _, _, _, _, _ -> },
                        onInvite = {},
                        onRespondInvitation = { _, _ -> },
                        onStart = {},
                        onLeave = {},
                        onOpenFriendProfile = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("tournament_fee_section").performScrollTo()
        val optionWidths = listOf(0, 50, 100, 200, 500, 1000).map { fee ->
            composeRule.onNodeWithTag("tournament_fee_$fee")
                .fetchSemanticsNode().boundsInRoot.width
        }
        composeRule.onNodeWithTag(
            testTag = "tournament_fee_icon_50",
            useUnmergedTree = true
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.runOnIdle {
            val widthDelta = optionWidths.max() - optionWidths.min()
            assertTrue(
                "Entry fee widths must differ by at most one physical pixel: $optionWidths",
                widthDelta <= 1f
            )
        }
    }

    @Test
    fun selectingCustomEntryFeeKeepsTheFeeSectionHeightStable() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                    DeviceConfigurationOverride.FontScale(1.4f)
            ) {
                FastToWinTheme {
                    TournamentScreen(
                        state = GameState(player = PlayerState("Hiền", id = "player-hien")),
                        onBack = {},
                        onCreate = { _, _, _, _, _ -> },
                        onInvite = {},
                        onRespondInvitation = { _, _ -> },
                        onStart = {},
                        onLeave = {},
                        onOpenFriendProfile = {}
                    )
                }
            }
        }

        val before = composeRule.onNodeWithTag("tournament_fee_section")
            .performScrollTo()
            .fetchSemanticsNode().boundsInRoot.height
        composeRule.onNodeWithTag("tournament_fee_custom").performScrollTo().performClick()
        val after = composeRule.onNodeWithTag("tournament_fee_section")
            .fetchSemanticsNode().boundsInRoot.height

        composeRule.onNodeWithTag("tournament_custom_fee_input").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(kotlin.math.abs(before - after) < 1f) }
    }

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
                    onCreate = { _: String, _: GameMode, _: Int, _: Int, _: TournamentVisibility -> },
                    onInvite = {},
                    onRespondInvitation = { id, accepted -> response = id to accepted },
                    onStart = {},
                    onLeave = {},
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithText("Cúp Tốc Chiến").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("THAM GIA").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("invite-1" to true, response) }
    }

    @Test
    fun host_cannotStartTournamentUntilLobbyIsFull() {
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = tournamentLobbyState(playerCount = 3),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int, _: Int, _: TournamentVisibility -> },
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
                    onCreate = { _: String, _: GameMode, _: Int, _: Int, _: TournamentVisibility -> },
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

    @Test
    fun host_startsEightPlayerTournamentWhenLobbyIsFull() {
        var starts = 0
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = tournamentLobbyState(playerCount = 8, maxPlayers = 8),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int, _: Int, _: TournamentVisibility -> },
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

    @Test
    fun host_startsSixteenPlayerTournamentWhenLobbyIsFull() {
        var starts = 0
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = tournamentLobbyState(playerCount = 16, maxPlayers = 16),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int, _: Int, _: TournamentVisibility -> },
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

    @Test
    fun eightPlayerBracketShowsQuarterfinalsSemifinalsAndFinal() {
        val lobbyState = tournamentLobbyState(playerCount = 8, maxPlayers = 8)
        val tournament = checkNotNull(lobbyState.tournamentHub.activeTournament)
        val matches = buildList {
            repeat(4) { add(TournamentMatchSnapshot("quarter-$it", 1, it + 1)) }
            repeat(2) { add(TournamentMatchSnapshot("semi-$it", 2, it + 1)) }
            add(TournamentMatchSnapshot("final", 3, 1))
        }
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = lobbyState.copy(
                        tournamentHub = lobbyState.tournamentHub.copy(
                            activeTournament = tournament.copy(
                                phase = TournamentPhase.RUNNING,
                                matches = matches
                            )
                        )
                    ),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int, _: Int, _: TournamentVisibility -> },
                    onInvite = {},
                    onRespondInvitation = { _, _ -> },
                    onStart = {},
                    onLeave = {},
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithText("VÒNG TỨ KẾT").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("VÒNG BÁN KẾT").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TRẬN CHUNG KẾT").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sixteenPlayerBracketShowsAllFourRounds() {
        val lobbyState = tournamentLobbyState(playerCount = 16, maxPlayers = 16)
        val tournament = checkNotNull(lobbyState.tournamentHub.activeTournament)
        val matches = buildList {
            repeat(8) { add(TournamentMatchSnapshot("round-of-16-$it", 1, it + 1)) }
            repeat(4) { add(TournamentMatchSnapshot("quarter-$it", 2, it + 1)) }
            repeat(2) { add(TournamentMatchSnapshot("semi-$it", 3, it + 1)) }
            add(TournamentMatchSnapshot("final", 4, 1))
        }
        composeRule.setContent {
            FastToWinTheme {
                TournamentScreen(
                    state = lobbyState.copy(
                        tournamentHub = lobbyState.tournamentHub.copy(
                            activeTournament = tournament.copy(
                                phase = TournamentPhase.RUNNING,
                                matches = matches
                            )
                        )
                    ),
                    onBack = {},
                    onCreate = { _: String, _: GameMode, _: Int, _: Int, _: TournamentVisibility -> },
                    onInvite = {},
                    onRespondInvitation = { _, _ -> },
                    onStart = {},
                    onLeave = {},
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithText("VÒNG 1/8").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("VÒNG TỨ KẾT").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("VÒNG BÁN KẾT").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TRẬN CHUNG KẾT").performScrollTo().assertIsDisplayed()
    }

    private fun tournamentLobbyState(playerCount: Int, maxPlayers: Int = 4): GameState {
        val playerIds = listOf("player-hien") + (2..maxPlayers).map { "player-$it" }
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
                    maxPlayers = maxPlayers,
                    entryFee = 100,
                    prizePool = 400,
                    players = players,
                    createdAtEpochMillis = 1L
                )
            )
        )
    }
}
