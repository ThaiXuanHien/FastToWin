package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Visible in-app Back controls work on web. Browser history binding is a
    // separate navigation milestone because the app currently owns its stack.
}
