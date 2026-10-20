package com.hienthai.fastowin.platform

internal enum class RewardedAdAgeTreatment { UNSPECIFIED, CHILD, TEEN }
internal data class RewardedAdPrivacyPolicy(
    val mayRequest: Boolean,
    val underAgeOfConsent: Boolean,
    val ageTreatment: RewardedAdAgeTreatment,
    val personalized: Boolean
)
internal fun RewardedAdAgeBand.privacyPolicy() = when (this) {
    RewardedAdAgeBand.UNKNOWN, RewardedAdAgeBand.UNDER_13 ->
        RewardedAdPrivacyPolicy(false, true, RewardedAdAgeTreatment.CHILD, false)
    RewardedAdAgeBand.AGE_13_15 -> RewardedAdPrivacyPolicy(true, true, RewardedAdAgeTreatment.CHILD, false)
    RewardedAdAgeBand.AGE_16_17 -> RewardedAdPrivacyPolicy(true, false, RewardedAdAgeTreatment.TEEN, false)
    RewardedAdAgeBand.ADULT -> RewardedAdPrivacyPolicy(true, false, RewardedAdAgeTreatment.UNSPECIFIED, true)
}
