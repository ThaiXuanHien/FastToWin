package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val missionWalletKeys = listOf(
    TextKey.DailyMissions, TextKey.WeeklyMissions, TextKey.NoMissions,
    TextKey.MissionProgress, TextKey.Claimed, TextKey.Claiming,
    TextKey.ClaimReward, TextKey.MissionCompleted, TextKey.DifficultyEasy,
    TextKey.DifficultyNormal, TextKey.DifficultyHard,
    TextKey.NoWalletTransactions, TextKey.NoWalletTransactionsForFilter,
    TextKey.DailyCheckInSource, TextKey.MissionRewardSource,
    TextKey.MatchRewardSource, TextKey.ClanRewardSource,
    TextKey.ShopPurchaseSource, TextKey.WalletOtherSource,
    TextKey.WalletBalanceDescription, TextKey.RewardGoldDescription,
    TextKey.RewardXpDescription, TextKey.RewardGemsDescription,
    TextKey.WalletReceivedGold, TextKey.WalletUsedGold,
    TextKey.WalletReceivedXp, TextKey.WalletUsedXp,
    TextKey.WalletReceivedGems, TextKey.WalletUsedGems,
    TextKey.NoWalletActivity, TextKey.WalletRewardsAppear,
    TextKey.WalletNoActivityInFilter,
)

private fun missionWalletCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == missionWalletKeys.size) {
        "Expected ${missionWalletKeys.size} mission and wallet translations, received ${values.size}."
    }
    return missionWalletKeys.zip(values).toMap()
}

