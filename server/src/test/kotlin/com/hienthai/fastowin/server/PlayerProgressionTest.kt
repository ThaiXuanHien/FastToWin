package com.hienthai.fastowin.server

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerProgressionTest {
    @Test
    fun `experience required for each level increases gradually until level one hundred`() {
        assertEquals(PlayerLevelProgress(1, 0, 100), PlayerProgression.progress(0))
        assertEquals(PlayerLevelProgress(2, 0, 110), PlayerProgression.progress(100))
        assertEquals(PlayerLevelProgress(99, 1_079, 1_080), PlayerProgression.progress(58_409))
        assertEquals(PlayerLevelProgress(100, 1_080, 1_080), PlayerProgression.progress(58_410))
        assertEquals(PlayerLevelProgress(100, 1_080, 1_080), PlayerProgression.progress(100_000))
    }
}
