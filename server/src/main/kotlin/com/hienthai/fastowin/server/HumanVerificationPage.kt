package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.HUMAN_VERIFICATION_PAGE_PATH
import com.hienthai.fastowin.protocol.HumanVerificationAction
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * SEC-07: the page Android and iOS open in a WebView to solve Turnstile. It is served from the game
 * host so the widget's hostname matches the site key, and it hands the token to the app through a
 * native bridge. Web renders the widget in its own page instead (see TurnstileChallenge.wasm.kt).
 */
internal fun Route.humanVerificationChallengePage(verifier: TurnstileVerifier) {
    get(HUMAN_VERIFICATION_PAGE_PATH) {
        val siteKey = verifier.siteKey
        val action = HumanVerificationAction.fromWireName(call.request.queryParameters["action"])
        if (siteKey == null || action == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val theme = call.request.queryParameters["theme"].takeIf { it == "light" || it == "dark" } ?: "auto"
        val language = call.request.queryParameters["lang"].takeIf { it == "vi" || it == "en" } ?: "auto"
        call.applyChallengeHeaders()
        call.respondText(challengeHtml(siteKey, action.wireName, theme, language), ContentType.Text.Html)
    }
    get("$HUMAN_VERIFICATION_PAGE_PATH.js") {
        if (verifier.siteKey == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.applyChallengeHeaders()
        call.respondText(CHALLENGE_SCRIPT, ContentType.Text.JavaScript)
    }
}

private fun io.ktor.server.application.ApplicationCall.applyChallengeHeaders() {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("X-Content-Type-Options", "nosniff")
    response.header("Referrer-Policy", "no-referrer")
    response.header(
        "Content-Security-Policy",
        "default-src 'none'; script-src 'self' https://challenges.cloudflare.com; " +
            "frame-src https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com; " +
            "style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
    )
}

// Every interpolated value is validated above (fixed action names, key characters, theme/language
// enums), so no escaping beyond that is needed.
private fun challengeHtml(siteKey: String, action: String, theme: String, language: String): String = """
<!doctype html>
<html lang="${if (language == "auto") "en" else language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>Fast To Win</title>
<style>
  html, body { margin: 0; height: 100%; background: transparent; }
  body { display: flex; align-items: center; justify-content: center; }
</style>
</head>
<body>
<div id="challenge" data-sitekey="$siteKey" data-action="$action" data-theme="$theme" data-language="$language"></div>
<script src="$HUMAN_VERIFICATION_PAGE_PATH.js"></script>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&amp;onload=fastToWinTurnstileLoaded" async defer></script>
</body>
</html>
""".trimIndent()

private val CHALLENGE_SCRIPT = """
(function () {
  "use strict";
  function post(message) {
    var text = JSON.stringify(message);
    if (window.FastToWinHumanVerification && window.FastToWinHumanVerification.postMessage) {
      window.FastToWinHumanVerification.postMessage(text);
      return;
    }
    var handlers = window.webkit && window.webkit.messageHandlers;
    if (handlers && handlers.fastToWinHumanVerification) handlers.fastToWinHumanVerification.postMessage(text);
  }
  window.fastToWinTurnstileLoaded = function () {
    var root = document.getElementById("challenge");
    try {
      window.turnstile.render(root, {
        sitekey: root.dataset.sitekey,
        action: root.dataset.action,
        theme: root.dataset.theme,
        language: root.dataset.language,
        retry: "never",
        "refresh-expired": "auto",
        callback: function (token) { post({ type: "token", token: token }); },
        "error-callback": function () { post({ type: "error" }); return true; },
        "unsupported-callback": function () { post({ type: "error" }); }
      });
      post({ type: "ready" });
    } catch (error) {
      post({ type: "error" });
    }
  };
  window.setTimeout(function () { if (!window.turnstile) post({ type: "error" }); }, 20000);
})();
""".trimIndent()
