package com.hienthai.fastowin.platform

/**
 * Connects the shared UI to update facilities supplied by the current host.
 * Native hosts currently use the no-op implementation; the web host delegates
 * to its service worker.
 */
interface AppUpdateBridge {
    val updateAvailable: Boolean

    fun observe(onUpdateAvailable: () -> Unit): () -> Unit

    fun applyUpdate()

    fun dismissUpdate()
}

object NoOpAppUpdateBridge : AppUpdateBridge {
    override val updateAvailable: Boolean = false

    override fun observe(onUpdateAvailable: () -> Unit): () -> Unit = {}

    override fun applyUpdate() = Unit

    override fun dismissUpdate() = Unit
}
