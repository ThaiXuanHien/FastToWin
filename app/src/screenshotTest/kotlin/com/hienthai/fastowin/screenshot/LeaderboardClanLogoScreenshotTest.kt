package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.ui.screens.LeaderboardScreen

@PreviewTest
@Preview(name = "player_clans_vi_phone", widthDp = 375, heightDp = 1100)
@Preview(name = "player_clans_vi_large_text", widthDp = 320, heightDp = 1400, fontScale = 1.6f)
@Preview(name = "player_clans_vi_desktop", widthDp = 1024, heightDp = 1100)
@Composable
fun leaderboardWithClanIdentity() = ArcadeScreenshotFrame {
    val state = ArcadeScreenshotFixtures.leaderboardState()
    val leaderboard = requireNotNull(state.leaderboard)
    val entries = leaderboard.seasonTopPlayers.mapIndexed { index, entry ->
        when (index) {
            0 -> entry.copy(clanId = "clan-dragon", clanName = "Bang Rồng Thần Tốc", clanLogoId = "dragon")
            1 -> entry.copy(clanId = "clan-phoenix", clanName = "Bang Phượng Hoàng Bất Diệt", clanLogoId = "phoenix")
            else -> entry
        }
    }
    LeaderboardScreen(
        state = state.copy(profile = null, leaderboard = leaderboard.copy(
            seasonTopPlayers = entries,
            seasonCurrentPlayer = entries[1]
        )),
        onBack = {}, onRefresh = {}, onOpenFriendProfile = {}
    )
}
