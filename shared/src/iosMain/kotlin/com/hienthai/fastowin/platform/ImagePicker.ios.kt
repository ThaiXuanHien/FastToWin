package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable

@Composable
actual fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    content {
        // iOS implementation pending (requires UIViewController wrapper)
        onImageSelected(null)
    }
}
