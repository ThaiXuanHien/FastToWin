package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.state.ServerClock
import kotlinx.coroutines.delay

@Composable
internal fun rememberClockMillis(
    serverClock: ServerClock?, nowMillis: Long? = null, tickMillis: Long = 1_000, deadlineMillis: Long? = null
): Long {
    if (nowMillis != null) return nowMillis
    val clock = serverClock ?: remember { ServerClock(epochMillis()) }
    var current by remember(clock) { mutableLongStateOf(clock.now()) }
    LaunchedEffect(clock, tickMillis, deadlineMillis) {
        while (true) {
            current = clock.now()
            val nextTick = deadlineMillis?.let { minOf(tickMillis, it - current) } ?: tickMillis
            if (nextTick <= 0) break
            delay(nextTick)
        }
    }
    return current
}
