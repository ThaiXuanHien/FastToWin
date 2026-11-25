package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hienthai.fastowin.ui.theme.A1Palette

/** Biến thể màu của nhãn A1 (".tag.y/.b/.g/.r/.k/.p" trong thiết kế). */
enum class A1TagTone(val background: Color, val content: Color, val border: Color) {
    YELLOW(Color(0xFFFFF3C4), Color(0xFF7A4D00), Color(0xFFE0A100)),
    BLUE(Color(0xFFDCEBFF), Color(0xFF0D3A7A), Color(0xFF6AA4F0)),
    GREEN(Color(0xFFDFF8D0), Color(0xFF1F5A10), Color(0xFF6CC24A)),
    RED(Color(0xFFFFE0E0), Color(0xFF8A1A22), Color(0xFFF08A8A)),
    PURPLE(Color(0xFFECE3FF), Color(0xFF3A1A8A), Color(0xFFA98AF0)),
    INK(A1Palette.Ink, Color.White, A1Palette.Ink)
}

/** Nhãn nhỏ bo tròn, chữ Baloo đậm 12–13sp, viền 2dp (".tag" của thiết kế). */
@Composable
fun A1Tag(
    text: String,
    tone: A1TagTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .background(tone.background, RoundedCornerShape(11.dp))
            .border(2.dp, tone.border, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = tone.content, modifier = Modifier.size(14.dp)) }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
            fontWeight = FontWeight.ExtraBold,
            color = tone.content,
            maxLines = 1
        )
    }
}

/** Màu ô icon nổi trên đầu hộp thoại (tím cho nhập mã, xanh cho mật khẩu…). */
enum class A1HeroTone(val top: Color, val bottom: Color, val edge: Color) {
    PURPLE(A1Palette.Purple1, A1Palette.Purple2, A1Palette.PurpleDeep),
    BLUE(A1Palette.Blue1, A1Palette.Blue2, A1Palette.BlueDeep),
    YELLOW(A1Palette.Yellow1, A1Palette.Yellow3, A1Palette.YellowDeep),
    GREEN(A1Palette.Green1, A1Palette.Green2, A1Palette.GreenDeep),
    RED(A1Palette.Red1, A1Palette.Red2, A1Palette.RedDeep)
}

/** Ô icon 84dp bo 26dp, dải màu + viền + đế, đặt nhô lên mép trên hộp thoại. */
@Composable
fun A1HeroIcon(icon: ImageVector, tone: A1HeroTone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 84.dp, height = 89.dp)
            .a1Card(radius = 26.dp, depth = 5.dp, background = Color.Transparent, border = tone.edge)
            .background(Brush.verticalGradient(listOf(tone.top, tone.bottom)), RoundedCornerShape(26.dp))
            .border(3.dp, tone.edge, RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
    }
}

/**
 * Bảng trượt từ đáy màn hình (".sheet" của thiết kế): nền trắng, bo góc trên 28dp, viền navy,
 * thanh kéo ở giữa, nội dung cuộn được và luôn nằm trên bàn phím.
 */
@Composable
fun A1BottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = arcadeDialogProperties()) {
        NativeDialogInputScope {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight - 40.dp)
                        // Phủ trắng xuống cả vùng thanh điều hướng bên dưới (vùng đó nằm ngoài khung đã chừa inset).
                        .drawBehind { drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1f), size = androidx.compose.ui.geometry.Size(size.width, 200.dp.toPx())) }
                        .background(Color.White, shape)
                        .imePadding()
                        .testTag("a1_sheet")
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 6.dp)
                            .width(44.dp)
                            .heightIn(min = 5.dp)
                            .background(A1Palette.SurfaceAlt2, RoundedCornerShape(3.dp))
                    )
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, color = A1Palette.Ink)
                        content()
                    }
                }
            }
        }
    }
}

/** Nhãn ô nhập liệu đặt phía trên ô (".field label" của thiết kế). */
@Composable
fun A1FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
        fontWeight = FontWeight.Bold,
        color = A1Palette.Ink,
        modifier = modifier.padding(bottom = 6.dp)
    )
}
