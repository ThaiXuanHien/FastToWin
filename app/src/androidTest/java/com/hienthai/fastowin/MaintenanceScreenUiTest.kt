package com.hienthai.fastowin

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
                FastToWinTheme {
                    ArcadeBackdrop {
                        MaintenanceScreen(message = "Máy chủ đang nâng cấp dữ liệu mùa giải.")
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
}
