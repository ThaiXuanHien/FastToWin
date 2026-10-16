package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/** Owns native editor placement inside a separate dialog scene. */
@Composable
internal expect fun NativeDialogInputScope(content: @Composable () -> Unit)

/** Window behavior coordinated with the native editor placement scope. */
internal expect fun arcadeDialogProperties(): DialogProperties
