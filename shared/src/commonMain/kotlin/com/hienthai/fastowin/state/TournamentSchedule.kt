package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.TournamentScheduleSnapshot
import com.hienthai.fastowin.localization.utcOffsetLabel
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class TournamentScheduleStage { REGISTRATION, CHECK_IN, STARTING }
fun TournamentScheduleSnapshot.stageAt(now: Long): TournamentScheduleStage = when {
    now < checkInOpensAtEpochMillis -> TournamentScheduleStage.REGISTRATION
    now < startsAtEpochMillis -> TournamentScheduleStage.CHECK_IN
    else -> TournamentScheduleStage.STARTING
}
fun tournamentCountdown(remainingMillis: Long): String {
    val seconds = (remainingMillis.coerceAtLeast(0) + 999) / 1000
    val minutes = seconds / 60
    val tail = (seconds % 60).toString().padStart(2, '0')
    return if (minutes < 60) "${minutes.toString().padStart(2, '0')}:$tail"
    else "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$tail"
}
internal fun tournamentDisplayZone(zoneId: String): TimeZone = try {
    TimeZone.of(zoneId)
} catch (_: IllegalTimeZoneException) {
    TimeZone.UTC
}

fun tournamentLocalDateTime(epochMillis: Long, zoneId: String): String =
    tournamentLocalDateTime(epochMillis, tournamentDisplayZone(zoneId))

internal fun tournamentLocalDateTime(epochMillis: Long, zone: TimeZone): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(zone)
    val clockTime = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    val offset = utcOffsetLabel(zone, instant)?.let { " ($it)" }.orEmpty()
    return "${local.date} $clockTime$offset"
}
