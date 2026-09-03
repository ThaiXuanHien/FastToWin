package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Browser Back/Forward is coordinated centrally by AppNavigationBridge.
}
