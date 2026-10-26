package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.*
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.screens.*
import com.hienthai.fastowin.ui.theme.FastToWinTheme

private const val raceStart = 1792947600000L
private const val raceEnd = raceStart + 604800000L
private val raceReward = listOf(ClanRaceReward(250, 1000, 30), ClanRaceReward(100, 500, 15), ClanRaceReward(50, 250, 5))
private val raceOwn = ClanRaceStanding(51, "own", "Bang hội Những Người Bạn Đồng Hành Rất Lâu / A very long clan name", "dragon", 99,
    "1", "0.000001", raceStart)
private val raceLeader = ClanRaceStanding(1, "leader", "Những Nhà Vô Địch / Champions of the extraordinary weekly race", "crown", 999,
    "99999999999999999999999999999999999999", "99999999999999999999999999999999.999999", raceStart)
private val raceCurrent = ClanRaceCurrent("2026-10-26", raceStart, raceEnd, ClanRaceState.ACTIVE,
    ClanRaceConfig("0.000001", "1", raceReward), 8, raceStart + 3600000L)
private val frozenLeader = raceLeader.copy(name = "Tên bang đã chốt / Frozen former clan name")
private val raceSnapshot = raceCurrent.copy(weekStart = "2026-10-19", startEpochMillis = raceStart - 604800000L,
    endEpochMillis = raceStart, state = ClanRaceState.SETTLED,
    settlement = ClanRaceSettlementStatus(ClanRaceSettlementState.SETTLED,
        result = ClanRaceSettlementResult("2026-10-19", 7, raceStart, raceStart + 1000,
            listOf(frozenLeader), listOf(ClanRaceWinner(frozenLeader, raceReward.first(), 50)))))
private fun raceFixture() = ClanRaceUiState(raceCurrent,
    ClanRaceStandings(raceCurrent.weekStart, listOf(raceLeader), 0, 25, 51, raceOwn, raceStart),
    ClanRaceContributions(raceCurrent.weekStart, "own", listOf(
        ClanRaceContribution("member", "Thành viên đóng góp rất nhỏ / Small positive contributor", "current", true, true, "1", "0", "1", "0.000001"),
        ClanRaceContribution("member", "Lần gia nhập cũ / Former membership", "old", false, false, "9999999", "1000000", "10999999", "10.999999")), raceStart),
    ClanRaceHistory(listOf(raceSnapshot), 0, 25, 1, raceStart), lastRefreshedAtEpochMillis = raceStart)

