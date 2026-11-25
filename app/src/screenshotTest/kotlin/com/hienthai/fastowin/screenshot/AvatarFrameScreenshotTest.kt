package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.ui.components.PlayerAvatar

private val allFrames = listOf(
    "default", "bronze", "silver", "gold", "platinum", "diamond", "master", "perfect",
    "persistent", "lightning", "wildfire", "warrior", "veteran", "challenger", "glory", "unyielding",
    "legend", "emperor", "speed_shadow", "champion", "immortal", "dragon_might", "supreme", "peerless"
)

/** Kiểm tra avatar khớp lỗ của cả 24 khung (không lệch, không hở viền). */
@OptIn(ExperimentalLayoutApi::class)
@PreviewTest
@Preview(name = "all_frames", widthDp = 420, heightDp = 640)
@Composable
fun avatarFramesFitTheirOpenings() = ArcadeScreenshotFrame {
    FlowRow(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        allFrames.forEachIndexed { i, id ->
            PlayerAvatar(displayName = "H", avatarId = if (i % 2 == 0) "bolt" else "target", frameId = "frame_$id", size = 88.dp)
        }
    }
}
