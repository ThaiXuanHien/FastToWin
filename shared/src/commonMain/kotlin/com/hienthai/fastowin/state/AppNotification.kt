package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.ServerMessage

enum class AppNotificationKind {
    FRIEND_REQUEST,
    ROOM_INVITATION,
    MISSION,
    ACHIEVEMENT,
    COSMETIC,
    CLAN_INVITATION
}

enum class AppNotificationDestination { FRIENDS, PROFILE, CLAN }

data class AppNotification(
    val id: String,
    val kind: AppNotificationKind,
    val title: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val isRead: Boolean = false,
    val destination: AppNotificationDestination,
    val actionData: String? = null,
    val titleKey: String? = null,
    val titleArgs: Map<String, String> = emptyMap(),
    val messageKey: String? = null,
    val messageArgs: Map<String, String> = emptyMap(),
    val localizationData: Map<String, String> = emptyMap()
)

internal fun NotificationSnapshot.toAppNotification(localization: LocalizationService) = AppNotification(
    id = id,
    kind = AppNotificationKind.valueOf(kind.name),
    title = localizedNotificationText(localization, titleKey, titleArgs, title),
    message = localizedNotificationText(localization, messageKey, messageArgs, message),
    createdAtEpochMillis = createdAtEpochMillis,
    isRead = isRead,
    destination = AppNotificationDestination.valueOf(destination.name),
    actionData = actionData,
    titleKey = titleKey,
    titleArgs = titleArgs,
    messageKey = messageKey,
    messageArgs = messageArgs
)

internal fun AppNotification.toNotificationSnapshot() = NotificationSnapshot(
    id = id,
    kind = NotificationKind.valueOf(kind.name),
    title = title,
    message = message,
    createdAtEpochMillis = createdAtEpochMillis,
    isRead = isRead,
    destination = NotificationDestination.valueOf(destination.name),
    actionData = actionData,
    titleKey = titleKey,
    titleArgs = titleArgs,
    messageKey = messageKey,
    messageArgs = messageArgs
)

private fun localizedNotificationText(
    localization: LocalizationService,
    keyName: String?,
    arguments: Map<String, String>,
    fallback: String
): String {
    val key = keyName?.let { candidate -> TextKey.entries.firstOrNull { it.name == candidate } }
        ?: return fallback
    val localizedArguments = runCatching {
        when (key) {
            TextKey.NotificationAchievementMessage -> {
                val code = arguments.getValue("code")
                mapOf(
                    "title" to localization.achievementTitle(
                        code,
                        arguments.getValue("title"),
                        arguments["titleKey"]
                    ),
                    "description" to localization.achievementDescription(
                        code,
                        arguments.getValue("description"),
                        arguments["descriptionKey"]
                    )
                )
            }
            TextKey.NotificationCosmeticMessage -> mapOf(
                "item" to localization.cosmeticName(
                    arguments.getValue("id"),
                    arguments.getValue("name"),
                    arguments["nameKey"]
                )
            )
            TextKey.NotificationMissionMessage -> {
                val mission = MissionSnapshot(
                    code = arguments.getValue("code"),
                    title = arguments.getValue("title"),
                    progress = 1,
                    target = 1,
                    completed = true,
                    rewardXp = arguments.getValue("rewardXp").toInt(),
                    rewardGold = arguments.getValue("rewardGold").toInt(),
                    rewardGems = arguments.getValue("rewardGems").toInt(),
                    titleKey = arguments["titleKey"]
                )
                mapOf(
                    "mission" to mission.localizedTitle(localization),
                    "reward" to mission.rewardSummary(localization)
                )
            }
            else -> arguments
        }
    }.getOrElse { return fallback }
    return runCatching { localization.text(key, localizedArguments) }.getOrElse { fallback }
}

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
    nowMillis: Long,
    localization: LocalizationService
): List<AppNotification> = requests.map { request ->
    AppNotification(
        id = "friend:${request.requestId}",
        kind = AppNotificationKind.FRIEND_REQUEST,
        title = localization.text(TextKey.FriendRequests),
        message = localization.text(
            TextKey.NotificationFriendRequestMessage,
            mapOf("player" to request.displayName)
        ),
        createdAtEpochMillis = nowMillis,
        destination = AppNotificationDestination.FRIENDS,
        titleKey = TextKey.FriendRequests.name,
        messageKey = TextKey.NotificationFriendRequestMessage.name,
        messageArgs = mapOf("player" to request.displayName),
        localizationData = mapOf("type" to "friend", "player" to request.displayName)
    )
}

