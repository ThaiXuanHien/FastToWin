package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.ui.theme.A1Palette
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.network.GiftApiException
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun PlayCreditShop(
    quota: PlayQuotaSnapshot?,
    gems: Int,
    exchange: (suspend (String, String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var offer by remember { mutableStateOf<PlayCreditOffer?>(null) }
    var pending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<TextKey?>(null) }
    var receivedPlays by remember { mutableStateOf<Int?>(null) }
    var bankedBeforeExchange by remember { mutableStateOf<Long?>(null) }
    val requests = remember { mutableMapOf<String, String>() }
    receivedPlays?.let { plays ->
        ShopSuccessDialog(
            title = localized(TextKey.ShopExchangeSuccess),
            kind = ShopCurrency.PLAYS,
            amount = plays.toLong(),
            balanceAfter = bankedBeforeExchange?.plus(plays),
            onDismiss = { receivedPlays = null }
        )
    }
    offer?.let { item ->
        ShopConfirmSheet(
            icon = { PlayTicketIcon(size = 52.dp) },
            title = localized(TextKey.ShopPlaysOffer, "plays" to item.plays),
            subtitle = null,
            note = localized(TextKey.PlayCreditConfirmation, "gems" to item.gems, "plays" to item.plays),
            confirmLabel = localized(TextKey.Confirm).uppercase(),
            confirmTag = "play_credit_confirm",
            sheetTag = "play_credit_confirmation",
            enabled = !pending && exchange != null,
            onDismiss = { if (!pending) offer = null },
            onConfirm = {
                    if (pending || exchange == null) return@ShopConfirmSheet
                    pending = true
                    notice = null
                    bankedBeforeExchange = quota?.bankedMatches
                    scope.launch {
                        val id = requests.getOrPut(item.id) { Uuid.random().toString() }
                        try {
                            exchange(item.id, id)
                            requests.remove(item.id)
                            receivedPlays = item.plays
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            notice = when ((error as? GiftApiException)?.code) {
                                "INSUFFICIENT_GEMS" -> {
                                    requests.remove(item.id)
                                    TextKey.NotEnoughGems
                                }
                                else -> com.hienthai.fastowin.localization.giftErrorTextKey(
                                    (error as? GiftApiException)?.code ?: "NETWORK_ERROR"
                                )
                            }
                        } finally {
                            pending = false
                            offer = null
                        }
                    }
            }
        )
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Thẻ "Bạn còn N lượt": lượt hôm nay, lượt tích lũy và giờ hồi lượt.
        Row(
            Modifier.fillMaxWidth().a1Card().padding(horizontal = 16.dp, vertical = 14.dp).testTag("play_credit_balance"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlayTicketIcon(size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    localized(TextKey.ShopPlaysLeft, "count" to (quota?.dailyMatchesRemaining?.toString() ?: "—")),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = A1Palette.Ink
                )
                Text(
                    localized(
                        TextKey.ShopPlaysReset,
                        "banked" to (quota?.bankedMatches?.toString() ?: "—"),
                        "base" to (quota?.baseMatches ?: 10)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted
                )
            }
        }
        PLAY_CREDIT_OFFERS.forEach { item ->
            ShopOfferRow(
                icon = { PlayTicketIcon(size = 44.dp) },
                title = localized(TextKey.ShopPlaysOffer, "plays" to item.plays),
                subtitle = null,
                gemsCost = item.gems,
                enabled = exchange != null && !pending && gems >= item.gems,
                buttonLabel = if (gems < item.gems) localized(TextKey.NotEnoughGems)
                else localized(TextKey.ShopExchangeFor, "gems" to item.gems),
                onClick = { offer = item },
                modifier = Modifier.testTag("play_credit_offer:${item.id}"),
                buttonTag = "play_credit_exchange:${item.id}"
            )
        }
        notice?.let { ShopNotice(localized(it), isError = true) }
    }
}

/** Vé lượt chơi màu hồng (".ticket" trong thiết kế). */
@Composable
internal fun PlayTicketIcon(size: androidx.compose.ui.unit.Dp) {
    Icon(
        Icons.Rounded.ConfirmationNumber,
        contentDescription = null,
        tint = Color(0xFFFF4F7B),
        modifier = Modifier.size(size)
    )
}
