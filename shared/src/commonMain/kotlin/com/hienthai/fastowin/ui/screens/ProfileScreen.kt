package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ColumnScope
import com.hienthai.fastowin.ui.components.A1HeroTone
import com.hienthai.fastowin.ui.components.A1SkyBackdrop
import com.hienthai.fastowin.ui.components.A1FieldLabel
import com.hienthai.fastowin.ui.components.NativeDialogInputScope
import com.hienthai.fastowin.ui.components.arcadeDialogProperties
import com.hienthai.fastowin.state.MIN_ACCOUNT_PASSWORD_LENGTH
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.A1OutlinedText
import com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton
import com.hienthai.fastowin.ui.components.nextRankedTier
import com.hienthai.fastowin.ui.components.ratingProgressWithinTier
import com.hienthai.fastowin.ui.theme.A1Palette
import com.hienthai.fastowin.localization.localizedRankedTierName
import com.hienthai.fastowin.protocol.rankedTierFor
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.hienthai.fastowin.ui.components.AppOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.MatchDetailSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_FRAME_TARGET
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
import com.hienthai.fastowin.state.MissionState
import com.hienthai.fastowin.ui.components.ServerMissionList
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.createPlainTextClipEntry
import com.hienthai.fastowin.platform.toImageBitmap
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
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
import com.hienthai.fastowin.ui.components.HeaderRefreshAction
import com.hienthai.fastowin.ui.components.WalletDeltaAmounts
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.RewardAmount
import com.hienthai.fastowin.ui.components.RewardCurrency
import com.hienthai.fastowin.ui.components.catalogAvatarFrameName
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
    onSave: (String, String?, ByteArray?) -> Unit,
    canEdit: Boolean,
    profileOverride: PlayerProfileSnapshot? = null,
    isExternalProfile: Boolean = false,
    isAccountLoading: Boolean,
    accountError: String?,
    accountNotice: String?,
    accountSessions: List<AccountSessionSnapshot>,
    areSessionsLoading: Boolean,
    onChangePassword: (String, String) -> Unit,
    onDeleteAccount: () -> Unit,
    onClearAccountFeedback: () -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAllSessions: () -> Unit,
    onLogout: () -> Unit,
    sessionStartedAtMillis: Long? = null,
    onInviteToClan: ((String) -> Unit)? = null,
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** Mở màn Bạn bè (mục "Bạn bè" trong danh sách của màn Tôi). */
    onOpenFriends: (() -> Unit)? = null,
    /** Số lời mời kết bạn/mời phòng đang chờ (huy hiệu ở mục "Bạn bè"). */
    friendBadgeCount: Int = 0,
    /** Mở Lịch sử mùa, Hộp quà, Cửa hàng từ danh sách "Hoạt động" (null thì ẩn mục). */
    onOpenSeasonHistory: (() -> Unit)? = null,
    onOpenGifts: (() -> Unit)? = null,
    /** Số quà chưa nhận (huy hiệu ở mục "Hộp quà"). */
    giftBadgeCount: Long = 0,
    onOpenShop: (() -> Unit)? = null,
    onOpenSection: (ProfileSection) -> Unit = {},
    showBackButton: Boolean = true,
    onOpenRewardEvent: ((String) -> Unit)? = null,
    imagePicker: @Composable (
        onImageSelected: (ByteArray?) -> Unit,
        content: @Composable (onClick: () -> Unit) -> Unit
    ) -> Unit = { onImageSelected, content ->
        com.hienthai.fastowin.platform.ImagePicker(onImageSelected, content)
    },
    modifier: Modifier = Modifier,
    profileEditState: ProfileEditorState? = null
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)
    val profile = if (isExternalProfile) profileOverride else state.profile
    val isProfileLoading = if (isExternalProfile) state.isFriendProfileLoading else state.isProfileLoading
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val editor = profileEditState ?: rememberProfileEditorState(profile?.userId, state.profileNotice)
    val isEditing = editor.isEditing
    var displayName by editor::displayName
    var showAccountSecurity by remember { mutableStateOf(false) }
    var showAccountSessions by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    val avatarSelection = editor.avatarSelection
    DisposableEffect(avatarSelection) {
        // Image preparation is canceled when the picker leaves composition. Keep its last good draft.
        onDispose { avatarSelection.finishPreparation(null, null) }
    }
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
            onChangePassword = onChangePassword
        )
    }
    if (!isExternalProfile && showDeleteAccount) {
        DeleteAccountDialog(
            isLoading = isAccountLoading,
            error = accountError,
            onConfirm = onDeleteAccount,
            onDismiss = {
                if (!isAccountLoading) {
                    showDeleteAccount = false
                    onClearAccountFeedback()
                }
            }
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
        LogoutConfirmDialog(
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmation = false }
        )
    }
    if (!isExternalProfile && (state.isMatchDetailLoading || state.matchDetail != null)) {
        MatchDetailDialog(
            detail = state.matchDetail,
            isLoading = state.isMatchDetailLoading,
            onDismiss = onCloseMatchDetail,
            onOpenRewardEvent = onOpenRewardEvent
        )
    }
    LaunchedEffect(profile) {
        profile?.let {
            if (!isEditing) displayName = it.displayName
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
                applySafeDrawingInset = false,
                actions = {
                    HeaderRefreshAction(
                        isRefreshing = isProfileLoading,
                        onRefresh = onRefresh
                    )
                }
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
                if (isEditing) editor.discard() else editor.begin(profile.displayName)
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
            Box(modifier = Modifier.fillMaxWidth().a1Card()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(localized(TextKey.EditProfile), style = MaterialTheme.typography.titleLarge, color = A1Palette.Ink)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= MAX_PROFILE_DISPLAY_NAME_LENGTH) displayName = it },
                        label = localized(TextKey.Nickname),
                        supportingText = { Text("${displayName.length}/$MAX_PROFILE_DISPLAY_NAME_LENGTH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_display_name")
                    )
                    avatarSelection.preview?.let { bitmap ->
                        Box(
                            modifier = Modifier.fillMaxWidth().testTag("profile_avatar_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerAvatar(
                                displayName = displayName.ifBlank { profile.displayName },
                                avatarId = if (profile.avatarId == com.hienthai.fastowin.protocol.MODERATED_AVATAR_ID) null else profile.avatarId,
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
                        Text(localized(TextKey.Avatar), fontWeight = FontWeight.SemiBold, color = A1Palette.Ink)
                        key(avatarSelection) {
                            imagePicker(
                                { bytes ->
                                    val bitmap = bytes?.let { runCatching { it.toImageBitmap() }.getOrNull() }
                                    avatarSelection.finishPreparation(bytes, bitmap)
                                }
                            ) { onClick ->
                                TextButton(
                                    onClick = {
                                        avatarSelection.beginPreparation()
                                        onClick()
                                    },
                                    enabled = !state.isProfileSaving,
                                    modifier = Modifier.testTag("profile_avatar_picker")
                                ) {
                                    Text(localized(if (avatarSelection.isPreparing) TextKey.Processing else TextKey.UploadImage))
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcadeActionButton(
                            label = localized(
                                when {
                                    state.isProfileSaving -> TextKey.Saving
                                    avatarSelection.isPreparing -> TextKey.Processing
                                    else -> TextKey.Save
                                }
                            ),
                            onClick = {
                                avatarSelection.save(displayName, profile.avatarId, state.isProfileSaving, onSave)
                            },
                            enabled = avatarSelection.canSave(displayName, state.isProfileSaving),
                            modifier = Modifier.fillMaxWidth(),
                            style = ArcadeActionStyle.GOLD
                        )
                        ArcadeActionButton(
                            label = localized(TextKey.Cancel),
                            onClick = {
                                editor.discard()
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
        (state.profileSaveError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }

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

        val activityItems = buildList {
            add(ProfileMenuItem("profile_section_statistics", Icons.Rounded.Insights, localized(TextKey.StatisticsAchievements),
                ProfileTile.BLUE, subtitle = localized(TextKey.PerformanceMilestones)) { onOpenSection(ProfileSection.STATISTICS) })
            add(ProfileMenuItem("profile_section_recent_matches", Icons.Rounded.History, localized(TextKey.MatchHistory),
                ProfileTile.PURPLE) { onOpenSection(ProfileSection.RECENT_MATCHES) })
            if (!isExternalProfile) {
                add(ProfileMenuItem("profile_section_collection", Icons.Rounded.MilitaryTech, localized(TextKey.CollectionTitle),
                    ProfileTile.YELLOW, subtitle = localized(TextKey.CollectionSubtitle)) { onOpenSection(ProfileSection.COLLECTION) })
            }
            if (!isExternalProfile && canEdit) {
                add(ProfileMenuItem("profile_section_wallet", Icons.AutoMirrored.Rounded.ReceiptLong, localized(TextKey.WalletHistory),
                    ProfileTile.GREEN) { onOpenSection(ProfileSection.WALLET) })
            }
            if (!isExternalProfile && onOpenSeasonHistory != null) {
                add(ProfileMenuItem("profile_section_season_history", Icons.Rounded.WorkspacePremium, localized(TextKey.SeasonHistoryTitle),
                    ProfileTile.INK, onClick = onOpenSeasonHistory))
            }
            if (!isExternalProfile && onOpenFriends != null) {
                add(ProfileMenuItem("profile_section_friends", Icons.Rounded.Group, localized(TextKey.Friends),
                    ProfileTile.BLUE, badge = friendBadgeCount.toLong(), onClick = onOpenFriends))
            }
            if (!isExternalProfile && onOpenGifts != null) {
                add(ProfileMenuItem("profile_section_gifts", Icons.Rounded.CardGiftcard, localized(TextKey.HomeGifts),
                    ProfileTile.GREEN, badge = giftBadgeCount, onClick = onOpenGifts))
            }
            if (!isExternalProfile && onOpenShop != null) {
                add(ProfileMenuItem("profile_section_shop", Icons.Rounded.Storefront, localized(TextKey.Shop),
                    ProfileTile.YELLOW, onClick = onOpenShop))
            }
        }
        ProfileSkyTitle(localized(TextKey.Activity))
        ProfileMenuCard(activityItems)

        if (canEdit) {
            ProfileSkyTitle(localized(TextKey.SettingsAccount))
            ProfileMenuCard(
                listOf(
                    ProfileMenuItem("profile_settings", Icons.Rounded.Settings, localized(TextKey.AppSettings),
                        ProfileTile.WHITE, onClick = onOpenSettings),
                    ProfileMenuItem("profile_sessions", Icons.Rounded.Devices, localized(TextKey.LoginDevices), ProfileTile.WHITE) {
                        onClearAccountFeedback()
                        showAccountSessions = true
                        onLoadSessions()
                    },
                    ProfileMenuItem("profile_security", Icons.Rounded.Lock, localized(TextKey.AccountSecurity), ProfileTile.WHITE) {
                        onClearAccountFeedback()
                        showAccountSecurity = true
                    },
                    ProfileMenuItem("profile_logout", Icons.AutoMirrored.Rounded.Logout, localized(TextKey.Logout),
                        ProfileTile.WHITE, showChevron = false) { showLogoutConfirmation = true }
                )
            )
            // App Store 5.1.1(v): xoá tài khoản là một nút luôn hiện, kèm một bước xác nhận.
            ProfileDangerZone(
                onDelete = {
                    onClearAccountFeedback()
                    showDeleteAccount = true
                }
            )
        }
        Spacer(Modifier.height(8.dp))

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
    val isMaxLevel = progression.level >= 100
    // Thẻ hồ sơ A1: avatar có khung, tên, danh hiệu tím, mã; cấp + XP; ô hạng vàng.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_identity_card")
            .a1Card()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = if (canEdit) 52.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PlayerAvatar(
                    displayName = profile.displayName,
                    avatarId = profile.avatarId,
                    userId = profile.userId,
                    frameId = equippedFrame,
                    size = 96.dp,
                    imageUrl = serverUrl.toAvatarImageUrl(profile.userId, avatarRevision)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        profile.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 28.sp),
                        color = A1Palette.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val titleDescription = localized(TextKey.TitleValue, "title" to equippedTitle)
                    A1Tag(
                        equippedTitle,
                        A1TagTone.PURPLE,
                        icon = Icons.Rounded.WorkspacePremium,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = titleDescription }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            localized(TextKey.PlayerCodeValue, "code" to profile.playerCode),
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                            color = A1Palette.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onCopyCode, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = localized(TextKey.CopyPlayerCode),
                                modifier = Modifier.size(20.dp),
                                tint = A1Palette.Ink
                            )
                        }
                    }
                }
            }
            if (canEdit) {
                ArcadeHeaderIconButton(
                    onClick = onEdit,
                    enabled = !isSaving,
                    modifier = Modifier.align(Alignment.TopEnd).testTag("profile_edit")
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = localized(TextKey.EditProfile),
                        modifier = Modifier.size(24.dp),
                        tint = A1Palette.Ink
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    localized(TextKey.Level, "level" to progression.level),
                    modifier = Modifier.testTag("profile_level_label"),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp),
                    color = A1Palette.Ink
                )
                Text(
                    if (isMaxLevel) {
                        localized(TextKey.MaximumLevel)
                    } else {
                        "${groupThousands(progression.currentLevelExperience.toLong())} / " +
                            "${groupThousands(progression.nextLevelExperience.toLong())} XP"
                    },
                    modifier = Modifier.testTag("profile_xp_count"),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                    color = A1Palette.Muted,
                    textAlign = TextAlign.End
                )
            }
            A1ProgressBar(
                progress = if (isMaxLevel) 1f else {
                    progression.currentLevelExperience.toFloat() / progression.nextLevelExperience.coerceAtLeast(1)
                },
                fill = listOf(A1Palette.Blue1, A1Palette.Blue2),
                height = 16.dp
            )
            if (!isMaxLevel) {
                Text(
                    localized(
                        TextKey.XpToNextLevel,
                        "xp" to groupThousands((progression.nextLevelExperience - progression.currentLevelExperience).coerceAtLeast(0).toLong()),
                        "level" to progression.level + 1
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted
                )
            }
            sessionStartedAtMillis.takeIf { !isExternalProfile }?.let { CurrentSessionDuration(it) }
        }
        ProfileTierBox(profile.statistics.eloRating)
    }
}

/** Ô hạng vàng: khiên, "Vàng · 1742 Elo", thanh tiến độ trong bậc và số Elo còn thiếu. */
@Composable
private fun ProfileTierBox(rating: Int) {
    val tier = rankedTierFor(rating)
    val next = nextRankedTier(rating)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_tier_card")
            .background(A1Palette.Highlight, shape)
            .border(3.dp, A1Palette.Yellow2, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = 56.dp)
                .clip(ClanShieldShape)
                .background(Brush.verticalGradient(listOf(A1Palette.Yellow1, A1Palette.Yellow3)), ClanShieldShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = A1Palette.YellowDeep, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${localizedRankedTierName(tier)} · $rating Elo",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                color = A1Palette.Ink
            )
            A1ProgressBar(
                progress = ratingProgressWithinTier(rating),
                fill = listOf(A1Palette.Yellow1, A1Palette.Yellow2),
                height = 14.dp
            )
            Text(
                next?.let {
                    localized(TextKey.EloToNextTier, "count" to (it.minimumRating - rating), "tier" to localizedRankedTierName(it))
                } ?: localized(TextKey.HighestTierReached),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = A1TagTone.YELLOW.content
            )
        }
    }
}

