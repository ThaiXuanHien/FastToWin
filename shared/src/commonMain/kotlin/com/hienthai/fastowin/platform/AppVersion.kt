package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable

/** "1.0.24 (25)" của bản đang chạy; null khi nền tảng không có số phiên bản (Web). */
@Composable
expect fun rememberAppVersionLabel(): String?
