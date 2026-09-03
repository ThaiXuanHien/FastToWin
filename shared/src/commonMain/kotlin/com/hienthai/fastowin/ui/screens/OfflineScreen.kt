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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

/**
 * Shown when the service health endpoint cannot be reached. Unlike maintenance,
 * this state remains actionable and lets the player continue with offline practice.
 */
@Composable
fun OfflineScreen(
    onRetry: () -> Unit,
    onPractice: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ResponsiveScreen(
        modifier = modifier.fillMaxSize().testTag("offline_screen"),
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
                color = ArcadePalette.Coral400.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ArcadePalette.Coral400.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = ArcadePalette.Coral400,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "OFFLINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcadePalette.Coral400,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            OfflineIllustration()
            Spacer(Modifier.height(22.dp))

            Text(
                text = "TẠM MẤT\nKẾT NỐI",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Các trận online đang tạm dừng. Game sẽ tự kết nối lại khi mạng ổn định.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = ArcadePalette.Blue100.copy(alpha = 0.86f),
                textAlign = TextAlign.Center
            )

            ArcadePanel(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                accent = ArcadePalette.Blue300
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = ArcadePalette.Blue300,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Kiểm tra Wi-Fi hoặc dữ liệu di động",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Đây là lỗi kết nối, không phải máy chủ đang bảo trì.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ArcadePalette.Blue100.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            ArcadeActionButton(
                label = "THỬ KẾT NỐI LẠI",
                onClick = onRetry,
                icon = Icons.Rounded.Refresh,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = ArcadeActionStyle.PRIMARY
            )
                onPractice?.let {
                    ArcadeActionButton(
                        label = "LUYỆN TẬP OFFLINE",
                        onClick = it,
                        icon = Icons.Rounded.SportsEsports,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = ArcadeActionStyle.OUTLINE
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineIllustration() {
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
        Surface(
            modifier = Modifier.size(112.dp),
            shape = CircleShape,
            color = ArcadePalette.Navy900,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.WifiOff,
                    contentDescription = "Không có kết nối mạng",
                    tint = ArcadePalette.Coral400,
                    modifier = Modifier.size(58.dp)
                )
            }
        }
    }
}
