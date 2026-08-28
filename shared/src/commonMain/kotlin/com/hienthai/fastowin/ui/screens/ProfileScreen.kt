package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_FRAME_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GEMS
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GOLD
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_TITLE_TARGET
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
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
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.WalletDeltaAmounts
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_leaderboard_trophy
import com.hienthai.fastowin.resources.arcade_notifications_inbox
import com.hienthai.fastowin.resources.arcade_room_portal
import com.hienthai.fastowin.resources.arcade_shop_chest
import com.hienthai.fastowin.ui.theme.ArcadeGold
import com.hienthai.fastowin.ui.theme.ArcadeGem
import com.hienthai.fastowin.ui.theme.ArcadeOpponent
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.ui.theme.ArcadeSuccess
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

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
        }
    }
    LaunchedEffect(state.profileNotice) {
        if (state.profileNotice != null) isEditing = false
    }
    LaunchedEffect(isPlayerCodeCopied) {
        if (isPlayerCodeCopied) {
            delay(1_200)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        if (showBackButton) {
            FastToWinHeader(
                title = if (isExternalProfile) "Người chơi" else "Hồ sơ",
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
        val progression = profile.progression

        ProfileIdentityPanel(
            serverUrl = serverUrl,
            profile = profile,
            canEdit = canEdit,
            isSaving = state.isProfileSaving,
            isExternalProfile = isExternalProfile,
            sessionStartedAtMillis = sessionStartedAtMillis,
            onEdit = { isEditing = !isEditing },
            onCopyCode = {
                clipboardManager.setText(AnnotatedString(profile.playerCode))
                isPlayerCodeCopied = true
            }
        )

        if (isExternalProfile && onInviteToClan != null && state.profile?.clanId != null && profile.clanId == null) {
            ArcadeActionButton(
                label = "MỜI VÀO BANG",
                onClick = { onInviteToClan.invoke(profile.playerCode) },
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isEditing) {
            ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadeGold) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Chỉnh sửa hồ sơ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= MAX_PROFILE_DISPLAY_NAME_LENGTH) displayName = it },
                        label = { Text("Biệt danh") },
                        supportingText = { Text("${displayName.length}/$MAX_PROFILE_DISPLAY_NAME_LENGTH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_display_name")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ảnh đại diện", fontWeight = FontWeight.Medium)
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcadeActionButton(
                            label = if (state.isProfileSaving) "Đang lưu..." else "Lưu",
                            onClick = { onSave(displayName, profile.avatarId) },
                            enabled = displayName.isNotBlank() && !state.isProfileSaving,
                            modifier = Modifier.fillMaxWidth(),
                            style = ArcadeActionStyle.GOLD
                        )
                        ArcadeActionButton(
                            label = "Hủy",
                            onClick = {
                                displayName = profile.displayName
                                isEditing = false
                            },
                            enabled = !state.isProfileSaving,
                            modifier = Modifier.fillMaxWidth(),
                            style = ArcadeActionStyle.OUTLINE
                        )
                    }
                }
            }
        }

        if (!isExternalProfile) state.profileNotice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (isExternalProfile) {
            ArcadeStatGrid(
                stats = listOf(
                    "Trận" to profile.statistics.totalMatches.toString(),
                    "Thắng" to profile.statistics.wins.toString(),
                    "Elo" to profile.statistics.eloRating.toString()
                )
            )
            Text("Thành tích nổi bật", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            ArcadeStatGrid(
                stats = listOf(
                    "Chuỗi tốt nhất" to profile.statistics.bestWinStreak.toString(),
                    "Phản xạ TB" to "${profile.statistics.averageReactionMillis} ms"
                )
            )
        }

        ProfileSectionTitle("Hoạt động")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                AccountActionRow(
                    icon = Icons.Default.Insights,
                    title = "Thống kê & thành tích",
                    subtitle = "Hiệu suất và mốc đã mở",
                    onClick = { onOpenSection(ProfileSection.STATISTICS) },
                    modifier = Modifier.testTag("profile_section_statistics")
                )
                if (!isExternalProfile && canEdit) {
                    AccountActionRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Lịch sử tài sản",
                        subtitle = "Vàng, Gem và XP",
                        onClick = { onOpenSection(ProfileSection.WALLET) },
                        modifier = Modifier.testTag("profile_section_wallet")
                    )
                }
                if (!isExternalProfile) {
                    AccountActionRow(
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        title = "Nhiệm vụ",
                        subtitle = "Theo dõi và nhận thưởng",
                        onClick = { onOpenSection(ProfileSection.MISSIONS) },
                        modifier = Modifier.testTag("profile_section_missions")
                    )
                    AccountActionRow(
                        icon = Icons.Default.Collections,
                        title = "Bộ sưu tập",
                        subtitle = "Khung và danh hiệu",
                        onClick = { onOpenSection(ProfileSection.COLLECTION) },
                        modifier = Modifier.testTag("profile_section_collection")
                    )
                }
                AccountActionRow(
                    icon = Icons.Default.History,
                    title = "Trận gần đây",
                    subtitle = "Lịch sử thi đấu",
                    onClick = { onOpenSection(ProfileSection.RECENT_MATCHES) },
                    modifier = Modifier.testTag("profile_section_recent_matches")
                )
        }

        if (canEdit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileSectionTitle("Cài đặt & tài khoản")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        AccountActionRow(
                            icon = Icons.Default.Settings,
                            title = "Cài đặt ứng dụng",
                            subtitle = "",
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("profile_settings")
                        )
                        AccountActionRow(
                            icon = Icons.Default.Devices,
                            title = "Thiết bị đăng nhập",
                            subtitle = "",
                            onClick = {
                                onClearAccountFeedback()
                                showAccountSessions = true
                                onLoadSessions()
                            },
                            modifier = Modifier.testTag("profile_sessions")
                        )
                        AccountActionRow(
                            icon = Icons.Default.Lock,
                            title = "Bảo mật tài khoản",
                            subtitle = "",
                            onClick = {
                                onClearAccountFeedback()
                                showAccountSecurity = true
                            },
                            modifier = Modifier.testTag("profile_security")
                        )
                        AccountActionRow(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            title = "Đăng xuất",
                            subtitle = "",
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
                    "Đã sao chép",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ProfileIdentityPanel(
    serverUrl: String,
    profile: PlayerProfileSnapshot,
    canEdit: Boolean,
    isSaving: Boolean,
    isExternalProfile: Boolean,
    sessionStartedAtMillis: Long?,
    onEdit: () -> Unit,
    onCopyCode: () -> Unit
) {
    val progression = profile.progression
    val equippedFrame = progression.cosmetics.firstOrNull {
        it.type == CosmeticType.FRAME && it.equipped
    }?.id ?: "frame_default"
    val equippedTitle = progression.cosmetics.firstOrNull {
        it.type == CosmeticType.TITLE && it.equipped
    }?.name ?: "Tân binh"

    val panelShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_identity_card")
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        ArcadePalette.Gold800.copy(alpha = 0.72f),
                        ArcadePalette.Navy900.copy(alpha = 0.98f)
                    )
                )
            )
            .border(1.dp, ArcadeGold.copy(alpha = 0.52f), panelShape)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 330.dp
            Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = if (canEdit) 32.dp else 0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlayerAvatar(
                            displayName = profile.displayName,
                            avatarId = profile.avatarId,
                            frameId = equippedFrame,
                            size = 76.dp,
                            imageUrl = "$serverUrl/api/avatar/${profile.userId}"
                        )
                        ProfileIdentityDetails(
                            profile = profile,
                            equippedTitle = equippedTitle,
                            sessionStartedAtMillis = sessionStartedAtMillis.takeIf { !isExternalProfile },
                            onCopyCode = onCopyCode,
                            centered = true
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = if (canEdit) 42.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlayerAvatar(
                            displayName = profile.displayName,
                            avatarId = profile.avatarId,
                            frameId = equippedFrame,
                            size = 76.dp,
                            imageUrl = "$serverUrl/api/avatar/${profile.userId}"
                        )
                        ProfileIdentityDetails(
                            profile = profile,
                            equippedTitle = equippedTitle,
                            sessionStartedAtMillis = sessionStartedAtMillis.takeIf { !isExternalProfile },
                            onCopyCode = onCopyCode,
                            centered = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (canEdit) {
                    IconButton(
                        onClick = onEdit,
                        enabled = !isSaving,
                        modifier = Modifier.align(Alignment.TopEnd).size(44.dp).testTag("profile_edit")
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = ArcadePalette.Navy800,
                            border = BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.65f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Chỉnh sửa hồ sơ",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
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
private fun ProfileIdentityDetails(
    profile: PlayerProfileSnapshot,
    equippedTitle: String,
    sessionStartedAtMillis: Long?,
    onCopyCode: () -> Unit,
    centered: Boolean,
    modifier: Modifier = Modifier
) {
    val progression = profile.progression
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            profile.displayName,
            modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "Danh hiệu: $equippedTitle",
            color = ArcadePalette.Gold400,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Mã: ${profile.playerCode}",
                modifier = if (centered) Modifier else Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onCopyCode, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Sao chép mã người chơi",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Cấp ${progression.level}",
                modifier = Modifier.testTag("profile_level_label"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = ArcadePalette.Gold400
            )
            Text(
                "${progression.currentLevelExperience}/${progression.nextLevelExperience} XP",
                modifier = Modifier.testTag("profile_xp_count"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
        LinearProgressIndicator(
            progress = {
                progression.currentLevelExperience.toFloat() /
                    progression.nextLevelExperience.coerceAtLeast(1)
            },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = ArcadeGold,
            trackColor = ArcadePalette.Navy950
        )
        sessionStartedAtMillis?.let { CurrentSessionDuration(it) }
    }
}

@Composable
private fun ArcadeStatGrid(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    if (stats.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth < 280.dp -> 2
            else -> minOf(3, stats.size)
        }.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.chunked(columns).forEach { rowStats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowStats.forEach { (label, value) ->
                        StatCard(label, value, Modifier.weight(1f))
                    }
                    repeat(columns - rowStats.size) { Spacer(Modifier.weight(1f)) }
                }
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
                isRefreshing = isLoading && state.equippingCosmeticId == null,
                onRefresh = {
                    if (state.equippingCosmeticId == null) onRefresh()
                },
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
                    ProfileSectionHero(section)
                    when (section) {
                        ProfileSection.STATISTICS -> StatisticsAchievementsSectionContent(profile)
                        ProfileSection.WALLET -> {
                            ArcadeStatGrid(
                                stats = listOf(
                                    "Vàng" to profile.progression.gold.toString(),
                                    "Gem" to profile.progression.gems.toString(),
                                    "XP" to profile.progression.currentLevelExperience.toString()
                                )
                            )
                            WalletHistorySectionContent(state.walletTransactions)
                        }
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
private fun ProfileSectionHero(section: ProfileSection) {
    val (title, subtitle, illustration) = when (section) {
        ProfileSection.STATISTICS -> Triple(
            "Phong độ của bạn",
            "Theo dõi tốc độ, độ chính xác và tiến bộ qua từng chế độ.",
            Res.drawable.arcade_leaderboard_trophy
        )
        ProfileSection.WALLET -> Triple(
            "Dòng tài sản",
            "Mọi lần nhận và sử dụng Vàng, Gem, XP đều được lưu tại đây.",
            Res.drawable.arcade_shop_chest
        )
        ProfileSection.MISSIONS -> Triple(
            "Nhiệm vụ hôm nay",
            "Hoàn thành thử thách, nhận thưởng và nâng cấp tài khoản.",
            Res.drawable.arcade_notifications_inbox
        )
        ProfileSection.COLLECTION -> Triple(
            "Dấu ấn của bạn",
            "Trang bị khung và danh hiệu đã mở khóa.",
            Res.drawable.arcade_shop_chest
        )
        ProfileSection.RECENT_MATCHES -> Triple(
            "Lịch sử thi đấu",
            "Chạm một trận để xem điểm số và từng lượt bấm.",
            Res.drawable.arcade_room_portal
        )
    }
    val accent = when (section) {
        ProfileSection.WALLET, ProfileSection.COLLECTION -> ArcadeGold
        ProfileSection.MISSIONS -> ArcadeGem
        ProfileSection.RECENT_MATCHES -> ArcadeOpponent
        ProfileSection.STATISTICS -> MaterialTheme.colorScheme.primary
    }
    ArcadeFeatureHero(
        illustration = illustration,
        title = title,
        subtitle = subtitle,
        accent = accent
    )
}

@Composable
private fun WalletHistorySectionContent(transactions: List<WalletTransactionSnapshot>) {
    var selectedFilter by remember { mutableStateOf(0) }
    val visibleTransactions = remember(transactions, selectedFilter) {
        transactions.filter { transaction ->
            when (selectedFilter) {
                1 -> transaction.goldDelta > 0 || transaction.gemsDelta > 0 || transaction.xpDelta > 0
                2 -> transaction.goldDelta < 0 || transaction.gemsDelta < 0 || transaction.xpDelta < 0
                else -> true
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("wallet_history_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArcadeSegmentedControl(
            labels = listOf("Tất cả", "Nhận", "Đã dùng"),
            selectedIndex = selectedFilter,
            onSelected = { selectedFilter = it },
            modifier = Modifier.testTag("wallet_history_tabs")
        )
        if (visibleTransactions.isEmpty()) {
            ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadeGold) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                    Text("Chưa có giao dịch", fontWeight = FontWeight.Bold)
                    Text(
                        if (transactions.isEmpty()) {
                            "Phần thưởng và vật phẩm đã mua sẽ xuất hiện tại đây."
                        } else {
                            "Chưa có giao dịch trong mục này."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            visibleTransactions.forEach { transaction ->
                val isSpending = transaction.goldDelta < 0 || transaction.gemsDelta < 0 || transaction.xpDelta < 0
                val accent = if (isSpending) ArcadeOpponent else ArcadeSuccess
                ArcadePanel(
                    modifier = Modifier.fillMaxWidth().testTag("wallet_transaction:${transaction.id}"),
                    accent = accent
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Icon(
                                walletSourceIcon(transaction.sourceType),
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                tint = accent
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

private fun walletSourceIcon(sourceType: String): ImageVector = when (sourceType) {
    "DAILY_CHECK_IN" -> Icons.Default.DateRange
    "MISSION", "CLAN_QUEST" -> Icons.AutoMirrored.Filled.Assignment
    "MATCH", "TOURNAMENT_PRIZE", "SEASON_REWARD" -> Icons.Default.EmojiEvents
    "COSMETIC_PURCHASE", "STORE_PURCHASE" -> Icons.Default.ShoppingBag
    else -> Icons.Default.AccountBalanceWallet
}

private fun walletSourceLabel(sourceType: String): String = when (sourceType) {
    "DAILY_CHECK_IN" -> "Điểm danh hằng ngày"
    "MISSION" -> "Thưởng nhiệm vụ"
    "MATCH" -> "Thưởng trận đấu"
    "CLAN_QUEST" -> "Nhiệm vụ bang hội"
    "COSMETIC_PURCHASE" -> "Mua vật phẩm"
    "TOURNAMENT_ENTRY" -> "Phí tham gia giải đấu"
    "TOURNAMENT_PRIZE" -> "Giải thưởng vô địch"
    "SEASON_REWARD" -> "Thưởng mùa giải"
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
    val totalSelections = stats.correctSelections + stats.wrongSelections
    val accuracy = if (totalSelections == 0) 0 else stats.correctSelections * 100 / totalSelections
    val winRate = if (stats.totalMatches == 0) 0 else stats.wins * 100 / stats.totalMatches
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile_statistics_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ArcadeStatGrid(
            stats = listOf(
                "Tỷ lệ thắng" to "$winRate%",
                "Phản xạ TB" to "${stats.averageReactionMillis} ms",
                "Chính xác" to "$accuracy%"
            )
        )

        if (profile.recentMatches.isNotEmpty()) {
            val scores = profile.recentMatches.take(10).asReversed().map { it.playerScore }
            val eloTrend = remember(stats.eloRating, profile.recentMatches) {
                buildEloTrend(stats.eloRating, profile.recentMatches.take(10))
            }
            TrendChart("Phong độ 10 trận gần nhất", scores) { "$it điểm" }
            TrendChart("Biến động Elo", eloTrend) { "Elo $it" }
        }

        Text("Tổng quan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        ArcadeStatGrid(
            stats = listOf(
                "Trận" to stats.totalMatches.toString(),
                "Thắng" to stats.wins.toString(),
                "Thua" to stats.losses.toString(),
                "Hòa" to stats.draws.toString(),
                "Điểm cao" to stats.highestScore.toString(),
                "Chuỗi tốt nhất" to stats.bestWinStreak.toString(),
                "Đúng / Sai" to "${stats.correctSelections} / ${stats.wrongSelections}"
            )
        )

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

        Text("Thành tích", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        if (profile.achievements.isEmpty()) {
            ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadeGold) {
                Text(
                    "Chưa mở khóa thành tích nào.",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 640.dp) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    profile.achievements.chunked(columns).forEach { achievements ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            achievements.forEach { achievement ->
                                ArcadePanel(modifier = Modifier.weight(1f), accent = ArcadeGold) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ArcadeGold.copy(alpha = 0.18f),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.EmojiEvents,
                                                    contentDescription = null,
                                                    tint = ArcadeGold,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(achievement.title, fontWeight = FontWeight.Black)
                                            Text(
                                                achievement.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(columns - achievements.size) { Spacer(Modifier.weight(1f)) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sectionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "${missions.count { it.completed }}/${missions.size} hoàn thành",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        missions.forEach { mission ->
            MissionArcadeCard(
                mission = mission,
                canEdit = canEdit,
                claimingMissionCode = state.claimingMissionCode,
                onClaim = { onClaimMissionReward(mission.code) }
            )
        }
    }
}

@Composable
private fun MissionArcadeCard(
    mission: MissionSnapshot,
    canEdit: Boolean,
    claimingMissionCode: String?,
    onClaim: () -> Unit
) {
    val accent = when (mission.difficulty) {
        MissionDifficulty.EASY -> ArcadeSuccess
        MissionDifficulty.NORMAL -> MaterialTheme.colorScheme.primary
        MissionDifficulty.HARD -> ArcadeGold
        MissionDifficulty.ELITE -> ArcadeOpponent
    }
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = accent) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 440.dp
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MissionArcadeDetails(mission)
                    MissionClaimControl(
                        mission = mission,
                        canEdit = canEdit,
                        claimingMissionCode = claimingMissionCode,
                        onClaim = onClaim,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MissionArcadeDetails(mission, Modifier.weight(1f))
                    MissionClaimControl(
                        mission = mission,
                        canEdit = canEdit,
                        claimingMissionCode = claimingMissionCode,
                        onClaim = onClaim
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionArcadeDetails(mission: MissionSnapshot, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(mission.title, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            MissionDifficultyBadge(mission.difficulty)
        }
        LinearProgressIndicator(
            progress = { mission.progress.toFloat() / mission.target.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (mission.completed) ArcadeSuccess else MaterialTheme.colorScheme.primary
        )
        Text(
            "${mission.progress}/${mission.target}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RewardAmounts(gold = mission.rewardGold, xp = mission.rewardXp, gems = mission.rewardGems)
    }
}

@Composable
private fun MissionClaimControl(
    mission: MissionSnapshot,
    canEdit: Boolean,
    claimingMissionCode: String?,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        mission.rewardClaimed -> ArcadeActionButton(
            label = "Đã nhận",
            onClick = {},
            enabled = false,
            style = ArcadeActionStyle.OUTLINE,
            modifier = modifier.heightIn(min = 48.dp).testTag("claim_mission:${mission.code}")
        )
        mission.completed && canEdit -> ArcadeActionButton(
            label = if (claimingMissionCode == mission.code) "ĐANG NHẬN" else "NHẬN THƯỞNG",
            onClick = onClaim,
            enabled = claimingMissionCode == null,
            style = ArcadeActionStyle.GOLD,
            modifier = modifier.heightIn(min = 48.dp).testTag("claim_mission:${mission.code}")
        )
        mission.completed -> Text("Hoàn thành", modifier = modifier, style = MaterialTheme.typography.bodySmall)
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
    val equippingCosmeticId = state.equippingCosmeticId
    Text("Khung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    val frames = cosmetics.filter { it.type == CosmeticType.FRAME }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("collection_frames")) {
        val columns = when {
            maxWidth >= 760.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            frames.chunked(columns).forEach { frameRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    frameRow.forEach { cosmetic ->
                        val isEquipping = equippingCosmeticId == cosmetic.id
                        val canEquip = canEdit && cosmetic.unlocked && !cosmetic.equipped &&
                            !isLoading && equippingCosmeticId == null
                        CollectionFrameCard(
                            cosmetic = cosmetic,
                            profile = profile,
                            isEquipping = isEquipping,
                            canEquip = canEquip,
                            onClick = { onEquipCosmetics(cosmetic.id, equippedTitle) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - frameRow.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    Text("Danh hiệu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    val titles = cosmetics.filter { it.type == CosmeticType.TITLE }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("collection_titles")) {
        val columns = if (maxWidth >= 680.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            titles.chunked(columns).forEach { titleRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    titleRow.forEach { cosmetic ->
                        val isEquipping = equippingCosmeticId == cosmetic.id
                        val canEquip = canEdit && cosmetic.unlocked && !cosmetic.equipped &&
                            !isLoading && equippingCosmeticId == null
                        CollectionTitleCard(
                            cosmetic = cosmetic,
                            isEquipping = isEquipping,
                            canEquip = canEquip,
                            onClick = { onEquipCosmetics(equippedFrame, cosmetic.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - titleRow.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CollectionTitleCard(
    cosmetic: CosmeticSnapshot,
    isEquipping: Boolean,
    canEquip: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (cosmetic.equipped) ArcadeGold else ArcadePalette.Violet600
    Surface(
        onClick = onClick,
        enabled = canEquip,
        modifier = modifier.heightIn(min = 92.dp).testTag("collection_title:${cosmetic.id}"),
        shape = RoundedCornerShape(18.dp),
        color = if (cosmetic.equipped) ArcadeGold.copy(alpha = 0.13f)
            else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (cosmetic.equipped) 2.dp else 1.dp, accent.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isEquipping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp).testTag("collection_equipping:${cosmetic.id}"),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.MilitaryTech,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = accent
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(cosmetic.name, fontWeight = FontWeight.Black)
                Text(
                    when {
                        isEquipping -> "Đang trang bị…"
                        cosmetic.equipped -> "ĐANG TRANG BỊ"
                        cosmetic.unlocked && canEquip -> "Chạm để trang bị"
                        cosmetic.unlocked -> "Đã mở khóa"
                        else -> cosmetic.unlockRequirement()?.let { "Mở khóa: $it" }
                            ?: "Chưa mở khóa"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cosmetic.equipped) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CollectionFrameCard(
    cosmetic: CosmeticSnapshot,
    profile: PlayerProfileSnapshot,
    isEquipping: Boolean,
    canEquip: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (cosmetic.equipped) ArcadeGold else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 198.dp)
            .testTag("collection_frame:${cosmetic.id}"),
        enabled = canEquip,
        shape = RoundedCornerShape(20.dp),
        color = if (cosmetic.equipped) {
            ArcadeGold.copy(alpha = 0.13f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(if (cosmetic.equipped) 2.dp else 1.dp, accent.copy(alpha = 0.62f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                PlayerAvatar(
                    displayName = profile.displayName,
                    avatarId = profile.avatarId,
                    frameId = cosmetic.id,
                    size = 74.dp
                )
                if (isEquipping) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(38.dp).testTag("collection_equipping:${cosmetic.id}"),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
            Text(
                cosmetic.name,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.14f)
            ) {
                Text(
                    text = when {
                        isEquipping -> "Đang trang bị…"
                        cosmetic.equipped -> "ĐANG TRANG BỊ"
                        cosmetic.unlocked && canEquip -> "TRANG BỊ"
                        cosmetic.unlocked -> "Đã mở khóa"
                        else -> cosmetic.unlockRequirement()?.let { "Mở khóa: $it" }
                            ?: "Chưa mở khóa"
                    },
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cosmetic.unlocked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
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
    ArcadeSegmentedControl(
        labels = listOf("Tất cả", "Thắng", "Thua"),
        selectedIndex = when (historyFilter) {
            null -> 0
            MatchHistoryOutcome.WIN -> 1
            MatchHistoryOutcome.LOSS -> 2
            MatchHistoryOutcome.DRAW -> 0
        },
        onSelected = { index ->
            onFilterChange(
                when (index) {
                    1 -> MatchHistoryOutcome.WIN
                    2 -> MatchHistoryOutcome.LOSS
                    else -> null
                }
            )
        },
        modifier = Modifier.fillMaxWidth().testTag("recent_match_tabs"),
        itemTestTag = { index ->
            when (index) {
                1 -> "history_filter_win"
                2 -> "history_filter_loss"
                else -> "history_filter_all"
            }
        }
    )
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.34f)
            else ArcadePalette.OutlineDark.copy(alpha = 0.42f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDestructive) contentColor.copy(alpha = 0.78f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    ArcadeDialog(
        title = "BẢO MẬT TÀI KHOẢN",
        subtitle = "Đổi mật khẩu hoặc quản lý việc xóa tài khoản.",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = ArcadeGold)
                    Text("Đổi mật khẩu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
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
                ArcadeActionButton(
                    label = if (isLoading) "ĐANG CẬP NHẬT..." else "ĐỔI MẬT KHẨU",
                    onClick = { onChangePassword(currentPassword, newPassword) },
                    enabled = !isLoading && currentPassword.isNotBlank() && passwordError == null &&
                        confirmationError == null && confirmPassword.isNotEmpty() && !passwordUnchanged,
                    modifier = Modifier.fillMaxWidth(),
                    style = ArcadeActionStyle.GOLD
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadeOpponent) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Vùng nguy hiểm", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                        Text(
                            "Xóa tài khoản sẽ xóa vĩnh viễn hồ sơ, Elo, lịch sử và thành tích.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SecurePasswordField(deletePassword, "Nhập mật khẩu để xác nhận") { deletePassword = it }
                if (!confirmDelete) {
                    ArcadeActionButton(
                        label = "TÔI MUỐN XÓA TÀI KHOẢN",
                        onClick = { confirmDelete = true },
                        enabled = !isLoading && deletePassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        style = ArcadeActionStyle.OUTLINE
                    )
                } else {
                    ArcadeActionButton(
                        label = if (isLoading) "ĐANG XÓA..." else "XÓA TÀI KHOẢN",
                        onClick = { onDeleteAccount(deletePassword) },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        style = ArcadeActionStyle.DANGER
                    )
                }
        }
        ArcadeActionButton(
            label = "Đóng",
            onClick = onDismiss,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.OUTLINE
        )
    }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun AccountSessionsDialog(
    sessions: List<AccountSessionSnapshot>,
    isLoading: Boolean,
    error: String?,
    notice: String?,
    onRevokeSession: (String) -> Unit,
    onRevokeAllSessions: () -> Unit,
    onDismiss: () -> Unit
) {
    var sessionPendingRevoke by remember { mutableStateOf<AccountSessionSnapshot?>(null) }
    var confirmRevokeAll by remember { mutableStateOf(false) }

    sessionPendingRevoke?.let { session ->
        ArcadeDialog(
            title = if (session.isCurrent) "ĐĂNG XUẤT THIẾT BỊ NÀY?" else "ĐĂNG XUẤT THIẾT BỊ?",
            subtitle = if (session.isCurrent) {
                "Bạn sẽ quay về màn đăng nhập trên thiết bị hiện tại."
            } else {
                "Phiên trên ${sessionDeviceLabel(session.devicePlatform)} sẽ bị thu hồi ngay."
            },
            onDismissRequest = { sessionPendingRevoke = null }
        ) {
            ArcadeActionButton(
                label = "Đăng xuất",
                onClick = {
                    sessionPendingRevoke = null
                    onRevokeSession(session.sessionId)
                },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.DANGER
            )
            ArcadeActionButton(
                label = "Hủy",
                onClick = { sessionPendingRevoke = null },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }
    if (confirmRevokeAll) {
        ArcadeDialog(
            title = "ĐĂNG XUẤT TẤT CẢ THIẾT BỊ?",
            subtitle = "Tất cả phiên, bao gồm thiết bị này, sẽ bị thu hồi và bạn cần đăng nhập lại.",
            onDismissRequest = { confirmRevokeAll = false }
        ) {
            ArcadeActionButton(
                label = "Đăng xuất tất cả",
                onClick = {
                    confirmRevokeAll = false
                    onRevokeAllSessions()
                },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.DANGER
            )
            ArcadeActionButton(
                label = "Hủy",
                onClick = { confirmRevokeAll = false },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }

    ArcadeDialog(
        title = "THIẾT BỊ ĐĂNG NHẬP",
        subtitle = "Kiểm tra và thu hồi những phiên bạn không còn sử dụng.",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
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
                        ArcadePanel(
                            modifier = Modifier.fillMaxWidth(),
                            accent = if (session.isCurrent) ArcadeSuccess else MaterialTheme.colorScheme.primary
                        ) {
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
        }
        ArcadeActionButton(
            label = "Đăng xuất tất cả thiết bị",
            onClick = { confirmRevokeAll = true },
            enabled = !isLoading && sessions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.DANGER
        )
        ArcadeActionButton(
            label = "ĐÓNG",
            onClick = onDismiss,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().testTag("account_sessions_close"),
            style = ArcadeActionStyle.PRIMARY
        )
    }
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
    val icon: ImageVector,
    val title: String,
    val reward: String,
    val progress: Int,
    val target: Int
)

@Composable
private fun ProfileDailyCheckInStrip(checkIn: DailyCheckInSnapshot) {
    val rewardCardHeight = if (LocalDensity.current.fontScale >= 1.3f) 78.dp else 58.dp
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile_daily_check_in_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ProfileSectionTitle("Điểm danh")
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ArcadeGold.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, ArcadeGold.copy(alpha = 0.34f))
            ) {
                Text(
                    "Chuỗi ${checkIn.currentStreak} ngày",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    color = ArcadePalette.Gold400,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            DAILY_CHECK_IN_REWARDS_GOLD.forEachIndexed { index, rewardGold ->
                val day = index + 1
                val rewardGems = DAILY_CHECK_IN_REWARDS_GEMS[index]
                val completed = day < checkIn.cycleDay ||
                    (day == checkIn.cycleDay && checkIn.claimedToday)
                val current = day == checkIn.cycleDay
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(rewardCardHeight)
                        .testTag("profile_check_in_day_$day"),
                    shape = RoundedCornerShape(11.dp),
                    color = when {
                        current -> ArcadePalette.Gold800.copy(alpha = 0.72f)
                        completed -> ArcadePalette.Navy900.copy(alpha = 0.66f)
                        else -> ArcadePalette.Navy800
                    },
                    border = BorderStroke(
                        1.dp,
                        if (current) ArcadePalette.Gold400
                        else ArcadePalette.OutlineDark.copy(alpha = 0.72f)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            day.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = if (completed) 0.58f else 1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (rewardGems > 0) Icons.Default.Payments else Icons.Default.MonetizationOn,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = if (rewardGems > 0) ArcadeGem else ArcadeGold
                            )
                            Text(
                                (if (rewardGems > 0) rewardGems else rewardGold).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = if (completed) 0.58f else 1f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCheckInCalendar(checkIn: DailyCheckInSnapshot) {
    val today = checkIn.todayDate?.toProfileCalendarDate() ?: return
    val currentMonth = ProfileCalendarMonth(today.year, today.month)
    val earliestMonth = currentMonth.shift(-11)
    var visibleMonth by remember(checkIn.todayDate) { mutableStateOf(currentMonth) }
    val checkedDates = remember(checkIn.historyDates) { checkIn.historyDates.toSet() }
    val cells = profileCalendarCells(visibleMonth)

    ArcadePanel(
        modifier = Modifier.fillMaxWidth().testTag("daily_check_in_calendar"),
        accent = ArcadeGold
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lịch sử điểm danh", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Surface(shape = RoundedCornerShape(999.dp), color = ArcadeGold.copy(alpha = 0.16f)) {
                    Text(
                        "Chuỗi ${checkIn.currentStreak} ngày",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = ArcadeGold,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                "Lịch điểm danh · Theo dõi tối đa 12 tháng gần nhất",
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
            icon = Icons.Default.EmojiEvents,
            title = "7 ngày liên tiếp",
            reward = "Thành tích Khởi đầu đều đặn",
            progress = bestStreak,
            target = DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET
        ),
        DailyCheckInMilestone(
            icon = Icons.Default.MilitaryTech,
            title = "30 ngày liên tiếp",
            reward = "Danh hiệu Chuyên cần",
            progress = bestStreak,
            target = DAILY_CHECK_IN_TITLE_TARGET
        ),
        DailyCheckInMilestone(
            icon = Icons.Default.Shield,
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
            ArcadePanel(
                modifier = Modifier.fillMaxWidth(),
                accent = if (unlocked) ArcadeGold else MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (unlocked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                milestone.icon,
                                contentDescription = null,
                                tint = if (unlocked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    Surface(
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.42f)),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ArcadeProfileChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) ArcadePalette.Blue700 else ArcadePalette.Navy800,
        contentColor = if (enabled) ArcadePalette.White else ArcadePalette.OutlineDark,
        border = BorderStroke(
            1.dp,
            when {
                selected -> ArcadePalette.Gold500
                enabled -> ArcadePalette.OutlineDark
                else -> ArcadePalette.Navy700
            }
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun ModeStatisticsCard(statistics: GameModeStatisticsSnapshot) {
    val winRate = if (statistics.totalMatches == 0) 0 else statistics.wins * 100 / statistics.totalMatches
    ArcadePanel(
        modifier = Modifier.fillMaxWidth().testTag("mode_statistics:${statistics.gameMode.name}"),
        accent = MaterialTheme.colorScheme.primary
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
            LinearProgressIndicator(
                progress = { winRate / 100f },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = if (winRate >= 50) ArcadeSuccess else MaterialTheme.colorScheme.primary
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
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = lineColor) {
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

    ArcadeDialog(
        onDismissRequest = onDismiss,
        title = if (isLoading) "Đang tải trận đấu" else "Chi tiết trận",
        subtitle = detail?.let {
            "${it.summary.gameMode.displayName()} · ${if (it.summary.matchType == MatchType.RANKED) "Xếp hạng" else "Đấu thường"}"
        }
    ) {
        if (isLoading || detail == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
                val mine = events.filter { it.isCurrentPlayer }
                val correct = mine.count { it.accepted }
                val wrong = mine.size - correct
                val accuracy = if (mine.isEmpty()) 0 else correct * 100 / mine.size
                val acceptedTimes = mine.filter { it.accepted }
                    .sortedBy { it.occurredAtEpochMillis }
                    .map { it.occurredAtEpochMillis }
                val reactionSamples = acceptedTimes.zipWithNext { previous, next ->
                    (next - previous).coerceAtLeast(0L)
                }
                val averageReaction = if (reactionSamples.isEmpty()) {
                    if (correct == 0) 0L else detail.durationMillis / correct
                } else {
                    reactionSamples.average().toLong()
                }
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MatchDetailScoreboard(detail.summary)
                    ArcadeStatGrid(
                        stats = listOf(
                            "Phản xạ" to if (averageReaction > 0) "${averageReaction} ms" else "--",
                            "Chính xác" to "$accuracy%",
                            "Elo" to if (detail.summary.matchType == MatchType.RANKED) {
                                if (detail.summary.eloChange >= 0) "+${detail.summary.eloChange}" else detail.summary.eloChange.toString()
                            } else {
                                "Không đổi"
                            }
                        )
                    )
                    Text(
                        "${formatMatchDuration(detail.durationMillis)} · Đúng $correct · Sai $wrong",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (reactionSamples.isNotEmpty()) {
                        ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Violet400) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("NHANH NHẤT", style = MaterialTheme.typography.labelSmall, color = ArcadeSuccess)
                                    Text("${reactionSamples.minOrNull()} ms", fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CHẬM NHẤT", style = MaterialTheme.typography.labelSmall, color = ArcadeOpponent)
                                    Text("${reactionSamples.maxOrNull()} ms", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    if (events.isEmpty()) {
                        Text("Trận này chưa có dữ liệu lượt bấm để phát lại.")
                    } else {
                        val event = events[replayIndex.coerceIn(events.indices)]
                        ArcadePanel(
                            modifier = Modifier.fillMaxWidth(),
                            accent = if (event.accepted) ArcadeSuccess else ArcadeOpponent
                        ) {
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
                        MatchReplayControls(
                            isPlaying = isPlaying,
                            canGoPrevious = replayIndex > 0,
                            canGoNext = replayIndex < events.lastIndex,
                            onPrevious = { replayIndex = (replayIndex - 1).coerceAtLeast(0) },
                            onPlayPause = {
                                if (replayIndex >= events.lastIndex) replayIndex = 0
                                isPlaying = !isPlaying
                            },
                            onNext = { replayIndex = (replayIndex + 1).coerceAtMost(events.lastIndex) }
                        )
                    }
                }
            }
        ArcadeActionButton(
            label = "ĐÓNG",
            onClick = onDismiss,
            style = ArcadeActionStyle.GOLD,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MatchReplayControls(
    isPlaying: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val button: @Composable (String, () -> Unit, Boolean, ArcadeActionStyle, Modifier) -> Unit =
        { label, onClick, enabled, style, modifier ->
            ArcadeActionButton(
                label = label,
                onClick = onClick,
                enabled = enabled,
                style = style,
                modifier = modifier
            )
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        button(
            if (isPlaying) "DỪNG" else "PHÁT LẠI",
            onPlayPause,
            true,
            ArcadeActionStyle.PRIMARY,
            Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            button("TRƯỚC", onPrevious, canGoPrevious, ArcadeActionStyle.OUTLINE, Modifier.weight(1f))
            button("SAU", onNext, canGoNext, ArcadeActionStyle.OUTLINE, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MatchDetailScoreboard(summary: MatchHistorySnapshot) {
    val outcomeAccent = when (summary.outcome) {
        MatchHistoryOutcome.WIN -> ArcadeSuccess
        MatchHistoryOutcome.LOSS -> ArcadeOpponent
        MatchHistoryOutcome.DRAW -> ArcadeGold
    }
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = outcomeAccent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    summary.playerScore.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("BẠN", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    "VS",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Black
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    summary.opponentScore.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = ArcadeOpponent
                )
                Text(
                    summary.opponentName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatMatchDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

private fun CosmeticSnapshot.displayLabel(): String {
    if (unlocked) return name
    val requirement = unlockRequirement()
    return if (requirement == null) "Đã khóa · $name" else "Đã khóa · $name · $requirement"
}

private fun CosmeticSnapshot.unlockRequirement(): String? = when (id) {
    "frame_bronze" -> "Cấp 3"
    "frame_silver" -> "Cấp 6"
    "frame_gold" -> "Cấp 10"
    "frame_perfect" -> "Cấp 15 + thắng không bấm sai"
    "frame_persistent" -> "Điểm danh 100 lần"
    "title_champion" -> "Thắng 10 trận"
    "title_speed" -> "Thắng và chọn đủ 50 số trong 30 giây"
    "title_diligent" -> "Điểm danh 30 ngày liên tiếp"
    DAILY_CHECK_IN_AVATAR_ID -> "Điểm danh 50 lần"
    else -> null
}

@Composable
private fun MatchHistoryCard(match: MatchHistorySnapshot, onClick: (() -> Unit)?) {
    val (result, accent) = when (match.outcome) {
        MatchHistoryOutcome.WIN -> "THẮNG" to ArcadeSuccess
        MatchHistoryOutcome.LOSS -> "THUA" to ArcadeOpponent
        MatchHistoryOutcome.DRAW -> "HÒA" to ArcadeGold
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("match_history:${match.matchId}").then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
        ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.48f)),
        shadowElevation = 2.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 390.dp
            val eloText = if (match.eloChange >= 0) "+${match.eloChange}" else match.eloChange.toString()
            val details: @Composable () -> Unit = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "vs ${match.opponentName} · ${match.playerScore} – ${match.opponentScore}",
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${match.gameMode.displayName()} · ${if (match.matchType == MatchType.RANKED) "Xếp hạng" else "Đấu thường"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val resultBadge: @Composable () -> Unit = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.48f))
                ) {
                    Text(
                        result,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            val elo: @Composable () -> Unit = {
                Text(
                    if (match.matchType == MatchType.RANKED) "$eloText Elo" else "Thường",
                    color = when {
                        match.matchType != MatchType.RANKED -> MaterialTheme.colorScheme.onSurfaceVariant
                        match.eloChange >= 0 -> ArcadeSuccess
                        else -> ArcadeOpponent
                    },
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        resultBadge()
                        Box(modifier = Modifier.weight(1f)) { details() }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { elo() }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    resultBadge()
                    Box(modifier = Modifier.weight(1f)) { details() }
                    elo()
                    if (onClick != null) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
