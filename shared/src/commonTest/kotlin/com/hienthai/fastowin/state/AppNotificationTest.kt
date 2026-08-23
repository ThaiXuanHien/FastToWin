package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppNotificationTest {
    @Test
    fun `first profile load does not repeat historical unlocks`() {
        val current = profile(
            achievementUnlocked = true,
            cosmeticUnlocked = true,
            missionCompleted = true
        )

        assertTrue(progressionNotifications(null, current, NOW).isEmpty())
    }

    @Test
    fun `profile transition creates achievement cosmetic and mission notifications`() {
        val previous = profile(false, false, false)
        val current = profile(true, true, true)

        val notifications = progressionNotifications(previous, current, NOW)

        assertEquals(
            setOf(
                AppNotificationKind.ACHIEVEMENT,
                AppNotificationKind.COSMETIC,
                AppNotificationKind.MISSION
            ),
            notifications.mapTo(mutableSetOf()) { it.kind }
        )
        assertEquals(3, notifications.size)
        assertTrue(notifications.all { !it.isRead })
    }

    @Test
    fun `social notifications are deduplicated by server id`() {
        val friend = friendRequestNotifications(
            listOf(FriendRequestSnapshot("request-1", "user-1", "Hiếu", "HIEU001")),
            NOW
        ).single()
        val invitation = roomInvitationNotification(
            ServerMessage.RoomInvitation(
                invitationId = "invite-1",
                fromUserId = "user-2",
                fromDisplayName = "Hiền",
                roomId = "room-1",
                roomName = "Phòng vui",
                expiresAtEpochMillis = NOW + 60_000L
            ),
            NOW
        )

        val firstMerge = mergeNotifications(emptyList(), listOf(friend, invitation))
        val secondMerge = mergeNotifications(firstMerge, listOf(friend, invitation))

        assertEquals(2, secondMerge.size)
        assertEquals(setOf("friend:request-1", "room:invite-1"), secondMerge.mapTo(mutableSetOf()) { it.id })
        assertEquals(
            listOf(invitation),
            mergeNotifications(emptyList(), listOf(friend, invitation), dismissedIds = setOf(friend.id))
        )
    }

    private fun profile(
        achievementUnlocked: Boolean,
        cosmeticUnlocked: Boolean,
        missionCompleted: Boolean
    ) = PlayerProfileSnapshot(
        userId = "player-1",
        displayName = "Player",
        playerCode = "PLAYER001",
        achievements = if (achievementUnlocked) {
            listOf(AchievementSnapshot("first_win", "Chiến thắng đầu tiên", "Thắng một trận", NOW))
        } else {
            emptyList()
        },
        progression = PlayerProgressionSnapshot(
            dailyMissions = listOf(
                MissionSnapshot("daily_win", "Thắng một trận", if (missionCompleted) 1 else 0, 1, missionCompleted)
            ),
            cosmetics = listOf(
                CosmeticSnapshot("gold_frame", "Khung vàng", CosmeticType.FRAME, cosmeticUnlocked, false)
            )
        )
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
