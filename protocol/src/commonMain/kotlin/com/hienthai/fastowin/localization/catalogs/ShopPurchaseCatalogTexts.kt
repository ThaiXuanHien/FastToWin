package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

private val shopPurchaseKeys = listOf(
    TextKey.GoldVault, TextKey.GemVaultDescription, TextKey.ChooseGemPackage,
    TextKey.GemPackageDescription, TextKey.LoginToBuyGems, TextKey.PriceUnavailable,
    TextKey.EarnGemsDescription, TextKey.GoldVaultDescription,
    TextKey.ExchangeGemsForGold, TextKey.GoldExchangeConfirmation,
    TextKey.NotEnoughGems, TextKey.GoldExchangeGranted,
    TextKey.GoldExchangeAlreadyGranted, TextKey.GoldExchangeFailed,
    TextKey.BillingPlayNotReady, TextKey.BillingPriceLoadFailed,
    TextKey.BillingCancelled, TextKey.BillingUnavailable,
    TextKey.BillingPending, TextKey.BillingGemsAdded,
    TextKey.BillingWebUnsupported,
)

private fun shopPurchaseCopy(vararg values: String): Map<TextKey, String> {
    require(values.size == shopPurchaseKeys.size) {
        "Expected ${shopPurchaseKeys.size} shop translations, received ${values.size}."
    }
    return shopPurchaseKeys.zip(values).toMap()
}

