package com.hienthai.fastowin

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.screens.MaintenanceScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MaintenanceScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun maintenance_smallPhone_largeText_isInformationalAndHasNoActions() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                    DeviceConfigurationOverride.FontScale(1.3f)
            ) {
                ProvideLocalization(AppLanguage.VIETNAMESE) {
                    FastToWinTheme {
                        ArcadeBackdrop {
                            MaintenanceScreen(message = "Máy chủ đang nâng cấp dữ liệu mùa giải.")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("maintenance_screen").assertIsDisplayed()
        composeRule.onNodeWithText("MÁY CHỦ ĐANG\nNGHỈ GIỮA HIỆP").assertIsDisplayed()
        composeRule.onNodeWithText("Máy chủ đang nâng cấp dữ liệu mùa giải.").assertIsDisplayed()
        composeRule.onNodeWithText("THỬ LẠI").assertDoesNotExist()
        composeRule.onNodeWithText("LUYỆN TẬP OFFLINE").assertDoesNotExist()
    }

    @Test
    fun maintenance_usesVietnameseEnglishAndJapaneseCatalogs() {
        val language = androidx.compose.runtime.mutableStateOf(AppLanguage.VIETNAMESE)
        composeRule.setContent {
            ProvideLocalization(language.value) {
                FastToWinTheme {
                    ArcadeBackdrop { MaintenanceScreen() }
                }
            }
        }

        composeRule.onNodeWithText("MÁY CHỦ ĐANG\nNGHỈ GIỮA HIỆP").assertIsDisplayed()
        composeRule.runOnIdle { language.value = AppLanguage.ENGLISH }
        composeRule.onNodeWithText("SERVER TAKING\nA TIMEOUT").assertIsDisplayed()
        composeRule.runOnIdle { language.value = AppLanguage.JAPANESE }
        composeRule.onNodeWithText("サーバーは現在\nメンテナンス中").assertIsDisplayed()
    }
}
