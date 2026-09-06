package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.state.AuthState
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class LocalizedAuthUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchingLanguageUpdatesLoginWithoutClearingEmail() {
        val language = mutableStateOf(AppLanguage.VIETNAMESE)
        composeRule.setContent {
            ProvideLocalization(language.value) {
                FastToWinTheme {
                    AuthScreen(
                        state = AuthState(stage = AuthStage.LOGIN),
                        onOpenLogin = {},
                        onOpenRegister = {},
                        onOpenPasswordReset = {},
                        onPlayAsGuest = {},
                        onLogin = { _, _ -> },
                        onRegister = { _, _, _, _ -> },
                        onUpgradeGuest = { _, _ -> },
                        onRequestPasswordReset = {},
                        onConfirmPasswordReset = { _, _, _ -> },
                        onRequestEmailVerification = {},
                        onConfirmEmailVerification = {},
                        onBack = {},
                        onCancelUpgrade = {},
                        onCancelEmailVerification = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("auth_email").performTextInput("hien@example.com")
        composeRule.onNodeWithText("Đăng nhập").assertIsDisplayed()

        composeRule.runOnIdle { language.value = AppLanguage.ENGLISH }

        composeRule.onNodeWithText("Login").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email").assertTextContains("hien@example.com")
    }
}
