package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.hienthai.fastowin.ui.theme.A1Palette
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalWindowInfo
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
    shape: Shape = A1FieldShape,
    colors: TextFieldColors = a1FieldColors(),
) {
    // A1 (".field" của thiết kế): nhãn nằm PHÍA TRÊN ô; ô trắng cao 56dp, bo 16dp, viền navy 3dp,
    // viền xanh + quầng nhạt khi đang nhập, viền đỏ khi lỗi.
    if (label != null) {
        Column {
            A1FieldLabel(label)
            AppOutlinedTextField(
                value, onValueChange, modifier, enabled, readOnly, textStyle, null, placeholder,
                leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation,
                keyboardOptions, keyboardActions, singleLine, maxLines, minLines, shape, colors
            )
        }
        return
    }
    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { { Text(it) } }
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    val edge = when {
        isError -> Color(0xFFD0303A)
        fieldFocused -> A1Palette.Blue2
        else -> A1Palette.Line
    }
    val a1Modifier = Modifier
        .then(if (fieldFocused && !isError) Modifier.border(7.dp, A1Palette.Blue2.copy(alpha = 0.2f), shape) else Modifier)
        .border(3.dp, edge, shape)
    if (!usesNativeWebTextInput) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier.heightIn(min = 56.dp).then(a1Modifier),
            enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            label = null, placeholder = placeholderContent,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            interactionSource = fieldInteraction,
            shape = shape, colors = colors,
        )
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val bringIntoView = remember { BringIntoViewRequester() }
    val windowSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(focused, windowSize) {
        if (focused) bringIntoView.bringIntoView()
    }
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
    Box(modifier.bringIntoViewRequester(bringIntoView).then(a1Modifier)
        .defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth,
            minHeight = OutlinedTextFieldDefaults.MinHeight)) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = {
                NativeWebTextInput(
                    value = value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp * minLines),
                    enabled = enabled, readOnly = readOnly, label = label.orEmpty(),
                    placeholder = placeholder.orEmpty(),
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
            label = null, placeholder = null,
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

private val A1FieldShape = RoundedCornerShape(16.dp)

/** Màu ô nhập A1: nền trắng, chữ navy; viền do [AppOutlinedTextField] tự vẽ (3dp) nên viền Material trong suốt. */
@Composable
fun a1FieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = A1Palette.SurfaceAlt,
    errorContainerColor = Color.White,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = Color.Transparent,
    focusedTextColor = A1Palette.Ink,
    unfocusedTextColor = A1Palette.Ink,
    focusedLeadingIconColor = A1Palette.Line,
    unfocusedLeadingIconColor = A1Palette.Line,
    focusedTrailingIconColor = A1Palette.Line,
    unfocusedTrailingIconColor = A1Palette.Line,
    focusedPlaceholderColor = A1Palette.Muted,
    unfocusedPlaceholderColor = A1Palette.Muted,
    cursorColor = A1Palette.Blue2,
    errorSupportingTextColor = Color(0xFFB3202B),
    errorLeadingIconColor = Color(0xFFD0303A)
)

internal expect val usesNativeWebTextInput: Boolean

@Composable
internal expect fun NativeWebTextInput(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier,
    enabled: Boolean, readOnly: Boolean, label: String, placeholder: String,
    textStyle: TextStyle,
    keyboardOptions: KeyboardOptions, visualTransformation: VisualTransformation,
    singleLine: Boolean, minLines: Int, maxLines: Int,
    interactionSource: MutableInteractionSource, onImeAction: (ImeAction) -> Unit,
)
