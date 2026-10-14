package com.hienthai.fastowin

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.OfflineScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ProfileEditorState
import com.hienthai.fastowin.ui.screens.rememberProfileEditorState
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileDraftReconnectUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val online = mutableStateOf(true)
    private val active = mutableStateOf(true)
    private val owner = mutableStateOf<String?>("account-a")
    private val notice = mutableStateOf<String?>(null)
    private val image = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", 0)
    private var saves = 0
    private var savedName: String? = null
    private var savedImage: ByteArray? = null
    private var lateImage: ((ByteArray?) -> Unit)? = null
    private var holdNextPicker = false

    @Test fun draftSurvivesOfflineWithoutAutoSavingAndSavesOnlyOnConfirmation() {
        render()
        editDraft()
        reconnect()
        assertDraft()
        assertEquals(0, saves)
        composeRule.onNodeWithText("Lưu").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, saves)
            assertEquals("Draft name", savedName)
            assertArrayEquals(image, savedImage)
            notice.value = "Đã lưu hồ sơ."
        }
        composeRule.onNodeWithTag("profile_display_name").assertDoesNotExist()
    }

    @Test fun cancelAfterReconnectDiscardsDraftAndLatePickerResult() {
        render()
        editDraft()
        composeRule.runOnIdle { holdNextPicker = true }
        composeRule.onNodeWithTag("profile_avatar_picker").performScrollTo().performClick()
        reconnect()
        assertDraft()
        composeRule.onNodeWithText("Lưu").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Hủy").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_edit").performScrollTo().performClick()
        composeRule.runOnIdle { lateImage?.invoke(image) }
        composeRule.onNodeWithTag("profile_display_name").assertTextContains("Saved account-a")
        composeRule.onNodeWithTag("profile_avatar_preview").assertDoesNotExist()
        assertEquals(0, saves)
    }

    @Test fun oldSuccessNoticeDoesNotDiscardNewDraftOnReconnect() {
        notice.value = "Đã lưu hồ sơ."
        render()
        editDraft()
        reconnect()
        assertDraft()
        assertEquals(0, saves)
    }

    @Test fun switchingAccountDropsDraftRatherThanShowingItToAnotherAccount() {
        render()
        editDraft()
        composeRule.runOnIdle { owner.value = "account-b" }
        composeRule.onNodeWithTag("profile_display_name").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_display_name").assertTextContains("Saved account-b")
        composeRule.onNodeWithTag("profile_avatar_preview").assertDoesNotExist()
        composeRule.runOnIdle { owner.value = "account-a" }
        composeRule.onNodeWithTag("profile_display_name").assertDoesNotExist()
        assertEquals(0, saves)
    }

    @Test fun logoutDropsDraftEvenWhenTheSameAccountLogsInAgain() {
        render()
        editDraft()
        composeRule.runOnIdle { owner.value = null }
        composeRule.onNodeWithTag("profile_display_name").assertDoesNotExist()
        composeRule.runOnIdle { owner.value = "account-a" }
        composeRule.onNodeWithTag("profile_display_name").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_display_name").assertTextContains("Saved account-a")
        composeRule.onNodeWithTag("profile_avatar_preview").assertDoesNotExist()
        assertEquals(0, saves)
    }

    @Test fun leavingProfileDiscardsDraftWithoutSaving() {
        render()
        editDraft()
        composeRule.runOnIdle { active.value = false }
        composeRule.onNodeWithTag("profile_display_name").assertDoesNotExist()
        composeRule.runOnIdle { active.value = true }
        composeRule.onNodeWithTag("profile_display_name").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_display_name").assertTextContains("Saved account-a")
        composeRule.onNodeWithTag("profile_avatar_preview").assertDoesNotExist()
        assertEquals(0, saves)
    }

    private fun editDraft() {
        composeRule.onNodeWithTag("profile_edit").performClick()
        composeRule.onNodeWithTag("profile_display_name").performScrollTo().performTextReplacement("Draft name")
        composeRule.onNodeWithTag("profile_avatar_picker").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_avatar_preview").assertIsDisplayed()
    }

    private fun reconnect() {
        composeRule.runOnIdle { online.value = false }
        composeRule.onNodeWithTag("offline_screen").assertIsDisplayed()
        composeRule.runOnIdle { online.value = true }
    }

    private fun assertDraft() {
        composeRule.onNodeWithTag("profile_display_name").performScrollTo().assertTextContains("Draft name")
        composeRule.onNodeWithTag("profile_avatar_preview").performScrollTo().assertIsDisplayed()
    }

    private fun render() {
        composeRule.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme {
                    owner.value?.let { id ->
                        key(id) {
                            // Mirror GameContent's session owner above its offline/profile branch.
                            val editor = rememberProfileEditorState(id, notice.value, active.value)
                            if (!active.value) BasicText("Other screen")
                            else if (online.value) Profile(id, editor)
                            else OfflineScreen(onRetry = {}, onPractice = {})
                        }
                    }
                }
            }
        }
    }

    @Composable private fun Profile(id: String, editor: ProfileEditorState) {
        ProfileScreen(
            serverUrl = "", state = GameState(profile = PlayerProfileSnapshot(
                userId = id, displayName = "Saved $id", playerCode = "QA001"), profileNotice = notice.value),
            onBack = {}, onRefresh = {}, onOpenMatchDetail = {}, onCloseMatchDetail = {},
            onEquipCosmetics = { _, _ -> }, onClaimMissionReward = {},
            onSave = { name, _, bytes -> saves++; savedName = name; savedImage = bytes },
            canEdit = true, isAccountLoading = false, accountError = null, accountNotice = null,
            accountSessions = emptyList(), areSessionsLoading = false,
            onChangePassword = { _, _ -> }, onDeleteAccount = {}, onClearAccountFeedback = {},
            onLoadSessions = {}, onRevokeSession = {}, onRevokeAllSessions = {}, onLogout = {},
            showBackButton = false, profileEditState = editor,
            imagePicker = { selected, content ->
                content {
                    if (holdNextPicker) lateImage = selected else selected(image)
                }
            }
        )
    }
}
