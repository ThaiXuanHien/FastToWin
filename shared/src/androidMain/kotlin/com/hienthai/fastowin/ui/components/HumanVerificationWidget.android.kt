package com.hienthai.fastowin.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.hienthai.fastowin.state.HumanVerificationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val BRIDGE_NAME = "FastToWinHumanVerification"
private const val CLOUDFLARE_CHALLENGE_HOST = "challenges.cloudflare.com"

/** Loads the server's challenge page (same host as the site key) and takes the token over a JS bridge. */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
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
    val pageUrl = request.mobilePageUrl()
    val pageHost = Uri.parse(pageUrl).host
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                val view = this
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(message: String) {
                        // Bridge calls arrive on a WebView thread; only the challenge page may answer.
                        view.post {
                            if (Uri.parse(view.url ?: "").host != pageHost) return@post
                            when (val type = messageField(message, "type")) {
                                "ready" -> ready()
                                "token" -> messageField(message, "token")?.takeIf(String::isNotBlank)?.let(token)
                                "error" -> error()
                                else -> if (type == null) error()
                            }
                        }
                    }
                }, BRIDGE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val host = request.url.host
                        // Never leave the challenge: no links, redirects or popups to other sites.
                        return host != pageHost && host != CLOUDFLARE_CHALLENGE_HOST
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                        if (request.isForMainFrame) error()
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame) error()
                    }
                }
                loadUrl(pageUrl)
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface(BRIDGE_NAME)
            view.stopLoading()
            view.destroy()
        }
    )
}

private fun messageField(message: String, name: String): String? = runCatching {
    Json.parseToJsonElement(message).jsonObject[name]?.jsonPrimitive?.contentOrNull
}.getOrNull()
