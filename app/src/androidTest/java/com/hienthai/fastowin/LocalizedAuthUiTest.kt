package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.hienthai.fastowin.data.network.StoredAuthSession
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

    @Test
    fun emailVerificationKeepsSensitiveValuesAtTheUiBoundary() {
        setAuthContent(
            AuthState(
                stage = AuthStage.VERIFY_EMAIL,
                session = StoredAuthSession(
                    userId = "user-id",
                    email = "hien@example.com",
                    displayName = "Hien",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    accessExpiresAtEpochMillis = Long.MAX_VALUE,
                    refreshExpiresAtEpochMillis = Long.MAX_VALUE,
                    emailVerified = false,
                ),
                devEmailVerificationCode = "654321",
            )
        )

        composeRule.onNodeWithTag("auth_verification_email")
            .assertTextEquals(
                "Enter the 6-digit code sent to the email address below. It expires in 15 minutes.\n" +
                    "hien@example.com"
            )
        composeRule.onNodeWithTag("auth_dev_verification_code")
            .assertTextEquals("Dev mode — code filled automatically:\n654321")
    }

    @Test
    fun passwordResetKeepsDevTokenAtTheUiBoundary() {
        setAuthContent(
            AuthState(
                stage = AuthStage.RESET_PASSWORD,
                passwordResetEmail = "hien@example.com",
                devResetToken = "reset-token",
            )
        )

        composeRule.onNodeWithTag("auth_dev_reset_code")
            .assertTextEquals("Dev mode — code filled automatically:\nreset-token")
    }

    private fun setAuthContent(state: AuthState) {
        composeRule.setContent {
            ProvideLocalization(AppLanguage.ENGLISH) {
                FastToWinTheme {
                    AuthScreen(
                        state = state,
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
                        onCancelEmailVerification = {},
                    )
                }
            }
        }
    }
}
