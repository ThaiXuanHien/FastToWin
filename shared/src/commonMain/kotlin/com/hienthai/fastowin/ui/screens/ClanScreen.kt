package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.CLAN_AVATAR_IDS
import com.hienthai.fastowin.protocol.ClanJoinRequestSnapshot
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanQuestSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
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
import com.hienthai.fastowin.ui.components.CrossedSwordsIcon
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.RewardAmounts
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

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
    gold: Int = 0,
    gems: Int = 0,
    unreadNotifications: Int = 0,
    onOpenNotifications: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = if (showBackButton) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showBackButton) {
                FastToWinHeader(
                    title = "Bang hội",
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
            maxContentWidth = 920.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            when {
                myClanId == null -> ClanDiscoveryView(
                    clanList = clanList,
                    pendingJoinClanIds = pendingJoinClanIds,
                    notice = notice,
                    onCreateClan = onCreateClan,
                    onJoinClan = onJoinClan,
                    onSearch = onSearch,
                    modifier = contentModifier
                )

                currentClan != null -> ClanDetailView(
                    serverUrl = serverUrl,
                    currentUserId = currentUserId,
                    clan = currentClan,
                    notice = notice,
                    onLeave = onLeaveClan,
                    onKickMember = onKickMember,
                    onRespondJoinRequest = onRespondJoinRequest,
                    onUpdateLogo = { logoId -> onUpdateLogo(currentClan.id, logoId) },
                    onClaimQuest = { onClaimQuest(currentClan.id) },
                    modifier = contentModifier
                )

                else -> {
                    Box(contentModifier, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ArcadePalette.Gold500)
                    }
                    LaunchedEffect(myClanId) { onViewClan(myClanId) }
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var visibleClanCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleClans = clanList.take(visibleClanCount)

    LazyColumn(
        modifier = modifier.testTag("clan_discovery_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "clan_hero") {
            ArcadeFeatureHero(
                illustration = Res.drawable.arcade_clan_crest,
                title = "Sát cánh tranh tài",
                subtitle = "Gia nhập bang hội, hoàn thành nhiệm vụ và cùng nhau leo hạng.",
                accent = ArcadePalette.Violet600
            )
        }

        notice?.let { item(key = "clan_notice") { ClanNotice(it) } }

        item(key = "clan_search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Tìm bang hội") },
                placeholder = { Text("Nhập tên bang...") },
                modifier = Modifier.fillMaxWidth().testTag("clan_search_field"),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            visibleClanCount = DEFAULT_ARCADE_PAGE_SIZE
                            onSearch(searchQuery.trim().takeIf(String::isNotEmpty))
                        },
                        modifier = Modifier.semantics { contentDescription = "Tìm kiếm bang hội" }
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                }
            )
        }

        item(key = "create_clan") {
            ArcadeActionButton(
                label = "TẠO BANG HỘI",
                onClick = { showCreateDialog = true },
                icon = Icons.Rounded.Add,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth().testTag("open_create_clan")
            )
        }

        item(key = "clan_list_title") {
            ArcadeSectionHeading(
                title = "Khám phá bang hội",
                supporting = "${clanList.size} bang"
            )
        }

        if (clanList.isEmpty()) {
            item(key = "clan_empty") {
                ArcadeEmptyState(
                    illustration = Res.drawable.arcade_clan_crest,
                    title = "Chưa tìm thấy bang hội",
                    description = "Thử từ khóa khác hoặc tạo bang của riêng bạn."
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
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                onCreateClan(name, description)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ClanSummaryCard(
    clan: ClanSummarySnapshot,
    isPending: Boolean,
    onJoin: () -> Unit
) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Violet400) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            val compact = maxWidth < 400.dp || LocalDensity.current.fontScale >= 1.3f
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClanSummaryIdentity(clan)
                    ClanJoinButton(isPending, onJoin, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClanSummaryIdentity(clan, Modifier.weight(1f))
                    ClanJoinButton(isPending, onJoin, Modifier.width(124.dp))
                }
            }
        }
    }
}

