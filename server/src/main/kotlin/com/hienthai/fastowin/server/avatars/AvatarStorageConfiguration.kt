package com.hienthai.fastowin.server.avatars

enum class AvatarStorageMode { DATABASE, R2 }

// Credential-bearing configuration intentionally has no generated toString/copy/component functions.
class R2AvatarConfiguration(val accountId: String, val bucket: String, val accessKeyId: String, val secretAccessKey: String) {
    override fun toString() = "R2AvatarConfiguration([redacted])"
}
class AvatarStorageConfiguration(val mode: AvatarStorageMode, val r2: R2AvatarConfiguration?) {
    fun validateDatabase(configured: Boolean) {
        require(mode != AvatarStorageMode.R2 || configured) { "R2 avatar storage requires a persistent database." }
    }
    override fun toString() = "AvatarStorageConfiguration(mode=$mode)"
    companion object {
        fun fromEnvironment(values: Map<String, String> = System.getenv()): AvatarStorageConfiguration {
            val mode = when (values["FASTTOWIN_AVATAR_STORAGE"] ?: "database") {
                "database" -> AvatarStorageMode.DATABASE
                "r2" -> AvatarStorageMode.R2
                else -> throw IllegalArgumentException("FASTTOWIN_AVATAR_STORAGE must be database or r2.")
            }
            val names = listOf("ACCOUNT_ID", "BUCKET", "ACCESS_KEY_ID", "SECRET_ACCESS_KEY")
            val supplied = names.map { values["FASTTOWIN_AVATAR_R2_$it"] }
            if (mode == AvatarStorageMode.DATABASE && supplied.all { it == null }) return AvatarStorageConfiguration(mode, null)
            require(supplied.all { !it.isNullOrBlank() }) { "Complete FASTTOWIN_AVATAR_R2 configuration is required." }
            val (account, bucket, access, secret) = supplied.map { it!! }
            require(account.matches(Regex("[0-9a-fA-F]{32}"))) { "Invalid FASTTOWIN_AVATAR_R2_ACCOUNT_ID." }
            require(bucket.length in 3..63 && bucket.matches(Regex("[a-z0-9][a-z0-9-]*[a-z0-9]"))) {
                "Invalid FASTTOWIN_AVATAR_R2_BUCKET."
            }
            require(access.matches(Regex("[0-9a-fA-F]{32}"))) { "Invalid FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID." }
            require(secret.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY." }
            return AvatarStorageConfiguration(mode, R2AvatarConfiguration(account, bucket, access, secret))
        }
    }
}
