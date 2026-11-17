package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

/** Copy for the in-app explanation shown before the system notification permission prompt. */
internal fun pushOptInTexts(language: AppLanguage): Map<TextKey, String> = if (language == AppLanguage.VIETNAMESE) mapOf(
    TextKey.PushOptInTitle to "Bật thông báo",
    TextKey.PushOptInSubtitle to "Đừng bỏ lỡ khi bạn bè gọi bạn vào trận.",
    TextKey.PushOptInRoomInvites to "Lời mời vào phòng và giải đấu",
    TextKey.PushOptInRewards to "Nhắc điểm danh, thưởng nhiệm vụ",
    TextKey.PushOptInPrivacy to "Bạn có thể tắt bất cứ lúc nào trong Cài đặt.",
    TextKey.PushOptInEnable to "Bật thông báo",
) else mapOf(
    TextKey.PushOptInTitle to "Turn on notifications",
    TextKey.PushOptInSubtitle to "Don't miss it when friends call you into a match.",
    TextKey.PushOptInRoomInvites to "Room and tournament invitations",
    TextKey.PushOptInRewards to "Check-in and mission reward reminders",
    TextKey.PushOptInPrivacy to "You can turn them off anytime in Settings.",
    TextKey.PushOptInEnable to "Turn on notifications",
)
