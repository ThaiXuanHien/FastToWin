package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

internal val playQuotaTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.VIETNAMESE to mapOf(
        TextKey.OnlineMatchesRemaining to "Lượt online: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "Hết lượt đấu online",
        TextKey.OnlineQuotaExhaustedMessage to "Bạn đã dùng hết lượt hôm nay. Xem quảng cáo để nhận thêm 2 lượt.",
        TextKey.WatchAdForTwoMatches to "XEM QUẢNG CÁO · +2 LƯỢT",
        TextKey.RewardedAdGranted to "Đã cộng 2 lượt đấu online",
        TextKey.RewardedAdCancelled to "Đã hủy quảng cáo. Không cộng thêm lượt.",
        TextKey.RewardedAdUnavailable to "Quảng cáo nhận thưởng hiện chưa khả dụng.",
        TextKey.RewardedAdUseMobile to "Hãy dùng Fast To Win trên Android hoặc iOS để xem quảng cáo nhận thêm lượt.",
        TextKey.OnlineAccountRequired to "Đăng nhập để chơi online."
    ),
    AppLanguage.ENGLISH to mapOf(
        TextKey.OnlineMatchesRemaining to "Online matches: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "No online matches left",
        TextKey.OnlineQuotaExhaustedMessage to "You have used today's matches. Watch an ad to get 2 more.",
        TextKey.WatchAdForTwoMatches to "WATCH AD · +2 MATCHES",
        TextKey.RewardedAdGranted to "2 online matches added",
        TextKey.RewardedAdCancelled to "Ad cancelled. No matches were added.",
        TextKey.RewardedAdUnavailable to "Rewarded ads are unavailable right now.",
        TextKey.RewardedAdUseMobile to "Use Fast To Win on Android or iOS to watch an ad for more matches.",
        TextKey.OnlineAccountRequired to "Log in to play online."
    ),
    AppLanguage.SIMPLIFIED_CHINESE to mapOf(
        TextKey.OnlineMatchesRemaining to "在线对局：{remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "今日在线对局次数已用完",
        TextKey.OnlineQuotaExhaustedMessage to "你已用完今日次数。观看广告可获得 2 次额外机会。",
        TextKey.WatchAdForTwoMatches to "观看广告 · +2 次",
        TextKey.RewardedAdGranted to "已增加 2 次在线对局机会",
        TextKey.RewardedAdCancelled to "广告已取消，未增加次数。",
        TextKey.RewardedAdUnavailable to "奖励广告暂不可用。",
        TextKey.RewardedAdUseMobile to "请在 Android 或 iOS 版 Fast To Win 中观看广告以获得更多次数。",
        TextKey.OnlineAccountRequired to "登录后即可在线对战。"
    ),
    AppLanguage.JAPANESE to mapOf(
        TextKey.OnlineMatchesRemaining to "オンライン対戦：{remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "本日のオンライン対戦回数を使い切りました",
        TextKey.OnlineQuotaExhaustedMessage to "本日分を使い切りました。広告を見ると2回分追加されます。",
        TextKey.WatchAdForTwoMatches to "広告を見る · +2回",
        TextKey.RewardedAdGranted to "オンライン対戦を2回追加しました",
        TextKey.RewardedAdCancelled to "広告をキャンセルしました。回数は追加されません。",
        TextKey.RewardedAdUnavailable to "リワード広告は現在利用できません。",
        TextKey.RewardedAdUseMobile to "追加回数を受け取るには Android または iOS 版 Fast To Win をご利用ください。",
        TextKey.OnlineAccountRequired to "オンライン対戦にはログインが必要です。"
    ),
    AppLanguage.KOREAN to mapOf(
        TextKey.OnlineMatchesRemaining to "온라인 경기: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "오늘의 온라인 경기를 모두 사용했습니다",
        TextKey.OnlineQuotaExhaustedMessage to "오늘의 횟수를 모두 사용했습니다. 광고를 보면 2회가 추가됩니다.",
        TextKey.WatchAdForTwoMatches to "광고 보기 · +2회",
        TextKey.RewardedAdGranted to "온라인 경기 2회가 추가되었습니다",
        TextKey.RewardedAdCancelled to "광고를 취소했습니다. 경기 횟수는 추가되지 않았습니다.",
        TextKey.RewardedAdUnavailable to "현재 보상형 광고를 이용할 수 없습니다.",
        TextKey.RewardedAdUseMobile to "Android 또는 iOS용 Fast To Win에서 광고를 보고 경기 횟수를 받으세요.",
        TextKey.OnlineAccountRequired to "온라인 플레이를 하려면 로그인하세요."
    ),
    AppLanguage.SPANISH to mapOf(
        TextKey.OnlineMatchesRemaining to "Partidas online: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "No quedan partidas online",
        TextKey.OnlineQuotaExhaustedMessage to "Has usado las partidas de hoy. Mira un anuncio para conseguir 2 más.",
        TextKey.WatchAdForTwoMatches to "VER ANUNCIO · +2 PARTIDAS",
        TextKey.RewardedAdGranted to "Se añadieron 2 partidas online",
        TextKey.RewardedAdCancelled to "Anuncio cancelado. No se añadieron partidas.",
        TextKey.RewardedAdUnavailable to "Los anuncios con recompensa no están disponibles ahora.",
        TextKey.RewardedAdUseMobile to "Usa Fast To Win en Android o iOS para ver un anuncio y conseguir más partidas.",
        TextKey.OnlineAccountRequired to "Inicia sesión para jugar online."
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to mapOf(
        TextKey.OnlineMatchesRemaining to "Partidas online: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "Sem partidas online",
        TextKey.OnlineQuotaExhaustedMessage to "Você usou as partidas de hoje. Assista a um anúncio para ganhar mais 2.",
        TextKey.WatchAdForTwoMatches to "VER ANÚNCIO · +2 PARTIDAS",
        TextKey.RewardedAdGranted to "2 partidas online adicionadas",
        TextKey.RewardedAdCancelled to "Anúncio cancelado. Nenhuma partida foi adicionada.",
        TextKey.RewardedAdUnavailable to "Os anúncios com recompensa não estão disponíveis agora.",
        TextKey.RewardedAdUseMobile to "Use o Fast To Win no Android ou iOS para assistir a um anúncio e ganhar mais partidas.",
        TextKey.OnlineAccountRequired to "Entre na conta para jogar online."
    ),
    AppLanguage.FRENCH to mapOf(
        TextKey.OnlineMatchesRemaining to "Parties en ligne : {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "Plus de parties en ligne",
        TextKey.OnlineQuotaExhaustedMessage to "Vous avez utilisé les parties du jour. Regardez une publicité pour en obtenir 2 de plus.",
        TextKey.WatchAdForTwoMatches to "VOIR LA PUBLICITÉ · +2 PARTIES",
        TextKey.RewardedAdGranted to "2 parties en ligne ajoutées",
        TextKey.RewardedAdCancelled to "Publicité annulée. Aucune partie ajoutée.",
        TextKey.RewardedAdUnavailable to "Les publicités récompensées sont indisponibles pour le moment.",
        TextKey.RewardedAdUseMobile to "Utilisez Fast To Win sur Android ou iOS pour regarder une publicité et obtenir plus de parties.",
        TextKey.OnlineAccountRequired to "Connectez-vous pour jouer en ligne."
    ),
    AppLanguage.GERMAN to mapOf(
        TextKey.OnlineMatchesRemaining to "Online-Matches: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "Keine Online-Matches mehr",
        TextKey.OnlineQuotaExhaustedMessage to "Du hast die heutigen Matches verbraucht. Sieh dir Werbung an, um 2 weitere zu erhalten.",
        TextKey.WatchAdForTwoMatches to "WERBUNG ANSEHEN · +2 MATCHES",
        TextKey.RewardedAdGranted to "2 Online-Matches hinzugefügt",
        TextKey.RewardedAdCancelled to "Werbung abgebrochen. Es wurden keine Matches hinzugefügt.",
        TextKey.RewardedAdUnavailable to "Belohnungswerbung ist derzeit nicht verfügbar.",
        TextKey.RewardedAdUseMobile to "Nutze Fast To Win auf Android oder iOS, um durch Werbung weitere Matches zu erhalten.",
        TextKey.OnlineAccountRequired to "Melde dich an, um online zu spielen."
    ),
    AppLanguage.INDONESIAN to mapOf(
        TextKey.OnlineMatchesRemaining to "Pertandingan online: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "Pertandingan online habis",
        TextKey.OnlineQuotaExhaustedMessage to "Jatah hari ini sudah habis. Tonton iklan untuk mendapat 2 pertandingan lagi.",
        TextKey.WatchAdForTwoMatches to "TONTON IKLAN · +2 PERTANDINGAN",
        TextKey.RewardedAdGranted to "2 pertandingan online ditambahkan",
        TextKey.RewardedAdCancelled to "Iklan dibatalkan. Tidak ada pertandingan yang ditambahkan.",
        TextKey.RewardedAdUnavailable to "Iklan berhadiah sedang tidak tersedia.",
        TextKey.RewardedAdUseMobile to "Gunakan Fast To Win di Android atau iOS untuk menonton iklan dan mendapat pertandingan tambahan.",
        TextKey.OnlineAccountRequired to "Masuk untuk bermain online."
    ),
    AppLanguage.THAI to mapOf(
        TextKey.OnlineMatchesRemaining to "แมตช์ออนไลน์: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "แมตช์ออนไลน์หมดแล้ว",
        TextKey.OnlineQuotaExhaustedMessage to "คุณใช้สิทธิ์วันนี้ครบแล้ว ดูโฆษณาเพื่อรับเพิ่มอีก 2 แมตช์",
        TextKey.WatchAdForTwoMatches to "ดูโฆษณา · +2 แมตช์",
        TextKey.RewardedAdGranted to "เพิ่มแมตช์ออนไลน์ 2 ครั้งแล้ว",
        TextKey.RewardedAdCancelled to "ยกเลิกโฆษณาแล้ว ไม่มีการเพิ่มแมตช์",
        TextKey.RewardedAdUnavailable to "ขณะนี้โฆษณาแบบรับรางวัลไม่พร้อมใช้งาน",
        TextKey.RewardedAdUseMobile to "ใช้ Fast To Win บน Android หรือ iOS เพื่อดูโฆษณาและรับแมตช์เพิ่ม",
        TextKey.OnlineAccountRequired to "เข้าสู่ระบบเพื่อเล่นออนไลน์"
    ),
    AppLanguage.RUSSIAN to mapOf(
        TextKey.OnlineMatchesRemaining to "Онлайн-матчи: {remaining}/{total}",
        TextKey.OnlineQuotaExhaustedTitle to "Онлайн-матчи закончились",
        TextKey.OnlineQuotaExhaustedMessage to "Вы использовали матчи на сегодня. Посмотрите рекламу, чтобы получить ещё 2.",
        TextKey.WatchAdForTwoMatches to "СМОТРЕТЬ РЕКЛАМУ · +2 МАТЧА",
        TextKey.RewardedAdGranted to "Добавлено 2 онлайн-матча",
        TextKey.RewardedAdCancelled to "Реклама отменена. Матчи не добавлены.",
        TextKey.RewardedAdUnavailable to "Реклама с наградой сейчас недоступна.",
        TextKey.RewardedAdUseMobile to "Используйте Fast To Win на Android или iOS, чтобы посмотреть рекламу и получить матчи.",
        TextKey.OnlineAccountRequired to "Войдите, чтобы играть онлайн."
    )
)
