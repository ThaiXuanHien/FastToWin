package com.hienthai.fastowin.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class OpponentTileTrackerTest {
    @Test
    fun `numbers taken before any tap belong to the opponent`() {
        val tracker = OpponentTileTracker()
        assertEquals(setOf(1, 2, 3), tracker.opponentNumbers(listOf(1, 2, 3), correctSelections = 0))
    }

    @Test
    fun `rapid taps resolved over several server updates stay mine`() {
        val tracker = OpponentTileTracker()
        tracker.opponentNumbers(listOf(1, 2, 3, 4), 0)
        // Bấm liền 5..9 trước khi server kịp trả lời lần nào.
        (5..9).forEach(tracker::onTap)
        tracker.opponentNumbers(listOf(1, 2, 3, 4, 5), 1)
        tracker.opponentNumbers(listOf(1, 2, 3, 4, 5, 6, 7), 3)
        assertEquals(setOf(1, 2, 3, 4), tracker.opponentNumbers((1..9).toList(), 5))
    }

    @Test
    fun `opponent number taken while my tap was in flight stays the opponent's`() {
        val tracker = OpponentTileTracker()
        tracker.onTap(1)
        tracker.onTap(2)
        // Đối thủ lấy 2 trước, lần bấm 2 của mình bị server từ chối.
        assertEquals(setOf(2), tracker.opponentNumbers(listOf(1, 2), correctSelections = 1))
        tracker.onTap(3)
        assertEquals(setOf(2), tracker.opponentNumbers(listOf(1, 2, 3), correctSelections = 2))
    }

    @Test
    fun `mistaken tap on a later number does not steal it from the opponent`() {
        val tracker = OpponentTileTracker()
        tracker.onTap(9)
        tracker.opponentNumbers(emptyList(), 0)
        assertEquals(setOf(9), tracker.opponentNumbers(listOf(9), 0))
    }

    @Test
    fun `unknown history after rejoin shows no opponent colours`() {
        val tracker = OpponentTileTracker()
        assertEquals(emptySet(), tracker.opponentNumbers(listOf(1, 2, 3), correctSelections = 2))
    }
}
