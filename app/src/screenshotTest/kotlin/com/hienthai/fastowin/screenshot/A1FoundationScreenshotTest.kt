package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.ArcadeChoiceChip
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeRankBadge
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl

/** Bảng thành phần nền A1 "Ngày nắng": nút 3D, thẻ, tab chọn, chip, huy hiệu hạng trên nền trời. */
@PreviewTest
@Preview(name = "a1_foundation", widthDp = 390, heightDp = 980)
@Preview(name = "a1_foundation_small_large_font", widthDp = 320, heightDp = 1400, fontScale = 1.6f)
@Composable
fun a1Foundation() = ArcadeScreenshotFrame {
    ArcadeBackdrop(Modifier.fillMaxSize(), showHills = true) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ArcadeActionButton("CHƠI NGAY", onClick = {}, style = ArcadeActionStyle.GOLD, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArcadeActionButton("Tạo phòng", onClick = {}, style = ArcadeActionStyle.SPECIAL, modifier = Modifier.weight(1f))
                ArcadeActionButton("Vào phòng", onClick = {}, style = ArcadeActionStyle.PRIMARY, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArcadeActionButton("Nhận", onClick = {}, style = ArcadeActionStyle.SUCCESS, compact = true, modifier = Modifier.weight(1f))
                ArcadeActionButton("Rời bang", onClick = {}, style = ArcadeActionStyle.DANGER, compact = true, modifier = Modifier.weight(1f))
                ArcadeActionButton("Huỷ", onClick = {}, style = ArcadeActionStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f))
            }
            ArcadeActionButton("Hết lượt", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            ArcadePanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Thẻ A1", style = MaterialTheme.typography.titleLarge)
                    Text("Thẻ trắng, viền navy 3dp và đế đặc phía dưới.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ArcadeSegmentedControl(listOf("Khung", "Danh hiệu"), selectedIndex = 0, onSelected = {})
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArcadeChoiceChip("Tất cả", selected = true, onClick = {})
                        ArcadeChoiceChip("Thắng", selected = false, onClick = {})
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArcadeRankBadge(1); ArcadeRankBadge(2); ArcadeRankBadge(3); ArcadeRankBadge(4)
                    }
                }
            }
        }
    }
}
