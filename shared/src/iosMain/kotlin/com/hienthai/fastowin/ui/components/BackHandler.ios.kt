package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // BackHandler xử lý cả vuốt back từ cạnh màn hình và gesture hệ thống trên iOS.
    // TODO: Migrate sang NavigationBackHandler (navigationevent-compose:1.0.x) khi ready.
    BackHandler(enabled = enabled, onBack = onBack)
}