internal val shopPurchaseTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.SIMPLIFIED_CHINESE to shopPurchaseCopy(
        "兑换金币", "宝石可解锁稀有物品，购买记录会通过商店安全同步。", "选择宝石礼包",
        "宝石可解锁稀有物品。价格由你的商店账户提供。", "登录后购买宝石并在设备间同步。", "价格暂不可用",
        "签到、困难任务和特殊成就也能获得宝石。", "安全地将宝石兑换成金币。每笔兑换都会记录在资源历史中。",
        "兑换", "使用 {gems} 宝石兑换 {gold} 金币？", "宝石不足", "已获得 {gold} 金币。",
        "这笔兑换已经完成。", "无法兑换金币，请重试。", "Google Play 付款服务尚未就绪。",
        "无法从 Google Play 加载价格。", "已取消付款。", "此设备无法使用 Google Play 付款服务。",
        "Google Play 正在处理这笔付款。", "宝石已添加到你的账户。", "网页版不支持购买宝石。请使用 Android 或 iOS 应用。",
    ),
    AppLanguage.JAPANESE to shopPurchaseCopy(
        "ゴールド交換", "ジェムでレアアイテムを解除できます。購入履歴はストアで安全に同期されます。", "ジェムパックを選択",
        "ジェムでレアアイテムを解除できます。価格はストアアカウントから取得します。", "ログインしてジェムを購入し、端末間で同期しましょう。", "価格を取得できません",
        "ログイン報酬、難しいクエスト、特別な実績でもジェムを獲得できます。", "ジェムを安全にゴールドへ交換。交換履歴は資産履歴に記録されます。",
        "交換", "ジェム {gems} 個を使ってゴールド {gold} を受け取りますか？", "ジェムが足りません", "ゴールド {gold} を受け取りました。",
        "この交換はすでに完了しています。", "ゴールドに交換できませんでした。再試行してください。", "Google Play の決済を利用できません。",
        "Google Play から価格を取得できませんでした。", "支払いをキャンセルしました。", "この端末では Google Play の決済を利用できません。",
        "Google Play が支払いを処理中です。", "ジェムをアカウントに追加しました。", "Web版ではジェムを購入できません。AndroidまたはiOSアプリをご利用ください。",
    ),
    AppLanguage.KOREAN to shopPurchaseCopy(
        "골드 교환", "젬으로 희귀 아이템을 해금할 수 있으며 구매 내역은 스토어를 통해 안전하게 동기화됩니다.", "젬 묶음 선택",
        "젬으로 희귀 아이템을 해금하세요. 가격은 스토어 계정에서 가져옵니다.", "로그인하여 젬을 구매하고 기기 간에 동기화하세요.", "가격 정보 없음",
        "출석, 어려운 임무, 특별 업적으로도 젬을 얻을 수 있습니다.", "젬을 골드로 안전하게 교환하세요. 모든 교환은 재화 내역에 기록됩니다.",
        "교환", "젬 {gems}개로 골드 {gold}을(를) 받으시겠습니까?", "젬 부족", "골드 {gold}을(를) 받았습니다.",
        "이 교환은 이미 완료되었습니다.", "골드를 교환하지 못했습니다. 다시 시도하세요.", "Google Play 결제를 사용할 준비가 되지 않았습니다.",
        "Google Play에서 가격을 불러오지 못했습니다.", "결제가 취소되었습니다.", "이 기기에서는 Google Play 결제를 사용할 수 없습니다.",
        "Google Play에서 결제를 처리 중입니다.", "계정에 젬이 추가되었습니다.", "웹에서는 젬을 구매할 수 없습니다. Android 또는 iOS 앱을 사용하세요.",
    ),
    AppLanguage.SPANISH to shopPurchaseCopy(
        "Cambio por oro", "Las gemas desbloquean objetos raros y se sincronizan de forma segura mediante la tienda.", "Elige un paquete de gemas",
        "Las gemas desbloquean objetos raros. Los precios proceden de tu cuenta de la tienda.", "Inicia sesión para comprar gemas y sincronizarlas entre dispositivos.", "Precio no disponible",
        "También puedes conseguir gemas con registros diarios, misiones difíciles y logros especiales.", "Cambia gemas por oro de forma segura. Cada cambio queda registrado en tu historial de recursos.",
        "CAMBIAR", "¿Usar {gems} gemas para recibir {gold} de oro?", "No tienes suficientes gemas", "Has recibido {gold} de oro.",
        "Este cambio ya se completó.", "No se pudo cambiar por oro. Inténtalo de nuevo.", "Los pagos de Google Play aún no están listos.",
        "No se pudieron cargar los precios de Google Play.", "Pago cancelado.", "Los pagos de Google Play no están disponibles en este dispositivo.",
        "Google Play está procesando este pago.", "Se añadieron gemas a tu cuenta.", "No se pueden comprar gemas en la Web. Usa la app de Android o iOS.",
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to shopPurchaseCopy(
        "Troca por Ouro", "Gemas desbloqueiam itens raros e são sincronizadas com segurança pela loja.", "Escolha um pacote de Gemas",
        "Gemas desbloqueiam itens raros. Os preços vêm da sua conta da loja.", "Entre para comprar Gemas e sincronizá-las entre dispositivos.", "Preço indisponível",
        "Você também pode ganhar Gemas com check-ins, missões difíceis e conquistas especiais.", "Troque Gemas por Ouro com segurança. Cada troca fica registrada no histórico de recursos.",
        "TROCAR", "Usar {gems} Gemas para receber {gold} de Ouro?", "Gemas insuficientes", "Você recebeu {gold} de Ouro.",
        "Esta troca já foi concluída.", "Não foi possível trocar por Ouro. Tente novamente.", "A cobrança do Google Play não está pronta.",
        "Não foi possível carregar os preços do Google Play.", "Pagamento cancelado.", "A cobrança do Google Play não está disponível neste dispositivo.",
        "O Google Play está processando este pagamento.", "Gemas adicionadas à sua conta.", "Não é possível comprar Gemas na Web. Use o app para Android ou iOS.",
    ),
    AppLanguage.FRENCH to shopPurchaseCopy(
        "Échange contre de l’or", "Les gemmes débloquent des objets rares et sont synchronisées en sécurité via la boutique.", "Choisir un lot de gemmes",
        "Les gemmes débloquent des objets rares. Les prix proviennent de votre compte de boutique.", "Connectez-vous pour acheter des gemmes et les synchroniser entre appareils.", "Prix indisponible",
        "Vous pouvez aussi gagner des gemmes avec les pointages, les quêtes difficiles et les succès spéciaux.", "Échangez des gemmes contre de l’or en sécurité. Chaque échange est inscrit dans votre historique des ressources.",
        "ÉCHANGER", "Utiliser {gems} gemmes pour recevoir {gold} pièces d’or ?", "Gemmes insuffisantes", "Vous avez reçu {gold} pièces d’or.",
        "Cet échange a déjà été effectué.", "Impossible d’échanger contre de l’or. Réessayez.", "Le paiement Google Play n’est pas prêt.",
        "Impossible de charger les prix depuis Google Play.", "Paiement annulé.", "Le paiement Google Play n’est pas disponible sur cet appareil.",
        "Google Play traite ce paiement.", "Gemmes ajoutées à votre compte.", "L’achat de gemmes n’est pas disponible sur le Web. Utilisez l’application Android ou iOS.",
    ),
    AppLanguage.GERMAN to shopPurchaseCopy(
        "Goldtausch", "Juwelen schalten seltene Gegenstände frei und werden sicher über den Store synchronisiert.", "Juwelenpaket wählen",
        "Juwelen schalten seltene Gegenstände frei. Die Preise stammen aus deinem Store-Konto.", "Melde dich an, um Juwelen zu kaufen und geräteübergreifend zu synchronisieren.", "Preis nicht verfügbar",
        "Du kannst Juwelen auch durch Check-ins, schwere Aufgaben und besondere Erfolge verdienen.", "Tausche Juwelen sicher gegen Gold. Jeder Tausch wird im Ressourcenverlauf erfasst.",
        "TAUSCHEN", "{gems} Juwelen für {gold} Gold eintauschen?", "Nicht genug Juwelen", "{gold} Gold erhalten.",
        "Dieser Tausch wurde bereits durchgeführt.", "Goldtausch fehlgeschlagen. Versuche es erneut.", "Google-Play-Zahlungen sind noch nicht bereit.",
        "Preise konnten nicht von Google Play geladen werden.", "Zahlung abgebrochen.", "Google-Play-Zahlungen sind auf diesem Gerät nicht verfügbar.",
        "Google Play verarbeitet diese Zahlung.", "Juwelen wurden deinem Konto hinzugefügt.", "Juwelen können im Web nicht gekauft werden. Nutze die Android- oder iOS-App.",
    ),
    AppLanguage.INDONESIAN to shopPurchaseCopy(
        "Tukar Emas", "Gem membuka item langka dan disinkronkan dengan aman melalui toko.", "Pilih paket Gem",
        "Gem membuka item langka. Harga berasal dari akun tokomu.", "Masuk untuk membeli Gem dan menyinkronkannya antarperangkat.", "Harga tidak tersedia",
        "Kamu juga bisa memperoleh Gem dari check-in, misi sulit, dan pencapaian khusus.", "Tukar Gem dengan Emas secara aman. Setiap penukaran tercatat dalam riwayat sumber daya.",
        "TUKAR", "Gunakan {gems} Gem untuk menerima {gold} Emas?", "Gem tidak cukup", "Menerima {gold} Emas.",
        "Penukaran ini sudah selesai.", "Tidak dapat menukar Emas. Coba lagi.", "Pembayaran Google Play belum siap.",
        "Tidak dapat memuat harga dari Google Play.", "Pembayaran dibatalkan.", "Pembayaran Google Play tidak tersedia di perangkat ini.",
        "Google Play sedang memproses pembayaran ini.", "Gem telah ditambahkan ke akunmu.", "Pembelian Gem tidak didukung di Web. Gunakan aplikasi Android atau iOS.",
    ),
    AppLanguage.THAI to shopPurchaseCopy(
        "แลกทอง", "เจมใช้ปลดล็อกไอเทมหายากและซิงก์อย่างปลอดภัยผ่านร้านค้า", "เลือกแพ็กเจม",
        "เจมใช้ปลดล็อกไอเทมหายาก ราคามาจากบัญชีร้านค้าของคุณ", "เข้าสู่ระบบเพื่อซื้อเจมและซิงก์ระหว่างอุปกรณ์", "ไม่พบราคา",
        "รับเจมได้จากการเช็กอิน ภารกิจยาก และความสำเร็จพิเศษ", "แลกเจมเป็นทองอย่างปลอดภัย ทุกครั้งจะบันทึกในประวัติทรัพยากร",
        "แลก", "ใช้เจม {gems} เพื่อรับทอง {gold} หรือไม่", "เจมไม่พอ", "ได้รับทอง {gold} แล้ว",
        "รายการแลกนี้เสร็จแล้ว", "แลกทองไม่สำเร็จ โปรดลองอีกครั้ง", "ระบบชำระเงิน Google Play ยังไม่พร้อม",
        "โหลดราคาจาก Google Play ไม่ได้", "ยกเลิกการชำระเงินแล้ว", "อุปกรณ์นี้ใช้ระบบชำระเงิน Google Play ไม่ได้",
        "Google Play กำลังดำเนินการชำระเงิน", "เพิ่มเจมในบัญชีของคุณแล้ว", "ซื้อเจมบนเว็บไม่ได้ โปรดใช้แอป Android หรือ iOS",
    ),
    AppLanguage.RUSSIAN to shopPurchaseCopy(
        "Обмен на золото", "Самоцветы открывают редкие предметы и безопасно синхронизируются через магазин.", "Выбрать набор самоцветов",
        "Самоцветы открывают редкие предметы. Цены берутся из вашего аккаунта магазина.", "Войдите, чтобы покупать самоцветы и синхронизировать их между устройствами.", "Цена недоступна",
        "Самоцветы также можно заработать за отметки, сложные задания и особые достижения.", "Безопасно обменивайте самоцветы на золото. Каждый обмен записывается в историю ресурсов.",
        "ОБМЕНЯТЬ", "Обменять {gems} самоцветов на {gold} золота?", "Недостаточно самоцветов", "Получено {gold} золота.",
        "Этот обмен уже выполнен.", "Не удалось обменять на золото. Повторите попытку.", "Оплата через Google Play пока не готова.",
        "Не удалось загрузить цены из Google Play.", "Платёж отменён.", "Оплата через Google Play недоступна на этом устройстве.",
        "Google Play обрабатывает этот платёж.", "Самоцветы добавлены в ваш аккаунт.", "Покупка самоцветов в веб-версии недоступна. Используйте приложение для Android или iOS.",
    ),
)
