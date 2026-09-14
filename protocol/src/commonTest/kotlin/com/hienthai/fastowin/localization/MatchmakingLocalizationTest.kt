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
}