internal val missionWalletTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to missionWalletCopy(
        "每日", "每周", "暂无新任务", "已完成 {completed}/{total}", "已领取", "领取中",
        "领取奖励", "已完成", "简单", "普通", "困难",
        "暂无资源交易记录", "没有符合筛选条件的交易", "每日签到", "任务奖励",
        "对局奖励", "公会奖励", "商店购买", "其他活动", "{gold} 金币，{gems} 宝石",
        "{count} 金币", "{count} 经验值", "{count} 宝石", "获得 {count} 金币",
        "消耗 {count} 金币", "获得 {count} 经验值", "消耗 {count} 经验值",
        "获得 {count} 宝石", "消耗 {count} 宝石", "暂无交易",
        "奖励和购买的物品会显示在这里。", "此分类暂无交易。",
    ),
    AppLanguage.JAPANESE to missionWalletCopy(
        "毎日", "毎週", "新しいミッションはありません", "{completed}/{total} 件完了", "受け取り済み", "受け取り中",
        "報酬を受け取る", "完了", "かんたん", "ふつう", "むずかしい",
        "資産の取引はまだありません", "この条件に合う取引はありません", "毎日のログイン", "ミッション報酬",
        "対戦報酬", "ギルド報酬", "ショップで購入", "その他の活動", "ゴールド {gold}、ジェム {gems}",
        "ゴールド {count}", "経験値 {count}", "ジェム {count}", "ゴールド {count} を獲得",
        "ゴールド {count} を使用", "経験値 {count} を獲得", "経験値 {count} を使用",
        "ジェム {count} を獲得", "ジェム {count} を使用", "取引はありません",
        "報酬と購入したアイテムはここに表示されます。", "このカテゴリに取引はありません。",
    ),
    AppLanguage.KOREAN to missionWalletCopy(
        "일일", "주간", "새로운 임무가 없습니다", "{completed}/{total}개 완료", "수령 완료", "수령 중",
        "보상 받기", "완료", "쉬움", "보통", "어려움",
        "아직 재화 거래 내역이 없습니다", "이 필터에 맞는 거래가 없습니다", "일일 출석", "임무 보상",
        "경기 보상", "길드 보상", "상점 구매", "기타 활동", "골드 {gold}, 젬 {gems}",
        "골드 {count}", "경험치 {count}", "젬 {count}", "골드 {count} 획득",
        "골드 {count} 사용", "경험치 {count} 획득", "경험치 {count} 사용",
        "젬 {count} 획득", "젬 {count} 사용", "거래 내역 없음",
        "보상과 구매한 아이템이 여기에 표시됩니다.", "이 분류에는 거래가 없습니다.",
    ),
    AppLanguage.SPANISH to missionWalletCopy(
        "Diarias", "Semanales", "No hay misiones nuevas.", "{completed}/{total} completadas", "Reclamado", "RECLAMANDO",
        "RECLAMAR RECOMPENSA", "Completada", "Fácil", "Intermedia", "Difícil",
        "Aún no hay transacciones de recursos.", "No hay transacciones que coincidan con este filtro.", "Registro diario", "Recompensa de misión",
        "Recompensa de partida", "Recompensa del clan", "Compra en la tienda", "Otra actividad", "{gold} de oro, {gems} gemas",
        "{count} de oro", "{count} de XP", "{count} gemas", "recibió {count} de oro",
        "gastó {count} de oro", "recibió {count} de XP", "gastó {count} de XP",
        "recibió {count} gemas", "gastó {count} gemas", "Sin transacciones",
        "Aquí aparecerán las recompensas y los artículos comprados.", "No hay transacciones en esta categoría.",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to missionWalletCopy(
        "Diárias", "Semanais", "Não há novas missões.", "{completed}/{total} concluídas", "Recebido", "RECEBENDO",
        "RECEBER RECOMPENSA", "Concluída", "Fácil", "Média", "Difícil",
        "Ainda não há transações de recursos.", "Nenhuma transação corresponde a este filtro.", "Presença diária", "Recompensa de missão",
        "Recompensa de partida", "Recompensa do clã", "Compra na loja", "Outra atividade", "{gold} de ouro, {gems} gemas",
        "{count} de ouro", "{count} de XP", "{count} gemas", "recebeu {count} de ouro",
        "gastou {count} de ouro", "recebeu {count} de XP", "gastou {count} de XP",
        "recebeu {count} gemas", "gastou {count} gemas", "Sem transações",
        "Recompensas e itens comprados aparecerão aqui.", "Não há transações nesta categoria.",
    ),
    AppLanguage.FRENCH to missionWalletCopy(
        "Quotidiennes", "Hebdomadaires", "Aucune nouvelle mission.", "{completed}/{total} terminées", "Récupéré", "RÉCUPÉRATION",
        "RÉCUPÉRER LA RÉCOMPENSE", "Terminée", "Facile", "Moyen", "Difficile",
        "Aucune transaction de ressources pour le moment.", "Aucune transaction ne correspond à ce filtre.", "Présence quotidienne", "Récompense de mission",
        "Récompense de partie", "Récompense de clan", "Achat en boutique", "Autre activité", "{gold} pièces d'or, {gems} gemmes",
        "{count} pièces d'or", "{count} points d'XP", "{count} gemmes", "a reçu {count} pièces d'or",
        "a dépensé {count} pièces d'or", "a reçu {count} points d'XP", "a dépensé {count} points d'XP",
        "a reçu {count} gemmes", "a dépensé {count} gemmes", "Aucune transaction",
        "Les récompenses et les objets achetés apparaîtront ici.", "Aucune transaction dans cette catégorie.",
    ),
    AppLanguage.GERMAN to missionWalletCopy(
        "Täglich", "Wöchentlich", "Keine neuen Missionen.", "{completed}/{total} abgeschlossen", "Abgeholt", "WIRD ABGEHOLT",
        "BELOHNUNG ABHOLEN", "Abgeschlossen", "Leicht", "Mittel", "Schwer",
        "Noch keine Ressourcentransaktionen.", "Keine Transaktionen entsprechen diesem Filter.", "Tägliche Anmeldung", "Missionsbelohnung",
        "Matchbelohnung", "Gildenbelohnung", "Kauf im Shop", "Andere Aktivität", "{gold} Gold, {gems} Juwelen",
        "{count} Gold", "{count} Erfahrungspunkte", "{count} Juwelen", "{count} Gold erhalten",
        "{count} Gold ausgegeben", "{count} Erfahrungspunkte erhalten", "{count} Erfahrungspunkte ausgegeben",
        "{count} Juwelen erhalten", "{count} Juwelen ausgegeben", "Keine Transaktionen",
        "Belohnungen und gekaufte Gegenstände erscheinen hier.", "Keine Transaktionen in dieser Kategorie.",
    ),
    AppLanguage.INDONESIAN to missionWalletCopy(
        "Harian", "Mingguan", "Belum ada misi baru.", "{completed}/{total} selesai", "Sudah diambil", "MENGAMBIL",
        "AMBIL HADIAH", "Selesai", "Mudah", "Sedang", "Sulit",
        "Belum ada transaksi sumber daya.", "Tidak ada transaksi yang cocok dengan filter ini.", "Absensi harian", "Hadiah misi",
        "Hadiah pertandingan", "Hadiah klan", "Pembelian di toko", "Aktivitas lain", "{gold} emas, {gems} gem",
        "{count} emas", "{count} XP", "{count} gem", "menerima {count} emas",
        "menghabiskan {count} emas", "menerima {count} XP", "menghabiskan {count} XP",
        "menerima {count} gem", "menghabiskan {count} gem", "Belum ada transaksi",
        "Hadiah dan barang yang dibeli akan muncul di sini.", "Tidak ada transaksi dalam kategori ini.",
    ),
    AppLanguage.THAI to missionWalletCopy(
        "รายวัน", "รายสัปดาห์", "ยังไม่มีภารกิจใหม่", "สำเร็จแล้ว {completed}/{total}", "รับแล้ว", "กำลังรับ",
        "รับรางวัล", "สำเร็จ", "ง่าย", "ปานกลาง", "ยาก",
        "ยังไม่มีรายการทรัพยากร", "ไม่มีรายการที่ตรงกับตัวกรองนี้", "เช็กอินรายวัน", "รางวัลภารกิจ",
        "รางวัลการแข่งขัน", "รางวัลแคลน", "ซื้อจากร้านค้า", "กิจกรรมอื่น", "ทอง {gold}, เจม {gems}",
        "ทอง {count}", "ค่าประสบการณ์ {count}", "เจม {count}", "ได้รับทอง {count}",
        "ใช้ทอง {count}", "ได้รับค่าประสบการณ์ {count}", "ใช้ค่าประสบการณ์ {count}",
        "ได้รับเจม {count}", "ใช้เจม {count}", "ยังไม่มีรายการ",
        "รางวัลและไอเทมที่ซื้อจะแสดงที่นี่", "ไม่มีรายการในหมวดนี้",
    ),
    AppLanguage.RUSSIAN to missionWalletCopy(
        "Ежедневные", "Еженедельные", "Новых заданий нет.", "Выполнено {completed}/{total}", "Получено", "ПОЛУЧЕНИЕ",
        "ЗАБРАТЬ НАГРАДУ", "Выполнено", "Легко", "Средне", "Сложно",
        "Операций с ресурсами пока нет.", "Нет операций, подходящих под этот фильтр.", "Ежедневная отметка", "Награда за задание",
        "Награда за матч", "Награда клана", "Покупка в магазине", "Другое действие", "{gold} золота, {gems} самоцветов",
        "{count} золота", "{count} опыта", "{count} самоцветов", "получено {count} золота",
        "потрачено {count} золота", "получено {count} опыта", "потрачено {count} опыта",
        "получено {count} самоцветов", "потрачено {count} самоцветов", "Операций нет",
        "Здесь появятся награды и купленные предметы.", "В этой категории операций нет.",
    ),
)
