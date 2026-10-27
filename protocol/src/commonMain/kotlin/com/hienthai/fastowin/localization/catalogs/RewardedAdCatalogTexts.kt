package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

internal val rewardedAdTexts = mapOf(
    AppLanguage.ENGLISH to mapOf(
        TextKey.RewardedAdLoading to "Loading your ad…",
        TextKey.RewardedAdClosed to "Ad closed.",
        TextKey.RewardedAdPending to "Waiting for secure reward verification. You can close this and check again.",
        TextKey.RewardedAdVerifyRetry to "Check reward status",
        TextKey.RewardedAdPrivacyDenied to "Ads are unavailable with your current privacy choices.",
        TextKey.RewardedAdAgeTitle to "Your age group",
        TextKey.RewardedAdAgeMessage to "Choose your age group to set suitable ad privacy protections. Your birthday is not collected.",
        TextKey.RewardedAdAgeBlocked to "Rewarded ads are available from age 13.",
        TextKey.RewardedAdPrivacyOptions to "Ad privacy options",
        TextKey.RewardedAdPrivacyDescription to "Review your choices for ads on this device.",
        TextKey.RewardedAdAgeUnder13 to "Under 13",
        TextKey.RewardedAdAge13To15 to "13–15",
        TextKey.RewardedAdAge16To17 to "16–17",
        TextKey.RewardedAdAge18Plus to "18 or older"
    ),
    AppLanguage.VIETNAMESE to mapOf(
        TextKey.RewardedAdLoading to "Đang tải quảng cáo…",
        TextKey.RewardedAdClosed to "Đã đóng quảng cáo.",
        TextKey.RewardedAdPending to "Đang chờ xác minh phần thưởng an toàn. Bạn có thể đóng và kiểm tra lại.",
        TextKey.RewardedAdVerifyRetry to "Kiểm tra phần thưởng",
        TextKey.RewardedAdPrivacyDenied to "Quảng cáo không khả dụng với lựa chọn quyền riêng tư hiện tại.",
        TextKey.RewardedAdAgeTitle to "Nhóm tuổi của bạn",
        TextKey.RewardedAdAgeMessage to "Chọn nhóm tuổi để áp dụng bảo vệ quyền riêng tư phù hợp cho quảng cáo. Không thu thập ngày sinh.",
        TextKey.RewardedAdAgeBlocked to "Quảng cáo nhận thưởng dành cho người từ 13 tuổi.",
        TextKey.RewardedAdPrivacyOptions to "Quyền riêng tư quảng cáo",
        TextKey.RewardedAdPrivacyDescription to "Xem lại lựa chọn quảng cáo trên thiết bị này.",
        TextKey.RewardedAdAgeUnder13 to "Dưới 13 tuổi",
        TextKey.RewardedAdAge13To15 to "13–15 tuổi",
        TextKey.RewardedAdAge16To17 to "16–17 tuổi",
        TextKey.RewardedAdAge18Plus to "Từ 18 tuổi"
    ),
)
