package com.hienthai.fastowin.ui.components

import com.hienthai.fastowin.protocol.MODERATED_AVATAR_ID
import kotlin.test.Test
import kotlin.test.assertEquals

class UgcAvatarImageTest {
    @Test fun `suppressed avatar ignores explicit cached URL and user URL`() {
        assertEquals("", resolvePlayerAvatarImageUrl(MODERATED_AVATAR_ID, "player-1",
            "https://images.example/removed-avatar.jpg", "https://game.example", 99))
        assertEquals("", resolvePlayerAvatarImageUrl(MODERATED_AVATAR_ID, "player-1",
            "", "https://game.example", 100))
    }
    @Test fun `ordinary avatar retains explicit image and current user revision resolution`() {
        assertEquals("https://images.example/current.jpg", resolvePlayerAvatarImageUrl("avatar_male", "player-1",
            "https://images.example/current.jpg", "https://game.example", 4))
        assertEquals("https://game.example/api/avatar/player-1?v=4", resolvePlayerAvatarImageUrl(null, "player-1",
            "", "https://game.example", 4))
    }
}
