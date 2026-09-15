package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val collectionMatchDetailsKeys = listOf(
    TextKey.LoadingMatch, TextKey.MatchDetails, TextKey.ReactionLabel,
    TextKey.NoEloChange, TextKey.MatchTapSummary, TextKey.Previous, TextKey.Next,
    TextKey.LockedCosmetic, TextKey.UnlockLevel,
    TextKey.UnlockPerfectFrame, TextKey.UnlockPersistentFrame,
    TextKey.UnlockChampionTitle, TextKey.UnlockSpeedTitle,
    TextKey.UnlockDiligentTitle, TextKey.UnlockCheckInAvatar,
    TextKey.AchievementWinTenTitle, TextKey.AchievementWinTenDescription,
    TextKey.AchievementPerfectTitle, TextKey.AchievementPerfectDescription,
    TextKey.AchievementSpeedTitle, TextKey.AchievementSpeedDescription,
    TextKey.AchievementCheckInTitle, TextKey.AchievementCheckInDescription,
)

private fun collectionMatchDetailsCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == collectionMatchDetailsKeys.size) {
        "Expected ${collectionMatchDetailsKeys.size} collection and match-detail translations, received ${values.size}."
    }
    return collectionMatchDetailsKeys.zip(values).toMap()
}

internal val collectionMatchDetailsTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to collectionMatchDetailsCopy(
        "正在加载对局", "对局详情", "反应时间", "Elo 无变化", "{duration} · 正确 {correct} · 错误 {wrong}", "上一页", "下一页",
        "已锁定 · {name}{requirement}", "等级 {level}", "达到 15 级并完美获胜 1 场", "签到 100 次",
        "赢得 10 场对局", "获胜并在 30 秒内找出全部 50 个数字", "连续签到 30 天", "签到 50 次",
        "十场胜利", "赢得 10 场对局", "完美对局", "无误触赢得对局",
        "闪电速度", "在 30 秒内找出全部 50 个数字", "稳步起航", "连续签到 7 天",
    ),
    AppLanguage.JAPANESE to collectionMatchDetailsCopy(
        "対戦を読み込み中", "対戦の詳細", "反応時間", "Eloの変動なし", "{duration} · 正解 {correct} · ミス {wrong}", "前へ", "次へ",
        "ロック中 · {name}{requirement}", "レベル {level}", "レベル15に到達してパーフェクト勝利", "ログイン100回",
        "10試合に勝利", "勝利して30秒以内に50個の数字をすべて見つける", "30日連続ログイン", "ログイン50回",
        "10勝", "10試合に勝利する", "パーフェクトマッチ", "ミスせずに勝利する",
        "稲妻の速さ", "30秒以内に50個の数字をすべて見つける", "着実なスタート", "7日連続でログインする",
    ),
    AppLanguage.KOREAN to collectionMatchDetailsCopy(
        "경기 불러오는 중", "경기 상세", "반응 시간", "Elo 변동 없음", "{duration} · 정답 {correct} · 오답 {wrong}", "이전", "다음",
        "잠김 · {name}{requirement}", "레벨 {level}", "레벨 15 달성 후 완벽한 승리", "100회 출석",
        "10경기 승리", "승리하고 30초 안에 숫자 50개 모두 찾기", "30일 연속 출석", "50회 출석",
        "10승", "10경기 승리하기", "완벽한 경기", "오답 없이 승리하기",
        "번개 같은 속도", "30초 안에 숫자 50개 모두 찾기", "꾸준한 시작", "7일 연속 출석하기",
    ),
    AppLanguage.SPANISH to collectionMatchDetailsCopy(
        "Cargando partida", "Detalles de la partida", "Reacción", "Sin cambios de Elo", "{duration} · {correct} aciertos · {wrong} errores", "ANTERIOR", "SIGUIENTE",
        "Bloqueado · {name}{requirement}", "Nivel {level}", "Nivel 15 y una victoria perfecta", "100 registros diarios",
        "Gana 10 partidas", "Gana y encuentra los 50 números en 30 segundos", "Racha de 30 días", "50 registros diarios",
        "Diez victorias", "Gana 10 partidas", "Partida perfecta", "Gana sin tocar un número incorrecto",
        "Velocidad relámpago", "Encuentra los 50 números en menos de 30 segundos", "Buen comienzo", "Regístrate durante 7 días consecutivos",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to collectionMatchDetailsCopy(
        "Carregando partida", "Detalhes da partida", "Reação", "Sem mudança no Elo", "{duration} · {correct} acertos · {wrong} erros", "ANTERIOR", "PRÓXIMO",
        "Bloqueado · {name}{requirement}", "Nível {level}", "Nível 15 e uma vitória perfeita", "100 presenças",
        "Vença 10 partidas", "Vença e encontre os 50 números em 30 segundos", "Sequência de 30 dias", "50 presenças",
        "Dez vitórias", "Vença 10 partidas", "Partida perfeita", "Vença sem tocar em um número errado",
        "Velocidade relâmpago", "Encontre os 50 números em até 30 segundos", "Começo firme", "Registre presença por 7 dias seguidos",
    ),
    AppLanguage.FRENCH to collectionMatchDetailsCopy(
        "Chargement de la partie", "Détails de la partie", "Réaction", "Aucun changement d'Elo", "{duration} · {correct} justes · {wrong} erreurs", "PRÉCÉDENT", "SUIVANT",
        "Verrouillé · {name}{requirement}", "Niveau {level}", "Niveau 15 et une victoire parfaite", "100 présences",
        "Gagnez 10 parties", "Gagnez et trouvez les 50 nombres en 30 secondes", "Série de 30 jours", "50 présences",
        "Dix victoires", "Gagnez 10 parties", "Partie parfaite", "Gagnez sans toucher un mauvais nombre",
        "Vitesse éclair", "Trouvez les 50 nombres en moins de 30 secondes", "Départ régulier", "Enregistrez votre présence 7 jours de suite",
    ),
    AppLanguage.GERMAN to collectionMatchDetailsCopy(
        "Match wird geladen", "Matchdetails", "Reaktion", "Keine Elo-Änderung", "{duration} · {correct} richtig · {wrong} falsch", "ZURÜCK", "WEITER",
        "Gesperrt · {name}{requirement}", "Stufe {level}", "Stufe 15 und ein perfekter Sieg", "100 Anmeldungen",
        "Gewinne 10 Matches", "Gewinne und finde alle 50 Zahlen in 30 Sekunden", "30 Tage in Folge", "50 Anmeldungen",
        "Zehn Siege", "Gewinne 10 Matches", "Perfektes Match", "Gewinne ohne falsches Antippen",
        "Blitzschnell", "Finde alle 50 Zahlen innerhalb von 30 Sekunden", "Beständiger Start", "Melde dich 7 Tage in Folge an",
    ),
    AppLanguage.INDONESIAN to collectionMatchDetailsCopy(
        "Memuat pertandingan", "Detail pertandingan", "Reaksi", "Elo tidak berubah", "{duration} · {correct} benar · {wrong} salah", "SEBELUMNYA", "BERIKUTNYA",
        "Terkunci · {name}{requirement}", "Tingkat {level}", "Tingkat 15 dan satu kemenangan sempurna", "100 kali absen",
        "Menangi 10 pertandingan", "Menang dan temukan semua 50 angka dalam 30 detik", "Beruntun 30 hari", "50 kali absen",
        "Sepuluh kemenangan", "Menangi 10 pertandingan", "Pertandingan sempurna", "Menang tanpa salah ketuk",
        "Secepat kilat", "Temukan semua 50 angka dalam 30 detik", "Awal yang mantap", "Absen selama 7 hari berturut-turut",
    ),
    AppLanguage.THAI to collectionMatchDetailsCopy(
        "กำลังโหลดการแข่งขัน", "รายละเอียดการแข่งขัน", "การตอบสนอง", "Elo ไม่เปลี่ยน", "{duration} · ถูก {correct} · ผิด {wrong}", "ก่อนหน้า", "ถัดไป",
        "ล็อกอยู่ · {name}{requirement}", "เลเวล {level}", "เลเวล 15 และชนะโดยไม่กดผิด", "เช็กอิน 100 ครั้ง",
        "ชนะ 10 นัด", "ชนะและหาตัวเลขครบ 50 ตัวภายใน 30 วินาที", "เช็กอินต่อเนื่อง 30 วัน", "เช็กอิน 50 ครั้ง",
        "ชนะสิบครั้ง", "ชนะ 10 นัด", "เกมสมบูรณ์แบบ", "ชนะโดยไม่กดผิด",
        "ความเร็วสายฟ้า", "หาตัวเลขครบ 50 ตัวภายใน 30 วินาที", "เริ่มต้นมั่นคง", "เช็กอินต่อเนื่อง 7 วัน",
    ),
    AppLanguage.RUSSIAN to collectionMatchDetailsCopy(
        "Загрузка матча", "Подробности матча", "Реакция", "Без изменения Elo", "{duration} · верно {correct} · ошибок {wrong}", "НАЗАД", "ДАЛЕЕ",
        "Заблокировано · {name}{requirement}", "Уровень {level}", "Уровень 15 и одна идеальная победа", "100 отметок",
        "Победите в 10 матчах", "Победите и найдите все 50 чисел за 30 секунд", "Серия из 30 дней", "50 отметок",
        "Десять побед", "Победите в 10 матчах", "Идеальный матч", "Победите без ошибочного нажатия",
        "Молниеносная скорость", "Найдите все 50 чисел за 30 секунд", "Уверенный старт", "Отмечайтесь 7 дней подряд",
    ),
)
