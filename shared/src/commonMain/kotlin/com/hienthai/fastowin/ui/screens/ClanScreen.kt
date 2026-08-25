package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.RewardAmounts
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_clan_crest
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
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
    SystemBackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = if (showBackButton) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            WindowInsets(0, 0, 0, 0)
        },
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
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (myClanId != null) {
                // Show my clan info
                if (currentClan != null) {
                    ClanDetailView(
                        serverUrl = serverUrl,
                        currentUserId = currentUserId,
                        clan = currentClan,
                        notice = notice,
                        onLeave = onLeaveClan,
                        onKickMember = onKickMember,
                        onRespondJoinRequest = onRespondJoinRequest,
                        onUpdateLogo = { logoId -> onUpdateLogo(currentClan.id, logoId) },
                        onClaimQuest = { onClaimQuest(currentClan.id) }
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    LaunchedEffect(myClanId) {
                        onViewClan(myClanId)
                    }
                }
            } else {
                // Show clan list
                var showCreateDialog by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArcadeFeatureHero(
                        illustration = Res.drawable.arcade_clan_crest,
                        title = "Sát cánh tranh tài",
                        subtitle = "Gia nhập một bang hội, hoàn thành nhiệm vụ và cùng nhau leo hạng.",
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                    notice?.let { ClanNotice(it) }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm bang hội...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onSearch(searchQuery.takeIf { it.isNotBlank() }) }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Tìm kiếm")
                            }
                        }
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tạo Bang Hội")
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(clanList, key = { it.id }) { clan ->
                            ArcadePanel(
                                modifier = Modifier.fillMaxWidth(),
                                accent = MaterialTheme.colorScheme.tertiary
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Image(
                                        painter = painterResource(Res.drawable.arcade_clan_crest),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(clan.name, fontWeight = FontWeight.Black)
                                        Text(
                                            "${clan.memberCount}/${clan.maxMembers} thành viên • ${clan.trophies} cúp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val isPending = clan.id in pendingJoinClanIds
                                    Button(
                                        onClick = { onJoinClan(clan.id) },
                                        enabled = !isPending
                                    ) {
                                        Text(if (isPending) "Đang chờ" else "Xin vào")
                                    }
                                }
                            }
                        }
                    }
                }

                if (showCreateDialog) {
                    var name by remember { mutableStateOf("") }
                    var desc by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showCreateDialog = false },
                        title = { Text("Tạo Bang Hội") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên Bang") })
                                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Mô tả") })
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                onCreateClan(name, desc)
                                showCreateDialog = false
                            }) { Text("Tạo") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateDialog = false }) { Text("Hủy") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ClanDetailView(
    serverUrl: String,
    currentUserId: String?,
    clan: ClanSnapshot,
    notice: String?,
    onLeave: () -> Unit,
    onKickMember: (String, String) -> Unit,
    onRespondJoinRequest: (String, String, Boolean) -> Unit,
    onUpdateLogo: (String) -> Unit,
    onClaimQuest: () -> Unit
) {
    var showLogoDialog by remember { mutableStateOf(false) }
    val isOwner = clan.ownerId == currentUserId
    val currentMember = clan.members.firstOrNull { it.userId == currentUserId }

    if (showLogoDialog && isOwner) {
        AlertDialog(
            onDismissRequest = { showLogoDialog = false },
            title = { Text("Chọn Logo") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(com.hienthai.fastowin.protocol.CLAN_AVATAR_IDS) { id ->
                        Surface(
                            modifier = Modifier.size(48.dp).clickable {
                                onUpdateLogo(id)
                                showLogoDialog = false
                            },
                            shape = CircleShape,
                            color = if (clan.logoId == id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Shield, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogoDialog = false }) { Text("Đóng") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ArcadeFeatureHero(
                illustration = Res.drawable.arcade_clan_crest,
                title = clan.name,
                subtitle = clan.description.ifBlank { "Cùng đồng đội chinh phục bảng xếp hạng." },
                accent = MaterialTheme.colorScheme.tertiary,
                onIllustrationClick = if (isOwner) ({ showLogoDialog = true }) else null,
                illustrationContentDescription = if (isOwner) "Đổi biểu tượng bang" else "Biểu tượng bang"
            )
        }

        notice?.let { item { ClanNotice(it) } }

        item {
            ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.tertiary) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("Tổng Cúp ${clan.members.sumOf { it.trophies }}", fontWeight = FontWeight.Black)
                }
            }
        }

        clan.quest?.let { quest ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Nhiệm vụ tuần: Thắng ${quest.target} trận",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = { (quest.progress.toFloat() / quest.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Text("${quest.progress}/${quest.target}")
                        Text("Phần thưởng", style = MaterialTheme.typography.bodySmall)
                        RewardAmounts(
                            gold = quest.rewardGold,
                            xp = quest.rewardXp,
                            gems = quest.rewardGems
                        )
                        if (currentMember?.questRewardClaimed == true) {
                            Text("Đã nhận", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        } else if (quest.progress >= quest.target) {
                            Button(onClick = onClaimQuest, modifier = Modifier.fillMaxWidth()) {
                                Text("Nhận thưởng")
                            }
                        }
                    }
                }
            }
        }

        if (isOwner && clan.joinRequests.isNotEmpty()) {
            item {
                Text(
                    "Yêu cầu tham gia (${clan.joinRequests.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(clan.joinRequests, key = { "request:${it.userId}" }) { request ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(request.displayName, fontWeight = FontWeight.Bold)
                        Text("Mã: ${request.playerCode}", style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            TextButton(onClick = {
                                onRespondJoinRequest(clan.id, request.userId, false)
                            }) { Text("Từ chối") }
                            Button(onClick = {
                                onRespondJoinRequest(clan.id, request.userId, true)
                            }) { Text("Duyệt") }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Thành viên (${clan.members.size}/${clan.maxMembers})", style = MaterialTheme.typography.titleMedium)
        }
        items(clan.members, key = { "member:${it.userId}" }) { member ->
            ListItem(
                headlineContent = { Text(member.displayName) },
                supportingContent = { Text(member.role.name) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(member.trophies.toString())
                        if (isOwner && clan.ownerId != member.userId) {
                            IconButton(onClick = { onKickMember(clan.id, member.userId) }) {
                                Icon(
                                    Icons.Rounded.PersonRemove,
                                    contentDescription = "Mời thành viên rời bang",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            )
        }
        item {
            Button(
                onClick = onLeave,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rời Bang")
            }
        }
    }
}

@Composable
private fun ClanNotice(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
