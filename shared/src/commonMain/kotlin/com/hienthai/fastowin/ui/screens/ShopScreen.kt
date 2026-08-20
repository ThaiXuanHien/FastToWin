package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.ShopItem
import com.hienthai.fastowin.protocol.SHOP_ITEMS
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    progression: PlayerProgressionSnapshot?,
    onBuy: (String) -> Unit,
    onEquip: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("CARD_BACK") }
    val tabs = listOf("CARD_BACK" to "Thẻ bài", "BOARD_SKIN" to "Bàn cờ", "AVATAR_FRAME" to "Khung", "EMOJI" to "Biểu cảm")
    
    val gold = progression?.gold ?: 0
    val gems = progression?.gems ?: 0
    
    // Derived from cosmetics list in progression
    val ownedIds = progression?.cosmetics?.filter { it.unlocked }?.map { it.id }?.toSet() ?: emptySet()
    val equippedIds = progression?.cosmetics?.mapNotNull { it.equippedId }?.toSet() ?: emptySet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cửa hàng", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Đóng")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(gold.toString(), fontWeight = FontWeight.Bold)
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(gems.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab },
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEach { (type, label) ->
                    Tab(
                        selected = selectedTab == type,
                        onClick = { selectedTab = type },
                        text = { Text(label) }
                    )
                }
            }
            
            val items = SHOP_ITEMS.filter { it.type.name == selectedTab }
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items) { item ->
                    val isOwned = item.id in ownedIds
                    val isEquipped = item.id in equippedIds
                    
                    ShopItemCard(
                        item = item,
                        isOwned = isOwned,
                        isEquipped = isEquipped,
                        canAfford = gold >= item.price,
                        onBuy = { onBuy(item.id) },
                        onEquip = { onEquip(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    isOwned: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isEquipped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Box for image/preview placeholder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.type.name == "CARD_BACK") {
                    Text("Bài", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (item.type.name == "BOARD_SKIN") {
                    Text("Bàn", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            
            if (isOwned) {
                if (isEquipped) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đang dùng", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Button(onClick = onEquip, modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("Trang bị", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Button(
                    onClick = onBuy,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    enabled = canAfford,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canAfford) Color(0xFFFFD700) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (canAfford) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(item.price.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
