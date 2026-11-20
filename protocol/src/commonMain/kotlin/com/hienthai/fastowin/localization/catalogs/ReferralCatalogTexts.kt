package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

internal fun referralTexts(language: AppLanguage) = if (language == AppLanguage.VIETNAMESE) {
    mapOf(
        TextKey.ReferralInvite to "Mời bạn chơi",
        TextKey.ReferralCodeLabel to "Mã mời của bạn",
        TextKey.ReferralCodeOptional to "Mã mời (không bắt buộc)",
        TextKey.ReferralInvalidCode to "Mã mời không hợp lệ. Bạn có thể sửa hoặc xóa mã để đăng ký.",
        TextKey.ReferralOffer to "Bạn mới nhận {friendGold} Vàng khi đã xác minh email, chọn đúng ít nhất một số và hoàn thành trận thường hoặc xếp hạng đầu tiên. Bạn nhận {inviterGold} Vàng cho mỗi lượt đủ điều kiện, tối đa {limit} lượt mỗi tháng.",
        TextKey.ReferralShare to "Chia sẻ link mời",
        TextKey.ReferralCopyCode to "Sao chép mã",
        TextKey.ReferralShareText to "Chơi FastToWin cùng mình! Mở {link} hoặc nhập mã {code} khi đăng ký.",
        TextKey.ReferralPending to "Chờ hoàn thành trận · {count}",
        TextKey.ReferralRewarded to "Đã nhận thưởng · {count}",
        TextKey.ReferralMonth to "Lượt có thưởng tháng này · {count}/{limit}",
        TextKey.ReferralCompletedWithoutBonus to "Đã hoàn thành, bạn không nhận thưởng · {count}",
        TextKey.ReferralRewardTitle to "Thưởng giới thiệu bạn bè",
        TextKey.ReferralRewardMessage to "Bạn đã nhận {gold} Vàng từ lời mời chơi FastToWin.",
        TextKey.ReferralOpenApp to "Mở ứng dụng FastToWin",
        TextKey.ReferralLoadFailed to "Chưa tải được mã mời và tiến độ. Vui lòng thử lại."
    )
} else {
    mapOf(
        TextKey.ReferralInvite to "Invite friends",
        TextKey.ReferralCodeLabel to "Your invite code",
        TextKey.ReferralCodeOptional to "Invite code (optional)",
        TextKey.ReferralInvalidCode to "Invalid invite code. Edit or clear it to sign up.",
        TextKey.ReferralOffer to "Your friend earns {friendGold} gold after verifying their email, selecting at least one correct number, and finishing their first casual or ranked match. You earn {inviterGold} gold per eligible invite, up to {limit} each month.",
        TextKey.ReferralShare to "Share invite link",
        TextKey.ReferralCopyCode to "Copy code",
        TextKey.ReferralShareText to "Play FastToWin with me! Open {link} or enter {code} when signing up.",
        TextKey.ReferralPending to "Waiting for first match · {count}",
        TextKey.ReferralRewarded to "Rewarded · {count}",
        TextKey.ReferralMonth to "Rewarded this month · {count}/{limit}",
        TextKey.ReferralCompletedWithoutBonus to "Completed without an inviter reward · {count}",
        TextKey.ReferralRewardTitle to "Referral reward",
        TextKey.ReferralRewardMessage to "You earned {gold} gold from a FastToWin invite.",
        TextKey.ReferralOpenApp to "Open FastToWin app",
        TextKey.ReferralLoadFailed to "Could not load your invite code and progress. Please try again."
    )
}
