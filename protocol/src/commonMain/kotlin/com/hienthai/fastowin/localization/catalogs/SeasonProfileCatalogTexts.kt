package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val seasonProfileKeys = listOf(
    TextKey.SeasonHistoryDescription, TextKey.LoadingSeasonHistory,
    TextKey.SeasonHistoryRefreshHint, TextKey.NoSeasonHistory,
    TextKey.NoSeasonHistoryDescription, TextKey.HighestTier, TextKey.HighestElo,
    TextKey.FinalElo, TextKey.FinalRank, TextKey.NotRanked,
    TextKey.SeasonRewardReceived, TextKey.SeasonRewardProcessing,
    TextKey.NoPlacementReward, TextKey.SeasonProgressPlacement,
    TextKey.EloToNextTier, TextKey.HideTierRewards, TextKey.ViewTierRewards,
    TextKey.FinishPlacementHint, TextKey.HeldReward, TextKey.ReachedHighestTier,
)

private fun seasonProfileCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == seasonProfileKeys.size) {
        "Expected ${seasonProfileKeys.size} season translations, received ${values.size}."
    }
    return seasonProfileKeys.zip(values).toMap()
}

internal val seasonProfileTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to seasonProfileCopy(
        "回顾每个赛季的最高段位、Elo 峰值和奖励。", "正在加载赛季历史…", "下拉刷新最新赛季结果。", "暂无赛季记录",
        "完成排位赛，记录你的首个赛季。", "最高段位", "最高 Elo", "赛季末 Elo", "赛季末排名", "未上榜",
        "已领取赛季奖励", "赛季奖励正在处理中。", "定位赛不足 {count} 场，本赛季没有奖励。",
        "定位赛 {played}/{required}", "距离 {tier} 还差 {count} Elo", "收起段位奖励", "查看段位奖励",
        "完成定位赛以确定段位并解锁奖励。", "当前可得奖励", "已达到的最高段位",
    ),
    AppLanguage.JAPANESE to seasonProfileCopy(
        "各シーズンの最高ランク、最高Elo、報酬を確認できます。", "シーズン履歴を読み込み中…", "下に引いて最新のシーズン結果を更新。", "シーズン履歴なし",
        "ランク戦を完了して最初のシーズンを記録しましょう。", "最高ランク", "最高Elo", "最終Elo", "最終順位", "ランク外",
        "シーズン報酬を受け取りました", "シーズン報酬を処理中です。", "認定戦が {count} 試合未満のため、このシーズンの報酬はありません。",
        "認定戦 {played}/{required} 試合", "{tier} まであと {count} Elo", "ランク報酬を閉じる", "ランク報酬を見る",
        "認定戦を終えてランクを確定し、報酬を解除しましょう。", "現在保持中の報酬", "到達した最高ランク",
    ),
    AppLanguage.KOREAN to seasonProfileCopy(
        "각 시즌의 최고 등급, 최고 Elo, 보상을 확인하세요.", "시즌 기록 불러오는 중…", "아래로 당겨 최신 시즌 결과를 새로고침하세요.", "시즌 기록 없음",
        "랭크 경기를 완료해 첫 시즌을 기록하세요.", "최고 등급", "최고 Elo", "최종 Elo", "최종 순위", "순위 없음",
        "시즌 보상 획득", "시즌 보상을 처리 중입니다.", "배치 경기가 {count}회 미만이므로 이번 시즌 보상이 없습니다.",
        "배치 경기 {played}/{required}", "{tier}까지 Elo {count} 필요", "등급 보상 숨기기", "등급 보상 보기",
        "배치 경기를 마쳐 등급을 확정하고 보상을 해금하세요.", "현재 확보한 보상", "도달한 최고 등급",
    ),
    AppLanguage.SPANISH to seasonProfileCopy(
        "Consulta tu rango más alto, Elo máximo y recompensas de cada temporada.", "Cargando historial de temporadas…", "Desliza hacia abajo para actualizar los resultados de la temporada.", "Sin historial de temporadas",
        "Completa partidas clasificatorias para registrar tu primera temporada.", "Rango más alto", "Elo máximo", "Elo final", "Posición final", "Sin clasificar",
        "Recompensa de temporada recibida", "La recompensa de temporada se está procesando.", "Con menos de {count} partidas de clasificación, esta temporada no tiene recompensa.",
        "Clasificación: {played}/{required} partidas", "Faltan {count} de Elo para llegar a {tier}", "Ocultar recompensas por rango", "Ver recompensas por rango",
        "Termina la clasificación para confirmar tu rango y desbloquear recompensas.", "Recompensa asegurada", "Rango más alto alcanzado",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to seasonProfileCopy(
        "Veja sua maior categoria, Elo máximo e recompensas de cada temporada.", "Carregando histórico de temporadas…", "Puxe para baixo para atualizar os resultados da temporada.", "Sem histórico de temporadas",
        "Conclua partidas ranqueadas para registrar sua primeira temporada.", "Maior categoria", "Elo máximo", "Elo final", "Posição final", "Sem classificação",
        "Recompensa da temporada recebida", "A recompensa da temporada está sendo processada.", "Com menos de {count} partidas de colocação, esta temporada não dá recompensa.",
        "Colocação: {played}/{required} partidas", "Faltam {count} de Elo para {tier}", "Ocultar recompensas das categorias", "Ver recompensas das categorias",
        "Conclua a colocação para definir sua categoria e desbloquear recompensas.", "Recompensa garantida", "Maior categoria alcançada",
    ),
    AppLanguage.FRENCH to seasonProfileCopy(
        "Revoyez votre meilleur palier, votre Elo maximal et vos récompenses de chaque saison.", "Chargement de l’historique des saisons…", "Tirez vers le bas pour actualiser les résultats de la saison.", "Aucun historique de saison",
        "Terminez des matchs classés pour enregistrer votre première saison.", "Meilleur palier", "Elo maximal", "Elo final", "Classement final", "Non classé",
        "Récompense de saison reçue", "La récompense de saison est en cours de traitement.", "Moins de {count} matchs de placement : aucune récompense pour cette saison.",
        "Placement : {played}/{required} matchs", "Encore {count} Elo pour atteindre {tier}", "Masquer les récompenses des paliers", "Voir les récompenses des paliers",
        "Terminez le placement pour confirmer votre palier et débloquer des récompenses.", "Récompense acquise", "Meilleur palier atteint",
    ),
    AppLanguage.GERMAN to seasonProfileCopy(
        "Sieh dir deine höchste Stufe, dein Spitzen-Elo und deine Belohnungen jeder Saison an.", "Saisonverlauf wird geladen…", "Zum Aktualisieren der neuesten Saisonergebnisse nach unten ziehen.", "Kein Saisonverlauf",
        "Schließe Ranglistenspiele ab, um deine erste Saison festzuhalten.", "Höchste Stufe", "Höchstes Elo", "End-Elo", "Endplatzierung", "Nicht platziert",
        "Saisonbelohnung erhalten", "Die Saisonbelohnung wird verarbeitet.", "Weniger als {count} Platzierungsspiele: keine Belohnung für diese Saison.",
        "Platzierung: {played}/{required} Spiele", "Noch {count} Elo bis {tier}", "Stufenbelohnungen ausblenden", "Stufenbelohnungen ansehen",
        "Schließe die Platzierung ab, um deine Stufe zu bestätigen und Belohnungen freizuschalten.", "Gesicherte Belohnung", "Höchste erreichte Stufe",
    ),
    AppLanguage.INDONESIAN to seasonProfileCopy(
        "Lihat tier tertinggi, Elo puncak, dan hadiah setiap musim.", "Memuat riwayat musim…", "Tarik ke bawah untuk memperbarui hasil musim terbaru.", "Belum ada riwayat musim",
        "Selesaikan pertandingan peringkat untuk mencatat musim pertamamu.", "Tier tertinggi", "Elo tertinggi", "Elo akhir", "Peringkat akhir", "Belum berperingkat",
        "Hadiah musim diterima", "Hadiah musim sedang diproses.", "Kurang dari {count} pertandingan penempatan, jadi musim ini tidak mendapat hadiah.",
        "Penempatan {played}/{required} pertandingan", "Butuh {count} Elo lagi untuk mencapai {tier}", "Sembunyikan hadiah tier", "Lihat hadiah tier",
        "Selesaikan penempatan untuk memastikan tier dan membuka hadiah.", "Hadiah yang sudah diamankan", "Tier tertinggi yang dicapai",
    ),
    AppLanguage.THAI to seasonProfileCopy(
        "ดูขั้นสูงสุด Elo สูงสุด และรางวัลของแต่ละฤดูกาล", "กำลังโหลดประวัติฤดูกาล…", "ดึงลงเพื่ออัปเดตผลฤดูกาลล่าสุด", "ไม่มีประวัติฤดูกาล",
        "แข่งจัดอันดับให้จบเพื่อบันทึกฤดูกาลแรกของคุณ", "ขั้นสูงสุด", "Elo สูงสุด", "Elo เมื่อจบฤดูกาล", "อันดับเมื่อจบฤดูกาล", "ไม่ติดอันดับ",
        "ได้รับรางวัลฤดูกาลแล้ว", "กำลังดำเนินการรางวัลฤดูกาล", "แข่งจัดอันดับน้อยกว่า {count} แมตช์ ฤดูกาลนี้จึงไม่มีรางวัล",
        "จัดอันดับ {played}/{required} แมตช์", "ต้องการอีก {count} Elo เพื่อถึง {tier}", "ซ่อนรางวัลตามขั้น", "ดูรางวัลตามขั้น",
        "แข่งจัดอันดับให้ครบเพื่อยืนยันขั้นและปลดล็อกรางวัล", "รางวัลที่ได้รับสิทธิ์", "ขั้นสูงสุดที่เคยถึง",
    ),
    AppLanguage.RUSSIAN to seasonProfileCopy(
        "Посмотрите свой высший ранг, пик Elo и награды каждого сезона.", "Загрузка истории сезонов…", "Потяните вниз, чтобы обновить результаты сезона.", "История сезонов пуста",
        "Завершите рейтинговые матчи, чтобы записать первый сезон.", "Высший ранг", "Максимальный Elo", "Итоговый Elo", "Итоговое место", "Без рейтинга",
        "Награда сезона получена", "Награда сезона обрабатывается.", "Меньше {count} квалификационных матчей — в этом сезоне награды нет.",
        "Квалификация: {played}/{required} матчей", "До {tier} не хватает {count} Elo", "Скрыть награды рангов", "Посмотреть награды рангов",
        "Завершите квалификацию, чтобы подтвердить ранг и открыть награды.", "Гарантированная награда", "Высший достигнутый ранг",
    ),
)
