package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class GiftMailboxLocalizationTest {
    @Test fun `compensation label identifies stored incident and exact round in each language`() {
        // Lookup keeps the test compilable before the additive catalog key exists.
        val key = assertNotNull(TextKey.entries.find { it.name == "GiftCompensationRound" })
        val args = mapOf("incident" to "INC-DEMO_{count}", "round" to Int.MAX_VALUE)
        assertEquals("Đền bù · INC-DEMO_{count} · Đợt 2147483647",
            LocalizationService(AppLanguage.VIETNAMESE).text(key, args))
        assertEquals("Compensation · INC-DEMO_{count} · Round 2147483647",
            LocalizationService(AppLanguage.ENGLISH).text(key, args))
    }

    @Test fun `reward descriptions retain zero and maximum exact amounts`() {
        for (language in selectableAppLanguages) {
            val localization = LocalizationService(language)
            for (count in listOf(0, Int.MAX_VALUE)) {
                assertTrue(localization.text(TextKey.GiftGoldReward, mapOf("count" to count)).startsWith(count.toString()))
                assertEquals("$count XP", localization.text(TextKey.GiftXpReward, mapOf("count" to count)))
            }
        }
    }

    @Test fun `mailbox actions and server errors have Vietnamese and English copy`() {
        val vi = LocalizationService(AppLanguage.VIETNAMESE)
        val en = LocalizationService(AppLanguage.ENGLISH)
        assertEquals("Hộp thư quà", vi.text(TextKey.GiftMailboxTitle))
        assertEquals("Gift mailbox", en.text(TextKey.GiftMailboxTitle))
        assertEquals("Nhận tất cả", vi.text(TextKey.GiftClaimAll))
        assertEquals("Claim all", en.text(TextKey.GiftClaimAll))
        assertTrue(vi.text(giftErrorTextKey("CONFLICT")).contains("cập nhật"))
        assertTrue(en.text(giftErrorTextKey("STORAGE_UNAVAILABLE")).contains("unavailable"))
        assertEquals(TextKey.GiftErrorGeneric, giftErrorTextKey("UNEXPECTED"))
    }
}
