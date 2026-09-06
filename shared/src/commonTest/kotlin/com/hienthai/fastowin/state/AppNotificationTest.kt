package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
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

        assertTrue(progressionNotifications(null, current, NOW, vietnamese()).isEmpty())
    }

    @Test
    fun `profile transition creates achievement cosmetic and mission notifications`() {
        val previous = profile(false, false, false)
        val current = profile(true, true, true)

        val notifications = progressionNotifications(previous, current, NOW, vietnamese())

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
        assertEquals(
            "Thắng 1 trận hôm nay • Nhận 150 Vàng + 25 XP + 2 Gem.",
            notifications.single { it.kind == AppNotificationKind.MISSION }.message
        )
    }

    @Test
    fun `social notifications are deduplicated by server id`() {
        val friend = friendRequestNotifications(
            listOf(FriendRequestSnapshot("request-1", "user-1", "Hiếu", "HIEU001")),
            NOW,
            vietnamese()
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
            NOW,
            vietnamese()
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

    @Test
    fun `generated notifications use the active language`() {
        val english = LocalizationService(AppLanguage.ENGLISH)
        val friend = friendRequestNotifications(
            listOf(FriendRequestSnapshot("request-1", "user-1", "Hieu", "HIEU001")),
            NOW,
            english
        ).single()
        val invitation = roomInvitationNotification(
            ServerMessage.RoomInvitation(
                invitationId = "invite-1",
                fromUserId = "user-2",
                fromDisplayName = "Hien",
                roomId = "room-1",
                roomName = "Fast room",
                expiresAtEpochMillis = NOW + 60_000L
            ),
            NOW,
            english
        )
        val mission = progressionNotifications(
            profile(false, false, false),
            profile(false, false, true),
            NOW,
            english
        ).single()
        val progression = progressionNotifications(
            profile(false, false, false),
            profile(true, true, false),
            NOW,
            english
        )

        assertEquals("Friend requests", friend.title)
        assertEquals("Hieu wants to be your friend.", friend.message)
        assertEquals("ROOM INVITATION", invitation.title)
        assertEquals("Hien invited you to Fast room.", invitation.message)
        assertEquals("Completed", mission.title)
        assertEquals("Win 1 match today • Claim 150 Gold + 25 XP + 2 Gems.", mission.message)
        assertEquals(
            "Ten victories: Win 10 matches",
            progression.single { it.kind == AppNotificationKind.ACHIEVEMENT }.message
        )
        assertEquals(
            "You unlocked Gold frame.",
            progression.single { it.kind == AppNotificationKind.COSMETIC }.message
        )
    }

    @Test
    fun `malformed network mission title key keeps raw title`() {
        val previous = profile(false, false, false)
        val malformedMission = previous.progression.dailyMissions.single().copy(
            title = "Legacy mission title",
            progress = 1,
            completed = true,
            titleKey = "ServerRateLimited"
        )
        val current = previous.copy(
            progression = previous.progression.copy(dailyMissions = listOf(malformedMission))
        )

        val notification = progressionNotifications(
            previous,
            current,
            NOW,
            LocalizationService(AppLanguage.ENGLISH)
        ).single()

        assertEquals("Legacy mission title • Claim 150 Gold + 25 XP + 2 Gems.", notification.message)
    }

    @Test
    fun `generated notification can be rerendered after language changes`() {
        val notification = friendRequestNotifications(
            listOf(FriendRequestSnapshot("request-1", "user-1", "Hieu", "HIEU001")),
            NOW,
            vietnamese()
        ).single()

        val english = notification.relocalized(LocalizationService(AppLanguage.ENGLISH))

        assertEquals("Friend requests", english.title)
        assertEquals("Hieu wants to be your friend.", english.message)
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
            listOf(AchievementSnapshot("WIN_10", "Thắng 10 trận", "Thắng 10 trận.", NOW))
        } else {
            emptyList()
        },
        progression = PlayerProgressionSnapshot(
            dailyMissions = listOf(
                MissionSnapshot(
                    "DAILY_WIN_1",
                    "Thắng một trận",
                    if (missionCompleted) 1 else 0,
                    1,
                    missionCompleted,
                    rewardXp = 25,
                    rewardGold = 150,
                    rewardGems = 2
                )
            ),
            cosmetics = listOf(
                CosmeticSnapshot("frame_gold", "Khung vàng", CosmeticType.FRAME, cosmeticUnlocked, false)
            )
        )
    )

    private fun vietnamese() = LocalizationService(AppLanguage.VIETNAMESE)

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
