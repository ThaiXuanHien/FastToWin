package com.hienthai.fastowin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun chineseFriendsUsesLocalizedTitle() {
        render(AppLanguage.SIMPLIFIED_CHINESE) {
            FriendsScreen(
                state = GameState(), onBack = {}, onRefresh = {}, onSendRequest = {},
                onRespondRequest = { _, _ -> }, onCancelRequest = {}, onRemoveFriend = {},
                onBlockPlayer = {}, onUnblockPlayer = {}, onInviteFriend = {},
                onRespondRoomInvitation = { _, _ -> }, onOpenFriendProfile = {}
            )
        }
        composeRule.onNodeWithText("好友").assertIsDisplayed()
        composeRule.onNodeWithText("你的队伍").assertIsDisplayed()
    }

    @Test
    fun japaneseClanUsesLocalizedTitle() {
        render(AppLanguage.JAPANESE) {
            ClanScreen(
                serverUrl = "ws://127.0.0.1:8080/game", currentUserId = null,
                myClanId = null, clanList = emptyList(), pendingJoinClanIds = emptySet(),
                currentClan = null, notice = null, onCreateClan = { _, _ -> },
                onJoinClan = {}, onLeaveClan = {}, onSearch = {},
                onKickMember = { _, _ -> }, onRespondJoinRequest = { _, _, _ -> },
                onUpdateLogo = { _, _ -> }, onClaimQuest = {}, onViewClan = {}, onBack = {}
            )
        }
        composeRule.onNodeWithText("クラン").assertIsDisplayed()
        composeRule.onNodeWithText("仲間と挑戦").assertIsDisplayed()
    }

    @Test
    fun koreanLeaderboardUsesLocalizedTitle() {
        render(AppLanguage.KOREAN) {
            LeaderboardScreen(
                state = GameState(), onBack = {}, onRefresh = {}, onOpenFriendProfile = {}
            )
        }
        composeRule.onNodeWithText("랭킹").assertIsDisplayed()
        composeRule.onNodeWithText("명예의 레이스").assertIsDisplayed()
    }

    @Test
    fun indonesianTournamentUsesLocalizedTitle() {
        render(AppLanguage.INDONESIAN) {
            TournamentScreen(
                state = GameState(), onBack = {}, onCreate = { _, _, _, _ -> }, onInvite = {},
                onRespondInvitation = { _, _ -> }, onStart = {}, onLeave = {},
                onOpenFriendProfile = {}
            )
        }
        composeRule.onNodeWithText("Turnamen").assertIsDisplayed()
        composeRule.onNodeWithText("Arena eliminasi").assertIsDisplayed()
    }

    @Test
    fun thaiNotificationsUsesLocalizedTitle() {
        render(AppLanguage.THAI) {
            NotificationsScreen(
                notifications = listOf(
                    AppNotification(
                        id = "thai-localization",
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
        composeRule.onNodeWithText("การแจ้งเตือน").assertIsDisplayed()
        composeRule.onNodeWithText("ข่าวสารสำหรับคุณ").assertIsDisplayed()
    }

    @Test
    fun frenchShopUsesLocalizedTitle() {
        render(AppLanguage.FRENCH) {
            ShopScreen(progression = null, onClose = {})
        }
        composeRule.onNodeWithText("Boutique").assertIsDisplayed()
        composeRule.onNodeWithText("Coffre de Gemmes").assertIsDisplayed()
    }

    @Test
    fun frenchGoldOffersUseLocalizedProgressionCopy() {
        render(AppLanguage.FRENCH) {
            ShopScreen(
                progression = PlayerProgressionSnapshot(gems = 100),
                onClose = {}
            )
        }

        composeRule.onNodeWithText("Or").performClick()
        composeRule.onNodeWithText("Sac d'Or").assertIsDisplayed()
        composeRule.onNodeWithText("Coffre d'Or").assertIsDisplayed()
        composeRule.onNodeWithText("Trésor d'Or").assertIsDisplayed()
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
