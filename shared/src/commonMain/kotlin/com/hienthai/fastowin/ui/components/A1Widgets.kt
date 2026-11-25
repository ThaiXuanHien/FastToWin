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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
    icon: ImageVector? = null,
    /** Nhãn to (vd. "Cổ điển · Xếp hạng" ở màn đếm ngược: chữ 16sp, lề 14×5). */
    large: Boolean = false
) {
    Row(
        modifier = modifier
            .background(tone.background, RoundedCornerShape(if (large) 14.dp else 11.dp))
            .border(2.dp, tone.border, RoundedCornerShape(if (large) 14.dp else 11.dp))
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 5.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = tone.content, modifier = Modifier.size(14.dp)) }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = if (large) 16.sp else 13.sp, lineHeight = if (large) 20.sp else 17.sp),
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
            val density = androidx.compose.ui.platform.LocalDensity.current
            val dismissThreshold = with(density) { 120.dp.toPx() }
            // Độ kéo xuống hiện tại của bảng; thả tay quá ngưỡng thì đóng, chưa đủ thì bật về.
            var dragOffset by remember { mutableStateOf(0f) }
            val animatedOffset by androidx.compose.animation.core.animateFloatAsState(dragOffset, label = "sheetDrag")
            var dragging by remember { mutableStateOf(false) }
            val settle = {
                dragging = false
                if (dragOffset > dismissThreshold) onDismissRequest() else dragOffset = 0f
            }
            // Kéo nội dung xuống khi đã ở đầu danh sách cũng kéo cả bảng (như bảng trượt của hệ thống).
            val nestedDrag = remember {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                        if (available.y < 0 && dragOffset > 0f) {
                            val used = maxOf(available.y, -dragOffset)
                            dragOffset += used
                            return androidx.compose.ui.geometry.Offset(0f, used)
                        }
                        return androidx.compose.ui.geometry.Offset.Zero
                    }

                    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                        if (available.y > 0 && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                            dragging = true
                            dragOffset += available.y
                            return androidx.compose.ui.geometry.Offset(0f, available.y)
                        }
                        return androidx.compose.ui.geometry.Offset.Zero
                    }

                    override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                        if (dragOffset > 0f) {
                            settle()
                            return available
                        }
                        return androidx.compose.ui.unit.Velocity.Zero
                    }
                }
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    // Bấm ra ngoài bảng (vùng tối phía trên) để đóng.
                    .pointerInput(Unit) { detectTapGestures { onDismissRequest() } },
                contentAlignment = Alignment.BottomCenter
            ) {
                val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight - 40.dp)
                        .graphicsLayer { translationY = if (dragging) dragOffset else animatedOffset }
                        // Chặn chạm xuyên qua bảng (để không bị hiểu là bấm ra ngoài).
                        .pointerInput(Unit) { detectTapGestures { } }
                        // Phủ trắng xuống cả vùng thanh điều hướng bên dưới (vùng đó nằm ngoài khung đã chừa inset).
                        .drawBehind { drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1f), size = androidx.compose.ui.geometry.Size(size.width, 200.dp.toPx())) }
                        .background(Color.White, shape)
                        .imePadding()
                        .nestedScroll(nestedDrag)
                        .testTag("a1_sheet")
                ) {
                    // Thanh kéo + tiêu đề: kéo xuống để đóng.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                                    dragging = true
                                    dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                },
                                onDragStopped = { settle() }
                            )
                            .testTag("a1_sheet_handle")
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp, bottom = 6.dp)
                                .width(44.dp)
                                .heightIn(min = 5.dp)
                                .background(A1Palette.SurfaceAlt2, RoundedCornerShape(3.dp))
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = A1Palette.Ink,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
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
