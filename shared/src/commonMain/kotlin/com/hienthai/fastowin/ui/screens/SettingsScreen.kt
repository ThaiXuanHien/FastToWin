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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppFontScale
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.data.preferences.BoardStyle
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

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
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)
    ArcadeBackdrop(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                FastToWinHeader(
                    title = "Cài đặt",
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

                    SettingsSection(
                        title = "Phản hồi khi chơi",
                        subtitle = "Áp dụng ngay khi bạn chạm vào bàn số."
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            title = "Âm thanh hiệu ứng",
                            subtitle = "Báo đúng, sai và kết thúc trận",
                            checked = preferences.soundEnabled,
                            onCheckedChange = {
                                onPreferencesChange(preferences.copy(soundEnabled = it))
                                if (it) onPreviewSound()
                            }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Vibration,
                            title = "Rung phản hồi",
                            subtitle = "Rung nhẹ khi chạm số",
                            checked = preferences.vibrationEnabled,
                            onCheckedChange = { onPreferencesChange(preferences.copy(vibrationEnabled = it)) }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.ColorLens,
                            title = "Hiệu ứng hình ảnh",
                            subtitle = "Combo và cảnh báo bám đuổi",
                            checked = preferences.visualEffectsEnabled,
                            onCheckedChange = {
                                onPreferencesChange(preferences.copy(visualEffectsEnabled = it))
                            }
                        )
                        OutlinedButton(
                            onClick = onPreviewSound,
                            enabled = preferences.soundEnabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Nghe thử âm thanh")
                        }
                    }

                    SettingsSection(
                        title = "Giao diện",
                        subtitle = "Tùy chọn được lưu riêng trên thiết bị này."
                    ) {
                        SettingChoiceTitle(Icons.Rounded.ColorLens, "Chủ đề ứng dụng")
                        ChoiceRow(
                            entries = AppThemeMode.entries,
                            selected = preferences.themeMode,
                            label = {
                                when (it) {
                                    AppThemeMode.SYSTEM -> "Hệ thống"
                                    AppThemeMode.LIGHT -> "Sáng"
                                    AppThemeMode.DARK -> "Tối"
                                }
                            },
                            onSelected = { onPreferencesChange(preferences.copy(themeMode = it)) }
                        )

                        SettingChoiceTitle(Icons.Rounded.ColorLens, "Màu bàn số")
                        ChoiceRow(
                            entries = BoardStyle.entries,
                            selected = preferences.boardStyle,
                            label = {
                                when (it) {
                                    BoardStyle.CLASSIC -> "Cổ điển"
                                    BoardStyle.OCEAN -> "Đại dương"
                                    BoardStyle.HIGH_CONTRAST -> "Tương phản"
                                }
                            },
                            onSelected = { onPreferencesChange(preferences.copy(boardStyle = it)) }
                        )

                        SettingChoiceTitle(Icons.Rounded.FormatSize, "Kích thước chữ")
                        ChoiceRow(
                            entries = AppFontScale.entries,
                            selected = preferences.fontScale,
                            label = {
                                when (it) {
                                    AppFontScale.COMPACT -> "Nhỏ"
                                    AppFontScale.STANDARD -> "Chuẩn"
                                    AppFontScale.LARGE -> "Lớn"
                                }
                            },
                            onSelected = { onPreferencesChange(preferences.copy(fontScale = it)) }
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenTutorial,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Xem lại hướng dẫn chơi")
                    }

                    OutlinedButton(
                        onClick = { onPreferencesChange(AppPreferences()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Khôi phục cài đặt mặc định")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHero() {
    ArcadePanel(
        modifier = Modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.tertiary
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
            "PREFERENCES",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            "Chơi theo cách của bạn",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            "Âm thanh, rung và giao diện được lưu riêng trên thiết bị.",
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
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
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
            accent = MaterialTheme.colorScheme.primary
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
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            onCheckedChange = onCheckedChange,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
        )
    }
}

@Composable
private fun SettingChoiceTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useEqualRow = maxWidth >= 280.dp && LocalDensity.current.fontScale < 1.35f
        if (useEqualRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    ChoiceChip(
                        selected = entry == selected,
                        label = label(entry),
                        onClick = { onSelected(entry) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    ChoiceChip(
                        selected = entry == selected,
                        label = label(entry),
                        onClick = { onSelected(entry) },
                        modifier = Modifier.widthIn(min = 92.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp)
    )
}