@Composable
private fun ClanSummaryIdentity(clan: ClanSummarySnapshot, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClanLogoMark(logoId = clan.logoId, modifier = Modifier.size(56.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                clan.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${clan.memberCount}/${clan.maxMembers} thành viên · ${formatNumber(clan.trophies)} cúp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClanJoinButton(isPending: Boolean, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    ArcadeActionButton(
        label = if (isPending) "Đang chờ" else "Xin vào",
        onClick = onJoin,
        enabled = !isPending,
        style = if (isPending) ArcadeActionStyle.OUTLINE else ArcadeActionStyle.PRIMARY,
        modifier = modifier
    )
}

@Composable
private fun CreateClanDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    ArcadeDialog(
        title = "Tạo Bang Hội",
        subtitle = "Chọn tên ngắn gọn, dễ nhớ để đồng đội tìm thấy bạn.",
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create_clan_dialog")
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = { Text("Tên bang") },
            leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("create_clan_name")
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(160) },
            label = { Text("Mô tả bang hội") },
            minLines = 3,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("create_clan_description")
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeActionButton(
                label = "TẠO",
                onClick = { onCreate(name.trim(), description.trim()) },
                enabled = name.isNotBlank(),
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth()
            )
            ArcadeActionButton(
                label = "HỦY",
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
    modifier: Modifier = Modifier
) {
    var showLogoDialog by remember { mutableStateOf(false) }
    var kickTarget by remember { mutableStateOf<ClanMemberSnapshot?>(null) }
    var visibleMemberCount by remember(clan.id) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val isOwner = clan.ownerId == currentUserId
    val currentMember = clan.members.firstOrNull { it.userId == currentUserId }
    val visibleMembers = clan.members.take(visibleMemberCount)

    LazyColumn(
        modifier = modifier.testTag("clan_detail_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "clan_detail_hero") {
            ArcadeIconHero(
                kicker = "BANG HỘI",
                title = clan.name,
                subtitle = clan.description.ifBlank { "Cùng đồng đội chinh phục bảng xếp hạng." },
                icon = clanLogoIcon(clan.logoId),
                accent = ArcadePalette.Violet600,
                onIconClick = if (isOwner) ({ showLogoDialog = true }) else null,
                iconContentDescription = if (isOwner) "Chọn logo bang" else "Logo bang"
            )
        }

        notice?.let { item(key = "clan_detail_notice") { ClanNotice(it) } }

        item(key = "clan_stats") { ClanStats(clan) }

        clan.quest?.let { quest ->
            item(key = "clan_quest") {
                ClanQuestCard(
                    quest = quest,
                    alreadyClaimed = currentMember?.questRewardClaimed == true,
                    onClaim = onClaimQuest
                )
            }
        }

        if (isOwner && clan.joinRequests.isNotEmpty()) {
            item(key = "join_request_heading") {
                ArcadeSectionHeading(
                    title = "Yêu cầu tham gia (${clan.joinRequests.size})",
                    supporting = "${clan.joinRequests.size} mới"
                )
            }
            items(clan.joinRequests, key = { "request:${it.userId}" }) { request ->
                ClanJoinRequestCard(
                    request = request,
                    onReject = { onRespondJoinRequest(clan.id, request.userId, false) },
                    onApprove = { onRespondJoinRequest(clan.id, request.userId, true) }
                )
            }
        }

        item(key = "member_heading") {
            ArcadeSectionHeading(
                title = "Thành viên",
                supporting = "${clan.members.size}/${clan.maxMembers}"
            )
        }

        items(visibleMembers, key = { "member:${it.userId}" }) { member ->
            ClanMemberCard(
                member = member,
                isOwner = isOwner,
                canRemove = clan.ownerId != member.userId,
                onRemove = { kickTarget = member }
            )
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

        item(key = "leave_clan") {
            ArcadeActionButton(
                label = "RỜI BANG",
                onClick = onLeave,
                style = ArcadeActionStyle.DANGER,
                modifier = Modifier.fillMaxWidth().testTag("leave_clan")
            )
        }
    }

    kickTarget?.let { member ->
        ArcadeDialog(
            title = "Mời thành viên rời bang?",
            subtitle = "${member.displayName} sẽ bị xóa khỏi bang ${clan.name}.",
            onDismissRequest = { kickTarget = null },
            modifier = Modifier.testTag("kick_clan_member_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = "MỜI RỜI BANG",
                    onClick = {
                        kickTarget = null
                        onKickMember(clan.id, member.userId)
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_kick_clan_member")
                )
                ArcadeActionButton(
                    label = "HỦY",
                    onClick = { kickTarget = null },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showLogoDialog && isOwner) {
        ClanLogoDialog(
            selectedLogoId = clan.logoId,
            onSelect = { id ->
                onUpdateLogo(id)
                showLogoDialog = false
            },
            onDismiss = { showLogoDialog = false }
        )
    }
}

@Composable
private fun ClanStats(clan: ClanSnapshot) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.35f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClanStatCard("${clan.members.size}", "Thành viên", Modifier.fillMaxWidth())
                ClanStatCard(formatNumber(clan.trophies), "Cúp bang", Modifier.fillMaxWidth())
                ClanStatCard(formatNumber(clan.members.sumOf { it.trophies }), "Cúp thành viên", Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClanStatCard("${clan.members.size}", "Thành viên", Modifier.weight(1f))
                ClanStatCard(formatNumber(clan.trophies), "Cúp bang", Modifier.weight(1f))
                ClanStatCard(formatNumber(clan.members.sumOf { it.trophies }), "Tổng cúp", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ClanStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 84.dp),
        shape = RoundedCornerShape(16.dp),
        color = ArcadePalette.Navy800.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, ArcadePalette.OutlineDark)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = ArcadePalette.Gold500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = ArcadePalette.Blue100,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ClanQuestCard(
    quest: ClanQuestSnapshot,
    alreadyClaimed: Boolean,
    onClaim: () -> Unit
) {
    val progress = (quest.progress.toFloat() / quest.target.coerceAtLeast(1)).coerceIn(0f, 1f)
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "NHIỆM VỤ TUẦN",
                style = MaterialTheme.typography.labelMedium,
                color = ArcadePalette.Gold500,
                fontWeight = FontWeight.Black
            )
            Text(
                "Thắng ${quest.target} trận cùng bang hội",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(9.dp),
                color = ArcadePalette.Gold500,
                trackColor = ArcadePalette.Navy700
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${quest.progress}/${quest.target}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                RewardAmounts(gold = quest.rewardGold, xp = quest.rewardXp, gems = quest.rewardGems)
            }
            when {
                alreadyClaimed -> ArcadeActionButton(
                    label = "Đã nhận",
                    onClick = {},
                    enabled = false,
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
                quest.progress >= quest.target -> ArcadeActionButton(
                    label = "NHẬN THƯỞNG",
                    onClick = onClaim,
                    style = ArcadeActionStyle.GOLD,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ClanJoinRequestCard(
    request: ClanJoinRequestSnapshot,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ClanLogoMark(logoId = "shield", modifier = Modifier.size(48.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(request.displayName, fontWeight = FontWeight.Black)
                    Text(
                        "Mã ${request.playerCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = "Duyệt",
                    onClick = onApprove,
                    style = ArcadeActionStyle.GOLD,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "Từ chối",
                    onClick = onReject,
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ClanMemberCard(
    member: ClanMemberSnapshot,
    isOwner: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Blue500) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClanLogoMark(logoId = member.displayName, modifier = Modifier.size(46.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.displayName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${member.role.localizedName()} · ${formatNumber(member.trophies)} cúp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOwner && canRemove) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.PersonRemove,
                        contentDescription = "Mời ${member.displayName} rời bang",
                        tint = ArcadePalette.Coral400
                    )
                }
            } else {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = ArcadePalette.Gold500)
            }
        }
    }
}

@Composable
private fun ClanLogoDialog(
    selectedLogoId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ArcadeDialog(
        title = "Chọn Logo",
        subtitle = "Chạm vào biểu tượng để áp dụng ngay.",
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("clan_logo_dialog")
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(2.dp)
        ) {
            items(CLAN_AVATAR_IDS, key = { it }) { id ->
                val isSelected = selectedLogoId == id
                Surface(
                    onClick = { onSelect(id) },
                    modifier = Modifier
                        .size(60.dp)
                        .semantics {
                            selected = isSelected
                            role = Role.Button
                            contentDescription = "Logo ${id.clanLogoLabel()}"
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) ArcadePalette.Blue700 else ArcadePalette.Navy900,
                    border = BorderStroke(
                        if (isSelected) 3.dp else 1.dp,
                        if (isSelected) ArcadePalette.Gold500 else ArcadePalette.OutlineDark
                    )
                ) {
                    ClanLogoMark(logoId = id, modifier = Modifier.padding(8.dp))
                }
            }
        }
        ArcadeActionButton(
            label = "ĐÓNG",
            onClick = onDismiss,
            style = ArcadeActionStyle.OUTLINE,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ClanLogoMark(logoId: String?, modifier: Modifier = Modifier) {
    val normalized = logoId.orEmpty().lowercase()
    val accent = when (normalized) {
        "sword" -> ArcadePalette.Coral400
        "flag" -> ArcadePalette.Blue300
        "dragon" -> ArcadePalette.Violet400
        "wolf" -> Color(0xFFC8D4EA)
        "eagle" -> ArcadePalette.Gold500
        "crown" -> Color(0xFFFFE28B)
        else -> ArcadePalette.Mint400
    }
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(listOf(ArcadePalette.Navy700, ArcadePalette.Navy900)),
                CircleShape
            )
            .border(2.dp, accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            clanLogoIcon(normalized),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.fillMaxSize().padding(9.dp)
        )
    }
}

private fun clanLogoIcon(logoId: String?): androidx.compose.ui.graphics.vector.ImageVector = when (
    logoId.orEmpty().lowercase()
) {
    "sword" -> CrossedSwordsIcon
    "flag" -> Icons.Rounded.Flag
    "dragon", "wolf" -> Icons.Rounded.Pets
    "eagle" -> Icons.Rounded.Flight
    "crown" -> Icons.Rounded.EmojiEvents
    else -> Icons.Rounded.Shield
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ArcadePalette.Navy800,
        border = BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.64f))
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(13.dp),
            color = ArcadePalette.Blue100
        )
    }
}

private fun ClanRole.localizedName(): String = when (this) {
    ClanRole.LEADER -> "Bang chủ"
    ClanRole.CO_LEADER -> "Phó bang"
    ClanRole.MEMBER -> "Thành viên"
}

private fun String.clanLogoLabel(): String = when (this) {
    "shield" -> "Khiên"
    "sword" -> "Song kiếm"
    "flag" -> "Cờ"
    "dragon" -> "Rồng"
    "wolf" -> "Sói"
    "eagle" -> "Đại bàng"
    "crown" -> "Vương miện"
    else -> this
}

private fun formatNumber(value: Int): String {
    val raw = value.toString()
    return raw.reversed().chunked(3).joinToString(".").reversed()
}
