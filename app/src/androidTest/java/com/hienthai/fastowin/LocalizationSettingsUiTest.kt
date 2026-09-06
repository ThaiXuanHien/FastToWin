package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.localization.resolveSavedLanguage
import com.hienthai.fastowin.ui.screens.SettingsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalizationSettingsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun selectionUpdatesTitleClosesDialogAndKeepsScrollPosition() {
        val preferences = renderSettings()
        composeRule.onNodeWithTag("language_setting").performScrollTo().performClick()
        composeRule.onNodeWithTag("language_option_en").performScrollTo().performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("language_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("language_setting").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("en", preferences.value.languageCode) }
        composeRule.onNodeWithTag("language_setting").performClick()
        composeRule.onNodeWithTag("language_option_en").assertIsSelected()
    }

    @Test
    fun allThirteenOptionsCanBeReachedAndSystemUsesDeviceLanguage() {
        val preferences = renderSettings()
        composeRule.onNodeWithTag("language_setting").performScrollTo().performClick()
        listOf("system", "vi", "en", "zh-Hans", "ja", "ko", "es", "pt-BR", "fr", "de", "id", "th", "ru").forEach {
            composeRule.onNodeWithTag("language_option_$it").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("language_option_ru").performClick()
        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
        composeRule.onNodeWithTag("language_setting").performScrollTo().performClick()
        composeRule.onNodeWithTag("language_option_system").performScrollTo().performClick()
        composeRule.onNodeWithText("Cài đặt").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("system", preferences.value.languageCode) }
    }

    @Test
    fun closingWithoutSelectionDoesNotChangePreferences() {
        val preferences = renderSettings()
        composeRule.onNodeWithTag("language_setting").performScrollTo().performClick()
        composeRule.onNodeWithTag("language_close").performScrollTo().performClick()
        composeRule.onNodeWithTag("language_dialog").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("vi", preferences.value.languageCode) }
    }

    private fun renderSettings(): androidx.compose.runtime.MutableState<AppPreferences> {
        val preferences = mutableStateOf(AppPreferences(languageCode = "vi"))
        composeRule.setContent {
            ProvideLocalization(resolveSavedLanguage(preferences.value.languageCode, listOf("vi-VN"))) {
                FastToWinTheme(preferences.value) {
                    SettingsScreen(
                        preferences = preferences.value,
                        onPreferencesChange = { preferences.value = it },
                        onPreviewSound = {}, onOpenTutorial = {}, onBack = {}
                    )
                }
            }
        }
        return preferences
    }
}
