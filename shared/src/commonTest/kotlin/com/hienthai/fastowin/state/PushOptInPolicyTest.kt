package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.platform.AppPushStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushOptInPolicyTest {
    private val now = 1_800_000_000_000L
    private val fresh = AppPreferences()

    @Test
    fun asksSignedInPlayerOnceWhileSystemPromptIsAvailable() {
        assertTrue(PushOptInPolicy.shouldPrompt(AppPushStatus.PROMPT, fresh, isSignedIn = true, nowMillis = now))
    }

    @Test
    fun neverAsksGuestsOrWhenPermissionIsSettled() {
        assertFalse(PushOptInPolicy.shouldPrompt(AppPushStatus.PROMPT, fresh, isSignedIn = false, nowMillis = now))
        listOf(
            AppPushStatus.ENABLED, AppPushStatus.DENIED, AppPushStatus.REQUESTING,
            AppPushStatus.UNSUPPORTED, AppPushStatus.UNCONFIGURED, AppPushStatus.DISABLED, AppPushStatus.ERROR
        ).forEach { status ->
            assertFalse(PushOptInPolicy.shouldPrompt(status, fresh, isSignedIn = true, nowMillis = now), status.name)
        }
    }

    @Test
    fun asksAgainOnlyOnceAfterThreeDays() {
        val afterFirst = PushOptInPolicy.recordPrompt(fresh, now)
        assertEquals(1, afterFirst.pushOptInPromptCount)
        assertFalse(PushOptInPolicy.shouldPrompt(AppPushStatus.PROMPT, afterFirst, true, now + PushOptInPolicy.REPROMPT_AFTER_MILLIS - 1))
        val later = now + PushOptInPolicy.REPROMPT_AFTER_MILLIS
        assertTrue(PushOptInPolicy.shouldPrompt(AppPushStatus.PROMPT, afterFirst, true, later))
        val afterSecond = PushOptInPolicy.recordPrompt(afterFirst, later)
        assertFalse(PushOptInPolicy.shouldPrompt(AppPushStatus.PROMPT, afterSecond, true, later + 30L * PushOptInPolicy.REPROMPT_AFTER_MILLIS))
    }
}
