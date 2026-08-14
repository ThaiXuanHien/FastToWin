package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.BlockedPlayerSnapshot
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import java.util.UUID

data class StoredFriends(
    val friends: List<FriendSnapshot> = emptyList(),
    val incomingRequests: List<FriendRequestSnapshot> = emptyList(),
    val outgoingRequests: List<FriendRequestSnapshot> = emptyList(),
    val blockedPlayers: List<BlockedPlayerSnapshot> = emptyList(),
    val recentPlayers: List<RecentPlayerSnapshot> = emptyList()
)

sealed interface FriendRequestResult {
    data class Success(val recipientId: String) : FriendRequestResult
    data object PlayerNotFound : FriendRequestResult
    data object SelfRequest : FriendRequestResult
    data object AlreadyExists : FriendRequestResult
    data object Blocked : FriendRequestResult
}

sealed interface FriendResponseResult {
    data class Success(val requesterId: String) : FriendResponseResult
    data object NotFound : FriendResponseResult
}

sealed interface FriendCancellationResult {
    data class Success(val recipientId: String) : FriendCancellationResult
    data object NotFound : FriendCancellationResult
}

sealed interface SocialMutationResult {
    data class Success(val otherUserId: String) : SocialMutationResult
    data object NotFound : SocialMutationResult
    data object SelfAction : SocialMutationResult
}

interface FriendRepository {
    suspend fun load(userId: String): StoredFriends
    suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long): FriendRequestResult
    suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long): FriendResponseResult
    suspend fun cancelRequest(userId: String, requestId: String): FriendCancellationResult
    suspend fun removeFriend(userId: String, friendUserId: String): SocialMutationResult
    suspend fun blockPlayer(userId: String, playerUserId: String, nowMillis: Long): SocialMutationResult
    suspend fun unblockPlayer(userId: String, playerUserId: String): SocialMutationResult
    suspend fun areFriends(firstUserId: String, secondUserId: String): Boolean
    suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String): Boolean
}

object NoOpFriendRepository : FriendRepository {
    override suspend fun load(userId: String) = StoredFriends()
    override suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long) =
        FriendRequestResult.PlayerNotFound
    override suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long) =
        FriendResponseResult.NotFound
    override suspend fun cancelRequest(userId: String, requestId: String) = FriendCancellationResult.NotFound
    override suspend fun removeFriend(userId: String, friendUserId: String) = SocialMutationResult.NotFound
    override suspend fun blockPlayer(userId: String, playerUserId: String, nowMillis: Long) =
        SocialMutationResult.NotFound
    override suspend fun unblockPlayer(userId: String, playerUserId: String) = SocialMutationResult.NotFound
    override suspend fun areFriends(firstUserId: String, secondUserId: String) = false
    override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String) = false
}
