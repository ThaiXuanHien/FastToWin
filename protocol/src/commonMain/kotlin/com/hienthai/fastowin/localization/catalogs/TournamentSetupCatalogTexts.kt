package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val tournamentSetupKeys = listOf(
    TextKey.TournamentLobby, TextKey.ChampionName,
    TextKey.TournamentLobbySummary, TextKey.BracketInProgress,
    TextKey.TournamentPrizeAwarded, TextKey.CreateAnotherTournament,
    TextKey.DecideLater, TextKey.ChooseTournamentMode,
    TextKey.CreatePrivateTournament, TextKey.TournamentSize,
    TextKey.TournamentFourFormat, TextKey.TournamentEightFormat,
    TextKey.TournamentSixteenFormat, TextKey.TournamentName,
    TextKey.TournamentNameExample, TextKey.GameModeLabel,
    TextKey.EntryFeeGold, TextKey.Free, TextKey.Custom,
    TextKey.EnterEntryFee, TextKey.EntryFeeExample,
    TextKey.TournamentNoElo, TextKey.PlayerCountUpper,
    TextKey.EntryFee, TextKey.Prize, TextKey.GoldAmount,
    TextKey.Participants, TextKey.WaitingForPlayerEllipsis,
    TextKey.TournamentHost,
)

private fun tournamentSetupCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == tournamentSetupKeys.size) {
        "Expected ${tournamentSetupKeys.size} tournament setup translations, received ${values.size}."
    }
    return tournamentSetupKeys.zip(values).toMap()
}

