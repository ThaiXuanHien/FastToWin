package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .padding(bottom = depth)
            .drawBehind {
                val r = radius.toPx()
                drawRoundRect(colors.base, topLeft = Offset(0f, depth.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(r, r))
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (pressed) depth else 0.dp)
                .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)), shape)
                .border(A1Dimens.Border, colors.border, shape),
            content = content
        )
    }
}
