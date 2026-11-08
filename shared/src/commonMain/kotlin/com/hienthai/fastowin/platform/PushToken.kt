package com.hienthai.fastowin.platform

fun normalizePushToken(token: String?): String? =
    token?.trim()?.takeIf(String::isNotEmpty)
