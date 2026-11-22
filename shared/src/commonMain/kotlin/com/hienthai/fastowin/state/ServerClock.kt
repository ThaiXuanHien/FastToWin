package com.hienthai.fastowin.state

import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Estimates server epoch time from the elapsed duration since receipt of a sample. */
class ServerClock(internal val serverEpochMillis: Long, private val receivedAt: TimeMark = TimeSource.Monotonic.markNow()) {
    fun now(): Long = serverEpochMillis + receivedAt.elapsedNow().inWholeMilliseconds
}
