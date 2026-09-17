package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val clanCreationLogoKeys = listOf(
    TextKey.SearchClanPlaceholder, TextKey.CreateAction,
    TextKey.ClanSummary, TextKey.PendingApproval,
    TextKey.CreateClanTitle, TextKey.CreateClanDescription,
    TextKey.ClanLogo, TextKey.RemoveClanMemberAction,
    TextKey.ClanLogoShield, TextKey.ClanLogoSwords,
    TextKey.ClanLogoFlag, TextKey.ClanLogoDragon,
    TextKey.ClanLogoWolf, TextKey.ClanLogoEagle,
    TextKey.ClanLogoCrown, TextKey.ClanLogoNamed,
    TextKey.Individual, TextKey.YouSuffix,
)

private fun clanCreationLogoCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == clanCreationLogoKeys.size) {
        "Expected ${clanCreationLogoKeys.size} clan creation translations, received ${values.size}."
    }
    return clanCreationLogoKeys.zip(values).toMap()
}

internal val clanCreationLogoTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to clanCreationLogoCopy(
        "输入公会名称…", "创建", "{members} 名成员 · {trophies} 座奖杯", "等待审批",
        "创建公会", "选择一个简短易记的名称，方便队友找到你。", "公会徽标", "移除成员",
        "盾牌", "双剑", "旗帜", "龙", "狼", "雄鹰", "皇冠", "徽标 {logo}", "个人", " · 你",
    ),
    AppLanguage.JAPANESE to clanCreationLogoCopy(
        "クラン名を入力…", "作成", "メンバー{members}人 · トロフィー{trophies}個", "承認待ち",
        "クランを作成", "仲間が見つけやすい、短く覚えやすい名前を選びましょう。", "クランロゴ", "メンバーを除外",
        "盾", "交差する剣", "旗", "ドラゴン", "オオカミ", "ワシ", "王冠", "ロゴ：{logo}", "個人", " · あなた",
    ),
    AppLanguage.KOREAN to clanCreationLogoCopy(
        "클랜 이름 입력…", "만들기", "멤버 {members}명 · 트로피 {trophies}개", "승인 대기 중",
        "클랜 만들기", "팀원이 쉽게 찾도록 짧고 기억하기 쉬운 이름을 선택하세요.", "클랜 로고", "멤버 내보내기",
        "방패", "교차한 검", "깃발", "용", "늑대", "독수리", "왕관", "로고: {logo}", "개인", " · 나",
    ),
    AppLanguage.SPANISH to clanCreationLogoCopy(
        "Introduce el nombre del clan…", "CREAR", "{members} miembros · {trophies} trofeos", "Pendiente de aprobación",
        "Crear clan", "Elige un nombre corto y fácil de recordar para que tus compañeros te encuentren.",
        "Emblema del clan", "EXPULSAR MIEMBRO", "Escudo", "Espadas cruzadas", "Bandera", "Dragón",
        "Lobo", "Águila", "Corona", "Emblema {logo}", "Individual", " · Tú",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to clanCreationLogoCopy(
        "Digite o nome do clã…", "CRIAR", "{members} membros · {trophies} troféus", "Aguardando aprovação",
        "Criar clã", "Escolha um nome curto e fácil de lembrar para que seus colegas encontrem você.",
        "Emblema do clã", "REMOVER MEMBRO", "Escudo", "Espadas cruzadas", "Bandeira", "Dragão",
        "Lobo", "Águia", "Coroa", "Emblema {logo}", "Individual", " · Você",
    ),
    AppLanguage.FRENCH to clanCreationLogoCopy(
        "Saisissez le nom du clan…", "CRÉER", "{members} membres · {trophies} trophées", "En attente d'approbation",
        "Créer un clan", "Choisissez un nom court et mémorable pour que vos coéquipiers vous trouvent.",
        "Emblème du clan", "RETIRER LE MEMBRE", "Bouclier", "Épées croisées", "Drapeau", "Dragon héraldique",
        "Loup", "Aigle", "Couronne", "Emblème {logo}", "Individuel", " · Vous",
    ),
    AppLanguage.GERMAN to clanCreationLogoCopy(
        "Clanname eingeben…", "ERSTELLEN", "{members} Mitglieder · {trophies} Trophäen", "Genehmigung ausstehend",
        "Clan erstellen", "Wähle einen kurzen, einprägsamen Namen, damit Mitspieler dich finden.",
        "Clanwappen", "MITGLIED ENTFERNEN", "Schild", "Gekreuzte Schwerter", "Flagge", "Drache",
        "Wolfskopf", "Adler", "Krone", "Wappen {logo}", "Einzelspieler", " · Du",
    ),
    AppLanguage.INDONESIAN to clanCreationLogoCopy(
        "Masukkan nama klan…", "BUAT", "{members} anggota · {trophies} trofi", "Menunggu persetujuan",
        "Buat klan", "Pilih nama yang singkat dan mudah diingat agar rekan satu tim dapat menemukanmu.",
        "Lambang klan", "KELUARKAN ANGGOTA", "Perisai", "Pedang bersilang", "Bendera", "Naga",
        "Serigala", "Elang", "Mahkota", "Lambang {logo}", "Individu", " · Kamu",
    ),
    AppLanguage.THAI to clanCreationLogoCopy(
        "กรอกชื่อแคลน…", "สร้าง", "สมาชิก {members} คน · ถ้วยรางวัล {trophies} ใบ", "รออนุมัติ",
        "สร้างแคลน", "เลือกชื่อสั้นและจำง่ายเพื่อให้เพื่อนร่วมทีมค้นหาคุณได้", "ตราสัญลักษณ์แคลน", "นำสมาชิกออก",
        "โล่", "ดาบไขว้", "ธง", "มังกร", "หมาป่า", "อินทรี", "มงกุฎ", "ตรา {logo}", "บุคคล", " · คุณ",
    ),
    AppLanguage.RUSSIAN to clanCreationLogoCopy(
        "Введите название клана…", "СОЗДАТЬ", "{members} участников · {trophies} трофеев", "Ожидает одобрения",
        "Создать клан", "Выберите короткое и запоминающееся название, чтобы товарищи могли вас найти.",
        "Эмблема клана", "ИСКЛЮЧИТЬ УЧАСТНИКА", "Щит", "Скрещённые мечи", "Флаг", "Дракон",
        "Волк", "Орёл", "Корона", "Эмблема {logo}", "Личный рейтинг", " · Вы",
    ),
)

/** VI/EN are selectable; legacy catalogs keep a safe English fallback. */
internal val clanCreationCostTexts: Map<AppLanguage, Map<TextKey, String>> =
    AppLanguage.entries.associateWith { language ->
        mapOf(
            TextKey.ServerClanCreationInsufficientFunds to when (language) {
                AppLanguage.VIETNAMESE -> "Cần 2.000 Vàng và 20 Gem để tạo bang."
                else -> "You need 2,000 Gold and 20 Gems to create a clan."
            }
        )
    }
