package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.data.preferences.AppFontScale
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.BoardStyle
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.normalizeAppLanguageSelection
import com.hienthai.fastowin.localization.platformLanguageTags
import com.hienthai.fastowin.localization.resolveSavedLanguage
import com.hienthai.fastowin.localization.selectableAppLanguages
import com.hienthai.fastowin.platform.AppInstallStatus
import com.hienthai.fastowin.platform.AppPushStatus
import com.hienthai.fastowin.platform.PublicEndpoints
import com.hienthai.fastowin.platform.rememberAppVersionLabel
import com.hienthai.fastowin.protocol.PushPreferencesSnapshot
import com.hienthai.fastowin.ui.components.A1BottomSheet
import com.hienthai.fastowin.ui.components.A1OutlinedText
import com.hienthai.fastowin.ui.components.AnnouncementActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.A1Palette

/*
 * Màn "Cài đặt" theo thiết kế A1: các nhóm Chung / Âm thanh & cảm giác / Thông báo / Trợ giúp,
 * mỗi nhóm là một thẻ trắng gồm các dòng (ô icon màu, tên, công tắc hoặc mũi tên).
 */

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
    adPrivacyOptionsRequired: Boolean = false,
    onAdPrivacyOptions: () -> Unit = {},
    announcementState: com.hienthai.fastowin.state.AnnouncementState? = null,
    onAnnouncementPreferences: (Boolean, Boolean) -> Unit = { _, _ -> },
    onAnnouncementRetry: () -> Unit = {},
    systemLanguageTags: List<String> = platformLanguageTags(),
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val legalLanguage = LocalLocalization.current.language
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    // Máy chưa từng hỏi quyền thông báo: giải thích trước (hộp thoại "Đừng bỏ lỡ trận nào") rồi mới để hệ điều hành hỏi.
    var showPushRationale by rememberSaveable { mutableStateOf(false) }
    if (showPushRationale) {
        com.hienthai.fastowin.ui.components.PushOptInDialog(
            onEnable = {
                showPushRationale = false
                onEnablePush()
            },
            onLater = { showPushRationale = false }
        )
    }
    val systemLanguage = resolveSavedLanguage("system", systemLanguageTags)
    val versionLabel = rememberAppVersionLabel()
    if (showLanguageDialog) {
        LanguageDialog(
            selectedCode = preferences.languageCode,
            systemLanguage = systemLanguage,
            onSelected = { code ->
                onPreferencesChange(preferences.copy(languageCode = code))
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    SystemBackHandler(onBack = onBack)
    ArcadeBackdrop(modifier = modifier.fillMaxSize().testTag("settings_screen")) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                // Như thiết kế: chỉ nút quay lại + tiêu đề.
                FastToWinHeader(
                    title = localized(TextKey.SettingsTitle),
                    gold = gold,
                    gems = gems,
                    unreadNotifications = unreadNotifications,
                    onNotifications = onOpenNotifications,
                    onBack = onBack,
                    showBalances = false,
                    showNotifications = false
                )
            }
        ) { paddingValues ->
            ResponsiveScreen(
                modifier = Modifier.padding(paddingValues),
                maxContentWidth = 640.dp,
                applySafeDrawingInsets = false
            ) { contentModifier ->
                Column(
                    modifier = contentModifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (installStatus != AppInstallStatus.UNSUPPORTED) {
                        InstallCard(installStatus, onInstallApp)
                    }

                    // ---- Chung
                    SettingsSectionLabel(localized(TextKey.SettingsGeneralSection))
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Translate,
                            tile = SettingsTile.BLUE,
                            title = localized(TextKey.LanguageTitle),
                            modifier = Modifier.testTag("language_setting"),
                            onClick = { showLanguageDialog = true }
                        ) {
                            val selection = normalizeAppLanguageSelection(preferences.languageCode)
                            Text(
                                if (selection == "system") {
                                    localized(TextKey.ResolvedSystemLanguage, "language" to systemLanguage.nativeName)
                                } else {
                                    selectableAppLanguages.firstOrNull { it.code == selection }?.nativeName ?: AppLanguage.ENGLISH.nativeName
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = A1Palette.Muted,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Chevron()
                        }
                        SettingsChoiceRow(
                            icon = Icons.Rounded.FormatSize,
                            tile = SettingsTile.PURPLE,
                            title = localized(TextKey.SettingsFontSizeTitle)
                        ) {
                            val entries = AppFontScale.entries
                            ArcadeSegmentedControl(
                                labels = entries.map {
                                    localized(
                                        when (it) {
                                            AppFontScale.COMPACT -> TextKey.FontCompact
                                            AppFontScale.STANDARD -> TextKey.FontStandard
                                            AppFontScale.LARGE -> TextKey.FontLarge
                                        }
                                    )
                                },
                                selectedIndex = entries.indexOf(preferences.fontScale).coerceAtLeast(0),
                                onSelected = { index -> entries.getOrNull(index)?.let { onPreferencesChange(preferences.copy(fontScale = it)) } },
                                labelFontSizes = listOf(13.sp, 15.sp, 17.sp),
                                modifier = Modifier.testTag("settings_font_scale")
                            )
                        }
                        SettingsChoiceRow(
                            icon = Icons.Rounded.Palette,
                            tile = SettingsTile.GREEN,
                            title = localized(TextKey.SettingsBoardStyleTitle),
                            showDivider = false
                        ) {
                            val entries = BoardStyle.entries
                            val boardLabels = entries.map {
                                localized(
                                    when (it) {
                                        BoardStyle.CLASSIC -> TextKey.BoardClassic
                                        BoardStyle.OCEAN -> TextKey.BoardOcean
                                        BoardStyle.HIGH_CONTRAST -> TextKey.BoardHighContrast
                                    }
                                )
                            }
                            // Chữ lớn: ba nhãn không vừa một hàng nên cho cuộn ngang thay vì cắt chữ.
                            if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) {
                                com.hienthai.fastowin.ui.components.ArcadeScrollableSegmentedControl(
                                    labels = boardLabels,
                                    selectedIndex = entries.indexOf(preferences.boardStyle).coerceAtLeast(0),
                                    onSelected = { index -> entries.getOrNull(index)?.let { onPreferencesChange(preferences.copy(boardStyle = it)) } },
                                    minItemWidth = 120.dp,
                                    modifier = Modifier.testTag("settings_board_style")
                                )
                            } else ArcadeSegmentedControl(
                                labels = entries.map {
                                    localized(
                                        when (it) {
                                            BoardStyle.CLASSIC -> TextKey.BoardClassic
                                            BoardStyle.OCEAN -> TextKey.BoardOcean
                                            BoardStyle.HIGH_CONTRAST -> TextKey.BoardHighContrast
                                        }
                                    )
                                },
                                selectedIndex = entries.indexOf(preferences.boardStyle).coerceAtLeast(0),
                                onSelected = { index -> entries.getOrNull(index)?.let { onPreferencesChange(preferences.copy(boardStyle = it)) } },
                                modifier = Modifier.testTag("settings_board_style")
                            )
                        }
                    }

                    // ---- Âm thanh & cảm giác
                    SettingsSectionLabel(localized(TextKey.SettingsSoundFeelSection))
                    SettingsCard {
                        SettingsSwitchRow(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            tile = SettingsTile.GREEN,
                            title = localized(TextKey.SettingsSoundTitle),
                            checked = preferences.soundEnabled,
                            onCheckedChange = {
                                onPreferencesChange(preferences.copy(soundEnabled = it))
                                if (it) onPreviewSound()
                            }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Vibration,
                            tile = SettingsTile.RED,
                            title = localized(TextKey.SettingsVibrationTitle),
                            checked = preferences.vibrationEnabled,
                            onCheckedChange = { onPreferencesChange(preferences.copy(vibrationEnabled = it)) }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.AutoAwesome,
                            tile = SettingsTile.INK,
                            title = localized(TextKey.SettingsVisualEffectsTitle),
                            subtitle = localized(TextKey.SettingsVisualEffectsSubtitle),
                            checked = preferences.visualEffectsEnabled,
                            showDivider = false,
                            onCheckedChange = { onPreferencesChange(preferences.copy(visualEffectsEnabled = it)) }
                        )
                    }

                    // ---- Thông báo
                    val showDevicePushControl = pushStatus !in setOf(AppPushStatus.UNSUPPORTED, AppPushStatus.UNCONFIGURED)
                    if (showDevicePushControl || pushPreferences != null || announcementState != null) {
                        SettingsSectionLabel(localized(TextKey.SettingsNotificationsTitle))
                        SettingsCard {
                            if (showDevicePushControl) {
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.NotificationsActive,
                                    tile = SettingsTile.YELLOW,
                                    title = localized(TextKey.SettingsPushTitle),
                                    subtitle = if (pushStatus == AppPushStatus.ENABLED) localized(TextKey.SettingsPushSubtitle) else localized(pushStatus.descriptionKey()),
                                    checked = pushStatus == AppPushStatus.ENABLED,
                                    enabled = pushStatus != AppPushStatus.REQUESTING,
                                    showDivider = pushPreferences != null || announcementState != null,
                                    modifier = Modifier.testTag("settings_push_device"),
                                    onCheckedChange = {
                                        when {
                                            !it -> onDisablePush()
                                            pushStatus == AppPushStatus.PROMPT -> showPushRationale = true
                                            else -> onEnablePush()
                                        }
                                    }
                                )
                            }
                            pushPreferences?.let { prefs ->
                                // Thông báo đẩy trên máy đang tắt thì các loại thông báo chưa có tác dụng: làm mờ và khóa.
                                val typesEnabled = !pushPreferencesSaving && (!showDevicePushControl || pushStatus == AppPushStatus.ENABLED)
                                val items = listOf(
                                    PushToggle(Icons.Rounded.MeetingRoom, TextKey.SettingsRoomInvitationsTitle, prefs.roomInvitationsEnabled) { prefs.copy(roomInvitationsEnabled = it) },
                                    PushToggle(Icons.Rounded.EmojiEvents, TextKey.SettingsTournamentInvitationsTitle, prefs.tournamentInvitationsEnabled) { prefs.copy(tournamentInvitationsEnabled = it) },
                                    PushToggle(Icons.Rounded.Schedule, TextKey.SettingsTournamentRemindersTitle, prefs.tournamentRemindersEnabled) { prefs.copy(tournamentRemindersEnabled = it) },
                                    PushToggle(Icons.Rounded.TaskAlt, TextKey.SettingsMissionRewardsTitle, prefs.missionRewardsEnabled) { prefs.copy(missionRewardsEnabled = it) },
                                    PushToggle(Icons.Rounded.CalendarMonth, TextKey.SettingsDailyCheckInTitle, prefs.dailyCheckInEnabled) { prefs.copy(dailyCheckInEnabled = it) },
                                    PushToggle(Icons.Rounded.PersonAdd, TextKey.SettingsFriendActivityTitle, prefs.friendActivityEnabled) { prefs.copy(friendActivityEnabled = it) },
                                    PushToggle(Icons.Rounded.CardGiftcard, TextKey.SettingsGiftsTitle, prefs.giftsEnabled) { prefs.copy(giftsEnabled = it) },
                                    PushToggle(Icons.Rounded.MilitaryTech, TextKey.SettingsSeasonUpdatesTitle, prefs.seasonUpdatesEnabled) { prefs.copy(seasonUpdatesEnabled = it) },
                                    PushToggle(Icons.Rounded.Shield, TextKey.SettingsClanActivityTitle, prefs.clanActivityEnabled) { prefs.copy(clanActivityEnabled = it) }
                                )
                                items.forEachIndexed { index, item ->
                                    SettingsSwitchRow(
                                        icon = item.icon,
                                        tile = SettingsTile.WHITE,
                                        title = localized(item.title),
                                        checked = item.checked,
                                        enabled = typesEnabled,
                                        compact = true,
                                        showDivider = index < items.lastIndex || announcementState != null,
                                        onCheckedChange = { onPushPreferencesChange(item.update(it)) }
                                    )
                                }
                            }
                            announcementState?.let { AnnouncementConsentRows(it, onAnnouncementPreferences, onAnnouncementRetry) }
                        }
                    }

                    // ---- Trợ giúp
                    SettingsSectionLabel(localized(TextKey.SettingsHelpSection))
                    SettingsCard {
                        SettingsRow(Icons.Rounded.School, SettingsTile.WHITE, localized(TextKey.SettingsOpenTutorial), onClick = onOpenTutorial) { Chevron() }
                        SettingsRow(
                            Icons.Rounded.SupportAgent, SettingsTile.WHITE, localized(TextKey.SettingsContactSupport),
                            modifier = Modifier.testTag("settings_support"),
                            onClick = { uriHandler.openUri(PublicEndpoints.legalUrl("terms", legalLanguage) + "#section-4") }
                        ) { ExternalIcon() }
                        listOf(
                            Triple(Icons.Rounded.Description, TextKey.LegalTerms, "terms"),
                            Triple(Icons.Rounded.PrivacyTip, TextKey.LegalPrivacy, "privacy"),
                            Triple(Icons.Rounded.DeleteForever, TextKey.LegalDeleteAccount, "delete-account")
                        ).forEach { (icon, label, path) ->
                            SettingsRow(
                                icon, SettingsTile.WHITE, localized(label),
                                modifier = Modifier.testTag("legal_$path"),
                                onClick = { uriHandler.openUri(PublicEndpoints.legalUrl(path, legalLanguage)) }
                            ) { ExternalIcon() }
                        }
                        if (adPrivacyOptionsRequired) {
                            SettingsRow(
                                Icons.Rounded.Campaign, SettingsTile.WHITE, localized(TextKey.RewardedAdPrivacyOptions),
                                subtitle = localized(TextKey.RewardedAdPrivacyDescription),
                                modifier = Modifier.testTag("ad_privacy_options"),
                                onClick = onAdPrivacyOptions
                            ) { Chevron() }
                        }
                        SettingsRow(
                            Icons.Rounded.RestartAlt, SettingsTile.WHITE, localized(TextKey.SettingsResetDefaults),
                            showDivider = false,
                            modifier = Modifier.testTag("settings_reset"),
                            onClick = { onPreferencesChange(AppPreferences()) }
                        ) {}
                    }
                    versionLabel?.let {
                        A1OutlinedText(
                            localized(TextKey.SettingsVersionLine, "version" to it),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            outlineWidth = 2.dp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private class PushToggle(
    val icon: ImageVector,
    val title: TextKey,
    val checked: Boolean,
    val update: (Boolean) -> PushPreferencesSnapshot
)

/** Màu ô icon của dòng (".ico.b/.p/.g/.y/.r/.k/.w"). */
private enum class SettingsTile(val background: Color, val tint: Color) {
    BLUE(A1Palette.Blue2, Color.White),
    PURPLE(A1Palette.Purple2, Color.White),
    GREEN(A1Palette.Green2, Color.White),
    YELLOW(A1Palette.Yellow2, Color(0xFF6A3300)),
    RED(A1Palette.Red2, Color.White),
    INK(A1Palette.Ink, Color.White),
    WHITE(Color.White, A1Palette.Line)
}

/** Tiêu đề nhóm (".sec"): chữ trắng viền navy trên nền trời. */
@Composable
private fun SettingsSectionLabel(text: String) {
    A1OutlinedText(
        text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
        outlineWidth = 2.dp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() }
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().a1Card(), content = content)
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = A1Palette.Ink, modifier = Modifier.size(26.dp))
}

@Composable
private fun ExternalIcon() {
    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = A1Palette.Ink, modifier = Modifier.size(22.dp))
}

@Composable
private fun SettingsRowTexts(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 19.sp),
            fontWeight = FontWeight.ExtraBold,
            color = A1Palette.Ink
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = A1Palette.Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Một dòng bấm được (".li"): ô icon, tên (+ dòng phụ), phần đuôi (giá trị, mũi tên…). */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    tile: SettingsTile,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .a1DashedDivider(showDivider)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        A1IconTile(icon, tile.background, tile.tint)
        SettingsRowTexts(title, subtitle, Modifier.weight(1f))
        trailing()
    }
}