internal fun roomInvitationNotification(
    invitation: ServerMessage.RoomInvitation,
    nowMillis: Long,
    localization: LocalizationService
) = AppNotification(
    id = "room:${invitation.invitationId}",
    kind = AppNotificationKind.ROOM_INVITATION,
    title = localization.text(TextKey.RoomInvitationTitle),
    message = localization.text(
        TextKey.NotificationRoomInvitationMessage,
        mapOf("player" to invitation.fromDisplayName, "room" to invitation.roomName)
    ),
    createdAtEpochMillis = nowMillis,
    destination = AppNotificationDestination.FRIENDS,
    titleKey = TextKey.RoomInvitationTitle.name,
    messageKey = TextKey.NotificationRoomInvitationMessage.name,
    messageArgs = mapOf("player" to invitation.fromDisplayName, "room" to invitation.roomName),
    localizationData = mapOf(
        "type" to "room",
        "player" to invitation.fromDisplayName,
        "room" to invitation.roomName
    )
)

internal fun progressionNotifications(
    previous: PlayerProfileSnapshot?,
    current: PlayerProfileSnapshot,
    nowMillis: Long,
    localization: LocalizationService
): List<AppNotification> {
    if (previous == null) return emptyList()
    val result = mutableListOf<AppNotification>()

    val previousAchievements = previous.achievements.associateBy { it.code }
    current.achievements.filter { achievement ->
        achievement.unlocked && previousAchievements[achievement.code]?.unlocked != true
    }.forEach { achievement ->
        result += AppNotification(
            id = "achievement:${achievement.code}",
            kind = AppNotificationKind.ACHIEVEMENT,
            title = localization.text(TextKey.AchievementsTitle),
            message = localization.text(
                TextKey.NotificationAchievementMessage,
                mapOf(
                    "title" to localization.achievementTitle(
                        achievement.code,
                        achievement.title,
                        achievement.titleKey
                    ),
                    "description" to localization.achievementDescription(
                        achievement.code,
                        achievement.description,
                        achievement.descriptionKey
                    )
                )
            ),
            createdAtEpochMillis = nowMillis,
            destination = AppNotificationDestination.PROFILE,
            titleKey = TextKey.AchievementsTitle.name,
            messageKey = TextKey.NotificationAchievementMessage.name,
            messageArgs = buildMap {
                put("code", achievement.code)
                put("title", achievement.title)
                put("description", achievement.description)
                achievement.titleKey?.takeIf(String::isNotBlank)?.let { put("titleKey", it) }
                achievement.descriptionKey?.takeIf(String::isNotBlank)?.let { put("descriptionKey", it) }
            },
            localizationData = buildMap {
                put("type", "achievement")
                put("code", achievement.code)
                put("title", achievement.title)
                put("description", achievement.description)
                achievement.titleKey?.takeIf(String::isNotBlank)?.let { put("titleKey", it) }
                achievement.descriptionKey?.takeIf(String::isNotBlank)?.let { put("descriptionKey", it) }
            }
        )
    }

    val previousCosmetics = previous.progression.cosmetics.associateBy { it.id }
    current.progression.cosmetics.filter { cosmetic ->
        cosmetic.unlocked && previousCosmetics[cosmetic.id]?.unlocked != true
    }.forEach { cosmetic ->
        result += AppNotification(
            id = "cosmetic:${cosmetic.id}",
            kind = AppNotificationKind.COSMETIC,
            title = localization.text(TextKey.Unlocked),
            message = localization.text(
                TextKey.NotificationCosmeticMessage,
                mapOf("item" to localization.cosmeticName(cosmetic.id, cosmetic.name, cosmetic.nameKey))
            ),
            createdAtEpochMillis = nowMillis,
            destination = AppNotificationDestination.PROFILE,
            titleKey = TextKey.Unlocked.name,
            messageKey = TextKey.NotificationCosmeticMessage.name,
            messageArgs = buildMap {
                put("id", cosmetic.id)
                put("name", cosmetic.name)
                cosmetic.nameKey?.takeIf(String::isNotBlank)?.let { put("nameKey", it) }
            },
            localizationData = buildMap {
                put("type", "cosmetic")
                put("id", cosmetic.id)
                put("name", cosmetic.name)
                cosmetic.nameKey?.takeIf(String::isNotBlank)?.let { put("nameKey", it) }
            }
        )
    }

    result += completedMissionNotifications(
        previous.progression.dailyMissions,
        current.progression.dailyMissions,
        "daily:${nowMillis / DAY_MILLIS}",
        nowMillis,
        localization
    )
    result += completedMissionNotifications(
        previous.progression.weeklyMissions,
        current.progression.weeklyMissions,
        "weekly:${nowMillis / WEEK_MILLIS}",
        nowMillis,
        localization
    )
    return result
}

