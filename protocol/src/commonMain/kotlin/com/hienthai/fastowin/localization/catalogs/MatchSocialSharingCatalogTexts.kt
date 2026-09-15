package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val matchSocialSharingKeys = listOf(
    TextKey.BlockPlayerTitle, TextKey.BlockPlayerDescription,
    TextKey.Block, TextKey.Processing,
    TextKey.Accept, TextKey.Decline,
    TextKey.Draw, TextKey.Win, TextKey.Loss,
    TextKey.MatchReward, TextKey.RankedNoRematch,
    TextKey.Rematch, TextKey.ResponseSeconds,
    TextKey.InviteRematch, TextKey.LoginForSocial,
    TextKey.AddFriend, TextKey.AcceptFriend,
    TextKey.FriendRequestSent, TextKey.AlreadyFriends,
    TextKey.Blocked, TextKey.LoadingInfo,
    TextKey.ShareResultCaption, TextKey.ShareTimeLabel,
    TextKey.ShareAccuracyLabel, TextKey.ShareSlogan,
    TextKey.ShareResultSheetTitle,
)

private fun matchSocialSharingCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == matchSocialSharingKeys.size) {
        "Expected ${matchSocialSharingKeys.size} match social translations, received ${values.size}."
    }
    return matchSocialSharingKeys.zip(values).toMap()
}

