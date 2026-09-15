package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val clanNotificationKeys = listOf(
    TextKey.ClearAllNotificationsDescription, TextKey.ClearAllNotificationsA11y,
    TextKey.EmptyInboxDescription, TextKey.NotificationsHeroDescription,
    TextKey.DeleteNotificationDescription, TextKey.DeleteNotification,
    TextKey.ClanTogetherDescription, TextKey.ClanName, TextKey.ClanDescription,
    TextKey.DefaultClanDescription, TextKey.SelectClanLogo,
    TextKey.JoinRequestsCount, TextKey.LeaveClanDescription,
    TextKey.RemoveClanMemberDescription, TextKey.ClanTrophies,
    TextKey.MemberTrophies, TextKey.TotalTrophies,
    TextKey.WeeklyClanQuest, TextKey.WinClanMatches,
    TextKey.ChooseClanLogoDescription,
)

private fun clanNotificationCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == clanNotificationKeys.size) {
        "Expected ${clanNotificationKeys.size} social translations, received ${values.size}."
    }
    return clanNotificationKeys.zip(values).toMap()
}

internal val clanNotificationTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to clanNotificationCopy(
        "可见通知将被删除，并在你的设备间同步。", "清除所有通知", "邀请、奖励和消息将在这里显示。", "关注邀请、奖励和重要动态。",
        "“{title}”将从列表中删除。", "删除通知", "加入公会、完成任务，与伙伴一起提升排名。", "公会名称", "公会简介",
        "与队友一起征服排行榜。", "选择公会徽标", "加入申请（{count}）", "你将离开 {clan}。",
        "{player} 将被移出 {clan}。", "公会奖杯", "成员奖杯", "奖杯总数", "每周任务", "与公会一起赢得 {count} 场比赛",
        "点击图标即可立即使用。",
    ),
    AppLanguage.JAPANESE to clanNotificationCopy(
        "表示中の通知が削除され、端末間で同期されます。", "すべての通知を削除", "招待、報酬、お知らせはここに表示されます。", "招待、報酬、重要な活動を確認しましょう。",
        "「{title}」を一覧から削除します。", "通知を削除", "クランに参加し、クエストを達成して仲間とランクを上げましょう。", "クラン名", "クランの説明",
        "仲間とランキングの頂点を目指しましょう。", "クランのロゴを選択", "参加申請（{count}）", "{clan} を脱退します。",
        "{player} を {clan} から除外します。", "クランのトロフィー", "メンバーのトロフィー", "トロフィー合計", "週間クエスト", "クランとともに {count} 試合勝利",
        "アイコンをタップするとすぐに反映されます。",
    ),
    AppLanguage.KOREAN to clanNotificationCopy(
        "표시된 알림이 삭제되고 다른 기기에도 동기화됩니다.", "모든 알림 삭제", "초대, 보상, 소식이 여기에 표시됩니다.", "초대, 보상, 중요한 활동을 확인하세요.",
        "‘{title}’ 알림이 목록에서 삭제됩니다.", "알림 삭제", "길드에 가입해 임무를 완료하고 함께 순위를 올리세요.", "길드 이름", "길드 설명",
        "동료와 함께 순위표를 정복하세요.", "길드 로고 선택", "가입 요청({count})", "{clan}에서 탈퇴합니다.",
        "{player}님이 {clan}에서 추방됩니다.", "길드 트로피", "멤버 트로피", "총 트로피", "주간 임무", "길드와 함께 {count}경기 승리",
        "아이콘을 누르면 즉시 적용됩니다.",
    ),
    AppLanguage.SPANISH to clanNotificationCopy(
        "Las notificaciones visibles se eliminarán y el cambio se sincronizará entre tus dispositivos.", "Borrar todas las notificaciones", "Aquí aparecerán invitaciones, recompensas y noticias.", "Sigue tus invitaciones, recompensas y actividad importante.",
        "«{title}» se quitará de la lista.", "Eliminar notificación", "Únete a un clan, completa misiones y sube en la clasificación con tus compañeros.", "Nombre del clan", "Descripción del clan",
        "Conquista la clasificación con tus compañeros.", "Elegir logo del clan", "Solicitudes de ingreso ({count})", "Saldrás de {clan}.",
        "{player} será expulsado de {clan}.", "Trofeos del clan", "Trofeos del miembro", "Trofeos totales", "MISIÓN SEMANAL", "Gana {count} partidas con tu clan",
        "Toca un icono para aplicarlo al instante.",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to clanNotificationCopy(
        "As notificações visíveis serão removidas e a mudança será sincronizada entre dispositivos.", "Limpar todas as notificações", "Convites, recompensas e novidades aparecerão aqui.", "Acompanhe convites, recompensas e atividades importantes.",
        "“{title}” será removida da lista.", "Excluir notificação", "Entre em um clã, conclua missões e suba no ranking com seus colegas.", "Nome do clã", "Descrição do clã",
        "Conquiste o ranking com seus colegas.", "Escolher logo do clã", "Pedidos para entrar ({count})", "Você sairá de {clan}.",
        "{player} será removido de {clan}.", "Troféus do clã", "Troféus do membro", "Total de troféus", "MISSÃO SEMANAL", "Vença {count} partidas com seu clã",
        "Toque em um ícone para aplicá-lo imediatamente.",
    ),
    AppLanguage.FRENCH to clanNotificationCopy(
        "Les notifications affichées seront supprimées et le changement sera synchronisé sur vos appareils.", "Supprimer toutes les notifications", "Invitations, récompenses et nouvelles apparaîtront ici.", "Suivez vos invitations, récompenses et activités importantes.",
        "« {title} » sera retiré de la liste.", "Supprimer la notification", "Rejoignez un clan, terminez des quêtes et grimpez au classement ensemble.", "Nom du clan", "Description du clan",
        "Conquérez le classement avec vos coéquipiers.", "Choisir le logo du clan", "Demandes d’adhésion ({count})", "Vous quitterez {clan}.",
        "{player} sera retiré de {clan}.", "Trophées du clan", "Trophées du membre", "Total des trophées", "QUÊTE HEBDOMADAIRE", "Gagnez {count} matchs avec votre clan",
        "Touchez une icône pour l’appliquer immédiatement.",
    ),
    AppLanguage.GERMAN to clanNotificationCopy(
        "Sichtbare Benachrichtigungen werden entfernt und geräteübergreifend synchronisiert.", "Alle Benachrichtigungen löschen", "Einladungen, Belohnungen und Neuigkeiten erscheinen hier.", "Verfolge Einladungen, Belohnungen und wichtige Aktivitäten.",
        "„{title}“ wird aus der Liste entfernt.", "Benachrichtigung löschen", "Tritt einem Clan bei, erfülle Aufgaben und steige gemeinsam auf.", "Clanname", "Clanbeschreibung",
        "Erobere mit deinen Mitspielern die Rangliste.", "Clanlogo wählen", "Beitrittsanfragen ({count})", "Du verlässt {clan}.",
        "{player} wird aus {clan} entfernt.", "Clan-Trophäen", "Mitglieder-Trophäen", "Trophäen insgesamt", "WÖCHENTLICHE AUFGABE", "Gewinne {count} Spiele mit deinem Clan",
        "Tippe auf ein Symbol, um es sofort zu verwenden.",
    ),
    AppLanguage.INDONESIAN to clanNotificationCopy(
        "Notifikasi yang terlihat akan dihapus dan disinkronkan antarperangkat.", "Hapus semua notifikasi", "Undangan, hadiah, dan kabar akan muncul di sini.", "Pantau undangan, hadiah, dan aktivitas penting.",
        "“{title}” akan dihapus dari daftar.", "Hapus notifikasi", "Gabung klan, selesaikan misi, dan naik peringkat bersama.", "Nama klan", "Deskripsi klan",
        "Taklukkan papan peringkat bersama rekanmu.", "Pilih logo klan", "Permintaan bergabung ({count})", "Kamu akan keluar dari {clan}.",
        "{player} akan dikeluarkan dari {clan}.", "Trofi klan", "Trofi anggota", "Total trofi", "MISI MINGGUAN", "Menangkan {count} pertandingan bersama klanmu",
        "Ketuk ikon untuk langsung menerapkannya.",
    ),
    AppLanguage.THAI to clanNotificationCopy(
        "การแจ้งเตือนที่มองเห็นจะถูกลบและซิงก์ข้ามอุปกรณ์", "ลบการแจ้งเตือนทั้งหมด", "คำเชิญ รางวัล และข่าวสารจะแสดงที่นี่", "ติดตามคำเชิญ รางวัล และกิจกรรมสำคัญ",
        "“{title}” จะถูกนำออกจากรายการ", "ลบการแจ้งเตือน", "เข้ากิลด์ ทำภารกิจ และไต่อันดับไปด้วยกัน", "ชื่อกิลด์", "คำอธิบายกิลด์",
        "พิชิตอันดับกับเพื่อนร่วมทีม", "เลือกโลโก้กิลด์", "คำขอเข้าร่วม ({count})", "คุณจะออกจาก {clan}",
        "{player} จะถูกนำออกจาก {clan}", "ถ้วยรางวัลกิลด์", "ถ้วยรางวัลสมาชิก", "ถ้วยรางวัลรวม", "ภารกิจรายสัปดาห์", "ชนะ {count} แมตช์กับกิลด์",
        "แตะไอคอนเพื่อใช้งานทันที",
    ),
    AppLanguage.RUSSIAN to clanNotificationCopy(
        "Показанные уведомления будут удалены, а изменения синхронизируются на ваших устройствах.", "Удалить все уведомления", "Здесь появятся приглашения, награды и новости.", "Следите за приглашениями, наградами и важными событиями.",
        "«{title}» будет удалено из списка.", "Удалить уведомление", "Вступайте в клан, выполняйте задания и поднимайтесь в рейтинге вместе.", "Название клана", "Описание клана",
        "Покоряйте рейтинг вместе с товарищами.", "Выбрать эмблему клана", "Заявки на вступление ({count})", "Вы покинете {clan}.",
        "{player} будет исключён из {clan}.", "Трофеи клана", "Трофеи участника", "Всего трофеев", "ЕЖЕНЕДЕЛЬНОЕ ЗАДАНИЕ", "Выиграйте {count} матчей вместе с кланом",
        "Нажмите на значок, чтобы сразу применить его.",
    ),
)
