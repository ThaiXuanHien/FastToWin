package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation

internal actual val usesNativeWebTextInput = false

@Composable
internal actual fun NativeWebTextInput(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier,
    enabled: Boolean, readOnly: Boolean, label: String, textStyle: TextStyle,
    keyboardOptions: KeyboardOptions, visualTransformation: VisualTransformation,
    singleLine: Boolean, minLines: Int, maxLines: Int,
    interactionSource: MutableInteractionSource, onImeAction: (ImeAction) -> Unit,
) = Unit
