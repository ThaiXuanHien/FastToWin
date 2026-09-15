package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val rankWalletSummaryKeys = listOf(
    TextKey.RankBronze, TextKey.RankSilver, TextKey.RankGold,
    TextKey.RankPlatinum, TextKey.RankDiamond,
    TextKey.RankMaster, TextKey.RankChallenger,
    TextKey.ModeWinRate, TextKey.ModeMatchSummary, TextKey.ScoreAverage,
    TextKey.ClanMissionSource, TextKey.CosmeticPurchaseSource,
    TextKey.TournamentEntrySource, TextKey.TournamentPrizeSource,
)

private fun rankWalletSummaryCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == rankWalletSummaryKeys.size) {
        "Expected ${rankWalletSummaryKeys.size} rank and wallet translations, received ${values.size}."
    }
    return rankWalletSummaryKeys.zip(values).toMap()
}

internal val rankWalletSummaryTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to rankWalletSummaryCopy(
        "青铜", "白银", "黄金", "铂金", "钻石", "大师", "王者",
        "{rate}% 胜率", "{matches} 场 • {wins} 胜 • {losses} 负 • {draws} 平",
        "最高 {high} • 平均 {average}", "公会任务", "外观购买", "赛事报名费", "赛事冠军奖励",
    ),
    AppLanguage.JAPANESE to rankWalletSummaryCopy(
        "ブロンズ", "シルバー", "ゴールド", "プラチナ", "ダイヤモンド", "マスター", "チャレンジャー",
        "勝率 {rate}%", "{matches}試合 • {wins}勝 • {losses}敗 • {draws}分",
        "最高 {high} • 平均 {average}", "クランミッション", "外観アイテム購入", "大会参加費", "大会優勝報酬",
    ),
    AppLanguage.KOREAN to rankWalletSummaryCopy(
        "브론즈", "실버", "골드", "플래티넘", "다이아몬드", "마스터", "챌린저",
        "승률 {rate}%", "{matches}경기 • {wins}승 • {losses}패 • {draws}무",
        "최고 {high} • 평균 {average}", "클랜 임무", "외형 아이템 구매", "대회 참가비", "대회 우승 보상",
    ),
    AppLanguage.SPANISH to rankWalletSummaryCopy(
        "Bronce", "Plata", "Oro", "Platino", "Diamante", "Maestro", "Desafiante",
        "{rate}% de victorias", "{matches} partidas • {wins} victorias • {losses} derrotas • {draws} empates",
        "Máxima {high} • Media {average}", "Misión del clan", "Compra de aspecto",
        "Cuota de inscripción al torneo", "Premio de campeón del torneo",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to rankWalletSummaryCopy(
        "Ranque Bronze", "Prata", "Ouro", "Platina", "Diamante", "Mestre", "Desafiante",
        "{rate}% de vitórias", "{matches} partidas • {wins} vitórias • {losses} derrotas • {draws} empates",
        "Máxima {high} • Média {average}", "Missão do clã", "Compra de visual",
        "Taxa de entrada no torneio", "Prêmio de campeão do torneio",
    ),
    AppLanguage.FRENCH to rankWalletSummaryCopy(
        "Palier Bronze", "Argent", "Or", "Platine", "Diamant", "Maître", "Prétendant",
        "{rate} % de victoires", "{matches} parties • {wins} victoires • {losses} défaites • {draws} égalités",
        "Maximum {high} • Moyenne {average}", "Mission de clan", "Achat d'apparence",
        "Frais d'inscription au tournoi", "Récompense du champion du tournoi",
    ),
    AppLanguage.GERMAN to rankWalletSummaryCopy(
        "Bronzerang", "Silberrang", "Goldrang", "Platinrang", "Diamantrang", "Meisterrang", "Herausforderer",
        "{rate} % Siege", "{matches} Matches • {wins} Siege • {losses} Niederlagen • {draws} Unentschieden",
        "Höchstwert {high} • Durchschnitt {average}", "Clanmission", "Designkauf",
        "Turnier-Startgebühr", "Turnierpreis für den Sieger",
    ),
    AppLanguage.INDONESIAN to rankWalletSummaryCopy(
        "Perunggu", "Perak", "Emas", "Peringkat Platinum", "Berlian", "Peringkat Ahli", "Penantang",
        "{rate}% kemenangan", "{matches} pertandingan • {wins} menang • {losses} kalah • {draws} seri",
        "Tertinggi {high} • Rata-rata {average}", "Misi klan", "Pembelian tampilan",
        "Biaya masuk turnamen", "Hadiah juara turnamen",
    ),
    AppLanguage.THAI to rankWalletSummaryCopy(
        "บรอนซ์", "ซิลเวอร์", "โกลด์", "แพลทินัม", "ไดมอนด์", "มาสเตอร์", "ผู้ท้าชิง",
        "อัตราชนะ {rate}%", "{matches} นัด • ชนะ {wins} • แพ้ {losses} • เสมอ {draws}",
        "สูงสุด {high} • เฉลี่ย {average}", "ภารกิจแคลน", "ซื้อรูปลักษณ์",
        "ค่าลงทะเบียนการแข่งขัน", "รางวัลแชมป์การแข่งขัน",
    ),
    AppLanguage.RUSSIAN to rankWalletSummaryCopy(
        "Бронза", "Серебро", "Золото", "Платина", "Алмаз", "Мастер", "Претендент",
        "Побед: {rate}%", "{matches} матчей • {wins} побед • {losses} поражений • {draws} ничьих",
        "Максимум {high} • Среднее {average}", "Задание клана", "Покупка облика",
        "Взнос за участие в турнире", "Награда чемпиону турнира",
    ),
)
