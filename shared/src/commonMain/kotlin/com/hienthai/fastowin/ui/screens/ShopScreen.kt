package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.GoldExchangeOffer
import com.hienthai.fastowin.protocol.GOLD_EXCHANGE_OFFERS
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.StoreBillingState
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.GemColor
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_shop_chest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    progression: PlayerProgressionSnapshot?,
    onClose: () -> Unit,
    gemPackages: List<GemPackageSnapshot> = emptyList(),
    billingState: StoreBillingState = StoreBillingState(com.hienthai.fastowin.protocol.StorePlatform.GOOGLE_PLAY),
    isCatalogLoading: Boolean = false,
    isAccount: Boolean = true,
    onBuyGems: (String) -> Unit = {},
    onExchangeGold: (String) -> Unit = {},
    isGoldExchangePending: Boolean = false,
    exchangeNotice: String? = null,
    unreadNotifications: Int = 0,
    onNotifications: () -> Unit = {}
) {
    SystemBackHandler(onBack = onClose)
    var selectedTab by remember { mutableStateOf("GEMS") }
    var confirmingOffer by remember { mutableStateOf<GoldExchangeOffer?>(null) }
    val tabs = listOf(
        "GEMS" to localized(TextKey.GemTab),
        "GOLD" to localized(TextKey.GoldTab)
    )
    
    val gold = progression?.gold ?: 0
    val gems = progression?.gems ?: 0
    
    confirmingOffer?.let { offer ->
        ArcadeDialog(
            title = localized(TextKey.GoldVault),
            subtitle = localized(
                TextKey.GoldExchangeConfirmation,
                "gems" to offer.gemsCost,
                "gold" to offer.goldAmount
            ),
            onDismissRequest = { if (!isGoldExchangePending) confirmingOffer = null },
            modifier = Modifier.testTag("gold_exchange_confirmation")
        ) {
            ArcadeActionButton(
                label = localized(TextKey.Confirm),
                onClick = {
                    onExchangeGold(offer.id)
                    confirmingOffer = null
                },
                enabled = !isGoldExchangePending,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth().testTag("gold_exchange_confirm")
            )
            ArcadeActionButton(
                label = localized(TextKey.Cancel),
                onClick = { confirmingOffer = null },
                enabled = !isGoldExchangePending,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            FastToWinHeader(
                title = localized(TextKey.Shop),
                gold = gold,
                gems = gems,
                unreadNotifications = unreadNotifications,
                onNotifications = onNotifications,
                onBack = onClose
            )
        }
    ) { innerPadding ->
        ResponsiveScreen(
            modifier = Modifier.padding(innerPadding),
            maxContentWidth = 920.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArcadeFeatureHero(
                    illustration = Res.drawable.arcade_shop_chest,
                    title = localized(if (selectedTab == "GEMS") TextKey.GemVault else TextKey.GoldVault),
                    subtitle = if (selectedTab == "GEMS") {
                        localized(TextKey.GemVaultDescription)
                    } else {
                        localized(TextKey.GoldVaultDescription)
                    },
                    accent = if (selectedTab == "GEMS") ArcadePalette.Mint400 else ArcadePalette.Gold400
                )
                ArcadeSegmentedControl(
                    labels = tabs.map { it.second },
                    selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
                    onSelected = { index -> selectedTab = tabs[index].first },
                    modifier = Modifier.fillMaxWidth().testTag("shop_category_tabs"),
                    itemTestTag = { index -> "shop_tab:${tabs[index].first}" }
                )

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
                    GoldExchangeList(
                        offers = GOLD_EXCHANGE_OFFERS,
                        gems = gems,
                        isAccount = isAccount,
                        isPending = isGoldExchangePending,
                        notice = exchangeNotice,
                        onExchange = { confirmingOffer = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GoldExchangeList(
    offers: List<GoldExchangeOffer>,
    gems: Int,
    isAccount: Boolean,
    isPending: Boolean,
    notice: String?,
    onExchange: (GoldExchangeOffer) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                localized(TextKey.GoldVault),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
        item {
            Text(
                localized(TextKey.GoldVaultDescription),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        notice?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        listItems(offers, key = GoldExchangeOffer::id) { offer ->
            val canExchange = isAccount && !isPending && gems >= offer.gemsCost
            ArcadePanel(
                modifier = Modifier.fillMaxWidth().testTag("gold_offer:${offer.id}"),
                accent = ArcadePalette.Gold500
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    val stack = maxWidth < 390.dp || LocalDensity.current.fontScale >= 1.35f
                    val balances: @Composable () -> Unit = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = ArcadePalette.Gold800.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, ArcadePalette.Gold400)
                            ) {
                                Icon(
                                    Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = ArcadePalette.Gold400,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    localized(offer.nameKey()),
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    localized(TextKey.GoldAmount, "count" to offer.goldAmount),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    localized(TextKey.GemAmount, "count" to offer.gemsCost),
                                    color = GemColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    val action: @Composable (Modifier) -> Unit = { actionModifier ->
                        ArcadeActionButton(
                            label = if (gems < offer.gemsCost) {
                                localized(TextKey.NotEnoughGems)
                            } else {
                                localized(TextKey.ExchangeGemsForGold)
                            },
                            onClick = { onExchange(offer) },
                            enabled = canExchange,
                            style = ArcadeActionStyle.GOLD,
                            modifier = actionModifier.testTag("gold_exchange:${offer.id}")
                        )
                    }
                    if (stack) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            balances()
                            action(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(Modifier.weight(1f)) { balances() }
                            action(Modifier.widthIn(min = 132.dp, max = 180.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun GoldExchangeOffer.nameKey(): TextKey = when (id) {
    "gold_bag" -> TextKey.GoldOfferBagName
    "gold_chest" -> TextKey.GoldOfferChestName
    else -> TextKey.GoldOfferVaultName
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
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(localized(TextKey.ChooseGemPackage), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        item {
            Text(localized(TextKey.GemPackageDescription), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isAccount) {
            item { Text(localized(TextKey.LoginToBuyGems), color = MaterialTheme.colorScheme.error) }
        }
        if (isCatalogLoading && packages.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        listItems(packages, key = GemPackageSnapshot::productId) { gemPackage ->
            val price = billingState.prices[gemPackage.productId]?.formattedPrice ?: localized(TextKey.PriceUnavailable)
            val isPurchasing = billingState.purchasingProductId == gemPackage.productId
            GemPackageCard(
                gemPackage = gemPackage,
                price = price,
                isPurchasing = isPurchasing,
                enabled = isAccount && billingState.isReady && billingState.purchasingProductId == null,
                onBuy = { onBuy(gemPackage.productId) }
            )
        }
        billingState.notice?.let { notice ->
            item { Text(localized(notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        if (billingState.notice == null) {
            billingState.rawNoticeFallback?.let { notice ->
                item { Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        billingState.error?.let { error ->
            item { Text(localized(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (billingState.error == null) {
            billingState.rawErrorFallback?.let { error ->
                item { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Text(
                localized(TextKey.EarnGemsDescription),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GemPackageCard(
    gemPackage: GemPackageSnapshot,
    price: String,
    isPurchasing: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit
) {
    ArcadePanel(
        modifier = Modifier.fillMaxWidth().testTag("gem_package_${gemPackage.productId}"),
        accent = if (gemPackage.featured) ArcadePalette.Gold500 else ArcadePalette.Mint400
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val stackContent = maxWidth < 410.dp || LocalDensity.current.fontScale >= 1.35f
            val packageInfo: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = ArcadePalette.Mint900,
                        border = BorderStroke(1.dp, ArcadePalette.Mint400)
                    ) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = null,
                            tint = GemColor,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(gemPackage.title, fontWeight = FontWeight.Black, maxLines = 2)
                        Text(localized(TextKey.GemAmount, "count" to gemPackage.gems), color = GemColor, fontWeight = FontWeight.Bold)
                        if (gemPackage.featured) {
                            Text(
                                localized(TextKey.Popular),
                                color = ArcadePalette.Gold500,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
            if (stackContent) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    packageInfo()
                    ArcadeActionButton(
                        label = if (isPurchasing) localized(TextKey.Processing) else price,
                        onClick = onBuy,
                        enabled = enabled,
                        style = if (gemPackage.featured) ArcadeActionStyle.GOLD else ArcadeActionStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) { packageInfo() }
                    ArcadeActionButton(
                        label = if (isPurchasing) localized(TextKey.Processing) else price,
                        onClick = onBuy,
                        enabled = enabled,
                        style = if (gemPackage.featured) ArcadeActionStyle.GOLD else ArcadeActionStyle.PRIMARY,
                        modifier = Modifier.widthIn(min = 132.dp, max = 178.dp)
                    )
                }
            }
        }
    }
}
