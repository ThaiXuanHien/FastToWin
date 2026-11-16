package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

/** SEC-07: copy for the Turnstile challenge shown before sign-up, password reset and code resends. */
internal fun humanVerificationTexts(language: AppLanguage): Map<TextKey, String> = if (language == AppLanguage.VIETNAMESE) mapOf(
    TextKey.HumanVerificationTitle to "Xác minh nhanh",
    TextKey.HumanVerificationDescription to "Hoàn tất bước kiểm tra này để chúng tôi biết bạn không phải robot.",
    TextKey.HumanVerificationLoading to "Đang tải bước kiểm tra…",
    TextKey.HumanVerificationRequired to "Vui lòng hoàn tất bước xác minh để tiếp tục.",
    TextKey.HumanVerificationFailed to "Xác minh không thành công. Vui lòng thử lại.",
    TextKey.HumanVerificationUnavailable to "Chưa thể xác minh lúc này. Vui lòng thử lại sau.",
) else mapOf(
    TextKey.HumanVerificationTitle to "Quick check",
    TextKey.HumanVerificationDescription to "Complete this check so we know you're not a bot.",
    TextKey.HumanVerificationLoading to "Loading the check…",
    TextKey.HumanVerificationRequired to "Complete the verification to continue.",
    TextKey.HumanVerificationFailed to "Verification failed. Please try again.",
    TextKey.HumanVerificationUnavailable to "Verification isn't available right now. Please try again later.",
)
