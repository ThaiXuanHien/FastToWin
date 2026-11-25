package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.ui.components.clanEmblemImageVector
import com.hienthai.fastowin.ui.theme.A1Palette

/** Hình khiên của emblem bang (".shield" trong thiết kế): đầu phẳng bo góc, đáy nhọn. */
internal val ClanShieldShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w * 0.96f, h * 0.15f)
    quadraticTo(w, h * 0.17f, w, h * 0.22f)
    lineTo(w, h * 0.56f)
    quadraticTo(w * 0.98f, h * 0.70f, w * 0.5f, h)
    quadraticTo(w * 0.02f, h * 0.70f, 0f, h * 0.56f)
    lineTo(0f, h * 0.22f)
    quadraticTo(0f, h * 0.17f, w * 0.04f, h * 0.15f)
    close()
}

/** Màu khiên theo biểu tượng bang: [đỉnh, đáy, màu icon]. */
internal fun clanShieldColors(logoId: String): Triple<Color, Color, Color> = when (logoId) {
    "sword" -> Triple(Color(0xFF8FC7FF), A1Palette.Blue2, Color.White)
    "flag" -> Triple(Color(0xFFFF9A8A), A1Palette.Red2, Color.White)
    "dragon", "wolf" -> Triple(A1Palette.Purple1, A1Palette.Purple2, Color.White)
    "eagle", "lion" -> Triple(Color(0xFFFFE38A), Color(0xFFE0A100), Color(0xFF7A3D00))
    "crown" -> Triple(Color(0xFFFF9A8A), A1Palette.Red2, Color.White)
    "phoenix" -> Triple(Color(0xFFFFC28A), Color(0xFFF07A2A), Color.White)
    "kirin" -> Triple(Color(0xFF9BEFC4), Color(0xFF2DB37A), Color.White)
    else -> Triple(Color(0xFF8FC7FF), A1Palette.Blue2, Color.White)
}

/** Emblem bang hình khiên dải màu với icon ở giữa (dùng cho cả danh sách, chi tiết và bảng xếp hạng). */
@Composable
internal fun ClanShieldEmblem(logoId: String?, modifier: Modifier = Modifier) {
    val normalized = logoId.orEmpty().lowercase().ifBlank { "shield" }
    val (top, bottom, iconTint) = clanShieldColors(normalized)
    Box(
        modifier = modifier
            .clip(ClanShieldShape)
            .background(Brush.verticalGradient(listOf(top, bottom)), ClanShieldShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            clanEmblemImageVector(normalized),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.fillMaxSize(0.5f).padding(bottom = 2.dp)
        )
    }
}

/** Nhãn chức vụ thành viên: Bang chủ (navy), Phó bang (tím), Thành viên (xanh). */
@Composable
internal fun ClanRoleTag(role: ClanRole, label: String, modifier: Modifier = Modifier) {
    val (background, content, border) = when (role) {
        ClanRole.LEADER -> Triple(A1Palette.Ink, Color.White, A1Palette.Ink)
        ClanRole.CO_LEADER -> Triple(Color(0xFFECE3FF), Color(0xFF3A1A8A), Color(0xFFA98AF0))
        ClanRole.MEMBER -> Triple(Color(0xFFDCEBFF), Color(0xFF0D3A7A), Color(0xFF6AA4F0))
    }
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(12.dp))
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
            fontWeight = FontWeight.ExtraBold,
            color = content,
            maxLines = 1
        )
    }
}

/** Thanh tiến độ A1 (".bar"): viền navy 2dp, nền xanh nhạt, phần đầy theo [fill]. */
@Composable
internal fun A1ProgressBar(progress: Float, fill: List<Color>, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(A1Palette.SurfaceAlt2, RoundedCornerShape(height / 2))
            .border(2.dp, A1Palette.Line, RoundedCornerShape(height / 2))
            .padding(2.dp)
            .drawBehind {
                val w = size.width * progress.coerceIn(0f, 1f)
                if (w > 0f) {
                    drawRoundRect(Brush.verticalGradient(fill), size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
                    drawRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(size.height / 2, 0f), size = Size((w - size.height).coerceAtLeast(0f), 3.dp.toPx().coerceAtMost(size.height / 3)))
                }
            }
    )
}

/** Ô số liệu (".card.soft"): số Baloo lớn + nhãn muted. */
@Composable
internal fun ClanStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Transparent)
            .padding(bottom = 3.dp)
            .drawBehind {
                val r = 18.dp.toPx()
                drawRoundRect(A1Palette.Line, topLeft = Offset(0f, 3.dp.toPx()), size = size, cornerRadius = CornerRadius(r, r))
            }
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(3.dp, A1Palette.Line, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp), color = A1Palette.Ink, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            color = A1Palette.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Nút ba chấm mở menu thao tác thêm (báo cáo bang, đuổi thành viên…). */
@Composable
internal fun ClanOverflowMenu(
    contentDescription: String,
    modifier: Modifier = Modifier,
    buttonTag: String? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // Nút có vùng chạm riêng (48dp) để không bị gộp vào dòng bấm được bên ngoài.
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(44.dp).then(if (buttonTag != null) Modifier.testTag(buttonTag) else Modifier)
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = contentDescription, tint = A1Palette.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { content { open = false } }
    }
}

@Composable
internal fun ClanSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = A1Palette.Ink)
}