@Composable private fun RaceFrame(language: AppLanguage = AppLanguage.VIETNAMESE, content: @Composable () -> Unit) {
    ProvideLocalization(language) {
        FastToWinTheme(preferences = AppPreferences(themeMode = AppThemeMode.DARK, visualEffectsEnabled = false)) {
            Surface(Modifier.fillMaxSize()) { content() }
        }
    }
}
@PreviewTest
@Preview(name = "prizes_vi_phone", widthDp = 375, heightDp = 1100)
@Preview(name = "prizes_vi_desktop", widthDp = 1024, heightDp = 1000)
@Composable
fun racePrizeCardsPreview() = RaceFrame {
    // Visual fixture only; rewards remain entirely admin-configured at runtime.
    val current = raceCurrent.copy(config = ClanRaceConfig("0.001", "1", listOf(
        ClanRaceReward(30, 300, 3), ClanRaceReward(20, 200, 2), ClanRaceReward(10, 100, 1)
    )))
    ClanRaceScreen(ClanRaceUiState(current = current), {}, {})
}
@PreviewTest @Preview(name = "race_vi_phone_375", widthDp = 375, heightDp = 1700) @Composable
fun racePhone() = RaceFrame { ClanRaceScreen(raceFixture(), {}, {}) }
@PreviewTest @Preview(name = "entry_vi_phone_375", widthDp = 375, heightDp = 900) @Composable
fun raceClanEntry() = RaceFrame {
    val state = ArcadeScreenshotFixtures.clanState()
    ClanScreen(serverUrl = "", currentUserId = state.profile?.userId, myClanId = state.profile?.clanId,
        clanList = state.clanList, pendingJoinClanIds = emptySet(), currentClan = state.currentClan, notice = null,
        onCreateClan = { _, _ -> }, onJoinClan = {}, onLeaveClan = {}, onSearch = {}, onKickMember = { _, _ -> },
        onRespondJoinRequest = { _, _, _ -> }, onUpdateLogo = { _, _ -> }, onClaimQuest = {}, onViewClan = {}, onBack = {},
        onOpenRace = {})
}
@PreviewTest @Preview(name = "race_en_desktop_1024", widthDp = 1024, heightDp = 1700) @Composable
fun raceDesktop() = RaceFrame(AppLanguage.ENGLISH) { ClanRaceScreen(raceFixture(), {}, {}) }
@PreviewTest @Preview(name = "race_en_landscape_844", widthDp = 844, heightDp = 390) @Composable
fun raceLandscape() = RaceFrame(AppLanguage.ENGLISH) { ClanRaceScreen(raceFixture(), {}, {}) }
@PreviewTest @Preview(name = "race_vi_font_160", widthDp = 375, heightDp = 1700, fontScale = 1.6f) @Composable
fun raceLargeFont() = RaceFrame { ClanRaceScreen(raceFixture().copy(current = raceCurrent.copy(config = null)), {}, {}) }
@PreviewTest @Preview(name = "inactive_vi_375", widthDp = 375, heightDp = 850) @Composable
fun raceInactive() = RaceFrame { ClanRaceScreen(ClanRaceUiState(current = raceCurrent.copy(state = ClanRaceState.INACTIVE, config = null)), {}, {}) }
@PreviewTest @Preview(name = "loading_en_375", widthDp = 375, heightDp = 850) @Composable
fun raceLoading() = RaceFrame(AppLanguage.ENGLISH) { ClanRaceScreen(ClanRaceUiState(isLoading = true), {}, {}) }
@PreviewTest @Preview(name = "error_en_375", widthDp = 375, heightDp = 850) @Composable
fun raceError() = RaceFrame(AppLanguage.ENGLISH) { ClanRaceScreen(ClanRaceUiState(loadError = "STORAGE_UNAVAILABLE"), {}, {}) }
@PreviewTest @Preview(name = "pending_en_375", widthDp = 375, heightDp = 850) @Composable
fun racePending() = RaceFrame(AppLanguage.ENGLISH) { ClanRaceScreen(ClanRaceUiState(history = ClanRaceHistory(listOf(raceSnapshot.copy(state = ClanRaceState.CLOSED, settlement = ClanRaceSettlementStatus(ClanRaceSettlementState.PENDING))), 0, 25, 1, raceStart)), {}, {}) }
@PreviewTest @Preview(name = "settled_en_1024", widthDp = 1024, heightDp = 1000) @Composable
fun raceSettled() = RaceFrame(AppLanguage.ENGLISH) { ClanRaceScreen(ClanRaceUiState(history = ClanRaceHistory(listOf(raceSnapshot), 0, 25, 1, raceStart)), {}, {}) }
@PreviewTest @Preview(name = "mailbox_vi_375_font160", widthDp = 375, heightDp = 1400, fontScale = 1.6f) @Composable
fun raceMailbox() = RaceFrame {
    val gift = MailboxGift("race", "server fallback", "server fallback", "fallback body", "fallback body", 500, 100,
        raceStart, raceEnd, GiftState.AVAILABLE, gems = 15,
        clanRace = ClanRaceMailboxContext("entitlement", "2026-10-19", raceStart - 604800000L, raceStart, "own", raceOwn.name, 2,
            "1", "0.000001", true, raceStart, raceStart, raceEnd, raceReward[1], "CLAN_WEEKLY_RACE"))
    GiftMailboxScreen(GiftMailboxState(isOpen = true, items = listOf(gift), total = 1), false,
        {}, {}, {}, {}, {}, {}, nowMillis = raceStart)
}
