@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.hienthai.fastowin

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.components.ConnectionRecoveryNotice
import com.hienthai.fastowin.ui.screens.HomeDashboard
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectionRecoveryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recoveryOffersReloadDismissAndReconnectWithLargeText() {
        val status = mutableStateOf(ConnectionStatus.CONNECTED)
        val available = mutableStateOf(true)
        var reloads = 0
        var retries = 0
        compose.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f),
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.safeDrawing)) {
                            HomeDashboard(state = GameState(player = PlayerState("Người chơi"), connectionStatus = status.value),
                                isGuest = false, onQuickMatch = { _, _ -> }, onOpenRooms = {}, onOpenFriends = {},
                                onOpenLeaderboard = {}, onOpenProfile = {}, onOpenNotifications = {}, onOpenGifts = {},
                                onOpenClan = {}, onOpenPractice = {}, onOpenTournament = {}, onOpenShop = {},
                                onClaimDailyCheckIn = {}, onUpgradeGuest = {}, onLogout = {}, modifier = Modifier.padding(16.dp))
                            ConnectionRecoveryNotice(status.value, available.value,
                                onReconnect = { retries++ }, onReload = { reloads++ }, onDismiss = { available.value = false },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp))
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Chưa thể hoàn tất lúc này").assertIsDisplayed()
        screenshot("reload-large-text")
        compose.onNodeWithTag("connection_recovery_action").performClick()
        compose.runOnIdle { assertEquals(1, reloads); assertEquals(0, retries) }
        compose.onNodeWithTag("connection_recovery_dismiss").performClick()
        compose.onNodeWithTag("connection_recovery_notice").assertDoesNotExist()
        compose.runOnIdle { status.value = ConnectionStatus.RECONNECTING }
        compose.mainClock.advanceTimeBy(2_100)
        compose.onNodeWithText("Đang kết nối lại…").assertIsDisplayed()
        screenshot("reconnecting-large-text")
        compose.onNodeWithTag("connection_recovery_action").performClick()
        compose.runOnIdle { assertEquals(1, reloads); assertEquals(1, retries); status.value = ConnectionStatus.CONNECTED }
        compose.onNodeWithTag("connection_recovery_notice").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = File(context.getExternalFilesDir(null), "connection-ux/$name.png")
        path.parentFile!!.mkdirs()
        path.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
