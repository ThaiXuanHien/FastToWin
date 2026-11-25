package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen

@PreviewTest
@Preview(name = "31_days_phone", widthDp = 390, heightDp = 2200)
@Preview(name = "31_days_desktop", widthDp = 840, heightDp = 1660)
@Composable
fun monthlyRewards() = MonthlyCheckInFixture("2026-10-17")

@PreviewTest
@Preview(name = "leap_year_large_text", widthDp = 360, heightDp = 3100, fontScale = 1.6f)
@Composable
fun leapYearRewards() = MonthlyCheckInFixture("2028-02-23")

@Composable
private fun MonthlyCheckInFixture(today: String) = ArcadeScreenshotFrame {
    val base = ArcadeScreenshotFixtures.profile()
    val checkIn = base.progression.dailyCheckIn
    val month = today.substring(0, 7)
    val history = checkIn.historyRewards.mapKeys { (date, _) -> month + date.takeLast(3) }
    val profile = base.copy(
        progression = base.progression.copy(
            dailyCheckIn = checkIn.copy(todayDate = today, historyDates = history.keys.toList(), historyRewards = history)
        )
    )
    ProfileSectionScreen(
        state = ArcadeScreenshotFixtures.homeState().copy(profile = profile),
        profile = profile,
        section = ProfileSection.DAILY_CHECK_IN,
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