private fun completedMissionNotifications(
    previous: List<MissionSnapshot>,
    current: List<MissionSnapshot>,
    periodKey: String,
    nowMillis: Long,
    localization: LocalizationService
): List<AppNotification> {
    val previousByCode = previous.associateBy(MissionSnapshot::code)
    return current.filter { mission ->
        mission.completed && previousByCode[mission.code]?.completed == false
    }.map { mission ->
        AppNotification(
            id = "mission:$periodKey:${mission.code}",
            kind = AppNotificationKind.MISSION,
            title = localization.text(TextKey.MissionCompleted),
            message = localization.text(
                TextKey.NotificationMissionMessage,
                mapOf(
                    "mission" to mission.localizedTitle(localization),
                    "reward" to mission.rewardSummary(localization)
                )
            ),
            createdAtEpochMillis = nowMillis,
            destination = AppNotificationDestination.PROFILE,
            titleKey = TextKey.MissionCompleted.name,
            messageKey = TextKey.NotificationMissionMessage.name,
            messageArgs = buildMap {
                put("code", mission.code)
                put("title", mission.title)
                mission.titleKey?.let { put("titleKey", it) }
                put("rewardGold", mission.rewardGold.toString())
                put("rewardXp", mission.rewardXp.toString())
                put("rewardGems", mission.rewardGems.toString())
            },
            localizationData = buildMap {
                put("type", "mission")
                put("code", mission.code)
                put("title", mission.title)
                mission.titleKey?.let { put("titleKey", it) }
                put("rewardGold", mission.rewardGold.toString())
                put("rewardXp", mission.rewardXp.toString())
                put("rewardGems", mission.rewardGems.toString())
            }
        )
    }
}

internal fun AppNotification.relocalized(localization: LocalizationService): AppNotification = when (localizationData["type"]) {
    "friend" -> copy(
        title = localization.text(TextKey.FriendRequests),
        message = localization.text(
            TextKey.NotificationFriendRequestMessage,
            mapOf("player" to localizationData.getValue("player"))
        )
    )
    "room" -> copy(
        title = localization.text(TextKey.RoomInvitationTitle),
        message = localization.text(
            TextKey.NotificationRoomInvitationMessage,
            mapOf(
                "player" to localizationData.getValue("player"),
                "room" to localizationData.getValue("room")
            )
        )
    )
    "achievement" -> {
        val code = localizationData.getValue("code")
        copy(
            title = localization.text(TextKey.AchievementsTitle),
            message = localization.text(
                TextKey.NotificationAchievementMessage,
                mapOf(
                    "title" to localization.achievementTitle(
                        code,
                        localizationData.getValue("title"),
                        localizationData["titleKey"]
                    ),
                    "description" to localization.achievementDescription(
                        code,
                        localizationData.getValue("description"),
                        localizationData["descriptionKey"]
                    )
                )
            )
        )
    }
    "cosmetic" -> copy(
        title = localization.text(TextKey.Unlocked),
        message = localization.text(
            TextKey.NotificationCosmeticMessage,
            mapOf(
                "item" to localization.cosmeticName(
                    localizationData.getValue("id"),
                    localizationData.getValue("name"),
                    localizationData["nameKey"]
                )
            )
        )
    )
    "mission" -> {
        val mission = MissionSnapshot(
            code = localizationData.getValue("code"),
            title = localizationData.getValue("title"),
            progress = 1,
            target = 1,
            completed = true,
            rewardXp = localizationData.getValue("rewardXp").toInt(),
            rewardGold = localizationData.getValue("rewardGold").toInt(),
            rewardGems = localizationData.getValue("rewardGems").toInt(),
            titleKey = localizationData["titleKey"]
        )
        copy(
            title = localization.text(TextKey.MissionCompleted),
            message = localization.text(
                TextKey.NotificationMissionMessage,
                mapOf(
                    "mission" to mission.localizedTitle(localization),
                    "reward" to mission.rewardSummary(localization)
                )
            )
        )
    }
    else -> copy(
        title = localizedNotificationText(localization, titleKey, titleArgs, title),
        message = localizedNotificationText(localization, messageKey, messageArgs, message)
    )
}

