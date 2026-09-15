package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val checkInKeys = listOf(
    TextKey.CheckIn, TextKey.StreakDays, TextKey.CheckInCalendarDescription,
    TextKey.PreviousMonth, TextKey.NextMonth, TextKey.MonthYear,
    TextKey.CheckedIn, TextKey.CheckInMilestones, TextKey.CheckInSummary,
    TextKey.ConsecutiveDays, TextKey.TotalCheckIns,
    TextKey.SteadyStartAchievement, TextKey.DiligentTitle,
    TextKey.PersistentFrame,
)

private fun checkInCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == checkInKeys.size) {
        "Expected ${checkInKeys.size} check-in translations, received ${values.size}."
    }
    return checkInKeys.zip(values).toMap()
}

internal val checkInTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to checkInCopy(
        "每日签到", "连续签到 {count} 天", "签到日历 · 最多显示最近 12 个月",
        "上个月", "下个月", "{year}年{month}月", "已签到", "签到里程碑",
        "最长连续签到 {best} 天 · 累计签到 {total} 次", "连续 {count} 天",
        "累计签到 {count} 次", "成就：稳步起航", "称号：勤勉", "坚韧头像框",
    ),
    AppLanguage.JAPANESE to checkInCopy(
        "毎日のログイン", "{count}日連続", "ログインカレンダー · 過去12か月まで表示",
        "前の月", "次の月", "{year}年{month}月", "ログイン済み", "ログインの節目",
        "最長連続 {best}日 · 累計 {total}回", "{count}日連続",
        "累計ログイン {count}回", "実績：着実なスタート", "称号：努力家", "不屈のフレーム",
    ),
    AppLanguage.KOREAN to checkInCopy(
        "일일 출석", "{count}일 연속 출석", "출석 달력 · 최근 12개월까지 표시",
        "이전 달", "다음 달", "{year}년 {month}월", "출석 완료", "출석 이정표",
        "최고 연속 {best}일 · 총 {total}회 출석", "{count}일 연속",
        "총 {count}회 출석", "업적: 꾸준한 시작", "칭호: 성실한 사람", "끈기의 프레임",
    ),
    AppLanguage.SPANISH to checkInCopy(
        "Registro diario", "Racha de {count} días", "Calendario de registros · Hasta los últimos 12 meses",
        "Mes anterior", "Mes siguiente", "Mes {month}/{year}", "Registrado", "Hitos de registro",
        "Mejor racha: {best} días · {total} registros en total", "{count} días consecutivos",
        "{count} registros en total", "Logro: Buen comienzo", "Título: Diligente", "Marco perseverante",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to checkInCopy(
        "Presença diária", "Sequência de {count} dias", "Calendário de presença · Até os últimos 12 meses",
        "Mês anterior", "Próximo mês", "Mês {month}/{year}", "Presença registrada", "Marcos de presença",
        "Maior sequência: {best} dias · {total} presenças no total", "{count} dias seguidos",
        "{count} presenças no total", "Conquista: Começo firme", "Título: Dedicado", "Moldura perseverante",
    ),
    AppLanguage.FRENCH to checkInCopy(
        "Présence quotidienne", "Série de {count} jours", "Calendrier de présence · Jusqu'aux 12 derniers mois",
        "Mois précédent", "Mois suivant", "Mois {month}/{year}", "Présence enregistrée", "Paliers de présence",
        "Meilleure série : {best} jours · {total} présences au total", "{count} jours consécutifs",
        "{count} présences au total", "Succès : Départ régulier", "Titre : Assidu", "Cadre persévérant",
    ),
    AppLanguage.GERMAN to checkInCopy(
        "Tägliche Anmeldung", "{count} Tage in Folge", "Anmeldekalender · Bis zu 12 Monate rückwirkend",
        "Vorheriger Monat", "Nächster Monat", "Monat {month}/{year}", "Angemeldet", "Anmeldemeilensteine",
        "Beste Serie: {best} Tage · insgesamt {total} Anmeldungen", "{count} aufeinanderfolgende Tage",
        "Insgesamt {count} Anmeldungen", "Erfolg: Beständiger Start", "Titel: Fleißig", "Ausdauernder Rahmen",
    ),
    AppLanguage.INDONESIAN to checkInCopy(
        "Absensi harian", "Beruntun {count} hari", "Kalender absensi · Hingga 12 bulan terakhir",
        "Bulan sebelumnya", "Bulan berikutnya", "Bulan {month}/{year}", "Sudah absen", "Tonggak absensi",
        "Rekor beruntun {best} hari · total {total} kali absen", "{count} hari berturut-turut",
        "Total {count} kali absen", "Pencapaian: Awal yang Mantap", "Gelar: Rajin", "Bingkai Gigih",
    ),
    AppLanguage.THAI to checkInCopy(
        "เช็กอินรายวัน", "ต่อเนื่อง {count} วัน", "ปฏิทินเช็กอิน · ย้อนหลังสูงสุด 12 เดือน",
        "เดือนก่อน", "เดือนถัดไป", "เดือน {month}/{year}", "เช็กอินแล้ว", "เป้าหมายการเช็กอิน",
        "ต่อเนื่องสูงสุด {best} วัน · เช็กอินทั้งหมด {total} ครั้ง", "ต่อเนื่อง {count} วัน",
        "เช็กอินทั้งหมด {count} ครั้ง", "ความสำเร็จ: เริ่มต้นมั่นคง", "ฉายา: ผู้ขยัน", "กรอบแห่งความพากเพียร",
    ),
    AppLanguage.RUSSIAN to checkInCopy(
        "Ежедневная отметка", "Серия: {count} дн.", "Календарь отметок · Не более 12 последних месяцев",
        "Предыдущий месяц", "Следующий месяц", "Месяц {month}/{year}", "Отмечено", "Этапы отметок",
        "Лучшая серия: {best} дн. · всего отметок: {total}", "{count} дней подряд",
        "Всего отметок: {count}", "Достижение: Уверенный старт", "Титул: Старательный", "Рамка упорства",
    ),
)
