package com.hienthai.fastowin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationDestination
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.ClanScreen
import com.hienthai.fastowin.ui.screens.FriendsScreen
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.screens.NotificationsScreen
import com.hienthai.fastowin.ui.screens.ShopScreen
import com.hienthai.fastowin.ui.screens.TournamentScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class LocalizedSocialShopUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun vietnameseFriendsUsesLocalizedTitle() {
        render(AppLanguage.VIETNAMESE) {
            FriendsScreen(
                state = GameState(), onBack = {}, onRefresh = {}, onSendRequest = {},
                onRespondRequest = { _, _ -> }, onCancelRequest = {}, onRemoveFriend = {},
                onBlockPlayer = {}, onUnblockPlayer = {}, onInviteFriend = {},
                onRespondRoomInvitation = { _, _ -> }, onOpenFriendProfile = {}
            )
        }
        composeRule.onNodeWithText("Bạn bè").assertIsDisplayed()
        composeRule.onNodeWithText("Biệt đội của bạn").assertIsDisplayed()
    }

    @Test
    fun vietnameseClanUsesLocalizedTitle() {
        render(AppLanguage.VIETNAMESE) {
            ClanScreen(
                serverUrl = "ws://127.0.0.1:8080/game", currentUserId = null,
                myClanId = null, clanList = emptyList(), pendingJoinClanIds = emptySet(),
                currentClan = null, notice = null, onCreateClan = { _, _ -> },
                onJoinClan = {}, onLeaveClan = {}, onSearch = {},
                onKickMember = { _, _ -> }, onRespondJoinRequest = { _, _, _ -> },
                onUpdateLogo = { _, _ -> }, onClaimQuest = {}, onViewClan = {}, onBack = {}
            )
        }
        composeRule.onNodeWithText("Bang hội").assertIsDisplayed()
        composeRule.onNodeWithText("Sát cánh tranh tài").assertIsDisplayed()
    }

    @Test
    fun vietnameseLeaderboardUsesLocalizedTitle() {
        render(AppLanguage.VIETNAMESE) {
            LeaderboardScreen(
                state = GameState(), onBack = {}, onRefresh = {}, onOpenFriendProfile = {}
            )
        }
        composeRule.onNodeWithText("Xếp hạng").assertIsDisplayed()
        composeRule.onNodeWithText("Đường đua danh vọng").assertIsDisplayed()
    }

    @Test
    fun vietnameseTournamentUsesLocalizedTitle() {
        render(AppLanguage.VIETNAMESE) {
            TournamentScreen(
                state = GameState(), onBack = {}, onCreate = { _, _, _, _, _ -> }, onInvite = {},
                onRespondInvitation = { _, _ -> }, onStart = {}, onLeave = {},
                onOpenFriendProfile = {}
            )
        }
        composeRule.onNodeWithText("Đấu giải").assertIsDisplayed()
        composeRule.onNodeWithText("Đấu trường loại trực tiếp").assertIsDisplayed()
    }

    @Test
    fun vietnameseNotificationsUsesLocalizedTitle() {
        render(AppLanguage.VIETNAMESE) {
            NotificationsScreen(
                notifications = listOf(
                    AppNotification(
                        id = "vietnamese-localization",
                        kind = AppNotificationKind.MISSION,
                        title = "Test notification",
                        message = "Test message",
                        createdAtEpochMillis = 1L,
                        destination = AppNotificationDestination.PROFILE
                    )
                ),
                onBack = {}, onOpen = {}, onDismiss = {},
                onMarkAllRead = {}, onClearAll = {}
            )
        }
        composeRule.onNodeWithText("Thông báo").assertIsDisplayed()
        composeRule.onNodeWithText("Tin mới dành cho bạn").assertIsDisplayed()
    }

    @Test
    fun vietnameseShopUsesLocalizedTitle() {
        render(AppLanguage.VIETNAMESE) {
            ShopScreen(progression = null, onClose = {})
        }
        composeRule.onNodeWithText("Cửa hàng").assertIsDisplayed()
        composeRule.onNodeWithText("Kho Gem").assertIsDisplayed()
    }

    @Test
    fun vietnameseGoldOffersUseLocalizedProgressionCopy() {
        render(AppLanguage.VIETNAMESE) {
            ShopScreen(
                progression = PlayerProgressionSnapshot(gems = 100),
                onClose = {}
            )
        }

        composeRule.onNodeWithText("Vàng").performClick()
        composeRule.onNodeWithText("Túi Vàng").assertIsDisplayed()
        composeRule.onNodeWithText("Rương Vàng").assertIsDisplayed()
        composeRule.onNodeWithTag("gold_exchange_list")
            .performScrollToNode(hasText("Kho Báu Vàng"))
        composeRule.onNodeWithText("Kho Báu Vàng").assertIsDisplayed()
    }

    @Test
    fun vietnameseShopUsesGemAndGoldTerminology() {
        render(AppLanguage.VIETNAMESE) {
            ShopScreen(progression = null, onClose = {})
        }
        composeRule.onNodeWithText("Gem").assertIsDisplayed()
        composeRule.onNodeWithText("Vàng").assertIsDisplayed()
        composeRule.onAllNodesWithText("Mặt bài").assertCountEquals(0)
    }

    @Test
    fun englishShopUsesGemAndGoldTabs() {
        render(AppLanguage.ENGLISH) {
            ShopScreen(progression = null, onClose = {})
        }
        composeRule.onNodeWithText("Gems").assertIsDisplayed()
        composeRule.onNodeWithText("Gold").assertIsDisplayed()
    }

    private fun render(language: AppLanguage, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalization(language) {
                FastToWinTheme(content = content)
            }
        }
    }
}
