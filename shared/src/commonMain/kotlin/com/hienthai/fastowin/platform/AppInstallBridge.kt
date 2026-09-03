package com.hienthai.fastowin.platform

enum class AppInstallStatus {
    UNSUPPORTED,
    MANUAL,
    AVAILABLE,
    INSTALLING,
    INSTALLED,
    ERROR
}

/** Host integration for installing the web app as a PWA. */
interface AppInstallBridge {
    val status: AppInstallStatus

    fun observe(onStatusChanged: (AppInstallStatus) -> Unit): () -> Unit

    fun install()
}

object NoOpAppInstallBridge : AppInstallBridge {
    override val status: AppInstallStatus = AppInstallStatus.UNSUPPORTED

    override fun observe(onStatusChanged: (AppInstallStatus) -> Unit): () -> Unit = {}

    override fun install() = Unit
}
