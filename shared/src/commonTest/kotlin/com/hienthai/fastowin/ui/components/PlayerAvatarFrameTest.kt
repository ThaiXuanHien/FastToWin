package com.hienthai.fastowin.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerAvatarFrameTest {
    @Test
    fun `each progression frame selects its dedicated arcade asset`() {
        val expected = mapOf(
            "frame_lightning" to "arcade_frame_lightning",
            "frame_wildfire" to "arcade_frame_wildfire",
            "frame_warrior" to "arcade_frame_warrior",
            "frame_veteran" to "arcade_frame_veteran",
            "frame_diamond" to "arcade_frame_diamond",
            "frame_challenger" to "arcade_frame_challenger",
            "frame_glory" to "arcade_frame_glory",
            "frame_unyielding" to "arcade_frame_unyielding",
            "frame_legend" to "arcade_frame_legend",
            "frame_emperor" to "arcade_frame_emperor",
            "frame_speed_shadow" to "arcade_frame_speed_shadow",
            "frame_champion" to "arcade_frame_champion",
            "frame_immortal" to "arcade_frame_immortal",
            "frame_dragon_might" to "arcade_frame_dragon_might",
            "frame_supreme" to "arcade_frame_supreme",
            "frame_peerless" to "arcade_frame_peerless"
        )

        assertEquals(expected, expected.keys.associateWith(::avatarFrameAssetName))
    }

    @Test
    fun `legacy and unknown frame ids retain their compatible assets`() {
        assertEquals("arcade_frame_perfect", avatarFrameAssetName("frame_perfect"))
        assertEquals("arcade_frame_persistent", avatarFrameAssetName("frame_persistent"))
        assertEquals("arcade_frame_gold", avatarFrameAssetName("season_1_gold"))
        assertEquals("arcade_frame_default", avatarFrameAssetName("unknown_frame"))
    }
}
