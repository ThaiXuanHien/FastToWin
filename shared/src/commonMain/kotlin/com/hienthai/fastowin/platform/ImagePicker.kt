package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable

@Composable
expect fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
)
