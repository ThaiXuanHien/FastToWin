package com.hienthai.fastowin.server.ads

/** A single owned Android unit: its numeric suffix is Google's documented callback representation. */
data class AdMobConfiguration(val androidAdUnitId: String) {
    init { require(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(androidAdUnitId)) { "Invalid Android rewarded ad unit." } }
    fun acceptsUnit(unit: String) = unit == androidAdUnitId || unit == androidAdUnitId.substringAfter('/')
    companion object {
        fun fromEnvironment(environment: String, databaseAvailable: Boolean, values: Map<String, String> = System.getenv()): AdMobConfiguration? {
            val enabled = values["FASTTOWIN_ADMOB_ENABLED"] ?: "false"
            require(enabled in setOf("true", "false")) { "FASTTOWIN_ADMOB_ENABLED must be true or false." }
            if (enabled == "false") return null
            require(environment != "prod" || databaseAvailable) { "AdMob production requires PostgreSQL." }
            return AdMobConfiguration(requireNotNull(values["FASTTOWIN_ADMOB_ANDROID_AD_UNIT_ID"]) { "AdMob Android unit is required." })
        }
    }
}
class AdMobInvalidRequest : RuntimeException()
class AdMobUnavailable : RuntimeException()
class AdMobTooManySessions : RuntimeException()
