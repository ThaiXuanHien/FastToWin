package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable

/** Owns native editor placement inside a separate dialog scene. */
@Composable
internal expect fun NativeDialogInputScope(content: @Composable () -> Unit)
