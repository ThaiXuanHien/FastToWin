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

    @Test
    fun `social and shop descriptions resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Connect with a player code and invite friends to a match.", "No teammates yet. Enter a player code to send an invitation.", "Join a clan, complete quests and climb the ranks together.", "Try another keyword or create your own clan.", "Gather 4, 8 or 16 contenders and reach for the trophy.", "Invitations, rewards and news will appear here.", "New items will arrive here soon."),
            AppLanguage.VIETNAMESE to listOf("Kết nối bằng mã người chơi và mời bạn vào trận.", "Chưa có đồng đội. Nhập mã người chơi để gửi lời mời.", "Gia nhập bang hội, hoàn thành nhiệm vụ và cùng nhau leo hạng.", "Thử từ khóa khác hoặc tạo bang của riêng bạn.", "Tập hợp 4, 8 hoặc 16 chiến binh và chạm tay vào cúp vô địch.", "Lời mời, phần thưởng và tin mới sẽ xuất hiện tại đây.", "Vật phẩm mới sẽ sớm xuất hiện tại quầy này."),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("通过玩家代码添加好友并邀请他们参加对战。", "还没有队友。输入玩家代码发送邀请。", "加入战队、完成任务，与队友一起攀登排行榜。", "尝试其他关键词，或创建自己的战队。", "召集4、8或16名选手，向奖杯发起冲击。", "邀请、奖励和新消息会显示在这里。", "新商品即将上架。"),
            AppLanguage.JAPANESE to listOf("プレイヤーコードでつながり、フレンドを対戦に招待しましょう。", "まだ仲間がいません。プレイヤーコードを入力して招待を送りましょう。", "クランに参加し、ミッションを達成して仲間とランクを上げましょう。", "別のキーワードを試すか、自分のクランを作成してください。", "4人、8人、16人の挑戦者を集め、優勝を目指しましょう。", "招待、報酬、新着情報がここに表示されます。", "新しい商品がまもなく登場します。"),
            AppLanguage.KOREAN to listOf("플레이어 코드로 친구를 연결하고 대전에 초대하세요.", "아직 동료가 없습니다. 플레이어 코드를 입력해 초대를 보내세요.", "클랜에 가입하고 임무를 완료하며 함께 순위를 올리세요.", "다른 검색어를 시도하거나 직접 클랜을 만드세요.", "4명, 8명 또는 16명의 도전자를 모아 우승에 도전하세요.", "초대, 보상, 새로운 소식이 여기에 표시됩니다.", "새로운 상품이 곧 추가됩니다."),
            AppLanguage.SPANISH to listOf("Conecta con un código de jugador e invita a tus amigos a una partida.", "Aún no tienes compañeros. Introduce un código de jugador para enviar una invitación.", "Únete a un clan, completa misiones y sube de rango en equipo.", "Prueba otra palabra o crea tu propio clan.", "Reúne a 4, 8 o 16 participantes y ve por el trofeo.", "Aquí aparecerán invitaciones, recompensas y novedades.", "Pronto llegarán nuevos artículos."),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Conecte-se com um código de jogador e convide amigos para uma partida.", "Ainda não há companheiros. Digite um código de jogador para enviar um convite.", "Entre em um clã, cumpra missões e suba no ranking com sua equipe.", "Tente outra palavra ou crie seu próprio clã.", "Reúna 4, 8 ou 16 competidores e dispute o troféu.", "Convites, recompensas e novidades aparecerão aqui.", "Novos itens chegarão em breve."),
            AppLanguage.FRENCH to listOf("Ajoute des joueurs avec leur code et invite tes amis à jouer.", "Aucun coéquipier pour le moment. Saisis un code joueur pour envoyer une invitation.", "Rejoins un clan, accomplis des missions et grimpe au classement en équipe.", "Essaie un autre mot-clé ou crée ton propre clan.", "Réunis 4, 8 ou 16 participants et vise le trophée.", "Les invitations, récompenses et nouveautés apparaîtront ici.", "De nouveaux objets arriveront bientôt."),
            AppLanguage.GERMAN to listOf("Verbinde dich per Spielercode und lade Freunde zu einem Match ein.", "Noch keine Mitspieler. Gib einen Spielercode ein, um eine Einladung zu senden.", "Tritt einem Clan bei, erfülle Aufgaben und steigt gemeinsam auf.", "Versuche ein anderes Stichwort oder erstelle deinen eigenen Clan.", "Versammle 4, 8 oder 16 Teilnehmer und kämpfe um den Pokal.", "Einladungen, Belohnungen und Neuigkeiten erscheinen hier.", "Neue Gegenstände sind bald verfügbar."),
            AppLanguage.INDONESIAN to listOf("Terhubung dengan kode pemain dan undang teman ke pertandingan.", "Belum ada rekan tim. Masukkan kode pemain untuk mengirim undangan.", "Gabung klan, selesaikan misi, dan naik peringkat bersama.", "Coba kata kunci lain atau buat klanmu sendiri.", "Kumpulkan 4, 8, atau 16 peserta dan raih trofi.", "Undangan, hadiah, dan kabar baru akan muncul di sini.", "Item baru akan segera hadir."),
            AppLanguage.THAI to listOf("เชื่อมต่อด้วยรหัสผู้เล่นและชวนเพื่อนเข้าร่วมการแข่งขัน", "ยังไม่มีเพื่อนร่วมทีม กรอกรหัสผู้เล่นเพื่อส่งคำเชิญ", "เข้าร่วมแคลน ทำภารกิจ และไต่อันดับไปด้วยกัน", "ลองใช้คำค้นอื่นหรือสร้างแคลนของคุณเอง", "รวมผู้เข้าแข่งขัน 4, 8 หรือ 16 คน แล้วมุ่งสู่ถ้วยรางวัล", "คำเชิญ รางวัล และข่าวสารจะแสดงที่นี่", "ไอเทมใหม่กำลังจะมาเร็ว ๆ นี้"),
            AppLanguage.RUSSIAN to listOf("Добавляйте друзей по коду игрока и приглашайте их в матч.", "У вас пока нет товарищей. Введите код игрока, чтобы отправить приглашение.", "Вступайте в клан, выполняйте задания и поднимайтесь в рейтинге вместе.", "Попробуйте другое слово или создайте собственный клан.", "Соберите 4, 8 или 16 участников и сразитесь за кубок.", "Здесь появятся приглашения, награды и новости.", "Новые предметы скоро появятся."),
        )
        val keys = listOf(
            TextKey.YourSquadDescription,
            TextKey.NoFriendsDescription,
            TextKey.ClanTogetherDescription,
            TextKey.NoClansFoundDescription,
            TextKey.KnockoutArenaDescription,
            TextKey.EmptyInboxDescription,
            TextKey.RestockingDescription,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `clan donation summary resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("CLAN PROGRESS", "DONATE", "Contribute Gold or Gems to grow your clan.", "Balance: {amount} {currency}", "Clan XP gained: {xp}", "Your contribution"),
            AppLanguage.VIETNAMESE to listOf("TIẾN TRÌNH BANG", "QUYÊN GÓP", "Góp Vàng hoặc Gem để cùng phát triển bang hội.", "Số dư: {amount} {currency}", "Bang nhận {xp} XP", "Bạn đã góp"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("战队进度", "捐献", "捐献金币或宝石，助力战队成长。", "余额：{amount} {currency}", "获得战队经验：{xp}", "你的贡献"),
            AppLanguage.JAPANESE to listOf("クラン進捗", "寄付", "ゴールドまたはジェムを寄付してクランを成長させましょう。", "残高：{amount} {currency}", "獲得クランXP：{xp}", "あなたの貢献"),
            AppLanguage.KOREAN to listOf("클랜 진행도", "기부", "골드 또는 젬을 기부해 클랜을 성장시키세요.", "잔액: {amount} {currency}", "획득 클랜 XP: {xp}", "나의 기여"),
            AppLanguage.SPANISH to listOf("PROGRESO DEL CLAN", "DONAR", "Aporta oro o gemas para hacer crecer tu clan.", "Saldo: {amount} {currency}", "XP de clan obtenida: {xp}", "Tu contribución"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("PROGRESSO DO CLÃ", "DOAR", "Contribua com Ouro ou Gemas para fortalecer seu clã.", "Saldo: {amount} {currency}", "XP do clã recebida: {xp}", "Sua contribuição"),
            AppLanguage.FRENCH to listOf("PROGRESSION DU CLAN", "DONNER", "Donne de l’Or ou des Gemmes pour développer ton clan.", "Solde : {amount} {currency}", "XP de clan gagnée : {xp}", "Ta contribution"),
            AppLanguage.GERMAN to listOf("CLANFORTSCHRITT", "SPENDEN", "Spende Gold oder Juwelen, um deinen Clan zu stärken.", "Guthaben: {amount} {currency}", "Erhaltene Clan-XP: {xp}", "Dein Beitrag"),
            AppLanguage.INDONESIAN to listOf("PROGRES KLAN", "DONASI", "Sumbangkan Emas atau Gem untuk mengembangkan klanmu.", "Saldo: {amount} {currency}", "XP klan diperoleh: {xp}", "Kontribusimu"),
            AppLanguage.THAI to listOf("ความคืบหน้าแคลน", "บริจาค", "บริจาคทองหรือเจมเพื่อพัฒนาแคลนของคุณ", "ยอดคงเหลือ: {amount} {currency}", "XP แคลนที่ได้รับ: {xp}", "ผลงานของคุณ"),
            AppLanguage.RUSSIAN to listOf("ПРОГРЕСС КЛАНА", "ПОЖЕРТВОВАТЬ", "Жертвуйте золото или кристаллы для развития клана.", "Баланс: {amount} {currency}", "Получено опыта клана: {xp}", "Ваш вклад"),
        )
        val keys = listOf(
            TextKey.ClanProgress,
            TextKey.DonateToClan,
            TextKey.DonateToClanDescription,
            TextKey.DonationBalance,
            TextKey.DonationXpPreview,
            TextKey.YourContribution,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `clan donation feedback resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Donations cannot be undone.", "Your clan gained {xp} XP.", "This donation was already completed.", "Choose a valid donation amount.", "You do not have enough {currency}.", "Join this clan before donating.", "Could not complete the donation. Please try again."),
            AppLanguage.VIETNAMESE to listOf("Khoản quyên góp không thể hoàn tác.", "Bang đã nhận {xp} XP.", "Khoản quyên góp này đã hoàn tất trước đó.", "Hãy chọn mức quyên góp hợp lệ.", "Bạn không đủ {currency}.", "Hãy tham gia bang trước khi quyên góp.", "Chưa thể quyên góp. Vui lòng thử lại."),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("捐献无法撤销。", "你的战队获得了 {xp} 经验。", "该捐献已完成。", "请选择有效的捐献数量。", "你的 {currency} 不足。", "请先加入该战队再进行捐献。", "无法完成捐献，请重试。"),
            AppLanguage.JAPANESE to listOf("寄付は取り消せません。", "クランが {xp} XPを獲得しました。", "この寄付はすでに完了しています。", "有効な寄付額を選択してください。", "{currency}が足りません。", "寄付する前にこのクランに参加してください。", "寄付を完了できませんでした。もう一度お試しください。"),
            AppLanguage.KOREAN to listOf("기부는 취소할 수 없습니다.", "클랜이 {xp} XP를 획득했습니다.", "이미 완료된 기부입니다.", "올바른 기부 금액을 선택하세요.", "{currency}이(가) 부족합니다.", "기부하기 전에 이 클랜에 가입하세요.", "기부를 완료할 수 없습니다. 다시 시도하세요."),
            AppLanguage.SPANISH to listOf("Las donaciones no se pueden deshacer.", "Tu clan ha obtenido {xp} XP.", "Esta donación ya se completó.", "Elige una cantidad de donación válida.", "No tienes suficiente {currency}.", "Únete a este clan antes de donar.", "No se pudo completar la donación. Inténtalo de nuevo."),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("As doações não podem ser desfeitas.", "Seu clã recebeu {xp} XP.", "Esta doação já foi concluída.", "Escolha um valor de doação válido.", "Você não tem {currency} suficiente.", "Entre neste clã antes de doar.", "Não foi possível concluir a doação. Tente novamente."),
            AppLanguage.FRENCH to listOf("Les dons ne peuvent pas être annulés.", "Ton clan a gagné {xp} XP.", "Ce don a déjà été effectué.", "Choisis un montant de don valide.", "Tu n’as pas assez de {currency}.", "Rejoins ce clan avant de faire un don.", "Impossible d’effectuer le don. Réessaie."),
            AppLanguage.GERMAN to listOf("Spenden können nicht rückgängig gemacht werden.", "Dein Clan hat {xp} XP erhalten.", "Diese Spende wurde bereits abgeschlossen.", "Wähle einen gültigen Spendenbetrag.", "Du hast nicht genug {currency}.", "Tritt diesem Clan bei, bevor du spendest.", "Die Spende konnte nicht abgeschlossen werden. Versuche es erneut."),
            AppLanguage.INDONESIAN to listOf("Donasi tidak dapat dibatalkan.", "Klanmu memperoleh {xp} XP.", "Donasi ini sudah selesai.", "Pilih jumlah donasi yang valid.", "{currency} milikmu tidak cukup.", "Gabung klan ini sebelum berdonasi.", "Donasi tidak dapat diselesaikan. Coba lagi."),
            AppLanguage.THAI to listOf("ไม่สามารถยกเลิกการบริจาคได้", "แคลนของคุณได้รับ {xp} XP", "การบริจาคนี้เสร็จสิ้นแล้ว", "เลือกจำนวนบริจาคที่ถูกต้อง", "คุณมี {currency} ไม่เพียงพอ", "เข้าร่วมแคลนนี้ก่อนบริจาค", "ไม่สามารถดำเนินการบริจาคได้ โปรดลองอีกครั้ง"),
            AppLanguage.RUSSIAN to listOf("Пожертвования нельзя отменить.", "Ваш клан получил {xp} опыта.", "Это пожертвование уже выполнено.", "Выберите допустимую сумму пожертвования.", "У вас недостаточно {currency}.", "Вступите в этот клан перед пожертвованием.", "Не удалось выполнить пожертвование. Попробуйте ещё раз."),
        )
        val keys = listOf(
            TextKey.DonationCannotUndo,
            TextKey.DonationApplied,
            TextKey.DonationDuplicate,
            TextKey.DonationInvalidAmount,
            TextKey.DonationInsufficientFunds,
            TextKey.DonationMembershipRequired,
            TextKey.DonationFailed,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room and tournament invitations resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Invited you to “{room}”", "ROOM INVITATION", "{player} is waiting for you", "ROOM", "Tournament invitation", "{host} invited you to “{tournament}” · {mode} · {players} players.", "{host} invited you · {mode} · {players} players"),
            AppLanguage.VIETNAMESE to listOf("Mời bạn vào “{room}”", "LỜI MỜI VÀO PHÒNG", "{player} đang chờ bạn", "PHÒNG", "Lời mời đấu giải", "{host} mời bạn tham gia “{tournament}” · {mode} · {players} người.", "{host} mời bạn · {mode} · {players} người"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("邀请你加入“{room}”", "房间邀请", "{player} 正在等你", "房间", "锦标赛邀请", "{host} 邀请你参加“{tournament}” · {mode} · {players} 名玩家。", "{host} 邀请你 · {mode} · {players} 名玩家"),
            AppLanguage.JAPANESE to listOf("「{room}」に招待されました", "ルーム招待", "{player}があなたを待っています", "ルーム", "トーナメント招待", "{host}が「{tournament}」に招待しました · {mode} · {players}人。", "{host}からの招待 · {mode} · {players}人"),
            AppLanguage.KOREAN to listOf("“{room}” 방으로 초대했습니다", "방 초대", "{player}님이 기다리고 있습니다", "방", "토너먼트 초대", "{host}님이 “{tournament}”에 초대했습니다 · {mode} · {players}명.", "{host}님의 초대 · {mode} · {players}명"),
            AppLanguage.SPANISH to listOf("Te invitó a “{room}”", "INVITACIÓN A SALA", "{player} te está esperando", "SALA", "Invitación a torneo", "{host} te invitó a “{tournament}” · {mode} · {players} jugadores.", "Invitación de {host} · {mode} · {players} jugadores"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Convidou você para “{room}”", "CONVITE PARA SALA", "{player} está esperando por você", "SALA", "Convite para torneio", "{host} convidou você para “{tournament}” · {mode} · {players} jogadores.", "Convite de {host} · {mode} · {players} jogadores"),
            AppLanguage.FRENCH to listOf("T’a invité dans « {room} »", "INVITATION DE SALON", "{player} t’attend", "SALON", "Invitation à un tournoi", "{host} t’a invité au tournoi « {tournament} » · {mode} · {players} joueurs.", "Invitation de {host} · {mode} · {players} joueurs"),
            AppLanguage.GERMAN to listOf("Hat dich in „{room}“ eingeladen", "RAUMEINLADUNG", "{player} wartet auf dich", "RAUM", "Turniereinladung", "{host} hat dich zu „{tournament}“ eingeladen · {mode} · {players} Spieler.", "Einladung von {host} · {mode} · {players} Spieler"),
            AppLanguage.INDONESIAN to listOf("Mengundangmu ke “{room}”", "UNDANGAN RUANG", "{player} sedang menunggumu", "RUANG", "Undangan turnamen", "{host} mengundangmu ke “{tournament}” · {mode} · {players} pemain.", "Undangan dari {host} · {mode} · {players} pemain"),
            AppLanguage.THAI to listOf("เชิญคุณเข้าร่วม “{room}”", "คำเชิญเข้าห้อง", "{player} กำลังรอคุณ", "ห้อง", "คำเชิญเข้าร่วมทัวร์นาเมนต์", "{host} เชิญคุณเข้าร่วม “{tournament}” · {mode} · {players} คน", "คำเชิญจาก {host} · {mode} · {players} คน"),
            AppLanguage.RUSSIAN to listOf("Приглашает вас в «{room}»", "ПРИГЛАШЕНИЕ В КОМНАТУ", "{player} ждёт вас", "КОМНАТА", "Приглашение на турнир", "{host} приглашает вас на «{tournament}» · {mode} · {players} игроков.", "Приглашение от {host} · {mode} · {players} игроков"),
        )
        val keys = listOf(
            TextKey.RoomInvitationSummary,
            TextKey.RoomInvitationTitle,
            TextKey.WaitingForYou,
            TextKey.RoomLabel,
            TextKey.TournamentInvitationTitle,
            TextKey.TournamentInvitationDescription,
            TextKey.TournamentInviteCompact,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `clan member management resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Join requests ({count})", "Approve", "{role} · {trophies} trophies", "Remove {player} from clan", "Choose Logo", "Leader", "Co-leader", "Member"),
            AppLanguage.VIETNAMESE to listOf("Yêu cầu tham gia ({count})", "Duyệt", "{role} · {trophies} cúp", "Mời {player} rời bang", "Chọn Logo", "Bang chủ", "Phó bang", "Thành viên"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("加入申请（{count}）", "批准", "{role} · {trophies} 个奖杯", "将 {player} 移出战队", "选择徽标", "队长", "副队长", "成员"),
            AppLanguage.JAPANESE to listOf("参加申請（{count}）", "承認", "{role} · トロフィー{trophies}個", "{player}をクランから除名", "ロゴを選択", "リーダー", "サブリーダー", "メンバー"),
            AppLanguage.KOREAN to listOf("가입 요청 ({count})", "승인", "{role} · 트로피 {trophies}개", "{player}님을 클랜에서 추방", "로고 선택", "클랜장", "부클랜장", "멤버"),
            AppLanguage.SPANISH to listOf("Solicitudes de ingreso ({count})", "Aprobar", "{role} · {trophies} trofeos", "Expulsar a {player} del clan", "Elegir logo", "Líder", "Colíder", "Miembro"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Solicitações de entrada ({count})", "Aprovar", "{role} · {trophies} troféus", "Remover {player} do clã", "Escolher logo", "Líder", "Vice-líder", "Membro"),
            AppLanguage.FRENCH to listOf("Demandes d’adhésion ({count})", "Approuver", "{role} · {trophies} trophées", "Exclure {player} du clan", "Choisir le logo", "Chef", "Chef adjoint", "Membre"),
            AppLanguage.GERMAN to listOf("Beitrittsanfragen ({count})", "Genehmigen", "{role} · {trophies} Trophäen", "{player} aus dem Clan entfernen", "Logo wählen", "Anführer", "Co-Anführer", "Mitglied"),
            AppLanguage.INDONESIAN to listOf("Permintaan bergabung ({count})", "Setujui", "{role} · {trophies} trofi", "Keluarkan {player} dari klan", "Pilih logo", "Pemimpin", "Wakil pemimpin", "Anggota"),
            AppLanguage.THAI to listOf("คำขอเข้าร่วม ({count})", "อนุมัติ", "{role} · {trophies} ถ้วย", "นำ {player} ออกจากแคลน", "เลือกโลโก้", "หัวหน้า", "รองหัวหน้า", "สมาชิก"),
            AppLanguage.RUSSIAN to listOf("Заявки на вступление ({count})", "Одобрить", "{role} · {trophies} трофеев", "Исключить {player} из клана", "Выбрать эмблему", "Лидер", "Заместитель", "Участник"),
        )
        val keys = listOf(
            TextKey.JoinRequestsCount,
            TextKey.Approve,
            TextKey.ClanMemberSummary,
            TextKey.RemoveClanMemberNamed,
            TextKey.ChooseClanLogo,
            TextKey.ClanRoleLeader,
            TextKey.ClanRoleCoLeader,
            TextKey.ClanRoleMember,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
