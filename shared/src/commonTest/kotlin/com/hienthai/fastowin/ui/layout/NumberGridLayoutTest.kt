package com.hienthai.fastowin.ui.layout

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberGridLayoutTest {
    @Test
    fun `portrait board has five full columns and ten full rows`() {
        val layout = numberGridLayout(50, 343.dp, 600.dp)
        assertEquals(5, layout.columns)
        assertEquals(10, layout.rows)
    }

    @Test
    fun `landscape board has ten full columns and five full rows`() {
        val layout = numberGridLayout(50, 720.dp, 220.dp)
        assertEquals(10, layout.columns)
        assertEquals(5, layout.rows)
    }

    @Test
    fun `fifty numbers always fill every row and fit the board`() {
        for (width in listOf(288, 343, 398, 720, 760)) {
            for (height in listOf(150, 220, 320, 480, 600, 900)) {
                val layout = numberGridLayout(50, width.dp, height.dp)
                assertEquals(50, layout.columns * layout.rows, "$width x $height")
                assertTrue(layout.columns in listOf(5, 10))
                assertTrue(layout.cellSize > 0.dp)
                assertTrue(layout.width.value <= width + 0.01f)
                assertTrue(layout.height.value <= height + 0.01f)
                assertEquals(layout.padding * 2 + layout.cellSize * layout.columns +
                    layout.spacing * (layout.columns - 1), layout.width)
                assertEquals(layout.padding * 2 + layout.cellSize * layout.rows +
                    layout.spacing * (layout.rows - 1), layout.height)
            }
        }
    }

    @Test
    fun `empty or incomplete boards do not divide by zero`() {
        for (count in listOf(0, 1, 7, 49, 51)) {
            val layout = numberGridLayout(count, 343.dp, 600.dp)
            assertTrue(layout.columns * layout.rows >= count)
            assertTrue(layout.cellSize > 0.dp)
        }
    }
}
