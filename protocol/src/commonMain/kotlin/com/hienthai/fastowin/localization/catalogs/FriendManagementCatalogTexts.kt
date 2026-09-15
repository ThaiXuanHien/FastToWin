package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val friendManagementKeys = listOf(
    TextKey.RemoveFriendDescription, TextKey.RemoveFriendAction,
    TextKey.GoBack, TextKey.BlockFriendDescription,
    TextKey.NewCount, TextKey.PlayerCodeLabel, TextKey.PlayerCodeShort,
    TextKey.BlockPlayerNamed, TextKey.ActiveFriends,
    TextKey.PendingReplies, TextKey.SentFriendInvitation,
    TextKey.BlockedPlayers, TextKey.Unblock, TextKey.PlayerActions,
    TextKey.InviteSent, TextKey.SendingInvitation,
    TextKey.InviteToRoom, TextKey.SocialHub,
)

private fun friendManagementCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == friendManagementKeys.size) {
        "Expected ${friendManagementKeys.size} friend-management translations, received ${values.size}."
    }
    return friendManagementKeys.zip(values).toMap()
}

internal val friendManagementTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to friendManagementCopy(
        "你和 {player} 将不再是好友。", "删除好友", "返回",
        "{player} 将无法向你发送好友或房间邀请。", "{count} 条新消息", "玩家代码", "代码：{code}",
        "屏蔽 {player}", "{count} 人在线", "等待回复", "邀请已发送 · {code}", "已屏蔽", "取消屏蔽",
        "对 {player} 的操作", "已邀请", "正在发送…", "邀请加入房间", "社交中心",
    ),
    AppLanguage.JAPANESE to friendManagementCopy(
        "あなたと {player} はフレンドではなくなります。", "フレンド解除", "戻る",
        "{player} はフレンド申請やルーム招待を送れなくなります。", "新着 {count} 件", "プレイヤーコード", "コード：{code}",
        "{player} をブロック", "オンライン {count} 人", "返信待ち", "招待送信済み · {code}", "ブロック中", "ブロック解除",
        "{player} の操作", "招待済み", "送信中…", "ルームに招待", "ソーシャルハブ",
    ),
    AppLanguage.KOREAN to friendManagementCopy(
        "{player}님과 친구 관계가 해제됩니다.", "친구 삭제", "돌아가기",
        "{player}님은 친구 신청이나 방 초대를 보낼 수 없습니다.", "새 항목 {count}개", "플레이어 코드", "코드: {code}",
        "{player} 차단", "온라인 {count}명", "응답 대기 중", "초대 보냄 · {code}", "차단됨", "차단 해제",
        "{player} 작업", "초대됨", "보내는 중…", "방에 초대", "소셜 허브",
    ),
    AppLanguage.SPANISH to friendManagementCopy(
        "Tú y {player} dejaréis de ser amigos.", "ELIMINAR AMIGO", "VOLVER",
        "{player} no podrá enviarte solicitudes de amistad ni invitaciones a salas.", "Nuevos: {count}", "Código de jugador", "Código: {code}",
        "Bloquear a {player}", "{count} en línea", "Esperando respuestas", "Invitación enviada · {code}", "Bloqueados", "DESBLOQUEAR",
        "Acciones para {player}", "Invitado", "Enviando…", "Invitar a la sala", "CENTRO SOCIAL",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to friendManagementCopy(
        "Você e {player} deixarão de ser amigos.", "REMOVER AMIGO", "VOLTAR",
        "{player} não poderá enviar pedidos de amizade nem convites para salas.", "Novos: {count}", "Código do jogador", "Código: {code}",
        "Bloquear {player}", "{count} conectados", "Aguardando respostas", "Convite enviado · {code}", "Bloqueados", "DESBLOQUEAR",
        "Ações para {player}", "Convidado", "Enviando…", "Convidar para a sala", "CENTRAL SOCIAL",
    ),
    AppLanguage.FRENCH to friendManagementCopy(
        "Vous et {player} ne serez plus amis.", "RETIRER L’AMI", "RETOUR",
        "{player} ne pourra plus vous envoyer de demandes d’ami ni d’invitations de salon.", "Nouveaux : {count}", "Code joueur", "Code : {code}",
        "Bloquer {player}", "{count} en ligne", "En attente de réponses", "Invitation envoyée · {code}", "Bloqués", "DÉBLOQUER",
        "Actions pour {player}", "Invité", "Envoi…", "Inviter dans le salon", "ESPACE SOCIAL",
    ),
    AppLanguage.GERMAN to friendManagementCopy(
        "Du und {player} seid danach nicht mehr befreundet.", "FREUND ENTFERNEN", "ZURÜCK",
        "{player} kann dir keine Freundschafts- oder Raumeinladungen mehr senden.", "{count} neu", "Spielercode", "Spielercode: {code}",
        "{player} blockieren", "{count} sind online", "Antworten ausstehend", "Einladung gesendet · {code}", "Blockiert", "ENTSPERREN",
        "Aktionen für {player}", "Eingeladen", "Wird gesendet…", "In Raum einladen", "SOZIALE ZENTRALE",
    ),
    AppLanguage.INDONESIAN to friendManagementCopy(
        "Kamu dan {player} tidak akan berteman lagi.", "HAPUS TEMAN", "KEMBALI",
        "{player} tidak dapat mengirim permintaan pertemanan atau undangan kamar kepadamu.", "{count} baru", "Kode pemain", "Kode: {code}",
        "Blokir {player}", "{count} sedang online", "Menunggu balasan", "Undangan terkirim · {code}", "Diblokir", "BUKA BLOKIR",
        "Tindakan untuk {player}", "Diundang", "Mengirim…", "Undang ke kamar", "PUSAT SOSIAL",
    ),
    AppLanguage.THAI to friendManagementCopy(
        "คุณกับ {player} จะไม่ได้เป็นเพื่อนกันอีก", "ลบเพื่อน", "ย้อนกลับ",
        "{player} จะไม่สามารถส่งคำขอเป็นเพื่อนหรือคำเชิญเข้าห้องให้คุณได้", "ใหม่ {count} รายการ", "รหัสผู้เล่น", "รหัส: {code}",
        "บล็อก {player}", "ออนไลน์ {count} คน", "รอการตอบกลับ", "ส่งคำเชิญแล้ว · {code}", "บล็อกแล้ว", "เลิกบล็อก",
        "การดำเนินการสำหรับ {player}", "เชิญแล้ว", "กำลังส่ง…", "เชิญเข้าห้อง", "ศูนย์รวมเพื่อน",
    ),
    AppLanguage.RUSSIAN to friendManagementCopy(
        "Вы с {player} больше не будете друзьями.", "УДАЛИТЬ ИЗ ДРУЗЕЙ", "НАЗАД",
        "{player} не сможет отправлять вам заявки в друзья или приглашения в комнаты.", "Новых: {count}", "Код игрока", "Код: {code}",
        "Заблокировать {player}", "В сети: {count}", "Ожидают ответа", "Приглашение отправлено · {code}", "Заблокированные", "РАЗБЛОКИРОВАТЬ",
        "Действия с {player}", "Приглашён", "Отправка…", "Пригласить в комнату", "ЦЕНТР ОБЩЕНИЯ",
    ),
)
