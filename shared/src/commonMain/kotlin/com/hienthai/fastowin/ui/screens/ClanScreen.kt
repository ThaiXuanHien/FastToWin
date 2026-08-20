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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import com.hienthai.fastowin.protocol.ClanSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanScreen(
    serverUrl: String,
    myClanId: String?,
    clanList: List<ClanSummarySnapshot>,
    currentClan: ClanSnapshot?,
    onCreateClan: (name: String, desc: String) -> Unit,
    onJoinClan: (String) -> Unit,
    onLeaveClan: () -> Unit,
    onSearch: (String?) -> Unit,
    onKickMember: (String, String) -> Unit,
    onUpdateLogo: (String, String) -> Unit,
    onClaimQuest: (String) -> Unit,
    onViewClan: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bang Hội") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Trở về")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (myClanId != null) {
                // Show my clan info
                if (currentClan != null) {
                    ClanDetailView(
                        serverUrl = serverUrl,
                        clan = currentClan,
                        onLeave = onLeaveClan,
                        onKickMember = onKickMember,
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

                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm bang hội...") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onSearch(searchQuery.takeIf { it.isNotBlank() }) }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Tìm kiếm")
                            }
                        }
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tạo Bang Hội")
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(clanList) { clan ->
                            ListItem(
                                headlineContent = { Text(clan.name, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("\uD83C\uDFC6  Cúp") },
                                trailingContent = {
                                    Button(onClick = { onJoinClan(clan.id) }) {
                                        Text("Tham gia")
                                    }
                                }
                            )
                            HorizontalDivider()
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
    clan: ClanSnapshot,
    onLeave: () -> Unit,
    onKickMember: (String, String) -> Unit,
    onUpdateLogo: (String) -> Unit,
    onClaimQuest: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        var showLogoDialog by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(clanEmoji(clan.logoId), style = MaterialTheme.typography.headlineLarge)
                }
            }
            Column {
                Text(clan.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(clan.description, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { showLogoDialog = true }) {
                    Text("Đổi logo")
                }
            }
        }

        if (showLogoDialog) {
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
                                    Text(clanEmoji(id), style = MaterialTheme.typography.headlineSmall)
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
        Text("🏆 Tổng Cúp: ${clan.members.sumOf { it.trophies }}")

        // Clan Quest Section
        clan.quest?.let { quest ->
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
                    Text("Thưởng: ${quest.rewardGold} Vàng", style = MaterialTheme.typography.bodySmall)
                    if (quest.progress >= quest.target) {
                        Button(onClick = onClaimQuest, modifier = Modifier.fillMaxWidth()) {
                            Text("Nhận thưởng")
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Thành viên (${clan.members.size}/${clan.maxMembers})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(clan.members) { member ->
                ListItem(
                    headlineContent = { Text(member.displayName) },
                    supportingContent = { Text(member.role.name) },
                    trailingContent = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆 ${member.trophies}")
                            if (clan.ownerId != member.userId) {
                                // Assume we have currentUserId or just pass an onKick lambda that will fail if not owner
                                IconButton(onClick = { onKickMember(clan.id, member.userId) }) {
                                    Text("👋", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                )
            }
        }
        Button(
            onClick = onLeave,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rời Bang")
        }
    }
}
private fun clanEmoji(logoId: String?): String = when (logoId) {
    "shield" -> "???"
    "sword" -> "??"
    "flag" -> "??"
    "dragon" -> "??"
    "wolf" -> "??"
    "eagle" -> "??"
    "crown" -> "??"
    else -> "???"
}
