package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

@Composable
fun AnnouncementUpdateScreen(supported: Boolean, available: Boolean, onBack: () -> Unit, onApply: () -> Unit, modifier: Modifier = Modifier) {
    SystemBackHandler(onBack = onBack)
    Scaffold(modifier.fillMaxSize().testTag("announcement_update"), topBar = {
        FastToWinHeader(title = localized(TextKey.AnnouncementUpdateTitle), gold = 0, gems = 0, unreadNotifications = 0,
            onNotifications = {}, onBack = onBack, showBalances = false, showNotifications = false)
    }) { padding ->
        ResponsiveScreen(Modifier.padding(padding), maxContentWidth = 760.dp) { content ->
            Column(content.verticalScroll(rememberScrollState()).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(localized(when { !supported -> TextKey.AnnouncementUpdateUnsupported; available -> TextKey.AnnouncementUpdateReady; else -> TextKey.AnnouncementUpdateCurrent }), style = MaterialTheme.typography.bodyLarge)
                if (supported && available) AnnouncementActionButton(localized(TextKey.AnnouncementUpdateApply), onApply, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
