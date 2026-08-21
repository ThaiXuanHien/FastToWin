package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable

/**
 * Xử lý sự kiện Back hệ thống (vuốt từ cạnh màn hình hoặc nút Back cứng trên Android).
 *
 * @param enabled Nếu true, trình xử lý này sẽ tiêu thụ sự kiện Back.
 * @param onBack Hành động được thực thi khi người dùng thực hiện thao tác Back.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)
