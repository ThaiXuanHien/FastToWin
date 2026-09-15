package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val seasonRewardSummaryKeys = listOf(
    TextKey.SeasonsPlayed, TextKey.SeasonSummaryLine,
    TextKey.HighestTierReached, TextKey.SeasonPeakElo,
    TextKey.PassedTier, TextKey.TierStartsAtElo,
    TextKey.RewardReceiptDescription, TextKey.SeasonRewardAdded,
    TextKey.SeasonSummaryTitle, TextKey.SeasonSummaryCongratulations,
    TextKey.Great, TextKey.SeasonRewardAvatarName,
    TextKey.AddedToCollection, TextKey.ExclusiveSeasonFrame,
    TextKey.ExclusiveSeasonTitle, TextKey.ExclusiveSeasonCosmetic,
    TextKey.SeasonEnded, TextKey.DaysRemaining,
    TextKey.DaysHoursRemaining, TextKey.HoursMinutesRemaining,
    TextKey.MinutesRemaining,
)

private fun seasonRewardSummaryCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == seasonRewardSummaryKeys.size) {
        "Expected ${seasonRewardSummaryKeys.size} season reward translations, received ${values.size}."
    }
    return seasonRewardSummaryKeys.zip(values).toMap()
}

internal val seasonRewardSummaryTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to seasonRewardSummaryCopy(
        "已参加 {count} 个赛季", "赛季 {season} • {matches} • {tier}", "已达到最高段位",
        "赛季最高：{elo} Elo • {tier}", "已通过", "从 {elo} Elo 开始",
        "已领取 {season} 奖励，{tier} 段位", "已添加到资源", "赛季总结",
        "恭喜！你的赛季成绩和奖励已记录。", "太棒了", "赛季奖励", "已添加到收藏",
        "赛季专属头像框", "赛季专属称号", "赛季专属外观", "赛季已结束",
        "剩余 {days} 天", "剩余 {days}天 {hours}小时", "剩余 {hours}小时 {minutes}分钟",
        "剩余 {minutes}分钟",
    ),
    AppLanguage.JAPANESE to seasonRewardSummaryCopy(
        "{count}シーズン参加", "シーズン{season} • {matches} • {tier}", "到達した最高ランク",
        "シーズン最高：{elo} Elo • {tier}", "通過済み", "{elo} Eloから",
        "{season}の報酬（{tier}ランク）を受け取りました", "リソースに追加済み", "シーズン結果",
        "おめでとうございます！シーズン成績と報酬を記録しました。", "やった！", "シーズン報酬", "コレクションに追加済み",
        "シーズン限定フレーム", "シーズン限定称号", "シーズン限定外観", "シーズン終了",
        "あと{days}日", "あと{days}日{hours}時間", "あと{hours}時間{minutes}分", "あと{minutes}分",
    ),
    AppLanguage.KOREAN to seasonRewardSummaryCopy(
        "{count}개 시즌 참가", "시즌 {season} • {matches} • {tier}", "달성한 최고 티어",
        "시즌 최고: {elo} Elo • {tier}", "통과", "{elo} Elo부터",
        "{season} 보상, {tier} 티어 수령", "자원에 추가됨", "시즌 요약",
        "축하합니다! 시즌 결과와 보상이 기록되었습니다.", "좋아요", "시즌 보상", "컬렉션에 추가됨",
        "시즌 한정 프레임", "시즌 한정 칭호", "시즌 한정 외형", "시즌 종료",
        "{days}일 남음", "{days}일 {hours}시간 남음", "{hours}시간 {minutes}분 남음", "{minutes}분 남음",
    ),
    AppLanguage.SPANISH to seasonRewardSummaryCopy(
        "{count} temporadas jugadas", "Temporada {season} • {matches} • {tier}", "Rango máximo alcanzado",
        "Máximo de temporada: {elo} Elo • {tier}", "Superado", "Desde {elo} Elo",
        "Recompensa de {season} recibida, rango {tier}", "Añadido a los recursos", "Resumen de temporada",
        "¡Enhorabuena! Se han registrado tu resultado y tus recompensas de temporada.", "Genial",
        "Recompensa de temporada", "Añadido a la colección", "Marco exclusivo de temporada",
        "Título exclusivo de temporada", "Aspecto exclusivo de temporada", "Temporada finalizada",
        "Quedan {days} días", "Quedan {days} d {hours} h", "Quedan {hours} h {minutes} min",
        "Quedan {minutes} min",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to seasonRewardSummaryCopy(
        "{count} temporadas disputadas", "Temporada {season} • {matches} • {tier}", "Maior nível alcançado",
        "Pico da temporada: {elo} Elo • {tier}", "Concluído", "A partir de {elo} Elo",
        "Recompensa de {season} recebida, nível {tier}", "Adicionado aos recursos", "Resumo da temporada",
        "Parabéns! Seu resultado e suas recompensas da temporada foram registrados.", "Ótimo",
        "Recompensa da temporada", "Adicionado à coleção", "Moldura exclusiva da temporada",
        "Título exclusivo da temporada", "Visual exclusivo da temporada", "Temporada encerrada",
        "Restam {days} dias", "Restam {days} d {hours} h", "Restam {hours} h {minutes} min",
        "Restam {minutes} min",
    ),
    AppLanguage.FRENCH to seasonRewardSummaryCopy(
        "{count} saisons disputées", "Saison {season} • {matches} • {tier}", "Palier maximal atteint",
        "Meilleur Elo de la saison : {elo} • {tier}", "Franchi", "À partir de {elo} Elo",
        "Récompense de {season} reçue, palier {tier}", "Ajouté aux ressources", "Bilan de saison",
        "Félicitations ! Votre résultat et vos récompenses de saison ont été enregistrés.", "Bravo",
        "Récompense de saison", "Ajouté à la collection", "Cadre exclusif de saison",
        "Titre exclusif de saison", "Apparence exclusive de saison", "Saison terminée",
        "Il reste {days} jours", "Il reste {days} j {hours} h", "Il reste {hours} h {minutes} min",
        "Il reste {minutes} min",
    ),
    AppLanguage.GERMAN to seasonRewardSummaryCopy(
        "{count} Saisons gespielt", "Saison {season} • {matches} • {tier}", "Höchste erreichte Stufe",
        "Saisonbestwert: {elo} Elo • {tier}", "Bestanden", "Ab {elo} Elo",
        "Belohnung für {season}, Stufe {tier}, erhalten", "Zu den Ressourcen hinzugefügt", "Saisonübersicht",
        "Glückwunsch! Dein Saisonergebnis und deine Belohnungen wurden gespeichert.", "Klasse",
        "Saisonbelohnung", "Zur Sammlung hinzugefügt", "Exklusiver Saisonrahmen",
        "Exklusiver Saisontitel", "Exklusives Saison-Design", "Saison beendet",
        "Noch {days} Tage", "Noch {days} T. {hours} Std.", "Noch {hours} Std. {minutes} Min.",
        "Noch {minutes} Min.",
    ),
    AppLanguage.INDONESIAN to seasonRewardSummaryCopy(
        "{count} musim dimainkan", "Musim {season} • {matches} • {tier}", "Tingkat tertinggi tercapai",
        "Puncak musim: {elo} Elo • {tier}", "Terlewati", "Mulai {elo} Elo",
        "Hadiah {season} diterima, tingkat {tier}", "Ditambahkan ke sumber daya", "Ringkasan musim",
        "Selamat! Hasil dan hadiah musimmu telah dicatat.", "Mantap", "Hadiah musim",
        "Ditambahkan ke koleksi", "Bingkai eksklusif musim", "Gelar eksklusif musim",
        "Tampilan eksklusif musim", "Musim berakhir", "Tersisa {days} hari",
        "Tersisa {days} h {hours} j", "Tersisa {hours} j {minutes} mnt", "Tersisa {minutes} mnt",
    ),
    AppLanguage.THAI to seasonRewardSummaryCopy(
        "เล่นแล้ว {count} ฤดูกาล", "ฤดูกาล {season} • {matches} • {tier}", "ระดับสูงสุดที่ทำได้",
        "Elo สูงสุดของฤดูกาล: {elo} • {tier}", "ผ่านแล้ว", "เริ่มที่ {elo} Elo",
        "ได้รับรางวัล {season} ระดับ {tier}", "เพิ่มในทรัพยากรแล้ว", "สรุปฤดูกาล",
        "ยินดีด้วย! ผลงานและรางวัลประจำฤดูกาลของคุณถูกบันทึกแล้ว", "เยี่ยม", "รางวัลประจำฤดูกาล",
        "เพิ่มในคอลเลกชันแล้ว", "กรอบพิเศษประจำฤดูกาล", "ฉายาพิเศษประจำฤดูกาล",
        "รูปลักษณ์พิเศษประจำฤดูกาล", "ฤดูกาลสิ้นสุดแล้ว", "เหลือ {days} วัน",
        "เหลือ {days} วัน {hours} ชม.", "เหลือ {hours} ชม. {minutes} นาที", "เหลือ {minutes} นาที",
    ),
    AppLanguage.RUSSIAN to seasonRewardSummaryCopy(
        "Сыграно сезонов: {count}", "Сезон {season} • {matches} • {tier}", "Высший достигнутый ранг",
        "Пик сезона: {elo} Elo • {tier}", "Пройдено", "От {elo} Elo",
        "Получена награда за {season}, ранг {tier}", "Добавлено к ресурсам", "Итоги сезона",
        "Поздравляем! Результат сезона и награды сохранены.", "Отлично", "Сезонная награда",
        "Добавлено в коллекцию", "Эксклюзивная сезонная рамка", "Эксклюзивный сезонный титул",
        "Эксклюзивный сезонный облик", "Сезон завершён", "Осталось {days} дн.",
        "Осталось {days} д. {hours} ч.", "Осталось {hours} ч. {minutes} мин.", "Осталось {minutes} мин.",
    ),
)
