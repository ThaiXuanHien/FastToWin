package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.network.AnnouncementPlayerItem
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*

@Composable
fun AnnouncementBanner(item: AnnouncementPlayerItem, pending: Boolean, onOpen: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val context = item.announcement
    if (context.state != AdminAnnouncementState.ACTIVE || item.bannerHidden) return
    ArcadePanel(modifier.fillMaxWidth().testTag("announcement_banner")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(localized(TextKey.AnnouncementLabel), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(if (LocalLocalization.current.language == AppLanguage.VIETNAMESE) context.titleVi else context.titleEn,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnnouncementActionButton(localized(TextKey.AnnouncementDetails), onOpen, enabled = !pending)
                AnnouncementActionButton(localized(TextKey.AnnouncementHide), onHide, enabled = !pending, outline = true)
            }
        }
    }
}

/** Native button grows for wrapped labels, including the player's enlarged text setting. */
@Composable
fun AnnouncementActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true,
    outline: Boolean = false, modifier: Modifier = Modifier) {
    val buttonModifier = modifier.heightIn(min = 48.dp)
    if (outline) OutlinedButton(onClick, enabled = enabled, modifier = buttonModifier,
        shape = MaterialTheme.shapes.large) { Text(label, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
    else Button(onClick, enabled = enabled, modifier = buttonModifier,
        shape = MaterialTheme.shapes.large) { Text(label, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
}
fun announcementStateKey(state: AdminAnnouncementState): TextKey = when (state) {
    AdminAnnouncementState.ACTIVE -> TextKey.AnnouncementActive
    AdminAnnouncementState.SCHEDULED -> TextKey.AnnouncementScheduled
    AdminAnnouncementState.EXPIRED -> TextKey.AnnouncementExpired
    AdminAnnouncementState.CANCELLED -> TextKey.AnnouncementCancelled
}
fun announcementCategoryKey(category: AdminAnnouncementCategory): TextKey = when (category) {
    AdminAnnouncementCategory.GENERAL -> TextKey.AnnouncementGeneral
    AdminAnnouncementCategory.EVENT -> TextKey.AnnouncementEvent
    AdminAnnouncementCategory.MAINTENANCE -> TextKey.AnnouncementMaintenance
    AdminAnnouncementCategory.COMPENSATION -> TextKey.AnnouncementCompensation
}
