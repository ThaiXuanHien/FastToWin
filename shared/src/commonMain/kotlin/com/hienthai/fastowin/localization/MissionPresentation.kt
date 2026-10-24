package com.hienthai.fastowin.localization

import com.hienthai.fastowin.protocol.*

fun LocalizationService.missionTitle(mission: MissionInstanceView): String {
    val key = TextKey.entries.firstOrNull { it.name == mission.titleKey }
        ?: TextKey.entries.firstOrNull { it.name == "MissionAction${mission.action.name}" }
        ?: return mission.code
    val arguments = mission.titleArguments.toMutableMap().apply {
        put("target", mission.target.toString())
        mission.accuracyPercent?.let { put("accuracy", it.toString()) }
        mission.streakThreshold?.let { put("streak", it.toString()) }
    }
    return runCatching { text(key, arguments) }.getOrElse { mission.code }
}

/** Legacy snapshots cannot reconstruct missing accuracy/streak parameters truthfully. */
fun LocalizationService.missionTitle(mission: MissionSnapshot): String {
    val key = mission.titleKey?.let { name -> TextKey.entries.firstOrNull { it.name == name } }
        ?: when (mission.code) {
            "DAILY_PLAY_3" -> TextKey.MissionPlayThree
            "DAILY_WIN_1" -> TextKey.MissionWinOne
            "WEEKLY_CORRECT_100" -> TextKey.MissionCorrectHundred
            "WEEKLY_PERFECT_1" -> TextKey.MissionPerfectWin
            else -> null
        }
    return key?.let { runCatching { text(it) }.getOrNull() } ?: mission.title
}

fun LocalizationService.missionPeriod(mission: MissionInstanceView): String =
    if (mission.isImportedClanMission && mission.periodStart == "1970-01-01") text(TextKey.MissionOngoingClan)
    else text(TextKey.MissionPeriodRange, mapOf("start" to mission.periodStart, "end" to mission.periodEndExclusive))

// Historical imports omit this metadata; the reserved legacy source only records ranked wins.
val MissionInstanceView.displayMatchType: MatchType?
    get() = matchType ?: if (isImportedClanMission) MatchType.RANKED else null
private val MissionInstanceView.isImportedClanMission: Boolean
    get() = scope == MissionScope.CLAN && code == "LEGACY_CLAN_QUEST"
