package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val framePresenceKeys = listOf(
    TextKey.AvatarOfPlayer, TextKey.SeasonalFrame,
    TextKey.BronzeFrame, TextKey.SilverFrame, TextKey.GoldFrame,
    TextKey.PerfectFrame, TextKey.PersistentFrameName, TextKey.BasicFrame,
    TextKey.PresenceOffline, TextKey.PresenceOnline,
    TextKey.PresenceInRoom, TextKey.PresencePlaying,
)

private fun framePresenceCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == framePresenceKeys.size) {
        "Expected ${framePresenceKeys.size} frame and presence translations, received ${values.size}."
    }
    return framePresenceKeys.zip(values).toMap()
}

internal val framePresenceTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to framePresenceCopy(
        "{player} 的头像", "赛季头像框 • {tier}", "青铜头像框", "白银头像框", "黄金头像框",
        "完美头像框", "坚韧头像框", "基础头像框", "离线", "在线", "在房间中", "对局中",
    ),
    AppLanguage.JAPANESE to framePresenceCopy(
        "{player}のアバター", "シーズンフレーム • {tier}", "ブロンズフレーム", "シルバーフレーム",
        "ゴールドフレーム", "パーフェクトフレーム", "不屈のフレーム", "基本フレーム",
        "オフライン", "オンライン", "ルーム内", "対戦中",
    ),
    AppLanguage.KOREAN to framePresenceCopy(
        "{player}의 아바타", "시즌 프레임 • {tier}", "브론즈 프레임", "실버 프레임", "골드 프레임",
        "퍼펙트 프레임", "인내의 프레임", "기본 프레임", "오프라인", "온라인", "방에 있음", "경기 중",
    ),
    AppLanguage.SPANISH to framePresenceCopy(
        "Avatar de {player}", "Marco de temporada • {tier}", "Marco de bronce", "Marco de plata",
        "Marco de oro", "Marco perfecto", "Marco perseverante", "Marco básico",
        "Sin conexión", "En línea", "En una sala", "En una partida",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to framePresenceCopy(
        "Avatar de {player}", "Moldura da temporada • {tier}", "Moldura de bronze", "Moldura de prata",
        "Moldura de ouro", "Moldura perfeita", "Moldura persistente", "Moldura básica",
        "Desconectado", "Conectado", "Em uma sala", "Em uma partida",
    ),
    AppLanguage.FRENCH to framePresenceCopy(
        "Avatar de {player}", "Cadre de saison • {tier}", "Cadre de bronze", "Cadre d'argent",
        "Cadre d'or", "Cadre parfait", "Cadre persévérant", "Cadre de base",
        "Hors ligne", "En ligne", "Dans un salon", "En partie",
    ),
    AppLanguage.GERMAN to framePresenceCopy(
        "Avatar von {player}", "Saisonrahmen • {tier}", "Bronzerahmen", "Silberrahmen", "Goldrahmen",
        "Perfekter Rahmen", "Ausdauerrahmen", "Standardrahmen",
        "Nicht verbunden", "Im Netz", "In einem Raum", "Im Match",
    ),
    AppLanguage.INDONESIAN to framePresenceCopy(
        "Avatar {player}", "Bingkai musim • {tier}", "Bingkai perunggu", "Bingkai perak",
        "Bingkai emas", "Bingkai sempurna", "Bingkai gigih", "Bingkai dasar",
        "Luring", "Daring", "Di dalam ruang", "Sedang bertanding",
    ),
    AppLanguage.THAI to framePresenceCopy(
        "อวาตาร์ของ {player}", "กรอบประจำฤดูกาล • {tier}", "กรอบทองแดง", "กรอบเงิน", "กรอบทอง",
        "กรอบสมบูรณ์แบบ", "กรอบความมุ่งมั่น", "กรอบพื้นฐาน",
        "ออฟไลน์", "ออนไลน์", "อยู่ในห้อง", "กำลังแข่งขัน",
    ),
    AppLanguage.RUSSIAN to framePresenceCopy(
        "Аватар игрока {player}", "Сезонная рамка • {tier}", "Бронзовая рамка", "Серебряная рамка",
        "Золотая рамка", "Идеальная рамка", "Стойкая рамка", "Базовая рамка",
        "Не в сети", "В сети", "В комнате", "В матче",
    ),
)
