package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.platformRefreshInput

/**
 * Keeps native pull-to-refresh on touch devices while providing a visible action for pointer input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastToWinPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val refreshInput = remember { platformRefreshInput() }
    if (refreshInput.touchPullEnabled) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            content = content
        )
        return
    }

    Column(modifier = modifier) {
        if (refreshInput.pointerRefreshEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                ArcadeActionButton(
                    label = localized(TextKey.Retry),
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    compact = true,
                    modifier = Modifier
                        .widthIn(min = 44.dp, max = 160.dp)
                        .testTag(if (isRefreshing) "pointer_refresh_busy" else "pointer_refresh"),
                    style = ArcadeActionStyle.OUTLINE
                )
            }
        }
        Box(modifier = Modifier.weight(1f), content = content)
    }
}
