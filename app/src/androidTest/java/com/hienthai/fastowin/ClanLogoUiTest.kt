package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.screens.ClanLogoDialog
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ClanLogoUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun lockedLogoExplainsItsLevelAndCannotBeSelected() {
        var selected: String? = null
        composeRule.setContent {
            ProvideLocalization(AppLanguage.ENGLISH) {
                FastToWinTheme {
                    ClanLogoDialog("shield", clanLevel = 1, onSelect = { selected = it }, onDismiss = {})
                }
            }
        }
        composeRule.onNodeWithTag("clan_logo_sword").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("clan_logo_requirement_sword", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, selected) }
        composeRule.onNodeWithTag("clan_logo_flag").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("flag", selected) }
    }

    @Test
    fun legacySelectedLogoStaysCurrentWithoutReapplyingLockedChoice() {
        var selections = 0
        var closed = false
        composeRule.setContent {
            FastToWinTheme {
                ClanLogoDialog("crown", clanLevel = 1, onSelect = { selections++ }, onDismiss = { closed = true })
            }
        }
        composeRule.onNodeWithTag("clan_logo_crown").performScrollTo().assertIsSelected().assertIsNotEnabled()
        composeRule.onNodeWithTag("close_clan_logo_dialog").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(0, selections)
            assertEquals(true, closed)
        }
    }

    @Test
    fun levelBoundaryUnlocksLogoAndAlreadyCurrentChoiceDoesNotSubmit() {
        var selected: String? = null
        composeRule.setContent {
            FastToWinTheme {
                ClanLogoDialog("shield", clanLevel = 3, onSelect = { selected = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag("clan_logo_shield").assertIsSelected().assertIsNotEnabled()
        composeRule.onNodeWithTag("clan_logo_sword").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("sword", selected) }
        composeRule.onNodeWithTag("clan_logo_flag").performScrollTo().assertIsNotEnabled()
    }
}
