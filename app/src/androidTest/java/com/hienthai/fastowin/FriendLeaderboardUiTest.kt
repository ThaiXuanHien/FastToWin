package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class FriendLeaderboardUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun friendsTabRanksFriendsByEloAndPinsTheCurrentPlayer() {
        setFriends(
            listOf(
                LeaderboardEntrySnapshot(1, "Strong", "STRONG1", 10, 20, 50, eloRating = 1900, userId = "strong"),
                LeaderboardEntrySnapshot(2, "Me", "ME00001", 8, 20, 50, eloRating = 1500, userId = "me"),
                LeaderboardEntrySnapshot(3, "Fresh", "FRESH01", 0, 0, 0, eloRating = 1000, userId = "fresh")
            )
        )
        composeRule.onNodeWithTag("leaderboard_tab_friends").performClick()
        composeRule.onNodeWithTag("leaderboard_friend_STRONG1_value", useUnmergedTree = true).assertTextContains("1900")
        composeRule.onNodeWithTag("leaderboard_friend_ME00001").assertIsSelected()
        composeRule.onNodeWithTag("leaderboard_current_friend").assertIsDisplayed()
            .assertTextContains("#2", substring = true)
        composeRule.onNodeWithTag("leaderboard_current_friend").performClick()
        composeRule.onNodeWithTag("leaderboard_player_profile_dialog").assertIsDisplayed()
    }

    @Test
    fun friendsTabSuggestsAddingFriendsWhenOnlyThePlayerIsListed() {
        setFriends(listOf(LeaderboardEntrySnapshot(1, "Me", "ME00001", 8, 20, 50, eloRating = 1500, userId = "me")))
        composeRule.onNodeWithTag("leaderboard_tab_friends").performClick()
        composeRule.onNodeWithText("Add friends to compare your Elo with them here.").assertIsDisplayed()
        composeRule.onNodeWithTag("leaderboard_current_friend").assertDoesNotExist()
    }

    private fun setFriends(friends: List<LeaderboardEntrySnapshot>) {
        composeRule.setContent {
            ProvideLocalization(AppLanguage.ENGLISH) {
                FastToWinTheme {
                    LeaderboardScreen(
                        GameState(
                            profile = PlayerProfileSnapshot(userId = "me", displayName = "Me", playerCode = "ME00001"),
                            leaderboard = LeaderboardSnapshot(friendPlayers = friends)
                        ),
                        {}, {}, showBackButton = false
                    )
                }
            }
        }
    }
}
