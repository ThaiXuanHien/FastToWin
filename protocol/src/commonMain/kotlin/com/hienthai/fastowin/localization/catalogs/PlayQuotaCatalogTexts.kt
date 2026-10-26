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
)
