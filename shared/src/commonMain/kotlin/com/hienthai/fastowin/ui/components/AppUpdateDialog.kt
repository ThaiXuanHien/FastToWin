package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.ui.theme.ArcadePalette

@Composable
fun UpdateAvailableDialog(
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    ArcadeDialog(
        title = "CẬP NHẬT GAME",
        subtitle = "Phiên bản mới đã sẵn sàng",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(ArcadePalette.Blue700, ArcadePalette.Violet600)
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = ArcadePalette.Blue300.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(18.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFE46D), ArcadePalette.Gold500)
                            ),
                            RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = ArcadePalette.Navy900,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "SẴN SÀNG TĂNG TỐC?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Nhận bản sửa lỗi và cải tiến mới nhất.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDDE8FF)
                    )
                }
                Spacer(Modifier.width(14.dp))
            }

            ArcadePanel(
                modifier = Modifier.fillMaxWidth(),
                accent = ArcadePalette.Mint400
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ArcadePalette.Mint400,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Cập nhật nhanh, tự tải lại",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tài khoản và tiến trình của bạn vẫn được giữ nguyên.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA9BADC)
                        )
                    }
                }
            }

            ArcadeActionButton(
                label = "CẬP NHẬT NGAY",
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Refresh,
                style = ArcadeActionStyle.GOLD
            )
            ArcadeActionButton(
                label = "ĐỂ SAU",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }
}
