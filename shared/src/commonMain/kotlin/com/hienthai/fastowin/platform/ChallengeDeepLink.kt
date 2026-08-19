package com.hienthai.fastowin.platform

import com.hienthai.fastowin.state.PracticeChallenge
import com.hienthai.fastowin.state.parsePracticeChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppDeepLinkRouter {
    fun openUri(uri: String): Boolean =
        RoomDeepLinkRouter.openUri(uri) || ChallengeDeepLinkRouter.openUri(uri)
}

object ChallengeDeepLinkRouter {
    private val mutablePendingChallenge = MutableStateFlow<PracticeChallenge?>(null)
    internal val pendingChallenge: StateFlow<PracticeChallenge?> = mutablePendingChallenge.asStateFlow()

    fun openUri(uri: String): Boolean {
        val challenge = parseChallengeDeepLink(uri) ?: return false
        mutablePendingChallenge.value = challenge
        return true
    }

    internal fun consume(code: String) {
        if (mutablePendingChallenge.value?.code == code) mutablePendingChallenge.value = null
    }
}

fun buildChallengeDeepLink(code: String): String {
    val challenge = requireNotNull(parsePracticeChallenge(code)) { "Mã thử thách không hợp lệ." }
    return "$CHALLENGE_LINK_PREFIX${challenge.code}"
}

fun parseChallengeDeepLink(uri: String): PracticeChallenge? {
    val normalized = uri.trim()
    if (!normalized.startsWith(CHALLENGE_LINK_PREFIX, ignoreCase = true)) return null
    val code = normalized.substring(CHALLENGE_LINK_PREFIX.length)
        .substringBefore('?')
        .substringBefore('#')
    return parsePracticeChallenge(code)
}

private const val CHALLENGE_LINK_PREFIX = "fasttowin://challenge/"
