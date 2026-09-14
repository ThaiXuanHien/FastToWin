package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchmakingLocalizationTest {
    @Test
    fun `matchmaking status resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Matchmaking", "Match found", "Finding an opponent…", "Prioritising players near your Elo.", "Finding players in the same mode.", "Elo range", "No Elo impact", "CANCEL MATCHMAKING"),
            AppLanguage.VIETNAMESE to listOf("Ghép đối thủ", "Đã ghép trận", "Đang tìm đối thủ…", "Ưu tiên người chơi có Elo gần bạn.", "Đang ghép người chơi cùng chế độ.", "Khoảng Elo", "Không ảnh hưởng Elo", "HỦY GHÉP TRẬN"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("匹配对手", "已找到对手", "正在寻找对手…", "优先匹配 Elo 接近的玩家。", "正在匹配相同模式的玩家。", "Elo 范围", "不影响 Elo", "取消匹配"),
            AppLanguage.JAPANESE to listOf("マッチング", "対戦相手が見つかりました", "対戦相手を探しています…", "Eloが近いプレイヤーを優先します。", "同じモードのプレイヤーを探しています。", "Elo範囲", "Eloへの影響なし", "マッチングをキャンセル"),
            AppLanguage.KOREAN to listOf("매칭", "상대를 찾았습니다", "상대를 찾는 중…", "Elo가 비슷한 플레이어를 우선합니다.", "같은 모드의 플레이어를 찾는 중입니다.", "Elo 범위", "Elo에 영향 없음", "매칭 취소"),
            AppLanguage.SPANISH to listOf("Emparejamiento", "Partida encontrada", "Buscando oponente…", "Se priorizan jugadores con un Elo cercano.", "Buscando jugadores del mismo modo.", "Rango de Elo", "No afecta al Elo", "CANCELAR BÚSQUEDA"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Pareamento", "Partida encontrada", "Procurando adversário…", "Priorizando jogadores com Elo próximo.", "Procurando jogadores no mesmo modo.", "Faixa de Elo", "Não afeta o Elo", "CANCELAR BUSCA"),
            AppLanguage.FRENCH to listOf("Recherche d’adversaire", "Adversaire trouvé", "Recherche d’un adversaire…", "Priorité aux joueurs avec un Elo proche.", "Recherche de joueurs dans le même mode.", "Plage d’Elo", "Aucun impact sur l’Elo", "ANNULER LA RECHERCHE"),
            AppLanguage.GERMAN to listOf("Spielersuche", "Gegner gefunden", "Gegner wird gesucht…", "Spieler mit ähnlichem Elo werden bevorzugt.", "Spieler im gleichen Modus werden gesucht.", "Elo-Bereich", "Kein Elo-Einfluss", "SPIELERSUCHE ABBRECHEN"),
            AppLanguage.INDONESIAN to listOf("Pencarian lawan", "Lawan ditemukan", "Mencari lawan…", "Memprioritaskan pemain dengan Elo yang berdekatan.", "Mencari pemain dalam mode yang sama.", "Rentang Elo", "Tidak memengaruhi Elo", "BATAL CARI LAWAN"),
            AppLanguage.THAI to listOf("จับคู่", "พบคู่แข่งแล้ว", "กำลังค้นหาคู่แข่ง…", "ให้ความสำคัญกับผู้เล่นที่มี Elo ใกล้เคียง", "กำลังค้นหาผู้เล่นในโหมดเดียวกัน", "ช่วง Elo", "ไม่มีผลต่อ Elo", "ยกเลิกการจับคู่"),
            AppLanguage.RUSSIAN to listOf("Подбор соперника", "Соперник найден", "Поиск соперника…", "Приоритет игрокам с близким Elo.", "Поиск игроков в том же режиме.", "Диапазон Elo", "Не влияет на Elo", "ОТМЕНИТЬ ПОИСК"),
        )
        val keys = listOf(
            TextKey.Matchmaking,
            TextKey.MatchFound,
            TextKey.FindingOpponent,
            TextKey.RankedMatchmakingHint,
            TextKey.CasualMatchmakingHint,
            TextKey.EloRange,
            TextKey.NoEloImpact,
            TextKey.CancelMatchmaking,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `public room list copy resolves explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("PUBLIC ROOMS", "Find the right opponent", "Create a private room or join a waiting room.", "Search room or host name", "All", "Waiting", "{count} rooms", "No rooms are waiting. Create one or pull down to refresh.", "No rooms match these filters."),
            AppLanguage.VIETNAMESE to listOf("PHÒNG CÔNG KHAI", "Tìm đối thủ phù hợp", "Tạo phòng riêng hoặc tham gia phòng đang chờ.", "Tìm tên phòng hoặc chủ phòng", "Tất cả", "Đang chờ", "{count} phòng", "Chưa có phòng đang chờ. Hãy tạo phòng mới hoặc kéo xuống để làm mới.", "Không tìm thấy phòng phù hợp với bộ lọc."),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("公开房间", "寻找合适的对手", "创建私密房间或加入等待中的房间。", "搜索房间或房主名称", "全部", "等待中", "{count} 个房间", "暂无等待中的房间。请创建一个或下拉刷新。", "没有符合筛选条件的房间。"),
            AppLanguage.JAPANESE to listOf("公開ルーム", "ぴったりの対戦相手を探す", "非公開ルームを作成するか、待機中のルームに参加しましょう。", "ルーム名またはホスト名を検索", "すべて", "待機中", "{count}ルーム", "待機中のルームはありません。作成するか、下に引いて更新してください。", "条件に一致するルームがありません。"),
            AppLanguage.KOREAN to listOf("공개 방", "알맞은 상대 찾기", "비공개 방을 만들거나 대기 중인 방에 참가하세요.", "방 또는 방장 이름 검색", "전체", "대기 중", "방 {count}개", "대기 중인 방이 없습니다. 새로 만들거나 아래로 당겨 새로고침하세요.", "필터와 일치하는 방이 없습니다."),
            AppLanguage.SPANISH to listOf("SALAS PÚBLICAS", "Encuentra al oponente adecuado", "Crea una sala privada o únete a una sala en espera.", "Buscar sala o anfitrión", "Todas", "En espera", "{count} salas", "No hay salas en espera. Crea una o desliza hacia abajo para actualizar.", "No hay salas que coincidan con estos filtros."),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("SALAS PÚBLICAS", "Encontre o adversário ideal", "Crie uma sala privada ou entre em uma sala em espera.", "Buscar sala ou anfitrião", "Todas", "Aguardando", "{count} salas", "Nenhuma sala está aguardando. Crie uma ou puxe para atualizar.", "Nenhuma sala corresponde a estes filtros."),
            AppLanguage.FRENCH to listOf("SALONS PUBLICS", "Trouve le bon adversaire", "Crée un salon privé ou rejoins un salon en attente.", "Rechercher un salon ou un hôte", "Tous", "En attente", "{count} salons", "Aucun salon en attente. Crée-en un ou tire vers le bas pour actualiser.", "Aucun salon ne correspond à ces filtres."),
            AppLanguage.GERMAN to listOf("ÖFFENTLICHE RÄUME", "Passenden Gegner finden", "Erstelle einen privaten Raum oder tritt einem offenen Raum bei.", "Raum oder Host suchen", "Alle", "Wartend", "{count} Räume", "Keine Räume warten. Erstelle einen oder ziehe zum Aktualisieren nach unten.", "Keine Räume entsprechen diesen Filtern."),
            AppLanguage.INDONESIAN to listOf("RUANG PUBLIK", "Temukan lawan yang tepat", "Buat ruang privat atau gabung ke ruang yang sedang menunggu.", "Cari ruang atau nama host", "Semua", "Menunggu", "{count} ruang", "Tidak ada ruang yang sedang menunggu. Buat ruang atau tarik ke bawah untuk menyegarkan.", "Tidak ada ruang yang cocok dengan filter ini."),
            AppLanguage.THAI to listOf("ห้องสาธารณะ", "ค้นหาคู่แข่งที่เหมาะสม", "สร้างห้องส่วนตัวหรือเข้าร่วมห้องที่กำลังรอ", "ค้นหาห้องหรือชื่อโฮสต์", "ทั้งหมด", "กำลังรอ", "{count} ห้อง", "ไม่มีห้องที่กำลังรอ สร้างห้องใหม่หรือดึงลงเพื่อรีเฟรช", "ไม่มีห้องที่ตรงกับตัวกรองนี้"),
            AppLanguage.RUSSIAN to listOf("ОТКРЫТЫЕ КОМНАТЫ", "Найдите подходящего соперника", "Создайте закрытую комнату или войдите в комнату ожидания.", "Поиск комнаты или ведущего", "Все", "Ожидание", "Комнат: {count}", "Нет комнат в ожидании. Создайте комнату или потяните вниз для обновления.", "Нет комнат, соответствующих этим фильтрам."),
        )
        val keys = listOf(
            TextKey.PublicRooms,
            TextKey.FindOpponent,
            TextKey.PublicRoomsDescription,
            TextKey.SearchRooms,
            TextKey.All,
            TextKey.Waiting,
            TextKey.RoomCount,
            TextKey.NoWaitingRooms,
            TextKey.NoMatchingRooms,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room connection progress and invalid link resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Reconnecting…", "Authenticating session…", "Connecting…", "The linked room no longer exists or is full."),
            AppLanguage.VIETNAMESE to listOf("Đang kết nối lại…", "Đang xác thực phiên chơi…", "Đang kết nối…", "Phòng trong liên kết không còn tồn tại hoặc đã đủ người."),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("正在重新连接…", "正在验证会话…", "正在连接…", "链接中的房间已不存在或已满。"),
            AppLanguage.JAPANESE to listOf("再接続中…", "セッションを認証しています…", "接続中…", "リンク先のルームは存在しないか、満員です。"),
            AppLanguage.KOREAN to listOf("재연결 중…", "세션 인증 중…", "연결 중…", "링크된 방이 더 이상 존재하지 않거나 가득 찼습니다."),
            AppLanguage.SPANISH to listOf("Reconectando…", "Autenticando sesión…", "Conectando…", "La sala del enlace ya no existe o está llena."),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Reconectando…", "Autenticando sessão…", "Conectando…", "A sala do link não existe mais ou está cheia."),
            AppLanguage.FRENCH to listOf("Reconnexion…", "Authentification de la session…", "Connexion…", "Le salon lié n’existe plus ou est complet."),
            AppLanguage.GERMAN to listOf("Verbindung wird wiederhergestellt…", "Sitzung wird authentifiziert…", "Verbindung wird hergestellt…", "Der verknüpfte Raum existiert nicht mehr oder ist voll."),
            AppLanguage.INDONESIAN to listOf("Menghubungkan kembali…", "Mengautentikasi sesi…", "Menghubungkan…", "Ruang pada tautan sudah tidak ada atau penuh."),
            AppLanguage.THAI to listOf("กำลังเชื่อมต่อใหม่…", "กำลังยืนยันเซสชัน…", "กำลังเชื่อมต่อ…", "ห้องจากลิงก์ไม่มีอยู่แล้วหรือเต็ม"),
            AppLanguage.RUSSIAN to listOf("Повторное подключение…", "Проверка сеанса…", "Подключение…", "Комната по ссылке больше не существует или заполнена."),
        )
        val keys = listOf(
            TextKey.Reconnecting,
            TextKey.AuthenticatingSession,
            TextKey.Connecting,
            TextKey.RoomLinkUnavailable,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
