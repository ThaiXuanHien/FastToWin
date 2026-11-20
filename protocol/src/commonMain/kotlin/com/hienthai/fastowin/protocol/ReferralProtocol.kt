package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

const val REFERRAL_SUMMARY_PATH = "/api/referrals"
const val REFERRAL_INVITER_GOLD = 500
const val REFERRAL_NEW_PLAYER_GOLD = 300
const val REFERRAL_MONTHLY_LIMIT = 5

private val referralCodePattern = Regex("[A-Z0-9]{1,12}")

fun normalizeReferralCode(value: String): String? =
    value.trim().uppercase().takeIf(referralCodePattern::matches)

@Serializable
data class ReferralSummary(
    val code: String,
    val pendingCount: Int,
    val rewardedCount: Int,
    val rewardedThisMonth: Int,
    val completedWithoutInviterBonusCount: Int
)
