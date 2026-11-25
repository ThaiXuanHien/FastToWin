package com.hienthai.fastowin

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.hienthai.fastowin.data.network.GiftApiException
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.GiftMailboxState
import com.hienthai.fastowin.ui.screens.*
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real-device UI acceptance with isolated rewards; never spends a live player's gems. */
class PlayCreditsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun claimedAdminGiftKeepsTheClaimButtonDisabled() {
        val gift = mutableStateOf(MailboxGift(
            "admin-gift", "Quà admin", "Admin gift", "Cảm ơn bạn!", "Thank you!",
            100, 20, 0L, 172_800_000L, GiftState.AVAILABLE
        ))
        render {
            GiftMailboxScreen(
                state = GiftMailboxState(isOpen = true, items = listOf(gift.value), total = 1),
                isGuest = false,
                onBack = {},
                onRefresh = {},
                onLoadMore = {},
                onClaim = { gift.value = gift.value.copy(state = GiftState.CLAIMED) },
                onClaimAll = {},
                nowMillis = 0L
            )
        }

        val claim = composeRule.onNodeWithTag("gift_claim_admin-gift")
        claim.performScrollTo().assertIsEnabled().performClick()
        claim.assertIsNotEnabled()
        composeRule.onNodeWithText("Đã nhận").assertIsDisplayed()
    }

    @Test
    fun exchangeRequiresConfirmationAndRefreshesBankWithoutChangingDailyPlays() {
        val quota = mutableStateOf(quota())
        val gems = mutableStateOf(50)
        var exchanges = 0
        render {
            ShopScreen(
                progression = PlayerProgressionSnapshot(gems = gems.value), onClose = {},
                isAccount = true, initialTab = ShopTab.PLAYS, quota = quota.value,
                onExchangePlays = { offerId, requestId ->
                    assertEquals("plays_5", offerId)
                    assertTrue(requestId.isNotBlank())
                    exchanges++
                    gems.value -= 20
                    quota.value = quota.value.copy(bankedMatches = 20, remainingMatches = 22)
                }
            )
        }
        composeRule.onNodeWithText("Bạn còn 2 lượt").assertIsDisplayed()
        composeRule.onNodeWithText("Tích lũy 15 · hồi 5 lượt lúc 00:00").assertIsDisplayed()
        val header = composeRule.onNodeWithTag("shop_header").fetchSemanticsNode().boundsInRoot
        val tabs = composeRule.onNodeWithTag("shop_category_tabs").fetchSemanticsNode().boundsInRoot
        assertTrue(tabs.top - header.bottom >= with(composeRule.density) { 16.dp.toPx() } - 1)
        capture("play-credit-balances")
        composeRule.onNodeWithTag("play_credit_exchange:plays_5").performClick()
        composeRule.runOnIdle { assertEquals(0, exchanges) }
        composeRule.onNodeWithText("Đổi 20 Gem lấy 5 lượt tích lũy?").assertIsDisplayed()
        composeRule.onNodeWithText("Xác nhận", ignoreCase = true).performClick()
        composeRule.onNodeWithTag("reward_receipt_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("reward_receipt_plays").assertIsDisplayed()
        capture("play-credit-reward-popup")
        composeRule.onNodeWithTag("reward_receipt_close").performClick()
        composeRule.onNodeWithTag("reward_receipt_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("Đã đổi lượt chơi.").assertDoesNotExist()
        composeRule.onNodeWithText("Tích lũy 20 · hồi 5 lượt lúc 00:00").assertIsDisplayed()
        composeRule.onNodeWithText("Bạn còn 2 lượt").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, exchanges) }
        capture("play-credit-exchanged")
    }

    @Test
    fun uncertainExchangeRetriesTheSameRequestId() {
        val requestIds = mutableListOf<String>()
        render {
            ShopScreen(
                progression = PlayerProgressionSnapshot(gems = 50), onClose = {},
                isAccount = true, initialTab = ShopTab.PLAYS, quota = quota(),
                onExchangePlays = { _, requestId ->
                    requestIds += requestId
                    if (requestIds.size == 1) throw GiftApiException("NETWORK_ERROR", "Uncertain response")
                }
            )
        }
        repeat(2) {
            composeRule.onNodeWithTag("play_credit_exchange:plays_5").performClick()
            composeRule.onNodeWithText("Xác nhận", ignoreCase = true).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("reward_receipt_dialog").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, requestIds.size)
            assertEquals(requestIds.first(), requestIds.last())
        }
    }

    @Test
    fun goldExchangeShowsActualGrantedRewardAndAcknowledgesItOnce() {
        val receipt = mutableStateOf<ServerMessage.GoldExchangeResult?>(null)
        val acknowledged = mutableListOf<String>()
        render {
            ShopScreen(
                progression = PlayerProgressionSnapshot(gems = 50), onClose = {}, initialTab = ShopTab.GOLD,
                goldExchangeResult = receipt.value,
                onExchangeGold = { offerId -> receipt.value = ServerMessage.GoldExchangeResult(
                    "gold-receipt-1", offerId, receivedGold = 1234, status = GoldExchangeStatus.GRANTED
                ) },
                onDismissGoldExchangeResult = { acknowledged += it }
            )
        }
        composeRule.onNodeWithTag("gold_exchange:gold_bag").performScrollTo().performClick()
        composeRule.onNodeWithTag("gold_exchange_confirm").performClick()
        composeRule.onNodeWithTag("reward_receipt_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("reward_receipt_gold").assertIsDisplayed()
        composeRule.onNodeWithText("+1.234", substring = true).assertIsDisplayed()
        capture("gold-reward-popup")
        composeRule.onNodeWithTag("reward_receipt_close").performClick()
        composeRule.onNodeWithTag("reward_receipt_dialog").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf("gold-receipt-1"), acknowledged) }
        // Keeping the same receipt in parent state must not replay its popup.
        composeRule.onNodeWithTag("shop_tab:PLAYS").performClick()
        composeRule.onNodeWithTag("shop_tab:GOLD").performClick()
        composeRule.onNodeWithTag("reward_receipt_dialog").assertDoesNotExist()
        composeRule.runOnIdle { receipt.value = receipt.value!!.copy(requestId = "gold-receipt-2") }
        composeRule.onNodeWithTag("reward_receipt_dialog").assertIsDisplayed()
    }

    @Test
    fun failedOrAlreadyGrantedGoldExchangeDoesNotAnnounceNewRewards() {
        val receipt = mutableStateOf(ServerMessage.GoldExchangeResult(
            "already", "gold_bag", receivedGold = 1000, status = GoldExchangeStatus.ALREADY_GRANTED
        ))
        render { ShopScreen(
            progression = PlayerProgressionSnapshot(gems = 50), onClose = {}, initialTab = ShopTab.GOLD,
            goldExchangeResult = receipt.value, exchangeNotice = "Không đủ Gem"
        ) }
        composeRule.onNodeWithTag("reward_receipt_dialog").assertDoesNotExist()
        composeRule.runOnIdle { receipt.value = receipt.value.copy(status = GoldExchangeStatus.INSUFFICIENT_GEMS) }
        composeRule.onNodeWithTag("reward_receipt_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("Không đủ Gem").assertIsDisplayed()
    }

    @Test
    fun leaderboardPopupUsesExistingFriendActionAndShowsCenteredMetrics() {
        var sentCode: String? = null
        render {
            LeaderboardPlayerDialog(
                entry = LeaderboardEntrySnapshot(1, "Linh Chi", "LINHCHI", 93, 140, 50, eloRating = 2468, userId = "other"),
                state = GameState(isClanSocialReady = true), onDismiss = {}, onRefreshSocial = {},
                onSendFriendRequest = { sentCode = it }, onRespondFriendRequest = { _, _ -> },
                onUpgradeGuest = null
            )
        }
        capture("leaderboard-player-popup")
        composeRule.onNodeWithText("2.468").assertIsDisplayed()
        val label = composeRule.onNodeWithText("Thắng").fetchSemanticsNode().boundsInRoot
        val value = composeRule.onNodeWithText("93").fetchSemanticsNode().boundsInRoot
        assertTrue(label.bottom <= value.top)
        assertEquals(label.center.x, value.center.x, 1f)
        composeRule.onNodeWithTag("leaderboard_player_friend_action").performClick()
        composeRule.runOnIdle { assertEquals("LINHCHI", sentCode) }
    }

    private fun render(content: @Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private fun quota() = PlayQuotaSnapshot(
        quotaDate = "2026-11-15", baseMatches = 5, matchesConsumed = 3,
        remainingMatches = 17, nextResetAtEpochMillis = 1_800_000_000_000, bankedMatches = 15
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        // Platform window enter/exit animations are outside Compose's idling clock.
        SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "player-updates-qa").apply { mkdirs() }
        File(directory, "$name.txt").writeText(composeRule.onAllNodes(isRoot(), useUnmergedTree = true).printToString())
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }
}
