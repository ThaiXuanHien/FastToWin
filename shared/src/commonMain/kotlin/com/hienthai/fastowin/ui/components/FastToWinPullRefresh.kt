package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hienthai.fastowin.platform.platformRefreshInput

/**
 * Keeps native pull-to-refresh on touch devices. Pointer devices expose refresh in the screen header.
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

    Box(modifier = modifier, content = content)
}
