package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.ServerMessage

enum class AppNotificationKind {
    FRIEND_REQUEST,
    ROOM_INVITATION,
    MISSION,
    ACHIEVEMENT,
    COSMETIC
}

enum class AppNotificationDestination { FRIENDS, PROFILE }

data class AppNotification(
    val id: String,
    val kind: AppNotificationKind,
    val title: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val isRead: Boolean = false,
    val destination: AppNotificationDestination
)

internal fun mergeNotifications(
    current: List<AppNotification>,
    incoming: List<AppNotification>,
    dismissedIds: Set<String> = emptySet()
): List<AppNotification> {
    val existingIds = current.mapTo(mutableSetOf()) { it.id }
    val uniqueIncoming = incoming.filter { it.id !in dismissedIds && existingIds.add(it.id) }
    return (uniqueIncoming + current)
        .sortedByDescending(AppNotification::createdAtEpochMillis)
        .take(MAX_IN_APP_NOTIFICATIONS)
}

internal fun friendRequestNotifications(
    requests: List<FriendRequestSnapshot>,
    nowMillis: Long
): List<AppNotification> = requests.map { request ->
    AppNotification(
        id = "friend:${request.requestId}",
        kind = AppNotificationKind.FRIEND_REQUEST,
        title = "Lời mời kết bạn",
        message = "${request.displayName} muốn kết bạn với bạn.",
        createdAtEpochMillis = nowMillis,
        destination = AppNotificationDestination.FRIENDS
    )
}

internal fun roomInvitationNotification(
    invitation: ServerMessage.RoomInvitation,
    nowMillis: Long
) = AppNotification(
    id = "room:${invitation.invitationId}",
    kind = AppNotificationKind.ROOM_INVITATION,
    title = "Lời mời vào phòng",
    message = "${invitation.fromDisplayName} mời bạn vào phòng ${invitation.roomName}.",
    createdAtEpochMillis = nowMillis,
    destination = AppNotificationDestination.FRIENDS
)

internal fun progressionNotifications(
    previous: PlayerProfileSnapshot?,
    current: PlayerProfileSnapshot,
    nowMillis: Long
): List<AppNotification> {
    if (previous == null) return emptyList()
    val result = mutableListOf<AppNotification>()

    val previousAchievementCodes = previous.achievements.mapTo(mutableSetOf()) { it.code }
    current.achievements.filterNot { it.code in previousAchievementCodes }.forEach { achievement ->
        result += AppNotification(
            id = "achievement:${achievement.code}",
            kind = AppNotificationKind.ACHIEVEMENT,
            title = "Mở khóa thành tích",
            message = "${achievement.title}: ${achievement.description}",
            createdAtEpochMillis = nowMillis,
            destination = AppNotificationDestination.PROFILE
        )
    }

    val previousCosmetics = previous.progression.cosmetics.associateBy { it.id }
    current.progression.cosmetics.filter { cosmetic ->
        cosmetic.unlocked && previousCosmetics[cosmetic.id]?.unlocked != true
    }.forEach { cosmetic ->
        result += AppNotification(
            id = "cosmetic:${cosmetic.id}",
            kind = AppNotificationKind.COSMETIC,
            title = "Mở khóa vật phẩm",
            message = "Bạn đã mở khóa ${cosmetic.name}.",
            createdAtEpochMillis = nowMillis,
            destination = AppNotificationDestination.PROFILE
        )
    }

    result += completedMissionNotifications(
        previous.progression.dailyMissions,
        current.progression.dailyMissions,
        "daily:${nowMillis / DAY_MILLIS}",
        nowMillis
    )
    result += completedMissionNotifications(
        previous.progression.weeklyMissions,
        current.progression.weeklyMissions,
        "weekly:${nowMillis / WEEK_MILLIS}",
        nowMillis
    )
    return result
}

private fun completedMissionNotifications(
    previous: List<MissionSnapshot>,
    current: List<MissionSnapshot>,
    periodKey: String,
    nowMillis: Long
): List<AppNotification> {
    val previousByCode = previous.associateBy(MissionSnapshot::code)
    return current.filter { mission ->
        mission.completed && previousByCode[mission.code]?.completed == false
    }.map { mission ->
        AppNotification(
            id = "mission:$periodKey:${mission.code}",
            kind = AppNotificationKind.MISSION,
            title = "Hoàn thành nhiệm vụ",
            message = mission.title,
            createdAtEpochMillis = nowMillis,
            destination = AppNotificationDestination.PROFILE
        )
    }
}

private const val MAX_IN_APP_NOTIFICATIONS = 100
private const val DAY_MILLIS = 86_400_000L
private const val WEEK_MILLIS = 7L * DAY_MILLIS
