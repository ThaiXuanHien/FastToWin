package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.platform.AppPushStatus

/**
 * Decides when to explain notifications before asking the system for permission.
 * Android 13+ starts with notifications off, so without this most players never hear
 * about room invitations. We ask signed-in players at most twice, at least 3 days apart,
 * and only while the system prompt can still be shown.
 */
object PushOptInPolicy {
    const val MAX_PROMPTS = 2
    const val REPROMPT_AFTER_MILLIS = 3L * 24 * 60 * 60 * 1000

    fun shouldPrompt(
        status: AppPushStatus,
        preferences: AppPreferences,
        isSignedIn: Boolean,
        nowMillis: Long
    ): Boolean {
        if (!isSignedIn || status != AppPushStatus.PROMPT) return false
        val count = preferences.pushOptInPromptCount
        if (count <= 0) return true
        if (count >= MAX_PROMPTS) return false
        return nowMillis - preferences.pushOptInLastPromptMillis >= REPROMPT_AFTER_MILLIS
    }

    /** Records that the explanation was shown, whatever the player chose. */
    fun recordPrompt(preferences: AppPreferences, nowMillis: Long): AppPreferences =
        preferences.copy(
            pushOptInPromptCount = preferences.pushOptInPromptCount + 1,
            pushOptInLastPromptMillis = nowMillis
        )
}
