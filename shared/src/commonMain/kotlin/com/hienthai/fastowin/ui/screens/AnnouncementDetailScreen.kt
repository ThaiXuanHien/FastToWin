package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.AnnouncementState
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import kotlin.time.Instant

@Composable
fun AnnouncementDetailScreen(state: AnnouncementState, onBack: () -> Unit, onRefresh: () -> Unit, onAction: () -> Unit,
    onCorrection: (String) -> Unit, modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState()) {
    SystemBackHandler(onBack = onBack)
    Scaffold(modifier.fillMaxSize().testTag("announcement_detail"), topBar = {
        FastToWinHeader(title = localized(TextKey.AnnouncementLabel), gold = 0, gems = 0, unreadNotifications = 0,
            onNotifications = {}, onBack = onBack, showBalances = false, showNotifications = false)
    }) { padding ->
        ResponsiveScreen(Modifier.padding(padding), maxContentWidth = 760.dp) { content ->
            Column(content.verticalScroll(scrollState).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.loading && state.selected == null) Text(localized(TextKey.AnnouncementLoading))
                if (state.error != null) {
                    Text(localized(TextKey.AnnouncementError))
                    AnnouncementActionButton(localized(TextKey.AnnouncementRetry), onRefresh, enabled = !state.loading)
                }
                state.selected?.let { item ->
                    val context = item.announcement
                    val vi = LocalLocalization.current.language == AppLanguage.VIETNAMESE
                    Text(localized(announcementCategoryKey(context.category)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(if (vi) context.titleVi else context.titleEn, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(localized(announcementStateKey(context.state)), fontWeight = FontWeight.Bold)
                    if (item.dismissed) Text(localized(TextKey.AnnouncementDismissed))
                    Text(if (vi) context.bodyVi else context.bodyEn, style = MaterialTheme.typography.bodyLarge)
                    Text(localized(TextKey.AnnouncementBounds, "start" to utcAnnouncement(context.startsAtEpochMillis), "end" to utcAnnouncement(context.endsAtEpochMillis)))
                    if (context.action != AdminAnnouncementAction.NONE) AnnouncementActionButton(localized(when (context.action) {
                        AdminAnnouncementAction.REWARD_EVENT -> TextKey.AnnouncementOpenEvent
                        AdminAnnouncementAction.GIFT_MAILBOX -> TextKey.AnnouncementOpenMailbox
                        else -> TextKey.AnnouncementUpdateTitle
                    }), onAction, enabled = !state.loading && !state.pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                    context.supersedesAnnouncementId?.let { id -> AnnouncementActionButton(localized(TextKey.AnnouncementCorrection), { onCorrection(id) },
                        enabled = !state.loading, outline = true, modifier = Modifier.fillMaxWidth()) }
                    Text(localized(TextKey.AnnouncementAuthority), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
private fun utcAnnouncement(millis: Long) = Instant.fromEpochMilliseconds(millis).toString().replace('T', ' ')
