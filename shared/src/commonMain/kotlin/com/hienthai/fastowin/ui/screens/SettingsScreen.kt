package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppFontScale
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.data.preferences.BoardStyle
import com.hienthai.fastowin.platform.AppPushStatus
import com.hienthai.fastowin.platform.AppInstallStatus
import com.hienthai.fastowin.protocol.PushPreferencesSnapshot
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onPreferencesChange: (AppPreferences) -> Unit,
    onPreviewSound: () -> Unit,
    onOpenTutorial: () -> Unit,
    onBack: () -> Unit,
    gold: Int = 0,
    gems: Int = 0,
    unreadNotifications: Int = 0,
    onOpenNotifications: () -> Unit = {},
    pushStatus: AppPushStatus = AppPushStatus.UNSUPPORTED,
    onEnablePush: () -> Unit = {},
    onDisablePush: () -> Unit = {},
    pushPreferences: PushPreferencesSnapshot? = null,
    pushPreferencesSaving: Boolean = false,
    onPushPreferencesChange: (PushPreferencesSnapshot) -> Unit = {},
    installStatus: AppInstallStatus = AppInstallStatus.UNSUPPORTED,
    onInstallApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    if (showLanguageDialog) {
        LanguageDialog(
            selectedCode = preferences.languageCode,
            onSelected = { code ->
                onPreferencesChange(preferences.copy(languageCode = code))
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    SystemBackHandler(onBack = onBack)
    ArcadeBackdrop(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                FastToWinHeader(
                    title = localized(TextKey.SettingsTitle),
                    gold = gold,
                    gems = gems,
                    unreadNotifications = unreadNotifications,
                    onNotifications = onOpenNotifications,
                    onBack = onBack
                )
            }
        ) { paddingValues ->
            ResponsiveScreen(
                modifier = Modifier.padding(paddingValues),
                maxContentWidth = 840.dp,
                applySafeDrawingInsets = false
            ) { contentModifier ->
                Column(
                    modifier = contentModifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsHero()

                    if (installStatus != AppInstallStatus.UNSUPPORTED) {
                        SettingsSection(
                            title = localized(TextKey.SettingsInstallTitle),
                            subtitle = localized(TextKey.SettingsInstallSubtitle)
                        ) {
                            SettingsInstallRow(installStatus)
                            when (installStatus) {
                                AppInstallStatus.AVAILABLE,
                                AppInstallStatus.ERROR -> ArcadeActionButton(
                                    label = if (installStatus == AppInstallStatus.ERROR) {
                                        localized(TextKey.SettingsInstallRetry)
                                    } else {
                                        localized(TextKey.SettingsInstallAction)
                                    },
                                    onClick = onInstallApp,
                                    icon = Icons.Rounded.InstallMobile,
                                    style = ArcadeActionStyle.GOLD,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                )

                                AppInstallStatus.INSTALLING -> ArcadeActionButton(
                                    label = localized(TextKey.SettingsInstallOpening),
                                    onClick = {},
                                    enabled = false,
                                    icon = Icons.Rounded.InstallMobile,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                )

                                AppInstallStatus.MANUAL -> Text(
                                    text = localized(TextKey.SettingsInstallManualInstructions),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                AppInstallStatus.INSTALLED,
                                AppInstallStatus.UNSUPPORTED -> Unit
                            }
                        }
                    }

                    val showDevicePushControl = pushStatus !in setOf(
                        AppPushStatus.UNSUPPORTED,
                        AppPushStatus.UNCONFIGURED
                    )
                    if (showDevicePushControl || pushPreferences != null) {
                        SettingsSection(
                            title = localized(TextKey.SettingsNotificationsTitle),
                            subtitle = localized(TextKey.SettingsNotificationsSubtitle)
                        ) {
                            if (showDevicePushControl) {
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.NotificationsActive,
                                    title = localized(TextKey.SettingsDeviceNotifications),
                                    subtitle = localized(pushStatus.descriptionKey()),
                                    checked = pushStatus == AppPushStatus.ENABLED,
                                    enabled = pushStatus != AppPushStatus.REQUESTING,
                                    onCheckedChange = { enabled ->
                                        if (enabled) onEnablePush() else onDisablePush()
                                    }
                                )
                            }
                            pushPreferences?.let { preferences ->
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.MeetingRoom,
                                    title = localized(TextKey.SettingsRoomInvitationsTitle),
                                    subtitle = localized(TextKey.SettingsRoomInvitationsSubtitle),
                                    checked = preferences.roomInvitationsEnabled,
                                    enabled = !pushPreferencesSaving,
                                    onCheckedChange = {
                                        onPushPreferencesChange(preferences.copy(roomInvitationsEnabled = it))
                                    }
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.EmojiEvents,
                                    title = localized(TextKey.SettingsTournamentInvitationsTitle),
                                    subtitle = localized(TextKey.SettingsTournamentInvitationsSubtitle),
                                    checked = preferences.tournamentInvitationsEnabled,
                                    enabled = !pushPreferencesSaving,
                                    onCheckedChange = {
                                        onPushPreferencesChange(preferences.copy(tournamentInvitationsEnabled = it))
                                    }
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.TaskAlt,
                                    title = localized(TextKey.SettingsMissionRewardsTitle),
                                    subtitle = localized(TextKey.SettingsMissionRewardsSubtitle),
                                    checked = preferences.missionRewardsEnabled,
                                    enabled = !pushPreferencesSaving,
                                    onCheckedChange = {
                                        onPushPreferencesChange(preferences.copy(missionRewardsEnabled = it))
                                    }
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.CalendarMonth,
                                    title = localized(TextKey.SettingsDailyCheckInTitle),
                                    subtitle = localized(TextKey.SettingsDailyCheckInSubtitle),
                                    checked = preferences.dailyCheckInEnabled,
                                    enabled = !pushPreferencesSaving,
                                    onCheckedChange = {
                                        onPushPreferencesChange(preferences.copy(dailyCheckInEnabled = it))
                                    }
                                )
                            }
                        }
                    }

                    SettingsSection(
                        title = localized(TextKey.SettingsFeedbackTitle),
                        subtitle = localized(TextKey.SettingsFeedbackSubtitle)
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            title = localized(TextKey.SettingsSoundTitle),
                            subtitle = localized(TextKey.SettingsSoundSubtitle),
                            checked = preferences.soundEnabled,
                            onCheckedChange = {
                                onPreferencesChange(preferences.copy(soundEnabled = it))
                                if (it) onPreviewSound()
                            }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Vibration,
                            title = localized(TextKey.SettingsVibrationTitle),
                            subtitle = localized(TextKey.SettingsVibrationSubtitle),
                            checked = preferences.vibrationEnabled,
                            onCheckedChange = { onPreferencesChange(preferences.copy(vibrationEnabled = it)) }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.ColorLens,
                            title = localized(TextKey.SettingsVisualEffectsTitle),
                            subtitle = localized(TextKey.SettingsVisualEffectsSubtitle),
                            checked = preferences.visualEffectsEnabled,
                            onCheckedChange = {
                                onPreferencesChange(preferences.copy(visualEffectsEnabled = it))
                            }
                        )
                        ArcadeActionButton(
                            label = localized(TextKey.SettingsPreviewSound),
                            onClick = onPreviewSound,
                            enabled = preferences.soundEnabled,
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            style = ArcadeActionStyle.OUTLINE,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        )
                    }

                    SettingsSection(
                        title = localized(TextKey.AppearanceTitle),
                        subtitle = localized(TextKey.AppearanceSubtitle)
                    ) {
                        val themeLabels = mapOf(
                            AppThemeMode.SYSTEM to localized(TextKey.ThemeSystem),
                            AppThemeMode.LIGHT to localized(TextKey.ThemeLight),
                            AppThemeMode.DARK to localized(TextKey.ThemeDark)
                        )
                        val boardStyleLabels = mapOf(
                            BoardStyle.CLASSIC to localized(TextKey.BoardClassic),
                            BoardStyle.OCEAN to localized(TextKey.BoardOcean),
                            BoardStyle.HIGH_CONTRAST to localized(TextKey.BoardHighContrast)
                        )
                        val fontScaleLabels = mapOf(
                            AppFontScale.COMPACT to localized(TextKey.FontCompact),
                            AppFontScale.STANDARD to localized(TextKey.FontStandard),
                            AppFontScale.LARGE to localized(TextKey.FontLarge)
                        )
                        LanguageSettingRow(preferences.languageCode) { showLanguageDialog = true }
                        SettingChoiceTitle(Icons.Rounded.ColorLens, localized(TextKey.SettingsThemeTitle))
                        ChoiceRow(
                            entries = AppThemeMode.entries,
                            selected = preferences.themeMode,
                            label = themeLabels::getValue,
                            onSelected = { onPreferencesChange(preferences.copy(themeMode = it)) }
                        )

                        SettingChoiceTitle(Icons.Rounded.ColorLens, localized(TextKey.SettingsBoardStyleTitle))
                        ChoiceRow(
                            entries = BoardStyle.entries,
                            selected = preferences.boardStyle,
                            label = boardStyleLabels::getValue,
                            onSelected = { onPreferencesChange(preferences.copy(boardStyle = it)) }
                        )

                        SettingChoiceTitle(Icons.Rounded.FormatSize, localized(TextKey.SettingsFontSizeTitle))
                        ChoiceRow(
                            entries = AppFontScale.entries,
                            selected = preferences.fontScale,
                            label = fontScaleLabels::getValue,
                            onSelected = { onPreferencesChange(preferences.copy(fontScale = it)) }
                        )
                    }

                    ArcadeActionButton(
                        label = localized(TextKey.SettingsOpenTutorial),
                        onClick = onOpenTutorial,
                        style = ArcadeActionStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    )

                    ArcadeActionButton(
                        label = localized(TextKey.SettingsResetDefaults),
                        onClick = { onPreferencesChange(AppPreferences()) },
                        icon = Icons.Rounded.RestartAlt,
                        style = ArcadeActionStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSettingRow(code: String, onClick: () -> Unit) {
    val selected = AppLanguage.entries.firstOrNull { it.code == code }
    val resolved = LocalLocalization.current.language
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("language_setting"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = ArcadePalette.Blue300)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localized(TextKey.LanguageTitle), fontWeight = FontWeight.SemiBold)
                Text(
                    selected?.nativeName ?: localized(TextKey.ResolvedSystemLanguage, "language" to resolved.nativeName),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun LanguageDialog(selectedCode: String, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val selection = AppLanguage.entries.firstOrNull { it.code == selectedCode }?.code ?: "system"
    val resolvedLanguage = LocalLocalization.current.language
    ArcadeDialog(
        title = localized(TextKey.ChooseLanguageTitle),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("language_dialog")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageOption(
                code = "system",
                title = localized(TextKey.SystemLanguage),
                subtitle = localized(TextKey.ResolvedSystemLanguage, "language" to resolvedLanguage.nativeName),
                isSelected = selection == "system"
            ) {
                onSelected("system")
            }
            AppLanguage.entries.forEach { language ->
                LanguageOption(language.code, language.nativeName, language.englishName, selection == language.code) {
                    onSelected(language.code)
                }
            }
            ArcadeActionButton(
                label = localized(TextKey.Close), onClick = onDismiss,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth().testTag("language_close")
            )
        }
    }
}

@Composable
private fun LanguageOption(code: String, title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) ArcadePalette.Navy700 else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .testTag("language_option_$code")
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = ArcadePalette.White, fontWeight = FontWeight.SemiBold)
                if (subtitle != title) Text(subtitle, color = ArcadePalette.White, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = isSelected, onClick = null)
        }
    }
}

