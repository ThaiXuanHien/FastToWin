package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.data.network.StoredAuthSession
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.state.AuthState
import com.hienthai.fastowin.state.VerificationDelivery
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

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
                        onRegister = { _, _, _, _, _ -> },
                        onUpgradeGuest = { _, _, _ -> },
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
            .assertTextEquals("hien@example.com")
        composeRule.onNodeWithTag("auth_verification_delivery")
            .assertTextEquals("Enter the 6-digit code from your email. Each code expires after 15 minutes.")
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

    @Test
    fun failedDeliveryOffersEmailCorrectionFromTheVerificationScreen() {
        var submittedEmail: String? = null
        setAuthContent(
            AuthState(
                stage = AuthStage.VERIFY_EMAIL,
                session = StoredAuthSession(
                    "user-id", "old@example.com", "Hien", "access", "refresh",
                    Long.MAX_VALUE, Long.MAX_VALUE, emailVerified = false
                ),
                verificationDelivery = VerificationDelivery.FAILED
            ),
            onChangePendingEmail = { submittedEmail = it }
        )

        composeRule.onNodeWithTag("auth_verification_delivery")
            .assertTextEquals("Could not send the verification code. Try sending it again.")
        composeRule.onNodeWithTag("auth_verification_edit_email").performClick()
        composeRule.onNodeWithTag("auth_verification_save_email").assertIsNotEnabled()
        composeRule.onNodeWithTag("auth_verification_new_email").performTextInput("corrected@example.com")
        composeRule.onNodeWithTag("auth_verification_save_email").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("corrected@example.com", submittedEmail) }
    }

    private fun setAuthContent(state: AuthState, onChangePendingEmail: (String) -> Unit = {}) {
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
                        onRegister = { _, _, _, _, _ -> },
                        onUpgradeGuest = { _, _, _ -> },
                        onRequestPasswordReset = {},
                        onConfirmPasswordReset = { _, _, _ -> },
                        onRequestEmailVerification = {},
                        onConfirmEmailVerification = {},
                        onBack = {},
                        onCancelUpgrade = {},
                        onCancelEmailVerification = {},
                        onChangePendingEmail = onChangePendingEmail,
                    )
                }
            }
        }
    }
}
