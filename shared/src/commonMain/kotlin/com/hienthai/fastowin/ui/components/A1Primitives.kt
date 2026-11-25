package com.hienthai.fastowin.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.ui.theme.A1Palette

/** Màu của một nút 3D A1: dải màu mặt nút, viền, đế (bóng đặc phía dưới) và màu chữ. */
@Immutable
data class A1ButtonColors(
    val top: Color,
    val bottom: Color,
    val border: Color,
    val base: Color,
    val content: Color,
    /** Bóng đặc dưới chữ giúp chữ trắng nổi trên mặt nút sáng. */
    val textShadow: Color?
)

internal object A1ButtonDefaults {
    val Yellow = A1ButtonColors(A1Palette.Yellow1, A1Palette.Yellow3, A1Palette.YellowBorder, A1Palette.YellowDeep, Color.White, A1Palette.YellowDeep)
    val Blue = A1ButtonColors(A1Palette.Blue1, A1Palette.Blue2, A1Palette.BlueDeep, A1Palette.BlueDeep, Color.White, A1Palette.BlueDeep)
    val Purple = A1ButtonColors(A1Palette.Purple1, A1Palette.Purple2, A1Palette.PurpleDeep, A1Palette.PurpleDeep, Color.White, A1Palette.PurpleDeep)
    val Green = A1ButtonColors(A1Palette.Green1, A1Palette.Green2, A1Palette.GreenDeep, A1Palette.GreenDeep, Color.White, A1Palette.GreenDeep)
    val Red = A1ButtonColors(A1Palette.Red1, A1Palette.Red2, A1Palette.RedDeep, A1Palette.RedDeep, Color.White, A1Palette.RedDeep)
    val White = A1ButtonColors(Color.White, Color.White, A1Palette.Line, A1Palette.Line, A1Palette.Ink, null)
    val Disabled = A1ButtonColors(Color(0xFFE3EAF3), Color(0xFFCBD6E4), Color(0xFF8AA0BC), Color(0xFF8AA0BC), Color(0xFF4F6685), null)
}

internal fun a1TextShadow(color: Color?): Shadow? =
    color?.let { Shadow(color = it, offset = Offset(0f, 4f), blurRadius = 0f) }

/** Độ dày viền và độ cao đế chuẩn của A1. */
object A1Dimens {
    val Border: Dp = 3.dp
    val CardDepth: Dp = 5.dp
    val SoftDepth: Dp = 3.dp
    val CardRadius: Dp = 22.dp
    val ButtonRadius: Dp = 18.dp
}

/**
 * Thẻ trắng viền navy dày, có đế đặc phía dưới (thay cho bóng mờ).
 * Đế được vẽ phía sau nên không đổi kích thước bố cục bên trong.
 */
fun Modifier.a1Card(
    radius: Dp = A1Dimens.CardRadius,
    depth: Dp = A1Dimens.CardDepth,
    background: Color = A1Palette.Surface,
    border: Color = A1Palette.Line
): Modifier = this
    .padding(bottom = depth)
    .drawBehind {
        val r = radius.toPx()
        drawRoundRect(border, topLeft = Offset(0f, depth.toPx()), size = size, cornerRadius = CornerRadius(r, r))
    }
    .background(background, RoundedCornerShape(radius))
    .border(A1Dimens.Border, border, RoundedCornerShape(radius))

/**
 * Nền trời A1: dải xanh từ trên xuống và hai ngọn đồi xanh ở chân màn hình.
 * Chỉ là trang trí, không chuyển động, nên an toàn khi người dùng bật giảm chuyển động.
 */
@Composable
fun A1SkyBackdrop(
    modifier: Modifier = Modifier,
    showHills: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to A1Palette.Sky1,
                0.45f to A1Palette.Sky2,
                1f to A1Palette.Sky3
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Tia nắng mờ toả từ phía trên giữa màn hình.
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.28f),
                    radius = size.maxDimension * 0.55f
                )
            )
            if (showHills) {
                val back = Path().apply {
                    moveTo(size.width * 0.35f, size.height)
                    cubicTo(size.width * 0.55f, size.height - 220.dp.toPx(), size.width * 1.05f, size.height - 220.dp.toPx(), size.width * 1.2f, size.height)
                    close()
                }
                drawPath(back, A1Palette.HillDark)
                val front = Path().apply {
                    moveTo(-size.width * 0.15f, size.height)
                    cubicTo(size.width * 0.05f, size.height - 200.dp.toPx(), size.width * 0.75f, size.height - 200.dp.toPx(), size.width * 1.05f, size.height)
                    close()
                }
                drawPath(front, A1Palette.Hill)
            }
        }
        content()
    }
}

