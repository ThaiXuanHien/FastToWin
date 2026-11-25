package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.state.MissionState
import com.hienthai.fastowin.ui.components.ServerMissionList

import com.hienthai.fastowin.ui.components.canReportUgcTarget
import com.hienthai.fastowin.ui.components.UgcClanReportAction
import com.hienthai.fastowin.protocol.UgcTargetType

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.hienthai.fastowin.ui.components.AppOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.CLAN_LOGOS
import com.hienthai.fastowin.protocol.clanLogoRequiredLevel
import com.hienthai.fastowin.protocol.isClanLogoUnlocked
import com.hienthai.fastowin.protocol.CLAN_CREATION_GEM_COST
import com.hienthai.fastowin.protocol.CLAN_CREATION_GOLD_COST
import com.hienthai.fastowin.protocol.CLAN_CREATION_MIN_LEVEL
import com.hienthai.fastowin.protocol.ClanDonationCurrency
import com.hienthai.fastowin.protocol.ClanJoinRequestSnapshot
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedQuantity
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_clan_crest
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeIconHero
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.ClanEmblemAccents
import com.hienthai.fastowin.ui.components.clanEmblemImageVector
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.FriendPresenceIndicator
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.state.ClanChatState
import com.hienthai.fastowin.protocol.CLAN_LOGO_DAILY_CHANGE_LIMIT
import com.hienthai.fastowin.protocol.CLAN_MAX_CO_LEADERS
import com.hienthai.fastowin.protocol.canModerateClan
import com.hienthai.fastowin.ui.components.NetworkImage
import com.hienthai.fastowin.ui.components.clanLogoImageUrl
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddModerator
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.ui.components.A1CoinIcon
import com.hienthai.fastowin.ui.components.A1Dimens
import com.hienthai.fastowin.ui.components.A1GemIcon
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.UgcReportMenuItem
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.theme.A1Palette
import com.hienthai.fastowin.protocol.FriendPresence


