package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val roomMatchKeys = listOf(
    TextKey.GameRooms, TextKey.Ready, TextKey.YouAreHost,
    TextKey.TeammateNumber, TextKey.OpponentNumber,
    TextKey.LocalPlayerLabel, TextKey.OpponentLabel,
    TextKey.GameDefaultRoom, TextKey.YourTeam, TextKey.OpponentTeam,
    TextKey.PaceProgress, TextKey.SpeedValue, TextKey.CorrectWrongSummary,
    TextKey.Measuring, TextKey.Fastest, TextKey.Slowest, TextKey.TurnRange,
    TextKey.DrawResult, TextKey.FinalMatch, TextKey.SemifinalMatch,
    TextKey.OpponentLeft, TextKey.WaitingForResponse,
    TextKey.Invited, TextKey.SendingShort,
)

private fun roomMatchCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == roomMatchKeys.size) {
        "Expected ${roomMatchKeys.size} room and match translations, received ${values.size}."
    }
    return roomMatchKeys.zip(values).toMap()
}

internal val roomMatchTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to roomMatchCopy(
        "游戏房间", "已准备", "你将成为房主。", "队友 {number}", "对手 {number}",
        "你", "对手", "50 数字对局", "你的队伍", "对手队伍",
        "进度 {current}/{total}", "速度 {speed}", "正确 {correct} · 错误 {wrong}",
        "测量中", "最快", "最慢", "第 {start}–{end} 回合",
        "平局！", "决赛", "半决赛", "对手已离开", "等待回应", "已邀请", "发送中…",
    ),
    AppLanguage.JAPANESE to roomMatchCopy(
        "対戦ルーム", "準備完了", "あなたがホストになります。", "チームメイト {number}", "対戦相手 {number}",
        "あなた", "対戦相手", "50数字対戦", "あなたのチーム", "相手チーム",
        "ペース {current}/{total}", "速さ {speed}", "正解 {correct} · ミス {wrong}",
        "計測中", "最速", "最遅", "第{start}～{end}ターン",
        "引き分け！", "決勝", "準決勝", "相手が退出", "返答待ち", "招待済み", "送信中…",
    ),
    AppLanguage.KOREAN to roomMatchCopy(
        "게임방", "준비 완료", "방장이 됩니다.", "팀원 {number}", "상대 {number}",
        "나", "상대", "숫자 50개 경기", "우리 팀", "상대 팀",
        "진행 {current}/{total}", "속도 {speed}", "정답 {correct} · 오답 {wrong}",
        "측정 중", "가장 빠름", "가장 느림", "{start}–{end}턴",
        "무승부!", "결승전", "준결승전", "상대가 나갔습니다", "응답 대기 중", "초대 완료", "보내는 중…",
    ),
    AppLanguage.SPANISH to roomMatchCopy(
        "Salas de juego", "Preparado", "Serás el anfitrión.", "Compañero {number}", "Rival {number}",
        "TÚ", "RIVAL", "Partida de 50 números", "TU EQUIPO", "EQUIPO RIVAL",
        "RITMO {current}/{total}", "VELOCIDAD {speed}", "Aciertos {correct} · Errores {wrong}",
        "Midiendo", "Más rápido", "Más lento", "Turnos {start}–{end}",
        "¡EMPATE!", "Gran final", "Semifinal", "EL RIVAL SE FUE", "ESPERANDO RESPUESTA", "INVITACIÓN ENVIADA", "Enviando…",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to roomMatchCopy(
        "Salas de jogo", "Pronto", "Você será o anfitrião.", "Colega de equipe {number}", "Adversário {number}",
        "VOCÊ", "ADVERSÁRIO", "Partida de 50 números", "SUA EQUIPE", "EQUIPE ADVERSÁRIA",
        "RITMO {current}/{total}", "VELOCIDADE {speed}", "Acertos {correct} · Erros {wrong}",
        "Medindo", "Mais rápido", "Mais lento", "Turnos {start}–{end}",
        "EMPATE!", "Grande final", "Semifinal", "O ADVERSÁRIO SAIU", "AGUARDANDO RESPOSTA", "CONVITE ENVIADO", "Enviando…",
    ),
    AppLanguage.FRENCH to roomMatchCopy(
        "Salons de jeu", "Prêt", "Vous serez l'hôte du salon.", "Coéquipier {number}", "Adversaire {number}",
        "VOUS", "ADVERSAIRE", "Partie à 50 nombres", "VOTRE ÉQUIPE", "ÉQUIPE ADVERSE",
        "RYTHME {current}/{total}", "VITESSE {speed}", "Justes {correct} · Erreurs {wrong}",
        "Mesure en cours", "Le plus rapide", "Le plus lent", "Tours {start}–{end}",
        "ÉGALITÉ !", "Grande finale", "Demi-finale", "L'ADVERSAIRE EST PARTI", "EN ATTENTE DE RÉPONSE", "INVITATION ENVOYÉE", "Envoi…",
    ),
    AppLanguage.GERMAN to roomMatchCopy(
        "Spielräume", "Bereit", "Du wirst der Gastgeber sein.", "Teammitglied {number}", "Gegner {number}",
        "DU", "GEGNER", "Match mit 50 Zahlen", "DEIN TEAM", "GEGNERISCHES TEAM",
        "TEMPO {current}/{total}", "GESCHWINDIGKEIT {speed}", "Richtig {correct} · Falsch {wrong}",
        "Messung läuft", "Am schnellsten", "Am langsamsten", "Züge {start}–{end}",
        "UNENTSCHIEDEN!", "Finale", "Halbfinale", "GEGNER HAT VERLASSEN", "WARTE AUF ANTWORT", "EINGELADEN", "Wird gesendet…",
    ),
    AppLanguage.INDONESIAN to roomMatchCopy(
        "Ruang permainan", "Siap", "Kamu akan menjadi pemilik ruang.", "Rekan tim {number}", "Lawan {number}",
        "KAMU", "LAWAN", "Pertandingan 50 angka", "TIM KAMU", "TIM LAWAN",
        "TEMPO {current}/{total}", "KECEPATAN {speed}", "Benar {correct} · Salah {wrong}",
        "Mengukur", "Paling cepat", "Paling lambat", "Giliran {start}–{end}",
        "SERI!", "Babak final", "Babak semifinal", "LAWAN KELUAR", "MENUNGGU JAWABAN", "SUDAH DIUNDANG", "Mengirim…",
    ),
    AppLanguage.THAI to roomMatchCopy(
        "ห้องเล่น", "พร้อม", "คุณจะเป็นเจ้าของห้อง", "เพื่อนร่วมทีม {number}", "คู่แข่ง {number}",
        "คุณ", "คู่แข่ง", "แมตช์ 50 ตัวเลข", "ทีมของคุณ", "ทีมคู่แข่ง",
        "จังหวะ {current}/{total}", "ความเร็ว {speed}", "ถูก {correct} · ผิด {wrong}",
        "กำลังวัด", "เร็วที่สุด", "ช้าที่สุด", "รอบที่ {start}–{end}",
        "เสมอ!", "รอบชิงชนะเลิศ", "รอบรองชนะเลิศ", "คู่แข่งออกแล้ว", "รอการตอบกลับ", "เชิญแล้ว", "กำลังส่ง…",
    ),
    AppLanguage.RUSSIAN to roomMatchCopy(
        "Игровые комнаты", "Готов", "Вы будете хозяином комнаты.", "Союзник {number}", "Соперник {number}",
        "ВЫ", "СОПЕРНИК", "Матч на 50 чисел", "ВАША КОМАНДА", "КОМАНДА СОПЕРНИКА",
        "ТЕМП {current}/{total}", "СКОРОСТЬ {speed}", "Верно {correct} · Ошибок {wrong}",
        "Измерение", "Самый быстрый", "Самый медленный", "Ходы {start}–{end}",
        "НИЧЬЯ!", "Финал турнира", "Полуфинал", "СОПЕРНИК ВЫШЕЛ", "ОЖИДАНИЕ ОТВЕТА", "ПРИГЛАШЕНИЕ ОТПРАВЛЕНО", "Отправка…",
    ),
)
