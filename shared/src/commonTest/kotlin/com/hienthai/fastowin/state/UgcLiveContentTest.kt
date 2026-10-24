package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.ui.components.resolvePlayerAvatarImageUrl
import kotlin.test.*

class UgcLiveContentTest {
    @Test fun `avatar removal immediately blocks only the exact target in this account state`() {
        val owner = GameState().withContentModerationChange(UgcTargetType.PLAYER_AVATAR, "target")
        assertEquals("", resolvePlayerAvatarImageUrl(null, "target", "https://cached.example/old", "https://game.example", 4,
            owner.suppressedAvatarIds))
        assertEquals("https://cached.example/other", resolvePlayerAvatarImageUrl(null, "other", "https://cached.example/other", "https://game.example", 4,
            owner.suppressedAvatarIds))
        assertTrue(GameState().suppressedAvatarIds.isEmpty())
        assertEquals(setOf("target"), owner.invalidatedAvatarIds)
    }
    @Test fun `fresh authoritative avatar removes the temporary block but never reuses stale cached content`() {
        val blocked = GameState().withContentModerationChange(UgcTargetType.PLAYER_AVATAR, "target")
        val stillBlocked = blocked.withAuthoritativeAvatars(listOf("target" to MODERATED_AVATAR_ID))
        assertEquals(setOf("target"), stillBlocked.suppressedAvatarIds)
        val replaced = stillBlocked.withAuthoritativeAvatars(listOf("target" to "new-avatar"))
        assertTrue(replaced.suppressedAvatarIds.isEmpty())
        assertEquals(setOf("target"), replaced.invalidatedAvatarIds)
    }
    @Test fun `removing another kind of content cannot hide a player's unrelated avatar`() {
        val state = GameState().withContentModerationChange(UgcTargetType.ROOM, "target")
        assertTrue(state.suppressedAvatarIds.isEmpty())
        assertTrue(state.invalidatedAvatarIds.isEmpty())
    }
}