internal val tournamentSetupTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to tournamentSetupCopy(
        "赛事大厅", "冠军：{player}", "{mode} · {current}/{max} 名玩家 · {prize} 金币",
        "对阵表正在进行中。获胜者晋级下一轮。", "{prize} 金币奖励已发放。", "你现在可以创建另一个赛事。",
        "稍后", "选择赛事模式", "创建私人赛事", "赛事规模",
        "4 名玩家 • 2 场半决赛 • 1 场决赛", "8 名玩家 • 4 场四分之一决赛 • 2 场半决赛 • 1 场决赛",
        "16 名玩家 • 8 场十六强赛 • 4 场四分之一决赛 • 2 场半决赛 • 1 场决赛",
        "赛事名称", "例如：极速冠军杯", "游戏模式", "报名费（金币）", "免费", "自定义",
        "输入金币报名费", "例如：750", "赛事对局不影响 Elo。", "{count} 名玩家", "报名费", "奖金",
        "{count} 金币", "参赛者", "等待玩家…", "赛事主办者",
    ),
    AppLanguage.JAPANESE to tournamentSetupCopy(
        "トーナメントロビー", "優勝者：{player}", "{mode} · {current}/{max}人 · ゴールド{prize}",
        "対戦表は進行中です。勝者が次のラウンドへ進みます。", "賞金{prize}ゴールドが授与されました。", "新しいトーナメントを作成できます。",
        "あとで", "トーナメントモードを選択", "プライベートトーナメントを作成", "トーナメント規模",
        "4人 • 準決勝2試合 • 決勝1試合", "8人 • 準々決勝4試合 • 準決勝2試合 • 決勝1試合",
        "16人 • 1回戦8試合 • 準々決勝4試合 • 準決勝2試合 • 決勝1試合",
        "トーナメント名", "例：スピードチャンピオンズカップ", "ゲームモード", "参加料（ゴールド）", "無料", "カスタム",
        "ゴールド参加料を入力", "例：750", "トーナメント戦はEloに影響しません。", "{count}人", "参加料", "賞金",
        "{count}ゴールド", "参加者", "プレイヤーを待っています…", "主催者",
    ),
    AppLanguage.KOREAN to tournamentSetupCopy(
        "토너먼트 로비", "우승자: {player}", "{mode} · {current}/{max}명 · 상금 {prize} 골드",
        "대진표가 진행 중입니다. 승자는 다음 라운드로 진출합니다.", "{prize} 골드 상금이 지급되었습니다.", "이제 새 토너먼트를 만들 수 있습니다.",
        "나중에", "토너먼트 모드 선택", "비공개 토너먼트 만들기", "토너먼트 규모",
        "4명 • 준결승 2경기 • 결승 1경기", "8명 • 8강 4경기 • 준결승 2경기 • 결승 1경기",
        "16명 • 16강 8경기 • 8강 4경기 • 준결승 2경기 • 결승 1경기",
        "토너먼트 이름", "예: 스피드 챔피언스 컵", "게임 모드", "참가비(골드)", "무료", "직접 설정",
        "골드 참가비 입력", "예: 750", "토너먼트 경기는 Elo에 영향을 주지 않습니다.", "{count}명", "참가비", "상금",
        "{count} 골드", "참가자", "플레이어 대기 중…", "주최자",
    ),
    AppLanguage.SPANISH to tournamentSetupCopy(
        "Sala del torneo", "Campeón: {player}", "{mode} · {current}/{max} jugadores · {prize} de oro",
        "El cuadro está en curso. Los ganadores avanzan a la siguiente ronda.", "Se ha entregado el premio de {prize} de oro.", "Ya puedes crear otro torneo.",
        "MÁS TARDE", "Elegir modo del torneo", "Crear torneo privado", "Tamaño del torneo",
        "4 jugadores • 2 semifinales • 1 final", "8 jugadores • 4 cuartos • 2 semifinales • 1 final",
        "16 jugadores • 8 octavos • 4 cuartos • 2 semifinales • 1 final",
        "Nombre del torneo", "p. ej., Copa de Campeones de Velocidad", "Modo de juego", "Cuota de entrada (oro)", "Gratis", "Personalizada",
        "Introduce la cuota en oro", "p. ej., 750", "Las partidas del torneo no afectan al Elo.", "{count} JUGADORES", "Cuota de entrada", "Premio",
        "{count} de oro", "Participantes", "Esperando jugador…", "Organizador",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to tournamentSetupCopy(
        "Sala do torneio", "Campeão: {player}", "{mode} · {current}/{max} jogadores · {prize} Ouro",
        "A chave está em andamento. Os vencedores avançam para a próxima rodada.", "O prêmio de {prize} Ouro foi entregue.", "Agora você pode criar outro torneio.",
        "MAIS TARDE", "Escolha o modo do torneio", "Criar torneio privado", "Tamanho do torneio",
        "4 jogadores • 2 semifinais • 1 final", "8 jogadores • 4 quartas de final • 2 semifinais • 1 final",
        "16 jogadores • 8 oitavas de final • 4 quartas de final • 2 semifinais • 1 final",
        "Nome do torneio", "ex.: Copa dos Campeões de Velocidade", "Modo de jogo", "Taxa de entrada (Ouro)", "Grátis", "Personalizado",
        "Digite a taxa de entrada em Ouro", "ex.: 750", "As partidas do torneio não afetam o Elo.", "{count} JOGADORES", "Taxa de entrada", "Prêmio",
        "{count} Ouro", "Participantes", "Aguardando jogador…", "Organizador",
    ),
    AppLanguage.FRENCH to tournamentSetupCopy(
        "Salon du tournoi", "Champion : {player}", "{mode} · {current}/{max} joueurs · {prize} Or",
        "Le tableau est en cours. Les vainqueurs passent au tour suivant.", "La récompense de {prize} Or a été attribuée.", "Vous pouvez maintenant créer un autre tournoi.",
        "PLUS TARD", "Choisir le mode du tournoi", "Créer un tournoi privé", "Taille du tournoi",
        "4 joueurs • 2 demi-finales • 1 finale", "8 joueurs • 4 quarts • 2 demi-finales • 1 finale",
        "16 joueurs • 8 huitièmes • 4 quarts • 2 demi-finales • 1 finale",
        "Nom du tournoi", "ex. Coupe des champions de vitesse", "Mode de jeu", "Droit d’entrée (Or)", "Gratuit", "Personnalisé",
        "Saisissez le droit d’entrée en Or", "ex. 750", "Les matchs de tournoi n’affectent pas l’Elo.", "{count} JOUEURS", "Droit d’entrée", "Prix",
        "{count} Or", "Joueurs inscrits", "En attente d’un joueur…", "Organisateur",
    ),
    AppLanguage.GERMAN to tournamentSetupCopy(
        "Turnierlobby", "Sieger: {player}", "{mode} · {current}/{max} Spieler · {prize} Gold",
        "Der Turnierbaum läuft. Sieger kommen in die nächste Runde.", "Der Preis von {prize} Gold wurde vergeben.", "Du kannst jetzt ein weiteres Turnier erstellen.",
        "SPÄTER", "Turniermodus wählen", "Privates Turnier erstellen", "Turniergröße",
        "4 Spieler • 2 Halbfinals • 1 Finale", "8 Spieler • 4 Viertelfinals • 2 Halbfinals • 1 Finale",
        "16 Spieler • 8 Achtelfinals • 4 Viertelfinals • 2 Halbfinals • 1 Finale",
        "Turniername", "z. B. Pokal der Tempo-Champions", "Spielmodus", "Startgebühr (Gold)", "Kostenlos", "Benutzerdefiniert",
        "Gold-Startgebühr eingeben", "z. B. 750", "Turnierspiele wirken sich nicht auf Elo aus.", "{count} SPIELER", "Startgebühr", "Preis",
        "Gold: {count}", "Teilnehmer", "Warten auf Spieler…", "Gastgeber",
    ),
    AppLanguage.INDONESIAN to tournamentSetupCopy(
        "Lobi turnamen", "Juara: {player}", "{mode} · {current}/{max} pemain · {prize} Emas",
        "Bagan sedang berlangsung. Pemenang maju ke babak berikutnya.", "Hadiah {prize} Emas telah diberikan.", "Kamu sekarang dapat membuat turnamen lain.",
        "NANTI", "Pilih mode turnamen", "Buat turnamen privat", "Ukuran turnamen",
        "4 pemain • 2 semifinal • 1 final", "8 pemain • 4 perempat final • 2 semifinal • 1 final",
        "16 pemain • 8 babak 16 besar • 4 perempat final • 2 semifinal • 1 final",
        "Nama turnamen", "cth. Piala Juara Kecepatan", "Mode permainan", "Biaya masuk (Emas)", "Gratis", "Kustom",
        "Masukkan biaya masuk dalam Emas", "cth. 750", "Pertandingan turnamen tidak memengaruhi Elo.", "{count} PEMAIN", "Biaya masuk", "Hadiah",
        "{count} Emas", "Peserta", "Menunggu pemain…", "Tuan rumah",
    ),
    AppLanguage.THAI to tournamentSetupCopy(
        "ล็อบบี้ทัวร์นาเมนต์", "แชมป์: {player}", "{mode} · {current}/{max} คน · รางวัล {prize} ทอง",
        "สายการแข่งขันกำลังดำเนินอยู่ ผู้ชนะจะผ่านเข้าสู่รอบถัดไป", "มอบรางวัล {prize} ทองแล้ว", "ตอนนี้คุณสร้างทัวร์นาเมนต์ใหม่ได้แล้ว",
        "ไว้ทีหลัง", "เลือกโหมดทัวร์นาเมนต์", "สร้างทัวร์นาเมนต์ส่วนตัว", "ขนาดทัวร์นาเมนต์",
        "4 คน • รอบรองชนะเลิศ 2 คู่ • รอบชิงชนะเลิศ 1 คู่", "8 คน • รอบก่อนรองชนะเลิศ 4 คู่ • รอบรองชนะเลิศ 2 คู่ • รอบชิงชนะเลิศ 1 คู่",
        "16 คน • รอบ 16 ทีม 8 คู่ • รอบก่อนรองชนะเลิศ 4 คู่ • รอบรองชนะเลิศ 2 คู่ • รอบชิงชนะเลิศ 1 คู่",
        "ชื่อทัวร์นาเมนต์", "เช่น ถ้วยแชมป์ความเร็ว", "โหมดเกม", "ค่าเข้าร่วม (ทอง)", "ฟรี", "กำหนดเอง",
        "กรอกค่าเข้าร่วมเป็นทอง", "เช่น 750", "การแข่งขันทัวร์นาเมนต์ไม่มีผลต่อ Elo", "ผู้เล่น {count} คน", "ค่าเข้าร่วม", "รางวัล",
        "ทอง {count}", "ผู้เข้าร่วม", "กำลังรอผู้เล่น…", "ผู้จัดการแข่งขัน",
    ),
    AppLanguage.RUSSIAN to tournamentSetupCopy(
        "Лобби турнира", "Чемпион: {player}", "{mode} · {current}/{max} игроков · {prize} золота",
        "Турнирная сетка уже разыгрывается. Победители проходят в следующий раунд.", "Награда {prize} золота вручена.", "Теперь вы можете создать ещё один турнир.",
        "ПОЗЖЕ", "Выбрать режим турнира", "Создать закрытый турнир", "Размер турнира",
        "4 игрока • 2 полуфинала • 1 финал", "8 игроков • 4 четвертьфинала • 2 полуфинала • 1 финал",
        "16 игроков • 8 матчей 1/8 • 4 четвертьфинала • 2 полуфинала • 1 финал",
        "Название турнира", "например, Кубок чемпионов скорости", "Режим игры", "Взнос (золото)", "Бесплатно", "Свой",
        "Введите взнос в золоте", "например, 750", "Турнирные матчи не влияют на Elo.", "ИГРОКОВ: {count}", "Взнос", "Приз",
        "{count} золота", "Участники", "Ожидание игрока…", "Организатор",
    ),
)