/** Màu ô icon của từng dòng (".ico" trong thiết kế). */
private enum class ProfileTile(val top: Color, val bottom: Color, val edge: Color, val icon: Color) {
    BLUE(A1Palette.Blue1, A1Palette.Blue2, A1Palette.BlueDeep, Color.White),
    PURPLE(A1Palette.Purple1, A1Palette.Purple2, A1Palette.PurpleDeep, Color.White),
    YELLOW(A1Palette.Yellow1, A1Palette.Yellow2, A1Palette.YellowBorder, A1Palette.YellowBorder),
    GREEN(A1Palette.Green1, A1Palette.Green2, A1Palette.GreenDeep, Color.White),
    INK(Color(0xFF24508A), A1Palette.Ink, A1Palette.BlueDeep, Color.White),
    WHITE(Color.White, Color.White, A1Palette.Line, A1Palette.Ink)
}

private class ProfileMenuItem(
    val tag: String,
    val icon: ImageVector,
    val title: String,
    val tile: ProfileTile,
    val subtitle: String? = null,
    val badge: Long = 0,
    val showChevron: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun ProfileSkyTitle(title: String) {
    A1OutlinedText(
        title,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
        modifier = Modifier.padding(start = 4.dp, top = 10.dp).semantics { heading() }
    )
}

/** Một thẻ trắng chứa các dòng, ngăn bằng gạch đứt. */
@Composable
private fun ProfileMenuCard(items: List<ProfileMenuItem>) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().a1Card()) {
        items.forEachIndexed { index, item ->
            ProfileMenuRow(item, showDivider = index < items.lastIndex)
        }
    }
}

