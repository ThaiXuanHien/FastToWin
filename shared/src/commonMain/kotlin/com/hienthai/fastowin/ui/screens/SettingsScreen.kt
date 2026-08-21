package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppFontScale
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.data.preferences.BoardStyle
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
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cài đặt", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
                    }
                }
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
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            SettingsSection(
                title = "Phản hồi khi chơi",
                subtitle = "Áp dụng ngay khi bạn chạm vào bàn số."
            ) {
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = "Âm thanh hiệu ứng",
                    subtitle = "Phát âm báo khi chọn đúng, sai và kết thúc trận",
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
                    subtitle = "Hiện phản hồi đúng, sai và cảnh báo bám đuổi",
                    checked = preferences.visualEffectsEnabled,
                    onCheckedChange = {
                        onPreferencesChange(preferences.copy(visualEffectsEnabled = it))
                    }
                )
                OutlinedButton(
                    onClick = onPreviewSound,
                    enabled = preferences.soundEnabled,
                    modifier = Modifier.fillMaxWidth()
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

            TextButton(
                onClick = onOpenTutorial,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xem lại hướng dẫn chơi")
            }

            TextButton(
                onClick = { onPreferencesChange(AppPreferences()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Khôi phục cài đặt mặc định")
            }
            }
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
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onSelected(entry) },
                label = { Text(label(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
