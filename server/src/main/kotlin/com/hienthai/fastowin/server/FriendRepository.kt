package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import java.util.UUID

data class StoredFriends(
    val friends: List<FriendSnapshot> = emptyList(),
    val incomingRequests: List<FriendRequestSnapshot> = emptyList(),
    val outgoingRequests: List<FriendRequestSnapshot> = emptyList()
)

sealed interface FriendRequestResult {
    data class Success(val recipientId: String) : FriendRequestResult
    data object PlayerNotFound : FriendRequestResult
    data object SelfRequest : FriendRequestResult
    data object AlreadyExists : FriendRequestResult
}

sealed interface FriendResponseResult {
    data class Success(val requesterId: String) : FriendResponseResult
    data object NotFound : FriendResponseResult
}

interface FriendRepository {
    suspend fun load(userId: String): StoredFriends
    suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long): FriendRequestResult
    suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long): FriendResponseResult
    suspend fun areFriends(firstUserId: String, secondUserId: String): Boolean
}

object NoOpFriendRepository : FriendRepository {
    override suspend fun load(userId: String) = StoredFriends()
    override suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long) =
        FriendRequestResult.PlayerNotFound
    override suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long) =
        FriendResponseResult.NotFound
    override suspend fun areFriends(firstUserId: String, secondUserId: String) = false
}
