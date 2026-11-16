package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.state.HumanVerificationRequest

/** Turnstile's standard widget is 300×65 CSS px; the slot keeps that space reserved while it loads. */
internal val HumanVerificationWidgetWidth = 300.dp
internal val HumanVerificationWidgetHeight = 72.dp

/** Mobile WebView page for [request], themed to match the dark arcade dialog. */
internal fun HumanVerificationRequest.mobilePageUrl(): String =
    "$challengePageUrl?action=${action.wireName}&theme=dark&lang=${if (languageTag.startsWith("vi")) "vi" else "en"}"

/**
 * Renders the Cloudflare Turnstile widget. Implementations call [onReady] once the widget is on
 * screen, [onToken] with a solved token, and [onError] when it cannot load or the check fails.
 */
@Composable
internal expect fun HumanVerificationWidget(
    request: HumanVerificationRequest,
    onReady: () -> Unit,
    onToken: (String) -> Unit,
    onError: () -> Unit,
    modifier: Modifier
)

/** SEC-07: the security check shown before sign-up, password reset and verification resends. */
@Composable
fun HumanVerificationDialog(
    request: HumanVerificationRequest,
    onToken: (String) -> Unit,
    onCancel: () -> Unit
) {
    // A retry rebuilds the widget from scratch; Turnstile tokens and failed widgets are single-use.
    var attempt by remember(request.id) { mutableIntStateOf(0) }
    var ready by remember(request.id, attempt) { mutableStateOf(false) }
    var failed by remember(request.id, attempt) { mutableStateOf(false) }
    ArcadeDialog(
        title = localized(TextKey.HumanVerificationTitle),
        subtitle = localized(TextKey.HumanVerificationDescription),
        onDismissRequest = onCancel,
        showCloseButton = true,
        modifier = Modifier.testTag("human_verification_dialog")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = HumanVerificationWidgetHeight),
                contentAlignment = Alignment.Center
            ) {
                if (!failed) {
                    androidx.compose.runtime.key(request.id, attempt) {
                        HumanVerificationWidget(
                            request = request,
                            onReady = { ready = true },
                            onToken = onToken,
                            onError = { failed = true },
                            modifier = Modifier
                                .size(HumanVerificationWidgetWidth, HumanVerificationWidgetHeight)
                                .testTag("human_verification_widget")
                        )
                    }
                }
                if (!ready && !failed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF8FB4FF))
                        Text(
                            localized(TextKey.HumanVerificationLoading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD5E0F7)
                        )
                    }
                }
                if (failed) {
                    Text(
                        localized(TextKey.HumanVerificationFailed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFB4AB),
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("human_verification_error")
                    )
                }
            }
            if (failed) {
                ArcadeActionButton(
                    label = localized(TextKey.Retry),
                    onClick = { attempt++ },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("human_verification_retry")
                )
            }
            ArcadeActionButton(
                label = localized(TextKey.Cancel),
                onClick = onCancel,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("human_verification_cancel")
            )
        }
    }
}
