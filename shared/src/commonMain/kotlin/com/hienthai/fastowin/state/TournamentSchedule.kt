package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.TournamentScheduleRequest
import com.hienthai.fastowin.protocol.TournamentScheduleSnapshot
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class TournamentScheduleStage { REGISTRATION, CHECK_IN, STARTING }
data class TournamentServerClock(val serverEpochMillis: Long, val receivedAtEpochMillis: Long) {
    fun now(localEpochMillis: Long): Long = serverEpochMillis + (localEpochMillis - receivedAtEpochMillis)
}
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
fun parseTournamentSchedule(localText: String, zoneText: String): Result<TournamentScheduleRequest> =
    runCatching {
        require(Regex("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}").matches(localText.trim()))
        val local = LocalDateTime.parse(localText.trim().replace(' ', 'T'))
        val zone = TimeZone.of(zoneText.trim())
        val instant = local.toInstant(zone)
        require(instant.toLocalDateTime(zone) == local)
        TournamentScheduleRequest(instant.toEpochMilliseconds(), zone.id)
    }
fun TournamentScheduleRequest.toScheduleSnapshot(): TournamentScheduleSnapshot =
    TournamentScheduleSnapshot(startsAtEpochMillis, timeZoneId, startsAtEpochMillis - 900_000, startsAtEpochMillis - 900_000)
fun tournamentLocalDateTime(epochMillis: Long, zoneId: String): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.of(zoneId))
    return "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
