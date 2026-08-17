package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MatchDetailSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
import com.hienthai.fastowin.protocol.PROFILE_AVATAR_IDS
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.MAX_ACCOUNT_PASSWORD_LENGTH
import com.hienthai.fastowin.state.accountPasswordConfirmationError
import com.hienthai.fastowin.state.accountPasswordError
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMatchDetail: (String) -> Unit,
    onCloseMatchDetail: () -> Unit,
    onEquipCosmetics: (String, String) -> Unit,
    onSave: (String, String?) -> Unit,
    canEdit: Boolean,
    isAccountLoading: Boolean,
    accountError: String?,
    accountNotice: String?,
    accountSessions: List<AccountSessionSnapshot>,
    areSessionsLoading: Boolean,
    onChangePassword: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAccountFeedback: () -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAllSessions: () -> Unit,
    onLogout: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val profile = state.profile
    var isEditing by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var selectedAvatarId by remember { mutableStateOf<String?>(null) }
    var showAccountSecurity by remember { mutableStateOf(false) }
    var showAccountSessions by remember { mutableStateOf(false) }
    var historyFilter by remember { mutableStateOf<MatchHistoryOutcome?>(null) }
    if (showAccountSecurity) {
        AccountSecurityDialog(
            isLoading = isAccountLoading,
            error = accountError,
            onDismiss = {
                if (!isAccountLoading) {
                    showAccountSecurity = false
                    onClearAccountFeedback()
                }
            },
            onChangePassword = onChangePassword,
            onDeleteAccount = onDeleteAccount
        )
    }
    if (showAccountSessions) {
        AccountSessionsDialog(
            sessions = accountSessions,
            isLoading = areSessionsLoading,
            error = accountError,
            notice = accountNotice,
            onRefresh = onLoadSessions,
            onRevokeSession = onRevokeSession,
            onRevokeAllSessions = onRevokeAllSessions,
            onDismiss = {
                if (!areSessionsLoading) {
                    showAccountSessions = false
                    onClearAccountFeedback()
                }
            }
        )
    }
    if (state.isMatchDetailLoading || state.matchDetail != null) {
        MatchDetailDialog(
            detail = state.matchDetail,
            isLoading = state.isMatchDetailLoading,
            onDismiss = onCloseMatchDetail
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
    ResponsiveScreen(
        modifier = modifier,
        maxContentWidth = 920.dp,
        includeBottomSafeDrawingInset = showBackButton,
        avoidKeyboard = isEditing
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
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
                    profile.progression.cosmetics.firstOrNull {
                        it.type == CosmeticType.TITLE && it.equipped
                    }?.let { Text(it.name, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold) }
                    Text("Mã người chơi: ${profile.playerCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Elo ${profile.statistics.eloRating}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (canEdit) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onClearAccountFeedback()
                        showAccountSessions = true
                        onLoadSessions()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Devices, null)
                    Text("  Thiết bị")
                }
                OutlinedButton(
                    onClick = {
                        onClearAccountFeedback()
                        showAccountSecurity = true
                    },
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
        val progression = profile.progression
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cấp ${progression.level}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("${progression.experiencePoints} XP", fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = {
                        progression.currentLevelExperience.toFloat() /
                            progression.nextLevelExperience.coerceAtLeast(1)
                    },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Text(
                    "${progression.currentLevelExperience}/${progression.nextLevelExperience} XP tới cấp tiếp theo",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        progression.season?.let { season ->
            val daysLeft = ((season.endsAtEpochMillis - epochMillis()).coerceAtLeast(0L) / 86_400_000L) + 1L
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(season.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("${season.tier} • ${season.rating} điểm mùa", color = MaterialTheme.colorScheme.primary)
                    Text("Còn $daysLeft ngày • ${season.rewardDescription}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("Nhiệm vụ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        (progression.dailyMissions + progression.weeklyMissions).forEach { mission ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(if (mission.completed) "✅" else "🎯")
                    Column(Modifier.weight(1f)) {
                        Text(mission.title, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(
                            progress = { mission.progress.toFloat() / mission.target.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                    Text("${mission.progress}/${mission.target}", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (progression.cosmetics.isNotEmpty()) {
            val equippedFrame = progression.cosmetics.firstOrNull { it.type == CosmeticType.FRAME && it.equipped }?.id
                ?: "frame_default"
            val equippedTitle = progression.cosmetics.firstOrNull { it.type == CosmeticType.TITLE && it.equipped }?.id
                ?: "title_rookie"
            Text("Bộ sưu tập", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Khung", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(progression.cosmetics.filter { it.type == CosmeticType.FRAME }, key = { it.id }) { cosmetic ->
                    FilterChip(
                        selected = cosmetic.equipped,
                        enabled = cosmetic.unlocked && !state.isProfileLoading,
                        onClick = { onEquipCosmetics(cosmetic.id, equippedTitle) },
                        label = { Text(if (cosmetic.unlocked) cosmetic.name else "🔒 ${cosmetic.name}") }
                    )
                }
            }
            Text("Danh hiệu", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(progression.cosmetics.filter { it.type == CosmeticType.TITLE }, key = { it.id }) { cosmetic ->
                    FilterChip(
                        selected = cosmetic.equipped,
                        enabled = cosmetic.unlocked && !state.isProfileLoading,
                        onClick = { onEquipCosmetics(equippedFrame, cosmetic.id) },
                        label = { Text(if (cosmetic.unlocked) cosmetic.name else "🔒 ${cosmetic.name}") }
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Trận", stats.totalMatches, Modifier.weight(1f))
            StatCard("Thắng", stats.wins, Modifier.weight(1f))
            StatCard("Thua", stats.losses, Modifier.weight(1f))
            StatCard("Hòa", stats.draws, Modifier.weight(1f))
        }

        if (profile.recentMatches.isNotEmpty()) {
            val scoreTrend = profile.recentMatches.take(10).asReversed().map { it.playerScore }
            val eloTrend = remember(profile.statistics.eloRating, profile.recentMatches) {
                buildEloTrend(profile.statistics.eloRating, profile.recentMatches.take(10))
            }
            TrendChart("Phong độ điểm • 10 trận", scoreTrend) { "$it điểm" }
            TrendChart("Biến động Elo", eloTrend) { "Elo $it" }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = historyFilter == null, onClick = { historyFilter = null }, label = { Text("Tất cả") })
            FilterChip(
                selected = historyFilter == MatchHistoryOutcome.WIN,
                onClick = { historyFilter = MatchHistoryOutcome.WIN },
                label = { Text("Thắng") }
            )
            FilterChip(
                selected = historyFilter == MatchHistoryOutcome.LOSS,
                onClick = { historyFilter = MatchHistoryOutcome.LOSS },
                label = { Text("Thua") }
            )
        }
        val visibleMatches = profile.recentMatches.filter { historyFilter == null || it.outcome == historyFilter }
        if (profile.recentMatches.isEmpty()) {
            Text("Bạn chưa có trận đấu hoàn thành.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (visibleMatches.isEmpty()) {
            Text("Không có trận phù hợp với bộ lọc.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleMatches.forEach { match ->
                    MatchHistoryCard(match, onClick = { onOpenMatchDetail(match.matchId) })
                }
            }
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
    val passwordError = accountPasswordError(newPassword)
    val confirmationError = accountPasswordConfirmationError(newPassword, confirmPassword)
    val passwordUnchanged = currentPassword.isNotEmpty() && currentPassword == newPassword
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
                if (newPassword.isNotEmpty() && passwordError != null) {
                    Text(passwordError, color = MaterialTheme.colorScheme.error)
                }
                confirmationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (passwordUnchanged) {
                    Text("Mật khẩu mới phải khác mật khẩu hiện tại.", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { onChangePassword(currentPassword, newPassword) },
                    enabled = !isLoading && currentPassword.isNotBlank() && passwordError == null &&
                        confirmationError == null && confirmPassword.isNotEmpty() && !passwordUnchanged,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else Text("Đổi mật khẩu")
                }
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
                    ) {
                        if (isLoading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        else Text("Xác nhận xóa vĩnh viễn")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Đóng") } }
    )
}

@Composable
private fun AccountSessionsDialog(
    sessions: List<AccountSessionSnapshot>,
    isLoading: Boolean,
    error: String?,
    notice: String?,
    onRefresh: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAllSessions: () -> Unit,
    onDismiss: () -> Unit
) {
    var sessionPendingRevoke by remember { mutableStateOf<AccountSessionSnapshot?>(null) }
    var confirmRevokeAll by remember { mutableStateOf(false) }

    sessionPendingRevoke?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingRevoke = null },
            title = { Text(if (session.isCurrent) "Đăng xuất thiết bị này?" else "Đăng xuất thiết bị?") },
            text = {
                Text(
                    if (session.isCurrent) {
                        "Bạn sẽ quay về màn đăng nhập trên thiết bị hiện tại."
                    } else {
                        "Phiên trên ${sessionDeviceLabel(session.devicePlatform)} sẽ bị thu hồi ngay."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sessionPendingRevoke = null
                    onRevokeSession(session.sessionId)
                }) { Text("Đăng xuất", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingRevoke = null }) { Text("Hủy") }
            }
        )
    }
    if (confirmRevokeAll) {
        AlertDialog(
            onDismissRequest = { confirmRevokeAll = false },
            title = { Text("Đăng xuất tất cả thiết bị?") },
            text = { Text("Tất cả phiên, bao gồm thiết bị này, sẽ bị thu hồi và bạn cần đăng nhập lại.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevokeAll = false
                    onRevokeAllSessions()
                }) { Text("Đăng xuất tất cả", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeAll = false }) { Text("Hủy") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thiết bị đăng nhập") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Bạn có thể thu hồi những phiên không còn sử dụng.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading && sessions.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (sessions.isEmpty()) {
                    Text("Không tìm thấy phiên đăng nhập đang hoạt động.")
                } else {
                    sessions.forEach { session ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Devices, contentDescription = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        buildString {
                                            append(sessionDeviceLabel(session.devicePlatform))
                                            if (session.isCurrent) append(" • Thiết bị này")
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = if (session.isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Hoạt động ${relativeSessionTime(session.lastSeenAtEpochMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        sessionExpiryLabel(session.expiresAtEpochMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { sessionPendingRevoke = session },
                                    enabled = !isLoading
                                ) { Text("Đăng xuất") }
                            }
                        }
                    }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = { confirmRevokeAll = true },
                    enabled = !isLoading && sessions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Đăng xuất tất cả thiết bị", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !isLoading) { Text("Làm mới") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Đóng") }
        }
    )
}

private fun sessionDeviceLabel(platform: String?): String = when (platform?.lowercase()) {
    "android" -> "Android"
    "ios" -> "iPhone / iPad"
    else -> "Thiết bị không xác định"
}

private fun relativeSessionTime(timestampMillis: Long): String {
    val elapsed = (epochMillis() - timestampMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1L -> "vừa xong"
        hours < 1L -> "$minutes phút trước"
        days < 1L -> "$hours giờ trước"
        else -> "$days ngày trước"
    }
}

private fun sessionExpiryLabel(expiresAtMillis: Long): String {
    val remainingDays = ((expiresAtMillis - epochMillis()).coerceAtLeast(0L) / 86_400_000L) + 1L
    return "Phiên còn hiệu lực khoảng $remainingDays ngày"
}

@Composable
private fun SecurePasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= MAX_ACCOUNT_PASSWORD_LENGTH) onValueChange(it) },
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
                )
            }
        },
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
private fun TrendChart(title: String, values: List<Int>, valueLabel: (Int) -> String) {
    if (values.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(valueLabel(values.last()), color = lineColor, fontWeight = FontWeight.Black)
            }
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                repeat(3) { index ->
                    val y = size.height * index / 2f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                val minValue = values.minOrNull() ?: 0
                val maxValue = values.maxOrNull() ?: minValue
                val range = (maxValue - minValue).coerceAtLeast(1)
                val stepX = if (values.size == 1) 0f else size.width / (values.size - 1)
                val points = values.mapIndexed { index, value ->
                    Offset(
                        x = if (values.size == 1) size.width / 2f else index * stepX,
                        y = size.height - (value - minValue).toFloat() / range * size.height
                    )
                }
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(lineColor, start, end, strokeWidth = 6f, cap = StrokeCap.Round)
                }
                points.forEach { drawCircle(lineColor, radius = 7f, center = it) }
            }
        }
    }
}

private fun buildEloTrend(currentElo: Int, recentMatches: List<MatchHistorySnapshot>): List<Int> {
    var rating = currentElo
    val newestToOldest = mutableListOf(rating)
    recentMatches.forEach { match ->
        rating -= match.eloChange
        newestToOldest += rating
    }
    return newestToOldest.asReversed()
}

@Composable
private fun MatchDetailDialog(
    detail: MatchDetailSnapshot?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    var replayIndex by remember(detail?.summary?.matchId) { mutableStateOf(0) }
    var isPlaying by remember(detail?.summary?.matchId) { mutableStateOf(false) }
    val events = detail?.events.orEmpty()
    LaunchedEffect(isPlaying, replayIndex, events.size) {
        if (!isPlaying) return@LaunchedEffect
        if (replayIndex >= events.lastIndex) {
            isPlaying = false
            return@LaunchedEffect
        }
        delay(550)
        replayIndex++
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLoading) "Đang tải trận đấu" else "Chi tiết trận đấu") },
        text = {
            if (isLoading || detail == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val mine = events.filter { it.isCurrentPlayer }
                val correct = mine.count { it.accepted }
                val wrong = mine.size - correct
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${detail.summary.playerScore} – ${detail.summary.opponentScore}  •  ${detail.summary.opponentName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Thời gian ${formatMatchDuration(detail.durationMillis)} • Đúng $correct • Sai $wrong",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (events.isEmpty()) {
                        Text("Trận này chưa có dữ liệu lượt bấm để phát lại.")
                    } else {
                        val event = events[replayIndex.coerceIn(events.indices)]
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Lượt ${replayIndex + 1}/${events.size}", style = MaterialTheme.typography.labelMedium)
                                Text(event.playerName, fontWeight = FontWeight.Bold)
                                Text(event.number.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                                Text(
                                    if (event.accepted) "Chọn đúng số ${event.expectedNumber}" else "Chọn sai • Cần tìm ${event.expectedNumber}",
                                    color = if (event.accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { replayIndex = (replayIndex - 1).coerceAtLeast(0) },
                                enabled = replayIndex > 0,
                                modifier = Modifier.weight(1f)
                            ) { Text("Trước") }
                            Button(
                                onClick = {
                                    if (replayIndex >= events.lastIndex) replayIndex = 0
                                    isPlaying = !isPlaying
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                                Text(if (isPlaying) " Dừng" else " Phát lại")
                            }
                            OutlinedButton(
                                onClick = { replayIndex = (replayIndex + 1).coerceAtMost(events.lastIndex) },
                                enabled = replayIndex < events.lastIndex,
                                modifier = Modifier.weight(1f)
                            ) { Text("Sau") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

private fun formatMatchDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun MatchHistoryCard(match: MatchHistorySnapshot, onClick: () -> Unit) {
    val (result, color) = when (match.outcome) {
        MatchHistoryOutcome.WIN -> "Thắng" to MaterialTheme.colorScheme.primary
        MatchHistoryOutcome.LOSS -> "Thua" to MaterialTheme.colorScheme.error
        MatchHistoryOutcome.DRAW -> "Hòa" to MaterialTheme.colorScheme.tertiary
    }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
