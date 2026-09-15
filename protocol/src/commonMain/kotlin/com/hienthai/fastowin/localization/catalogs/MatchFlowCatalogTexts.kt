package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val matchFlowKeys = listOf(
    TextKey.ReactionHappy, TextKey.ReactionLaugh, TextKey.ReactionFire,
    TextKey.ReactionVictory, TextKey.ReactionLove, TextKey.ReactionSpeed,
    TextKey.ExitMatchTitle, TextKey.ForfeitCasualDescription,
    TextKey.ForfeitRankedDescription, TextKey.ExitMatch, TextKey.ContinuePlaying,
    TextKey.GameDisconnected, TextKey.GameReconnecting, TextKey.TieScoreWarning,
    TextKey.CloseScoreWarning, TextKey.LivesCount, TextKey.SendReaction,
    TextKey.NextNumber, TextKey.RematchInviteTitle, TextKey.RematchInviteDescription,
    TextKey.VictoryResult, TextKey.DefeatResult, TextKey.ForfeitResultDescription,
    TextKey.ReturnToLobby,
)

private fun matchFlowCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == matchFlowKeys.size) {
        "Expected ${matchFlowKeys.size} match translations, received ${values.size}."
    }
    return matchFlowKeys.zip(values).toMap()
}

internal val matchFlowTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to matchFlowCopy(
        "开心", "大笑", "火力全开", "胜利", "喜爱", "加速", "退出对局？", "现在退出将判负。",
        "现在退出将判负并扣除 Elo。", "退出对局", "继续游戏", "连接中断，正在重连…", "正在重新连接对局…",
        "比分持平——下一回合很关键！", "比分接近——只差 {difference} 分", "{count} 次机会", "发送表情",
        "下一个数字", "再战邀请", "{player} 想与你再战。", "胜利！", "失败", "你退出了对局，已判负。", "返回大厅",
    ),
    AppLanguage.JAPANESE to matchFlowCopy(
        "うれしい", "大笑い", "燃える", "勝利", "大好き", "加速", "対戦を離れますか？", "今離れると敗北になります。",
        "今離れると敗北となり、Eloも失います。", "対戦を離れる", "プレイを続ける", "接続が切れました。再接続中…", "対戦に再接続中…",
        "同点です。次のターンが重要！", "接戦です。差はわずか {difference} 点", "残り {count} 回", "リアクションを送る",
        "次の数字", "再戦への招待", "{player} が再戦を希望しています。", "勝利！", "敗北", "対戦を離れたため、不戦敗になりました。", "ロビーに戻る",
    ),
    AppLanguage.KOREAN to matchFlowCopy(
        "기쁨", "웃음", "불타오름", "승리", "사랑", "속도 올리기", "경기를 나가시겠습니까?", "지금 나가면 패배로 처리됩니다.",
        "지금 나가면 패배로 처리되고 Elo가 감소합니다.", "경기 나가기", "계속 플레이", "연결이 끊겼습니다. 다시 연결 중…", "경기에 다시 연결 중…",
        "동점입니다. 다음 차례가 중요합니다!", "접전입니다. 단 {difference}점 차이", "남은 기회 {count}개", "반응 보내기",
        "다음 숫자", "재대결 초대", "{player}님이 재대결을 원합니다.", "승리!", "패배", "경기를 나가 기권패로 처리되었습니다.", "로비로 돌아가기",
    ),
    AppLanguage.SPANISH to matchFlowCopy(
        "Feliz", "Risa", "En llamas", "Victoria", "Cariño", "Acelerar", "¿Salir de la partida?", "Salir ahora cuenta como derrota.",
        "Salir ahora cuenta como derrota y resta Elo.", "SALIR DE LA PARTIDA", "SEGUIR JUGANDO", "Conexión perdida. Reconectando…", "Reconectando a la partida…",
        "¡Empate! La próxima jugada importa.", "¡Muy igualados! Solo {difference} puntos de diferencia", "{count} vidas", "Enviar reacción",
        "SIGUIENTE NÚMERO", "INVITACIÓN A REVANCHA", "{player} quiere una revancha.", "¡VICTORIA!", "DERROTA", "Saliste de la partida y perdiste por abandono.", "Volver al lobby",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to matchFlowCopy(
        "Feliz", "Risada", "Em chamas", "Vitória", "Amor", "Acelerar", "Sair da partida?", "Sair agora conta como derrota.",
        "Sair agora conta como derrota e reduz seu Elo.", "SAIR DA PARTIDA", "CONTINUAR JOGANDO", "Conexão perdida. Reconectando…", "Reconectando à partida…",
        "Empate! A próxima jogada é decisiva.", "Disputa acirrada! Apenas {difference} pontos de diferença", "{count} vidas", "Enviar reação",
        "PRÓXIMO NÚMERO", "CONVITE PARA REVANCHE", "{player} quer uma revanche.", "VITÓRIA!", "DERROTA", "Você saiu da partida e perdeu por desistência.", "Voltar ao lobby",
    ),
    AppLanguage.FRENCH to matchFlowCopy(
        "Joie", "Rire", "En feu", "Victoire", "Amour", "Accélérer", "Quitter la partie ?", "Partir maintenant compte comme une défaite.",
        "Partir maintenant compte comme une défaite et fait perdre de l’Elo.", "QUITTER LA PARTIE", "CONTINUER À JOUER", "Connexion perdue. Reconnexion…", "Reconnexion à la partie…",
        "Égalité ! Le prochain tour est décisif.", "Coude à coude ! Seulement {difference} points d’écart", "{count} vies", "Envoyer une réaction",
        "PROCHAIN NOMBRE", "INVITATION À REJOUER", "{player} souhaite rejouer.", "VICTOIRE !", "DÉFAITE", "Vous avez quitté la partie et déclaré forfait.", "Retour au salon",
    ),
    AppLanguage.GERMAN to matchFlowCopy(
        "Fröhlich", "Lachen", "Feuer und Flamme", "Sieg", "Liebe", "Schneller", "Spiel verlassen?", "Wenn du jetzt gehst, zählt das als Niederlage.",
        "Wenn du jetzt gehst, verlierst du das Spiel und Elo.", "SPIEL VERLASSEN", "WEITERSPIELEN", "Verbindung verloren. Erneuter Verbindungsaufbau…", "Verbindung zum Spiel wird wiederhergestellt…",
        "Gleichstand! Der nächste Zug zählt.", "Kopf an Kopf – nur {difference} Punkte Abstand", "{count} Leben", "Reaktion senden",
        "NÄCHSTE ZAHL", "EINLADUNG ZUR REVANCHE", "{player} möchte eine Revanche.", "SIEG!", "NIEDERLAGE", "Du hast das Spiel verlassen und aufgegeben.", "Zur Lobby zurück",
    ),
    AppLanguage.INDONESIAN to matchFlowCopy(
        "Senang", "Tertawa", "Membara", "Kemenangan", "Suka", "Percepat", "Keluar dari pertandingan?", "Keluar sekarang dihitung sebagai kekalahan.",
        "Keluar sekarang dihitung sebagai kekalahan dan mengurangi Elo.", "KELUAR DARI PERTANDINGAN", "LANJUT BERMAIN", "Koneksi terputus. Menghubungkan ulang…", "Menghubungkan ulang ke pertandingan…",
        "Skor imbang! Giliran berikutnya menentukan.", "Sangat ketat! Hanya selisih {difference} poin", "{count} nyawa", "Kirim reaksi",
        "ANGKA BERIKUTNYA", "UNDANGAN TANDING ULANG", "{player} ingin tanding ulang.", "MENANG!", "KALAH", "Kamu keluar dari pertandingan dan dinyatakan kalah.", "Kembali ke lobi",
    ),
    AppLanguage.THAI to matchFlowCopy(
        "ดีใจ", "หัวเราะ", "ไฟลุก", "ชัยชนะ", "รัก", "เร่งความเร็ว", "ออกจากแมตช์หรือไม่", "ออกตอนนี้จะนับว่าแพ้",
        "ออกตอนนี้จะนับว่าแพ้และเสีย Elo", "ออกจากแมตช์", "เล่นต่อ", "การเชื่อมต่อหลุด กำลังเชื่อมใหม่…", "กำลังเชื่อมต่อแมตช์ใหม่…",
        "คะแนนเท่ากัน รอบต่อไปสำคัญมาก!", "สูสีมาก ต่างกันเพียง {difference} คะแนน", "ชีวิต {count} ครั้ง", "ส่งอีโมจิ",
        "เลขถัดไป", "คำเชิญแข่งอีกครั้ง", "{player} ต้องการแข่งอีกครั้ง", "ชนะ!", "แพ้", "คุณออกจากแมตช์และถูกปรับแพ้", "กลับไปห้องรอ",
    ),
    AppLanguage.RUSSIAN to matchFlowCopy(
        "Радость", "Смех", "В огне", "Победа", "Любовь", "Ускорение", "Выйти из матча?", "Выход сейчас будет засчитан как поражение.",
        "Выход сейчас будет засчитан как поражение с потерей Elo.", "ВЫЙТИ ИЗ МАТЧА", "ПРОДОЛЖИТЬ ИГРУ", "Связь потеряна. Переподключение…", "Возвращаемся в матч…",
        "Счёт равный — следующий ход решает всё!", "Борьба идёт на равных — разница всего {difference} очков", "Жизни: {count}", "Отправить реакцию",
        "СЛЕДУЮЩЕЕ ЧИСЛО", "ПРИГЛАШЕНИЕ НА РЕВАНШ", "{player} хочет сыграть ещё раз.", "ПОБЕДА!", "ПОРАЖЕНИЕ", "Вы покинули матч и получили техническое поражение.", "Вернуться в лобби",
    ),
)
