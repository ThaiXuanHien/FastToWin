package com.hienthai.fastowin

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Device acceptance of the real dialogs with isolated protocol fixtures, no live accounts. */
class UgcSafetyUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val playerId = "12345678-1234-1234-1234-123456789abc"

    @Test fun consentRequiresAnExplicitCheckAndDisablesWhilePending() {
        var state by mutableStateOf(UgcState(terms = ContentTermsStatus("2026-10-20", null, true), showTerms = true))
        var accepts = 0
        render {
            ContentTermsDialog(state, { state = state.copy(termsChecked = it) },
                { accepts++; state = state.copy(termsPending = true) }, {}, {})
        }
        composeRule.onNodeWithTag("ugc_terms_checkbox").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("ugc_terms_accept").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("ugc_terms_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("ugc_terms_accept").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("ugc_terms_accept").assertIsNotEnabled()
        composeRule.onNodeWithTag("ugc_terms_checkbox").performScrollTo().assertIsOn().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, accepts) }
        capture("consent-pending")
    }

    @Test fun uncertainReportRetainsTypedNoteAndRetriesTheSameRequestBeforeReceipt() {
        val sent = CopyOnWriteArrayList<ClientMessage.ReportContent>()
        val controller = UgcController { if (it is ClientMessage.ReportContent) sent.add(it) }
        controller.openReport(UgcTargetType.PLAYER_PROFILE, playerId, "<b>QA player</b>")
        render {
            val state by controller.state.collectAsState()
            val scope = rememberCoroutineScope()
            state.report?.let { draft ->
                UgcReportDialog(draft, { reason, note, type -> controller.editReport(reason, note, type) },
                    { scope.launch { controller.submitReport() } }, controller::dismissReport)
            }
        }
        composeRule.onNodeWithTag("ugc_report_note").performScrollTo().performTextInput("Samsung QA details")
        closeSoftKeyboard()
        capture("report-draft")
        composeRule.onNodeWithTag("ugc_report_submit").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { sent.size == 1 }
        composeRule.onNodeWithTag("ugc_report_submit").assertIsNotEnabled()
        composeRule.onNodeWithText("Receipt:", substring = true).assertDoesNotExist()
        composeRule.runOnIdle { controller.disconnected() }
        composeRule.onNodeWithTag("ugc_report_note").performScrollTo()
            .assertTextContains("Samsung QA details").assertIsNotEnabled()
        composeRule.onNodeWithTag("ugc_report_submit").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { sent.size == 2 }
        composeRule.runOnIdle {
            assertEquals("Samsung QA details", sent[0].request.note)
            assertEquals(playerId, sent[0].request.targetId)
            assertEquals(UgcTargetType.PLAYER_PROFILE, sent[0].request.targetType)
            assertEquals(sent[0].request, sent[1].request)
            runBlocking { controller.receive(ServerMessage.ContentReportResult(
                sent[1].request.requestId, receipt = ContentReportReceipt("qa-receipt"))) }
        }
        composeRule.onNodeWithText("Receipt: qa-receipt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ugc_report_submit").assertDoesNotExist()
        capture("report-receipt")
    }

    @Test fun vietnameseBlockConfirmationKeepsPlayerMarkupAsTextAndRequiresAction() {
        var blocks = 0
        render(AppLanguage.VIETNAMESE) {
            UgcBlockConfirmationDialog("<b>QA player</b>", { blocks++ }, {})
        }
        composeRule.onNodeWithText("CHẶN <b>QA player</b>?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, blocks) }
        capture("block-confirmation-vi")
        composeRule.onNodeWithText("CHẶN", useUnmergedTree = true).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, blocks) }
    }

    @Test fun reportControlsHideSelfAndMissingTargetsButKeepOtherPlayers() {
        var selected: UgcTarget? = null
        render {
            ProvideUgcReportActions(playerId, { selected = it }) {
                Column {
                    UgcReportButton(UgcTargetType.PLAYER_PROFILE, playerId, "Self")
                    UgcReportButton(UgcTargetType.PLAYER_AVATAR, null, "Missing")
                    UgcReportButton(UgcTargetType.PLAYER_PROFILE, "other-player", "Other")
                }
            }
        }
        composeRule.onNodeWithTag("report:PLAYER_PROFILE:$playerId").assertDoesNotExist()
        composeRule.onNodeWithTag("report:PLAYER_AVATAR:null").assertDoesNotExist()
        composeRule.onNodeWithTag("report:PLAYER_PROFILE:other-player").performClick()
        composeRule.runOnIdle { assertEquals(UgcTarget(UgcTargetType.PLAYER_PROFILE, "other-player", "Other"), selected) }
    }

    private fun render(language: AppLanguage = AppLanguage.ENGLISH, content: @Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalization(language) {
                FastToWinTheme(preferences = AppPreferences(themeMode = AppThemeMode.DARK, visualEffectsEnabled = false), content = content)
            }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ugc-qa").apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }
}
