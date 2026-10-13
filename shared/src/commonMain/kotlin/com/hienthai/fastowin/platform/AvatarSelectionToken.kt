package com.hienthai.fastowin.platform

internal class AvatarSelectionToken {
    private var generation = 0L

    fun next(): Long = ++generation

    fun isCurrent(token: Long): Boolean = token == generation

    fun invalidate() {
        generation++
    }
}
