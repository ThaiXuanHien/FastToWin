package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.StoreBillingState
import com.hienthai.fastowin.protocol.GOLD_EXCHANGE_OFFERS
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.GoldExchangeOffer
import com.hienthai.fastowin.protocol.GoldExchangeStatus
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.ui.components.A1BottomSheet
import com.hienthai.fastowin.ui.components.A1CoinIcon
import com.hienthai.fastowin.ui.components.A1GemIcon
import com.hienthai.fastowin.ui.components.A1HeroTone
import com.hienthai.fastowin.ui.components.A1OutlinedText
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.A1Palette

enum class ShopTab { GEMS, GOLD, PLAYS }

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
    onNotifications: () -> Unit = {},
    initialTab: ShopTab = ShopTab.GEMS,
    quota: com.hienthai.fastowin.protocol.PlayQuotaSnapshot? = null,
    onExchangePlays: (suspend (String, String) -> Unit)? = null,
    goldExchangeResult: ServerMessage.GoldExchangeResult? = null,
    onDismissGoldExchangeResult: (String) -> Unit = {},
    storePurchaseResult: ServerMessage.StorePurchaseResult? = null,
    onDismissStorePurchaseResult: (String) -> Unit = {},
    /** Mở "Lịch sử tài sản" (liên kết "Lịch sử mua" cuối tab Gem); null thì ẩn liên kết. */
    onOpenPurchaseHistory: (() -> Unit)? = null
) {
    SystemBackHandler(onBack = onClose)
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var confirmingOffer by remember { mutableStateOf<GoldExchangeOffer?>(null) }
    var confirmingPackage by remember { mutableStateOf<GemPackageSnapshot?>(null) }
    var acknowledgedGoldRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var acknowledgedStoreRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val gold = progression?.gold ?: 0
    val gems = progression?.gems ?: 0
    // Số dư lúc bấm xác nhận: "trước → sau" trong hộp thành công không phụ thuộc việc hồ sơ đã kịp làm mới chưa.
    var goldBeforeExchange by remember { mutableStateOf<Long?>(null) }
    var gemsBeforePurchase by remember { mutableStateOf<Long?>(null) }

    goldExchangeResult?.takeIf {
        it.status == GoldExchangeStatus.GRANTED && it.receivedGold > 0 && it.requestId != acknowledgedGoldRequest
    }?.let { receipt ->
        ShopSuccessDialog(
            title = localized(TextKey.ShopExchangeSuccess),
            kind = ShopCurrency.GOLD,
            amount = receipt.receivedGold.toLong(),
            balanceAfter = goldBeforeExchange?.plus(receipt.receivedGold),
            onDismiss = {
                acknowledgedGoldRequest = receipt.requestId
                onDismissGoldExchangeResult(receipt.requestId)
            }
        )
    }
    storePurchaseResult?.takeIf {
        it.status == com.hienthai.fastowin.protocol.StorePurchaseStatus.GRANTED &&
            it.gemsGranted > 0 && it.requestId != acknowledgedStoreRequest
    }?.let { receipt ->
        ShopSuccessDialog(
            title = localized(TextKey.ShopPurchaseSuccess),
            kind = ShopCurrency.GEMS,
            amount = receipt.gemsGranted.toLong(),
            balanceAfter = gemsBeforePurchase?.plus(receipt.gemsGranted),
            onDismiss = {
                acknowledgedStoreRequest = receipt.requestId
                onDismissStorePurchaseResult(receipt.requestId)
            }
        )
    }

    confirmingPackage?.let { gemPackage ->
        val price = billingState.prices[gemPackage.productId]?.formattedPrice ?: localized(TextKey.PriceUnavailable)
        ShopConfirmSheet(
            icon = { A1GemIcon(size = 54.dp) },
            title = localized(TextKey.GemAmount, "count" to groupThousands(gemPackage.gems.toLong())),
            subtitle = if (gemPackage.featured) localized(TextKey.ShopMostPopular) else null,
            note = localized(TextKey.ShopPaymentNote),
            confirmLabel = localized(TextKey.ShopBuyWithPrice, "price" to price),
            confirmTag = "gem_purchase_confirm",
            sheetTag = "gem_purchase_confirmation",
            enabled = isAccount && billingState.isReady && billingState.purchasingProductId == null,
            onConfirm = {
                confirmingPackage = null
                gemsBeforePurchase = gems.toLong()
                onBuyGems(gemPackage.productId)
            },
            onDismiss = { confirmingPackage = null }
        )
    }
    confirmingOffer?.let { offer ->
        ShopConfirmSheet(
            icon = { A1CoinIcon(size = 54.dp) },
            title = localized(TextKey.GoldAmount, "count" to groupThousands(offer.goldAmount.toLong())),
            subtitle = null,
            note = localized(TextKey.GoldExchangeConfirmation, "gems" to offer.gemsCost, "gold" to groupThousands(offer.goldAmount.toLong())),
            confirmLabel = localized(TextKey.Confirm).uppercase(),
            confirmTag = "gold_exchange_confirm",
            sheetTag = "gold_exchange_confirmation",
            enabled = !isGoldExchangePending,
            onConfirm = {
                goldBeforeExchange = gold.toLong()
                onExchangeGold(offer.id)
                confirmingOffer = null
            },
            onDismiss = { if (!isGoldExchangePending) confirmingOffer = null }
        )
    }

    val tabs = listOf(
        ShopTab.GEMS to localized(TextKey.GemTab),
        ShopTab.GOLD to localized(TextKey.GoldTab),
        ShopTab.PLAYS to localized(TextKey.PlayCredits)
    )
    Column(Modifier.fillMaxSize()) {
        FastToWinHeader(
            modifier = Modifier.testTag("shop_header"),
            title = localized(TextKey.Shop),
            gold = gold,
            gems = gems,
            unreadNotifications = unreadNotifications,
            onNotifications = onNotifications,
            onBack = onClose,
            // Thiết kế A1: cửa hàng chỉ có tiêu đề và số dư, không có chuông.
            showNotifications = false
        )
        ResponsiveScreen(
            modifier = Modifier.weight(1f),
            maxContentWidth = 920.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ArcadeSegmentedControl(
                    labels = tabs.map { it.second },
                    selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
                    onSelected = { index -> selectedTab = tabs[index].first },
                    modifier = Modifier.fillMaxWidth().testTag("shop_category_tabs"),
                    itemTestTag = { index -> "shop_tab:${tabs[index].first}" }
                )
                when (selectedTab) {
                    ShopTab.GEMS -> GemStore(
                        packages = gemPackages,
                        billingState = billingState,
                        isCatalogLoading = isCatalogLoading,
                        isAccount = isAccount,
                        onBuy = { confirmingPackage = it },
                        onOpenPurchaseHistory = onOpenPurchaseHistory
                    )
                    ShopTab.GOLD -> GoldExchangeList(
                        offers = GOLD_EXCHANGE_OFFERS,
                        gems = gems,
                        isAccount = isAccount,
                        isPending = isGoldExchangePending,
                        notice = exchangeNotice,
                        onExchange = { confirmingOffer = it }
                    )
                    ShopTab.PLAYS -> PlayCreditShop(quota, gems, onExchangePlays)
                }
            }
        }
    }
}

