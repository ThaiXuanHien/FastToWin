package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.ui.theme.A1Palette

enum class TiaMood { HAPPY, CHEER, SAD }

private val TiaInk = A1Palette.Line
private val TiaPupil = A1Palette.Ink

/**
 * Linh vật "Tia" của A1: khối vàng có tia chớp trên đầu. Vẽ bằng Canvas theo khung 140×150
 * nên sắc nét ở mọi kích thước. Chỉ để trang trí, không có ngữ nghĩa trợ năng.
 */
@Composable
fun TiaMascot(mood: TiaMood = TiaMood.HAPPY, size: Dp = 140.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = size, height = size * (150f / 140f))) {
        scale(this.size.width / 140f, this.size.height / 150f, pivot = Offset.Zero) { drawTia(mood) }
    }
}

private fun DrawScope.stroke(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawTia(mood: TiaMood) {
    // Bóng dưới chân
    drawOval(Color(0xFF0B2447).copy(alpha = 0.18f), Offset(32f, 138f), Size(76f, 12f))
    // Tia chớp
    val bolt = Path().apply {
        moveTo(74f, 2f); lineTo(56f, 34f); lineTo(70f, 34f); lineTo(62f, 54f)
        lineTo(88f, 22f); lineTo(74f, 22f); lineTo(82f, 2f); close()
    }
    drawPath(bolt, Color(0xFFFFF3A6))
    drawPath(bolt, TiaInk, style = stroke(5f))
    // Chân
    for (x in listOf(44f, 76f)) {
        drawRoundRect(A1Palette.Yellow3, Offset(x, 118f), Size(20f, 20f), CornerRadius(9f))
        drawRoundRect(TiaInk, Offset(x, 118f), Size(20f, 20f), CornerRadius(9f), style = stroke(5f))
    }
    // Tay
    if (mood == TiaMood.CHEER) {
        arm(28f, 76f, 12f, 60f, 16f, 38f); arm(112f, 76f, 128f, 60f, 124f, 38f)
        hand(16f, 34f); hand(124f, 34f)
    } else {
        arm(26f, 84f, 10f, 92f, 14f, 108f); arm(114f, 84f, 130f, 78f, 132f, 60f)
        hand(14f, 110f); hand(132f, 56f)
    }
    // Thân
    val body = Brush.verticalGradient(
        0f to Color(0xFFFFE066), 0.6f to Color(0xFFFFC21A), 1f to Color(0xFFFF9F0A),
        startY = 36f, endY = 124f
    )
    drawRoundRect(body, Offset(22f, 36f), Size(96f, 88f), CornerRadius(30f))
    drawRoundRect(TiaInk, Offset(22f, 36f), Size(96f, 88f), CornerRadius(30f), style = stroke(6f))
    drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(34f, 46f), Size(44f, 12f), CornerRadius(6f))
    // Mắt
    for ((cx, px) in listOf(54f to 56f, 86f to 88f)) {
        drawOval(Color.White, Offset(cx - 11f, 65f), Size(22f, 26f))
        drawOval(TiaInk, Offset(cx - 11f, 65f), Size(22f, 26f), style = stroke(4f))
        drawCircle(TiaPupil, 6f, Offset(px, 80f))
        drawCircle(Color.White, 2.2f, Offset(px + 2.5f, 77f))
    }
    // Má hồng
    drawOval(Color(0xFFFF6F91).copy(alpha = 0.65f), Offset(33f, 91.5f), Size(14f, 9f))
    drawOval(Color(0xFFFF6F91).copy(alpha = 0.65f), Offset(93f, 91.5f), Size(14f, 9f))
    // Miệng
    if (mood == TiaMood.SAD) {
        val frown = Path().apply { moveTo(60f, 106f); quadraticTo(70f, 98f, 80f, 106f) }
        drawPath(frown, TiaInk, style = stroke(4f))
        val tear = Path().apply { moveTo(116f, 58f); relativeQuadraticTo(6f, 10f, 0f, 14f); relativeQuadraticTo(-6f, -4f, 0f, -14f); close() }
        drawPath(tear, Color(0xFF8FD3FF)); drawPath(tear, TiaInk, style = stroke(3f))
    } else {
        val smile = Path().apply { moveTo(58f, 98f); quadraticTo(70f, 114f, 82f, 98f); close() }
        drawPath(smile, Color(0xFFFF6F6F)); drawPath(smile, TiaInk, style = stroke(4f))
    }
}

private fun DrawScope.arm(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float) {
    val p = Path().apply { moveTo(x0, y0); quadraticTo(cx, cy, x1, y1) }
    drawPath(p, TiaInk, style = stroke(6f))
}

private fun DrawScope.hand(cx: Float, cy: Float) {
    drawCircle(Color(0xFFFFC21A), 9f, Offset(cx, cy))
    drawCircle(TiaInk, 9f, Offset(cx, cy), style = stroke(5f))
}
