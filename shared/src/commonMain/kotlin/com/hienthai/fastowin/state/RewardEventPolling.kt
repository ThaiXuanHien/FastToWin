package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.AppLanguage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Both children inherit the route effect's lifetime. HTTP reads remain sequential,
// while the clock can expire the retained banner during a suspended request.
internal suspend fun runRewardEventPolling(
    controller: RewardEventController,
    account: String?,
    language: AppLanguage,
    view: RewardEventView,
    selectedId: String?
) = coroutineScope {
    var crossedBoundary = false
    launch {
        while (isActive) {
            delay(1000)
            val before = controller.state.value.items.map { it.state }
            controller.tick()
            if (before != controller.state.value.items.map { it.state }) crossedBoundary = true
        }
    }
    controller.enter(account, language, view)
    selectedId?.let { controller.select(it) }
    var ticks = 0
    while (isActive) {
        delay(1000)
        ticks++
        if (ticks >= 30 || crossedBoundary) {
            ticks = 0
            crossedBoundary = false
            selectedId?.let { controller.select(it) } ?: controller.refresh()
        }
    }
}
