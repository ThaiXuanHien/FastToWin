package com.hienthai.fastowin.server

private const val REACTION_SEGMENT_SIZE = 10
private const val MATCH_COUNTDOWN_MILLIS = 3_000L

internal fun roomSelectionMetrics(
    playerIds: Set<String>,
    startedAt: Long?,
    events: List<MatchSelectionEvent>,
    independentBoards: Boolean
): Map<String, SelectionMetrics> {
    val metrics = playerIds.associateWith { SelectionMetrics() }.toMutableMap()
    if (startedAt == null) return metrics
    val targetAvailableAtMillis = playerIds.associateWith { startedAt }.toMutableMap()
    events.sortedBy(MatchSelectionEvent::sequence).forEach { event ->
        val current = metrics.getOrPut(event.playerId) { SelectionMetrics() }
        when (event.result) {
            SelectionResult.REJECTED -> current.wrong++
            SelectionResult.ACCEPTED -> {
                current.correct++
                val rawReactionMillis =
                    (event.occurredAtMillis - (targetAvailableAtMillis[event.playerId] ?: startedAt)).coerceAtLeast(0L)
                val reactionMillis = if (current.reactionTimes.isEmpty() && rawReactionMillis >= MATCH_COUNTDOWN_MILLIS) {
                    rawReactionMillis - MATCH_COUNTDOWN_MILLIS
                } else {
                    rawReactionMillis
                }
                current.reactionTimes += reactionMillis
                if (independentBoards) {
                    targetAvailableAtMillis[event.playerId] = event.occurredAtMillis
                } else {
                    playerIds.forEach { targetAvailableAtMillis[it] = event.occurredAtMillis }
                }
            }
        }
    }
    return metrics
}

internal data class SelectionMetrics(
    var correct: Int = 0,
    var wrong: Int = 0,
    val reactionTimes: MutableList<Long> = mutableListOf()
) {
    val averageReactionMillis: Long
        get() = if (reactionTimes.isEmpty()) 0L else reactionTimes.sum() / reactionTimes.size

    private val segments: List<ReactionSegment>
        get() = reactionTimes.chunked(REACTION_SEGMENT_SIZE).mapIndexed { index, values ->
            ReactionSegment(
                start = index * REACTION_SEGMENT_SIZE + 1,
                end = index * REACTION_SEGMENT_SIZE + values.size,
                averageMillis = values.average().toLong()
            )
        }

    val fastestSegment: ReactionSegment? get() = segments.minByOrNull(ReactionSegment::averageMillis)
    val slowestSegment: ReactionSegment? get() = segments.maxByOrNull(ReactionSegment::averageMillis)
}

internal data class ReactionSegment(val start: Int, val end: Int, val averageMillis: Long)
