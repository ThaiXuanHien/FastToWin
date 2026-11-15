package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.ui.components.RewardReceiptAmounts
import com.hienthai.fastowin.ui.components.RewardReceiptDialog
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun playCreditReceipt() = ArcadeScreenshotFrame {
    RewardReceiptDialog(RewardReceiptAmounts(plays = 15), {})
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun goldExchangeReceipt() = ArcadeScreenshotFrame {
    RewardReceiptDialog(RewardReceiptAmounts(gold = 10000), {})
}

@PreviewTest
@Preview(name = "gold_receipt_en_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun goldExchangeReceiptEnglish() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) {
        RewardReceiptDialog(RewardReceiptAmounts(gold = 10000), {})
    }
}

@PreviewTest
@Preview(name = "reward_large_text", widthDp = 320, heightDp = 568, fontScale = 1.6f)
@Preview(name = "reward_landscape_large_text", widthDp = 844, heightDp = 390, fontScale = 1.6f)
@Composable
fun multipleRewardsLargeText() = ArcadeScreenshotFrame {
    RewardReceiptDialog(RewardReceiptAmounts(gold = 10000, xp = 500, gems = 50, plays = 15), {})
}
