package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
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
    val requests = remember { mutableMapOf<String, String>() }
    receivedPlays?.let { plays ->
        RewardReceiptDialog(RewardReceiptAmounts(plays = plays.toLong()), onDismiss = { receivedPlays = null })
    }
    offer?.let { item ->
        ArcadeDialog(
            title = localized(TextKey.PlayCredits),
            subtitle = localized(TextKey.PlayCreditConfirmation, "gems" to item.gems, "plays" to item.plays),
            onDismissRequest = { if (!pending) offer = null }
        ) {
            ArcadeActionButton(
                label = localized(TextKey.Confirm),
                enabled = !pending && exchange != null,
                onClick = {
                    if (pending || exchange == null) return@ArcadeActionButton
                    pending = true
                    notice = null
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
                },
                modifier = Modifier.fillMaxWidth()
            )
            ArcadeActionButton(localized(TextKey.Cancel), { offer = null }, Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE, enabled = !pending)
        }
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayCreditBalance(TextKey.DailyPlaysRemaining, quota?.dailyMatchesRemaining, Modifier.weight(1f))
            PlayCreditBalance(TextKey.BankedPlays, quota?.bankedMatches, Modifier.weight(1f))
        }
        PLAY_CREDIT_OFFERS.forEach { item ->
            ArcadePanel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(localized(TextKey.PlayCreditOffer, "plays" to item.plays))
                        Text("${item.gems} ${localized(TextKey.Gems)}")
                    }
                    ArcadeActionButton(
                        label = localized(TextKey.PlayCreditExchange),
                        onClick = { offer = item },
                        compact = true,
                        modifier = Modifier.widthIn(min = 96.dp, max = 124.dp),
                        enabled = exchange != null && !pending && gems >= item.gems
                    )
                }
            }
        }
        notice?.let { Text(localized(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

@Composable
private fun PlayCreditBalance(label: TextKey, amount: Number?, modifier: Modifier) {
    ArcadePanel(modifier) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(localized(label), textAlign = TextAlign.Center)
            Text(amount?.toString() ?: "—")
        }
    }
}

@Composable
internal fun GiftCodeEntry(redeem: (suspend (String) -> GiftCodeRedeemResult)?) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var code by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var redemptionError by remember { mutableStateOf<TextKey?>(null) }
    var receivedReward by remember { mutableStateOf<GiftCodeReward?>(null) }
    redemptionError?.let { error ->
        val dismiss = { redemptionError = null }
        ArcadeDialog(
            title = localized(TextKey.GiftCode),
            onDismissRequest = dismiss,
            fixedActions = {
                ArcadeActionButton(
                    label = localized(TextKey.Understood),
                    onClick = dismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Text(localized(error), style = MaterialTheme.typography.bodyMedium)
        }
    }
    receivedReward?.let { reward ->
        RewardReceiptDialog(
            RewardReceiptAmounts(
                gold = reward.gold.toLong(),
                xp = reward.xp.toLong(),
                gems = reward.gems.toLong(),
                plays = reward.plays.toLong()
            ),
            onDismiss = { receivedReward = null }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 24) code = it },
            label = { Text(localized(TextKey.GiftCode)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !pending,
            singleLine = true
        )
        ArcadeActionButton(
            label = localized(if (pending) TextKey.Loading else TextKey.GiftCodeRedeem),
            modifier = Modifier.fillMaxWidth(),
            enabled = redeem != null && !pending && code.trim().length >= 4,
            onClick = {
                if (pending || redeem == null) return@ArcadeActionButton
                focusManager.clearFocus()
                pending = true
                scope.launch {
                    try {
                        receivedReward = redeem(code).reward
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        redemptionError = when ((error as? GiftApiException)?.code) {
                            "GIFT_CODE_ALREADY_USED" -> TextKey.GiftCodeAlreadyUsed
                            "GIFT_CODE_EXPIRED" -> TextKey.GiftCodeExpired
                            "GIFT_CODE_EXHAUSTED" -> TextKey.GiftCodeExhausted
                            "GIFT_CODE_NOT_FOUND", "GIFT_CODE_STOPPED", "INVALID_REQUEST" -> TextKey.GiftCodeInvalid
                            else -> com.hienthai.fastowin.localization.giftErrorTextKey(
                                (error as? GiftApiException)?.code ?: "NETWORK_ERROR"
                            )
                        }
                    } finally {
                        pending = false
                    }
                }
            }
        )
    }
}