/** Đặt một khối đế đặc phía dưới [content] — dùng cho nút 3D khi nhấn thì mặt nút hạ xuống. */
@Composable
internal fun A1RaisedSurface(
    colors: A1ButtonColors,
    radius: Dp,
    depth: Dp,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    /** Vệt sáng lướt qua mặt nút (".shine" của thiết kế) — dùng cho CTA chính. */
    shine: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    // Độ bóng: dải sáng ở mép trên trong mặt nút (inset 0 3–4px #ffffff70–a0 của thiết kế).
    val gloss = when (colors) {
        A1ButtonDefaults.Yellow -> Color.White.copy(alpha = 0.63f) to 4.dp
        A1ButtonDefaults.White, A1ButtonDefaults.Disabled -> null
        else -> Color.White.copy(alpha = 0.44f) to 3.dp
    }
    val shineProgress = if (shine) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "shine")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(3200, easing = androidx.compose.animation.core.LinearEasing)
            ),
            label = "shineX"
        )
    } else null
    // propagateMinConstraints: mặt nút luôn phủ kín chiều cao tối thiểu (vd. nút CHƠI NGAY cao 72dp).
    Box(
        propagateMinConstraints = true,
        modifier = modifier
            .padding(bottom = depth)
            .drawBehind {
                val r = radius.toPx()
                drawRoundRect(colors.base, topLeft = Offset(0f, depth.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(r, r))
            }
    ) {
        // Chỉ lấp chiều ngang; chiều cao theo nội dung (tối thiểu theo min của cha nhờ propagateMinConstraints).
        // Không dùng fillMaxSize: trong hộp thoại (chiều cao có giới hạn) nút sẽ giãn hết cỡ.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = if (pressed) depth else 0.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
                .drawWithContent {
                    gloss?.let { (color, h) ->
                        drawRect(color, topLeft = Offset(0f, A1Dimens.Border.toPx()), size = Size(size.width, h.toPx()))
                    }
                    shineProgress?.value?.let { p ->
                        // 0–60% đứng chờ ở mép trái, 60–100% lướt sang phải (như keyframes "shine").
                        val travel = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        val bandW = size.width * 0.4f
                        val x = -size.width * 0.6f + travel * size.width * 1.9f
                        val skew = size.height * 0.36f
                        val band = Path().apply {
                            moveTo(x + skew, 0f); lineTo(x + skew + bandW, 0f)
                            lineTo(x + bandW, size.height); lineTo(x, size.height); close()
                        }
                        drawPath(
                            band,
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.56f), Color.Transparent),
                                startX = x, endX = x + bandW + skew
                            )
                        )
                    }
                    drawContent()
                }
                .border(A1Dimens.Border, colors.border, shape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
            content = content
        )
    }
}

/** Đồng Vàng A1: đồng xu vàng viền nâu có ngôi sao. Trang trí, không có ngữ nghĩa trợ năng. */
@Composable
fun A1CoinIcon(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(r, r)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFF6B0), Color(0xFFFFD23F), Color(0xFFE6A100), Color(0xFFB97800)),
                center = Offset(r * 0.7f, r * 0.6f), radius = r * 1.3f
            ), r, c
        )
        drawCircle(Color(0xFF7A4D00), r - r * 0.08f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.16f))
        drawCircle(Color(0xFFC98700), r * 0.66f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.1f))
        val star = Path()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r * 0.46f else r * 0.2f
            val a = (-90.0 + i * 36.0) * kotlin.math.PI / 180.0
            val p = Offset(c.x + (rad * kotlin.math.cos(a)).toFloat(), c.y + (rad * kotlin.math.sin(a)).toFloat())
            if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        star.close()
        drawPath(star, Color(0xFFFFF7CC))
    }
}

/** Gem A1: viên kim cương xanh lá viền đậm. Trang trí, không có ngữ nghĩa trợ năng. */
@Composable
fun A1GemIcon(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height * 0.92f; val top = this.size.height * 0.04f
        val gem = Path().apply {
            moveTo(w * 0.22f, top); lineTo(w * 0.78f, top); lineTo(w, top + h * 0.34f)
            lineTo(w * 0.5f, top + h); lineTo(0f, top + h * 0.34f); close()
        }
        drawPath(gem, Brush.linearGradient(listOf(Color(0xFFE3FFE9), Color(0xFF5FE08A), Color(0xFF16A34A), Color(0xFF0E7A36))))
        drawPath(gem, Color(0xFF0B4D24), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.08f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        // Mặt cắt sáng phía trên như ".gem::after" của thiết kế.
        val facet = Path().apply {
            moveTo(w * 0.22f, top); lineTo(w * 0.78f, top); lineTo(w, top + h * 0.34f); lineTo(0f, top + h * 0.34f); close()
        }
        drawPath(facet, Color.White.copy(alpha = 0.5f))
        drawPath(gem, Color(0xFF0B4D24), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.08f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Chữ trắng có viền navy (kiểu "on-sky" của A1) để đọc được trên nền trời sáng.
 * Lớp viền chỉ là hình vẽ; trình đọc màn hình chỉ đọc một lần.
 */
@Composable
fun A1OutlinedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    fill: Color = Color.White,
    outline: Color = A1Palette.Line,
    outlineWidth: Dp = 3.dp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier, contentAlignment = if (textAlign == androidx.compose.ui.text.style.TextAlign.Center) androidx.compose.ui.Alignment.Center else androidx.compose.ui.Alignment.TopStart) {
        androidx.compose.material3.Text(
            text,
            style = style.copy(
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = with(density) { outlineWidth.toPx() },
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            ),
            color = outline,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            modifier = Modifier.clearAndSetSemantics { }
        )
        androidx.compose.material3.Text(text, style = style, color = fill, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
    }
}

/**
 * Huy hiệu số của A1 (".badge"): tối thiểu 20dp, nền hồng đỏ (hoặc [color]), viền trắng 2dp,
 * chữ Baloo đậm 11sp. Trình đọc màn hình đọc mô tả của phần tử cha, nên huy hiệu không có ngữ nghĩa.
 */
@Composable
fun A1Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF3B5C),
    /** false: trình đọc màn hình đọc số (vd. số thông báo chưa đọc trên nút chuông). */
    decorative: Boolean = true
) {
    Box(
        modifier = modifier
            .then(if (decorative) Modifier.clearAndSetSemantics { } else Modifier)
            .heightIn(min = 20.dp)
            .widthIn(min = 20.dp)
            .background(color, RoundedCornerShape(10.dp))
            .border(2.dp, Color.White, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                fontFamily = androidx.compose.material3.MaterialTheme.typography.titleSmall.fontFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            color = Color.White,
            maxLines = 1
        )
    }
}
