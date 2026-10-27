package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletHistorySourceTest {
    private fun title(source: String, language: AppLanguage = AppLanguage.VIETNAMESE): String =
        LocalizationService(language).text(walletSourceTextKey(source))

    @Test
    fun clanCreationAndDonationIdentifyTheExpense() {
        assertEquals("Quyên góp bang hội", title("CLAN_DONATION"))
        assertEquals("Tạo bang hội", title("CLAN_CREATION"))
        assertEquals("Clan donation", title("CLAN_DONATION", AppLanguage.ENGLISH))
        assertEquals("Clan creation", title("CLAN_CREATION", AppLanguage.ENGLISH))
    }

    @Test
    fun giftsDistinguishAdminCompensationAndWeeklyRace() {
        assertEquals("Quà từ quản trị viên", title("ADMIN_GIFT"))
        assertEquals("Quà đền bù", title("COMPENSATION"))
        assertEquals("Admin gift", title("ADMIN_GIFT", AppLanguage.ENGLISH))
        assertEquals("Compensation gift", title("COMPENSATION", AppLanguage.ENGLISH))
        assertEquals("Thưởng đua top bang tuần", title("CLAN_WEEKLY_RACE"))
        assertEquals("Weekly clan race reward", title("CLAN_WEEKLY_RACE", AppLanguage.ENGLISH))
    }

    @Test
    fun exchangeAndAchievementHaveTheirOwnTitles() {
        assertEquals("Đổi Gem sang Vàng", title("GOLD_EXCHANGE"))
        assertEquals("Thưởng thành tích", title("ACHIEVEMENT"))
        assertEquals("Exchange Gems for Gold", title("GOLD_EXCHANGE", AppLanguage.ENGLISH))
        assertEquals("Achievement reward", title("ACHIEVEMENT", AppLanguage.ENGLISH))
    }

    @Test
    fun refundAndReversedDebitRemainDistinctAccountingLegs() {
        assertEquals("Hoàn phí tham gia giải đấu", title("TOURNAMENT_ENTRY_REFUND"))
        assertEquals("Phí tham gia giải đấu đã hoàn", title("TOURNAMENT_ENTRY_REVERSED"))
        // The legacy maintenance source is also written when the host cancels a tournament.
        assertEquals("Hoàn phí khi hủy giải đấu", title("TOURNAMENT_MAINTENANCE_REFUND"))
        assertEquals("Tournament entry fee refund", title("TOURNAMENT_ENTRY_REFUND", AppLanguage.ENGLISH))
        assertEquals("Refunded tournament entry fee", title("TOURNAMENT_ENTRY_REVERSED", AppLanguage.ENGLISH))
        assertEquals("Cancelled tournament refund", title("TOURNAMENT_MAINTENANCE_REFUND", AppLanguage.ENGLISH))
    }

    @Test
    fun unknownLegacySourcesKeepAnHonestFallback() {
        listOf("", "LEGACY_ADJUSTMENT", "FUTURE_REWARD", "compensation").forEach { source ->
            assertEquals("Điều chỉnh tài sản", title(source))
            assertEquals("Resource adjustment", title(source, AppLanguage.ENGLISH))
        }
    }

    @Test
    fun existingSourcesKeepTheirLocalizedDescriptions() {
        val expected = listOf(
            Triple("DAILY_CHECK_IN", "Điểm danh hằng ngày", "Daily check-in"),
            Triple("MISSION", "Thưởng nhiệm vụ", "Mission reward"),
            Triple("MATCH", "Thưởng trận đấu", "Match reward"),
            Triple("CLAN_QUEST", "Nhiệm vụ bang hội", "Clan mission"),
            Triple("COSMETIC_PURCHASE", "Mua vật phẩm", "Item purchase"),
            Triple("TOURNAMENT_ENTRY", "Phí tham gia giải đấu", "Tournament entry fee"),
            Triple("TOURNAMENT_PRIZE", "Giải thưởng vô địch", "Tournament champion prize"),
            Triple("SEASON_REWARD", "Thưởng mùa giải", "Season reward"),
            Triple("STORE_PURCHASE", "Nạp Gem", "Gem top-up"),
        )
        expected.forEach { (source, vietnamese, english) ->
            assertEquals(vietnamese, title(source), source)
            assertEquals(english, title(source, AppLanguage.ENGLISH), source)
        }
    }
}
