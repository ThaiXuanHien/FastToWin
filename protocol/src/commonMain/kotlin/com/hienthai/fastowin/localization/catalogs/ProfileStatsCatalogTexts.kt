package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val profileStatsKeys = listOf(
    TextKey.WinRate, TextKey.AccuracyLabel, TextKey.RecentFormTen,
    TextKey.EloChange, TextKey.PointsCount, TextKey.Overview,
    TextKey.Losses, TextKey.DrawsLabel, TextKey.HighScore,
    TextKey.CorrectWrongLabel, TextKey.ModeStatistics,
    TextKey.AchievementsTitle, TextKey.NoAchievements,
    TextKey.Frames, TextKey.CurrentSessionDuration,
    TextKey.MissionCorrectHundred, TextKey.MissionPerfectWin,
    TextKey.WeekMonday, TextKey.WeekTuesday, TextKey.WeekWednesday,
    TextKey.WeekThursday, TextKey.WeekFriday,
    TextKey.WeekSaturday, TextKey.WeekSunday,
)

private fun profileStatsCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == profileStatsKeys.size) {
        "Expected ${profileStatsKeys.size} profile statistics translations, received ${values.size}."
    }
    return profileStatsKeys.zip(values).toMap()
}

internal val profileStatsTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to profileStatsCopy(
        "胜率", "准确率", "最近 10 场", "Elo 变化", "{count} 分", "总览",
        "失败", "平局", "最高分", "正确 / 错误", "各模式统计", "成就",
        "尚未解锁任何成就。", "头像框", "本次登录时长 {duration}",
        "本周正确找到 100 个数字", "无误触赢得 1 场比赛",
        "周一", "周二", "周三", "周四", "周五", "周六", "周日",
    ),
    AppLanguage.JAPANESE to profileStatsCopy(
        "勝率", "正確率", "直近10試合", "Eloの変動", "{count}ポイント", "概要",
        "敗北", "引き分け", "最高得点", "正解 / ミス", "モード別の統計", "実績",
        "解除済みの実績はまだありません。", "フレーム", "現在のセッション {duration}",
        "今週、数字を100回正しく見つける", "ミスせずに1試合勝利する",
        "月", "火", "水", "木", "金", "土", "日",
    ),
    AppLanguage.KOREAN to profileStatsCopy(
        "승률", "정확도", "최근 10경기", "Elo 변동", "{count}점", "개요",
        "패배", "무승부", "최고 점수", "정답 / 오답", "모드별 통계", "업적",
        "아직 해금한 업적이 없습니다.", "프레임", "현재 접속 시간 {duration}",
        "이번 주 숫자 100개를 정확히 찾기", "오답 없이 1경기 승리하기",
        "월", "화", "수", "목", "금", "토", "일",
    ),
    AppLanguage.SPANISH to profileStatsCopy(
        "Porcentaje de victorias", "Precisión", "Últimas 10 partidas", "Cambios de Elo", "{count} puntos", "Resumen",
        "Derrotas", "Empates", "Puntuación máxima", "Aciertos / Errores", "Estadísticas por modo", "Logros",
        "Aún no has desbloqueado logros.", "Marcos", "Sesión actual: {duration}",
        "Encuentra 100 números correctos esta semana", "Gana 1 partida sin tocar un número incorrecto",
        "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to profileStatsCopy(
        "Taxa de vitórias", "Precisão", "Últimas 10 partidas", "Mudanças no Elo", "{count} pontos", "Visão geral",
        "Derrotas", "Empates", "Maior pontuação", "Acertos / Erros", "Estatísticas por modo", "Conquistas",
        "Nenhuma conquista desbloqueada ainda.", "Molduras", "Sessão atual: {duration}",
        "Encontre 100 números corretos nesta semana", "Vença 1 partida sem tocar em um número errado",
        "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom",
    ),
    AppLanguage.FRENCH to profileStatsCopy(
        "Taux de victoire", "Précision", "10 dernières parties", "Variations d'Elo", "{count} points", "Vue d'ensemble",
        "Défaites", "Égalités", "Meilleur score", "Justes / Erreurs", "Statistiques par mode", "Succès",
        "Aucun succès débloqué pour le moment.", "Cadres", "Session en cours : {duration}",
        "Trouvez 100 bons nombres cette semaine", "Gagnez 1 partie sans toucher un mauvais nombre",
        "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim",
    ),
    AppLanguage.GERMAN to profileStatsCopy(
        "Siegquote", "Genauigkeit", "Letzte 10 Matches", "Elo-Änderungen", "{count} Punkte", "Übersicht",
        "Niederlagen", "Unentschieden", "Höchstpunktzahl", "Richtig / Falsch", "Statistik nach Modus", "Erfolge",
        "Noch keine Erfolge freigeschaltet.", "Rahmen", "Aktuelle Sitzung: {duration}",
        "Finde diese Woche 100 Zahlen richtig", "Gewinne 1 Match ohne falsches Antippen",
        "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So",
    ),
    AppLanguage.INDONESIAN to profileStatsCopy(
        "Tingkat kemenangan", "Akurasi", "10 pertandingan terakhir", "Perubahan Elo", "{count} poin", "Ringkasan",
        "Kekalahan", "Seri", "Skor tertinggi", "Benar / Salah", "Statistik per mode", "Pencapaian",
        "Belum ada pencapaian yang terbuka.", "Bingkai", "Sesi saat ini: {duration}",
        "Temukan 100 angka dengan benar minggu ini", "Menangi 1 pertandingan tanpa salah ketuk",
        "Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min",
    ),
    AppLanguage.THAI to profileStatsCopy(
        "อัตราชนะ", "ความแม่นยำ", "10 นัดล่าสุด", "การเปลี่ยนแปลง Elo", "{count} คะแนน", "ภาพรวม",
        "แพ้", "เสมอ", "คะแนนสูงสุด", "ถูก / ผิด", "สถิติแยกตามโหมด", "ความสำเร็จ",
        "ยังไม่ปลดล็อกความสำเร็จ", "กรอบ", "เซสชันปัจจุบัน: {duration}",
        "เลือกตัวเลขถูก 100 ครั้งในสัปดาห์นี้", "ชนะ 1 นัดโดยไม่กดผิด",
        "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.", "อา.",
    ),
    AppLanguage.RUSSIAN to profileStatsCopy(
        "Процент побед", "Точность", "Последние 10 матчей", "Изменения Elo", "{count} очков", "Обзор",
        "Поражения", "Ничьи", "Рекордный счёт", "Верно / Ошибки", "Статистика по режимам", "Достижения",
        "Пока нет открытых достижений.", "Рамки", "Текущий сеанс: {duration}",
        "Найдите 100 правильных чисел за неделю", "Победите в 1 матче без ошибочного нажатия",
        "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс",
    ),
)
