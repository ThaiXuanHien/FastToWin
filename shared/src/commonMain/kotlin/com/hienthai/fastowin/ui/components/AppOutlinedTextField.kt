package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Same Material decoration on every platform, directly editable HTML on Web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val labelContent: (@Composable () -> Unit)? = label?.let { { Text(it) } }
    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { { Text(it) } }
    if (!usesNativeWebTextInput) {
        OutlinedTextField(
            value, onValueChange, modifier, enabled, readOnly, textStyle,
            labelContent, placeholderContent, leadingIcon, trailingIcon,
            prefix, suffix, supportingText, isError, visualTransformation,
            keyboardOptions, keyboardActions, singleLine, maxLines, minLines,
            shape = shape, colors = colors,
        )
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val textColor = when {
        !enabled -> colors.disabledTextColor
        isError -> colors.errorTextColor
        focused -> colors.focusedTextColor
        else -> colors.unfocusedTextColor
    }
    val actionScope = remember {
        object : KeyboardActionScope {
            // DOM focus traversal/blur is performed synchronously by the native view.
            override fun defaultKeyboardAction(imeAction: ImeAction) = Unit
        }
    }
    Box(modifier.padding(top = if (label != null) 8.dp else 0.dp)
        .defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth,
            minHeight = OutlinedTextFieldDefaults.MinHeight)) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = {
                NativeWebTextInput(
                    value = value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp * minLines),
                    enabled = enabled, readOnly = readOnly, label = label.orEmpty(),
                    textStyle = textStyle.copy(color = textColor),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    singleLine = singleLine, minLines = minLines, maxLines = maxLines,
                    interactionSource = interactionSource,
                    onImeAction = { action ->
                        val handler = when (action) {
                            ImeAction.Next -> keyboardActions.onNext
                            ImeAction.Previous -> keyboardActions.onPrevious
                            ImeAction.Search -> keyboardActions.onSearch
                            ImeAction.Send -> keyboardActions.onSend
                            ImeAction.Go -> keyboardActions.onGo
                            else -> keyboardActions.onDone
                        }
                        handler?.invoke(actionScope)
                    },
                )
            },
            enabled = enabled, singleLine = singleLine,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource, isError = isError,
            label = labelContent, placeholder = placeholderContent,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            prefix = prefix, suffix = suffix, supportingText = supportingText,
            colors = colors,
            container = {
                OutlinedTextFieldDefaults.Container(enabled, isError, interactionSource,
                    colors = colors, shape = shape)
            },
        )
    }
}

internal expect val usesNativeWebTextInput: Boolean

@Composable
internal expect fun NativeWebTextInput(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier,
    enabled: Boolean, readOnly: Boolean, label: String, textStyle: TextStyle,
    keyboardOptions: KeyboardOptions, visualTransformation: VisualTransformation,
    singleLine: Boolean, minLines: Int, maxLines: Int,
    interactionSource: MutableInteractionSource, onImeAction: (ImeAction) -> Unit,
)
