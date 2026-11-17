package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import com.hienthai.fastowin.state.HumanVerificationRequest
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLScriptElement

private const val TURNSTILE_SCRIPT_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"

/**
 * Web renders Turnstile straight into the game page (allowed by the CSP in deploy/ Caddyfiles);
 * the DOM node is placed over the canvas by Compose's HTML interop, like the native text inputs.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun HumanVerificationWidget(
    request: HumanVerificationRequest,
    onReady: () -> Unit,
    onToken: (String) -> Unit,
    onError: () -> Unit,
    modifier: Modifier
) {
    val ready by rememberUpdatedState(onReady)
    val token by rememberUpdatedState(onToken)
    val error by rememberUpdatedState(onError)
    val container = remember(request.id) {
        (document.createElement("div") as HTMLElement).apply {
            setAttribute("data-fasttowin-human-verification", "")
            style.width = "100%"
            style.height = "100%"
            style.display = "flex"
            style.alignItems = "center"
            style.justifyContent = "center"
        }
    }
    HtmlElementView(factory = { container }, modifier = modifier)
    DisposableEffect(container) {
        var disposed = false
        var widgetId: String? = null
        val language = if (request.languageTag.startsWith("vi")) "vi" else "en"
        loadTurnstileScript(
            onLoaded = {
                if (disposed) return@loadTurnstileScript
                widgetId = renderTurnstile(
                    container, request.siteKey, request.action.wireName, language,
                    onToken = { value -> if (!disposed && value.isNotBlank()) token(value) },
                    onError = { if (!disposed) error() }
                )
                if (widgetId == null) error() else ready()
            },
            onFailed = { if (!disposed) error() }
        )
        onDispose {
            disposed = true
            widgetId?.let(::removeTurnstile)
        }
    }
}

private fun loadTurnstileScript(onLoaded: () -> Unit, onFailed: () -> Unit) {
    if (turnstileAvailable()) return onLoaded()
    val script = (document.querySelector("script[data-fasttowin-turnstile]") as? HTMLScriptElement)
        ?: (document.createElement("script") as HTMLScriptElement).also { created ->
            created.setAttribute("data-fasttowin-turnstile", "")
            created.src = TURNSTILE_SCRIPT_URL
            created.async = true
            document.head!!.appendChild(created)
        }
    script.addEventListener("load", { if (turnstileAvailable()) onLoaded() else onFailed() })
    script.addEventListener("error", {
        // Let the next attempt (the dialog's retry) insert a fresh tag.
        script.parentNode?.removeChild(script)
        onFailed()
    })
}

private fun turnstileAvailable(): Boolean =
    js("typeof window.turnstile !== 'undefined' && typeof window.turnstile.render === 'function'")

@Suppress("UNUSED_PARAMETER")
private fun renderTurnstile(
    container: HTMLElement,
    siteKey: String,
    action: String,
    language: String,
    onToken: (String) -> Unit,
    onError: () -> Unit
): String? = js(
    """(() => {
        try {
            const id = window.turnstile.render(container, {
                sitekey: siteKey,
                action: action,
                theme: 'dark',
                language: language,
                retry: 'never',
                'refresh-expired': 'auto',
                callback: (token) => onToken(token),
                'error-callback': () => { onError(); return true; },
                'unsupported-callback': () => onError()
            });
            return id === undefined || id === null ? null : String(id);
        } catch (e) {
            return null;
        }
    })()"""
)

@Suppress("UNUSED_PARAMETER")
private fun removeTurnstile(widgetId: String): Unit =
    js("(() => { try { window.turnstile.remove(widgetId); } catch (e) {} })()")
