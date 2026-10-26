package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.SeasonHistoryEntrySnapshot
import com.hienthai.fastowin.protocol.SeasonRewardReceiptSnapshot
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen
import com.hienthai.fastowin.ui.screens.SeasonHistoryScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class LocalizedProfileUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileUsesVietnameseActivityCopy() {
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            ProfileTestScreen()
        }

        composeRule.onNodeWithText("Hoạt động").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Thống kê & thành tích").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Lịch sử tài sản").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun walletUsesVietnameseFilters() {
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            ProfileTestSection(
                section = ProfileSection.WALLET,
                state = profileState().copy(
                    walletTransactions = listOf(
                        WalletTransactionSnapshot(
                            id = "wallet-1",
                            sourceType = "DAILY_CHECK_IN",
                            sourceId = "day-1",
                            goldDelta = 50,
                            createdAtEpochMillis = 1L
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Tất cả").assertIsDisplayed()
        composeRule.onNodeWithText("Nhận").assertIsDisplayed()
        composeRule.onNodeWithText("Đã dùng").assertIsDisplayed()
    }

    @Test
    fun dailyCheckInUsesVietnameseCopy() {
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            ProfileTestSection(ProfileSection.DAILY_CHECK_IN)
        }

        composeRule.onNodeWithText("Lịch sử điểm danh").assertIsDisplayed()
        composeRule.onNodeWithText("Hành trình chuyên cần").assertIsDisplayed()
    }

    @Test
    fun logoutConfirmationUsesVietnameseCopy() {
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            ProfileTestScreen()
        }

        composeRule.onNodeWithTag("profile_logout").performScrollTo().performClick()
        composeRule.onNodeWithText("Đăng xuất?").assertIsDisplayed()
        composeRule.onNodeWithText("ĐĂNG XUẤT").assertIsDisplayed()
    }

    @Test
    fun defaultSeasonHistoryAndReceiptUseEnglishCopyWhileCustomNameIsPreserved() {
        setLocalizedContent(AppLanguage.ENGLISH) {
            SeasonHistoryScreen(
                state = profileState().copy(
                    profile = profileFixture().copy(
                        progression = profileFixture().progression.copy(
                            seasonHistory = listOf(
                                SeasonHistoryEntrySnapshot(
                                    seasonNumber = 1,
                                    seasonName = "Mùa Khởi Đầu",
                                    seasonNameKey = "SeasonInitialName",
                                    endedAtEpochMillis = 1L,
                                    finalRating = 1_100,
                                    peakRating = 1_200,
                                    matchesPlayed = 8,
                                    placementMatchesPlayed = 5,
                                    reward = SeasonRewardReceiptSnapshot(
                                        seasonNumber = 1,
                                        seasonName = "Mùa Khởi Đầu",
                                        seasonNameKey = "SeasonInitialName",
                                        tier = RankedTier.SILVER,
                                        peakRating = 1_200,
                                        gold = 600,
                                        awardedAtEpochMillis = 1L,
                                    ),
                                ),
                                SeasonHistoryEntrySnapshot(
                                    seasonNumber = 8,
                                    seasonName = "Creator Cup",
                                    endedAtEpochMillis = 2L,
                                    finalRating = 1_050,
                                    peakRating = 1_100,
                                    matchesPlayed = 6,
                                    placementMatchesPlayed = 5,
                                )
                            )
                        )
                    )
                ),
                onBack = {},
                onRefresh = {},
                onOpenNotifications = {}
            )
        }

        composeRule.onNodeWithText("Season history").assertIsDisplayed()
        composeRule.onNodeWithText("Achievements by season").assertIsDisplayed()
        composeRule.onNodeWithText("Opening Season").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Creator Cup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mùa Khởi Đầu").assertDoesNotExist()
    }

    @Test
    fun switchingLanguageKeepsScrolledProfileItemVisible() {
        val language = mutableStateOf(AppLanguage.VIETNAMESE)
        composeRule.setContent {
            ProvideLocalization(language.value) {
                FastToWinTheme { ProfileTestScreen() }
            }
        }

        composeRule.onNodeWithTag("profile_settings").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { language.value = AppLanguage.ENGLISH }
        composeRule.onNodeWithTag("profile_settings").assertIsDisplayed()
        composeRule.onNodeWithText("App settings").assertIsDisplayed()
    }

    private fun setLocalizedContent(language: AppLanguage, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalization(language) {
                FastToWinTheme { content() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ProfileTestScreen() {
        ProfileScreen(
            serverUrl = "http://127.0.0.1:8080",
            state = profileState(),
            onBack = {},
            onRefresh = {},
            onOpenMatchDetail = {},
            onCloseMatchDetail = {},
            onEquipCosmetics = { _, _ -> },
            onClaimMissionReward = {},
            onSave = { _, _, _ -> },
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

    @androidx.compose.runtime.Composable
    private fun ProfileTestSection(
        section: ProfileSection,
        state: GameState = profileState()
    ) {
        ProfileSectionScreen(
            state = state,
            profile = requireNotNull(state.profile),
            section = section,
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

private fun profileState() = GameState(profile = profileFixture())

private fun profileFixture() = PlayerProfileSnapshot(
    userId = "player-hien",
    displayName = "Hiền",
    playerCode = "HIEN001",
    progression = PlayerProgressionSnapshot(gold = 1_200, gems = 25)
)
