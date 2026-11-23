package com.hienthai.fastowin.ui.components

import com.hienthai.fastowin.protocol.AvatarFrameDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerAvatarFrameTest {
    @Test
    fun `new and seasonal frames use the versioned artwork from the server catalog`() {
        val frames = listOf(
            AvatarFrameDefinition("frame_moonrise", "Trăng lên", "Moonrise", "/api/avatar-frames/frame_moonrise/image?v=2"),
            AvatarFrameDefinition("frame_gold", "Khung Vàng", "Gold frame", "/api/avatar-frames/frame_gold/image?v=3")
        ).associateBy { it.id }

        assertEquals("https://game.example/api/avatar-frames/frame_moonrise/image?v=2",
            resolveAvatarFrameImageUrl("wss://game.example/game", "frame_moonrise", frames))
        assertEquals("https://game.example/api/avatar-frames/frame_gold/image?v=3",
            resolveAvatarFrameImageUrl("wss://game.example/game", "season_26_gold", frames))
        assertEquals("", resolveAvatarFrameImageUrl("wss://game.example/game", "frame_unknown", frames))
    }

    @Test
    fun `default and legacy frame artwork come from the catalog too`() {
        val frames = listOf(
            AvatarFrameDefinition("frame_default", "Khung cơ bản", "Basic frame", "/api/avatar-frames/frame_default/image?v=0"),
            AvatarFrameDefinition("frame_perfect", "Khung Hoàn hảo", "Perfect frame", "/api/avatar-frames/frame_perfect/image?v=4")
        ).associateBy { it.id }

        assertEquals(
            "https://game.example/api/avatar-frames/frame_default/image?v=0",
            resolveAvatarFrameImageUrl("wss://game.example/game", "frame_default", frames)
        )
        assertEquals(
            "https://game.example/api/avatar-frames/frame_perfect/image?v=4",
            resolveAvatarFrameImageUrl("wss://game.example/game", "frame_perfect", frames)
        )
        assertEquals("", resolveAvatarFrameImageUrl("", "frame_default", frames))
    }
}