@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun ClanScreen(
    serverUrl: String,
    currentUserId: String?,
    myClanId: String?,
    clanList: List<ClanSummarySnapshot>,
    pendingJoinClanIds: Set<String>,
    currentClan: ClanSnapshot?,
    notice: String?,
    onCreateClan: (name: String, desc: String) -> Unit,
    onJoinClan: (String) -> Unit,
    onLeaveClan: () -> Unit,
    onSearch: (String?) -> Unit,
    onKickMember: (String, String) -> Unit,
    onRespondJoinRequest: (String, String, Boolean) -> Unit,
    onUpdateLogo: (String, String) -> Unit,
    onClaimQuest: (String) -> Unit,
    onViewClan: (String) -> Unit,
    onBack: () -> Unit,
    onDonate: (String, ClanDonationCurrency, Int) -> Unit = { _, _, _ -> },
    isDonationPending: Boolean = false,
    gold: Int = 0,
    gems: Int = 0,
    unreadNotifications: Int = 0,
    onOpenNotifications: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
    missionState: MissionState? = null,
    onRefreshMissions: () -> Unit = {},
    onClaimMissionInstance: (String) -> Unit = {},
    onDismissMissionReward: () -> Unit = {},
    onOpenRace: (() -> Unit)? = null,
    playerLevel: Int = 1,
    social: FriendsSnapshot = FriendsSnapshot(),
    isSocialLoading: Boolean = false,
    socialNotice: String? = null,
    socialError: String? = null,
    onSendFriendRequest: (String) -> Unit = {},
    onRespondFriendRequest: (String, Boolean) -> Unit = { _, _ -> },
    isSocialReady: Boolean = true,
    onRefreshSocial: () -> Unit = {},
    loadError: String? = null,
    viewedClanId: String? = null,
    clanChat: ClanChatState? = null,
    chatActions: ClanChatActions = ClanChatActions(),
    onUploadLogo: (clanId: String, bytes: ByteArray) -> Unit = { _, _ -> },
    onRemoveLogo: (clanId: String) -> Unit = {},
    onSetMemberRole: (clanId: String, memberId: String, role: ClanRole) -> Unit = { _, _, _ -> },
    isLogoUploading: Boolean = false,
    /** Hạng của bang trên bảng xếp hạng bang (ô "Xếp hạng"), null khi chưa có. */
    clanRank: Int? = null
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)
    val ownClanChat = clanChat?.takeIf { viewedClanId == null && myClanId != null && currentClan?.id == myClanId }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = if (showBackButton) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showBackButton) {
                FastToWinHeader(
                    title = localized(TextKey.Clan),
                    gold = gold,
                    gems = gems,
                    unreadNotifications = unreadNotifications,
                    onNotifications = onOpenNotifications,
                    onBack = onBack
                )
            }
        }
    ) { scaffoldPadding ->
        ResponsiveScreen(
            modifier = Modifier.padding(scaffoldPadding),
            // Members get chat beside the overview on wide screens.
            maxContentWidth = if (ownClanChat != null) 1180.dp else 920.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Column(contentModifier) {
                Box(Modifier.weight(1f)) {
                    val targetClanId = viewedClanId ?: myClanId
                    when {
                        targetClanId == null -> ClanDiscoveryView(
                            clanList = clanList,
                            pendingJoinClanIds = pendingJoinClanIds,
                            notice = notice,
                            onCreateClan = onCreateClan,
                            onJoinClan = onJoinClan,
                            onSearch = onSearch,
                            gold = gold,
                            gems = gems,
                            playerLevel = playerLevel,
                            modifier = Modifier.fillMaxSize()
                        )

                        currentClan?.id == targetClanId -> ClanDetailView(
                            serverUrl = serverUrl,
                            currentUserId = currentUserId,
                            clan = currentClan,
                            notice = notice,
                            loadError = loadError,
                            onRetryLoad = { onViewClan(targetClanId) },
                            onLeave = onLeaveClan,
                            onKickMember = onKickMember,
                            onRespondJoinRequest = onRespondJoinRequest,
                            onUpdateLogo = { logoId -> onUpdateLogo(currentClan.id, logoId) },
                            onClaimQuest = { onClaimQuest(currentClan.id) },
                            missionState = missionState,
                            onRefreshMissions = onRefreshMissions,
                            onClaimMissionInstance = onClaimMissionInstance,
                            onDismissMissionReward = onDismissMissionReward,
                            gold = gold,
                            gems = gems,
                            isDonationPending = isDonationPending,
                            onDonate = { currency, amount -> onDonate(currentClan.id, currency, amount) },
                            social = social,
                            isSocialLoading = isSocialLoading,
                            socialNotice = socialNotice,
                            socialError = socialError,
                            onSendFriendRequest = onSendFriendRequest,
                            onRespondFriendRequest = onRespondFriendRequest,
                            isSocialReady = isSocialReady,
                            onRefreshSocial = onRefreshSocial,
                            modifier = Modifier.fillMaxSize(),
                            clanChat = ownClanChat,
                            chatActions = chatActions,
                            onUploadLogo = { bytes -> onUploadLogo(currentClan.id, bytes) },
                            onRemoveLogo = { onRemoveLogo(currentClan.id) },
                            onSetMemberRole = { memberId, role -> onSetMemberRole(currentClan.id, memberId, role) },
                            isLogoUploading = isLogoUploading,
                            onOpenRace = onOpenRace.takeIf { viewedClanId == null },
                            clanRank = clanRank.takeIf { viewedClanId == null }
                        )

                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (loadError == null) {
                                    CircularProgressIndicator(color = ArcadePalette.Gold500)
                                } else {
                                    ClanLoadError(loadError, onRetry = { onViewClan(targetClanId) })
                                }
                            }
                            LaunchedEffect(targetClanId) { if (viewedClanId == null) onViewClan(targetClanId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClanLoadError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        ArcadeActionButton(
            label = localized(TextKey.Retry),
            onClick = onRetry,
            modifier = Modifier.testTag("clan_retry")
        )
    }
}

@Composable
private fun ClanDiscoveryView(
    clanList: List<ClanSummarySnapshot>,
    pendingJoinClanIds: Set<String>,
    notice: String?,
    onCreateClan: (String, String) -> Unit,
    onJoinClan: (String) -> Unit,
    onSearch: (String?) -> Unit,
    gold: Int,
    gems: Int,
    playerLevel: Int,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var visibleClanCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleClans = clanList.take(visibleClanCount)
    val searchActionDescription = localized(TextKey.SearchClanAction)

    LazyColumn(
        modifier = modifier.testTag("clan_discovery_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "clan_hero") { ClanDiscoveryHero() }

        notice?.let { item(key = "clan_notice") { ClanNotice(it) } }

        item(key = "create_clan") {
            ArcadeActionButton(
                label = localized(TextKey.CreateClan),
                onClick = { showCreateDialog = true },
                icon = Icons.Rounded.AddModerator,
                style = ArcadeActionStyle.GOLD,
                large = true,
                modifier = Modifier.fillMaxWidth().testTag("open_create_clan")
            )
        }

        // A native background editor would otherwise paint over the modal and
        // remain reachable by keyboard. Keep its lazy item observing modal state
        // so the cached composition disposes the editor, retaining the query.
        item(key = "clan_search") {
            if (!showCreateDialog) {
                val runSearch = {
                    visibleClanCount = DEFAULT_ARCADE_PAGE_SIZE
                    onSearch(searchQuery.trim().takeIf(String::isNotEmpty))
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = localized(TextKey.SearchClanPlaceholder),
                    modifier = Modifier.fillMaxWidth().testTag("clan_search_field"),
                    singleLine = true,
                    leadingIcon = {
                        IconButton(
                            onClick = runSearch,
                            modifier = Modifier.semantics { contentDescription = searchActionDescription }
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = A1Palette.Ink)
                        }
                    }
                )
            }
        }

        if (clanList.isEmpty()) {
            item(key = "clan_empty") {
                ArcadeEmptyState(
                    illustration = Res.drawable.arcade_clan_crest,
                    title = localized(TextKey.NoClansFound),
                    description = localized(TextKey.NoClansFoundDescription)
                )
            }
        } else {
            items(visibleClans, key = { "clan:${it.id}" }) { clan ->
                ClanSummaryCard(
                    clan = clan,
                    isPending = clan.id in pendingJoinClanIds,
                    onJoin = { onJoinClan(clan.id) }
                )
            }
            item(key = "clan_discovery_load_more") {
                ArcadeLoadMoreButton(
                    visibleItemCount = visibleClans.size,
                    totalItemCount = clanList.size,
                    onLoadMore = {
                        visibleClanCount = nextArcadePageItemCount(visibleClanCount, clanList.size)
                    },
                    testTag = "clan_discovery_load_more"
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateClanDialog(
            gold = gold,
            gems = gems,
            playerLevel = playerLevel,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                onCreateClan(name, description)
                showCreateDialog = false
            }
        )
    }
}

/** Thẻ giới thiệu ở đầu màn tìm bang: khiên xanh + "Sát cánh tranh tài". */
@Composable
private fun ClanDiscoveryHero() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .a1Card(background = Color.Transparent)
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFE6F0FF))), RoundedCornerShape(A1Dimens.CardRadius))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ClanShieldEmblem("sword", Modifier.size(width = 72.dp, height = 82.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(localized(TextKey.ClanTogetherTitle), style = MaterialTheme.typography.headlineSmall, color = A1Palette.Ink)
            Text(
                localized(TextKey.ClanTogetherDescription),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted
            )
        }
    }
}

/** Thẻ bang trong danh sách (".card"): khiên, tên, "Cấp · thành viên · điểm", nút Xin vào / nhãn Đang chờ, menu ba chấm. */
@Composable
private fun ClanSummaryCard(
    clan: ClanSummarySnapshot,
    isPending: Boolean,
    onJoin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .a1Card()
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
            .testTag("clan_card:${clan.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClanLogoMark(logoId = clan.logoId, modifier = Modifier.size(width = 56.dp, height = 64.dp), clanId = clan.id, logoRevision = clan.logoRevision)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                clan.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = A1Palette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                localized(
                    TextKey.ClanSummaryShort,
                    "level" to clan.level,
                    "members" to "${clan.memberCount}/${clan.maxMembers}",
                    "points" to formatNumber(clan.trophies)
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted
            )
        }
        ClanJoinButton(isPending, onJoin)
        if (canReportUgcTarget(UgcTargetType.CLAN, clan.id)) {
            ClanOverflowMenu(localized(TextKey.MoreActions)) { dismiss ->
                UgcReportMenuItem(UgcTargetType.CLAN, clan.id, clan.name, dismiss)
            }
        }
    }
}

