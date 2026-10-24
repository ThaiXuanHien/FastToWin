package com.hienthai.fastowin.platform

import android.content.Context
import java.security.MessageDigest

/** Local-only account namespaces contain no access tokens, raw account IDs, nonce, or birthday. */
internal class AndroidRewardedAdStorage(context: Context) : PendingRewardedAdStore {
    private val preferences = context.applicationContext.getSharedPreferences("rewarded_ad_state", Context.MODE_PRIVATE)
    private fun key(owner: String) = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }
    override fun read(ownerId: String) = preferences.getString("pending_${key(ownerId)}", null)
    override fun write(ownerId: String, sessionId: String?) {
        // Persist before showing: a killed process can recover the server-authoritative outcome.
        check(preferences.edit().putString("pending_${key(ownerId)}", sessionId).commit())
    }
    fun age(ownerId: String): RewardedAdAgeBand = preferences.getString("age_${key(ownerId)}", null)
        ?.let { value -> RewardedAdAgeBand.entries.firstOrNull { it.name == value } } ?: RewardedAdAgeBand.UNKNOWN
    fun age(ownerId: String, band: RewardedAdAgeBand) { check(preferences.edit().putString("age_${key(ownerId)}", band.name).commit()) }
}
