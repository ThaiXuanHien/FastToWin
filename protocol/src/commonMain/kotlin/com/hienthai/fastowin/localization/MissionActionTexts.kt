package com.hienthai.fastowin.localization

private val missionActionKeys=listOf(TextKey.MissionActionPLAY,TextKey.MissionActionWIN,TextKey.MissionActionCORRECT,TextKey.MissionActionACCURACY,TextKey.MissionActionPERFECT_WIN,TextKey.MissionActionWIN_STREAK,TextKey.MissionActionCHECK_IN,TextKey.MissionActionDONATE_GOLD,TextKey.MissionActionDONATE_GEMS)
private fun missionCopy(vararg copy:String)=missionActionKeys.zip(copy.toList()).toMap()
internal val missionActionTexts=mapOf(
    AppLanguage.ENGLISH to missionCopy("Play {target} matches","Win {target} matches","Find {target} correct numbers","Reach {accuracy}% accuracy in {target} matches","Win {target} perfect matches","Reach a {target}-win streak","Check in {target} times","Donate {target} Gold","Donate {target} Gems"),
    AppLanguage.VIETNAMESE to missionCopy("Chơi {target} trận","Thắng {target} trận","Chọn đúng {target} số","Đạt độ chính xác {accuracy}% trong {target} trận","Thắng hoàn hảo {target} trận","Đạt chuỗi thắng {target}","Điểm danh {target} lần","Quyên góp {target} Vàng","Quyên góp {target} Gem"),
).mapValues { (language,copy) -> copy + (TextKey.MissionClanStreak to when(language) {
    AppLanguage.ENGLISH -> "{target} members reach a {streak}-win streak"
    AppLanguage.VIETNAMESE -> "{target} thành viên đạt chuỗi {streak} trận thắng"
}) }
