package com.hienthai.fastowin.server.activity

import java.util.UUID

const val ACTIVITY_CHECKPOINT_MILLIS = 30_000L
const val ACTIVITY_MAX_GAP_MILLIS = 60_000L
const val ACTIVITY_RETENTION_MILLIS = 90 * 86_400_000L
internal const val ACTIVITY_CLEANUP_MILLIS = 3_600_000L

// userId exists only in the in-process write request. Stored observations contain subject keys only.
data class ActivityObservation(val id: UUID, val connectionId: UUID, val userId: String, val from: Long, val until: Long)
data class ActivityWindow(val id: UUID, val from: Long, val until: Long)
data class ActivityCheckpoint(val observations: List<ActivityObservation>, val window: ActivityWindow, val now: Long, val cleanup: Boolean)