// ---------- Gem ----------

@Composable
private fun GemStore(
    packages: List<GemPackageSnapshot>,
    billingState: StoreBillingState,
    isCatalogLoading: Boolean,
    isAccount: Boolean,
    onBuy: (GemPackageSnapshot) -> Unit,
    onOpenPurchaseHistory: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!isAccount) ShopNotice(localized(TextKey.LoginToBuyGems), isError = true)
        if (isCatalogLoading && packages.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                LocalDensity.current.fontScale >= 1.5f -> 1
                maxWidth >= 600.dp -> 3
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                packages.withIndex().chunked(columns).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { (index, gemPackage) ->
                            val isPurchasing = billingState.purchasingProductId == gemPackage.productId
                            GemPackageCard(
                                gemPackage = gemPackage,
                                pileSize = (index + 1).coerceAtMost(4),
                                price = if (isPurchasing) localized(TextKey.Processing)
                                else billingState.prices[gemPackage.productId]?.formattedPrice ?: localized(TextKey.PriceUnavailable),
                                enabled = isAccount && billingState.isReady && billingState.purchasingProductId == null,
                                onBuy = { onBuy(gemPackage) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        billingState.notice?.let { ShopNotice(localized(it), isError = false) }
        if (billingState.notice == null) billingState.rawNoticeFallback?.let { ShopNotice(it, isError = false) }
        billingState.error?.let { ShopNotice(localized(it), isError = true) }
        if (billingState.error == null) billingState.rawErrorFallback?.let { ShopNotice(it, isError = true) }
        // Thẻ ghi chú: giá theo cửa hàng ứng dụng, Gem được cộng ngay khi giao dịch xác nhận.
        Column(
            Modifier.fillMaxWidth().a1Card(depth = 0.dp, background = Color(0xFFF5FAFF)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShopInfoRow(Icons.Rounded.Info, A1Palette.Blue2, localized(TextKey.ShopPriceNote))
            ShopInfoRow(Icons.Rounded.VerifiedUser, A1Palette.Green2, localized(TextKey.ShopGemsInstant))
        }
        if (onOpenPurchaseHistory != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                A1OutlinedText(
                    localized(TextKey.ShopPurchaseHistory),
                    style = MaterialTheme.typography.titleMedium,
                    fill = A1Palette.Green1,
                    outline = A1Palette.GreenDeep,
                    outlineWidth = 2.5.dp,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onOpenPurchaseHistory)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("shop_purchase_history")
                )
            }
        }
    }
}

@Composable
private fun GemPackageCard(
    gemPackage: GemPackageSnapshot,
    pileSize: Int,
    price: String,
    enabled: Boolean,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mọi thẻ chừa 12dp phía trên cho nhãn "Phổ biến nhất" để hai thẻ cùng hàng thẳng nhau.
    Box(modifier.padding(top = 12.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .testTag("gem_package_${gemPackage.productId}")
                .a1Card(
                    background = if (gemPackage.featured) Color(0xFFFFF9E6) else A1Palette.Surface,
                    border = if (gemPackage.featured) A1Palette.YellowBorder else A1Palette.Line
                )
                .padding(start = 14.dp, end = 14.dp, top = 22.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GemPile(pileSize)
            Text(
                localized(TextKey.GemAmount, "count" to groupThousands(gemPackage.gems.toLong())),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 30.sp),
                color = A1Palette.Ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            ArcadeActionButton(
                label = price,
                onClick = onBuy,
                enabled = enabled,
                style = ArcadeActionStyle.SUCCESS,
                modifier = Modifier.fillMaxWidth().testTag("gem_buy_${gemPackage.productId}")
            )
        }
        if (gemPackage.featured) {
            A1Tag(
                localized(TextKey.ShopMostPopular),
                A1TagTone.YELLOW,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-12).dp)
            )
        }
    }
}

