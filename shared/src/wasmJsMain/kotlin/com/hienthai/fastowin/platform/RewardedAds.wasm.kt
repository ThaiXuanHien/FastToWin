package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
actual fun rememberRewardedAdGateway(): RewardedAdGateway {
    val gateway = remember { DefaultRewardedAdGateway(isWeb = true) }
    DisposableEffect(gateway) {
        onDispose(gateway::close)
    }
    return gateway
}
