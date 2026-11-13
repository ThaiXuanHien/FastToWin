package com.hienthai.fastowin.localization

import com.hienthai.fastowin.protocol.MissionSnapshot

/** Legacy snapshots already carry a rendered title when template arguments are unavailable. */
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