@Composable
private fun SettingsInstallRow(status: AppInstallStatus) {
    val (titleKey, subtitleKey) = when (status) {
        AppInstallStatus.AVAILABLE -> TextKey.InstallAvailableTitle to TextKey.InstallAvailableSubtitle
        AppInstallStatus.INSTALLING -> TextKey.InstallInstallingTitle to TextKey.InstallInstallingSubtitle
        AppInstallStatus.INSTALLED -> TextKey.InstallInstalledTitle to TextKey.InstallInstalledSubtitle
        AppInstallStatus.MANUAL -> TextKey.InstallManualTitle to TextKey.InstallManualSubtitle
        AppInstallStatus.ERROR -> TextKey.InstallErrorTitle to TextKey.InstallErrorSubtitle
        AppInstallStatus.UNSUPPORTED -> TextKey.InstallUnsupportedTitle to TextKey.InstallUnsupportedSubtitle
    }
    val title = localized(titleKey)
    val subtitle = localized(subtitleKey)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.medium,
            color = ArcadePalette.Navy700,
            contentColor = if (status == AppInstallStatus.INSTALLED) {
                ArcadePalette.Mint400
            } else {
                ArcadePalette.Gold500
            },
            border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.Blue300)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.InstallMobile,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsHero() {
    ArcadePanel(
        modifier = Modifier.fillMaxWidth(),
        accent = ArcadePalette.Violet400
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackVertically = maxWidth < 340.dp && LocalDensity.current.fontScale >= 1.3f
            if (stackVertically) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingsHeroText(Modifier.fillMaxWidth())
                    SettingsHeroIcon()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsHeroText(Modifier.weight(1f))
                    SettingsHeroIcon()
                }
            }
        }
    }
}

