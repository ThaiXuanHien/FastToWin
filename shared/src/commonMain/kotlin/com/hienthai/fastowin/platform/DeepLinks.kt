package com.hienthai.fastowin.platform

import com.hienthai.fastowin.state.PracticeChallenge
import com.hienthai.fastowin.state.parsePracticeChallenge
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.normalizeReferralCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RoomDeepLink(val roomId: String)

object AppDeepLinkRouter {
    private val mutableNotificationInboxRequested = MutableStateFlow(false)
    internal val notificationInboxRequested = mutableNotificationInboxRequested.asStateFlow()

    fun openUri(uri: String): Boolean =
        when (uri) {
            "fasttowin://notifications" -> {
                mutableNotificationInboxRequested.value = true
                true
            }
            else -> RoomDeepLinkRouter.openUri(uri) || ChallengeDeepLinkRouter.openUri(uri) ||
                ReferralDeepLinkRouter.openUri(uri)
        }

    internal fun consumeNotificationInboxRequest(): Boolean =
        mutableNotificationInboxRequested.compareAndSet(expect = true, update = false)
}

internal object ReferralDeepLinkRouter {
    private val mutablePendingCode = MutableStateFlow<String?>(null)
    val pendingCode = mutablePendingCode.asStateFlow()

    fun openUri(uri: String): Boolean {
        val code = parseReferralCode(uri, REFERRAL_DEEP_LINK_PREFIX) ?: return false
        mutablePendingCode.value = code
        return true
    }

    fun consume(code: String) {
        mutablePendingCode.compareAndSet(code, null)
    }
}

internal fun referralWebPath(code: String): String = REFERRAL_WEB_PREFIX + requireNotNull(normalizeReferralCode(code))

internal fun referralNativeLink(code: String): String =
    REFERRAL_DEEP_LINK_PREFIX + requireNotNull(normalizeReferralCode(code))

internal fun parseReferralWebPath(path: String): String? = parseReferralCode(path, REFERRAL_WEB_PREFIX)

private fun parseReferralCode(value: String, prefix: String): String? =
    value.takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.substringBefore('?')?.substringBefore('#')
        ?.let(::normalizeReferralCode)

private const val REFERRAL_WEB_PREFIX = "/invite/"
private const val REFERRAL_DEEP_LINK_PREFIX = "fasttowin://invite/"

object RoomDeepLinkRouter {
    private val mutablePendingLink = MutableStateFlow<RoomDeepLink?>(null)
    internal val pendingLink: StateFlow<RoomDeepLink?> = mutablePendingLink.asStateFlow()

    fun openUri(uri: String): Boolean {
        val link = parseRoomDeepLink(uri) ?: return false
        mutablePendingLink.value = link
        return true
    }

    internal fun consume(roomId: String) {
        if (mutablePendingLink.value?.roomId == roomId) mutablePendingLink.value = null
    }
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

fun buildRoomDeepLink(roomId: String): String {
    require(isValidRoomId(roomId)) { INVALID_ROOM_ID }
    return "$ROOM_LINK_PREFIX$roomId"
}

fun parseRoomDeepLink(uri: String): RoomDeepLink? {
    val normalized = uri.trim()
    val roomId = when {
        normalized.startsWith(ROOM_LINK_PREFIX, ignoreCase = true) ->
            normalized.substring(ROOM_LINK_PREFIX.length).substringBefore('?').substringBefore('#')
        else -> return null
    }
    return roomId.takeIf(::isValidRoomId)?.let(::RoomDeepLink)
}

fun buildRoomShareText(
    roomName: String,
    roomId: String,
    deepLink: String = buildRoomDeepLink(roomId),
    localization: LocalizationService = LocalizationService(AppLanguage.VIETNAMESE)
): String = localization.text(TextKey.RoomShareText, mapOf("room" to roomName.trim(), "link" to deepLink))

fun buildChallengeDeepLink(code: String): String {
    val challenge = requireNotNull(parsePracticeChallenge(code)) { INVALID_CHALLENGE_CODE }
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

private fun isValidRoomId(roomId: String): Boolean =
    roomId.length in 1..64 && roomId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

private const val ROOM_LINK_PREFIX = "fasttowin://room/"
private const val CHALLENGE_LINK_PREFIX = "fasttowin://challenge/"
private const val INVALID_ROOM_ID = "invalid_room_id"
private const val INVALID_CHALLENGE_CODE = "invalid_challenge_code"
