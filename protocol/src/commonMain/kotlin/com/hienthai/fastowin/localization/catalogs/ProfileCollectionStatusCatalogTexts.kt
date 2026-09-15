package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val profileCollectionStatusKeys = listOf(
    TextKey.Copied, TextKey.Rookie, TextKey.TitleValue,
    TextKey.PlayerCodeValue, TextKey.DifficultyElite,
    TextKey.EmptyCollection, TextKey.Titles, TextKey.Equipping,
    TextKey.Equipped, TextKey.TapToEquip, TextKey.Unlocked,
    TextKey.UnlockRequirement, TextKey.Locked,
    TextKey.NoCompletedMatches, TextKey.NoMatchesForFilter,
    TextKey.UpdatingPassword, TextKey.DeletingAccount,
    TextKey.LoginDevicesTitle, TextKey.RevokeDeviceTitle,
    TextKey.RevokeCurrentDeviceTitle, TextKey.UnknownDevice,
    TextKey.JustNow, TextKey.MinutesAgo, TextKey.HoursAgo,
    TextKey.DaysAgo, TextKey.SessionExpiresInDays, TextKey.ActivityTime,
)

private fun profileCollectionStatusCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == profileCollectionStatusKeys.size) {
        "Expected ${profileCollectionStatusKeys.size} profile status translations, received ${values.size}."
    }
    return profileCollectionStatusKeys.zip(values).toMap()
}