private fun MissionSnapshot.rewardSummary(localization: LocalizationService): String = buildList {
    if (rewardGold > 0) add("$rewardGold ${localization.text(TextKey.Gold)}")
    if (rewardXp > 0) add("$rewardXp XP")
    if (rewardGems > 0) add("$rewardGems ${localization.text(TextKey.Gems)}")
}.joinToString(" + ")

private fun MissionSnapshot.localizedTitle(localization: LocalizationService): String {
    if (titleKey != null) {
        return TextKey.entries.firstOrNull { it.name == titleKey }
            ?.let { key -> runCatching { localization.text(key) }.getOrNull() }
            ?: title
    }
    return when (code.uppercase()) {
        "DAILY_PLAY_3" -> localization.text(TextKey.MissionPlayThree)
        "DAILY_WIN_1" -> localization.text(TextKey.MissionWinOne)
        "WEEKLY_CORRECT_100" -> localization.text(TextKey.MissionCorrectHundred)
        "WEEKLY_PERFECT_1" -> localization.text(TextKey.MissionPerfectWin)
        else -> title
    }
}

private fun LocalizationService.achievementTitle(
    code: String,
    fallback: String,
    keyName: String? = null
): String = textOrFallback(
    keyName.toTextKey() ?: when (code.uppercase()) {
        "FIRST_WIN" -> TextKey.AchievementFirstWinTitle
        "WIN_10" -> TextKey.AchievementWinTenTitle
        "PERFECT_GAME" -> TextKey.AchievementPerfectTitle
        "SPEED_50" -> TextKey.AchievementSpeedTitle
        "DAILY_STREAK_7" -> TextKey.AchievementCheckInTitle
        else -> null
    },
    fallback
)

private fun LocalizationService.achievementDescription(
    code: String,
    fallback: String,
    keyName: String? = null
): String = textOrFallback(
    keyName.toTextKey() ?: when (code.uppercase()) {
        "FIRST_WIN" -> TextKey.AchievementFirstWinDescription
        "WIN_10" -> TextKey.AchievementWinTenDescription
        "PERFECT_GAME" -> TextKey.AchievementPerfectDescription
        "SPEED_50" -> TextKey.AchievementSpeedDescription
        "DAILY_STREAK_7" -> TextKey.AchievementCheckInDescription
        else -> null
    },
    fallback
)

private fun LocalizationService.cosmeticName(
    id: String,
    fallback: String,
    keyName: String? = null
): String = textOrFallback(
    keyName.toTextKey() ?: when (id) {
        "frame_default" -> TextKey.BasicFrame
        "frame_bronze" -> TextKey.BronzeFrame
        "frame_silver" -> TextKey.SilverFrame
        "frame_gold" -> TextKey.GoldFrame
        "frame_perfect" -> TextKey.PerfectFrame
        "frame_persistent" -> TextKey.PersistentFrameName
        "title_rookie" -> TextKey.Rookie
        "title_champion" -> TextKey.Champion
        "title_speed" -> TextKey.AchievementSpeedTitle
        "title_diligent" -> TextKey.DiligentTitle
        "avatar_checkin_50" -> TextKey.SeasonRewardAvatarName
        "card_back_gold" -> TextKey.ShopItemGoldName
        "card_back_diamond" -> TextKey.ShopItemDiamondName
        "board_skin_dark" -> TextKey.ShopBoardDarkName
        "board_skin_forest" -> TextKey.ShopBoardForestName
        else -> null
    },
    fallback
)

private fun LocalizationService.textOrFallback(key: TextKey?, fallback: String): String =
    key?.let { runCatching { text(it) }.getOrNull() } ?: fallback

private fun String?.toTextKey(): TextKey? =
    this?.takeIf(String::isNotBlank)?.let { candidate ->
        TextKey.entries.firstOrNull { it.name == candidate }
    }

private const val MAX_IN_APP_NOTIFICATIONS = 100
private const val DAY_MILLIS = 86_400_000L
private const val WEEK_MILLIS = 7L * DAY_MILLIS
