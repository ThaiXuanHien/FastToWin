package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.viewinterop.HtmlElementView
import androidx.compose.ui.unit.isUnspecified
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent

internal actual val usesNativeWebTextInput = true

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun NativeWebTextInput(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier,
    enabled: Boolean, readOnly: Boolean, label: String, textStyle: TextStyle,
    keyboardOptions: KeyboardOptions, visualTransformation: VisualTransformation,
    singleLine: Boolean, minLines: Int, maxLines: Int,
    interactionSource: MutableInteractionSource, onImeAction: (ImeAction) -> Unit,
) {
    val change by rememberUpdatedState(onValueChange)
    val imeAction by rememberUpdatedState(onImeAction)
    val options by rememberUpdatedState(keyboardOptions)
    val editable by rememberUpdatedState(enabled && !readOnly)
    var composing by remember { mutableStateOf(false) }
    // Force a controlled-value update even when validation rejects an edit.
    var editRevision by remember { mutableStateOf(0) }
    var focus by remember { mutableStateOf<FocusInteraction.Focus?>(null) }
    val density = LocalDensity.current
    val fontSize = (if (textStyle.fontSize.isUnspecified) 16f else textStyle.fontSize.value)
        .times(density.fontScale).coerceAtLeast(16f)
    HtmlElementView(
        factory = {
            val element = document.createElement(if (singleLine) "input" else "textarea") as HTMLElement
            element.setAttribute("data-fasttowin-native-input", "")
            element.style.apply {
                width = "100%"
                height = "100%"
                minWidth = "0"
                padding = "0"
                margin = "0"
                border = "0"
                outline = "none" // Material border receives DOM focus interactions below.
                background = "transparent"
                fontFamily = "system-ui, sans-serif"
                boxSizing = "border-box"
                setProperty("resize", "none")
            }
            element.addEventListener("focus", {
                val interaction = FocusInteraction.Focus()
                focus = interaction
                interactionSource.tryEmit(interaction)
            })
            element.addEventListener("blur", {
                focus?.let { interactionSource.tryEmit(FocusInteraction.Unfocus(it)) }
                focus = null
            })
            element.addEventListener("compositionstart", { composing = true })
            element.addEventListener("compositionend", {
                composing = false
                if (editable) change(element.inputValue())
                editRevision++
            })
            element.addEventListener("input", {
                if (!composing && editable) change(element.inputValue())
                editRevision++
            })
            element.addEventListener("keydown", { event ->
                val key = event as KeyboardEvent
                if (key.key == "Enter" && !composing && singleLine) {
                    key.preventDefault()
                    val action = options.imeAction
                    imeAction(action)
                    if (action == ImeAction.Next || action == ImeAction.Previous) {
                        val inputs = document.querySelectorAll("[data-fasttowin-native-input]:not(:disabled)")
                        for (index in 0 until inputs.length) {
                            if (inputs.item(index) == element) {
                                val next = index + if (action == ImeAction.Next) 1 else -1
                                (inputs.item(next) as? HTMLElement)?.focus()
                                break
                            }
                        }
                    } else {
                        element.blur()
                    }
                }
            })
            element
        },
        modifier = modifier,
        update = { element ->
            editRevision // Read so rejected edits also restore the controlled value.
            val type = when {
                visualTransformation is PasswordVisualTransformation -> "password"
                keyboardOptions.keyboardType == KeyboardType.Email -> "email"
                keyboardOptions.keyboardType == KeyboardType.Uri -> "url"
                keyboardOptions.keyboardType == KeyboardType.Phone -> "tel"
                else -> "text"
            }
            if (element is HTMLInputElement) {
                if (element.type != type) element.type = type
                element.disabled = !enabled
                element.readOnly = readOnly
            }
            if (element is HTMLTextAreaElement) {
                element.disabled = !enabled
                element.readOnly = readOnly
                element.rows = minLines.coerceAtMost(maxLines)
            }
            element.setAttribute("aria-label", label)
            element.setAttribute("inputmode", when (keyboardOptions.keyboardType) {
                KeyboardType.Email -> "email"
                KeyboardType.Number, KeyboardType.NumberPassword -> "numeric"
                KeyboardType.Decimal -> "decimal"
                KeyboardType.Phone -> "tel"
                KeyboardType.Uri -> "url"
                else -> "text"
            })
            element.setAttribute("enterkeyhint", when (keyboardOptions.imeAction) {
                ImeAction.Next -> "next"
                ImeAction.Previous -> "previous"
                ImeAction.Search -> "search"
                ImeAction.Send -> "send"
                ImeAction.Go -> "go"
                else -> "done"
            })
            if (type == "password" || keyboardOptions.keyboardType == KeyboardType.Email) {
                element.setAttribute("autocapitalize", "none")
                element.setAttribute("autocorrect", "off")
                element.setAttribute("spellcheck", "false")
            }
            element.style.fontSize = "${fontSize}px"
            element.style.lineHeight = "1.5"
            element.style.color = "#" + textStyle.color.toArgb().toUInt().toString(16).padStart(8, '0').takeLast(6)
            // Never rewrite equal values or in-progress IME text: assigning value
            // resets the selection and can terminate Japanese/Chinese composition.
            if (!composing && element.inputValue() != value) element.setInputValue(value)
        },
        onRelease = { element ->
            element.blur()
            focus?.let { interactionSource.tryEmit(FocusInteraction.Unfocus(it)) }
            focus = null
        },
    )
}

private fun HTMLElement.inputValue(): String = when (this) {
    is HTMLInputElement -> value
    is HTMLTextAreaElement -> value
    else -> ""
}

private fun HTMLElement.setInputValue(text: String) {
    val previous = inputValue()
    val selection = selectionRange()
    when (this) {
        is HTMLInputElement -> value = text
        is HTMLTextAreaElement -> value = text
    }
    selection ?: return
    val start = mapSelectionOffset(previous, text, selection.first)
    val end = mapSelectionOffset(previous, text, selection.second)
    when (this) {
        is HTMLInputElement -> setSelectionRange(start, end)
        is HTMLTextAreaElement -> setSelectionRange(start, end)
    }
}

private fun HTMLElement.selectionRange(): Pair<Int, Int>? = when (this) {
    is HTMLInputElement -> selectionStart?.let { start -> selectionEnd?.let { end -> start to end } }
    is HTMLTextAreaElement -> selectionStart?.let { start -> selectionEnd?.let { end -> start to end } }
    else -> null
}

private fun mapSelectionOffset(previous: String, replacement: String, offset: Int): Int {
    val prefixLength = previous.commonPrefixWith(replacement).length
    val suffixLimit = minOf(previous.length - prefixLength, replacement.length - prefixLength)
    var suffixLength = 0
    while (
        suffixLength < suffixLimit &&
        previous[previous.lastIndex - suffixLength] == replacement[replacement.lastIndex - suffixLength]
    ) {
        suffixLength++
    }
    return when {
        offset <= prefixLength -> offset
        offset >= previous.length - suffixLength -> offset + replacement.length - previous.length
        else -> prefixLength + minOf(offset - prefixLength, replacement.length - prefixLength - suffixLength)
    }.coerceIn(0, replacement.length)
}
