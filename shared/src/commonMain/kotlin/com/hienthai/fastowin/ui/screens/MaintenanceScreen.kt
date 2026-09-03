package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

/**
 * Full-screen lock shown only after the server explicitly reports planned
 * maintenance. Connection failures are not a maintenance signal.
 */
@Composable
fun MaintenanceScreen(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(enabled = true, onBack = {})

    ResponsiveScreen(
        modifier = modifier.fillMaxSize().testTag("maintenance_screen"),
        maxContentWidth = 520.dp
    ) { contentModifier ->
        BoxWithConstraints(modifier = contentModifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = ArcadePalette.Gold500.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, ArcadePalette.Gold500.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Build,
                        contentDescription = null,
                        tint = ArcadePalette.Gold500,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "ĐANG BẢO TRÌ",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcadePalette.Gold500,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            MaintenanceIllustration()
            Spacer(Modifier.height(22.dp))

            Text(
                text = "MÁY CHỦ ĐANG\nNGHỈ GIỮA HIỆP",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = message?.takeIf { it.isNotBlank() }
                    ?: "Đội kỹ thuật đang nâng cấp đấu trường. Quá trình này có thể kéo dài vài giờ.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = ArcadePalette.Blue100.copy(alpha = 0.86f),
                textAlign = TextAlign.Center
            )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = ArcadePalette.Navy800.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, ArcadePalette.OutlineDark)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = ArcadePalette.Mint400,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Hệ thống sẽ tự mở lại khi hoàn tất",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Bạn không cần thao tác, đăng xuất hoặc cài lại ứng dụng.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ArcadePalette.Blue100.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceIllustration() {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .size(176.dp)
            .background(
                Brush.linearGradient(
                    listOf(ArcadePalette.Blue700, ArcadePalette.Violet600, ArcadePalette.Coral600)
                ),
                shape
            )
            .border(2.dp, ArcadePalette.Blue300.copy(alpha = 0.72f), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .rotate(-4f)
                .background(ArcadePalette.Navy900, RoundedCornerShape(24.dp))
                .border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                repeat(3) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(ArcadePalette.Navy700, RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    if (index == 1) ArcadePalette.Gold500 else ArcadePalette.Mint400,
                                    CircleShape
                                )
                        )
                        Box(
                            Modifier
                                .height(4.dp)
                                .weight(1f)
                                .background(ArcadePalette.Blue300.copy(alpha = 0.6f), CircleShape)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(46.dp)
                .rotate(9f)
                .background(ArcadePalette.Gold500, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = null,
                tint = ArcadePalette.Navy900,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