/** Dòng có tiêu đề và một thanh chọn bên dưới (Cỡ chữ, Màu bàn số). */
@Composable
private fun SettingsChoiceRow(
    icon: ImageVector,
    tile: SettingsTile,
    title: String,
    showDivider: Boolean = true,
    control: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .a1DashedDivider(showDivider)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            A1IconTile(icon, tile.background, tile.tint)
            SettingsRowTexts(title, null, Modifier.weight(1f))
        }
        control()
    }
}

/** Dòng có công tắc; cả dòng bấm được để bật/tắt. */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    tile: SettingsTile,
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 56.dp else 64.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .a1DashedDivider(showDivider)
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        A1IconTile(icon, tile.background, tile.tint)
        SettingsRowTexts(title, subtitle, Modifier.weight(1f))
        A1Switch(checked = checked)
    }
}

/** Công tắc A1 (".sw"): 56×32, xanh lá khi bật, xám xanh khi tắt, núm trắng. Chỉ để hiển thị; dòng chứa nó nhận thao tác. */
@Composable
internal fun A1Switch(checked: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .size(width = 56.dp, height = 32.dp)
            .background(if (checked) A1Palette.Green2 else Color(0xFFC9D6E6), shape)
            .border(2.dp, if (checked) A1Palette.GreenDeep else Color(0xFF8AA6C8), shape)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(24.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** Thẻ cài ứng dụng (chỉ bản Web hỗ trợ cài đặt). */
@Composable
private fun InstallCard(status: AppInstallStatus, onInstall: () -> Unit) {
    val (titleKey, subtitleKey) = when (status) {
        AppInstallStatus.AVAILABLE -> TextKey.InstallAvailableTitle to TextKey.InstallAvailableSubtitle
        AppInstallStatus.INSTALLING -> TextKey.InstallInstallingTitle to TextKey.InstallInstallingSubtitle
        AppInstallStatus.INSTALLED -> TextKey.InstallInstalledTitle to TextKey.InstallInstalledSubtitle
        AppInstallStatus.MANUAL -> TextKey.InstallManualTitle to TextKey.InstallManualSubtitle
        AppInstallStatus.ERROR -> TextKey.InstallErrorTitle to TextKey.InstallErrorSubtitle
        AppInstallStatus.UNSUPPORTED -> TextKey.InstallUnsupportedTitle to TextKey.InstallUnsupportedSubtitle
    }
    Column(Modifier.fillMaxWidth().a1Card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            A1IconTile(
                Icons.Rounded.InstallMobile,
                if (status == AppInstallStatus.INSTALLED) A1Palette.Green2 else A1Palette.Blue2
            )
            SettingsRowTexts(localized(titleKey), localized(subtitleKey), Modifier.weight(1f))
        }
        when (status) {
            AppInstallStatus.AVAILABLE, AppInstallStatus.ERROR -> ArcadeActionButton(
                label = localized(if (status == AppInstallStatus.ERROR) TextKey.SettingsInstallRetry else TextKey.SettingsInstallAction),
                onClick = onInstall,
                icon = Icons.Rounded.InstallMobile,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth()
            )
            AppInstallStatus.INSTALLING -> ArcadeActionButton(
                label = localized(TextKey.SettingsInstallOpening),
                onClick = {},
                enabled = false,
                icon = Icons.Rounded.InstallMobile,
                modifier = Modifier.fillMaxWidth()
            )
            AppInstallStatus.MANUAL -> Text(
                localized(TextKey.SettingsInstallManualInstructions),
                style = MaterialTheme.typography.bodySmall,
                color = A1Palette.Muted
            )
            AppInstallStatus.INSTALLED, AppInstallStatus.UNSUPPORTED -> Unit
        }
    }
}

/** Thẻ riêng cho thông báo admin/sự kiện (dùng lại ở nơi khác và trong ảnh chụp test). */
@Composable
fun AnnouncementConsentSettings(
    state: com.hienthai.fastowin.state.AnnouncementState,
    onChange: (Boolean, Boolean) -> Unit,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionLabel(localized(TextKey.AnnouncementConsentTitle))
        SettingsCard { AnnouncementConsentRows(state, onChange, onRetry) }
    }
}

