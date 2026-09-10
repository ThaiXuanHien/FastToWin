package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.GOLD_EXCHANGE_OFFERS
import com.hienthai.fastowin.protocol.GoldExchangeOffer
import com.hienthai.fastowin.protocol.MatchType

internal data class RewardGrant(
    val gold: Int = 0,
    val gems: Int = 0,
    val xp: Int = 0
)

internal object RewardEconomy {
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
