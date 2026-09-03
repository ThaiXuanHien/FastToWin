package com.hienthai.fastowin.platform

enum class AppPushStatus {
    UNSUPPORTED,
    UNCONFIGURED,
    PROMPT,
    REQUESTING,
    ENABLED,
    DISABLED,
    DENIED,
    ERROR
}

/** Host integration for device push notifications. */
interface AppPushBridge {
    val status: AppPushStatus

    fun observe(
        onStatusChanged: (AppPushStatus) -> Unit,
        onTokenChanged: (String) -> Unit
    ): () -> Unit

    fun enable()

    fun disable()
}

object NoOpAppPushBridge : AppPushBridge {
    override val status: AppPushStatus = AppPushStatus.UNSUPPORTED

    override fun observe(
        onStatusChanged: (AppPushStatus) -> Unit,
        onTokenChanged: (String) -> Unit
    ): () -> Unit = {}

    override fun enable() = Unit

    override fun disable() = Unit
}