internal val matchSocialSharingTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to matchSocialSharingCopy(
        "屏蔽 {player}？", "屏蔽后，双方将无法添加好友、发送邀请或再战。", "屏蔽", "处理中…", "接受", "拒绝",
        "平局", "胜利", "失败", "对局奖励", "排位赛不支持再战。请返回大厅匹配新的对手。", "再战",
        "请在 {seconds} 秒内回应", "邀请再战", "登录后即可添加好友或屏蔽玩家。", "添加好友", "接受好友申请",
        "好友申请已发送", "已是好友", "已屏蔽", "正在加载信息",
        "Fast To Win 对局结果：{player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "时间", "准确率", "眼疾 • 手快 • 获胜", "分享对局结果",
    ),
    AppLanguage.JAPANESE to matchSocialSharingCopy(
        "{player}をブロックしますか？", "ブロックすると、フレンド追加、招待、再戦が互いにできなくなります。", "ブロック", "処理中…", "承認", "拒否",
        "引き分け", "勝ち", "負け", "対戦報酬", "ランク戦では再戦できません。ロビーに戻って新しい対戦相手を探してください。", "再戦",
        "回答まであと{seconds}秒", "再戦に招待", "フレンド追加やブロックをするにはログインしてください。", "フレンド追加", "フレンド申請を承認",
        "フレンド申請を送信しました", "フレンドです", "ブロック済み", "情報を読み込み中",
        "Fast To Win 対戦結果：{player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "時間", "正確率", "素早く見つけて • 素早くタップ • 勝利", "対戦結果を共有",
    ),
    AppLanguage.KOREAN to matchSocialSharingCopy(
        "{player}님을 차단할까요?", "차단하면 서로 친구 추가, 초대 또는 재대결을 할 수 없습니다.", "차단", "처리 중…", "수락", "거절",
        "무승부", "승리", "패배", "경기 보상", "랭크 경기에서는 재대결할 수 없습니다. 로비로 돌아가 새로운 상대를 찾으세요.", "재대결",
        "응답까지 {seconds}초", "재대결 초대", "친구 추가 또는 차단하려면 로그인하세요.", "친구 추가", "친구 요청 수락",
        "친구 요청을 보냈습니다", "이미 친구입니다", "차단됨", "정보 불러오는 중",
        "Fast To Win 경기 결과: {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "시간", "정확도", "빠르게 찾고 • 빠르게 누르고 • 승리", "경기 결과 공유",
    ),
    AppLanguage.SPANISH to matchSocialSharingCopy(
        "¿Bloquear a {player}?", "No podréis añadiros como amigos, enviar invitaciones ni solicitar una revancha.", "BLOQUEAR", "PROCESANDO…", "ACEPTAR", "RECHAZAR",
        "Empate", "Victoria", "Derrota", "Recompensa de partida", "Las partidas clasificatorias no admiten revancha. Vuelve a la sala para encontrar un nuevo rival.", "Revancha",
        "Quedan {seconds} segundos para responder", "Invitar a revancha", "Inicia sesión para añadir amigos o bloquear jugadores.", "Añadir amigo", "Aceptar solicitud",
        "Solicitud enviada", "Ya sois amigos", "Bloqueado", "Cargando información",
        "Resultado de Fast To Win: {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "TIEMPO", "PRECISIÓN", "Mira rápido • Toca rápido • Gana", "Compartir resultado",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to matchSocialSharingCopy(
        "Bloquear {player}?", "Vocês não poderão se adicionar como amigos, enviar convites ou jogar uma revanche.", "BLOQUEAR", "PROCESSANDO…", "ACEITAR", "RECUSAR",
        "Empate", "Vitória", "Derrota", "Recompensa da partida", "Partidas ranqueadas não permitem revanche. Volte ao lobby para encontrar um novo adversário.", "Revanche",
        "Restam {seconds} segundos para responder", "Convidar para revanche", "Entre para adicionar amigos ou bloquear jogadores.", "Adicionar amigo", "Aceitar amizade",
        "Solicitação enviada", "Já são amigos", "Bloqueado", "Carregando informações",
        "Resultado do Fast To Win: {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "TEMPO", "PRECISÃO", "Veja rápido • Toque rápido • Vença", "Compartilhar resultado",
    ),
    AppLanguage.FRENCH to matchSocialSharingCopy(
        "Bloquer {player} ?", "Vous ne pourrez plus vous ajouter en ami, vous inviter ou demander une revanche.", "BLOQUER", "TRAITEMENT…", "ACCEPTER", "REFUSER",
        "Match nul", "Victoire", "Défaite", "Récompense de partie", "Les parties classées ne permettent pas de revanche. Retournez au salon pour trouver un nouvel adversaire.", "Revanche",
        "Plus que {seconds} secondes pour répondre", "Inviter à une revanche", "Connectez-vous pour ajouter des amis ou bloquer des joueurs.", "Ajouter en ami", "Accepter la demande",
        "Demande envoyée", "Déjà amis", "Bloqué", "Chargement des informations",
        "Résultat Fast To Win : {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "TEMPS", "PRÉCISION", "Voyez vite • Touchez vite • Gagnez", "Partager le résultat",
    ),
    AppLanguage.GERMAN to matchSocialSharingCopy(
        "{player} blockieren?", "Ihr könnt euch danach nicht mehr als Freunde hinzufügen, Einladungen senden oder eine Revanche spielen.", "BLOCKIEREN", "WIRD VERARBEITET…", "ANNEHMEN", "ABLEHNEN",
        "Unentschieden", "Sieg", "Niederlage", "Matchbelohnung", "Ranglistenspiele unterstützen keine Revanche. Kehre zur Lobby zurück, um einen neuen Gegner zu finden.", "Revanche",
        "Noch {seconds} Sekunden zum Antworten", "Zur Revanche einladen", "Melde dich an, um Freunde hinzuzufügen oder Spieler zu blockieren.", "Freund hinzufügen", "Freundschaftsanfrage annehmen",
        "Freundschaftsanfrage gesendet", "Bereits befreundet", "Blockiert", "Informationen werden geladen",
        "Fast To Win-Ergebnis: {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "ZEIT", "GENAUIGKEIT", "Schnell sehen • Schnell tippen • Gewinnen", "Ergebnis teilen",
    ),
    AppLanguage.INDONESIAN to matchSocialSharingCopy(
        "Blokir {player}?", "Kalian tidak dapat saling menambahkan sebagai teman, mengirim undangan, atau bertanding ulang.", "BLOKIR", "MEMPROSES…", "TERIMA", "TOLAK",
        "Seri", "Menang", "Kalah", "Hadiah pertandingan", "Pertandingan peringkat tidak mendukung tanding ulang. Kembali ke lobi untuk mencari lawan baru.", "Tanding ulang",
        "Tersisa {seconds} detik untuk merespons", "Undang tanding ulang", "Masuk untuk menambah teman atau memblokir pemain.", "Tambah teman", "Terima permintaan pertemanan",
        "Permintaan pertemanan terkirim", "Sudah berteman", "Diblokir", "Memuat informasi",
        "Hasil Fast To Win: {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "WAKTU", "AKURASI", "Lihat cepat • Ketuk cepat • Menang", "Bagikan hasil",
    ),
    AppLanguage.THAI to matchSocialSharingCopy(
        "บล็อก {player} หรือไม่?", "ทั้งสองฝ่ายจะไม่สามารถเพิ่มเป็นเพื่อน ส่งคำเชิญ หรือแข่งใหม่ได้", "บล็อก", "กำลังดำเนินการ…", "ยอมรับ", "ปฏิเสธ",
        "เสมอ", "ชนะ", "แพ้", "รางวัลการแข่งขัน", "การแข่งขันจัดอันดับไม่รองรับการแข่งใหม่ กลับไปที่ล็อบบี้เพื่อจับคู่กับคู่แข่งคนใหม่", "แข่งใหม่",
        "เหลือ {seconds} วินาทีในการตอบกลับ", "เชิญแข่งใหม่", "เข้าสู่ระบบเพื่อเพิ่มเพื่อนหรือบล็อกผู้เล่น", "เพิ่มเพื่อน", "ยอมรับคำขอเป็นเพื่อน",
        "ส่งคำขอเป็นเพื่อนแล้ว", "เป็นเพื่อนกันแล้ว", "บล็อกแล้ว", "กำลังโหลดข้อมูล",
        "ผลการแข่งขัน Fast To Win: {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "เวลา", "ความแม่นยำ", "มองไว • แตะไว • ชนะ", "แชร์ผลการแข่งขัน",
    ),
    AppLanguage.RUSSIAN to matchSocialSharingCopy(
        "Заблокировать игрока {player}?", "Вы больше не сможете добавлять друг друга в друзья, отправлять приглашения или предлагать реванш.", "ЗАБЛОКИРОВАТЬ", "ОБРАБОТКА…", "ПРИНЯТЬ", "ОТКЛОНИТЬ",
        "Ничья", "Победа", "Поражение", "Награда за матч", "В рейтинговых матчах реванш недоступен. Вернитесь в лобби, чтобы найти нового соперника.", "Реванш",
        "До ответа: {seconds} сек.", "Предложить реванш", "Войдите, чтобы добавлять друзей или блокировать игроков.", "Добавить в друзья", "Принять заявку",
        "Заявка отправлена", "Уже друзья", "Заблокирован", "Загрузка информации",
        "Результат Fast To Win: {player} {playerScore} – {opponentScore} {opponent} • {mode}",
        "ВРЕМЯ", "ТОЧНОСТЬ", "Быстро ищи • Быстро нажимай • Побеждай", "Поделиться результатом",
    ),
)
