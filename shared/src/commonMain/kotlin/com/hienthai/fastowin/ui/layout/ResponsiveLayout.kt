package com.hienthai.fastowin.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Khung nội dung dùng chung cho Android và iOS.
 *
 * Nội dung vẫn chiếm toàn bộ chiều cao nhưng được giới hạn chiều rộng và căn giữa
 * trên tablet. Khoảng đệm ngang tự giảm trên điện thoại nhỏ và tăng trên màn lớn.
 */
@Composable
fun ResponsiveScreen(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 840.dp,
    applySafeDrawingInsets: Boolean = true,
    includeBottomSafeDrawingInset: Boolean = true,
    avoidKeyboard: Boolean = false,
    content: @Composable (Modifier) -> Unit
) {
    val insetModifier = if (applySafeDrawingInsets) {
        val safeInsets = if (includeBottomSafeDrawingInset) {
            WindowInsets.safeDrawing
        } else {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        }
        Modifier.windowInsetsPadding(safeInsets)
    } else {
        Modifier
    }
    val keyboardModifier = if (avoidKeyboard) Modifier.imePadding() else Modifier

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(insetModifier)
            .then(keyboardModifier)
    ) {
        val horizontalPadding = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 16.dp
            maxWidth < 840.dp -> 24.dp
            else -> 32.dp
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            content(
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
            )
        }
    }
}
