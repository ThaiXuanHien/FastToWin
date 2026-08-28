package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.ArcadeIconHero
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.launch

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
        description = "Chạm lần lượt từ 1 đến 50. Mục tiêu luôn nổi bật phía trên bàn.",
        hint = "Chạm sai được ghi vào thống kê độ chính xác."
    ),
    TutorialPage(
        icon = Icons.Rounded.Groups,
        title = "Cùng một mục tiêu",
        description = "Hai người cùng nhìn một số. Người nhanh hơn ghi điểm và số bị khóa cả hai bên.",
        hint = "Mỗi lượt đúng nhận 10 điểm."
    ),
    TutorialPage(
        icon = Icons.Rounded.Security,
        title = "Chọn cách bạn muốn chơi",
        description = "Luyện tập offline, đấu thường hoặc xếp hạng ảnh hưởng Elo.",
        hint = "Có thể xem lại hướng dẫn trong Cài đặt."
    )
)

@Composable
fun TutorialScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { tutorialPages.size })
    val scope = rememberCoroutineScope()

    // Vuốt back từ cạnh màn hình:
    // - Nếu đang ở page > 0 thì về page trước
    // - Nếu đang ở page đầu thì bỏ qua hướng dẫn
    SystemBackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onSkip()
        }
    }

    ArcadeBackdrop(modifier = modifier.fillMaxSize()) {
        ResponsiveScreen(maxContentWidth = 680.dp) { contentModifier ->
            Column(
                modifier = contentModifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("HƯỚNG DẪN", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onSkip) { Text("Bỏ qua") }
                }

                // Swipeable pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    val page = tutorialPages[pageIndex]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ArcadeIconHero(
                            kicker = "BƯỚC ${pageIndex + 1} / ${tutorialPages.size}",
                            title = page.title,
                            subtitle = page.description,
                            icon = page.icon,
                            accent = when (pageIndex) {
                                0 -> ArcadePalette.Violet600
                                1 -> ArcadePalette.Coral600
                                else -> ArcadePalette.Mint600
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        ArcadePanel(
                            modifier = Modifier.fillMaxWidth(),
                            accent = ArcadePalette.Gold500
                        ) {
                            Text(
                                page.hint,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Dot indicators
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tutorialPages.indices.forEach { index ->
                        Surface(
                            modifier = Modifier.size(if (index == pagerState.currentPage) 24.dp else 8.dp, 8.dp),
                            shape = CircleShape,
                            color = if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ) {}
                    }
                }

                // Action button - animated label khi đổi page
                AnimatedContent(
                    targetState = pagerState.currentPage == tutorialPages.lastIndex,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally { it / 4 })
                            .togetherWith(fadeOut(tween(150)) + slideOutHorizontally { -it / 4 })
                    },
                    label = "TutorialButtonLabel"
                ) { isLastPage ->
                    ArcadeActionButton(
                        label = if (isLastPage) "BẮT ĐẦU CHƠI" else "TIẾP TỤC",
                        onClick = {
                            if (isLastPage) {
                                onComplete()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tutorial_continue"),
                        style = ArcadeActionStyle.GOLD
                    )
                }
            }
        }
    }
}
