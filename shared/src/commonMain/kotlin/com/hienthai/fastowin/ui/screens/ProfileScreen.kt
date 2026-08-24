package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.MatchDetailSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.MissionDifficulty
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_FRAME_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_TITLE_TARGET
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
import com.hienthai.fastowin.protocol.PROFILE_AVATAR_IDS
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.GameModeStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.MAX_ACCOUNT_PASSWORD_LENGTH
import com.hienthai.fastowin.state.accountPasswordConfirmationError
import com.hienthai.fastowin.state.accountPasswordError
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.components.RewardAmounts
import com.hienthai.fastowin.ui.components.WalletBalanceRow
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.WalletDeltaAmounts
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.playerAvatarEmoji
import kotlinx.coroutines.delay

enum class ProfileSection {
    STATISTICS,
    WALLET,
    MISSIONS,
    COLLECTION,
    RECENT_MATCHES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    serverUrl: String,
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMatchDetail: (String) -> Unit,
    onCloseMatchDetail: () -> Unit,
    onEquipCosmetics: (String, String) -> Unit,
    onClaimMissionReward: (String) -> Unit,
    onSave: (String, String?) -> Unit,
    onUploadAvatar: (ByteArray) -> Unit,
    canEdit: Boolean,
    profileOverride: PlayerProfileSnapshot? = null,
    isExternalProfile: Boolean = false,
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
    sessionStartedAtMillis: Long? = null,
    onInviteToClan: ((String) -> Unit)? = null,
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSection: (ProfileSection) -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)
    val profile = if (isExternalProfile) profileOverride else state.profile
    val isProfileLoading = if (isExternalProfile) state.isFriendProfileLoading else state.isProfileLoading
    val clipboardManager = LocalClipboardManager.current
    var isEditing by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var selectedAvatarId by remember { mutableStateOf<String?>(null) }
    var showAccountSecurity by remember { mutableStateOf(false) }
    var showAccountSessions by remember { mutableStateOf(false) }
    var isPlayerCodeCopied by remember(profile?.playerCode) { mutableStateOf(false) }
    if (!isExternalProfile && showAccountSecurity) {
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
    if (!isExternalProfile && showAccountSessions) {
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
    if (!isExternalProfile && (state.isMatchDetailLoading || state.matchDetail != null)) {
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
    LaunchedEffect(isPlayerCodeCopied) {
        if (isPlayerCodeCopied) {
            delay(2_000)
            isPlayerCodeCopied = false
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        ResponsiveScreen(
            modifier = Modifier.fillMaxSize(),
            maxContentWidth = 920.dp,
            applySafeDrawingInsets = showBackButton,
            includeBottomSafeDrawingInset = showBackButton,
            avoidKeyboard = isEditing
        ) { contentModifier ->
        PullToRefreshBox(
            isRefreshing = isProfileLoading,
            onRefresh = {
                if (!isProfileLoading && (!canEdit || !state.isProfileSaving)) onRefresh()
            },
            modifier = contentModifier
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        if (showBackButton) {
            FastToWinHeader(
                title = if (isExternalProfile) "Hồ sơ bạn bè" else "Hồ sơ",
                gold = state.profile?.progression?.gold ?: 0,
                gems = state.profile?.progression?.gems ?: 0,
                unreadNotifications = state.unreadNotificationCount,
                onNotifications = onOpenNotifications,
                onBack = onBack,
                applySafeDrawingInset = false
            )
        }
        if (isProfileLoading && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (profile == null) {
            Text("Chưa tải được hồ sơ. Hãy kiểm tra kết nối và thử lại.")
            return@Column
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth().testTag("profile_identity_card"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = if (canEdit) 48.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PlayerAvatar(
                        displayName = profile.displayName,
                        avatarId = profile.avatarId,
                        frameId = profile.progression.cosmetics.firstOrNull {
                            it.type == CosmeticType.FRAME && it.equipped
                        }?.id ?: "frame_default",
                        size = 68.dp,
                        imageUrl = "$serverUrl/api/avatar/${profile.userId}"
                    )
                    Column {
                        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        profile.progression.cosmetics.firstOrNull {
                            it.type == CosmeticType.TITLE && it.equipped
                        }?.let { Text(it.name, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Mã người chơi: ${profile.playerCode}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(profile.playerCode))
                                    isPlayerCodeCopied = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Sao chép mã người chơi",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text("Elo ${profile.statistics.eloRating}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                        if (isExternalProfile && onInviteToClan != null && state.profile?.clanId != null && profile.clanId == null) {
                            Button(
                                onClick = { onInviteToClan.invoke(profile.playerCode) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Mời vào bang")
                            }
                        }
                        if (!isExternalProfile && sessionStartedAtMillis != null) {
                            CurrentSessionDuration(sessionStartedAtMillis)
                        }
                    }
                }
                if (canEdit) {
                    IconButton(
                        onClick = { isEditing = !isEditing },
                        enabled = !state.isProfileSaving,
                        modifier = Modifier.align(Alignment.TopEnd).testTag("profile_edit")
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Chỉnh sửa hồ sơ",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chọn ảnh đại diện", fontWeight = FontWeight.Medium)
                        com.hienthai.fastowin.platform.ImagePicker(
                            onImageSelected = { bytes ->
                                if (bytes != null) onUploadAvatar(bytes)
                            }
                        ) { onClick ->
                            TextButton(onClick = onClick) {
                                Text("Tải ảnh lên")
                            }
                        }
                    }
                    
                    val avatarCosmetics = profile.progression.cosmetics
                        .filter { it.type == CosmeticType.AVATAR }
                        .associateBy(CosmeticSnapshot::id)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PROFILE_AVATAR_IDS.toList(), key = { it }) { avatarId ->
                            val unlocked = avatarId != DAILY_CHECK_IN_AVATAR_ID ||
                                avatarCosmetics[avatarId]?.unlocked == true
                            FilterChip(
                                selected = selectedAvatarId == avatarId,
                                enabled = unlocked,
                                onClick = { if (unlocked) selectedAvatarId = avatarId },
                                label = {
                                    Text(
                                        (if (unlocked) "" else "🔒 ") + playerAvatarEmoji(avatarId),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
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

        if (!isExternalProfile) state.profileNotice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val progression = profile.progression
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cấp ${progression.level}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("${progression.experiencePoints} XP", fontWeight = FontWeight.Bold)
                }
                WalletBalanceRow(gold = progression.gold, gems = progression.gems)
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
                    Text(
                        if (season.placementMatchesPlayed < season.placementMatchesRequired) {
                            "Phân hạng ${season.placementMatchesPlayed}/${season.placementMatchesRequired} • Elo tạm thời ${season.rating}"
                        } else {
                            "${season.tier} • ${season.rating} Elo"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Elo cao nhất mùa: ${season.peakRating}", style = MaterialTheme.typography.bodySmall)
                    Text("Còn $daysLeft ngày • ${season.rewardDescription}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        DailyCheckInCalendar(progression.dailyCheckIn)

        DailyCheckInMilestones(
            bestStreak = progression.dailyCheckIn.bestStreak,
            totalCheckIns = progression.dailyCheckIn.totalCheckIns
        )

        Text("Hoạt động", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            AccountActionRow(
                icon = Icons.Default.Insights,
                title = "Thống kê & thành tích",
                subtitle = "Hiệu suất thi đấu và các mốc đã mở khóa",
                onClick = { onOpenSection(ProfileSection.STATISTICS) },
                modifier = Modifier.testTag("profile_section_statistics")
            )
            HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
            if (canEdit) {
                AccountActionRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "Lịch sử tài sản",
                    subtitle = "Các lần nhận và sử dụng Vàng, Gem, XP",
                    onClick = { onOpenSection(ProfileSection.WALLET) },
                    modifier = Modifier.testTag("profile_section_wallet")
                )
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
            }
            AccountActionRow(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = "Nhiệm vụ",
                subtitle = "Theo dõi tiến độ và nhận thưởng",
                onClick = { onOpenSection(ProfileSection.MISSIONS) },
                modifier = Modifier.testTag("profile_section_missions")
            )
            HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
            AccountActionRow(
                icon = Icons.Default.Collections,
                title = "Bộ sưu tập",
                subtitle = "Khung, danh hiệu và ảnh đại diện",
                onClick = { onOpenSection(ProfileSection.COLLECTION) },
                modifier = Modifier.testTag("profile_section_collection")
            )
            HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
            AccountActionRow(
                icon = Icons.Default.History,
                title = "Trận gần đây",
                subtitle = "Lịch sử đấu và biến động Elo",
                onClick = { onOpenSection(ProfileSection.RECENT_MATCHES) },
                modifier = Modifier.testTag("profile_section_recent_matches")
            )
        }

        if (canEdit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Cài đặt & tài khoản",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    AccountActionRow(
                        icon = Icons.Default.Settings,
                        title = "Cài đặt ứng dụng",
                        subtitle = "Âm thanh, rung, hiệu ứng và hướng dẫn",
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("profile_settings")
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                    AccountActionRow(
                        icon = Icons.Default.Devices,
                        title = "Thiết bị đăng nhập",
                        subtitle = "Xem và đăng xuất các phiên đang hoạt động",
                        onClick = {
                            onClearAccountFeedback()
                            showAccountSessions = true
                            onLoadSessions()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                    AccountActionRow(
                        icon = Icons.Default.Lock,
                        title = "Bảo mật tài khoản",
                        subtitle = "Đổi mật khẩu hoặc xóa tài khoản",
                        onClick = {
                            onClearAccountFeedback()
                            showAccountSecurity = true
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                    AccountActionRow(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = "Đăng xuất",
                        subtitle = "Kết thúc phiên trên thiết bị này",
                        isDestructive = true,
                        onClick = onLogout
                    )
                }
            }
        }

        }
        }
        }
        if (isPlayerCodeCopied) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    "Đã sao chép mã",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSectionScreen(
    state: GameState,
    profile: PlayerProfileSnapshot,
    section: ProfileSection,
    isExternalProfile: Boolean,
    canEdit: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMatchDetail: (String) -> Unit,
    onCloseMatchDetail: () -> Unit,
    onEquipCosmetics: (String, String) -> Unit,
    onClaimMissionReward: (String) -> Unit,
    onSave: (String, String?) -> Unit,
    onOpenNotifications: () -> Unit
) {
    SystemBackHandler(onBack = onBack)
    val isLoading = when {
        section == ProfileSection.WALLET -> state.isWalletHistoryLoading
        isExternalProfile -> state.isFriendProfileLoading
        else -> state.isProfileLoading
    }
    var historyFilter by remember { mutableStateOf<MatchHistoryOutcome?>(null) }

    if (!isExternalProfile && (state.isMatchDetailLoading || state.matchDetail != null)) {
        MatchDetailDialog(
            detail = state.matchDetail,
            isLoading = state.isMatchDetailLoading,
            onDismiss = onCloseMatchDetail
        )
    }

    Column(modifier = Modifier.fillMaxSize().testTag("profile_section_screen:${section.name}")) {
        FastToWinHeader(
            title = when (section) {
                ProfileSection.STATISTICS -> "Thống kê & thành tích"
                ProfileSection.WALLET -> "Lịch sử tài sản"
                ProfileSection.MISSIONS -> "Nhiệm vụ"
                ProfileSection.COLLECTION -> "Bộ sưu tập"
                ProfileSection.RECENT_MATCHES -> "Trận gần đây"
            },
            gold = state.profile?.progression?.gold ?: 0,
            gems = state.profile?.progression?.gems ?: 0,
            unreadNotifications = state.unreadNotificationCount,
            onNotifications = onOpenNotifications,
            onBack = onBack
        )
        ResponsiveScreen(
            modifier = Modifier.weight(1f),
            maxContentWidth = 920.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                modifier = contentModifier
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                        )
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (section) {
                        ProfileSection.STATISTICS -> StatisticsAchievementsSectionContent(profile)
                        ProfileSection.WALLET -> WalletHistorySectionContent(state.walletTransactions)
                        ProfileSection.MISSIONS -> MissionSectionContent(
                            state = state,
                            profile = profile,
                            canEdit = canEdit,
                            onClaimMissionReward = onClaimMissionReward
                        )
                        ProfileSection.COLLECTION -> CollectionSectionContent(
                            state = state,
                            profile = profile,
                            canEdit = canEdit,
                            isLoading = isLoading,
                            onEquipCosmetics = onEquipCosmetics,
                            onSave = onSave
                        )
                        ProfileSection.RECENT_MATCHES -> RecentMatchesSectionContent(
                            profile = profile,
                            isExternalProfile = isExternalProfile,
                            historyFilter = historyFilter,
                            onFilterChange = { historyFilter = it },
                            onOpenMatchDetail = onOpenMatchDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletHistorySectionContent(transactions: List<WalletTransactionSnapshot>) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("wallet_history_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (transactions.isEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                    Text("Chưa có giao dịch", fontWeight = FontWeight.Bold)
                    Text(
                        "Phần thưởng và vật phẩm đã mua sẽ xuất hiện tại đây.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            transactions.forEach { transaction ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().testTag("wallet_transaction:${transaction.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(walletSourceLabel(transaction.sourceType), fontWeight = FontWeight.Bold)
                            Text(
                                walletRelativeTime(transaction.createdAtEpochMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            WalletDeltaAmounts(
                                gold = transaction.goldDelta,
                                xp = transaction.xpDelta,
                                gems = transaction.gemsDelta
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun walletSourceLabel(sourceType: String): String = when (sourceType) {
    "DAILY_CHECK_IN" -> "Điểm danh hằng ngày"
    "MISSION" -> "Thưởng nhiệm vụ"
    "MATCH" -> "Thưởng trận đấu"
    "CLAN_QUEST" -> "Nhiệm vụ bang hội"
    "COSMETIC_PURCHASE" -> "Mua vật phẩm"
    "TOURNAMENT_ENTRY" -> "Phí tham gia giải đấu"
    "TOURNAMENT_PRIZE" -> "Giải thưởng vô địch"
    "STORE_PURCHASE" -> "Nạp Gem"
    else -> "Điều chỉnh tài sản"
}

private fun walletRelativeTime(createdAtEpochMillis: Long, nowMillis: Long = epochMillis()): String {
    val elapsed = (nowMillis - createdAtEpochMillis).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "Vừa xong"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} phút trước"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L} giờ trước"
        else -> "${elapsed / 86_400_000L} ngày trước"
    }
}

@Composable
private fun StatisticsAchievementsSectionContent(profile: PlayerProfileSnapshot) {
    val stats = profile.statistics
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile_statistics_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Trận", stats.totalMatches, Modifier.weight(1f))
            StatCard("Thắng", stats.wins, Modifier.weight(1f))
            StatCard("Thua", stats.losses, Modifier.weight(1f))
            StatCard("Hòa", stats.draws, Modifier.weight(1f))
        }

        if (profile.modeStatistics.isNotEmpty()) {
            Text("Thống kê theo chế độ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier.fillMaxWidth().testTag("mode_statistics"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                profile.modeStatistics
                    .sortedWith(compareBy<GameModeStatisticsSnapshot> { it.gameMode.unlockLevel }.thenBy { it.gameMode.name })
                    .forEach { ModeStatisticsCard(it) }
            }
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
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
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
    }
}

@Composable
private fun MissionSectionContent(
    state: GameState,
    profile: PlayerProfileSnapshot,
    canEdit: Boolean,
    onClaimMissionReward: (String) -> Unit
) {
    val missionGroups = listOf(
        "Hằng ngày" to profile.progression.dailyMissions,
        "Hằng tuần" to profile.progression.weeklyMissions
    )
    if (missionGroups.all { it.second.isEmpty() }) {
        Text("Chưa có nhiệm vụ mới.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    missionGroups.forEach { (sectionTitle, missions) ->
        if (missions.isNotEmpty()) {
            Text(sectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        missions.forEach { mission ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                mission.title,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            MissionDifficultyBadge(mission.difficulty)
                        }
                        LinearProgressIndicator(
                            progress = { mission.progress.toFloat() / mission.target.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Text("${mission.progress}/${mission.target}", style = MaterialTheme.typography.bodySmall)
                        RewardAmounts(
                            gold = mission.rewardGold,
                            xp = mission.rewardXp,
                            gems = mission.rewardGems
                        )
                    }
                    when {
                        mission.rewardClaimed -> Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.testTag("claim_mission:${mission.code}")
                        ) {
                            Text("Đã nhận")
                        }
                        mission.completed && canEdit -> Button(
                            onClick = { onClaimMissionReward(mission.code) },
                            enabled = state.claimingMissionCode == null,
                            modifier = Modifier.testTag("claim_mission:${mission.code}")
                        ) {
                            if (state.claimingMissionCode == mission.code) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Nhận")
                            }
                        }
                        mission.completed -> Text("Hoàn thành", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionDifficultyBadge(difficulty: MissionDifficulty) {
    val label = when (difficulty) {
        MissionDifficulty.EASY -> "Dễ"
        MissionDifficulty.NORMAL -> "Vừa"
        MissionDifficulty.HARD -> "Khó"
        MissionDifficulty.ELITE -> "Thử thách"
    }
    val containerColor = when (difficulty) {
        MissionDifficulty.EASY -> MaterialTheme.colorScheme.secondaryContainer
        MissionDifficulty.NORMAL -> MaterialTheme.colorScheme.primaryContainer
        MissionDifficulty.HARD -> MaterialTheme.colorScheme.tertiaryContainer
        MissionDifficulty.ELITE -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (difficulty) {
        MissionDifficulty.EASY -> MaterialTheme.colorScheme.onSecondaryContainer
        MissionDifficulty.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
        MissionDifficulty.HARD -> MaterialTheme.colorScheme.onTertiaryContainer
        MissionDifficulty.ELITE -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.testTag("mission_difficulty_${difficulty.name.lowercase()}")
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun CollectionSectionContent(
    state: GameState,
    profile: PlayerProfileSnapshot,
    canEdit: Boolean,
    isLoading: Boolean,
    onEquipCosmetics: (String, String) -> Unit,
    onSave: (String, String?) -> Unit
) {
    val cosmetics = profile.progression.cosmetics
    if (cosmetics.isEmpty()) {
        Text("Bộ sưu tập đang trống.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val equippedFrame = cosmetics.firstOrNull { it.type == CosmeticType.FRAME && it.equipped }?.id
        ?: "frame_default"
    val equippedTitle = cosmetics.firstOrNull { it.type == CosmeticType.TITLE && it.equipped }?.id
        ?: "title_rookie"
    Text("Khung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cosmetics.filter { it.type == CosmeticType.FRAME }, key = { it.id }) { cosmetic ->
            FilterChip(
                selected = cosmetic.equipped,
                enabled = canEdit && cosmetic.unlocked && !isLoading,
                onClick = { onEquipCosmetics(cosmetic.id, equippedTitle) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlayerAvatar(
                            displayName = profile.displayName,
                            avatarId = profile.avatarId,
                            frameId = cosmetic.id,
                            size = 32.dp
                        )
                        Text(cosmetic.displayLabel())
                    }
                }
            )
        }
    }
    Text("Danh hiệu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cosmetics.filter { it.type == CosmeticType.TITLE }, key = { it.id }) { cosmetic ->
            FilterChip(
                selected = cosmetic.equipped,
                enabled = canEdit && cosmetic.unlocked && !isLoading,
                onClick = { onEquipCosmetics(equippedFrame, cosmetic.id) },
                label = { Text(cosmetic.displayLabel()) }
            )
        }
    }
    val specialAvatars = cosmetics.filter { it.type == CosmeticType.AVATAR }
    if (specialAvatars.isNotEmpty()) {
        Text("Ảnh đại diện đặc biệt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(specialAvatars, key = { it.id }) { cosmetic ->
                FilterChip(
                    selected = cosmetic.equipped,
                    enabled = canEdit && cosmetic.unlocked && !state.isProfileSaving,
                    onClick = { onSave(profile.displayName, cosmetic.id) },
                    label = { Text("${playerAvatarEmoji(cosmetic.id)} ${cosmetic.displayLabel()}") }
                )
            }
        }
    }
}

@Composable
private fun RecentMatchesSectionContent(
    profile: PlayerProfileSnapshot,
    isExternalProfile: Boolean,
    historyFilter: MatchHistoryOutcome?,
    onFilterChange: (MatchHistoryOutcome?) -> Unit,
    onOpenMatchDetail: (String) -> Unit
) {
    if (profile.recentMatches.isNotEmpty()) {
        val scoreTrend = profile.recentMatches.take(10).asReversed().map { it.playerScore }
        val eloTrend = remember(profile.statistics.eloRating, profile.recentMatches) {
            buildEloTrend(profile.statistics.eloRating, profile.recentMatches.take(10))
        }
        TrendChart("Phong độ điểm • 10 trận", scoreTrend) { "$it điểm" }
        TrendChart("Biến động Elo", eloTrend) { "Elo $it" }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = historyFilter == null,
            onClick = { onFilterChange(null) },
            label = { Text("Tất cả") },
            modifier = Modifier.testTag("history_filter_all")
        )
        FilterChip(
            selected = historyFilter == MatchHistoryOutcome.WIN,
            onClick = { onFilterChange(MatchHistoryOutcome.WIN) },
            label = { Text("Thắng") },
            modifier = Modifier.testTag("history_filter_win")
        )
        FilterChip(
            selected = historyFilter == MatchHistoryOutcome.LOSS,
            onClick = { onFilterChange(MatchHistoryOutcome.LOSS) },
            label = { Text("Thua") },
            modifier = Modifier.testTag("history_filter_loss")
        )
    }
    val visibleMatches = profile.recentMatches.filter { historyFilter == null || it.outcome == historyFilter }
    when {
        profile.recentMatches.isEmpty() -> Text(
            "Chưa có trận đấu hoàn thành.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        visibleMatches.isEmpty() -> Text(
            "Không có trận phù hợp với bộ lọc.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> visibleMatches.forEach { match ->
            MatchHistoryCard(
                match,
                onClick = if (isExternalProfile) null else ({ onOpenMatchDetail(match.matchId) })
            )
        }
    }
}

@Composable
private fun CurrentSessionDuration(sessionStartedAtMillis: Long) {
    var elapsedSeconds by remember(sessionStartedAtMillis) { mutableStateOf(0L) }
    LaunchedEffect(sessionStartedAtMillis) {
        while (true) {
            elapsedSeconds = ((epochMillis() - sessionStartedAtMillis) / 1_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Default.Timer,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Phiên hiện tại ${formatSessionDuration(elapsedSeconds)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatSessionDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    fun Long.twoDigits() = toString().padStart(2, '0')
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}"
}

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = if (isDestructive) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = contentColor)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDestructive) contentColor.copy(alpha = 0.78f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.62f)
            )
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

private data class DailyCheckInMilestone(
    val icon: String,
    val title: String,
    val reward: String,
    val progress: Int,
    val target: Int
)

@Composable
private fun DailyCheckInCalendar(checkIn: DailyCheckInSnapshot) {
    val today = checkIn.todayDate?.toProfileCalendarDate() ?: return
    val currentMonth = ProfileCalendarMonth(today.year, today.month)
    val earliestMonth = currentMonth.shift(-11)
    var visibleMonth by remember(checkIn.todayDate) { mutableStateOf(currentMonth) }
    val checkedDates = remember(checkIn.historyDates) { checkIn.historyDates.toSet() }
    val cells = profileCalendarCells(visibleMonth)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("daily_check_in_calendar"),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Lịch điểm danh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Theo dõi tối đa 12 tháng gần nhất",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { visibleMonth = visibleMonth.shift(-1) },
                    enabled = visibleMonth > earliestMonth
                ) {
                    Icon(Icons.Default.ChevronLeft, "Tháng trước")
                }
                Text(
                    "Tháng ${visibleMonth.month}/${visibleMonth.year}",
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { visibleMonth = visibleMonth.shift(1) },
                    enabled = visibleMonth < currentMonth
                ) {
                    Icon(Icons.Default.ChevronRight, "Tháng sau")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                PROFILE_CALENDAR_WEEKDAYS.forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f).height(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val dateKey = profileCalendarDateKey(visibleMonth, day)
                                val checked = dateKey in checkedDates
                                val isToday = visibleMonth == currentMonth && day == today.day
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = when {
                                        checked -> MaterialTheme.colorScheme.primaryContainer
                                        isToday -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> Color.Transparent
                                    },
                                    border = if (isToday) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            day.toString(),
                                            fontWeight = if (checked || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (checked) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("● Đã điểm danh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("Viền: hôm nay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal data class ProfileCalendarDate(val year: Int, val month: Int, val day: Int)

internal data class ProfileCalendarMonth(val year: Int, val month: Int) : Comparable<ProfileCalendarMonth> {
    override fun compareTo(other: ProfileCalendarMonth): Int = monthIndex.compareTo(other.monthIndex)

    fun shift(months: Int): ProfileCalendarMonth {
        val shifted = monthIndex + months
        val shiftedYear = if (shifted >= 0) shifted / 12 else (shifted - 11) / 12
        return ProfileCalendarMonth(shiftedYear, shifted - shiftedYear * 12 + 1)
    }

    private val monthIndex: Int get() = year * 12 + month - 1
}

internal fun String.toProfileCalendarDate(): ProfileCalendarDate? {
    val parts = split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..profileCalendarDaysInMonth(year, month)) return null
    return ProfileCalendarDate(year, month, day)
}

internal fun profileCalendarCells(month: ProfileCalendarMonth): List<Int?> {
    val leadingEmptyCells = profileCalendarMondayIndex(month.year, month.month, 1)
    val days = profileCalendarDaysInMonth(month.year, month.month)
    val cellCount = ((leadingEmptyCells + days + 6) / 7) * 7
    return List(cellCount) { index ->
        (index - leadingEmptyCells + 1).takeIf { it in 1..days }
    }
}

private fun profileCalendarMondayIndex(year: Int, month: Int, day: Int): Int {
    var adjustedYear = year
    var adjustedMonth = month
    if (adjustedMonth < 3) {
        adjustedMonth += 12
        adjustedYear--
    }
    val yearInCentury = adjustedYear % 100
    val century = adjustedYear / 100
    val saturdayFirst = (
        day + (13 * (adjustedMonth + 1)) / 5 + yearInCentury + yearInCentury / 4 +
            century / 4 + 5 * century
        ) % 7
    return (saturdayFirst + 5) % 7
}

private fun profileCalendarDaysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun profileCalendarDateKey(month: ProfileCalendarMonth, day: Int): String =
    "${month.year.toString().padStart(4, '0')}-${month.month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

private val PROFILE_CALENDAR_WEEKDAYS = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

@Composable
private fun DailyCheckInMilestones(bestStreak: Int, totalCheckIns: Int) {
    val milestones = listOf(
        DailyCheckInMilestone(
            icon = "🏆",
            title = "7 ngày liên tiếp",
            reward = "Thành tích Khởi đầu đều đặn",
            progress = bestStreak,
            target = DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET
        ),
        DailyCheckInMilestone(
            icon = "🎖️",
            title = "30 ngày liên tiếp",
            reward = "Danh hiệu Chuyên cần",
            progress = bestStreak,
            target = DAILY_CHECK_IN_TITLE_TARGET
        ),
        DailyCheckInMilestone(
            icon = "📅",
            title = "50 lần điểm danh",
            reward = "Ảnh đại diện đặc biệt",
            progress = totalCheckIns,
            target = DAILY_CHECK_IN_AVATAR_TARGET
        ),
        DailyCheckInMilestone(
            icon = "🛡️",
            title = "100 lần điểm danh",
            reward = "Khung Bền bỉ",
            progress = totalCheckIns,
            target = DAILY_CHECK_IN_FRAME_TARGET
        )
    )
    Column(
        modifier = Modifier.fillMaxWidth().testTag("daily_check_in_milestones"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Mốc điểm danh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Chuỗi tốt nhất $bestStreak ngày • Tổng $totalCheckIns lần",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        milestones.forEach { milestone ->
            val progress = milestone.progress.coerceAtMost(milestone.target)
            val unlocked = progress >= milestone.target
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(if (unlocked) "✅" else milestone.icon, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(milestone.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            milestone.reward,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { progress.toFloat() / milestone.target },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                    Text("$progress/${milestone.target}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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
private fun ModeStatisticsCard(statistics: GameModeStatisticsSnapshot) {
    val winRate = if (statistics.totalMatches == 0) 0 else statistics.wins * 100 / statistics.totalMatches
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("mode_statistics:${statistics.gameMode.name}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(statistics.gameMode.displayName(), fontWeight = FontWeight.Bold)
                Text(
                    "$winRate% thắng",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "${statistics.totalMatches} trận • ${statistics.wins} thắng • ${statistics.losses} thua • ${statistics.draws} hòa",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Điểm cao ${statistics.highestScore} • Trung bình ${statistics.averageScore}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ProtocolGameMode.displayName(): String = when (this) {
    ProtocolGameMode.ORDER -> "Cổ điển"
    ProtocolGameMode.RANDOM_TARGET -> "Ngẫu nhiên"
    ProtocolGameMode.TIME_BONUS -> "Cộng thời gian"
    ProtocolGameMode.SPEED_UP -> "Tăng tốc"
    ProtocolGameMode.SURVIVAL -> "Sinh tồn"
    ProtocolGameMode.COMBO -> "Combo"
    ProtocolGameMode.TIME_ATTACK -> "Đua 60 giây"
    ProtocolGameMode.TEAM_2V2 -> "Đồng đội 2v2"
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

private fun CosmeticSnapshot.displayLabel(): String {
    if (unlocked) return name
    val requirement = when (id) {
        "frame_bronze" -> "Cấp 3"
        "frame_gold" -> "Cấp 10"
        "frame_perfect" -> "Cấp 15 + thắng không bấm sai"
        "frame_persistent" -> "Điểm danh 100 lần"
        "title_champion" -> "Thắng 10 trận"
        "title_speed" -> "Thắng và chọn đủ 50 số trong 30 giây"
        "title_diligent" -> "Điểm danh 30 ngày liên tiếp"
        DAILY_CHECK_IN_AVATAR_ID -> "Điểm danh 50 lần"
        else -> null
    }
    return if (requirement == null) "🔒 $name" else "🔒 $name · $requirement"
}

@Composable
private fun MatchHistoryCard(match: MatchHistorySnapshot, onClick: (() -> Unit)?) {
    val (result, color) = when (match.outcome) {
        MatchHistoryOutcome.WIN -> "Thắng" to MaterialTheme.colorScheme.primary
        MatchHistoryOutcome.LOSS -> "Thua" to MaterialTheme.colorScheme.error
        MatchHistoryOutcome.DRAW -> "Hòa" to MaterialTheme.colorScheme.tertiary
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("match_history:${match.matchId}").then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
        )
    ) {
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
                if (match.matchType == MatchType.RANKED) {
                    val eloText = if (match.eloChange >= 0) "+${match.eloChange}" else match.eloChange.toString()
                    Text(
                        "Xếp hạng • Elo $eloText",
                        color = if (match.eloChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Trận thường", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
