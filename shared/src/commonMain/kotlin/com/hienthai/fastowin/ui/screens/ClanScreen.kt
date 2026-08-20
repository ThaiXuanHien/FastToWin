package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import com.hienthai.fastowin.protocol.ClanSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanScreen(
    myClanId: String?,
    clanList: List<ClanSummarySnapshot>,
    currentClan: ClanSnapshot?,
    onCreateClan: (name: String, desc: String) -> Unit,
    onJoinClan: (clanId: String) -> Unit,
    onLeaveClan: () -> Unit,
    onViewClan: (clanId: String) -> Unit,
    onBack: () -> Unit
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
                        clan = currentClan,
                        onLeave = onLeaveClan
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tạo Bang Hội")
                    }
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
fun ClanDetailView(clan: ClanSnapshot, onLeave: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(clan.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(clan.description, style = MaterialTheme.typography.bodyLarge)
        Text("\uD83C\uDFC6 Tổng Cúp: ")
        HorizontalDivider()
        Text("Thành viên (/)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(clan.members) { member ->
                ListItem(
                    headlineContent = { Text(member.displayName) },
                    supportingContent = { Text(member.role.name) },
                    trailingContent = { Text("\uD83C\uDFC6 ") }
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
