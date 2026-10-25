package com.hienthai.fastowin

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.ClanRaceUiState
import com.hienthai.fastowin.ui.screens.ClanRaceScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** Compile-checked here; execution remains opt-in on an already authorized device. */
class ClanRaceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun refreshHasAccessibleTextAndNativeAction() {
        var refreshes = 0
        compose.setContent {
            ProvideLocalization(AppLanguage.ENGLISH) { FastToWinTheme { Surface {
                ClanRaceScreen(ClanRaceUiState(loadError = "STORAGE_UNAVAILABLE"), {}, { refreshes++ })
            } } }
        }
        compose.onNodeWithText("Refresh race").assertHasClickAction().performClick()
        compose.onNodeWithTag("race_error").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun outsidePageRankAndTinyEligiblePointsAreScrollableAndExact() {
        val week = "2026-10-26"
        val own = ClanRaceStanding(51, "own", "Long clan name repeated to preserve wrapping and readable identity", "crown", 99, "1", "0.000001", 1)
        val state = ClanRaceUiState(
            current = ClanRaceCurrent(week, 1, 9, ClanRaceState.ACTIVE, serverNowEpochMillis = 2),
            standings = ClanRaceStandings(week, emptyList(), 0, 25, 51, own, 2),
            contributions = ClanRaceContributions(week, "own", listOf(
                ClanRaceContribution("me", "Me", "current", true, true, "1", "0", "1", "0.000001")), 2))
        compose.setContent { ProvideLocalization(AppLanguage.ENGLISH) { FastToWinTheme { Surface { ClanRaceScreen(state, {}, {}) } } } }
        compose.onNodeWithTag("clan_race").performScrollToNode(hasTestTag("race_rank_own"))
        compose.onNodeWithTag("race_rank_own").assertTextEquals("Rank 51 · 0.000001 points")
        compose.onNodeWithTag("clan_race").performScrollToNode(hasTestTag("race_eligibility_current"))
        compose.onNodeWithTag("race_eligibility_current").assertTextEquals("Eligible at cutoff if membership continues")
    }
}
