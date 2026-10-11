package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.TournamentScreen

private const val SCHEDULE_NOW = 1_792_040_100_000L

@PreviewTest
@Preview(name = "phone375", widthDp = 375, heightDp = 1000)
@Preview(name = "phone320_large", widthDp = 320, heightDp = 1000, fontScale = 1.4f)
@Preview(name = "landscape", widthDp = 844, heightDp = 390)
@Preview(name = "desktop", widthDp = 1024, heightDp = 900)
@Composable
fun scheduledTournamentLobby() = ArcadeScreenshotFrame {
    val tournament = TournamentSnapshot(
        "scheduled", "Cúp phản xạ cuối tuần", "player-1", ProtocolGameMode.ORDER, TournamentPhase.LOBBY,
        entryFee = 100, prizePool = 400,
        players = listOf(
            TournamentPlayerSnapshot("player-1", "HienTX", isHost = true, isOnline = true),
            TournamentPlayerSnapshot("player-2", "Minh Anh", isOnline = true, isCheckedIn = true),
            TournamentPlayerSnapshot("player-3", "Kenji", isOnline = true, isCheckedIn = true),
            TournamentPlayerSnapshot("player-4", "An", isOnline = false)
        ), createdAtEpochMillis = SCHEDULE_NOW - 3_600_000,
        schedule = TournamentScheduleSnapshot(SCHEDULE_NOW + 300_000, "Asia/Bangkok", SCHEDULE_NOW - 600_000, SCHEDULE_NOW - 600_000),
        serverTimeEpochMillis = SCHEDULE_NOW
    )
    TournamentScreen(
        state = GameState(player = PlayerState("HienTX", id = "player-1"),
            profile = ArcadeScreenshotFixtures.profile(), connectionStatus = ConnectionStatus.CONNECTED,
            tournamentHub = TournamentHubSnapshot(activeTournament = tournament)),
        onBack = {}, onCreate = { _, _, _, _, _ -> }, onInvite = {}, onRespondInvitation = { _, _ -> },
        onStart = {}, onLeave = {}, onOpenFriendProfile = {}, onCreateScheduled = { _, _, _, _, _, _ -> },
        onCheckIn = {}, nowEpochMillis = SCHEDULE_NOW
    )
}
