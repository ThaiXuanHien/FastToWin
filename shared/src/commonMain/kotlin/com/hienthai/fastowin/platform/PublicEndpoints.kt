package com.hienthai.fastowin.platform

import com.hienthai.fastowin.localization.AppLanguage

/**
 * APP-01: public production addresses, generated from FASTTOWIN_PUBLIC_HOST in
 * gradle.properties. Moving to a custom domain changes only that value.
 */
object PublicEndpoints {
    val host: String get() = GENERATED_PUBLIC_HOST
    val webBase: String get() = "https://$host"
    val productionGameServerUrl: String get() = "wss://$host/game"

    /**
     * Hosts earlier releases connected to. Sessions are stored per server URL, so after a
     * domain move the app carries them over instead of signing every player out. Append the
     * old host here when changing FASTTOWIN_PUBLIC_HOST; never remove entries.
     */
    private val previousHosts = listOf("fasttowin.up.railway.app")

    val previousGameServerUrls: List<String> get() = previousHosts.filter { it != host }.map { "wss://$it/game" }

    /**
     * Store page for an app update (APP-02), or null when the host updates itself (web reload).
     * iOS has no App Store listing yet, so it opens the public site until one exists.
     */
    fun updateUrl(devicePlatform: String): String? = when (devicePlatform) {
        "android" -> "https://play.google.com/store/apps/details?id=com.hienthai.fastowin"
        "ios" -> webBase
        else -> null
    }

    /** Legal pages: privacy, terms or delete-account; English pages live under /en. */
    fun legalUrl(path: String, language: AppLanguage): String =
        "$webBase/$path" + if (language == AppLanguage.VIETNAMESE) "" else "/en"
}
