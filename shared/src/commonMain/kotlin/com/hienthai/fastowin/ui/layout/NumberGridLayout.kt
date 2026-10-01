package com.hienthai.fastowin.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class NumberGridLayout(
    val columns: Int,
    val rows: Int,
    val cellSize: Dp,
    val padding: Dp,
    val spacing: Dp
) {
    val width: Dp get() = padding * 2 + cellSize * columns + spacing * (columns - 1)
    val height: Dp get() = padding * 2 + cellSize * rows + spacing * (rows - 1)
}

internal fun numberGridLayout(numberCount: Int, width: Dp, height: Dp): NumberGridLayout {
    val count = numberCount.coerceAtLeast(0)
    val candidates = (5..10).filter { count > 0 && count % it == 0 }
        .ifEmpty { (5..10).toList() }
    // Full rows first: a 50-number board can only be 5x10 or 10x5.
    // Pick the layout with the largest square cells that fit the available board.
    return candidates.map { columns ->
        val rows = ((count + columns - 1) / columns).coerceAtLeast(1)
        val padding = if (columns >= 8) 6.dp else 8.dp
        val spacing = if (columns >= 8) 4.dp else 6.dp
        val cellWidth = (width - padding * 2 - spacing * (columns - 1)) / columns
        val cellHeight = (height - padding * 2 - spacing * (rows - 1)) / rows
        NumberGridLayout(
            columns = columns,
            rows = rows,
            cellSize = minOf(cellWidth, cellHeight, 54.dp).coerceAtLeast(0.dp),
            padding = padding,
            spacing = spacing
        )
    }.maxBy { it.cellSize.value }
}