internal val profileCollectionStatusTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to profileCollectionStatusCopy(
        "已复制", "新手", "称号：{title}", "代码：{code}", "精英", "你的收藏为空。", "称号",
        "正在装备…", "已装备", "点击装备", "已解锁", "解锁条件：{requirement}", "未解锁",
        "暂无已完成的比赛。", "没有符合筛选条件的比赛。", "正在更新…", "正在删除…", "登录设备",
        "退出该设备？", "退出当前设备？", "未知设备", "刚刚", "{count} 分钟前", "{count} 小时前",
        "{count} 天前", "会话将在约 {count} 天后到期", "活跃于 {time}",
    ),
    AppLanguage.JAPANESE to profileCollectionStatusCopy(
        "コピーしました", "ルーキー", "称号：{title}", "コード：{code}", "エリート", "コレクションは空です。", "称号",
        "装備中…", "装備済み", "タップして装備", "アンロック済み", "解放条件：{requirement}", "ロック中",
        "完了した試合はまだありません。", "条件に一致する試合はありません。", "更新中…", "削除中…", "ログイン中の端末",
        "この端末からログアウトしますか？", "現在の端末からログアウトしますか？", "不明な端末", "たった今", "{count}分前", "{count}時間前",
        "{count}日前", "セッションは約{count}日後に期限切れ", "{time}にアクティブ",
    ),
    AppLanguage.KOREAN to profileCollectionStatusCopy(
        "복사됨", "초보자", "칭호: {title}", "코드: {code}", "정예", "컬렉션이 비어 있습니다.", "칭호",
        "장착 중…", "장착됨", "눌러서 장착", "잠금 해제됨", "해제 조건: {requirement}", "잠김",
        "완료한 경기가 없습니다.", "이 필터와 일치하는 경기가 없습니다.", "업데이트 중…", "계정 삭제 중…", "로그인된 기기",
        "기기에서 로그아웃할까요?", "이 기기에서 로그아웃할까요?", "알 수 없는 기기", "방금 전", "{count}분 전", "{count}시간 전",
        "{count}일 전", "세션이 약 {count}일 후 만료됩니다", "{time}에 활동",
    ),
    AppLanguage.SPANISH to profileCollectionStatusCopy(
        "Copiado", "Novato", "Título: {title}", "Código: {code}", "Élite", "Tu colección está vacía.", "Títulos",
        "Equipando…", "EQUIPADO", "Toca para equipar", "Desbloqueado", "Desbloquear: {requirement}", "Bloqueado",
        "Aún no hay partidas completadas.", "Ninguna partida coincide con este filtro.", "ACTUALIZANDO…", "ELIMINANDO…", "DISPOSITIVOS CON SESIÓN INICIADA",
        "¿CERRAR SESIÓN EN EL DISPOSITIVO?", "¿CERRAR SESIÓN EN ESTE DISPOSITIVO?", "Dispositivo desconocido", "ahora mismo", "hace {count} min", "hace {count} h",
        "hace {count} días", "La sesión vence en unos {count} días", "Activo: {time}",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to profileCollectionStatusCopy(
        "Copiado", "Iniciante", "Título: {title}", "Código: {code}", "Nível avançado", "Sua coleção está vazia.", "Títulos",
        "Equipando…", "EQUIPADO", "Toque para equipar", "Desbloqueado", "Desbloqueio: {requirement}", "Bloqueado",
        "Ainda não há partidas concluídas.", "Nenhuma partida corresponde a este filtro.", "ATUALIZANDO…", "EXCLUINDO…", "DISPOSITIVOS CONECTADOS",
        "SAIR DO DISPOSITIVO?", "SAIR DESTE DISPOSITIVO?", "Dispositivo desconhecido", "agora mesmo", "há {count} min", "há {count} h",
        "há {count} dias", "A sessão expira em cerca de {count} dias", "Ativo em {time}",
    ),
    AppLanguage.FRENCH to profileCollectionStatusCopy(
        "Copié", "Débutant", "Titre : {title}", "Code : {code}", "Élite", "Votre collection est vide.", "Titres",
        "Équipement…", "ÉQUIPÉ", "Toucher pour équiper", "Déverrouillé", "Déblocage : {requirement}", "Verrouillé",
        "Aucun match terminé pour le moment.", "Aucun match ne correspond à ce filtre.", "MISE À JOUR…", "SUPPRESSION…", "APPAREILS CONNECTÉS",
        "DÉCONNECTER L’APPAREIL ?", "SE DÉCONNECTER DE CET APPAREIL ?", "Appareil inconnu", "à l’instant", "il y a {count} min", "il y a {count} h",
        "il y a {count} jours", "La session expire dans environ {count} jours", "Actif : {time}",
    ),
    AppLanguage.GERMAN to profileCollectionStatusCopy(
        "Kopiert", "Anfänger", "Titel: {title}", "Spielercode: {code}", "Elite-Stufe", "Deine Sammlung ist leer.", "Titel",
        "Wird ausgerüstet…", "AUSGERÜSTET", "Zum Ausrüsten tippen", "Freigeschaltet", "Freischalten: {requirement}", "Gesperrt",
        "Noch keine abgeschlossenen Spiele.", "Keine Spiele entsprechen diesem Filter.", "AKTUALISIERUNG…", "KONTO WIRD GELÖSCHT…", "ANGEMELDETE GERÄTE",
        "GERÄT ABMELDEN?", "DIESES GERÄT ABMELDEN?", "Unbekanntes Gerät", "gerade eben", "vor {count} Min.", "vor {count} Std.",
        "vor {count} Tagen", "Sitzung läuft in etwa {count} Tagen ab", "Aktiv: {time}",
    ),
    AppLanguage.INDONESIAN to profileCollectionStatusCopy(
        "Disalin", "Pemula", "Gelar: {title}", "Kode: {code}", "Elit", "Koleksimu kosong.", "Gelar",
        "Memasang…", "TERPASANG", "Ketuk untuk memasang", "Terbuka", "Syarat buka: {requirement}", "Terkunci",
        "Belum ada pertandingan selesai.", "Tidak ada pertandingan yang sesuai filter ini.", "MEMPERBARUI…", "MENGHAPUS…", "PERANGKAT YANG MASUK",
        "KELUAR DARI PERANGKAT?", "KELUAR DARI PERANGKAT INI?", "Perangkat tidak dikenal", "baru saja", "{count} mnt lalu", "{count} jam lalu",
        "{count} hari lalu", "Sesi berakhir sekitar {count} hari lagi", "Aktif {time}",
    ),
    AppLanguage.THAI to profileCollectionStatusCopy(
        "คัดลอกแล้ว", "มือใหม่", "ฉายา: {title}", "รหัส: {code}", "ระดับยอดฝีมือ", "คอลเลกชันของคุณว่างเปล่า", "ฉายา",
        "กำลังสวมใส่…", "สวมใส่แล้ว", "แตะเพื่อสวมใส่", "ปลดล็อกแล้ว", "ปลดล็อก: {requirement}", "ล็อกอยู่",
        "ยังไม่มีการแข่งขันที่จบแล้ว", "ไม่มีการแข่งขันตรงกับตัวกรองนี้", "กำลังอัปเดต…", "กำลังลบบัญชี…", "อุปกรณ์ที่เข้าสู่ระบบ",
        "ออกจากระบบบนอุปกรณ์?", "ออกจากระบบบนอุปกรณ์นี้?", "อุปกรณ์ที่ไม่รู้จัก", "เมื่อสักครู่", "{count} นาทีที่แล้ว", "{count} ชั่วโมงที่แล้ว",
        "{count} วันที่แล้ว", "เซสชันจะหมดอายุในประมาณ {count} วัน", "ใช้งานเมื่อ {time}",
    ),
    AppLanguage.RUSSIAN to profileCollectionStatusCopy(
        "Скопировано", "Новичок", "Титул: {title}", "Код: {code}", "Элита", "Ваша коллекция пуста.", "Титулы",
        "Экипировка…", "ЭКИПИРОВАНО", "Нажмите, чтобы экипировать", "Открыто", "Условие: {requirement}", "Заблокировано",
        "Завершённых матчей пока нет.", "Нет матчей по этому фильтру.", "ОБНОВЛЕНИЕ…", "УДАЛЕНИЕ…", "УСТРОЙСТВА С АКТИВНЫМ ВХОДОМ",
        "ВЫЙТИ НА УСТРОЙСТВЕ?", "ВЫЙТИ НА ЭТОМ УСТРОЙСТВЕ?", "Неизвестное устройство", "только что", "{count} мин. назад", "{count} ч. назад",
        "{count} дн. назад", "Сеанс истекает примерно через {count} дн.", "Активность: {time}",
    ),
)
