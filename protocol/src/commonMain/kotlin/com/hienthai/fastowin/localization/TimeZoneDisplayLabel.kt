package com.hienthai.fastowin.localization

/** Display-only aliases; callers retain the original identifier for scheduling and conversion. */
fun LocalizationService.timeZoneDisplayLabel(timeZoneId: String): String = when (timeZoneId) {
    "Asia/Bangkok", "Asia/Ho_Chi_Minh" -> text(TextKey.VietnamTime)
    else -> timeZoneId
}
