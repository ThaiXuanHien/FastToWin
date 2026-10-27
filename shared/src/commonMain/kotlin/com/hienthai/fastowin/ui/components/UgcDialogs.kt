package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import kotlinx.coroutines.launch

data class UgcTarget(val type: UgcTargetType, val id: String?, val label: String)
private data class UgcActions(val selfId: String?, val report: (UgcTarget) -> Unit)
private val LocalUgcActions = staticCompositionLocalOf<UgcActions?> { null }

/** Supplies report actions without coupling content layouts to the socket/controller lifecycle. */
@Composable
fun ProvideUgcReportActions(currentPlayerId: String?, onReport: (UgcTarget) -> Unit,
    content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUgcActions provides UgcActions(currentPlayerId, onReport), content = content)
}

@Composable
fun UgcSafetyProvider(serverUrl: String, revision: Long, controller: GameController,
    isGuest: Boolean, currentPlayerId: String?, suppressedAvatarIds: Set<String> = emptySet(),
    invalidatedAvatarIds: Set<String> = emptySet(), content: @Composable () -> Unit) {
    val state by controller.ugc.state.collectAsState()
    val scope = rememberCoroutineScope()
    var blockTarget by remember(currentPlayerId) { mutableStateOf<UgcReportDraft?>(null) }
    LaunchedEffect(state.report?.request?.requestId, state.report?.pending) {
        if (state.report?.pending == true) {
            kotlinx.coroutines.delay(15_000)
            state.report?.request?.requestId?.let(controller.ugc::reportTimedOut)
        }
    }
    LaunchedEffect(state.termsPending) {
        if (state.termsPending) { kotlinx.coroutines.delay(15_000); controller.ugc.termsTimedOut() }
    }
    ProvideUgcReportActions(currentPlayerId, { target ->
        target.id?.let { controller.ugc.openReport(target.type, it, target.label) }
    }) {
        AvatarImageProvider(serverUrl, revision, suppressedAvatarIds, invalidatedAvatarIds, content)
        state.report?.let { draft ->
            UgcReportDialog(draft,
                onEdit = { reason, note, type -> controller.ugc.editReport(reason, note, type) },
                onSubmit = { scope.launch { controller.ugc.submitReport() } },
                onDismiss = controller.ugc::dismissReport,
                onBlock = if (!isGuest && draft.targetType in playerTypes) ({ blockTarget = draft }) else null)
        }
        if (state.showTerms) ContentTermsDialog(state, controller.ugc::checkTerms,
            { scope.launch { controller.ugc.acceptTerms() } },
            { scope.launch { controller.ugc.retryTerms() } }, controller::dismissContentTerms)
        blockTarget?.let { target ->
            UgcBlockConfirmationDialog(target.targetLabel,
                onBlock = {
                    controller.blockPlayer(target.targetId)
                    blockTarget = null
                },
                onDismiss = { blockTarget = null })
        }
    }
}

@Composable
fun UgcBlockConfirmationDialog(targetLabel: String, onBlock: () -> Unit, onDismiss: () -> Unit) {
    ArcadeDialog(localized(TextKey.BlockPlayerTitle, "player" to targetLabel), onDismiss,
        subtitle = localized(TextKey.BlockFriendDescription, "player" to targetLabel)) {
        ArcadeActionButton(localized(TextKey.Block), onBlock,
            modifier = Modifier.fillMaxWidth(), style = ArcadeActionStyle.DANGER)
        TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(localized(TextKey.Cancel)) }
    }
}

private val playerTypes = setOf(UgcTargetType.PLAYER_PROFILE, UgcTargetType.PLAYER_AVATAR)

/** Lets lazy containers omit the entire item when its report control is unavailable. */
@Composable
fun canReportUgcTarget(type: UgcTargetType, id: String?): Boolean {
    val actions = LocalUgcActions.current ?: return false
    return !id.isNullOrBlank() && !(type in playerTypes && id == actions.selfId)
}

