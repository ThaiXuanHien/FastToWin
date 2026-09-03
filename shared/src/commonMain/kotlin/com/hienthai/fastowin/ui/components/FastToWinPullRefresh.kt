package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hienthai.fastowin.platform.supportsTouchPullToRefresh

/**
 * Keeps native pull-to-refresh on touch devices while avoiding accidental refresh gestures from
 * a desktop browser's mouse wheel or trackpad. Browser reload remains available on non-touch web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastToWinPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gestureEnabled = remember { supportsTouchPullToRefresh() }
    if (gestureEnabled) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            content = content
        )
    } else {
        Box(modifier = modifier, content = content)
    }
}
