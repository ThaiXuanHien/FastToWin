package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable
data class PlayCreditOffer(val id: String, val gems: Int, val plays: Int)

val PLAY_CREDIT_OFFERS = listOf(PlayCreditOffer("plays_5", 20, 5), PlayCreditOffer("plays_15", 50, 15))

@Serializable
data class PlayCreditExchangeRequest(val offerId: String, val requestId: String)

@Serializable
data class GiftCodeRedeemRequest(val code: String)

@Serializable
data class GiftCodeReward(val gold: Int = 0, val xp: Int = 0, val gems: Int = 0, val plays: Int = 0)

@Serializable
data class GiftCodeRedeemResult(val reward: GiftCodeReward)
