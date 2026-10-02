package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.protocol.GiftState
import com.hienthai.fastowin.protocol.MailboxGift
import com.hienthai.fastowin.state.GiftMailboxState
import com.hienthai.fastowin.ui.screens.GiftMailboxScreen

private val previewGifts = listOf(
    MailboxGift(
        "event-one", "Quà sự kiện tháng Mười", "October event gift",
        "Cảm ơn bạn đã đồng hành cùng FastToWin!", "Thank you for playing FastToWin!",
        250, 40, ArcadeScreenshotFixtures.NOW_MILLIS - 86_400_000,
        ArcadeScreenshotFixtures.NOW_MILLIS + 172_800_000, GiftState.AVAILABLE
    ),
    MailboxGift(
        "event-two", "Quà chào mừng", "Welcome gift",
        "Chúc bạn có những trận đấu thú vị.", "Enjoy your next matches.",
        100, 20, ArcadeScreenshotFixtures.NOW_MILLIS - 604_800_000,
        ArcadeScreenshotFixtures.NOW_MILLIS + 604_800_000, GiftState.CLAIMED,
        ArcadeScreenshotFixtures.NOW_MILLIS - 86_400_000
    ),
    MailboxGift(
        "event-three", "Quà đã hết hạn", "Expired gift",
        "Một món quà cũ từ đội ngũ.", "An earlier gift from the team.",
        50, 0, ArcadeScreenshotFixtures.NOW_MILLIS - 2_592_000_000,
        ArcadeScreenshotFixtures.NOW_MILLIS - 86_400_000, GiftState.EXPIRED
    )
)

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun giftMailbox() = GiftMailboxPreview()

@PreviewTest
@Preview(name = "desktop", widthDp = 1024, heightDp = 768, showSystemUi = false)
@Composable
fun giftMailboxDesktop() = GiftMailboxPreview()

@Composable
private fun GiftMailboxPreview() = ArcadeScreenshotFrame {
    GiftMailboxScreen(
        state = GiftMailboxState(isOpen = true, items = previewGifts, total = previewGifts.size.toLong()),
        isGuest = false, onBack = {}, onRefresh = {}, onLoadMore = {}, onClaim = {},
        onClaimAll = {}, onUpgradeGuest = {}, nowMillis = ArcadeScreenshotFixtures.NOW_MILLIS
    )
}
