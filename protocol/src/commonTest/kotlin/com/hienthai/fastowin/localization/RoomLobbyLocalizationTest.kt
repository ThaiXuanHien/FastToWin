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
}
