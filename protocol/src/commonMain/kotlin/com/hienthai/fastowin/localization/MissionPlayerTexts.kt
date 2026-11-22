package com.hienthai.fastowin.localization

private val missionPlayerKeys = listOf(TextKey.MissionPeriodRange, TextKey.MissionContributionPoints, TextKey.MissionRequestError, TextKey.MissionRefresh, TextKey.MissionOngoingClan)
private fun playerMissionCopy(vararg values: String) = missionPlayerKeys.zip(values.toList()).toMap()
internal val missionPlayerTexts = mapOf(
    AppLanguage.ENGLISH to playerMissionCopy("{start} → before {end} · 00:00 Vietnam time (UTC+7)", "{points} contribution points", "Unable to update missions. Try again.", "Refresh missions", "Ongoing clan mission"),
    AppLanguage.VIETNAMESE to playerMissionCopy("{start} → trước {end} · 00:00 giờ Việt Nam (UTC+7)", "{points} điểm cống hiến", "Không thể cập nhật nhiệm vụ. Hãy thử lại.", "Tải lại nhiệm vụ", "Nhiệm vụ bang đang diễn ra"),
)
