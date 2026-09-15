package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val leaderboardKeys = listOf(
    TextKey.FameRaceDescription, TextKey.PreviousSeason, TextKey.AllTime, TextKey.History,
    TextKey.TopPlayers, TextKey.WarriorsCount, TextKey.CurrentSeasonLeaderboardEmpty,
    TextKey.PreviousSeasonLeaderboardEmpty, TextKey.AllTimeLeaderboardEmpty,
    TextKey.YourPosition, TextKey.StrongestClans, TextKey.StrongestClansDescription,
    TextKey.TopClans, TextKey.ClansCount, TextKey.ClanLeaderboardEmpty, TextKey.YourClan,
    TextKey.RankingAwaiting, TextKey.MembersCount, TextKey.TotalElo,
    TextKey.LeaderboardPlayerSummary,
)

private fun leaderboardCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == leaderboardKeys.size) {
        "Expected ${leaderboardKeys.size} leaderboard translations, received ${values.size}."
    }
    return leaderboardKeys.zip(values).toMap()
}

internal val leaderboardTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to leaderboardCopy(
        "提升段位，守住连胜，争夺榜首。", "上赛季", "历史总榜", "历史", "顶尖玩家", "{count} 位玩家",
        "本赛季还没有玩家完成定位赛。", "上赛季没有玩家符合排名条件。", "还没有玩家完成对局。",
        "你的排名", "最强公会", "按所有公会成员的 Elo 总和排名。", "顶尖公会", "{count} 个公会",
        "目前没有上榜的公会。", "你的公会", "排名等你来争", "{count} 名成员", "总 Elo",
        "{tier} · {wins} 胜 · {rate}%",
    ),
    AppLanguage.JAPANESE to leaderboardCopy(
        "ランクを上げ、連勝を守り、頂点を目指しましょう。", "前シーズン", "全期間", "履歴", "上位プレイヤー", "{count}人のプレイヤー",
        "今シーズンはまだ誰も認定戦を終えていません。", "前シーズンのランキング対象者はいません。", "まだ誰も対戦を完了していません。",
        "あなたの順位", "最強クラン", "クラン全メンバーの合計Eloで決まります。", "上位クラン", "{count}クラン",
        "ランク入りしたクランはまだありません。", "あなたのクラン", "ランキングに挑戦", "{count}人のメンバー", "合計Elo",
        "{tier} · {wins}勝 · {rate}%",
    ),
    AppLanguage.KOREAN to leaderboardCopy(
        "등급을 올리고 연승을 지키며 정상을 차지하세요.", "지난 시즌", "전체 기간", "기록", "상위 플레이어", "플레이어 {count}명",
        "이번 시즌에는 배치 경기를 마친 플레이어가 없습니다.", "지난 시즌 순위에 오른 플레이어가 없습니다.", "아직 경기를 마친 플레이어가 없습니다.",
        "내 순위", "최강 길드", "모든 길드원의 Elo 합계로 결정됩니다.", "상위 길드", "길드 {count}개",
        "아직 순위에 오른 길드가 없습니다.", "내 길드", "순위에 도전하세요", "멤버 {count}명", "총 Elo",
        "{tier} · {wins}승 · {rate}%",
    ),
    AppLanguage.SPANISH to leaderboardCopy(
        "Sube de rango, protege tu racha de victorias y alcanza el primer puesto.", "Temporada anterior", "Desde siempre", "Historial", "Mejores jugadores", "{count} jugadores",
        "Ningún jugador ha terminado la clasificación esta temporada.", "Ningún jugador se clasificó para el ranking de la temporada anterior.", "Ningún jugador ha completado una partida todavía.",
        "TU POSICIÓN", "Clanes más fuertes", "Suma de Elo de todos los miembros del clan.", "Mejores clanes", "{count} clanes",
        "Aún no hay clanes clasificados.", "TU CLAN", "El ranking te espera", "{count} miembros", "Elo total",
        "{tier} · {wins} victorias · {rate}%",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to leaderboardCopy(
        "Suba de categoria, mantenha a sequência de vitórias e conquiste o topo.", "Temporada anterior", "Todos os tempos", "Histórico", "Melhores jogadores", "{count} jogadores",
        "Nenhum jogador concluiu as partidas de colocação nesta temporada.", "Nenhum jogador se classificou no ranking da temporada anterior.", "Nenhum jogador concluiu uma partida ainda.",
        "SUA POSIÇÃO", "Clãs mais fortes", "Soma do Elo de todos os membros do clã.", "Melhores clãs", "{count} clãs",
        "Ainda não há clãs no ranking.", "SEU CLÃ", "O ranking espera por você", "{count} membros", "Elo total",
        "{tier} · {wins} vitórias · {rate}%",
    ),
    AppLanguage.FRENCH to leaderboardCopy(
        "Gravissez les paliers, préservez votre série de victoires et prenez la première place.", "Saison précédente", "Depuis toujours", "Historique", "Meilleurs joueurs", "{count} joueurs",
        "Aucun joueur n’a terminé son classement cette saison.", "Aucun joueur ne s’est qualifié pour le classement de la saison précédente.", "Aucun joueur n’a encore terminé de partie.",
        "VOTRE POSITION", "Clans les plus forts", "Somme des Elo de tous les membres du clan.", "Meilleurs clans", "{count} clans",
        "Aucun clan n’est encore classé.", "VOTRE CLAN", "Le classement vous attend", "{count} membres", "Elo total",
        "{tier} · {wins} victoires · {rate}%",
    ),
    AppLanguage.GERMAN to leaderboardCopy(
        "Steige auf, verteidige deine Siegesserie und erobere Platz eins.", "Vorherige Saison", "Alle Zeiten", "Verlauf", "Top-Spieler", "{count} Spieler",
        "In dieser Saison hat noch niemand die Platzierung abgeschlossen.", "Für die Rangliste der letzten Saison hat sich niemand qualifiziert.", "Noch niemand hat ein Spiel beendet.",
        "DEIN PLATZ", "Stärkste Clans", "Gesamtes Elo aller Clanmitglieder.", "Top-Clans", "{count} Clans",
        "Noch kein Clan steht in der Rangliste.", "DEIN CLAN", "Die Rangliste wartet", "{count} Mitglieder", "Gesamt-Elo",
        "{tier} · {wins} Siege · {rate}%",
    ),
    AppLanguage.INDONESIAN to leaderboardCopy(
        "Naik peringkat, jaga rentetan kemenangan, dan rebut posisi teratas.", "Musim sebelumnya", "Sepanjang masa", "Riwayat", "Pemain teratas", "{count} pemain",
        "Belum ada pemain yang menyelesaikan penempatan musim ini.", "Belum ada pemain yang masuk peringkat musim sebelumnya.", "Belum ada pemain yang menyelesaikan pertandingan.",
        "POSISIMU", "Klan terkuat", "Jumlah Elo semua anggota klan.", "Klan teratas", "{count} klan",
        "Belum ada klan yang masuk peringkat.", "KLANMU", "Peringkat menantimu", "{count} anggota", "Elo gabungan",
        "{tier} · {wins} menang · {rate}%",
    ),
    AppLanguage.THAI to leaderboardCopy(
        "ไต่อันดับ รักษาสถิติชนะต่อเนื่อง และคว้าอันดับหนึ่ง", "ฤดูกาลก่อน", "ตลอดกาล", "ประวัติ", "ผู้เล่นอันดับต้น", "ผู้เล่น {count} คน",
        "ฤดูกาลนี้ยังไม่มีผู้เล่นผ่านการจัดอันดับ", "ไม่มีผู้เล่นผ่านเกณฑ์อันดับฤดูกาลก่อน", "ยังไม่มีผู้เล่นแข่งจบแมตช์",
        "อันดับของคุณ", "กิลด์แกร่งที่สุด", "Elo รวมของสมาชิกกิลด์ทุกคน", "กิลด์อันดับต้น", "กิลด์ {count} แห่ง",
        "ยังไม่มีกิลด์ติดอันดับ", "กิลด์ของคุณ", "อันดับรอคุณอยู่", "สมาชิก {count} คน", "Elo รวม",
        "{tier} · ชนะ {wins} · {rate}%",
    ),
    AppLanguage.RUSSIAN to leaderboardCopy(
        "Повышайте ранг, сохраняйте серию побед и займите первое место.", "Прошлый сезон", "За всё время", "История", "Лучшие игроки", "Игроков: {count}",
        "В этом сезоне никто ещё не завершил квалификацию.", "Никто не попал в рейтинг прошлого сезона.", "Пока никто не завершил матч.",
        "ВАША ПОЗИЦИЯ", "Сильнейшие кланы", "Суммарный Elo всех членов клана.", "Лучшие кланы", "Кланов: {count}",
        "Пока ни один клан не вошёл в рейтинг.", "ВАШ КЛАН", "Рейтинг ждёт вас", "Участников: {count}", "Общий Elo",
        "{tier} · {wins} побед · {rate}%",
    ),
)
