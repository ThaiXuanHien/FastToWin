package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.hienthai.fastowin.protocol.ShopItem
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.SHOP_ITEMS
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.platform.StoreBillingState
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.GemColor
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_shop_chest
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    progression: PlayerProgressionSnapshot?,
    onBuy: (String) -> Unit,
    onEquip: (String) -> Unit,
    onClose: () -> Unit,
    gemPackages: List<GemPackageSnapshot> = emptyList(),
    billingState: StoreBillingState = StoreBillingState(com.hienthai.fastowin.protocol.StorePlatform.GOOGLE_PLAY),
    isCatalogLoading: Boolean = false,
    isAccount: Boolean = true,
    onBuyGems: (String) -> Unit = {},
    unreadNotifications: Int = 0,
    onNotifications: () -> Unit = {}
) {
    SystemBackHandler(onBack = onClose)
    var selectedTab by remember { mutableStateOf("CARD_BACK") }
    val tabs = listOf(
        "GEMS" to "Gem",
        "CARD_BACK" to "Mặt bài",
        "BOARD_SKIN" to "Bàn số",
        "FRAME" to "Khung",
        "EMOJI" to "Biểu cảm"
    )
    
    val gold = progression?.gold ?: 0
    val gems = progression?.gems ?: 0
    
    // Derived from cosmetics list in progression
    val ownedIds = progression?.cosmetics?.filter { it.unlocked }?.map { it.id }?.toSet() ?: emptySet()
    val equippedIds = progression?.cosmetics?.filter { it.equipped }?.map { it.id }?.toSet() ?: emptySet()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            FastToWinHeader(
                title = "Cửa hàng",
                gold = gold,
                gems = gems,
                unreadNotifications = unreadNotifications,
                onNotifications = onNotifications,
                onBack = onClose
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeFeatureHero(
                illustration = Res.drawable.arcade_shop_chest,
                title = "Kho báu Arcade",
                subtitle = "Mở khóa diện mạo mới và tạo dấu ấn riêng trong mỗi trận đấu.",
                modifier = Modifier.padding(horizontal = 16.dp),
                accent = ArcadePalette.Gold400
            )
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab },
                edgePadding = 16.dp,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
            
            if (selectedTab == "GEMS") {
                GemStorePreview(
                    packages = gemPackages,
                    billingState = billingState,
                    isCatalogLoading = isCatalogLoading,
                    isAccount = isAccount,
                    onBuy = onBuyGems,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val items = SHOP_ITEMS.filter { it.type.name == selectedTab }
                if (items.isEmpty()) {
                    ArcadeEmptyState(
                        illustration = Res.drawable.arcade_shop_chest,
                        title = "Đang nhập hàng",
                        description = "Vật phẩm mới sẽ sớm xuất hiện tại quầy này.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
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
    }
}

@Composable
private fun GemStorePreview(
    packages: List<GemPackageSnapshot>,
    billingState: StoreBillingState,
    isCatalogLoading: Boolean,
    isAccount: Boolean,
    onBuy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Kho Gem", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Text("Gem mở khóa vật phẩm hiếm. Giá được hiển thị theo tài khoản Store của bạn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isAccount) {
            item { Text("Đăng nhập để mua và đồng bộ Gem trên các thiết bị.", color = MaterialTheme.colorScheme.error) }
        }
        if (isCatalogLoading && packages.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        listItems(packages, key = GemPackageSnapshot::productId) { gemPackage ->
            val price = billingState.prices[gemPackage.productId]?.formattedPrice ?: "Chưa có giá"
            val isPurchasing = billingState.purchasingProductId == gemPackage.productId
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("gem_package_${gemPackage.productId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = GemColor, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(gemPackage.title, fontWeight = FontWeight.Bold)
                            if (gemPackage.featured) {
                                SuggestionChip(onClick = {}, enabled = false, label = { Text("Phổ biến") })
                            }
                        }
                        Text("${gemPackage.gems} Gem", color = GemColor, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onBuy(gemPackage.productId) },
                        enabled = isAccount && billingState.isReady && billingState.purchasingProductId == null
                    ) {
                        if (isPurchasing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (isPurchasing) "Đang xử lý" else price)
                    }
                }
            }
        }
        billingState.notice?.let { notice ->
            item { Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        billingState.error?.let { error ->
            item { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text(
                "Bạn cũng có thể săn Gem qua điểm danh, nhiệm vụ khó và thành tích đặc biệt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                ShopItemPreview(item)
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
                        containerColor = if (canAfford) ArcadePalette.Gold400 else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (canAfford) ArcadePalette.Navy900 else MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun ShopItemPreview(item: ShopItem) {
    val isGold = item.id.contains("gold")
    val accent = when {
        isGold -> ArcadePalette.Gold400
        item.id.contains("diamond") -> ArcadePalette.Blue300
        item.id.contains("forest") -> ArcadePalette.Mint400
        else -> ArcadePalette.Violet400
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(ArcadePalette.Navy950, accent.copy(alpha = 0.52f))
                )
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when (item.type) {
            CosmeticType.CARD_BACK -> {
                Surface(
                    modifier = Modifier.fillMaxHeight(0.88f).aspectRatio(0.72f),
                    shape = RoundedCornerShape(14.dp),
                    color = accent,
                    border = BorderStroke(3.dp, Color.White.copy(alpha = 0.78f)),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isGold) "28" else "37",
                            style = MaterialTheme.typography.headlineLarge,
                            color = ArcadePalette.Navy950,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            CosmeticType.BOARD_SKIN -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(3) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            repeat(3) { column ->
                                Surface(
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    shape = RoundedCornerShape(7.dp),
                                    color = if ((row + column) % 2 == 0) accent else ArcadePalette.Navy800,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            (row * 3 + column + 1).toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> Image(
                painter = painterResource(Res.drawable.arcade_shop_chest),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