@Composable
private fun ProfileMenuRow(item: ProfileMenuItem, showDivider: Boolean) {
    val badgeDescription = if (item.badge > 0) localized(TextKey.BadgeNewCount, "count" to item.badge) else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .testTag(item.tag)
            .clickable(role = Role.Button, onClick = item.onClick)
            .then(if (showDivider) Modifier.drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    A1Palette.SurfaceAlt2,
                    Offset(3.dp.toPx(), y),
                    Offset(size.width - 3.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            } else Modifier)
            .semantics(mergeDescendants = true) { badgeDescription?.let { stateDescription = it } }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(Brush.verticalGradient(listOf(item.tile.top, item.tile.bottom)), RoundedCornerShape(12.dp))
                .border(2.5.dp, item.tile.edge, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = item.tile.icon, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                color = A1Palette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.badge > 0) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
                    .background(A1TagTone.RED.background, CircleShape)
                    .border(2.dp, A1TagTone.RED.border, CircleShape)
                    .padding(horizontal = 6.dp)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (item.badge > 99) "99+" else item.badge.toString(),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = A1TagTone.RED.content
                )
            }
        }
        if (item.showChevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = A1Palette.Ink,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** Thẻ "Vùng nguy hiểm": nền hồng nhạt, viền đỏ, nút Xóa tài khoản đỏ. */
@Composable
private fun ProfileDangerZone(onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_danger_zone")
            .a1Card(background = Color(0xFFFFF4F4), border = Color(0xFFC62A35))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            localized(TextKey.DangerZone).uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
            color = A1Palette.RedText,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            localized(TextKey.DeleteAccountShortWarning),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = A1Palette.Ink
        )
        Spacer(Modifier.height(4.dp))
        ArcadeActionButton(
            label = localized(TextKey.DeleteAccount),
            onClick = onDelete,
            icon = Icons.Rounded.DeleteForever,
            style = ArcadeActionStyle.DANGER,
            modifier = Modifier.fillMaxWidth().testTag("profile_delete_account")
        )
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
    onOpenNotifications: () -> Unit,
    onOpenRewardEvent: ((String) -> Unit)? = null,
    missionState: MissionState = MissionState(),
    onRefreshMissions: () -> Unit = {},
    onClaimMissionInstance: (String) -> Unit = {},
    onDismissMissionReward: () -> Unit = {},
    checkInMonth: String? = null
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
            onDismiss = onCloseMatchDetail,
            onOpenRewardEvent = onOpenRewardEvent
        )
    }

    // Các màn con A1 (Thống kê, Lịch sử thi đấu, Bộ sưu tập, Lịch sử tài sản): chỉ có nút quay lại + tiêu đề như thiết kế;
    // tải lại bằng cách kéo xuống.
    val a1Section = section == ProfileSection.STATISTICS || section == ProfileSection.RECENT_MATCHES ||
        section == ProfileSection.COLLECTION || section == ProfileSection.WALLET
    Column(modifier = Modifier.fillMaxSize().testTag("profile_section_screen:${section.name}")) {
        FastToWinHeader(
            title = when (section) {
                ProfileSection.STATISTICS -> localized(TextKey.StatisticsShort)
                ProfileSection.WALLET -> localized(TextKey.WalletHistory)
                ProfileSection.DAILY_CHECK_IN -> localized(TextKey.CheckIn)
                ProfileSection.MISSIONS -> localized(TextKey.MissionsTitle)
                ProfileSection.COLLECTION -> localized(TextKey.CollectionTitle)
                ProfileSection.RECENT_MATCHES -> localized(TextKey.MatchHistory)
            },
            gold = state.profile?.progression?.gold ?: 0,
            gems = state.profile?.progression?.gems ?: 0,
            unreadNotifications = state.unreadNotificationCount,
            onNotifications = onOpenNotifications,
            onBack = onBack,
            showBalances = !a1Section && section != ProfileSection.DAILY_CHECK_IN && section != ProfileSection.MISSIONS,
            showNotifications = !a1Section,
            actions = {
                if (!a1Section) {
                    HeaderRefreshAction(
                        isRefreshing = isLoading && state.equippingCosmeticId == null,
                        onRefresh = onRefresh
                    )
                }
            }
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
                        .testTag("profile_section_scroll")
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                        )
                        // Thiết kế A1 cách mép 20dp trên điện thoại (ResponsiveScreen đã chừa 16dp).
                        .padding(horizontal = if (a1Section) 4.dp else 0.dp)
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (section != ProfileSection.DAILY_CHECK_IN && !a1Section) ProfileSectionHero(section)
                    when (section) {
                        ProfileSection.STATISTICS -> A1StatisticsContent(profile)
                        ProfileSection.WALLET -> A1WalletHistoryContent(state.walletTransactions)
                        ProfileSection.DAILY_CHECK_IN -> DailyCheckInDetailsContent(
                            checkIn = profile.progression.dailyCheckIn,
                            initialMonth = checkInMonth
                        )
                        ProfileSection.MISSIONS -> ServerMissionList(
                            state = missionState,
                            onRefresh = onRefreshMissions,
                            onClaim = onClaimMissionInstance,
                            onDismissReward = onDismissMissionReward
                        )
                        ProfileSection.COLLECTION -> A1CollectionContent(
                            state = state,
                            profile = profile,
                            canEdit = canEdit,
                            isLoading = isLoading,
                            onEquipCosmetics = onEquipCosmetics
                        )
                        ProfileSection.RECENT_MATCHES -> A1MatchHistoryContent(
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
        ProfileSection.DAILY_CHECK_IN -> return
        ProfileSection.MISSIONS -> Triple(
            localized(TextKey.MissionsTitle),
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
private fun walletSourceLabel(sourceType: String): String = localized(walletSourceTextKey(sourceType))

internal fun walletSourceTextKey(sourceType: String): TextKey = when (sourceType) {
    "DAILY_CHECK_IN" -> TextKey.DailyCheckInSource
    "MISSION" -> TextKey.MissionRewardSource
    "MATCH" -> TextKey.MatchRewardSource
    "CLAN_QUEST" -> TextKey.ClanMissionSource
    "CLAN_WEEKLY_RACE" -> TextKey.ClanRaceWalletSource
    "COSMETIC_PURCHASE" -> TextKey.CosmeticPurchaseSource
    "TOURNAMENT_ENTRY" -> TextKey.TournamentEntrySource
    "TOURNAMENT_PRIZE" -> TextKey.TournamentPrizeSource
    "SEASON_REWARD" -> TextKey.SeasonRewardSource
    "STORE_PURCHASE" -> TextKey.GemTopUpSource
    "STORE_REFUND" -> TextKey.StoreRefundSource
    "CLAN_DONATION" -> TextKey.ClanDonationSource
    "CLAN_CREATION" -> TextKey.ClanCreationSource
    "ADMIN_GIFT" -> TextKey.AdminGiftSource
    "COMPENSATION" -> TextKey.CompensationSource
    "GOLD_EXCHANGE" -> TextKey.GoldExchangeSource
    "PLAY_CREDIT_EXCHANGE" -> TextKey.PlayCreditExchangeSource
    "GIFT_CODE" -> TextKey.GiftCode
    "ACHIEVEMENT" -> TextKey.AchievementRewardSource
    "TOURNAMENT_ENTRY_REFUND" -> TextKey.TournamentEntryRefundSource
    "TOURNAMENT_ENTRY_REVERSED" -> TextKey.TournamentEntryReversedSource
    "TOURNAMENT_MAINTENANCE_REFUND" -> TextKey.TournamentMaintenanceRefundSource
    else -> TextKey.WalletAdjustmentSource
}

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
internal fun localizedAchievementTitle(achievement: com.hienthai.fastowin.protocol.AchievementSnapshot): String =
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
internal fun localizedAchievementDescription(achievement: com.hienthai.fastowin.protocol.AchievementSnapshot): String =
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

/** "Đăng xuất?": ô icon xanh nổi, nút vàng ĐĂNG XUẤT và nút trắng Ở lại. */
@Composable
private fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ArcadeDialog(
        title = localized(TextKey.LogoutTitle),
        subtitle = localized(TextKey.LogoutDescription),
        onDismissRequest = onDismiss,
        heroIcon = Icons.AutoMirrored.Rounded.Logout,
        heroTone = A1HeroTone.BLUE,
        modifier = Modifier.testTag("logout_confirmation_dialog")
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ArcadeActionButton(
                label = localized(TextKey.LogoutAction),
                onClick = onConfirm,
                style = ArcadeActionStyle.GOLD,
                large = true,
                modifier = Modifier.fillMaxWidth().testTag("confirm_logout")
            )
            ArcadeActionButton(
                label = localized(TextKey.StayHere),
                onClick = onDismiss,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** "Xóa tài khoản?": ô icon đỏ, ghi chú viền đỏ, nút đỏ XÓA VĨNH VIỄN và Hủy (App Store 5.1.1(v)). */
@Composable
private fun DeleteAccountDialog(
    isLoading: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ArcadeDialog(
        title = localized(TextKey.DeleteAccountConfirmTitle),
        subtitle = localized(TextKey.DeleteAccountConfirmBody),
        onDismissRequest = onDismiss,
        heroIcon = Icons.Rounded.DeleteForever,
        heroTone = A1HeroTone.RED,
        modifier = Modifier.testTag("delete_account_dialog")
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF4F4), RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFFF08A8A), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = A1Palette.RedText, modifier = Modifier.size(20.dp))
                Text(
                    localized(TextKey.DeleteAccountNote),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Ink
                )
            }
            error?.let { Text(it, color = A1Palette.RedText, fontWeight = FontWeight.SemiBold) }
            ArcadeActionButton(
                label = if (isLoading) localized(TextKey.DeletingAccount) else localized(TextKey.DeleteAccountConfirmAction),
                onClick = onConfirm,
                enabled = !isLoading,
                style = ArcadeActionStyle.DANGER,
                large = true,
                modifier = Modifier.fillMaxWidth().testTag("confirm_delete_account")
            )
            ArcadeActionButton(
                label = localized(TextKey.Cancel),
                onClick = onDismiss,
                enabled = !isLoading,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Màn toàn trang trên nền trời (Thiết bị đăng nhập, Bảo mật tài khoản): nút quay lại + tiêu đề. */
@Composable
private fun AccountFullScreen(
    title: String,
    onBack: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onBack, properties = arcadeDialogProperties()) {
        // Nền trời phủ cả thanh trạng thái/điều hướng; lớp nhập liệu bên trong mới trừ lề hệ thống và bàn phím.
        A1SkyBackdrop(Modifier.fillMaxSize().testTag(tag), showHills = false) {
            NativeDialogInputScope {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    FastToWinHeader(
                        title = title,
                        gold = 0,
                        gems = 0,
                        unreadNotifications = 0,
                        onNotifications = {},
                        onBack = onBack,
                        applySafeDrawingInset = false,
                        showBalances = false,
                        showNotifications = false
                    )
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(
                            Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            content = content
                        )
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
    onChangePassword: (String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordError = accountPasswordError(newPassword)
    val confirmationError = accountPasswordConfirmationError(newPassword, confirmPassword)
    val passwordUnchanged = currentPassword.isNotEmpty() && currentPassword == newPassword
    AccountFullScreen(
        title = localized(TextKey.AccountSecurityTitle),
        onBack = onDismiss,
        tag = "account_security_screen"
    ) {
        Column(
            Modifier.fillMaxWidth().a1Card().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                localized(TextKey.ChangePassword),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = A1Palette.Ink,
                modifier = Modifier.semantics { heading() }
            )
            SecurePasswordField(currentPassword, localized(TextKey.CurrentPassword)) { currentPassword = it }
            SecurePasswordField(newPassword, localized(TextKey.NewPasswordLabel)) { newPassword = it }
            Text(
                if (newPassword.isNotEmpty() && passwordError != null) localized(passwordError)
                else localized(TextKey.PasswordHint, "minimum" to MIN_ACCOUNT_PASSWORD_LENGTH),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (newPassword.isNotEmpty() && passwordError != null) A1Palette.RedText else A1Palette.Muted
            )
            SecurePasswordField(confirmPassword, localized(TextKey.ConfirmNewPasswordLabel)) { confirmPassword = it }
            confirmationError?.let { Text(localized(it), color = A1Palette.RedText, fontWeight = FontWeight.SemiBold) }
            if (passwordUnchanged) {
                Text(localized(TextKey.NewPasswordMustDiffer), color = A1Palette.RedText, fontWeight = FontWeight.SemiBold)
            }
            error?.let { Text(it, color = A1Palette.RedText, fontWeight = FontWeight.SemiBold) }
            ArcadeActionButton(
                label = if (isLoading) localized(TextKey.UpdatingPassword) else localized(TextKey.UpdateAction),
                onClick = { onChangePassword(currentPassword, newPassword) },
                enabled = !isLoading && currentPassword.isNotBlank() && passwordError == null &&
                    confirmationError == null && confirmPassword.isNotEmpty() && !passwordUnchanged,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("account_security_submit"),
                style = ArcadeActionStyle.GOLD,
                large = true
            )
        }
    }
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

    AccountFullScreen(
        title = localized(TextKey.LoginDevicesTitle),
        onBack = onDismiss,
        tag = "account_sessions_screen"
    ) {
        A1OutlinedText(
            localized(TextKey.LoginDevicesDescription),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            outlineWidth = 2.5.dp
        )
        if (isLoading && sessions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (sessions.isEmpty()) {
            Text(
                localized(TextKey.NoActiveSessions),
                modifier = Modifier.fillMaxWidth().a1Card().padding(18.dp),
                color = A1Palette.Ink,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Column(Modifier.fillMaxWidth().a1Card()) {
                // Thiết bị hiện tại luôn đứng đầu.
                val ordered = sessions.sortedByDescending { it.isCurrent }
                ordered.forEachIndexed { index, session ->
                    SessionRow(
                        session = session,
                        showDivider = index < ordered.lastIndex,
                        enabled = !isLoading,
                        onRevoke = { sessionPendingRevoke = session }
                    )
                }
            }
        }
        notice?.let { ShopNotice(it, isError = false) }
        error?.let { ShopNotice(it, isError = true) }
        ArcadeActionButton(
            label = localized(TextKey.LogoutAllDevices),
            onClick = { confirmRevokeAll = true },
            enabled = !isLoading && sessions.isNotEmpty(),
            icon = Icons.AutoMirrored.Rounded.Logout,
            modifier = Modifier.fillMaxWidth().testTag("account_sessions_revoke_all"),
            style = ArcadeActionStyle.DANGER
        )
    }

    sessionPendingRevoke?.let { session ->
        ArcadeDialog(
            title = localized(if (session.isCurrent) TextKey.RevokeCurrentDeviceTitle else TextKey.RevokeDeviceTitle),
            subtitle = if (session.isCurrent) {
                localized(TextKey.RevokeCurrentDeviceDescription)
            } else {
                localized(TextKey.RevokeOtherDeviceDescription, "device" to sessionDeviceLabel(session.devicePlatform))
            },
            onDismissRequest = { sessionPendingRevoke = null },
            heroIcon = Icons.AutoMirrored.Rounded.Logout,
            heroTone = A1HeroTone.RED
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
    if (confirmRevokeAll) {
        ArcadeDialog(
            title = localized(TextKey.LogoutAllDevicesTitle),
            subtitle = localized(TextKey.LogoutAllDevicesDescription),
            onDismissRequest = { confirmRevokeAll = false },
            heroIcon = Icons.AutoMirrored.Rounded.Logout,
            heroTone = A1HeroTone.RED
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

/** Một phiên đăng nhập: ô icon theo nền tảng, tên + thời gian, nhãn "Hiện tại" hoặc nút Đăng xuất. */
@Composable
private fun SessionRow(session: AccountSessionSnapshot, showDivider: Boolean, enabled: Boolean, onRevoke: () -> Unit) {
    val deviceLabel = sessionDeviceLabel(session.devicePlatform)
    val (tileTop, tileBottom, tileEdge) = when {
        session.isCurrent -> Triple(A1Palette.Green1, A1Palette.Green2, A1Palette.GreenDeep)
        session.devicePlatform.equals("android", ignoreCase = true) -> Triple(A1Palette.Blue1, A1Palette.Blue2, A1Palette.BlueDeep)
        else -> Triple(Color(0xFF24508A), A1Palette.Ink, A1Palette.BlueDeep)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_session:${session.sessionId}")
            .then(if (showDivider) Modifier.drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    A1Palette.SurfaceAlt2, Offset(3.dp.toPx(), y), Offset(size.width - 3.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            } else Modifier)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Brush.verticalGradient(listOf(tileTop, tileBottom)), RoundedCornerShape(12.dp))
                .border(2.5.dp, tileEdge, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when (session.devicePlatform?.lowercase()) {
                    "android", "ios" -> Icons.Rounded.Smartphone
                    else -> Icons.Rounded.Computer
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (session.isCurrent) localized(TextKey.SessionThisDevice, "device" to deviceLabel) else deviceLabel,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                color = A1Palette.Ink
            )
            Text(
                localized(TextKey.ActivityTime, "time" to relativeSessionTime(session.lastSeenAtEpochMillis)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted
            )
        }
        if (session.isCurrent) {
            A1Tag(localized(TextKey.SessionCurrentTag), A1TagTone.GREEN)
        } else {
            ArcadeActionButton(
                label = localized(TextKey.Logout),
                onClick = onRevoke,
                enabled = enabled,
                style = ArcadeActionStyle.OUTLINE,
                compact = true,
                modifier = Modifier.widthIn(min = 96.dp, max = 124.dp).testTag("account_session_revoke:${session.sessionId}")
            ) {
                Text(
                    localized(TextKey.Logout),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = A1Palette.RedText,
                    maxLines = 1
                )
            }
        }
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
private fun SecurePasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= MAX_ACCOUNT_PASSWORD_LENGTH) onValueChange(it) },
        label = label,
        leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = A1Palette.Ink) },
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
internal fun DailyCheckInContent(
    checkIn: DailyCheckInSnapshot,
    isClaiming: Boolean,
    onClaim: () -> Unit,
    onOpenDetails: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("daily_check_in_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DailyCheckInCalendar(checkIn, onOpenDetails)
        if (!checkIn.claimedToday) {
            ArcadeActionButton(
                label = if (isClaiming) localized(TextKey.Claiming) else localized(
                    TextKey.ClaimDayReward,
                    "day" to (checkIn.todayDate?.toProfileCalendarDate()?.day ?: checkIn.cycleDay)
                ),
                onClick = onClaim,
                enabled = !isClaiming,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth().testTag("daily_check_in_claim")
            )
        }
    }
}

@Composable
private fun DailyCheckInCalendar(checkIn: DailyCheckInSnapshot, onOpenDetails: (String) -> Unit) {
    val today = checkIn.todayDate?.toProfileCalendarDate() ?: return
    val currentMonth = ProfileCalendarMonth(today.year, today.month)
    val earliestMonth = currentMonth.shift(-11)
    var monthOffset by rememberSaveable(currentMonth.year, currentMonth.month) { mutableStateOf(0) }
    val visibleMonth = currentMonth.shift(monthOffset)
    val cells = profileCalendarCells(visibleMonth)
    val monthlyRewards = remember(visibleMonth, checkIn) { monthlyCheckInRewards(visibleMonth, checkIn) }

    ArcadePanel(
        modifier = Modifier.fillMaxWidth().testTag("daily_check_in_calendar"),
        accent = ArcadeGold
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    localized(TextKey.CheckInHistory),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Surface(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    shape = RoundedCornerShape(999.dp),
                    color = ArcadeGold.copy(alpha = 0.16f)
                ) {
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
            CheckInMonthNavigation(
                visibleMonth, earliestMonth, currentMonth,
                onPrevious = { monthOffset-- },
                onNext = { monthOffset++ }
            )
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
                                val entry = monthlyRewards[day - 1]
                                val checked = entry.claimed
                                val isToday = entry.isToday
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckInStatusLegend(
                    label = localized(TextKey.CheckedIn),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onOpenDetails(visibleMonth.routeMonth) },
                    modifier = Modifier.testTag("daily_check_in_details")
                ) {
                    Text(localized(TextKey.CheckInDetails))
                }
            }
        }
    }
}

@Composable
private fun CheckInMonthNavigation(
    month: ProfileCalendarMonth,
    earliestMonth: ProfileCalendarMonth,
    currentMonth: ProfileCalendarMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrevious, enabled = month > earliestMonth) {
            Icon(Icons.Default.ChevronLeft, localized(TextKey.PreviousMonth))
        }
        Text(
            localized(TextKey.MonthYear, "month" to month.month, "year" to month.year),
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext, enabled = month < currentMonth) {
            Icon(Icons.Default.ChevronRight, localized(TextKey.NextMonth))
        }
    }
}

@Composable
private fun CheckInStatusLegend(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DailyCheckInDetailsContent(checkIn: DailyCheckInSnapshot, initialMonth: String?) {
    val date = checkIn.todayDate?.toProfileCalendarDate()
    if (date != null) {
        val currentMonth = ProfileCalendarMonth(date.year, date.month)
        val earliestMonth = currentMonth.shift(-11)
        val startMonth = initialMonth?.let { "$it-01".toProfileCalendarDate() }
            ?.let { ProfileCalendarMonth(it.year, it.month).coerceIn(earliestMonth, currentMonth) }
            ?: currentMonth
        var monthOffset by rememberSaveable(startMonth.year, startMonth.month) { mutableStateOf(0) }
        val month = startMonth.shift(monthOffset).coerceIn(earliestMonth, currentMonth)
        val rewards = remember(month, checkIn) { monthlyCheckInRewards(month, checkIn) }
        Column(
            modifier = Modifier.fillMaxWidth().testTag("daily_check_in_month_rewards"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                localized(TextKey.CheckInMonthlyRewards),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            CheckInMonthNavigation(
                month, earliestMonth, currentMonth,
                onPrevious = { monthOffset-- },
                onNext = { monthOffset++ }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckInStatusLegend(localized(TextKey.Claimed), ArcadePalette.Mint400)
                CheckInStatusLegend(localized(TextKey.CheckInMissed), ArcadePalette.Coral400)
            }
            if (month == currentMonth && date.day < rewards.size) {
                Text(
                    localized(TextKey.CheckInForecast),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MonthlyCheckInRewardGrid(rewards)
        }
    }
    DailyCheckInMilestones(bestStreak = checkIn.bestStreak, totalCheckIns = checkIn.totalCheckIns)
}

@Composable
private fun MonthlyCheckInRewardGrid(days: List<CheckInRewardDay>) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = { days.forEach { CheckInRewardCard(it) } }
    ) { cards, constraints ->
        val width = constraints.maxWidth
        val columns = (width.toDp() / (115.dp * fontScale.coerceAtLeast(1f))).toInt().coerceIn(2, 5)
        val gap = 8.dp.roundToPx()
        val cardWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val minHeight = 72.dp.roundToPx()
        // Choose one height for the whole month at the current column width and text scale.
        val cardHeight = cards.fold(minHeight) { height, card ->
            maxOf(height, card.minIntrinsicHeight(cardWidth))
        }
        val cardConstraints = Constraints.fixed(cardWidth, cardHeight)
        val placedCards = cards.map { it.measure(cardConstraints) }
        val rows = (cards.size + columns - 1) / columns
        val height = rows * cardHeight + (rows - 1).coerceAtLeast(0) * gap
        layout(width, constraints.constrainHeight(height)) {
            placedCards.forEachIndexed { index, card ->
                card.placeRelative(
                    x = (index % columns) * (cardWidth + gap),
                    y = (index / columns) * (cardHeight + gap)
                )
            }
        }
    }
}

@Composable
private fun CheckInRewardCard(entry: CheckInRewardDay) {
    val reward = entry.reward
    val status = when {
        entry.claimed -> TextKey.Claimed
        reward == null && !entry.isToday -> TextKey.CheckInMissed
        else -> null
    }
    val statusLabel = status?.let { localized(it) }
    ArcadePanel(
        modifier = Modifier.testTag("daily_check_in_reward:${entry.day}")
            .semantics { if (statusLabel != null) stateDescription = statusLabel },
        accent = if (entry.isToday) ArcadeGold else ArcadePalette.Blue700
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${localized(TextKey.DayLabel)} ${entry.day}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (entry.isToday && !entry.claimed) {
                    Text(
                        localized(TextKey.CheckInToday),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcadeGold
                    )
                }
                if (reward != null) {
                    RewardAmount(localized(TextKey.RewardGoldDescription, "count" to reward.gold), RewardCurrency.GOLD)
                    RewardAmount(localized(TextKey.RewardXpDescription, "count" to reward.xp), RewardCurrency.XP)
                    if (reward.gems > 0) {
                        RewardAmount(localized(TextKey.RewardGemsDescription, "count" to reward.gems), RewardCurrency.GEM)
                    }
                } else if (entry.claimed) {
                    Text(
                        localized(TextKey.CheckInRewardUnavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (status != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(8.dp)
                        .background(if (entry.claimed) ArcadePalette.Mint400 else ArcadePalette.Coral400, CircleShape)
                )
            }
        }
    }
}

internal data class ProfileCalendarDate(val year: Int, val month: Int, val day: Int)

internal data class ProfileCalendarMonth(val year: Int, val month: Int) : Comparable<ProfileCalendarMonth> {
    val routeMonth: String get() = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"

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
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
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
internal fun ProtocolGameMode.localizedDisplayName(): String = localized(when (this) {
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
private fun MatchDetailDialog(
    detail: MatchDetailSnapshot?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenRewardEvent: ((String) -> Unit)? = null
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
                    com.hienthai.fastowin.ui.components.RewardBreakdownCard(detail.summary.rewardBreakdown, persisted = true, onOpenEvent = onOpenRewardEvent)
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
                    localized(TextKey.Versus),
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
internal fun CosmeticSnapshot.localizedName(): String =
    (if (type == CosmeticType.FRAME) catalogAvatarFrameName(id) else null)
        ?: localizedNetworkText(nameKey, emptyMap(), name)

@Composable
internal fun CosmeticSnapshot.localizedUnlockRequirement(): String? =
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
