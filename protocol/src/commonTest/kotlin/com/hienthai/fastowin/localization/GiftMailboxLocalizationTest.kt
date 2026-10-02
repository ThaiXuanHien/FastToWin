package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GiftMailboxLocalizationTest {
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
