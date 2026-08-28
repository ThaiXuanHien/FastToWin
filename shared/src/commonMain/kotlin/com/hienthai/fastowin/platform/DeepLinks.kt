package com.hienthai.fastowin.platform

import com.hienthai.fastowin.state.PracticeChallenge
import com.hienthai.fastowin.state.parsePracticeChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RoomDeepLink(val roomId: String)

object AppDeepLinkRouter {
    fun openUri(uri: String): Boolean =
        RoomDeepLinkRouter.openUri(uri) || ChallengeDeepLinkRouter.openUri(uri)
}

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
    require(isValidRoomId(roomId)) { "ID phòng không hợp lệ." }
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

fun buildRoomShareText(roomName: String, roomId: String): String =
    """
    Tham gia phòng “${roomName.trim()}” trên Fast To Win:
    ${buildRoomDeepLink(roomId)}
    Nếu phòng riêng tư, hãy nhập mật khẩu do chủ phòng gửi riêng.
    """.trimIndent()

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

private fun isValidRoomId(roomId: String): Boolean =
    roomId.length in 1..64 && roomId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

private const val ROOM_LINK_PREFIX = "fasttowin://room/"
private const val CHALLENGE_LINK_PREFIX = "fasttowin://challenge/"
