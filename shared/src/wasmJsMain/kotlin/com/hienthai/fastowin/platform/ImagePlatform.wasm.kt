package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
actual fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    content { onImageSelected(null) }
}

actual fun ByteArray.toImageBitmap(): ImageBitmap = decodeToImageBitmap()