@Composable
private fun ClanJoinButton(isPending: Boolean, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    if (isPending) {
        // Nhãn "Đang chờ" thay cho nút: đọc như một nút đã tắt.
        A1Tag(
            localized(TextKey.PendingApproval),
            A1TagTone.YELLOW,
            modifier = modifier.semantics(mergeDescendants = true) {
                role = Role.Button
                disabled()
            },
            large = true
        )
    } else {
        ArcadeActionButton(
            label = localized(TextKey.RequestToJoin),
            onClick = onJoin,
            style = ArcadeActionStyle.SUCCESS,
            compact = true,
            modifier = modifier.width(100.dp)
        )
    }
}

@Composable
fun CreateClanDialog(
    gold: Int,
    gems: Int,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
    playerLevel: Int = 1
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    ArcadeDialog(
        title = localized(TextKey.CreateClanTitle),
        subtitle = localized(TextKey.CreateClanDescription),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create_clan_dialog"),
        scrollableContent = true
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = localized(TextKey.ClanName),
            leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("create_clan_name")
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(160) },
            label = localized(TextKey.ClanDescription),
            minLines = 3,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("create_clan_description")
        )
        Text(
            localized(TextKey.ClanCreationLevelRequirement,
                "level" to CLAN_CREATION_MIN_LEVEL, "current" to playerLevel),
            modifier = Modifier.testTag("create_clan_level_requirement"),
            color = if (playerLevel >= CLAN_CREATION_MIN_LEVEL) ArcadePalette.Mint400 else ArcadePalette.Coral400,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "${formatNumber(CLAN_CREATION_GOLD_COST)} ${localized(TextKey.Gold)} • " +
                "${formatNumber(CLAN_CREATION_GEM_COST)} ${localized(TextKey.Gems)}",
            modifier = Modifier.testTag("create_clan_cost"),
            color = if (gold >= CLAN_CREATION_GOLD_COST && gems >= CLAN_CREATION_GEM_COST) {
                ArcadePalette.Gold500
            } else {
                ArcadePalette.Coral400
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeActionButton(
                label = localized(TextKey.CreateAction),
                onClick = { onCreate(name.trim(), description.trim()) },
                enabled = name.isNotBlank() &&
                    playerLevel >= CLAN_CREATION_MIN_LEVEL &&
                    gold >= CLAN_CREATION_GOLD_COST &&
                    gems >= CLAN_CREATION_GEM_COST,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth().testTag("create_clan_submit")
            )
            ArcadeActionButton(
                label = localized(TextKey.Cancel),
                onClick = onDismiss,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun ClanDetailView(
    serverUrl: String,
    currentUserId: String?,
    clan: ClanSnapshot,
    notice: String?,
    onLeave: () -> Unit,
    onKickMember: (String, String) -> Unit,
    onRespondJoinRequest: (String, String, Boolean) -> Unit,
    onUpdateLogo: (String) -> Unit,
    onClaimQuest: () -> Unit,
    gold: Int = 0,
    gems: Int = 0,
    isDonationPending: Boolean = false,
    onDonate: (ClanDonationCurrency, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    missionState: MissionState? = null,
    onRefreshMissions: () -> Unit = {},
    onClaimMissionInstance: (String) -> Unit = {},
    onDismissMissionReward: () -> Unit = {},
    social: FriendsSnapshot = FriendsSnapshot(),
    isSocialLoading: Boolean = false,
    socialNotice: String? = null,
    socialError: String? = null,
    onSendFriendRequest: (String) -> Unit = {},
    onRespondFriendRequest: (String, Boolean) -> Unit = { _, _ -> },
    isSocialReady: Boolean = true,
    onRefreshSocial: () -> Unit = {},
    loadError: String? = null,
    onRetryLoad: () -> Unit = {},
    clanChat: ClanChatState? = null,
    chatActions: ClanChatActions = ClanChatActions(),
    onUploadLogo: (ByteArray) -> Unit = {},
    onRemoveLogo: () -> Unit = {},
    onSetMemberRole: (memberId: String, role: ClanRole) -> Unit = { _, _ -> },
    isLogoUploading: Boolean = false,
    /** Mở màn Đua bang (tab thứ ba); null khi đang xem bang khác. */
    onOpenRace: (() -> Unit)? = null,
    clanRank: Int? = null,
    chatNowMillis: Long? = null,
    /** Fixed zone for previews; null follows the device. */
    chatTimeZoneId: String? = null,
    initialChatTab: Boolean = false
) {
    var showLogoDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var showDonationDialog by remember { mutableStateOf(false) }
    var kickTarget by remember { mutableStateOf<ClanMemberSnapshot?>(null) }
    var selectedMemberId by remember(clan.id) { mutableStateOf<String?>(null) }
    val selectedMember = clan.members.firstOrNull { it.userId == selectedMemberId }
    LaunchedEffect(selectedMember?.userId) {
        if (selectedMember == null) selectedMemberId = null
    }
    var visibleMemberCount by remember(clan.id) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val isOwner = clan.ownerId == currentUserId
    val currentMember = clan.members.firstOrNull { it.userId == currentUserId }
    // The leader and co-leaders manage the logo; the server re-checks every change.
    val canManageLogo = isOwner || currentMember?.role?.canModerateClan() == true
    val chatAvailable = clanChat != null && currentMember != null
    var selectedTab by rememberSaveable(clan.id) { mutableStateOf(if (initialChatTab) 1 else 0) }
    val membersByElo = remember(clan.members) {
        clan.members.sortedWith(compareByDescending<ClanMemberSnapshot> { it.trophies }.thenBy { it.userId })
    }
    val visibleMembers = membersByElo.take(visibleMemberCount)
    val canReportClan = canReportUgcTarget(UgcTargetType.CLAN, clan.id)

    val logoUrl = clanLogoImageUrl(clan.id, clan.logoRevision)
    val clanTabs: @Composable (Modifier) -> Unit = { tabModifier ->
        val unread = clanChat?.unreadCount ?: 0
        // Tab: Tổng quan · Chat (khi là thành viên) · Đua bang (mở màn đua tuần).
        val tabs = buildList {
            add("overview" to localized(TextKey.ClanTabOverviewShort))
            if (chatAvailable) add(
                "chat" to if (unread > 0 && selectedTab != 1) localized(TextKey.ClanTabChatUnread, "count" to unread)
                else localized(TextKey.ClanTabChat)
            )
            if (onOpenRace != null) add("race" to localized(TextKey.ClanTabRace))
        }
        ArcadeSegmentedControl(
            labels = tabs.map { it.second },
            selectedIndex = selectedTab.coerceAtMost(if (chatAvailable) 1 else 0),
            onSelected = { index ->
                when (tabs[index].first) {
                    "race" -> onOpenRace?.invoke()
                    "chat" -> selectedTab = 1
                    else -> selectedTab = 0
                }
            },
            modifier = tabModifier,
            itemTestTag = { index ->
                when (tabs[index].first) {
                    "chat" -> "clan_tab_chat"
                    "race" -> "clan_weekly_race_entry"
                    else -> "clan_tab_overview"
                }
            }
        )
    }
    val overview: @Composable (Modifier, Boolean) -> Unit = { listModifier, withTabs -> LazyColumn(
        modifier = listModifier.testTag("clan_detail_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "clan_detail_hero") {
            ClanDetailHero(
                clan = clan,
                logoUrl = logoUrl,
                onEditLogo = if (canManageLogo) ({ showLogoDialog = true }) else null
            )
        }

        notice?.let { item(key = "clan_detail_notice") { ClanNotice(it) } }
        loadError?.let { message ->
            item(key = "clan_load_error") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ClanLoadError(message, onRetry = onRetryLoad)
                }
            }
        }

        item(key = "clan_stats") { ClanStats(clan, clanRank) }
        if (withTabs) item(key = "clan_tabs") { clanTabs(Modifier) }

        item(key = "clan_progress") {
            ClanProgressCard(
                clan = clan,
                currentMember = currentMember,
                onDonate = { showDonationDialog = true }
            )
        }

        if (currentMember != null && missionState != null) {
            item(key = "clan_missions") {
                ServerMissionList(missionState, onRefreshMissions, onClaimMissionInstance, onDismissMissionReward)
            }
        }

        if (isOwner && clan.joinRequests.isNotEmpty()) {
            items(clan.joinRequests, key = { "request:${it.userId}" }) { request ->
                ClanJoinRequestCard(
                    request = request,
                    onReject = { onRespondJoinRequest(clan.id, request.userId, false) },
                    onApprove = { onRespondJoinRequest(clan.id, request.userId, true) }
                )
            }
        }

        item(key = "member_list") {
            Column(Modifier.fillMaxWidth().a1Card().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClanSectionTitle(localized(TextKey.ClanMembers), Modifier.weight(1f))
                    if (isOwner && clan.joinRequests.isNotEmpty()) {
                        A1Tag(localized(TextKey.ClanRequestsShort, "count" to clan.joinRequests.size), A1TagTone.BLUE)
                    } else {
                        Text(
                            "${clan.members.size}/${clan.maxMembers}",
                            style = MaterialTheme.typography.titleSmall,
                            color = A1Palette.Muted
                        )
                    }
                }
                visibleMembers.forEachIndexed { index, member ->
                    ClanMemberCard(
                        member = member,
                        isSelf = member.userId == currentUserId,
                        isOwner = isOwner,
                        canRemove = clan.ownerId != member.userId,
                        onRemove = { kickTarget = member },
                        onViewProfile = { selectedMemberId = member.userId },
                        showDivider = index < visibleMembers.lastIndex
                    )
                }
            }
        }

        item(key = "clan_members_load_more") {
            ArcadeLoadMoreButton(
                visibleItemCount = visibleMembers.size,
                totalItemCount = clan.members.size,
                onLoadMore = {
                    visibleMemberCount = nextArcadePageItemCount(visibleMemberCount, clan.members.size)
                },
                testTag = "clan_members_load_more"
            )
        }

        if (currentMember != null) item(key = "leave_clan") {
            ArcadeActionButton(
                label = localized(TextKey.LeaveClanShort),
                onClick = { showLeaveConfirmation = true },
                icon = Icons.AutoMirrored.Rounded.Logout,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth().testTag("leave_clan")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = A1Palette.RedText, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(localized(TextKey.LeaveClanShort), style = MaterialTheme.typography.titleMedium, color = A1Palette.RedText)
            }
        }
        if (canReportClan) {
            item(key = "clan_report") {
                UgcClanReportAction(clan.id, clan.name, Modifier.fillMaxWidth())
            }
        }
    } }

    BoxWithConstraints(modifier) {
        val sideBySide = chatAvailable && maxWidth >= 840.dp
        val chatPanel: @Composable (Modifier) -> Unit = { panelModifier ->
            ClanChatPanel(
                chat = clanChat ?: ClanChatState(),
                members = clan.members,
                currentUserId = currentUserId,
                actions = chatActions,
                modifier = panelModifier,
                nowMillis = chatNowMillis ?: com.hienthai.fastowin.platform.epochMillis(),
                timeZone = chatTimeZoneId?.let { kotlinx.datetime.TimeZone.of(it) } ?: kotlinx.datetime.TimeZone.currentSystemDefault()
            )
        }
        when {
            !chatAvailable && onOpenRace == null -> overview(Modifier.fillMaxSize(), false)
            sideBySide && onOpenRace == null -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                overview(Modifier.weight(1f).fillMaxHeight(), false)
                chatPanel(Modifier.width(400.dp).fillMaxHeight().padding(top = 12.dp, bottom = 16.dp))
            }
            else -> Column(Modifier.fillMaxSize()) {
                if (selectedTab == 0) overview(Modifier.weight(1f).fillMaxWidth(), true)
                else {
                    clanTabs(Modifier.padding(top = 12.dp))
                    chatPanel(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, bottom = 12.dp))
                }
            }
        }
    }

    selectedMember?.let { member ->
        ClanMemberProfileDialog(
            member = member,
            currentUserId = currentUserId,
            social = social,
            isSocialLoading = isSocialLoading,
            socialNotice = socialNotice,
            socialError = socialError,
            onSendFriendRequest = onSendFriendRequest,
            onRespondFriendRequest = onRespondFriendRequest,
            onDismiss = { selectedMemberId = null },
            isSocialReady = isSocialReady,
            onRefreshSocial = onRefreshSocial,
            roleAction = if (isOwner && member.role != ClanRole.LEADER) {
                ClanMemberRoleAction(
                    appoint = member.role == ClanRole.MEMBER,
                    enabled = member.role == ClanRole.CO_LEADER ||
                        clan.members.count { it.role == ClanRole.CO_LEADER } < CLAN_MAX_CO_LEADERS,
                    onClick = {
                        onSetMemberRole(member.userId, if (member.role == ClanRole.MEMBER) ClanRole.CO_LEADER else ClanRole.MEMBER)
                    }
                )
            } else null
        )
    }

    if (showLeaveConfirmation) {
        ArcadeDialog(
            title = localized(TextKey.LeaveClanTitle),
            subtitle = localized(TextKey.LeaveClanDescription, "clan" to clan.name),
            onDismissRequest = { showLeaveConfirmation = false },
            modifier = Modifier.testTag("leave_clan_confirmation_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = localized(TextKey.LeaveClan),
                    onClick = {
                        showLeaveConfirmation = false
                        onLeave()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_leave_clan")
                )
                ArcadeActionButton(
                    label = localized(TextKey.Cancel),
                    onClick = { showLeaveConfirmation = false },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showDonationDialog) {
        ClanDonationDialog(
            gold = gold,
            gems = gems,
            isPending = isDonationPending,
            onConfirm = { currency, amount ->
                onDonate(currency, amount)
                showDonationDialog = false
            },
            onDismiss = { showDonationDialog = false }
        )
    }

    kickTarget?.let { member ->
        ArcadeDialog(
            title = localized(TextKey.RemoveClanMemberTitle),
            subtitle = localized(TextKey.RemoveClanMemberDescription, "player" to member.displayName, "clan" to clan.name),
            onDismissRequest = { kickTarget = null },
            modifier = Modifier.testTag("kick_clan_member_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = localized(TextKey.RemoveClanMemberAction),
                    onClick = {
                        kickTarget = null
                        onKickMember(clan.id, member.userId)
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_kick_clan_member")
                )
                ArcadeActionButton(
                    label = localized(TextKey.Cancel),
                    onClick = { kickTarget = null },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showLogoDialog && canManageLogo) {
        ClanLogoDialog(
            selectedLogoId = clan.logoId,
            clanLevel = clan.level,
            onSelect = { id ->
                onUpdateLogo(id)
                showLogoDialog = false
            },
            onDismiss = { showLogoDialog = false },
            uploadedLogoUrl = logoUrl,
            hasUploadedLogo = clan.logoRevision != null,
            isUploading = isLogoUploading,
            onUpload = onUploadLogo,
            onRemoveUpload = onRemoveLogo
        )
    }
}

@Composable
private fun ClanProgressCard(
    clan: ClanSnapshot,
    currentMember: ClanMemberSnapshot?,
    onDonate: () -> Unit
) {
    val isMaxLevel = clan.level >= 50 || clan.nextLevelExperience <= 0
    val progress = if (isMaxLevel) 1f else {
        clan.currentLevelExperience.toFloat() / clan.nextLevelExperience.coerceAtLeast(1)
    }.coerceIn(0f, 1f)
    Column(
        modifier = Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ClanSectionTitle(localized(TextKey.ClanProgressTitle), Modifier.weight(1f))
            Text(
                if (isMaxLevel) localized(TextKey.MaximumLevel)
                else "${formatNumber(clan.currentLevelExperience)}/${formatNumber(clan.nextLevelExperience)} XP",
                style = MaterialTheme.typography.titleSmall,
                color = A1Palette.Muted
            )
        }
        A1ProgressBar(progress, listOf(A1Palette.Purple1, A1Palette.Purple2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            A1CoinIcon(size = 22.dp)
            Text(formatNumber(clan.donatedGold), style = MaterialTheme.typography.titleSmall, color = A1Palette.Ink)
            Spacer(Modifier.width(4.dp))
            A1GemIcon(size = 22.dp)
            Text(formatNumber(clan.donatedGems), style = MaterialTheme.typography.titleSmall, color = A1Palette.Ink)
            currentMember?.let { member ->
                Spacer(Modifier.width(4.dp))
                Text(
                    localized(TextKey.ClanYouDonated, "amount" to formatNumber(member.donatedGold)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ArcadeActionButton(
            label = localized(TextKey.DonateToClan),
            onClick = onDonate,
            icon = Icons.Rounded.VolunteerActivism,
            style = ArcadeActionStyle.GOLD,
            large = true,
            modifier = Modifier.fillMaxWidth().testTag("open_clan_donation")
        )
    }
}

@Composable
private fun ClanDonationDialog(
    gold: Int,
    gems: Int,
    isPending: Boolean,
    onConfirm: (ClanDonationCurrency, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var currency by remember { mutableStateOf(ClanDonationCurrency.GOLD) }
    var selectedIndex by remember(currency) { mutableStateOf(0) }
    val amounts = if (currency == ClanDonationCurrency.GOLD) {
        listOf(100, 500, 1_000, 5_000)
    } else {
        listOf(1, 5, 10, 50)
    }
    val amount = amounts[selectedIndex]
    val balance = if (currency == ClanDonationCurrency.GOLD) gold else gems
    val currencyLabel = localized(if (currency == ClanDonationCurrency.GOLD) TextKey.Gold else TextKey.Gems)
    val experience = if (currency == ClanDonationCurrency.GOLD) amount / 10 else amount * 10
    val canConfirm = !isPending && amount <= balance

    ArcadeDialog(
        title = localized(TextKey.DonateToClan),
        subtitle = localized(TextKey.DonateToClanDescription),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("clan_donation_dialog")
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ArcadeSegmentedControl(
                labels = listOf(localized(TextKey.Gold), localized(TextKey.Gems)),
                selectedIndex = currency.ordinal,
                onSelected = { currency = ClanDonationCurrency.entries[it] },
                itemTestTag = { "donation_currency_${ClanDonationCurrency.entries[it].name.lowercase()}" }
            )
            Text(
                localized(TextKey.DonationBalance, "amount" to formatNumber(balance), "currency" to currencyLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ArcadeSegmentedControl(
                labels = amounts.map { formatNumber(it) },
                selectedIndex = selectedIndex,
                onSelected = { selectedIndex = it },
                itemTestTag = { index -> "donate_${currency.name.lowercase().removeSuffix("s")}_${amounts[index]}" }
            )
            Text(
                localized(TextKey.DonationXpPreview, "xp" to experience),
                style = MaterialTheme.typography.titleMedium,
                color = ArcadePalette.Gold500,
                fontWeight = FontWeight.Black
            )
            Text(
                localized(TextKey.DonationCannotUndo),
                style = MaterialTheme.typography.bodySmall,
                color = ArcadePalette.Coral400
            )
            ArcadeActionButton(
                label = localized(TextKey.Confirm),
                onClick = { onConfirm(currency, amount) },
                enabled = canConfirm,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth().testTag("confirm_clan_donation")
            )
            ArcadeActionButton(
                label = localized(TextKey.Cancel),
                onClick = onDismiss,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ClanStats(clan: ClanSnapshot, clanRank: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClanStatTile("${clan.members.size}/${clan.maxMembers}", localized(TextKey.ClanMembers), Modifier.weight(1f))
        ClanStatTile(formatNumber(clan.trophies), localized(TextKey.ClanPointsShort), Modifier.weight(1f))
        if (clanRank != null) {
            ClanStatTile("#$clanRank", localized(TextKey.ClanRankShort), Modifier.weight(1f))
        } else {
            ClanStatTile(formatNumber(clan.members.sumOf { it.trophies }), localized(TextKey.TotalMemberElo), Modifier.weight(1f))
        }
    }
}

/** Thẻ đầu màn chi tiết bang: khiên lớn, nhãn cấp tím, tên và mô tả. */
@Composable
private fun ClanDetailHero(clan: ClanSnapshot, logoUrl: String, onEditLogo: (() -> Unit)?) {
    val logoDescription = localized(if (onEditLogo != null) TextKey.SelectClanLogo else TextKey.ClanLogo)
    Row(
        modifier = Modifier.fillMaxWidth().a1Card().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 110.dp)
                .then(if (onEditLogo == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onEditLogo))
                .semantics { contentDescription = logoDescription }
                .testTag("clan_detail_logo")
        ) {
            ClanLogoMark(clan.logoId, Modifier.fillMaxSize(), clan.id, clan.logoRevision)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            A1Tag(localized(TextKey.Level, "level" to clan.level), A1TagTone.PURPLE)
            Text(
                clan.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                color = A1Palette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                clan.description.ifBlank { localized(TextKey.DefaultClanDescription) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted
            )
        }
    }
}

@Composable
private fun ClanJoinRequestCard(
    request: ClanJoinRequestSnapshot,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    // Thẻ vàng: người xin vào (tên đủ một dòng) và hai nút Từ chối / Duyệt bên dưới.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .a1Card(background = A1Palette.Highlight, border = A1Palette.YellowBorder)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerAvatar(request.displayName, avatarId = null, userId = request.userId, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(request.displayName, style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    localized(TextKey.PlayerCodeShort, "code" to request.playerCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = A1Palette.Muted
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArcadeActionButton(
                label = localized(TextKey.Decline),
                onClick = onReject,
                style = ArcadeActionStyle.OUTLINE,
                compact = true,
                modifier = Modifier.weight(1f)
            )
            ArcadeActionButton(
                label = localized(TextKey.Approve),
                onClick = onApprove,
                style = ArcadeActionStyle.SUCCESS,
                compact = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Dòng thành viên: avatar có chấm trạng thái, tên (bạn), Elo, nhãn chức vụ và menu ba chấm. */
@Composable
private fun ClanMemberCard(
    member: ClanMemberSnapshot,
    isSelf: Boolean,
    isOwner: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onViewProfile: () -> Unit,
    showDivider: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = localized(TextKey.ViewClanMemberProfile, "player" to member.displayName), onClick = onViewProfile)
            .then(
                if (showDivider) Modifier.drawBehind {
                    drawLine(
                        A1Palette.SurfaceAlt2,
                        androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                        androidx.compose.ui.geometry.Offset(size.width, size.height - 1.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                } else Modifier
            )
            .testTag("clan_member:${member.userId}")
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box {
            PlayerAvatar(member.displayName, member.avatarId, member.userId, member.frameId, size = 52.dp)
            if (member.presence != FriendPresence.OFFLINE) {
                FriendPresenceIndicator(member.presence, Modifier.align(Alignment.BottomEnd))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = A1Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isSelf) Text(localized(TextKey.ClanYouTag), style = MaterialTheme.typography.titleSmall, color = A1Palette.Muted)
            }
            Text(
                "${member.trophies} Elo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted
            )
        }
        ClanRoleTag(member.role, member.role.localizedName())
        if (isOwner && canRemove) {
            ClanOverflowMenu(
                localized(TextKey.RemoveClanMemberNamed, "player" to member.displayName),
                buttonTag = "clan_member_menu:${member.userId}"
            ) { dismiss ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(localized(TextKey.RemoveClanMemberNamed, "player" to member.displayName)) },
                    leadingIcon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = A1Palette.RedText) },
                    onClick = {
                        dismiss()
                        onRemove()
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("clan_kick:${member.userId}")
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
fun ClanLogoDialog(
    selectedLogoId: String?,
    clanLevel: Int,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    uploadedLogoUrl: String = "",
    hasUploadedLogo: Boolean = false,
    isUploading: Boolean = false,
    onUpload: ((ByteArray) -> Unit)? = null,
    onRemoveUpload: () -> Unit = {}
) {
    val currentLogoId = selectedLogoId?.lowercase() ?: "shield"
    var submitted by remember(selectedLogoId, clanLevel) { mutableStateOf(false) }
    ArcadeDialog(
        title = localized(TextKey.ChooseClanLogo),
        subtitle = localized(TextKey.ChooseClanLogoDescription),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("clan_logo_dialog"),
        fixedActions = {
            ArcadeActionButton(
                label = localized(TextKey.Close),
                onClick = onDismiss,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth().testTag("close_clan_logo_dialog")
            )
        }
    ) {
        onUpload?.let { upload ->
            ClanLogoUploadSection(uploadedLogoUrl, hasUploadedLogo, isUploading, upload, onRemoveUpload)
        }
        Text(
            localized(TextKey.ClanLogoUnlockDescription, "current" to clanLevel),
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadePalette.Blue100
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClanLogoMark(currentLogoId, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${localized(TextKey.ClanLogoCurrent)} · ${currentLogoId.clanLogoLabel()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ArcadePalette.Gold100
                )
                clanLogoRequiredLevel(currentLogoId)?.takeIf { it > clanLevel }?.let { level ->
                    Text(
                        localized(TextKey.ClanLogoUnlockAtLevel, "level" to level),
                        style = MaterialTheme.typography.bodySmall,
                        color = ArcadePalette.Blue100
                    )
                }
            }
        }
        val minimumCardWidth = if (LocalDensity.current.fontScale >= 1.4f) 148.dp else 116.dp
        // ArcadeDialog owns the single scroll container and keeps Close outside it.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = ((maxWidth + 8.dp) / (minimumCardWidth + 8.dp)).toInt().coerceIn(1, 3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CLAN_LOGOS.chunked(columns).forEach { logos ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        logos.forEach { logo ->
                            ClanLogoChoice(
                                id = logo.id,
                                requiredLevel = logo.requiredLevel,
                                isSelected = currentLogoId == logo.id,
                                isUnlocked = isClanLogoUnlocked(logo.id, clanLevel),
                                enabled = !submitted,
                                onSelect = {
                                    if (!submitted) {
                                        submitted = true
                                        onSelect(logo.id)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - logos.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClanLogoChoice(
    id: String,
    requiredLevel: Int,
    isSelected: Boolean,
    isUnlocked: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoDescription = localized(TextKey.ClanLogoNamed, "logo" to id.clanLogoLabel())
    val requirement = localized(TextKey.ClanLogoUnlockAtLevel, "level" to requiredLevel)
    val status = localized(if (isSelected) TextKey.ClanLogoCurrent else if (isUnlocked) TextKey.ClanLogoUnlocked else TextKey.Locked)
    Surface(
        onClick = onSelect,
        enabled = enabled && isUnlocked && !isSelected,
        modifier = modifier
            .heightIn(min = 116.dp)
            .testTag("clan_logo_$id")
            .semantics {
                selected = isSelected
                role = Role.Button
                contentDescription = logoDescription
                stateDescription = "$status. $requirement"
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) ArcadePalette.Blue900 else ArcadePalette.Navy900,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) ArcadePalette.Gold500 else ArcadePalette.OutlineDark
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp).alpha(if (isUnlocked || isSelected) 1f else 0.38f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ClanLogoMark(id, Modifier.size(48.dp))
            Text(id.clanLogoLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                localized(TextKey.ClanLogoRequiredLevel, "level" to requiredLevel),
                modifier = Modifier.testTag("clan_logo_requirement_$id"),
                style = MaterialTheme.typography.bodySmall,
                color = ArcadePalette.Blue100,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
internal fun ClanLogoMark(
    logoId: String?,
    modifier: Modifier = Modifier,
    clanId: String? = null,
    logoRevision: String? = null
) {
    val uploaded = clanLogoImageUrl(clanId, logoRevision)
    if (uploaded.isNotBlank()) {
        Box(
            modifier = modifier.clip(ClanShieldShape).background(A1Palette.SurfaceAlt2, ClanShieldShape),
            contentAlignment = Alignment.Center
        ) {
            NetworkImage(uploaded, Modifier.fillMaxSize().clip(ClanShieldShape), contentScale = ContentScale.Crop) {
                ClanIconMark(logoId, Modifier.fillMaxSize())
            }
        }
        return
    }
    ClanIconMark(logoId, modifier)
}

@Composable
private fun ClanIconMark(logoId: String?, modifier: Modifier) {
    ClanShieldEmblem(logoId, modifier)
}

@Composable
private fun ArcadeSectionHeading(title: String, supporting: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            supporting,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ArcadePalette.Gold500
        )
    }
}

@Composable
private fun ClanNotice(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFDCEBFF), RoundedCornerShape(14.dp))
            .border(2.dp, Color(0xFF6AA4F0), RoundedCornerShape(14.dp))
            .padding(13.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF0D3A7A)
    )
}

@Composable
internal fun ClanRole.localizedName(): String = localized(when (this) {
    ClanRole.LEADER -> TextKey.ClanRoleLeader
    ClanRole.CO_LEADER -> TextKey.ClanRoleCoLeader
    ClanRole.MEMBER -> TextKey.ClanRoleMember
})

@Composable
private fun String.clanLogoLabel(): String {
    val key = when (this) {
        "shield" -> TextKey.ClanLogoShield
        "sword" -> TextKey.ClanLogoSwords
        "flag" -> TextKey.ClanLogoFlag
        "dragon" -> TextKey.ClanLogoDragon
        "wolf" -> TextKey.ClanLogoWolf
        "eagle" -> TextKey.ClanLogoEagle
        "crown" -> TextKey.ClanLogoCrown
        "lion" -> TextKey.ClanLogoLion
        "phoenix" -> TextKey.ClanLogoPhoenix
        "kirin" -> TextKey.ClanLogoKirin
        else -> return this
    }
    return localized(key)
}

private fun formatNumber(value: Int): String {
    val raw = value.toString()
    return raw.reversed().chunked(3).joinToString(".").reversed()
}

private fun formatNumber(value: Long): String {
    val raw = value.toString()
    return raw.reversed().chunked(3).joinToString(".").reversed()
}

@Composable
private fun ClanLogoUploadSection(
    uploadedLogoUrl: String,
    hasUploadedLogo: Boolean,
    isUploading: Boolean,
    onUpload: (ByteArray) -> Unit,
    onRemoveUpload: () -> Unit
) {
    ArcadePanel(modifier = Modifier.fillMaxWidth().testTag("clan_logo_upload_section"), accent = ArcadePalette.Gold500) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(ArcadePalette.Navy700, CircleShape)
                        .border(2.dp, if (hasUploadedLogo) ArcadePalette.Gold500 else ArcadePalette.OutlineDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploading) CircularProgressIndicator(color = ArcadePalette.Gold500, modifier = Modifier.size(28.dp))
                    else if (hasUploadedLogo) NetworkImage(uploadedLogoUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = ArcadePalette.Gold500)
                    } else Icon(Icons.Rounded.Add, contentDescription = null, tint = ArcadePalette.Blue100)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(localized(if (isUploading) TextKey.ClanLogoUploading else TextKey.ClanLogoUploadedImage),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ArcadePalette.Gold100,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(localized(TextKey.ClanLogoUploadHint, "count" to CLAN_LOGO_DAILY_CHANGE_LIMIT),
                        style = MaterialTheme.typography.bodySmall, color = ArcadePalette.Blue100)
                }
            }
            com.hienthai.fastowin.platform.ImagePicker(onImageSelected = { bytes -> bytes?.let(onUpload) }) { pick ->
                ArcadeActionButton(
                    label = localized(TextKey.ClanLogoUpload),
                    onClick = pick,
                    enabled = !isUploading,
                    style = ArcadeActionStyle.GOLD,
                    modifier = Modifier.fillMaxWidth().testTag("clan_logo_upload")
                )
            }
            if (hasUploadedLogo) ArcadeActionButton(
                label = localized(TextKey.ClanLogoRemoveUpload),
                onClick = onRemoveUpload,
                enabled = !isUploading,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth().testTag("clan_logo_remove_upload")
            )
        }
    }
}
