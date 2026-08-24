package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.FriendsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class FriendsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recentPlayersAreNotShownInFriendsScreen() {
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
                    onRespondRoomInvitation = { _, _ -> },
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithText("Bạn bè Hiếu").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Vừa thi đấu cùng").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Đối thủ gần đây Hiếu").fetchSemanticsNodes().size)
    }

    @Test
    fun friendItemOpensProfileAndMoreMenuKeepsSensitiveActionsSeparate() {
        var openedFriendId: String? = null
        var removedFriendId: String? = null
        val friend = FriendSnapshot(
            userId = "player-1",
            displayName = "Bạn bè Hiếu",
            playerCode = "HIEU001",
            presence = FriendPresence.ONLINE
        )
        composeRule.setContent {
            FastToWinTheme {
                FriendsScreen(
                    state = GameState(social = FriendsSnapshot(friends = listOf(friend))),
                    onBack = {},
                    onRefresh = {},
                    onSendRequest = {},
                    onRespondRequest = { _, _ -> },
                    onCancelRequest = {},
                    onRemoveFriend = { removedFriendId = it },
                    onBlockPlayer = {},
                    onUnblockPlayer = {},
                    onInviteFriend = {},
                    onRespondRoomInvitation = { _, _ -> },
                    onOpenFriendProfile = { openedFriendId = it }
                )
            }
        }

        composeRule.onNodeWithTag("friend_item:player-1").performClick()
        composeRule.runOnIdle { assertEquals("player-1", openedFriendId) }

        composeRule.onNodeWithTag("friend_more:player-1").performClick()
        composeRule.onNodeWithText("Hủy kết bạn").performClick()
        composeRule.onNodeWithText("Hủy kết bạn").performClick()
        composeRule.runOnIdle { assertEquals("player-1", removedFriendId) }
    }

    @Test
    fun friendPresenceUsesAccessibleIndicatorsInsteadOfStatusText() {
        val friends = FriendPresence.entries.mapIndexed { index, presence ->
            FriendSnapshot(
                userId = "player-$index",
                displayName = "Người chơi $index",
                playerCode = "CODE$index",
                presence = presence
            )
        }
        composeRule.setContent {
            FastToWinTheme {
                FriendsScreen(
                    state = GameState(social = FriendsSnapshot(friends = friends)),
                    onBack = {},
                    onRefresh = {},
                    onSendRequest = {},
                    onRespondRequest = { _, _ -> },
                    onCancelRequest = {},
                    onRemoveFriend = {},
                    onBlockPlayer = {},
                    onUnblockPlayer = {},
                    onInviteFriend = {},
                    onRespondRoomInvitation = { _, _ -> },
                    onOpenFriendProfile = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Đang ngoại tuyến").assertExists()
        composeRule.onNodeWithContentDescription("Đang trực tuyến").assertExists()
        composeRule.onNodeWithContentDescription("Đang trong phòng").assertExists()
        composeRule.onNodeWithContentDescription("Đang trong trận").assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("Ngoại tuyến").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Trực tuyến").fetchSemanticsNodes().size)
    }
}
