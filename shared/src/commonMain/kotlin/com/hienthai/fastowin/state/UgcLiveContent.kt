package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.*

/** Session-owned suppression closes the interval before the sanitized snapshots arrive. */
internal fun GameState.withContentModerationChange(type: UgcTargetType, targetId: String): GameState =
    if (type == UgcTargetType.PLAYER_AVATAR && targetId.isNotBlank()) copy(
        suppressedAvatarIds = suppressedAvatarIds + targetId,
        invalidatedAvatarIds = invalidatedAvatarIds + targetId,
        avatarRevision = avatarRevision + 1
    ) else this

internal fun GameState.withAuthoritativeAvatars(avatars: List<Pair<String, String?>>): GameState {
    if (avatars.isEmpty()) return this
    val removed = avatars.filter { it.second == MODERATED_AVATAR_ID }.map { it.first }.toSet()
    return copy(suppressedAvatarIds = suppressedAvatarIds - avatars.map { it.first }.toSet() + removed,
        invalidatedAvatarIds = invalidatedAvatarIds + removed)
}

internal fun ServerMessage.authoritativeAvatars(): List<Pair<String, String?>> {
    fun GameSnapshot.avatars() = (players + spectators).map { it.id to it.avatarId }
    return when (this) {
        is ServerMessage.ProfileData -> listOf(profile.userId to profile.avatarId)
        is ServerMessage.FriendProfileData -> listOf(profile.userId to profile.avatarId)
        is ServerMessage.ClanInfoData -> if (isPresenceRefresh) emptyList() else clan.members.map { it.userId to it.avatarId }
        is ServerMessage.FriendsData -> social.run { friends.map { it.userId to it.avatarId } +
            incomingRequests.map { it.userId to it.avatarId } + outgoingRequests.map { it.userId to it.avatarId } +
            blockedPlayers.map { it.userId to it.avatarId } + recentPlayers.map { it.userId to it.avatarId } }
        is ServerMessage.LeaderboardData -> leaderboard.run {
            (topPlayers + seasonTopPlayers + previousSeasonTopPlayers + topGoldPlayers + topGemPlayers +
                listOfNotNull(currentPlayer, seasonCurrentPlayer, previousSeasonCurrentPlayer, currentGoldPlayer, currentGemPlayer))
                .mapNotNull { entry -> entry.userId?.let { it to entry.avatarId } }
        }
        is ServerMessage.SessionReady -> currentGame?.avatars().orEmpty()
        is ServerMessage.RoomCreated -> game.avatars()
        is ServerMessage.RoomUpdated -> game.avatars()
        is ServerMessage.GameStarted -> game.avatars()
        is ServerMessage.GameStateUpdated -> game.avatars()
        is ServerMessage.GameFinished -> game.avatars()
        is ServerMessage.RematchStatus -> game.avatars()
        else -> emptyList()
    }
}
