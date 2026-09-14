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
}
