package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.platform.RewardedAdGatewayState
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.ui.components.PlayQuotaExhaustedDialog
import com.hienthai.fastowin.ui.screens.MatchTypePickerDialog
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayQuotaUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun matchTypePicker_showsSharedRemainingQuota() {
        setContent {
            MatchTypePickerDialog(
                title = "Chọn loại trận",
                quota = quota(remaining = 3),
                onDismiss = {},
                onSelect = {}
            )
        }

        val quotaNode = composeRule.onNodeWithTag("online_quota_remaining").assertIsDisplayed()
            .fetchSemanticsNode()
        assertEquals("3/10", quotaNode.config[SemanticsProperties.StateDescription])
    }

    @Test
    fun exhaustedQuota_showsRewardedAdAction() {
        setContent {
            PlayQuotaExhaustedDialog(
                quota = quota(remaining = 0),
                rewardedAdState = RewardedAdGatewayState(
                    availability = RewardedAdAvailability.DEV_SIMULATED,
                    isReady = true
                ),
                isClaiming = false,
                onWatchAd = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithTag("play_quota_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("watch_rewarded_ad").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun unavailableRewardedAd_showsMobileGuidanceWithoutAction() {
        setContent {
            PlayQuotaExhaustedDialog(
                quota = quota(remaining = 0, availability = RewardedAdAvailability.UNAVAILABLE),
                rewardedAdState = RewardedAdGatewayState(
                    availability = RewardedAdAvailability.UNAVAILABLE,
                    isReady = false
                ),
                isClaiming = false,
                onWatchAd = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithTag("rewarded_ad_unavailable").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("watch_rewarded_ad").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun exhaustedQuota_smallPhoneLargeText_keepsActionsReachable() =
        assertResponsiveQuotaDialog(width = 320.dp, height = 568.dp, fontScale = 1.6f)

    @Test
    fun exhaustedQuota_largePhone_keepsActionsReachable() =
        assertResponsiveQuotaDialog(width = 430.dp, height = 932.dp)

    @Test
    fun exhaustedQuota_tablet_keepsDialogBounded() =
        assertResponsiveQuotaDialog(width = 840.dp, height = 1_180.dp)

    @Test
    fun exhaustedQuota_landscape_keepsActionsReachable() =
        assertResponsiveQuotaDialog(width = 720.dp, height = 400.dp, fontScale = 1.3f)

    private fun assertResponsiveQuotaDialog(width: Dp, height: Dp, fontScale: Float = 1f) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then
                    DeviceConfigurationOverride.FontScale(fontScale)
            ) {
                ProvideLocalization(AppLanguage.VIETNAMESE) {
                    FastToWinTheme {
                        PlayQuotaExhaustedDialog(
                            quota = quota(remaining = 0),
                            rewardedAdState = RewardedAdGatewayState(
                                availability = RewardedAdAvailability.DEV_SIMULATED,
                                isReady = true
                            ),
                            isClaiming = false,
                            onWatchAd = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }

        val dialogBounds = composeRule.onNodeWithTag("play_quota_dialog")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("Quota dialog must have a positive size", dialogBounds.width > 0f && dialogBounds.height > 0f)

        composeRule.onNodeWithTag("play_quota_close").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("watch_rewarded_ad").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme { content() }
            }
        }
    }

    private fun quota(
        remaining: Int,
        availability: RewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
    ) = PlayQuotaSnapshot(
        quotaDate = "2026-09-12",
        matchesConsumed = 10 - remaining,
        remainingMatches = remaining,
        nextResetAtEpochMillis = 1_789_148_400_000L,
        rewardedAdAvailability = availability
    )
}
