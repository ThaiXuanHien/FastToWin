package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val tournamentBracketKeys = listOf(
    TextKey.Bracket, TextKey.CancelTournament, TextKey.LeaveTournament,
    TextKey.AutomaticMatchesDescription, TextKey.Champion,
    TextKey.FinalRound, TextKey.SemifinalRound, TextKey.QuarterfinalRound,
    TextKey.RoundOfSixteen, TextKey.RoundNumber, TextKey.WaitingEllipsis,
    TextKey.ChampionCompact, TextKey.HideBracket,
    TextKey.JoinTournament, TextKey.InviteAction,
)

private fun tournamentBracketCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == tournamentBracketKeys.size) {
        "Expected ${tournamentBracketKeys.size} tournament bracket translations, received ${values.size}."
    }
    return tournamentBracketKeys.zip(values).toMap()
}

internal val tournamentBracketTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to tournamentBracketCopy(
        "对阵表", "取消赛事", "退出赛事", "比赛会自动创建。获胜者晋级下一轮。", "冠军",
        "决赛", "半决赛", "四分之一决赛", "十六强", "第 {round} 轮", "等待中…",
        "冠军：{player}", "隐藏对阵表", "加入", "邀请",
    ),
    AppLanguage.JAPANESE to tournamentBracketCopy(
        "対戦表", "トーナメントを中止", "トーナメントを退出", "試合は自動作成され、勝者が次のラウンドへ進みます。", "優勝",
        "決勝", "準決勝", "準々決勝", "ベスト16", "第{round}ラウンド", "待機中…",
        "優勝者：{player}", "対戦表を隠す", "参加", "招待",
    ),
    AppLanguage.KOREAN to tournamentBracketCopy(
        "대진표", "토너먼트 취소", "토너먼트 나가기", "경기는 자동으로 생성되며 승자는 다음 라운드로 진출합니다.", "우승",
        "결승", "준결승", "8강", "16강", "{round}라운드", "대기 중…",
        "우승자: {player}", "대진표 숨기기", "참가", "초대",
    ),
    AppLanguage.SPANISH to tournamentBracketCopy(
        "Cuadro", "CANCELAR TORNEO", "SALIR DEL TORNEO", "Las partidas se crean automáticamente. Los ganadores avanzan a la siguiente ronda.", "CAMPEÓN",
        "GRAN FINAL", "SEMIFINALES", "CUARTOS DE FINAL", "OCTAVOS DE FINAL", "RONDA {round}", "Esperando…",
        "Campeón: {player}", "Ocultar cuadro", "UNIRSE", "Invitar",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to tournamentBracketCopy(
        "Chave", "CANCELAR TORNEIO", "SAIR DO TORNEIO", "As partidas são criadas automaticamente. Os vencedores avançam para a próxima rodada.", "CAMPEÃO",
        "DECISÃO", "SEMIFINAIS", "QUARTAS DE FINAL", "OITAVAS DE FINAL", "RODADA {round}", "Aguardando…",
        "Campeão: {player}", "Ocultar chave", "ENTRAR", "Convidar",
    ),
    AppLanguage.FRENCH to tournamentBracketCopy(
        "Tableau", "ANNULER LE TOURNOI", "QUITTER LE TOURNOI", "Les matchs sont créés automatiquement. Les vainqueurs passent au tour suivant.", "VAINQUEUR",
        "FINALE", "DEMI-FINALES", "QUARTS DE FINALE", "HUITIÈMES DE FINALE", "TOUR {round}", "En attente…",
        "Vainqueur : {player}", "Masquer le tableau", "REJOINDRE", "Inviter",
    ),
    AppLanguage.GERMAN to tournamentBracketCopy(
        "Turnierbaum", "TURNIER ABSAGEN", "TURNIER VERLASSEN", "Spiele werden automatisch erstellt. Sieger kommen in die nächste Runde.", "SIEGER",
        "FINALE", "HALBFINALE", "VIERTELFINALE", "ACHTELFINALE", "RUNDE {round}", "Warten…",
        "Sieger: {player}", "Turnierbaum ausblenden", "BEITRETEN", "Einladen",
    ),
    AppLanguage.INDONESIAN to tournamentBracketCopy(
        "Bagan", "BATALKAN TURNAMEN", "KELUAR DARI TURNAMEN", "Pertandingan dibuat otomatis. Pemenang maju ke babak berikutnya.", "JUARA",
        "BABAK FINAL", "BABAK SEMIFINAL", "PEREMPAT FINAL", "BABAK 16 BESAR", "BABAK {round}", "Menunggu…",
        "Juara: {player}", "Sembunyikan bagan", "GABUNG", "Undang",
    ),
    AppLanguage.THAI to tournamentBracketCopy(
        "สายการแข่งขัน", "ยกเลิกทัวร์นาเมนต์", "ออกจากทัวร์นาเมนต์", "ระบบจะสร้างการแข่งขันอัตโนมัติ ผู้ชนะจะผ่านเข้าสู่รอบถัดไป", "แชมป์",
        "รอบชิงชนะเลิศ", "รอบรองชนะเลิศ", "รอบก่อนรองชนะเลิศ", "รอบ 16 ทีม", "รอบที่ {round}", "กำลังรอ…",
        "แชมป์: {player}", "ซ่อนสายการแข่งขัน", "เข้าร่วม", "เชิญ",
    ),
    AppLanguage.RUSSIAN to tournamentBracketCopy(
        "Сетка", "ОТМЕНИТЬ ТУРНИР", "ПОКИНУТЬ ТУРНИР", "Матчи создаются автоматически. Победители проходят в следующий раунд.", "ЧЕМПИОН",
        "ФИНАЛ", "ПОЛУФИНАЛЫ", "ЧЕТВЕРТЬФИНАЛЫ", "1/8 ФИНАЛА", "РАУНД {round}", "Ожидание…",
        "Чемпион: {player}", "Скрыть сетку", "ВСТУПИТЬ", "Пригласить",
    ),
)
