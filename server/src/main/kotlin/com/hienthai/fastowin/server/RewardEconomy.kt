package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.GOLD_EXCHANGE_OFFERS
import com.hienthai.fastowin.protocol.GoldExchangeOffer
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.MatchRewardEventSnapshot
import com.hienthai.fastowin.protocol.MatchRewardBreakdown

internal data class RewardGrant(
    val gold: Int = 0,
    val gems: Int = 0,
    val xp: Int = 0
)

internal object RewardEconomy {
    fun matchRewardBreakdown(matchType: MatchType, outcome: MatchOutcome, intentionalLeave: Boolean,
        eventRewards: MatchRewardEventSnapshot): MatchRewardBreakdown {
        val base = matchReward(matchType, outcome, intentionalLeave)
        require(eventRewards.goldMultiplier in 1..2 && eventRewards.xpMultiplier in 1..2)
        return MatchRewardBreakdown(base.gold, base.gold * (eventRewards.goldMultiplier - 1),
            base.gold * eventRewards.goldMultiplier, base.xp, base.xp * (eventRewards.xpMultiplier - 1),
            base.xp * eventRewards.xpMultiplier, if (intentionalLeave) emptyList() else eventRewards.sources)
    }

    fun matchReward(matchType: MatchType, outcome: MatchOutcome, intentionalLeave: Boolean,
        eventRewards: MatchRewardEventSnapshot): RewardGrant =
        matchRewardBreakdown(matchType, outcome, intentionalLeave, eventRewards).let { RewardGrant(gold = it.totalGold, xp = it.totalXp) }

    fun matchReward(
        matchType: MatchType,
        outcome: MatchOutcome,
        intentionalLeave: Boolean
    ): RewardGrant {
        if (intentionalLeave) return RewardGrant()
        return when (matchType to outcome) {
            MatchType.CASUAL to MatchOutcome.WIN -> RewardGrant(gold = 80, xp = 24)
            MatchType.CASUAL to MatchOutcome.DRAW -> RewardGrant(gold = 40, xp = 16)
            MatchType.CASUAL to MatchOutcome.LOSS -> RewardGrant(gold = 20, xp = 8)
            MatchType.RANKED to MatchOutcome.WIN -> RewardGrant(gold = 100, xp = 30)
            MatchType.RANKED to MatchOutcome.DRAW -> RewardGrant(gold = 50, xp = 20)
            MatchType.RANKED to MatchOutcome.LOSS -> RewardGrant(gold = 25, xp = 10)
            else -> error("Unsupported match reward combination: $matchType/$outcome")
        }
    }

    fun goldExchange(offerId: String): GoldExchangeOffer? =
        GOLD_EXCHANGE_OFFERS.firstOrNull { it.id == offerId }
}
