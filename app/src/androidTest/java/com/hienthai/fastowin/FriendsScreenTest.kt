package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.FriendsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class FriendsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun friendAndRecentPlayerWithSameUserIdRenderWithoutDuplicateKeyCrash() {
        val sharedUserId = "player-1"
        val state = GameState(
            social = FriendsSnapshot(
                friends = listOf(
                    FriendSnapshot(
                        userId = sharedUserId,
                        displayName = "Bạn bè Hiếu",
                        playerCode = "HIEU001",
                        presence = FriendPresence.ONLINE
                    )
                ),
                recentPlayers = listOf(
                    RecentPlayerSnapshot(
                        userId = sharedUserId,
                        displayName = "Đối thủ gần đây Hiếu",
                        playerCode = "HIEU001",
                        lastPlayedAtEpochMillis = 1L,
                        matchesPlayed = 1
                    )
                )
            )
        )

        composeRule.setContent {
            FastToWinTheme {
                FriendsScreen(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onSendRequest = {},
                    onRespondRequest = { _, _ -> },
                    onCancelRequest = {},
                    onRemoveFriend = {},
                    onBlockPlayer = {},
                    onUnblockPlayer = {},
                    onInviteFriend = {},
                    onRespondRoomInvitation = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Bạn bè Hiếu").assertIsDisplayed()
        composeRule.onNodeWithText("Đối thủ gần đây Hiếu").performScrollTo().assertIsDisplayed()
    }
}
