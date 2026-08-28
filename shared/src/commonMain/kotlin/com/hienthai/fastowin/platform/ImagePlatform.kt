package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
)

expect fun ByteArray.toImageBitmap(): ImageBitmap
