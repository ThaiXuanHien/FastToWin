package com.hienthai.fastowin.localization

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

internal fun utcOffsetLabel(zone: TimeZone, instant: Instant): String? {
    if (zone is FixedOffsetTimeZone) return null
    val offset = zone.offsetAt(instant).toString()
    if (zone.id in setOf("Asia/Bangkok", "Asia/Ho_Chi_Minh") && offset == "+07:00") return null
    return if (offset == "Z") "UTC+00:00" else "UTC$offset"
}
