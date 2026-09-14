package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class RoomLobbyLocalizationTest {
    @Test
    fun `room lobby actions and readiness resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Wait for the host to start the match.", "CANCEL READY", "READY", "Waiting for everyone to be ready…", "INVITE FRIENDS", "KICK", "CLOSE ROOM", "LEAVE ROOM", "READY", "WAITING"),
            AppLanguage.VIETNAMESE to listOf("Chờ chủ phòng bắt đầu trận.", "HỦY SẴN SÀNG", "SẴN SÀNG", "Đang chờ người khác sẵn sàng…", "MỜI BẠN BÈ", "MỜI RA", "ĐÓNG PHÒNG", "RỜI PHÒNG", "SẴN SÀNG", "ĐANG CHỜ"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("等待房主开始比赛。", "取消准备", "准备", "等待所有人准备…", "邀请好友", "踢出", "关闭房间", "离开房间", "已准备", "等待中"),
            AppLanguage.JAPANESE to listOf("ホストが対戦を開始するまでお待ちください。", "準備を解除", "準備完了", "全員の準備完了を待っています…", "フレンドを招待", "退出させる", "ルームを閉じる", "ルームを退出", "準備完了", "待機中"),
            AppLanguage.KOREAN to listOf("방장이 경기를 시작할 때까지 기다리세요.", "준비 취소", "준비", "모두가 준비되기를 기다리는 중…", "친구 초대", "내보내기", "방 닫기", "방 나가기", "준비 완료", "대기 중"),
            AppLanguage.SPANISH to listOf("Espera a que el anfitrión inicie la partida.", "CANCELAR LISTO", "LISTO", "Esperando a que todos estén listos…", "INVITAR AMIGOS", "EXPULSAR", "CERRAR SALA", "SALIR DE LA SALA", "LISTO", "EN ESPERA"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Aguarde o anfitrião iniciar a partida.", "CANCELAR PRONTO", "PRONTO", "Aguardando todos ficarem prontos…", "CONVIDAR AMIGOS", "EXPULSAR", "FECHAR SALA", "SAIR DA SALA", "PRONTO", "AGUARDANDO"),
            AppLanguage.FRENCH to listOf("Attends que l’hôte lance la partie.", "ANNULER PRÊT", "PRÊT", "En attente que tout le monde soit prêt…", "INVITER DES AMIS", "EXCLURE", "FERMER LE SALON", "QUITTER LE SALON", "PRÊT", "EN ATTENTE"),
            AppLanguage.GERMAN to listOf("Warte, bis der Host das Match startet.", "BEREITSCHAFT AUFHEBEN", "BEREIT", "Warten, bis alle bereit sind…", "FREUNDE EINLADEN", "ENTFERNEN", "RAUM SCHLIESSEN", "RAUM VERLASSEN", "BEREIT", "WARTET"),
            AppLanguage.INDONESIAN to listOf("Tunggu host memulai pertandingan.", "BATAL SIAP", "SIAP", "Menunggu semua pemain siap…", "UNDANG TEMAN", "KELUARKAN", "TUTUP RUANG", "KELUAR DARI RUANG", "SIAP", "MENUNGGU"),
            AppLanguage.THAI to listOf("รอให้โฮสต์เริ่มการแข่งขัน", "ยกเลิกพร้อม", "พร้อม", "กำลังรอให้ทุกคนพร้อม…", "เชิญเพื่อน", "นำออก", "ปิดห้อง", "ออกจากห้อง", "พร้อม", "รอ"),
            AppLanguage.RUSSIAN to listOf("Дождитесь, пока ведущий начнёт матч.", "ОТМЕНИТЬ ГОТОВНОСТЬ", "ГОТОВ", "Ожидание готовности всех игроков…", "ПРИГЛАСИТЬ ДРУЗЕЙ", "ИСКЛЮЧИТЬ", "ЗАКРЫТЬ КОМНАТУ", "ПОКИНУТЬ КОМНАТУ", "ГОТОВ", "ОЖИДАНИЕ"),
        )
        val keys = listOf(
            TextKey.WaitHostStart,
            TextKey.CancelReady,
            TextKey.ReadyAction,
            TextKey.WaitingForReady,
            TextKey.InviteFriends,
            TextKey.Kick,
            TextKey.CloseRoom,
            TextKey.LeaveRoom,
            TextKey.ReadyStatus,
            TextKey.WaitingStatus,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room player labels resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Players", "Blue Team", "Red Team", "You", "Teammate", "Opponent", "Waiting…", "Host • You", "Guest", "Host", "Waiting for player…"),
            AppLanguage.VIETNAMESE to listOf("Người chơi", "Đội Xanh", "Đội Đỏ", "Bạn", "Đồng đội", "Đối thủ", "Đang chờ…", "Chủ phòng • Bạn", "Khách", "Chủ phòng", "Đang chờ người chơi…"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("玩家", "蓝队", "红队", "你", "队友", "对手", "等待中…", "房主 • 你", "客人", "房主", "等待玩家…"),
            AppLanguage.JAPANESE to listOf("プレイヤー", "ブルーチーム", "レッドチーム", "あなた", "チームメイト", "対戦相手", "待機中…", "ホスト • あなた", "ゲスト", "ホスト", "プレイヤーを待っています…"),
            AppLanguage.KOREAN to listOf("플레이어", "블루 팀", "레드 팀", "나", "팀원", "상대", "대기 중…", "방장 • 나", "게스트", "방장", "플레이어를 기다리는 중…"),
            AppLanguage.SPANISH to listOf("Jugadores", "Equipo azul", "Equipo rojo", "Tú", "Compañero", "Oponente", "Esperando…", "Anfitrión • Tú", "Invitado", "Anfitrión", "Esperando jugador…"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Jogadores", "Equipe Azul", "Equipe Vermelha", "Você", "Companheiro", "Adversário", "Aguardando…", "Anfitrião • Você", "Convidado", "Anfitrião", "Aguardando jogador…"),
            AppLanguage.FRENCH to listOf("Joueurs", "Équipe bleue", "Équipe rouge", "Toi", "Coéquipier", "Adversaire", "En attente…", "Hôte • Toi", "Invité", "Hôte", "En attente d’un joueur…"),
            AppLanguage.GERMAN to listOf("Spieler", "Blaues Team", "Rotes Team", "Du", "Mitspieler", "Gegner", "Wartet…", "Host • Du", "Gast", "Host", "Warte auf Spieler…"),
            AppLanguage.INDONESIAN to listOf("Pemain", "Tim Biru", "Tim Merah", "Kamu", "Rekan tim", "Lawan", "Menunggu…", "Host • Kamu", "Tamu", "Host", "Menunggu pemain…"),
            AppLanguage.THAI to listOf("ผู้เล่น", "ทีมสีน้ำเงิน", "ทีมสีแดง", "คุณ", "เพื่อนร่วมทีม", "คู่แข่ง", "กำลังรอ…", "โฮสต์ • คุณ", "ผู้เล่นรับเชิญ", "โฮสต์", "กำลังรอผู้เล่น…"),
            AppLanguage.RUSSIAN to listOf("Игроки", "Синяя команда", "Красная команда", "Вы", "Союзник", "Соперник", "Ожидание…", "Ведущий • Вы", "Гость", "Ведущий", "Ожидание игрока…"),
        )
        val keys = listOf(
            TextKey.PlayersTitle,
            TextKey.BlueTeam,
            TextKey.RedTeam,
            TextKey.You,
            TextKey.Teammate,
            TextKey.Opponent,
            TextKey.WaitingPlayer,
            TextKey.HostYou,
            TextKey.Guest,
            TextKey.Host,
            TextKey.WaitingForPlayer,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room discovery and access copy resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Enter room code", "Paste the code shared by the host.", "Room code", "FIND ROOM", "No room was found with this code.", "Create new room", "Room name", "Public", "Private", "Room password", "Join room"),
            AppLanguage.VIETNAMESE to listOf("Nhập mã phòng", "Dán mã được chủ phòng chia sẻ.", "Mã phòng", "TÌM PHÒNG", "Không tìm thấy phòng với mã này.", "Tạo phòng mới", "Tên phòng", "Công khai", "Riêng tư", "Mật khẩu phòng", "Tham gia phòng"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("输入房间代码", "粘贴房主分享的代码。", "房间代码", "查找房间", "未找到使用此代码的房间。", "创建新房间", "房间名称", "公开", "私密", "房间密码", "加入房间"),
            AppLanguage.JAPANESE to listOf("ルームコードを入力", "ホストが共有したコードを貼り付けてください。", "ルームコード", "ルームを検索", "このコードのルームが見つかりません。", "新しいルームを作成", "ルーム名", "公開", "非公開", "ルームパスワード", "ルームに参加"),
            AppLanguage.KOREAN to listOf("방 코드 입력", "방장이 공유한 코드를 붙여넣으세요.", "방 코드", "방 찾기", "이 코드에 해당하는 방을 찾을 수 없습니다.", "새 방 만들기", "방 이름", "공개", "비공개", "방 비밀번호", "방 참가"),
            AppLanguage.SPANISH to listOf("Introduce el código de la sala", "Pega el código compartido por el anfitrión.", "Código de sala", "BUSCAR SALA", "No se encontró ninguna sala con este código.", "Crear nueva sala", "Nombre de la sala", "Pública", "Privada", "Contraseña de la sala", "Unirse a la sala"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Digite o código da sala", "Cole o código compartilhado pelo anfitrião.", "Código da sala", "ENCONTRAR SALA", "Nenhuma sala foi encontrada com este código.", "Criar nova sala", "Nome da sala", "Pública", "Privada", "Senha da sala", "Entrar na sala"),
            AppLanguage.FRENCH to listOf("Saisis le code du salon", "Colle le code partagé par l’hôte.", "Code du salon", "TROUVER LE SALON", "Aucun salon trouvé avec ce code.", "Créer un nouveau salon", "Nom du salon", "Public", "Privé", "Mot de passe du salon", "Rejoindre le salon"),
            AppLanguage.GERMAN to listOf("Raumcode eingeben", "Füge den vom Host geteilten Code ein.", "Raumcode", "RAUM SUCHEN", "Mit diesem Code wurde kein Raum gefunden.", "Neuen Raum erstellen", "Raumname", "Öffentlich", "Privat", "Raumpasswort", "Raum beitreten"),
            AppLanguage.INDONESIAN to listOf("Masukkan kode ruang", "Tempel kode yang dibagikan host.", "Kode ruang", "CARI RUANG", "Ruang dengan kode ini tidak ditemukan.", "Buat ruang baru", "Nama ruang", "Publik", "Privat", "Kata sandi ruang", "Gabung ruang"),
            AppLanguage.THAI to listOf("กรอกรหัสห้อง", "วางรหัสที่โฮสต์แชร์", "รหัสห้อง", "ค้นหาห้อง", "ไม่พบห้องที่ใช้รหัสนี้", "สร้างห้องใหม่", "ชื่อห้อง", "สาธารณะ", "ส่วนตัว", "รหัสผ่านห้อง", "เข้าร่วมห้อง"),
            AppLanguage.RUSSIAN to listOf("Введите код комнаты", "Вставьте код, которым поделился ведущий.", "Код комнаты", "НАЙТИ КОМНАТУ", "Комната с таким кодом не найдена.", "Создать новую комнату", "Название комнаты", "Открытая", "Закрытая", "Пароль комнаты", "Войти в комнату"),
        )
        val keys = listOf(
            TextKey.EnterRoomCode,
            TextKey.PasteRoomCode,
            TextKey.RoomCode,
            TextKey.FindRoom,
            TextKey.RoomCodeNotFound,
            TextKey.CreateNewRoom,
            TextKey.RoomName,
            TextKey.Public,
            TextKey.Private,
            TextKey.RoomPassword,
            TextKey.JoinRoomTitle,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room visibility and match type copy resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Players need a password.", "Anyone can join without a password.", "Ranked · Affects Elo", "Casual · No Elo impact", "Private room", "Public room", "{host} • {mode} • {matchType}", "ENTER", "Private room"),
            AppLanguage.VIETNAMESE to listOf("Người chơi cần nhập mật khẩu.", "Mọi người có thể tham gia, không cần mật khẩu.", "Đấu xếp hạng · Có ảnh hưởng Elo", "Đấu thường · Không ảnh hưởng Elo", "Phòng riêng", "Phòng công khai", "{host} • {mode} • {matchType}", "VÀO", "Phòng riêng"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("玩家需要输入密码。", "任何人无需密码即可加入。", "排位赛 · 影响 Elo", "休闲赛 · 不影响 Elo", "私密房间", "公开房间", "{host} • {mode} • {matchType}", "进入", "私密房间"),
            AppLanguage.JAPANESE to listOf("参加者はパスワードが必要です。", "誰でもパスワードなしで参加できます。", "ランク戦 · Eloに影響", "通常戦 · Eloに影響なし", "非公開ルーム", "公開ルーム", "{host} • {mode} • {matchType}", "入室", "非公開ルーム"),
            AppLanguage.KOREAN to listOf("플레이어는 비밀번호가 필요합니다.", "누구나 비밀번호 없이 참가할 수 있습니다.", "랭크전 · Elo에 영향", "일반전 · Elo에 영향 없음", "비공개 방", "공개 방", "{host} • {mode} • {matchType}", "입장", "비공개 방"),
            AppLanguage.SPANISH to listOf("Los jugadores necesitan una contraseña.", "Cualquiera puede unirse sin contraseña.", "Clasificatoria · Afecta al Elo", "Casual · No afecta al Elo", "Sala privada", "Sala pública", "{host} • {mode} • {matchType}", "ENTRAR", "Sala privada"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Os jogadores precisam de uma senha.", "Qualquer pessoa pode entrar sem senha.", "Ranqueada · Afeta o Elo", "Casual · Não afeta o Elo", "Sala privada", "Sala pública", "{host} • {mode} • {matchType}", "ENTRAR", "Sala privada"),
            AppLanguage.FRENCH to listOf("Les joueurs ont besoin d’un mot de passe.", "Tout le monde peut rejoindre sans mot de passe.", "Classé · Affecte l’Elo", "Amical · Aucun impact sur l’Elo", "Salon privé", "Salon public", "{host} • {mode} • {matchType}", "ENTRER", "Salon privé"),
            AppLanguage.GERMAN to listOf("Spieler benötigen ein Passwort.", "Jeder kann ohne Passwort beitreten.", "Rangliste · Beeinflusst Elo", "Normal · Kein Elo-Einfluss", "Privater Raum", "Öffentlicher Raum", "{host} • {mode} • {matchType}", "BETRETEN", "Privater Raum"),
            AppLanguage.INDONESIAN to listOf("Pemain memerlukan kata sandi.", "Siapa pun dapat bergabung tanpa kata sandi.", "Peringkat · Memengaruhi Elo", "Kasual · Tidak memengaruhi Elo", "Ruang privat", "Ruang publik", "{host} • {mode} • {matchType}", "MASUK", "Ruang privat"),
            AppLanguage.THAI to listOf("ผู้เล่นต้องใช้รหัสผ่าน", "ทุกคนเข้าร่วมได้โดยไม่ต้องใช้รหัสผ่าน", "จัดอันดับ · มีผลต่อ Elo", "ทั่วไป · ไม่มีผลต่อ Elo", "ห้องส่วนตัว", "ห้องสาธารณะ", "{host} • {mode} • {matchType}", "เข้า", "ห้องส่วนตัว"),
            AppLanguage.RUSSIAN to listOf("Игрокам нужен пароль.", "Любой может войти без пароля.", "Рейтинг · Влияет на Elo", "Обычная · Не влияет на Elo", "Закрытая комната", "Открытая комната", "{host} • {mode} • {matchType}", "ВОЙТИ", "Закрытая комната"),
        )
        val keys = listOf(
            TextKey.PrivatePasswordRequired,
            TextKey.PublicNoPassword,
            TextKey.RankedEloImpact,
            TextKey.CasualNoEloImpact,
            TextKey.PrivateRoom,
            TextKey.PublicRoom,
            TextKey.RoomListSummary,
            TextKey.EnterRoom,
            TextKey.JoinPrivateRoom,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `joined room and sharing copy resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Enter the password to join “{room}”.", "{room} • Hosted by {host}", "ROOM CODE • {code}", "IN ROOM", "Share the code to invite friends.", "{mode} • 50 numbers", "SHARE ROOM CODE", "Could not open sharing. Please try again."),
            AppLanguage.VIETNAMESE to listOf("Nhập mật khẩu để tham gia “{room}”.", "{room} • Chủ phòng {host}", "MÃ PHÒNG • {code}", "ĐÃ VÀO PHÒNG", "Chia sẻ mã để mời bạn bè.", "{mode} • 50 số", "CHIA SẺ MÃ PHÒNG", "Không thể mở bảng chia sẻ. Vui lòng thử lại."),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("输入密码加入“{room}”。", "{room} • 房主 {host}", "房间代码 • {code}", "已加入房间", "分享代码以邀请好友。", "{mode} • 50个数字", "分享房间代码", "无法打开分享，请重试。"),
            AppLanguage.JAPANESE to listOf("「{room}」に参加するにはパスワードを入力してください。", "{room} • ホスト {host}", "ルームコード • {code}", "ルーム参加中", "コードを共有してフレンドを招待しましょう。", "{mode} • 50個の数字", "ルームコードを共有", "共有画面を開けませんでした。もう一度お試しください。"),
            AppLanguage.KOREAN to listOf("“{room}” 방에 참가하려면 비밀번호를 입력하세요.", "{room} • 방장 {host}", "방 코드 • {code}", "방 참가 중", "코드를 공유해 친구를 초대하세요.", "{mode} • 숫자 50개", "방 코드 공유", "공유 창을 열 수 없습니다. 다시 시도하세요."),
            AppLanguage.SPANISH to listOf("Introduce la contraseña para unirte a “{room}”.", "{room} • Anfitrión: {host}", "CÓDIGO DE SALA • {code}", "EN LA SALA", "Comparte el código para invitar a tus amigos.", "{mode} • 50 números", "COMPARTIR CÓDIGO DE SALA", "No se pudo abrir el menú para compartir. Inténtalo de nuevo."),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Digite a senha para entrar em “{room}”.", "{room} • Anfitrião: {host}", "CÓDIGO DA SALA • {code}", "NA SALA", "Compartilhe o código para convidar amigos.", "{mode} • 50 números", "COMPARTILHAR CÓDIGO DA SALA", "Não foi possível abrir o compartilhamento. Tente novamente."),
            AppLanguage.FRENCH to listOf("Saisis le mot de passe pour rejoindre « {room} ».", "{room} • Hôte : {host}", "CODE DU SALON • {code}", "DANS LE SALON", "Partage le code pour inviter tes amis.", "{mode} • 50 nombres", "PARTAGER LE CODE DU SALON", "Impossible d’ouvrir le partage. Réessaie."),
            AppLanguage.GERMAN to listOf("Gib das Passwort ein, um „{room}“ beizutreten.", "{room} • Host: {host}", "RAUMCODE • {code}", "IM RAUM", "Teile den Code, um Freunde einzuladen.", "{mode} • 50 Zahlen", "RAUMCODE TEILEN", "Teilen konnte nicht geöffnet werden. Versuche es erneut."),
            AppLanguage.INDONESIAN to listOf("Masukkan kata sandi untuk bergabung ke “{room}”.", "{room} • Host {host}", "KODE RUANG • {code}", "DI DALAM RUANG", "Bagikan kode untuk mengundang teman.", "{mode} • 50 angka", "BAGIKAN KODE RUANG", "Tidak dapat membuka menu berbagi. Coba lagi."),
            AppLanguage.THAI to listOf("กรอกรหัสผ่านเพื่อเข้าร่วม “{room}”", "{room} • โฮสต์ {host}", "รหัสห้อง • {code}", "อยู่ในห้อง", "แชร์รหัสเพื่อเชิญเพื่อน", "{mode} • 50 ตัวเลข", "แชร์รหัสห้อง", "ไม่สามารถเปิดการแชร์ได้ โปรดลองอีกครั้ง"),
            AppLanguage.RUSSIAN to listOf("Введите пароль, чтобы войти в «{room}».", "{room} • Ведущий: {host}", "КОД КОМНАТЫ • {code}", "В КОМНАТЕ", "Поделитесь кодом, чтобы пригласить друзей.", "{mode} • 50 чисел", "ПОДЕЛИТЬСЯ КОДОМ", "Не удалось открыть меню «Поделиться». Попробуйте ещё раз."),
        )
        val keys = listOf(
            TextKey.PrivateJoinDescription,
            TextKey.PublicJoinDescription,
            TextKey.JoinedRoomCode,
            TextKey.JoinedRoom,
            TextKey.ShareCodeHint,
            TextKey.ModeNumberCount,
            TextKey.ShareRoomCode,
            TextKey.ShareSheetError,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