/** Thông báo của admin và sự kiện (lưu trên server). */
@Composable
private fun AnnouncementConsentRows(
    state: com.hienthai.fastowin.state.AnnouncementState,
    onChange: (Boolean, Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val preferences = state.preferences
    val enabled = preferences != null && !state.pending && !state.loading && state.preferenceIntent == null
    SettingsSwitchRow(
        icon = Icons.Rounded.Campaign, tile = SettingsTile.WHITE, title = localized(TextKey.AnnouncementAdminPush),
        subtitle = localized(TextKey.AnnouncementAdminPushDescription), checked = preferences?.adminEnabled ?: false,
        enabled = enabled, compact = true, onCheckedChange = { onChange(it, preferences?.eventEnabled ?: false) }
    )
    SettingsSwitchRow(
        icon = Icons.Rounded.Event, tile = SettingsTile.WHITE, title = localized(TextKey.AnnouncementEventPush),
        subtitle = localized(TextKey.AnnouncementEventPushDescription), checked = preferences?.eventEnabled ?: false,
        enabled = enabled, compact = true, showDivider = state.pending || state.error != null || (state.loading && preferences == null),
        onCheckedChange = { onChange(preferences?.adminEnabled ?: false, it) }
    )
    if (state.pending || (state.loading && preferences == null)) {
        Text(
            localized(if (state.pending) TextKey.AnnouncementSaving else TextKey.AnnouncementLoading),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = A1Palette.Muted
        )
    }
    if (state.error != null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                localized(
                    when {
                        state.error == "CONFLICT" -> TextKey.AnnouncementConflict
                        state.preferenceIntent != null -> TextKey.AnnouncementUncertain
                        else -> TextKey.AnnouncementError
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = A1Palette.RedText
            )
            AnnouncementActionButton(
                localized(TextKey.AnnouncementRetry), onRetry, enabled = !state.pending && !state.loading,
                outline = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LanguageDialog(
    selectedCode: String,
    systemLanguage: AppLanguage,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selection = normalizeAppLanguageSelection(selectedCode)
    // Thiết kế A1: bảng trượt "Ngôn ngữ" với các lựa chọn dạng radio và nút XONG.
    A1BottomSheet(
        title = localized(TextKey.LanguageSheetTitle),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("language_dialog")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LanguageOption(
                code = "system",
                title = localized(TextKey.SystemLanguage),
                subtitle = localized(TextKey.ResolvedSystemLanguage, "language" to systemLanguage.nativeName),
                isSelected = selection == "system"
            ) { onSelected("system") }
            selectableAppLanguages.forEach { language ->
                LanguageOption(language.code, language.nativeName, language.englishName, selection == language.code) {
                    onSelected(language.code)
                }
            }
        }
        ArcadeActionButton(
            label = localized(TextKey.DoneAction), onClick = onDismiss,
            style = ArcadeActionStyle.GOLD,
            large = true,
            modifier = Modifier.fillMaxWidth().testTag("language_close")
        )
    }
}

/** Lựa chọn ngôn ngữ (".opt"): viền xám, chọn thì viền xanh + nền xanh nhạt, chấm radio bên trái. */
@Composable
private fun LanguageOption(code: String, title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (isSelected) Color(0xFFEEF5FF) else Color.White, shape)
            .border(2.dp, if (isSelected) A1Palette.Blue2 else A1Palette.SurfaceAlt2, shape)
            .clip(shape)
            .testTag("language_option_$code")
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(3.dp, A1Palette.Line, CircleShape)
                .padding(5.dp)
                .background(if (isSelected) A1Palette.Blue2 else Color.Transparent, CircleShape)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = A1Palette.Ink, fontWeight = FontWeight.Bold)
            if (subtitle != title) Text(subtitle, color = A1Palette.Muted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
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