/** Đống ngọc 1–4 viên như thiết kế (gói càng lớn càng nhiều ngọc). */
@Composable
private fun GemPile(count: Int, size: Dp = 36.dp) {
    Box(Modifier.height(size * 1.6f), contentAlignment = Alignment.BottomCenter) {
        Row(horizontalArrangement = Arrangement.spacedBy(-(size * 0.2f))) {
            repeat(count.coerceIn(1, 4)) { i ->
                val lifted = count >= 3 && i == count / 2
                A1GemIcon(size = size, modifier = Modifier.offset(y = if (lifted) -(size * 0.5f) else 0.dp))
            }
        }
    }
}

@Composable
private fun ShopInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = A1Palette.Ink)
    }
}

@Composable
internal fun ShopNotice(message: String, isError: Boolean) {
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isError) Color(0xFFFFE0E0) else Color(0xFFDFF8D0), RoundedCornerShape(14.dp))
            .border(2.dp, if (isError) Color(0xFFF08A8A) else Color(0xFF6CC24A), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (isError) A1Palette.RedDeep else A1Palette.GreenDeep
    )
}

// ---------- Vàng ----------

@Composable
private fun GoldExchangeList(
    offers: List<GoldExchangeOffer>,
    gems: Int,
    isAccount: Boolean,
    isPending: Boolean,
    notice: String?,
    onExchange: (GoldExchangeOffer) -> Unit
) {
    val baseRate = offers.firstOrNull()?.let { it.goldAmount.toDouble() / it.gemsCost.coerceAtLeast(1) } ?: 1.0
    Column(Modifier.fillMaxWidth().testTag("gold_exchange_list"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        A1OutlinedText(
            localized(TextKey.ShopGoldCaption),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
            outlineWidth = 2.5.dp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        notice?.let { ShopNotice(it, isError = false) }
        if (!isAccount) ShopNotice(localized(TextKey.LoginToBuyGems), isError = true)
        offers.forEach { offer ->
            val bonus = ((offer.goldAmount / offer.gemsCost.coerceAtLeast(1).toDouble() / baseRate - 1) * 100).let { kotlin.math.round(it).toInt() }
            ShopOfferRow(
                icon = { A1CoinIcon(size = 48.dp) },
                title = localized(TextKey.GoldAmount, "count" to groupThousands(offer.goldAmount.toLong())),
                subtitle = if (bonus > 0) localized(TextKey.ShopBonusPercent, "percent" to bonus) else null,
                gemsCost = offer.gemsCost,
                enabled = isAccount && !isPending && gems >= offer.gemsCost,
                buttonLabel = if (gems < offer.gemsCost) localized(TextKey.NotEnoughGems)
                else localized(TextKey.ShopExchangeFor, "gems" to offer.gemsCost),
                onClick = { onExchange(offer) },
                modifier = Modifier.testTag("gold_offer:${offer.id}"),
                buttonTag = "gold_exchange:${offer.id}"
            )
        }
    }
}

/** Dòng ưu đãi (vàng / lượt chơi): icon, tên + phần thưởng thêm, nút xanh có giá Gem. */
@Composable
internal fun ShopOfferRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    gemsCost: Int,
    enabled: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonTag: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .a1Card()
            .heightIn(min = 76.dp)
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.sp),
                color = A1Palette.Ink
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
            }
        }
        ArcadeActionButton(
            label = buttonLabel,
            onClick = onClick,
            enabled = enabled,
            style = ArcadeActionStyle.SUCCESS,
            compact = true,
            modifier = Modifier
                // Nút tự giãn hết chiều ngang nên phải chặn bề rộng (gem + tối đa 3 chữ số).
                .widthIn(min = 92.dp, max = 112.dp)
                .semantics { contentDescription = buttonLabel }
                .then(buttonTag?.let { Modifier.testTag(it) } ?: Modifier)
        ) {
            Row(
                Modifier.clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                A1GemIcon(size = 20.dp)
                Text(
                    gemsCost.toString(),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalContentColor.current
                )
            }
        }
    }
}

