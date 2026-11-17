package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.hienthai.fastowin.state.HumanVerificationRequest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private const val HANDLER_NAME = "fastToWinHumanVerification"
private const val CLOUDFLARE_CHALLENGE_HOST = "challenges.cloudflare.com"

/** Loads the server's challenge page (same host as the site key) and takes the token over a script handler. */
@OptIn(ExperimentalForeignApi::class)
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
    val pageHost = NSURL.URLWithString(pageUrl)?.host
    val bridge = remember(request.id) {
        HumanVerificationBridge(
            pageHost = pageHost,
            onMessage = { type, value ->
                when (type) {
                    "ready" -> ready()
                    "token" -> value?.takeIf(String::isNotBlank)?.let(token)
                    else -> error()
                }
            }
        )
    }
    UIKitView(
        modifier = modifier,
        factory = {
            val configuration = WKWebViewConfiguration()
            // WebKit retains the handler strongly; it is removed again in onRelease.
            configuration.userContentController.addScriptMessageHandler(bridge, HANDLER_NAME)
            WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
                opaque = false
                backgroundColor = UIColor.clearColor
                scrollView.backgroundColor = UIColor.clearColor
                scrollView.scrollEnabled = false
                navigationDelegate = bridge
                val url = NSURL.URLWithString(pageUrl)
                if (url == null) error() else loadRequest(NSURLRequest.requestWithURL(url))
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.navigationDelegate = null
            view.configuration.userContentController.removeScriptMessageHandlerForName(HANDLER_NAME)
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class HumanVerificationBridge(
    private val pageHost: String?,
    private val onMessage: (type: String?, token: String?) -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        // Only the top-level challenge page may answer, never a nested frame.
        if (!didReceiveScriptMessage.frameInfo.mainFrame) return
        if (didReceiveScriptMessage.frameInfo.request.URL?.host != pageHost) return
        val body = didReceiveScriptMessage.body as? String ?: return onMessage(null, null)
        val fields = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        onMessage(
            fields?.get("type")?.jsonPrimitive?.contentOrNull,
            fields?.get("token")?.jsonPrimitive?.contentOrNull
        )
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit
    ) {
        // Never leave the challenge: no links, redirects or popups to other sites.
        val host = decidePolicyForNavigationAction.request.URL?.host
        decisionHandler(
            if (host == pageHost || host == CLOUDFLARE_CHALLENGE_HOST) WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            else WKNavigationActionPolicy.WKNavigationActionPolicyCancel
        )
    }

    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        onMessage(null, null)
    }
}
