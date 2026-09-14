package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class SocialScreenLocalizationTest {
    @Test
    fun `social and shop navigation copy resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Room invitations", "Friend requests", "Find a clan", "Members", "Invitations", "Mark all as read", "Gems", "Gold"),
            AppLanguage.VIETNAMESE to listOf("Lời mời vào phòng", "Lời mời kết bạn", "Tìm bang hội", "Thành viên", "Lời mời tham gia", "Đánh dấu tất cả đã đọc", "Gem", "Vàng"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("房间邀请", "好友请求", "查找战队", "成员", "邀请", "全部标为已读", "宝石", "金币"),
            AppLanguage.JAPANESE to listOf("ルーム招待", "フレンド申請", "クランを探す", "メンバー", "招待", "すべて既読にする", "ジェム", "ゴールド"),
            AppLanguage.KOREAN to listOf("방 초대", "친구 요청", "클랜 찾기", "멤버", "초대", "모두 읽음으로 표시", "젬", "골드"),
            AppLanguage.SPANISH to listOf("Invitaciones a salas", "Solicitudes de amistad", "Buscar un clan", "Miembros", "Invitaciones", "Marcar todo como leído", "Gemas", "Oro"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Convites para salas", "Solicitações de amizade", "Encontrar um clã", "Membros", "Convites", "Marcar tudo como lido", "Gemas", "Ouro"),
            AppLanguage.FRENCH to listOf("Invitations de salon", "Demandes d’amitié", "Trouver un clan", "Membres", "Invitations", "Tout marquer comme lu", "Gemmes", "Or"),
            AppLanguage.GERMAN to listOf("Raumeinladungen", "Freundschaftsanfragen", "Clan finden", "Mitglieder", "Einladungen", "Alle als gelesen markieren", "Juwelen", "Gold"),
            AppLanguage.INDONESIAN to listOf("Undangan ruang", "Permintaan pertemanan", "Cari klan", "Anggota", "Undangan", "Tandai semua sudah dibaca", "Gem", "Emas"),
            AppLanguage.THAI to listOf("คำเชิญเข้าห้อง", "คำขอเป็นเพื่อน", "ค้นหาแคลน", "สมาชิก", "คำเชิญ", "ทำเครื่องหมายว่าอ่านแล้วทั้งหมด", "เจม", "ทอง"),
            AppLanguage.RUSSIAN to listOf("Приглашения в комнаты", "Заявки в друзья", "Найти клан", "Участники", "Приглашения", "Отметить все как прочитанные", "Кристаллы", "Золото"),
        )
        val keys = listOf(
            TextKey.RoomInvitations,
            TextKey.FriendRequests,
            TextKey.SearchClan,
            TextKey.ClanMembers,
            TextKey.JoinInvitations,
            TextKey.MarkAllRead,
            TextKey.GemTab,
            TextKey.GoldTab,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `primary social and shop actions resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Search clans", "CREATE CLAN", "Join", "LEAVE CLAN", "START TOURNAMENT", "CREATE TOURNAMENT", "Clear all"),
            AppLanguage.VIETNAMESE to listOf("Tìm kiếm bang hội", "TẠO BANG HỘI", "Xin vào", "RỜI BANG", "BẮT ĐẦU GIẢI ĐẤU", "BẮT ĐẦU TẠO GIẢI", "Xóa tất cả"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("搜索战队", "创建战队", "加入", "退出战队", "开始锦标赛", "创建锦标赛", "全部清除"),
            AppLanguage.JAPANESE to listOf("クラン検索", "クラン作成", "参加", "クランを脱退", "トーナメント開始", "トーナメント作成", "すべて削除"),
            AppLanguage.KOREAN to listOf("클랜 검색", "클랜 만들기", "가입", "클랜 탈퇴", "토너먼트 시작", "토너먼트 만들기", "모두 삭제"),
            AppLanguage.SPANISH to listOf("Buscar clanes", "CREAR CLAN", "Unirse", "ABANDONAR CLAN", "INICIAR TORNEO", "CREAR TORNEO", "Borrar todo"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Buscar clãs", "CRIAR CLÃ", "Entrar", "SAIR DO CLÃ", "INICIAR TORNEIO", "CRIAR TORNEIO", "Limpar tudo"),
            AppLanguage.FRENCH to listOf("Rechercher des clans", "CRÉER UN CLAN", "Rejoindre", "QUITTER LE CLAN", "LANCER LE TOURNOI", "CRÉER LE TOURNOI", "Tout effacer"),
            AppLanguage.GERMAN to listOf("Clans suchen", "CLAN ERSTELLEN", "Beitreten", "CLAN VERLASSEN", "TURNIER STARTEN", "TURNIER ERSTELLEN", "Alle löschen"),
            AppLanguage.INDONESIAN to listOf("Cari klan", "BUAT KLAN", "Gabung", "KELUAR KLAN", "MULAI TURNAMEN", "BUAT TURNAMEN", "Hapus semua"),
            AppLanguage.THAI to listOf("ค้นหาแคลน", "สร้างแคลน", "เข้าร่วม", "ออกจากแคลน", "เริ่มทัวร์นาเมนต์", "สร้างทัวร์นาเมนต์", "ล้างทั้งหมด"),
            AppLanguage.RUSSIAN to listOf("Искать кланы", "СОЗДАТЬ КЛАН", "Вступить", "ПОКИНУТЬ КЛАН", "НАЧАТЬ ТУРНИР", "СОЗДАТЬ ТУРНИР", "Очистить все"),
        )
        val keys = listOf(
            TextKey.SearchClanAction,
            TextKey.CreateClan,
            TextKey.RequestToJoin,
            TextKey.LeaveClan,
            TextKey.StartTournament,
            TextKey.CreateTournamentAction,
            TextKey.ClearAllNotifications,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `social confirmations and empty states resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Remove friend?", "No clans found", "Leave clan?", "Remove clan member?", "Tournament cancelled", "Clear all notifications?", "Delete notification?"),
            AppLanguage.VIETNAMESE to listOf("Hủy kết bạn?", "Chưa tìm thấy bang hội", "Rời bang?", "Mời thành viên rời bang?", "Giải đấu đã hủy", "Xóa tất cả thông báo?", "Xóa thông báo?"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("删除好友？", "未找到战队", "退出战队？", "移除战队成员？", "锦标赛已取消", "清除所有通知？", "删除通知？"),
            AppLanguage.JAPANESE to listOf("フレンドを削除しますか？", "クランが見つかりません", "クランを脱退しますか？", "クランメンバーを除名しますか？", "トーナメントは中止されました", "すべての通知を削除しますか？", "通知を削除しますか？"),
            AppLanguage.KOREAN to listOf("친구를 삭제할까요?", "클랜을 찾을 수 없습니다", "클랜에서 탈퇴할까요?", "클랜 멤버를 추방할까요?", "토너먼트가 취소되었습니다", "모든 알림을 삭제할까요?", "알림을 삭제할까요?"),
            AppLanguage.SPANISH to listOf("¿Eliminar amigo?", "No se encontraron clanes", "¿Abandonar el clan?", "¿Expulsar miembro del clan?", "Torneo cancelado", "¿Borrar todas las notificaciones?", "¿Eliminar notificación?"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Remover amigo?", "Nenhum clã encontrado", "Sair do clã?", "Remover membro do clã?", "Torneio cancelado", "Limpar todas as notificações?", "Excluir notificação?"),
            AppLanguage.FRENCH to listOf("Supprimer cet ami ?", "Aucun clan trouvé", "Quitter le clan ?", "Exclure ce membre du clan ?", "Tournoi annulé", "Effacer toutes les notifications ?", "Supprimer la notification ?"),
            AppLanguage.GERMAN to listOf("Freund entfernen?", "Keine Clans gefunden", "Clan verlassen?", "Clanmitglied entfernen?", "Turnier abgesagt", "Alle Benachrichtigungen löschen?", "Benachrichtigung löschen?"),
            AppLanguage.INDONESIAN to listOf("Hapus teman?", "Klan tidak ditemukan", "Keluar dari klan?", "Keluarkan anggota klan?", "Turnamen dibatalkan", "Hapus semua notifikasi?", "Hapus notifikasi?"),
            AppLanguage.THAI to listOf("ลบเพื่อนหรือไม่?", "ไม่พบแคลน", "ออกจากแคลนหรือไม่?", "นำสมาชิกออกจากแคลนหรือไม่?", "ยกเลิกทัวร์นาเมนต์แล้ว", "ลบการแจ้งเตือนทั้งหมดหรือไม่?", "ลบการแจ้งเตือนหรือไม่?"),
            AppLanguage.RUSSIAN to listOf("Удалить друга?", "Кланы не найдены", "Покинуть клан?", "Исключить участника клана?", "Турнир отменён", "Удалить все уведомления?", "Удалить уведомление?"),
        )
        val keys = listOf(
            TextKey.RemoveFriendTitle,
            TextKey.NoClansFound,
            TextKey.LeaveClanTitle,
            TextKey.RemoveClanMemberTitle,
            TextKey.TournamentCancelled,
            TextKey.ClearAllNotificationsTitle,
            TextKey.DeleteNotificationTitle,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
