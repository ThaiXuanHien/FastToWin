package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized

/**
 * Giải thích thông báo dùng để làm gì trước khi hệ điều hành hỏi quyền (bảng "Bật thông báo" của A1):
 * ô chuông vàng nổi, tiêu đề, mô tả, nút vàng BẬT THÔNG BÁO và nút trắng "Để sau".
 * "Để sau" không mở hộp thoại hệ thống; Cài đặt vẫn có công tắc.
 */
@Composable
fun PushOptInDialog(
    onEnable: () -> Unit,
    onLater: () -> Unit
) {
    ArcadeDialog(
        title = localized(TextKey.PushOptInTitle),
        subtitle = localized(TextKey.PushOptInSubtitle),
        onDismissRequest = onLater,
        heroIcon = Icons.Rounded.NotificationsActive,
        heroTone = A1HeroTone.YELLOW,
        modifier = Modifier.testTag("push_opt_in_dialog")
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ArcadeActionButton(
                label = localized(TextKey.PushOptInEnable).uppercase(),
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth().testTag("push_opt_in_enable"),
                style = ArcadeActionStyle.GOLD,
                large = true
            )
            ArcadeActionButton(
                label = localized(TextKey.PushOptInLater),
                onClick = onLater,
                modifier = Modifier.fillMaxWidth().testTag("push_opt_in_later"),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }
}
