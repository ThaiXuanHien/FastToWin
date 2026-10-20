package com.hienthai.fastowin.platform

import kotlin.test.*

class RewardedAdPrivacyPolicyTest {
    @Test fun `unknown and under13 never authorize SDK initialization or ad requests`() {
        assertFalse(RewardedAdAgeBand.UNKNOWN.privacyPolicy().mayRequest)
        assertFalse(RewardedAdAgeBand.UNDER_13.privacyPolicy().mayRequest)
    }
    @Test fun `under16 uses under consent treatment and all minors disable personalized ads`() {
        assertEquals(RewardedAdPrivacyPolicy(true, true, RewardedAdAgeTreatment.CHILD, false), RewardedAdAgeBand.AGE_13_15.privacyPolicy())
        assertEquals(RewardedAdPrivacyPolicy(true, false, RewardedAdAgeTreatment.TEEN, false), RewardedAdAgeBand.AGE_16_17.privacyPolicy())
        assertEquals(RewardedAdPrivacyPolicy(true, false, RewardedAdAgeTreatment.UNSPECIFIED, true), RewardedAdAgeBand.ADULT.privacyPolicy())
    }
}