/** Opens player reports from the friend actions menu with the shared target guards. */
@Composable
fun UgcReportMenuItem(type: UgcTargetType, id: String?, label: String, onDismissMenu: () -> Unit) {
    val actions = LocalUgcActions.current ?: return
    if (!canReportUgcTarget(type, id)) return
    DropdownMenuItem(
        text = { Text(localized(TextKey.UgcReportPlayer)) },
        leadingIcon = { Icon(Icons.Rounded.Report, contentDescription = null) },
        onClick = { onDismissMenu(); actions.report(UgcTarget(type, id, label)) },
        modifier = Modifier.heightIn(min = 48.dp).testTag("report:${type.name}:$id")
    )
}

@Composable
fun UgcClanReportAction(id: String, label: String, modifier: Modifier = Modifier) {
    val actions = LocalUgcActions.current ?: return
    if (!canReportUgcTarget(UgcTargetType.CLAN, id)) return
    ArcadeActionButton(
        label = localized(TextKey.UgcReportClan),
        onClick = { actions.report(UgcTarget(UgcTargetType.CLAN, id, label)) },
        icon = Icons.Rounded.Report,
        style = ArcadeActionStyle.OUTLINE,
        modifier = modifier.testTag("report:CLAN:$id")
    )
}

@Composable
fun ContentTermsDialog(state: UgcState, onChecked: (Boolean) -> Unit, onAccept: () -> Unit,
    onRetry: () -> Unit, onDismiss: () -> Unit, dismissWhileSubmitting: Boolean = true) {
    val dismissEnabled = dismissWhileSubmitting || !state.termsPending || !state.termsChecked
    val dismiss = { if (dismissEnabled) onDismiss() }
    val uri = LocalUriHandler.current
    val language = LocalLocalization.current.language
    ArcadeDialog(localized(TextKey.UgcTermsTitle), dismiss, scrollableContent = true,
        modifier = Modifier.testTag("ugc_terms_dialog")) {
        Text(localized(TextKey.UgcTermsDescription), style = MaterialTheme.typography.bodyMedium)
        state.terms?.let { Text(localized(TextKey.UgcTermsVersion, "version" to it.requiredVersion),
            style = MaterialTheme.typography.labelMedium) }
        TextButton({ uri.openUri(contentTermsLegalUrl(language)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(localized(TextKey.UgcTermsRead))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(state.termsChecked,
            enabled = !state.termsPending && state.terms != null, role = Role.Checkbox, onValueChange = onChecked)
            .testTag("ugc_terms_checkbox"), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(state.termsChecked, null)
            Text(localized(TextKey.UgcTermsAgreement), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        if (state.termsPending) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.termsError?.let {
            Text(localized(if (it == "CONNECTION_FAILED") TextKey.UgcConnectionInterrupted else TextKey.UgcTermsFailed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
            TextButton(onRetry, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.termsPending) { Text(localized(TextKey.Retry)) }
        }
        ArcadeActionButton(localized(TextKey.UgcTermsAccept), onAccept,
            enabled = state.termsChecked && state.terms != null && !state.termsPending && state.termsError == null,
            modifier = Modifier.fillMaxWidth().testTag("ugc_terms_accept"), style = ArcadeActionStyle.GOLD)
        TextButton(dismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = dismissEnabled) { Text(localized(TextKey.Cancel)) }
    }
}

fun contentTermsLegalUrl(language: AppLanguage): String =
    "https://fasttowin.up.railway.app/terms" + if (language == AppLanguage.VIETNAMESE) "" else "/en"

@Composable
fun UgcReportDialog(draft: UgcReportDraft,
    onEdit: (UgcReportReason, String, UgcTargetType) -> Unit, onSubmit: () -> Unit,
    onDismiss: () -> Unit, onBlock: (() -> Unit)? = null) {
    val frozen = draft.request != null || draft.pending || draft.receipt != null
    var reasonsExpanded by remember { mutableStateOf(false) }
    ArcadeDialog(localized(TextKey.UgcReportTitle), onDismiss,
        subtitle = draft.targetLabel, showCloseButton = true, scrollableContent = true,
        modifier = Modifier.testTag("ugc_report_dialog"), fixedActions = {
            if (draft.receipt == null) {
                ArcadeActionButton(localized(when {
                    draft.pending -> TextKey.UgcReportSending
                    draft.request != null -> TextKey.UgcReportRetry
                    else -> TextKey.UgcReportSend
                }), onSubmit, enabled = !draft.pending,
                    modifier = Modifier.fillMaxWidth().testTag("ugc_report_submit"), style = ArcadeActionStyle.GOLD)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onBlock?.let {
                    TextButton(it, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(localized(TextKey.Block)) }
                }
                TextButton(onDismiss, Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(localized(if (draft.receipt != null) TextKey.Close else TextKey.Cancel))
                }
            }
        }) {
        if (draft.receipt != null) {
            Text(localized(TextKey.UgcReportReceived), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(localized(TextKey.UgcReportReceipt, "id" to draft.receipt.reportId), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(localized(TextKey.UgcReportDescription), style = MaterialTheme.typography.bodyMedium)
            if (draft.targetType in playerTypes) ArcadeSegmentedControl(
                labels = listOf(localized(TextKey.UgcPlayerProfile), localized(TextKey.UgcPlayerAvatar)),
                selectedIndex = if (draft.targetType == UgcTargetType.PLAYER_AVATAR) 1 else 0,
                onSelected = { onEdit(draft.reason, draft.note, if (it == 1) UgcTargetType.PLAYER_AVATAR else UgcTargetType.PLAYER_PROFILE) },
                enabled = { !frozen }, modifier = Modifier.fillMaxWidth(),
                itemTestTag = { if (it == 1) "ugc_report_avatar" else "ugc_report_nickname" })
            else Text(localized(draft.targetType.labelKey()), fontWeight = FontWeight.Bold)
            Text(localized(TextKey.UgcReportReason), style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton({ reasonsExpanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !frozen) {
                    Text(localized(draft.reason.labelKey()))
                }
                DropdownMenu(reasonsExpanded, { reasonsExpanded = false }) {
                    UgcReportReason.entries.forEach { reason -> DropdownMenuItem(
                        text = { Text(localized(reason.labelKey())) },
                        onClick = { reasonsExpanded = false; onEdit(reason, draft.note, draft.targetType) },
                        modifier = Modifier.heightIn(min = 48.dp)) }
                }
            }
            AppOutlinedTextField(draft.note, { onEdit(draft.reason, it, draft.targetType) },
                label = localized(TextKey.UgcReportNote), enabled = !frozen, minLines = 2, maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("ugc_report_note"),
                supportingText = { Text("${draft.note.length}/500") })
            if (draft.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            draft.error?.let { code ->
                Text(localized(when (code) {
                    "RATE_LIMITED", "REPORT_RATE_LIMITED" -> TextKey.UgcReportRateLimit
                    "TARGET_NOT_FOUND", "CONTENT_NOT_FOUND" -> TextKey.UgcReportUnavailable
                    else -> TextKey.UgcReportFailed
                }), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                Text(localized(TextKey.UgcReportFrozen), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun UgcTargetType.labelKey(): TextKey = when (this) {
    UgcTargetType.PLAYER_PROFILE -> TextKey.UgcPlayerProfile
    UgcTargetType.PLAYER_AVATAR -> TextKey.UgcPlayerAvatar
    UgcTargetType.CLAN -> TextKey.UgcClan
    UgcTargetType.ROOM -> TextKey.UgcRoom
    UgcTargetType.TOURNAMENT -> TextKey.UgcTournament
}
private fun UgcReportReason.labelKey(): TextKey = when (this) {
    UgcReportReason.HARASSMENT -> TextKey.UgcHarassment
    UgcReportReason.HATE -> TextKey.UgcHate
    UgcReportReason.SEXUAL_CONTENT -> TextKey.UgcSexualContent
    UgcReportReason.VIOLENCE -> TextKey.UgcViolence
    UgcReportReason.SPAM -> TextKey.UgcSpam
    UgcReportReason.PERSONAL_INFORMATION -> TextKey.UgcPersonalInformation
    UgcReportReason.OTHER -> TextKey.UgcOther
}
