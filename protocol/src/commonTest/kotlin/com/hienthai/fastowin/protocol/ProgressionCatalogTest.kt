package com.hienthai.fastowin.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressionCatalogTest {
    @Test
    fun `frame catalog contains the sixteen approved frames in display order`() {
        assertEquals(
            listOf(
                "frame_lightning" to "Tia Chớp",
                "frame_wildfire" to "Liệt Hỏa",
                "frame_warrior" to "Chiến Binh",
                "frame_veteran" to "Bách Chiến",
                "frame_diamond" to "Kim Cương",
                "frame_challenger" to "Thách Đấu",
                "frame_glory" to "Vinh Quang",
                "frame_unyielding" to "Bất Khuất",
                "frame_legend" to "Huyền Thoại",
                "frame_emperor" to "Đế Vương",
                "frame_speed_shadow" to "Tốc Ảnh",
                "frame_champion" to "Quán Quân",
                "frame_immortal" to "Bất Diệt",
                "frame_dragon_might" to "Long Uy",
                "frame_supreme" to "Chí Tôn",
                "frame_peerless" to "Vô Song"
            ),
            FRAME_CATALOG.map { it.id to it.fallbackName }
        )
    }

    @Test
    fun `title catalog contains the twelve approved titles in display order`() {
        assertEquals(
            listOf(
                "title_first_battle" to "Khai Chiến",
                "title_godspeed" to "Thần Tốc",
                "title_undefeated" to "Bất Bại",
                "title_veteran" to "Bách Chiến",
                "title_divine_eye" to "Mắt Thần",
                "title_pillar" to "Trụ Cột",
                "title_one_strike" to "Nhất Kích",
                "title_golden_reflex" to "Phản Xạ Vàng",
                "title_master" to "Cao Thủ",
                "title_conqueror" to "Kẻ Chinh Phục",
                "title_war_god" to "Chiến Thần",
                "title_speed_king" to "Vua Tốc Độ"
            ),
            TITLE_CATALOG.map { it.id to it.fallbackName }
        )
    }

    @Test
    fun `catalog ids and localization keys are unique`() {
        val catalog = FRAME_CATALOG + TITLE_CATALOG

        assertEquals(catalog.size, catalog.map(ProgressionCosmeticDefinition::id).distinct().size)
        assertEquals(catalog.size, catalog.map(ProgressionCosmeticDefinition::nameKey).distinct().size)
        assertEquals(
            catalog.size,
            catalog.map(ProgressionCosmeticDefinition::unlockDescriptionKey).distinct().size
        )
        assertTrue(catalog.all { it.nameKey.isNotBlank() })
        assertTrue(catalog.all { it.unlockDescriptionKey.isNotBlank() })
    }

    @Test
    fun `every approved achievement cosmetic reward resolves to a catalog entry`() {
        val approvedAchievementRewardIds = setOf(
            "frame_lightning", "frame_wildfire", "frame_warrior", "frame_veteran",
            "frame_diamond", "frame_glory", "frame_unyielding", "frame_legend",
            "frame_emperor", "frame_speed_shadow", "frame_champion", "frame_immortal",
            "frame_dragon_might", "frame_supreme", "frame_peerless",
            "title_first_battle", "title_godspeed", "title_undefeated", "title_veteran",
            "title_divine_eye", "title_pillar", "title_one_strike", "title_golden_reflex",
            "title_master", "title_conqueror", "title_war_god"
        )
        val catalogIds = (FRAME_CATALOG + TITLE_CATALOG).mapTo(mutableSetOf()) { it.id }

        assertTrue(approvedAchievementRewardIds.all(catalogIds::contains))
    }
}