@Composable
private fun SettingsHeroText(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            localized(TextKey.SettingsHeroEyebrow),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = ArcadePalette.Gold500
        )
        Text(
            localized(TextKey.SettingsHeroTitle),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            localized(TextKey.SettingsHeroSubtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsHeroIcon() {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = MaterialTheme.shapes.large,
        color = ArcadePalette.Violet600,
        contentColor = ArcadePalette.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.Violet400)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ArcadePanel(
            modifier = Modifier.fillMaxWidth(),
            accent = ArcadePalette.Blue300
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.medium,
            color = ArcadePalette.Navy700,
            contentColor = ArcadePalette.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.Blue300)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
        )
    }
}

private fun AppPushStatus.descriptionKey(): TextKey = when (this) {
    AppPushStatus.PROMPT -> TextKey.PushPromptDescription
    AppPushStatus.REQUESTING -> TextKey.PushRequestingDescription
    AppPushStatus.ENABLED -> TextKey.PushEnabledDescription
    AppPushStatus.DISABLED -> TextKey.PushDisabledDescription
    AppPushStatus.DENIED -> TextKey.PushDeniedDescription
    AppPushStatus.ERROR -> TextKey.PushErrorDescription
    AppPushStatus.UNSUPPORTED -> TextKey.PushUnsupportedDescription
    AppPushStatus.UNCONFIGURED -> TextKey.PushUnconfiguredDescription
}

@Composable
private fun SettingChoiceTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ArcadePalette.Blue300, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun <T> ChoiceRow(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    ArcadeSegmentedControl(
        labels = entries.map(label),
        selectedIndex = entries.indexOf(selected).coerceAtLeast(0),
        onSelected = { index -> entries.getOrNull(index)?.let(onSelected) }
    )
}
