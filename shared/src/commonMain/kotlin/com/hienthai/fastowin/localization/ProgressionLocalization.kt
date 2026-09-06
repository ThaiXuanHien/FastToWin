package com.hienthai.fastowin.localization

import androidx.compose.runtime.Composable
import com.hienthai.fastowin.protocol.RankedTier

@Composable
fun localizedRankedTierName(tier: RankedTier): String = localized(rankedTierTextKey(tier))

fun rankedTierTextKey(tier: RankedTier): TextKey =
    when (tier) {
        RankedTier.BRONZE -> TextKey.RankBronze
        RankedTier.SILVER -> TextKey.RankSilver
        RankedTier.GOLD -> TextKey.RankGold
        RankedTier.PLATINUM -> TextKey.RankPlatinum
        RankedTier.DIAMOND -> TextKey.RankDiamond
        RankedTier.MASTER -> TextKey.RankMaster
        RankedTier.CHALLENGER -> TextKey.RankChallenger
    }
