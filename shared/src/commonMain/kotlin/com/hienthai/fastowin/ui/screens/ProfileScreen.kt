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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.hienthai.fastowin.data.network.toAvatarImageUrl
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.GameModeStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.MAX_ACCOUNT_PASSWORD_LENGTH
import com.hienthai.fastowin.state.accountPasswordConfirmationError
import com.hienthai.fastowin.state.accountPasswordError
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedNetworkText
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.createPlainTextClipEntry
import com.hienthai.fastowin.platform.toImageBitmap
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.components.RewardAmounts
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.WalletDeltaAmounts
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

enum class ProfileSection {
    STATISTICS,
    WALLET,
    DAILY_CHECK_IN,
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
    imagePicker: @Composable (
        onImageSelected: (ByteArray?) -> Unit,
        content: @Composable (onClick: () -> Unit) -> Unit
    ) -> Unit = { onImageSelected, content ->
        com.hienthai.fastowin.platform.ImagePicker(onImageSelected, content)
    },
    modifier: Modifier = Modifier
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)
    val profile = if (isExternalProfile) profileOverride else state.profile
    val isProfileLoading = if (isExternalProfile) state.isFriendProfileLoading else state.isProfileLoading
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var showAccountSecurity by remember { mutableStateOf(false) }
    var showAccountSessions by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var pendingAvatarBytes by remember(profile?.userId) { mutableStateOf<ByteArray?>(null) }
    var pendingAvatarBitmap by remember(profile?.userId) { mutableStateOf<ImageBitmap?>(null) }
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
    if (!isExternalProfile && showLogoutConfirmation) {
        ArcadeDialog(
            title = localized(TextKey.LogoutTitle),
            subtitle = localized(TextKey.LogoutDescription),
            onDismissRequest = { showLogoutConfirmation = false },
            modifier = Modifier.testTag("logout_confirmation_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = localized(TextKey.LogoutAction),
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_logout")
                )
                ArcadeActionButton(
                    label = localized(TextKey.Cancel).uppercase(),
                    onClick = { showLogoutConfirmation = false },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
        if (state.profileNotice != null) {
            isEditing = false
            pendingAvatarBytes = null
            pendingAvatarBitmap = null
        }
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
        FastToWinPullRefresh(
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
                title = localized(if (isExternalProfile) TextKey.ExternalPlayerTitle else TextKey.ProfileTitle),
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
            Text(localized(TextKey.ProfileLoadError))
            return@Column
        }
        val progression = profile.progression

        ProfileIdentityPanel(
            serverUrl = serverUrl,
            profile = profile,
            avatarRevision = state.avatarRevision,
            canEdit = canEdit,
            isSaving = state.isProfileSaving,
            isExternalProfile = isExternalProfile,
            sessionStartedAtMillis = sessionStartedAtMillis,
            onEdit = {
                if (isEditing) {
                    pendingAvatarBytes = null
                    pendingAvatarBitmap = null
                }
                isEditing = !isEditing
            },
            onCopyCode = {
                coroutineScope.launch {
                    clipboard.setClipEntry(createPlainTextClipEntry(profile.playerCode))
                    isPlayerCodeCopied = true
                }
            }
        )

        if (isExternalProfile && onInviteToClan != null && state.profile?.clanId != null && profile.clanId == null) {
            ArcadeActionButton(
                label = localized(TextKey.InviteToClan),
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
                    Text(localized(TextKey.EditProfile), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= MAX_PROFILE_DISPLAY_NAME_LENGTH) displayName = it },
                        label = { Text(localized(TextKey.Nickname)) },
                        supportingText = { Text("${displayName.length}/$MAX_PROFILE_DISPLAY_NAME_LENGTH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_display_name")
                    )
                    pendingAvatarBitmap?.let { bitmap ->
                        Box(
                            modifier = Modifier.fillMaxWidth().testTag("profile_avatar_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerAvatar(
                                displayName = displayName.ifBlank { profile.displayName },
                                avatarId = profile.avatarId,
                                userId = profile.userId,
                                frameId = progression.cosmetics
                                    .firstOrNull { it.type == CosmeticType.FRAME && it.equipped }
                                    ?.id
                                    ?: "frame_default",
                                size = 96.dp,
                                previewBitmap = bitmap
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(localized(TextKey.Avatar), fontWeight = FontWeight.Medium)
                        imagePicker(
                            { bytes ->
                                if (bytes != null) {
                                    val bitmap = runCatching { bytes.toImageBitmap() }.getOrNull()
                                    if (bitmap != null) {
                                        pendingAvatarBytes = bytes
                                        pendingAvatarBitmap = bitmap
                                    }
                                }
                            }
                        ) { onClick ->
                            TextButton(
                                onClick = onClick,
                                modifier = Modifier.testTag("profile_avatar_picker")
                            ) {
                                Text(localized(TextKey.UploadImage))
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcadeActionButton(
                            label = if (state.isProfileSaving) localized(TextKey.Saving) else localized(TextKey.Save),
                            onClick = {
                                pendingAvatarBytes?.let(onUploadAvatar)
                                onSave(displayName, profile.avatarId)
                            },
                            enabled = displayName.isNotBlank() && !state.isProfileSaving,
                            modifier = Modifier.fillMaxWidth(),
                            style = ArcadeActionStyle.GOLD
                        )
                        ArcadeActionButton(
                            label = localized(TextKey.Cancel),
                            onClick = {
                                displayName = profile.displayName
                                pendingAvatarBytes = null
                                pendingAvatarBitmap = null
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
                    localized(TextKey.Matches) to profile.statistics.totalMatches.toString(),
                    localized(TextKey.Wins) to profile.statistics.wins.toString(),
                    "Elo" to profile.statistics.eloRating.toString()
                )
            )
            Text(localized(TextKey.FeaturedAchievements), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            ArcadeStatGrid(
                stats = listOf(
                    localized(TextKey.BestStreak) to profile.statistics.bestWinStreak.toString(),
                    localized(TextKey.AverageReactionShort) to "${profile.statistics.averageReactionMillis} ms"
                )
            )
        }

        ProfileSectionTitle(localized(TextKey.Activity))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                AccountActionRow(
                    icon = Icons.Default.Insights,
                    title = localized(TextKey.StatisticsAchievements),
                    subtitle = localized(TextKey.PerformanceMilestones),
                    onClick = { onOpenSection(ProfileSection.STATISTICS) },
                    modifier = Modifier.testTag("profile_section_statistics")
                )
                if (!isExternalProfile && canEdit) {
                    AccountActionRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = localized(TextKey.WalletHistory),
                        subtitle = localized(TextKey.WalletHistorySubtitle),
                        onClick = { onOpenSection(ProfileSection.WALLET) },
                        modifier = Modifier.testTag("profile_section_wallet")
                    )
                }
                if (!isExternalProfile) {
                    AccountActionRow(
                        icon = Icons.Default.DateRange,
                        title = localized(TextKey.CheckInHistory),
                        subtitle = localized(TextKey.CheckInHistorySubtitle),
                        onClick = { onOpenSection(ProfileSection.DAILY_CHECK_IN) },
                        modifier = Modifier.testTag("profile_section_daily_check_in")
                    )
                    AccountActionRow(
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        title = localized(TextKey.MissionsTitle),
                        subtitle = localized(TextKey.MissionsSubtitle),
                        onClick = { onOpenSection(ProfileSection.MISSIONS) },
                        modifier = Modifier.testTag("profile_section_missions")
                    )
                    AccountActionRow(
                        icon = Icons.Default.Collections,
                        title = localized(TextKey.CollectionTitle),
                        subtitle = localized(TextKey.CollectionSubtitle),
                        onClick = { onOpenSection(ProfileSection.COLLECTION) },
                        modifier = Modifier.testTag("profile_section_collection")
                    )
                }
                AccountActionRow(
                    icon = Icons.Default.History,
                    title = localized(TextKey.RecentMatches),
                    subtitle = localized(TextKey.MatchHistory),
                    onClick = { onOpenSection(ProfileSection.RECENT_MATCHES) },
                    modifier = Modifier.testTag("profile_section_recent_matches")
                )
        }

        if (canEdit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileSectionTitle(localized(TextKey.SettingsAccount))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        AccountActionRow(
                            icon = Icons.Default.Settings,
                            title = localized(TextKey.AppSettings),
                            subtitle = "",
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("profile_settings")
                        )
                        AccountActionRow(
                            icon = Icons.Default.Devices,
                            title = localized(TextKey.LoginDevices),
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
                            title = localized(TextKey.AccountSecurity),
                            subtitle = "",
                            onClick = {
                                onClearAccountFeedback()
                                showAccountSecurity = true
                            },
                            modifier = Modifier.testTag("profile_security")
                        )
                        AccountActionRow(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            title = localized(TextKey.Logout),
                            subtitle = "",
                            isDestructive = true,
                            onClick = { showLogoutConfirmation = true },
                            modifier = Modifier.testTag("profile_logout")
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
                    localized(TextKey.Copied),
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
    avatarRevision: Long,
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
    }?.localizedName() ?: localized(TextKey.Rookie)

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
                            userId = profile.userId,
                            frameId = equippedFrame,
                            size = 76.dp,
                            imageUrl = serverUrl.toAvatarImageUrl(profile.userId, avatarRevision)
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
                            userId = profile.userId,
                            frameId = equippedFrame,
                            size = 76.dp,
                            imageUrl = serverUrl.toAvatarImageUrl(profile.userId, avatarRevision)
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
                                    contentDescription = localized(TextKey.EditProfile),
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
            localized(TextKey.TitleValue, "title" to equippedTitle),
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
                localized(TextKey.PlayerCodeValue, "code" to profile.playerCode),
                modifier = if (centered) Modifier else Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onCopyCode, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = localized(TextKey.CopyPlayerCode),
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
                localized(TextKey.Level, "level" to progression.level),
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
                ProfileSection.STATISTICS -> localized(TextKey.StatisticsAchievements)
                ProfileSection.WALLET -> localized(TextKey.WalletHistory)
                ProfileSection.DAILY_CHECK_IN -> localized(TextKey.CheckInHistory)
                ProfileSection.MISSIONS -> localized(TextKey.MissionsTitle)
                ProfileSection.COLLECTION -> localized(TextKey.CollectionTitle)
                ProfileSection.RECENT_MATCHES -> localized(TextKey.RecentMatches)
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
            FastToWinPullRefresh(
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
                                    localized(TextKey.Gold) to profile.progression.gold.toString(),
                                    localized(TextKey.Gems) to profile.progression.gems.toString(),
                                    "XP" to profile.progression.currentLevelExperience.toString()
                                )
                            )
                            WalletHistorySectionContent(state.walletTransactions)
                        }
                        ProfileSection.DAILY_CHECK_IN -> {
                            val checkIn = profile.progression.dailyCheckIn
                            ProfileDailyCheckInStrip(checkIn)
                            DailyCheckInCalendar(checkIn)
                            DailyCheckInMilestones(
                                bestStreak = checkIn.bestStreak,
                                totalCheckIns = checkIn.totalCheckIns
                            )
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
            localized(TextKey.ProfilePerformanceHero),
            localized(TextKey.ProfilePerformanceDescription),
            Res.drawable.arcade_leaderboard_trophy
        )
        ProfileSection.WALLET -> Triple(
            localized(TextKey.WalletFlowHero),
            localized(TextKey.WalletFlowDescription),
            Res.drawable.arcade_shop_chest
        )
        ProfileSection.DAILY_CHECK_IN -> Triple(
            localized(TextKey.AttendanceJourney),
            localized(TextKey.AttendanceJourneyDescription),
            Res.drawable.arcade_leaderboard_trophy
        )
        ProfileSection.MISSIONS -> Triple(
            localized(TextKey.TodayMissions),
            localized(TextKey.TodayMissionsDescription),
            Res.drawable.arcade_notifications_inbox
        )
        ProfileSection.COLLECTION -> Triple(
            localized(TextKey.PersonalCollectionHero),
            localized(TextKey.PersonalCollectionDescription),
            Res.drawable.arcade_shop_chest
        )
        ProfileSection.RECENT_MATCHES -> Triple(
            localized(TextKey.RecentCompetitionHero),
            localized(TextKey.RecentCompetitionDescription),
            Res.drawable.arcade_room_portal
        )
    }
    val accent = when (section) {
        ProfileSection.WALLET, ProfileSection.COLLECTION -> ArcadeGold
        ProfileSection.DAILY_CHECK_IN -> ArcadeGold
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
    var visibleTransactionCount by remember(selectedFilter) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val filteredTransactions = remember(transactions, selectedFilter) {
        transactions.filter { transaction ->
            when (selectedFilter) {
                1 -> transaction.goldDelta > 0 || transaction.gemsDelta > 0 || transaction.xpDelta > 0
                2 -> transaction.goldDelta < 0 || transaction.gemsDelta < 0 || transaction.xpDelta < 0
                else -> true
            }
        }
    }
    val visibleTransactions = filteredTransactions.take(visibleTransactionCount)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("wallet_history_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArcadeSegmentedControl(
            labels = listOf(localized(TextKey.RecentFilterAll), localized(TextKey.Received), localized(TextKey.Used)),
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
                    Text(localized(TextKey.NoWalletActivity), fontWeight = FontWeight.Bold)
                    Text(
                        if (transactions.isEmpty()) {
                            localized(TextKey.WalletRewardsAppear)
                        } else {
                            localized(TextKey.WalletNoActivityInFilter)
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
            ArcadeLoadMoreButton(
                visibleItemCount = visibleTransactions.size,
                totalItemCount = filteredTransactions.size,
                onLoadMore = {
                    visibleTransactionCount = nextArcadePageItemCount(
                        visibleTransactionCount,
                        filteredTransactions.size
                    )
                },
                testTag = "wallet_history_load_more"
            )
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

@Composable
private fun walletSourceLabel(sourceType: String): String = localized(when (sourceType) {
    "DAILY_CHECK_IN" -> TextKey.DailyCheckInSource
    "MISSION" -> TextKey.MissionRewardSource
    "MATCH" -> TextKey.MatchRewardSource
    "CLAN_QUEST" -> TextKey.ClanMissionSource
    "COSMETIC_PURCHASE" -> TextKey.CosmeticPurchaseSource
    "TOURNAMENT_ENTRY" -> TextKey.TournamentEntrySource
    "TOURNAMENT_PRIZE" -> TextKey.TournamentPrizeSource
    "SEASON_REWARD" -> TextKey.SeasonRewardSource
    "STORE_PURCHASE" -> TextKey.GemTopUpSource
    else -> TextKey.WalletAdjustmentSource
})

@Composable
private fun walletRelativeTime(createdAtEpochMillis: Long, nowMillis: Long = epochMillis()): String {
    val elapsed = (nowMillis - createdAtEpochMillis).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> localized(TextKey.JustNow)
        elapsed < 3_600_000L -> localized(TextKey.MinutesAgo, "count" to elapsed / 60_000L)
        elapsed < 86_400_000L -> localized(TextKey.HoursAgo, "count" to elapsed / 3_600_000L)
        else -> localized(TextKey.DaysAgo, "count" to elapsed / 86_400_000L)
    }
}

@Composable
private fun StatisticsAchievementsSectionContent(profile: PlayerProfileSnapshot) {
    val localization = LocalLocalization.current
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
                localized(TextKey.WinRate) to "$winRate%",
                localized(TextKey.AverageReactionShort) to "${stats.averageReactionMillis} ms",
                localized(TextKey.AccuracyLabel) to "$accuracy%"
            )
        )

        if (profile.recentMatches.isNotEmpty()) {
            val scores = profile.recentMatches.take(10).asReversed().map { it.playerScore }
            val eloTrend = remember(stats.eloRating, profile.recentMatches) {
                buildEloTrend(stats.eloRating, profile.recentMatches.take(10))
            }
            TrendChart(localized(TextKey.RecentFormTen), scores) {
                localization.text(TextKey.PointsCount, mapOf("count" to it))
            }
            TrendChart(localized(TextKey.EloChange), eloTrend) { "Elo $it" }
        }

        Text(localized(TextKey.Overview), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        ArcadeStatGrid(
            stats = listOf(
                localized(TextKey.Matches) to stats.totalMatches.toString(),
                localized(TextKey.Wins) to stats.wins.toString(),
                localized(TextKey.Losses) to stats.losses.toString(),
                localized(TextKey.DrawsLabel) to stats.draws.toString(),
                localized(TextKey.HighScore) to stats.highestScore.toString(),
                localized(TextKey.BestStreak) to stats.bestWinStreak.toString(),
                localized(TextKey.CorrectWrongLabel) to "${stats.correctSelections} / ${stats.wrongSelections}"
            )
        )

        if (profile.modeStatistics.isNotEmpty()) {
            Text(localized(TextKey.ModeStatistics), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier.fillMaxWidth().testTag("mode_statistics"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                profile.modeStatistics
                    .sortedWith(compareBy<GameModeStatisticsSnapshot> { it.gameMode.unlockLevel }.thenBy { it.gameMode.name })
                    .forEach { ModeStatisticsCard(it) }
            }
        }

        Text(localized(TextKey.AchievementsTitle), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        if (profile.achievements.isEmpty()) {
            ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadeGold) {
                Text(
                    localized(TextKey.NoAchievements),
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
                                            Text(localizedAchievementTitle(achievement), fontWeight = FontWeight.Black)
                                            Text(
                                                localizedAchievementDescription(achievement),
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
        localized(TextKey.DailyMissions) to profile.progression.dailyMissions,
        localized(TextKey.WeeklyMissions) to profile.progression.weeklyMissions
    )
    if (missionGroups.all { it.second.isEmpty() }) {
        Text(localized(TextKey.NoMissions), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    localized(TextKey.MissionProgress, "completed" to missions.count { it.completed }, "total" to missions.size),
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
            Text(localizedMissionTitle(mission), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
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
private fun localizedAchievementTitle(achievement: com.hienthai.fastowin.protocol.AchievementSnapshot): String =
    localizedNetworkText(
        achievement.titleKey ?: legacyAchievementTitleKey(achievement.code)?.name,
        emptyMap(),
        achievement.title
    )

private fun legacyAchievementTitleKey(code: String): TextKey? = when (code) {
    "FIRST_WIN" -> TextKey.AchievementFirstWinTitle
    "WIN_10" -> TextKey.AchievementWinTenTitle
    "PERFECT_GAME" -> TextKey.AchievementPerfectTitle
    "SPEED_50" -> TextKey.AchievementSpeedTitle
    "DAILY_STREAK_7" -> TextKey.AchievementCheckInTitle
    else -> null
}

@Composable
private fun localizedAchievementDescription(achievement: com.hienthai.fastowin.protocol.AchievementSnapshot): String =
    localizedNetworkText(
        achievement.descriptionKey ?: legacyAchievementDescriptionKey(achievement.code)?.name,
        emptyMap(),
        achievement.description
    )

private fun legacyAchievementDescriptionKey(code: String): TextKey? = when (code) {
    "FIRST_WIN" -> TextKey.AchievementFirstWinDescription
    "WIN_10" -> TextKey.AchievementWinTenDescription
    "PERFECT_GAME" -> TextKey.AchievementPerfectDescription
    "SPEED_50" -> TextKey.AchievementSpeedDescription
    "DAILY_STREAK_7" -> TextKey.AchievementCheckInDescription
    else -> null
}

@Composable
private fun localizedMissionTitle(mission: MissionSnapshot): String =
    localizedNetworkText(
        mission.titleKey ?: when (mission.code) {
            "DAILY_PLAY_3" -> TextKey.MissionPlayThree.name
            "DAILY_WIN_1" -> TextKey.MissionWinOne.name
            "WEEKLY_CORRECT_100" -> TextKey.MissionCorrectHundred.name
            "WEEKLY_PERFECT_1" -> TextKey.MissionPerfectWin.name
            else -> null
        },
        emptyMap(),
        mission.title
    )

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
            label = localized(TextKey.Claimed),
            onClick = {},
            enabled = false,
            style = ArcadeActionStyle.OUTLINE,
            modifier = modifier.heightIn(min = 48.dp).testTag("claim_mission:${mission.code}")
        )
        mission.completed && canEdit -> ArcadeActionButton(
            label = localized(if (claimingMissionCode == mission.code) TextKey.Claiming else TextKey.ClaimReward),
            onClick = onClaim,
            enabled = claimingMissionCode == null,
            style = ArcadeActionStyle.GOLD,
            modifier = modifier.heightIn(min = 48.dp).testTag("claim_mission:${mission.code}")
        )
        mission.completed -> Text(localized(TextKey.MissionCompleted), modifier = modifier, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MissionDifficultyBadge(difficulty: MissionDifficulty) {
    val label = when (difficulty) {
        MissionDifficulty.EASY -> localized(TextKey.DifficultyEasy)
        MissionDifficulty.NORMAL -> localized(TextKey.DifficultyNormal)
        MissionDifficulty.HARD -> localized(TextKey.DifficultyHard)
        MissionDifficulty.ELITE -> localized(TextKey.DifficultyElite)
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
        Text(localized(TextKey.EmptyCollection), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val equippedFrame = cosmetics.firstOrNull { it.type == CosmeticType.FRAME && it.equipped }?.id
        ?: "frame_default"
    val equippedTitle = cosmetics.firstOrNull { it.type == CosmeticType.TITLE && it.equipped }?.id
        ?: "title_rookie"
    val equippingCosmeticId = state.equippingCosmeticId
    Text(localized(TextKey.Frames), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
    Text(localized(TextKey.Titles), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
                Text(cosmetic.localizedName(), fontWeight = FontWeight.Black)
                Text(
                    when {
                        isEquipping -> localized(TextKey.Equipping)
                        cosmetic.equipped -> localized(TextKey.Equipped)
                        cosmetic.unlocked && canEquip -> localized(TextKey.TapToEquip)
                        cosmetic.unlocked -> localized(TextKey.Unlocked)
                        else -> cosmetic.localizedUnlockRequirement()?.let {
                            localized(TextKey.UnlockRequirement, "requirement" to it)
                        }
                            ?: localized(TextKey.Locked)
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
                    userId = profile.userId,
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
                cosmetic.localizedName(),
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
                        isEquipping -> localized(TextKey.Equipping)
                        cosmetic.equipped -> localized(TextKey.Equipped)
                        cosmetic.unlocked && canEquip -> localized(TextKey.TapToEquip).uppercase()
                        cosmetic.unlocked -> localized(TextKey.Unlocked)
                        else -> cosmetic.localizedUnlockRequirement()?.let {
                            localized(TextKey.UnlockRequirement, "requirement" to it)
                        }
                            ?: localized(TextKey.Locked)
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
    var visibleMatchCount by remember(historyFilter) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    ArcadeSegmentedControl(
        labels = listOf(localized(TextKey.RecentFilterAll), localized(TextKey.RecentFilterWins), localized(TextKey.RecentFilterLosses)),
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
    val filteredMatches = profile.recentMatches.filter { historyFilter == null || it.outcome == historyFilter }
    val visibleMatches = filteredMatches.take(visibleMatchCount)
    when {
        profile.recentMatches.isEmpty() -> Text(
            localized(TextKey.NoCompletedMatches),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        filteredMatches.isEmpty() -> Text(
            localized(TextKey.NoMatchesForFilter),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> {
            visibleMatches.forEach { match ->
                MatchHistoryCard(
                    match,
                    onClick = if (isExternalProfile) null else ({ onOpenMatchDetail(match.matchId) })
                )
            }
            ArcadeLoadMoreButton(
                visibleItemCount = visibleMatches.size,
                totalItemCount = filteredMatches.size,
                onLoadMore = {
                    visibleMatchCount = nextArcadePageItemCount(visibleMatchCount, filteredMatches.size)
                },
                testTag = "recent_matches_load_more"
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
            localized(TextKey.CurrentSessionDuration, "duration" to formatSessionDuration(elapsedSeconds)),
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
        title = localized(TextKey.AccountSecurityTitle),
        subtitle = localized(TextKey.AccountSecurityDescription),
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
                    Text(localized(TextKey.ChangePassword), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                SecurePasswordField(currentPassword, localized(TextKey.CurrentPassword)) { currentPassword = it }
                SecurePasswordField(newPassword, localized(TextKey.NewPasswordLabel)) { newPassword = it }
                SecurePasswordField(confirmPassword, localized(TextKey.ConfirmNewPasswordLabel)) { confirmPassword = it }
                if (newPassword.isNotEmpty() && passwordError != null) {
                    Text(localized(passwordError), color = MaterialTheme.colorScheme.error)
                }
                confirmationError?.let { Text(localized(it), color = MaterialTheme.colorScheme.error) }
                if (passwordUnchanged) {
                    Text(localized(TextKey.NewPasswordMustDiffer), color = MaterialTheme.colorScheme.error)
                }
                ArcadeActionButton(
                    label = if (isLoading) localized(TextKey.UpdatingPassword) else localized(TextKey.ChangePassword).uppercase(),
                    onClick = { onChangePassword(currentPassword, newPassword) },
                    enabled = !isLoading && currentPassword.isNotBlank() && passwordError == null &&
                        confirmationError == null && confirmPassword.isNotEmpty() && !passwordUnchanged,
                    modifier = Modifier.fillMaxWidth(),
                    style = ArcadeActionStyle.GOLD
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadeOpponent) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(localized(TextKey.DangerZone), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                        Text(
                            localized(TextKey.DeleteAccountWarning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SecurePasswordField(deletePassword, localized(TextKey.PasswordToConfirm)) { deletePassword = it }
                if (!confirmDelete) {
                    ArcadeActionButton(
                        label = localized(TextKey.RequestDeleteAccount),
                        onClick = { confirmDelete = true },
                        enabled = !isLoading && deletePassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        style = ArcadeActionStyle.OUTLINE
                    )
                } else {
                    ArcadeActionButton(
                        label = if (isLoading) localized(TextKey.DeletingAccount) else localized(TextKey.Delete).uppercase(),
                        onClick = { onDeleteAccount(deletePassword) },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        style = ArcadeActionStyle.DANGER
                    )
                }
        }
        ArcadeActionButton(
            label = localized(TextKey.Close),
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
            title = localized(if (session.isCurrent) TextKey.RevokeCurrentDeviceTitle else TextKey.RevokeDeviceTitle),
            subtitle = if (session.isCurrent) {
                localized(TextKey.RevokeCurrentDeviceDescription)
            } else {
                localized(TextKey.RevokeOtherDeviceDescription, "device" to sessionDeviceLabel(session.devicePlatform))
            },
            onDismissRequest = { sessionPendingRevoke = null }
        ) {
            ArcadeActionButton(
                label = localized(TextKey.Logout),
                onClick = {
                    sessionPendingRevoke = null
                    onRevokeSession(session.sessionId)
                },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.DANGER
            )
            ArcadeActionButton(
                label = localized(TextKey.Cancel),
                onClick = { sessionPendingRevoke = null },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }
    if (confirmRevokeAll) {
        ArcadeDialog(
            title = localized(TextKey.LogoutAllDevicesTitle),
            subtitle = localized(TextKey.LogoutAllDevicesDescription),
            onDismissRequest = { confirmRevokeAll = false }
        ) {
            ArcadeActionButton(
                label = localized(TextKey.LogoutAllDevices),
                onClick = {
                    confirmRevokeAll = false
                    onRevokeAllSessions()
                },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.DANGER
            )
            ArcadeActionButton(
                label = localized(TextKey.Cancel),
                onClick = { confirmRevokeAll = false },
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }

    ArcadeDialog(
        title = localized(TextKey.LoginDevicesTitle),
        subtitle = localized(TextKey.LoginDevicesDescription),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Text(
                    localized(TextKey.LoginDevicesDescription),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading && sessions.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (sessions.isEmpty()) {
                    Text(localized(TextKey.NoActiveSessions))
                } else {
                    sessions.forEach { session ->
                        val deviceLabel = sessionDeviceLabel(session.devicePlatform)
                        val currentDeviceLabel = localized(TextKey.CurrentDevice)
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
                                            append(deviceLabel)
                                            if (session.isCurrent) append(" • $currentDeviceLabel")
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = if (session.isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        localized(TextKey.ActivityTime, "time" to relativeSessionTime(session.lastSeenAtEpochMillis)),
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
                                ) { Text(localized(TextKey.Logout)) }
                            }
                        }
                    }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        ArcadeActionButton(
            label = localized(TextKey.LogoutAllDevices),
            onClick = { confirmRevokeAll = true },
            enabled = !isLoading && sessions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.DANGER
        )
        ArcadeActionButton(
            label = localized(TextKey.Close).uppercase(),
            onClick = onDismiss,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().testTag("account_sessions_close"),
            style = ArcadeActionStyle.PRIMARY
        )
    }
}

@Composable
private fun sessionDeviceLabel(platform: String?): String = when (platform?.lowercase()) {
    "android" -> "Android"
    "ios" -> "iPhone / iPad"
    else -> localized(TextKey.UnknownDevice)
}

@Composable
private fun relativeSessionTime(timestampMillis: Long): String {
    val elapsed = (epochMillis() - timestampMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1L -> localized(TextKey.JustNow)
        hours < 1L -> localized(TextKey.MinutesAgo, "count" to minutes)
        days < 1L -> localized(TextKey.HoursAgo, "count" to hours)
        else -> localized(TextKey.DaysAgo, "count" to days)
    }
}

@Composable
private fun sessionExpiryLabel(expiresAtMillis: Long): String {
    val remainingDays = ((expiresAtMillis - epochMillis()).coerceAtLeast(0L) / 86_400_000L) + 1L
    return localized(TextKey.SessionExpiresInDays, "count" to remainingDays)
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
                    contentDescription = localized(if (isVisible) TextKey.HidePassword else TextKey.ShowPassword)
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
            ProfileSectionTitle(localized(TextKey.CheckIn))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ArcadeGold.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, ArcadeGold.copy(alpha = 0.34f))
            ) {
                Text(
                    localized(TextKey.StreakDays, "count" to checkIn.currentStreak),
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
                Text(localized(TextKey.CheckInHistory), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Surface(shape = RoundedCornerShape(999.dp), color = ArcadeGold.copy(alpha = 0.16f)) {
                    Text(
                        localized(TextKey.StreakDays, "count" to checkIn.currentStreak),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = ArcadeGold,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                localized(TextKey.CheckInCalendarDescription),
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
                    Icon(Icons.Default.ChevronLeft, localized(TextKey.PreviousMonth))
                }
                Text(
                    localized(TextKey.MonthYear, "month" to visibleMonth.month, "year" to visibleMonth.year),
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { visibleMonth = visibleMonth.shift(1) },
                    enabled = visibleMonth < currentMonth
                ) {
                    Icon(Icons.Default.ChevronRight, localized(TextKey.NextMonth))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                localizedCalendarWeekdays().forEach { label ->
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    localized(TextKey.CheckedIn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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

@Composable
private fun localizedCalendarWeekdays() = listOf(
    localized(TextKey.WeekMonday), localized(TextKey.WeekTuesday), localized(TextKey.WeekWednesday),
    localized(TextKey.WeekThursday), localized(TextKey.WeekFriday), localized(TextKey.WeekSaturday),
    localized(TextKey.WeekSunday)
)

@Composable
private fun DailyCheckInMilestones(bestStreak: Int, totalCheckIns: Int) {
    val milestones = listOf(
        DailyCheckInMilestone(
            icon = Icons.Default.EmojiEvents,
            title = localized(TextKey.ConsecutiveDays, "count" to 7),
            reward = localized(TextKey.SteadyStartAchievement),
            progress = bestStreak,
            target = DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET
        ),
        DailyCheckInMilestone(
            icon = Icons.Default.MilitaryTech,
            title = localized(TextKey.ConsecutiveDays, "count" to 30),
            reward = localized(TextKey.DiligentTitle),
            progress = bestStreak,
            target = DAILY_CHECK_IN_TITLE_TARGET
        ),
        DailyCheckInMilestone(
            icon = Icons.Default.Shield,
            title = localized(TextKey.TotalCheckIns, "count" to 100),
            reward = localized(TextKey.PersistentFrame),
            progress = totalCheckIns,
            target = DAILY_CHECK_IN_FRAME_TARGET
        )
    )
    Column(
        modifier = Modifier.fillMaxWidth().testTag("daily_check_in_milestones"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(localized(TextKey.CheckInMilestones), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            localized(TextKey.CheckInSummary, "best" to bestStreak, "total" to totalCheckIns),
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
                Text(statistics.gameMode.localizedDisplayName(), fontWeight = FontWeight.Bold)
                Text(
                    localized(TextKey.ModeWinRate, "rate" to winRate),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                localized(
                    TextKey.ModeMatchSummary,
                    "matches" to statistics.totalMatches,
                    "wins" to statistics.wins,
                    "losses" to statistics.losses,
                    "draws" to statistics.draws
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { winRate / 100f },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = if (winRate >= 50) ArcadeSuccess else MaterialTheme.colorScheme.primary
            )
            Text(
                localized(TextKey.ScoreAverage, "high" to statistics.highestScore, "average" to statistics.averageScore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProtocolGameMode.localizedDisplayName(): String = localized(when (this) {
    ProtocolGameMode.ORDER -> TextKey.ModeClassicTitle
    ProtocolGameMode.RANDOM_TARGET -> TextKey.ModeRandomTitle
    ProtocolGameMode.TIME_BONUS -> TextKey.ModeTimeBonusTitle
    ProtocolGameMode.SPEED_UP -> TextKey.ModeSpeedUpTitle
    ProtocolGameMode.SURVIVAL -> TextKey.ModeSurvivalTitle
    ProtocolGameMode.COMBO -> TextKey.ModeComboTitle
    ProtocolGameMode.TIME_ATTACK -> TextKey.ModeTimeAttackTitle
    ProtocolGameMode.TEAM_2V2 -> TextKey.ModeTeamTitle
})

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
    val events = detail?.events.orEmpty()

    ArcadeDialog(
        onDismissRequest = onDismiss,
        title = localized(if (isLoading) TextKey.LoadingMatch else TextKey.MatchDetails),
        subtitle = detail?.let {
            "${it.summary.gameMode.localizedDisplayName()} · ${localized(if (it.summary.matchType == MatchType.RANKED) TextKey.Ranked else TextKey.Casual)}"
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
                    MatchDetailScoreboard(detail.summary, Modifier.testTag("match_detail_scoreboard"))
                    ArcadeStatGrid(
                        modifier = Modifier.testTag("match_detail_metrics"),
                        stats = listOf(
                            localized(TextKey.ReactionLabel) to if (averageReaction > 0) "${averageReaction} ms" else "--",
                            localized(TextKey.AccuracyLabel) to "$accuracy%",
                            "Elo" to if (detail.summary.matchType == MatchType.RANKED) {
                                if (detail.summary.eloChange >= 0) "+${detail.summary.eloChange}" else detail.summary.eloChange.toString()
                            } else {
                                localized(TextKey.NoEloChange)
                            }
                        )
                    )
                    Text(
                        localized(
                            TextKey.MatchTapSummary,
                            "duration" to formatMatchDuration(detail.durationMillis),
                            "correct" to correct,
                            "wrong" to wrong
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (reactionSamples.isNotEmpty()) {
                        ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Violet400) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(localized(TextKey.Fastest).uppercase(), style = MaterialTheme.typography.labelSmall, color = ArcadeSuccess)
                                    Text("${reactionSamples.minOrNull()} ms", fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(localized(TextKey.Slowest).uppercase(), style = MaterialTheme.typography.labelSmall, color = ArcadeOpponent)
                                    Text("${reactionSamples.maxOrNull()} ms", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        ArcadeActionButton(
            label = localized(TextKey.Close).uppercase(),
            onClick = onDismiss,
            style = ArcadeActionStyle.GOLD,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MatchDetailScoreboard(summary: MatchHistorySnapshot, modifier: Modifier = Modifier) {
    val outcomeAccent = when (summary.outcome) {
        MatchHistoryOutcome.WIN -> ArcadeSuccess
        MatchHistoryOutcome.LOSS -> ArcadeOpponent
        MatchHistoryOutcome.DRAW -> ArcadeGold
    }
    ArcadePanel(modifier = modifier.fillMaxWidth(), accent = outcomeAccent) {
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
                Text(localized(TextKey.You).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
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

@Composable
private fun CosmeticSnapshot.localizedName(): String =
    localizedNetworkText(nameKey, emptyMap(), name)

@Composable
private fun CosmeticSnapshot.localizedUnlockRequirement(): String? =
    unlockDescriptionKey?.let { localizedNetworkText(it, emptyMap(), "") }.takeUnless { it.isNullOrBlank() }
        ?: legacyUnlockRequirement()

@Composable
private fun CosmeticSnapshot.legacyUnlockRequirement(): String? = when (id) {
    "frame_bronze" -> localized(TextKey.UnlockLevel, "level" to 3)
    "frame_silver" -> localized(TextKey.UnlockLevel, "level" to 6)
    "frame_gold" -> localized(TextKey.UnlockLevel, "level" to 10)
    "frame_perfect" -> localized(TextKey.UnlockPerfectFrame)
    "frame_persistent" -> localized(TextKey.UnlockPersistentFrame)
    "title_champion" -> localized(TextKey.UnlockChampionTitle)
    "title_speed" -> localized(TextKey.UnlockSpeedTitle)
    "title_diligent" -> localized(TextKey.UnlockDiligentTitle)
    DAILY_CHECK_IN_AVATAR_ID -> localized(TextKey.UnlockCheckInAvatar)
    else -> null
}

@Composable
private fun MatchHistoryCard(match: MatchHistorySnapshot, onClick: (() -> Unit)?) {
    val (result, accent) = when (match.outcome) {
        MatchHistoryOutcome.WIN -> localized(TextKey.Win).uppercase() to ArcadeSuccess
        MatchHistoryOutcome.LOSS -> localized(TextKey.Loss).uppercase() to ArcadeOpponent
        MatchHistoryOutcome.DRAW -> localized(TextKey.Draw).uppercase() to ArcadeGold
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
                        "${match.gameMode.localizedDisplayName()} · ${localized(if (match.matchType == MatchType.RANKED) TextKey.Ranked else TextKey.Casual)}",
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
                    if (match.matchType == MatchType.RANKED) "$eloText Elo" else localized(TextKey.Casual),
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
