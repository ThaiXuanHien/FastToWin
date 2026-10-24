package com.hienthai.fastowin.localization

private val missionPlayerKeys = listOf(TextKey.MissionPeriodRange, TextKey.MissionContributionPoints, TextKey.MissionRequestError, TextKey.MissionRefresh, TextKey.MissionOngoingClan)
private fun playerMissionCopy(vararg values: String) = missionPlayerKeys.zip(values.toList()).toMap()
internal val missionPlayerTexts = mapOf(
    AppLanguage.ENGLISH to playerMissionCopy("{start} → before {end}", "{points} contribution points", "Unable to update missions. Try again.", "Refresh missions", "Ongoing clan mission"),
    AppLanguage.VIETNAMESE to playerMissionCopy("{start} → trước {end}", "{points} điểm cống hiến", "Không thể cập nhật nhiệm vụ. Hãy thử lại.", "Tải lại nhiệm vụ", "Nhiệm vụ bang đang diễn ra"),
    AppLanguage.SIMPLIFIED_CHINESE to playerMissionCopy("{start} → {end} 之前", "{points} 贡献点", "无法更新任务。请重试。", "刷新任务", "进行中的公会任务"),
    AppLanguage.JAPANESE to playerMissionCopy("{start} → {end} より前", "貢献ポイント {points}", "ミッションを更新できません。再試行してください。", "ミッションを更新", "進行中のクランミッション"),
    AppLanguage.KOREAN to playerMissionCopy("{start} → {end} 이전", "기여 점수 {points}", "미션을 업데이트할 수 없습니다. 다시 시도하세요.", "미션 새로고침", "진행 중인 클랜 미션"),
    AppLanguage.SPANISH to playerMissionCopy("{start} → antes de {end}", "{points} puntos de contribución", "No se pueden actualizar las misiones. Inténtalo de nuevo.", "Actualizar misiones", "Misión de clan en curso"),
    AppLanguage.BRAZILIAN_PORTUGUESE to playerMissionCopy("{start} → antes de {end}", "{points} pontos de contribuição", "Não foi possível atualizar as missões. Tente novamente.", "Atualizar missões", "Missão de clã em andamento"),
    AppLanguage.FRENCH to playerMissionCopy("{start} → avant le {end}", "{points} points de contribution", "Impossible de mettre à jour les missions. Réessayez.", "Actualiser les missions", "Mission de clan en cours"),
    AppLanguage.GERMAN to playerMissionCopy("{start} → vor {end}", "{points} Beitragspunkte", "Missionen konnten nicht aktualisiert werden. Versuche es erneut.", "Missionen aktualisieren", "Laufende Clanmission"),
    AppLanguage.INDONESIAN to playerMissionCopy("{start} → sebelum {end}", "{points} poin kontribusi", "Misi tidak dapat diperbarui. Coba lagi.", "Muat ulang misi", "Misi klan berlangsung"),
    AppLanguage.THAI to playerMissionCopy("{start} → ก่อน {end}", "{points} คะแนนผลงาน", "อัปเดตภารกิจไม่ได้ โปรดลองอีกครั้ง", "โหลดภารกิจใหม่", "ภารกิจกิลด์ที่ดำเนินอยู่"),
    AppLanguage.RUSSIAN to playerMissionCopy("{start} → до {end}", "{points} очков вклада", "Не удалось обновить задания. Повторите попытку.", "Обновить задания", "Текущее задание клана")
)