// ---------- Hộp thoại ----------

/** Bảng xác nhận mua/đổi (".sheet" Xác nhận mua): icon, tên gói, ghi chú, nút xanh + Huỷ. */
@Composable
internal fun ShopConfirmSheet(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    note: String,
    confirmLabel: String,
    confirmTag: String,
    sheetTag: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    A1BottomSheet(title = "", onDismissRequest = onDismiss, modifier = Modifier.testTag(sheetTag)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            icon()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = A1Palette.Ink)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
                }
            }
        }
        Text(
            note,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, A1Palette.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = A1Palette.Ink
        )
        ArcadeActionButton(
            label = confirmLabel,
            onClick = onConfirm,
            enabled = enabled,
            style = ArcadeActionStyle.SUCCESS,
            large = true,
            modifier = Modifier.fillMaxWidth().testTag(confirmTag)
        )
        ArcadeActionButton(
            label = localized(TextKey.Cancel),
            onClick = onDismiss,
            style = ArcadeActionStyle.OUTLINE,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal enum class ShopCurrency(val receiptTag: String) { GEMS("gems"), GOLD("gold"), PLAYS("plays") }

/** "Mua thành công!": ô dấu tích xanh, số được cộng, số dư trước → sau, nút TUYỆT VỜI. */
@Composable
internal fun ShopSuccessDialog(
    title: String,
    kind: ShopCurrency,
    amount: Long,
    /** Số dư sau khi cộng; null khi chưa chắc số dư đã cập nhật (chỉ hiện "+N"). */
    balanceAfter: Long?,
    onDismiss: () -> Unit
) {
    val unit = localized(
        when (kind) {
            ShopCurrency.GEMS -> TextKey.Gems
            ShopCurrency.GOLD -> TextKey.Gold
            ShopCurrency.PLAYS -> TextKey.BankedPlays
        }
    )
    ArcadeDialog(
        title = title,
        subtitle = localized(TextKey.ShopAddedToAccount, "amount" to groupThousands(amount), "unit" to unit),
        onDismissRequest = onDismiss,
        heroIcon = Icons.Rounded.Check,
        heroTone = A1HeroTone.GREEN,
        modifier = Modifier.testTag("reward_receipt_dialog")
    ) {
        Row(
            Modifier
                .testTag("reward_receipt_${kind.receiptTag}")
                .background(A1Palette.Surface, RoundedCornerShape(18.dp))
                .border(2.dp, A1Palette.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (kind) {
                ShopCurrency.GEMS -> A1GemIcon(size = 24.dp)
                ShopCurrency.GOLD -> A1CoinIcon(size = 24.dp)
                ShopCurrency.PLAYS -> PlayTicketIcon(size = 24.dp)
            }
            Text(
                balanceAfter?.let { "${groupThousands((it - amount).coerceAtLeast(0))} → ${groupThousands(it)}" }
                    ?: "+${groupThousands(amount)}",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = A1Palette.Ink
            )
        }
        ArcadeActionButton(
            label = localized(TextKey.ShopAwesome),
            onClick = onDismiss,
            style = ArcadeActionStyle.GOLD,
            large = true,
            modifier = Modifier.fillMaxWidth().testTag("reward_receipt_close")
        )
    }
}

internal fun groupThousands(value: Long): String {
    val raw = kotlin.math.abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (value < 0) "-$raw" else raw
}
