package com.hienthai.fastowin.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Trang trí cảnh "Ngày nắng" của A1 (đồi cỏ, bụi cây, hoa, mặt trời, mây, tia nắng).
 * Chỉ vẽ, không có ngữ nghĩa trợ năng và không chuyển động (an toàn khi giảm chuyển động).
 */

/** Đồi cỏ, bụi cây và hoa ở chân khung nhìn (vẽ phía sau nội dung). */
fun Modifier.a1Hills(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val bleed = 50.dp.toPx()
    // Đồi sau (đậm hơn), lệch phải.
    val backH = 240.dp.toPx()
    drawOval(Color(0xFF4FC64A), Offset(150.dp.toPx(), h - backH), Size(w - 150.dp.toPx() + 90.dp.toPx(), backH * 2))
    // Đồi trước, có viền sáng ở mép trên.
    val frontH = 300.dp.toPx()
    val front = Rect(-bleed, h - frontH, w + bleed, h + frontH)
    drawOval(Color(0xFF9CF08F), front.topLeft, front.size)
    drawOval(Color(0xFF6FDC6A), Offset(front.left, front.top + 10.dp.toPx()), Size(front.width, front.height))
    // Bụi cây hai bên.
    bush(Offset(-20.dp.toPx(), h - 230.dp.toPx()), 1f)
    bush(Offset(w - 66.dp.toPx(), h - 180.dp.toPx()), 0.8f)
    // Hoa nhỏ.
    flower(Offset(66.dp.toPx(), h - 114.dp.toPx()))
    flower(Offset(w * 0.47f, h - 34.dp.toPx()))
    flower(Offset(w - 84.dp.toPx(), h - 64.dp.toPx()))
}

/**
 * Mặt trời, tia nắng và mây quanh khu vực linh vật.
 * [rayCenterY] là tâm tia nắng tính từ mép trên của phần tử (đơn vị px do người gọi quy đổi).
 */
fun Modifier.a1SunnySky(rayCenterYFraction: Float = 0.25f): Modifier = drawBehind {
    drawSunnySky(Rect(Offset.Zero, size), rayCenterYFraction)
}

/**
 * Vẽ tia nắng, mặt trời và mây cho vùng [area] (toạ độ của DrawScope hiện tại). Gọi từ drawBehind của
 * vùng chứa toàn bộ nội dung để mọi thẻ/nút luôn nằm đè lên trên phần trang trí.
 */
fun DrawScope.drawSunnySky(area: Rect, rayCenterYFraction: Float = 0.25f) {
    val c = Offset(area.center.x, area.top + area.height * rayCenterYFraction)
    rays(c, 320.dp.toPx())
    sun(Offset(area.left - 2.dp.toPx(), area.top + 18.dp.toPx()))
    cloud(Offset(area.right - 120.dp.toPx(), area.top + area.height * 0.58f), 1f)
    cloud(Offset(area.left + area.width * 0.5f, area.top + area.height * 0.08f), 0.6f)
}

private fun DrawScope.rays(center: Offset, radius: Float) {
    val brush = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.32f),
        0.55f to Color.White.copy(alpha = 0.12f),
        1f to Color.Transparent,
        center = center,
        radius = radius
    )
    val step = 20.0
    var a = 0.0
    while (a < 360.0) {
        val p = Path().apply {
            moveTo(center.x, center.y)
            val a0 = a * PI / 180.0
            val a1 = (a + 10.0) * PI / 180.0
            lineTo(center.x + (radius * cos(a0)).toFloat(), center.y + (radius * sin(a0)).toFloat())
            lineTo(center.x + (radius * cos(a1)).toFloat(), center.y + (radius * sin(a1)).toFloat())
            close()
        }
        drawPath(p, brush)
        a += step
    }
}

private fun DrawScope.sun(center: Offset) {
    val r = 48.dp.toPx()
    drawCircle(Color(0x26FFE066), r + 30.dp.toPx(), center)
    drawCircle(Color(0x4DFFE066), r + 14.dp.toPx(), center)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF8C4), Color(0xFFFFD23F), Color(0xFFFFB020)),
            center = Offset(center.x - r * 0.2f, center.y - r * 0.2f),
            radius = r * 1.2f
        ), r, center
    )
}

private fun DrawScope.cloud(topLeft: Offset, scale: Float) {
    val white = Color.White.copy(alpha = 0.95f)
    val s = scale
    drawRoundRect(white, topLeft, Size(118.dp.toPx() * s, 38.dp.toPx() * s), androidx.compose.ui.geometry.CornerRadius(40.dp.toPx() * s))
    drawCircle(white, 27.dp.toPx() * s, Offset(topLeft.x + 43.dp.toPx() * s, topLeft.y + 1.dp.toPx() * s))
    drawCircle(white, 20.dp.toPx() * s, Offset(topLeft.x + 78.dp.toPx() * s, topLeft.y + 4.dp.toPx() * s))
}

private fun DrawScope.bush(topLeft: Offset, scale: Float) {
    val w = 96.dp.toPx() * scale
    val h = 44.dp.toPx() * scale
    val outline = Color(0xFF1F7A1F)
    fun dome(x: Float, y: Float, dw: Float, dh: Float, fill: Color) {
        val p = Path().apply { addArc(Rect(x, y, x + dw, y + dh * 2), 180f, 180f); close() }
        drawPath(p, fill)
        drawPath(p, outline, style = Stroke(3.dp.toPx()))
    }
    dome(topLeft.x + 20.dp.toPx() * scale, topLeft.y - 22.dp.toPx() * scale, 52.dp.toPx() * scale, 40.dp.toPx() * scale, Color(0xFF4FCF48))
    dome(topLeft.x, topLeft.y, w, h, Color(0xFF3FBF3A))
}

private fun DrawScope.flower(center: Offset) {
    val petal = 5.dp.toPx()
    val d = 7.dp.toPx()
    for (o in listOf(Offset(0f, -d), Offset(0f, d), Offset(d, 0f), Offset(-d, 0f))) drawCircle(Color.White, petal, center + o)
    drawCircle(Color(0xFFFFE066), 6.dp.toPx(), center)
}
