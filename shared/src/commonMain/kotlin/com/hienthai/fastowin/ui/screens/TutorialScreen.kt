package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

private data class TutorialPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val hint: String
)

private val tutorialPages = listOf(
    TutorialPage(
        icon = Icons.Rounded.LooksOne,
        title = "Tìm số thật nhanh",
        description = "Quan sát bàn 50 số và chạm lần lượt từ 1 đến 50. Số cần tìm luôn được hiển thị phía trên bàn.",
        hint = "Chạm sai không làm mất lượt, nhưng được ghi vào thống kê độ chính xác."
    ),
    TutorialPage(
        icon = Icons.Rounded.Groups,
        title = "Cùng một mục tiêu",
        description = "Hai người chơi luôn nhìn cùng số mục tiêu. Khi một người chọn đúng, số đó bị khóa ở cả hai máy và mục tiêu chuyển sang số tiếp theo.",
        hint = "Người phản ứng nhanh hơn sẽ nhận 10 điểm cho lượt đó."
    ),
    TutorialPage(
        icon = Icons.Rounded.Security,
        title = "Chọn cách bạn muốn chơi",
        description = "Luyện tập hoạt động offline. Trận trong phòng dùng máy chủ để đồng bộ điểm, còn đấu xếp hạng sẽ làm thay đổi Elo.",
        hint = "Bạn có thể xem lại hướng dẫn bất kỳ lúc nào trong Cài đặt."
    )
)

@Composable
fun TutorialScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = tutorialPages[pageIndex]

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ResponsiveScreen(maxContentWidth = 680.dp) { contentModifier ->
            Column(
                modifier = contentModifier
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("HƯỚNG DẪN", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onSkip) { Text("Bỏ qua") }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        modifier = Modifier.size(136.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                page.icon,
                                contentDescription = null,
                                modifier = Modifier.size(68.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            page.hint,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }

                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tutorialPages.indices.forEach { index ->
                        Surface(
                            modifier = Modifier.size(if (index == pageIndex) 24.dp else 8.dp, 8.dp),
                            shape = CircleShape,
                            color = if (index == pageIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ) {}
                    }
                }
                Button(
                    onClick = {
                        if (pageIndex == tutorialPages.lastIndex) onComplete() else pageIndex++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("tutorial_continue"),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        if (pageIndex == tutorialPages.lastIndex) "Bắt đầu chơi" else "Tiếp tục",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
