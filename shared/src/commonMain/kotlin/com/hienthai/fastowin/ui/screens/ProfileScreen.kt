package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
import com.hienthai.fastowin.protocol.PROFILE_AVATAR_IDS
import com.hienthai.fastowin.state.GameState

@Composable
fun ProfileScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (String, String?) -> Unit,
    canEdit: Boolean,
    isAccountLoading: Boolean,
    accountError: String?,
    onChangePassword: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onLogout: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val profile = state.profile
    var isEditing by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var selectedAvatarId by remember { mutableStateOf<String?>(null) }
    var showAccountSecurity by remember { mutableStateOf(false) }
    if (showAccountSecurity) {
        AccountSecurityDialog(
            isLoading = isAccountLoading,
            error = accountError,
            onDismiss = { if (!isAccountLoading) showAccountSecurity = false },
            onChangePassword = onChangePassword,
            onDeleteAccount = onDeleteAccount
        )
    }
    LaunchedEffect(profile) {
        profile?.let {
            displayName = it.displayName
            selectedAvatarId = it.avatarId
        }
    }
    LaunchedEffect(state.profileNotice) {
        if (state.profileNotice != null) isEditing = false
    }
    Column(
        modifier = modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showBackButton) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            else Spacer(Modifier.size(48.dp))
            Text("Hồ sơ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row {
                if (profile != null && canEdit) {
                    IconButton(onClick = { isEditing = !isEditing }, enabled = !state.isProfileSaving) {
                        Icon(Icons.Default.Edit, "Chỉnh sửa hồ sơ")
                    }
                }
                IconButton(onClick = onRefresh, enabled = !state.isProfileLoading && !state.isProfileSaving) {
                    Icon(Icons.Default.Refresh, "Làm mới hồ sơ")
                }
            }
        }

        if (state.isProfileLoading && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (profile == null) {
            Text("Chưa tải được hồ sơ. Hãy kiểm tra kết nối và thử lại.")
            return@Column
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(avatarEmoji(profile.avatarId), style = MaterialTheme.typography.headlineLarge)
                    }
                }
                Column {
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Mã người chơi: ${profile.playerCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Elo ${profile.statistics.eloRating}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (canEdit) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showAccountSecurity = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, null)
                    Text("  Bảo mật")
                }
                TextButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    Text("  Đăng xuất")
                }
            }
        }

        if (isEditing) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Chỉnh sửa hồ sơ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= MAX_PROFILE_DISPLAY_NAME_LENGTH) displayName = it },
                        label = { Text("Biệt danh") },
                        supportingText = { Text("${displayName.length}/$MAX_PROFILE_DISPLAY_NAME_LENGTH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Chọn ảnh đại diện", fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PROFILE_AVATAR_IDS.toList(), key = { it }) { avatarId ->
                            FilterChip(
                                selected = selectedAvatarId == avatarId,
                                onClick = { selectedAvatarId = avatarId },
                                label = { Text(avatarEmoji(avatarId), style = MaterialTheme.typography.titleLarge) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                displayName = profile.displayName
                                selectedAvatarId = profile.avatarId
                                isEditing = false
                            },
                            enabled = !state.isProfileSaving,
                            modifier = Modifier.weight(1f)
                        ) { Text("Hủy") }
                        Button(
                            onClick = { onSave(displayName, selectedAvatarId) },
                            enabled = displayName.isNotBlank() && !state.isProfileSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isProfileSaving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            else Text("Lưu")
                        }
                    }
                }
            }
        }

        state.profileNotice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val stats = profile.statistics
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Trận", stats.totalMatches, Modifier.weight(1f))
            StatCard("Thắng", stats.wins, Modifier.weight(1f))
            StatCard("Thua", stats.losses, Modifier.weight(1f))
            StatCard("Hòa", stats.draws, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Điểm cao", stats.highestScore, Modifier.weight(1f))
            StatCard("Chuỗi hiện tại", stats.currentWinStreak, Modifier.weight(1f))
            StatCard("Chuỗi tốt nhất", stats.bestWinStreak, Modifier.weight(1f))
        }
        val totalSelections = stats.correctSelections + stats.wrongSelections
        val accuracy = if (totalSelections == 0) 0 else stats.correctSelections * 100 / totalSelections
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Chính xác", "$accuracy%", Modifier.weight(1f))
            StatCard("Đúng / Sai", "${stats.correctSelections} / ${stats.wrongSelections}", Modifier.weight(1f))
            StatCard("Phản ứng TB", "${stats.averageReactionMillis} ms", Modifier.weight(1f))
        }

        Text("Thành tích", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (profile.achievements.isEmpty()) {
            Text("Chưa mở khóa thành tích nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.achievements.forEach { achievement ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🏆", style = MaterialTheme.typography.headlineSmall)
                            Column {
                                Text(achievement.title, fontWeight = FontWeight.Bold)
                                Text(
                                    achievement.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Text("Trận gần đây", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (profile.recentMatches.isEmpty()) {
            Text("Bạn chưa có trận đấu hoàn thành.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                profile.recentMatches.forEach { match -> MatchHistoryCard(match) }
            }
        }

    }
}

@Composable
private fun AccountSecurityDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bảo mật tài khoản") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Đổi mật khẩu", fontWeight = FontWeight.Bold)
                SecurePasswordField(currentPassword, "Mật khẩu hiện tại") { currentPassword = it }
                SecurePasswordField(newPassword, "Mật khẩu mới") { newPassword = it }
                SecurePasswordField(confirmPassword, "Nhập lại mật khẩu mới") { confirmPassword = it }
                Button(
                    onClick = { onChangePassword(currentPassword, newPassword) },
                    enabled = !isLoading && currentPassword.isNotBlank() && newPassword.length >= 8 &&
                        newPassword == confirmPassword,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Đổi mật khẩu") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Xóa tài khoản", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text("Thao tác này xóa vĩnh viễn hồ sơ, Elo, lịch sử và thành tích.")
                SecurePasswordField(deletePassword, "Nhập mật khẩu để xác nhận") { deletePassword = it }
                if (!confirmDelete) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !isLoading && deletePassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Tôi muốn xóa tài khoản") }
                } else {
                    Button(
                        onClick = { onDeleteAccount(deletePassword) },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Xác nhận xóa vĩnh viễn") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Đóng") } }
    )
}

@Composable
private fun SecurePasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun avatarEmoji(avatarId: String?): String = when (avatarId) {
    "rocket" -> "🚀"
    "target" -> "🎯"
    "trophy" -> "🏆"
    "crown" -> "👑"
    "star" -> "⭐"
    else -> "⚡"
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    StatCard(label, value.toString(), modifier)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MatchHistoryCard(match: MatchHistorySnapshot) {
    val (result, color) = when (match.outcome) {
        MatchHistoryOutcome.WIN -> "Thắng" to MaterialTheme.colorScheme.primary
        MatchHistoryOutcome.LOSS -> "Thua" to MaterialTheme.colorScheme.error
        MatchHistoryOutcome.DRAW -> "Hòa" to MaterialTheme.colorScheme.tertiary
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(match.opponentName, fontWeight = FontWeight.Bold)
                Text(match.roomName, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(result, color = color, fontWeight = FontWeight.Bold)
                Text("${match.playerScore} – ${match.opponentScore}")
                val eloText = if (match.eloChange >= 0) "+${match.eloChange}" else match.eloChange.toString()
                Text("Elo $eloText", color = if (match.eloChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
}
