package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

class PostgresFriendRepository(private val dataSource: DataSource) : FriendRepository {
    override suspend fun load(userId: String): StoredFriends = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val currentId = UUID.fromString(userId)
            val friends = connection.prepareStatement(
                """
                SELECT u.id, p.display_name, p.player_code, p.avatar_url
                FROM friendships f
                JOIN users u ON u.id = CASE WHEN f.requester_id = ? THEN f.addressee_id ELSE f.requester_id END
                JOIN profiles p ON p.user_id = u.id
                WHERE (f.requester_id = ? OR f.addressee_id = ?)
                  AND f.status = 'ACCEPTED' AND u.status = 'ACTIVE'
                ORDER BY LOWER(p.display_name), p.player_code
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, currentId)
                statement.setObject(2, currentId)
                statement.setObject(3, currentId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(FriendSnapshot(
                            userId = result.getObject("id", UUID::class.java).toString(),
                            displayName = result.getString("display_name"),
                            playerCode = result.getString("player_code"),
                            avatarId = result.getString("avatar_url")
                        ))
                    }
                }
            }
            val incoming = loadRequests(connection, currentId, incoming = true)
            val outgoing = loadRequests(connection, currentId, incoming = false)
            StoredFriends(friends, incoming, outgoing)
        }
    }

    override suspend fun sendRequest(
        userId: String,
        playerCode: String,
        nowMillis: Long
    ): FriendRequestResult = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val requesterId = UUID.fromString(userId)
            val addresseeId = connection.prepareStatement(
                """
                SELECT u.id FROM profiles p JOIN users u ON u.id = p.user_id
                WHERE UPPER(p.player_code) = ? AND u.account_type = 'REGISTERED' AND u.status = 'ACTIVE'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, playerCode.trim().uppercase())
                statement.executeQuery().use { result ->
                    if (result.next()) result.getObject("id", UUID::class.java) else null
                }
            } ?: return@withContext FriendRequestResult.PlayerNotFound
            if (requesterId == addresseeId) return@withContext FriendRequestResult.SelfRequest
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO friendships (id, requester_id, addressee_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'PENDING', ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, requesterId)
                    statement.setObject(3, addresseeId)
                    statement.setTimestamp(4, java.sql.Timestamp(nowMillis))
                    statement.setTimestamp(5, java.sql.Timestamp(nowMillis))
                    statement.executeUpdate()
                }
                FriendRequestResult.Success(addresseeId.toString())
            } catch (error: SQLException) {
                if (error.sqlState != "23505") throw error
                val reopened = connection.prepareStatement(
                    """
                    UPDATE friendships
                    SET requester_id = ?, addressee_id = ?, status = 'PENDING', updated_at = ?
                    WHERE ((requester_id = ? AND addressee_id = ?) OR
                           (requester_id = ? AND addressee_id = ?))
                      AND status = 'DECLINED'
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, requesterId)
                    statement.setObject(2, addresseeId)
                    statement.setTimestamp(3, java.sql.Timestamp(nowMillis))
                    statement.setObject(4, requesterId)
                    statement.setObject(5, addresseeId)
                    statement.setObject(6, addresseeId)
                    statement.setObject(7, requesterId)
                    statement.executeUpdate() == 1
                }
                if (reopened) FriendRequestResult.Success(addresseeId.toString())
                else FriendRequestResult.AlreadyExists
            }
        }
    }

    override suspend fun respond(
        userId: String,
        requestId: String,
        accept: Boolean,
        nowMillis: Long
    ): FriendResponseResult = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val currentId = UUID.fromString(userId)
            val id = runCatching { UUID.fromString(requestId) }.getOrNull()
                ?: return@withContext FriendResponseResult.NotFound
            val requesterId = connection.prepareStatement(
                """
                UPDATE friendships SET status = ?, updated_at = ?
                WHERE id = ? AND addressee_id = ? AND status = 'PENDING'
                RETURNING requester_id
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, if (accept) "ACCEPTED" else "DECLINED")
                statement.setTimestamp(2, java.sql.Timestamp(nowMillis))
                statement.setObject(3, id)
                statement.setObject(4, currentId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getObject("requester_id", UUID::class.java) else null
                }
            } ?: return@withContext FriendResponseResult.NotFound
            FriendResponseResult.Success(requesterId.toString())
        }
    }

    override suspend fun areFriends(firstUserId: String, secondUserId: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT 1 FROM friendships
                    WHERE ((requester_id = ? AND addressee_id = ?) OR
                           (requester_id = ? AND addressee_id = ?))
                      AND status = 'ACCEPTED'
                    """.trimIndent()
                ).use { statement ->
                    val first = UUID.fromString(firstUserId)
                    val second = UUID.fromString(secondUserId)
                    statement.setObject(1, first)
                    statement.setObject(2, second)
                    statement.setObject(3, second)
                    statement.setObject(4, first)
                    statement.executeQuery().use { it.next() }
                }
            }
        }

    private fun loadRequests(
        connection: Connection,
        userId: UUID,
        incoming: Boolean
    ): List<FriendRequestSnapshot> {
        val ownColumn = if (incoming) "f.addressee_id" else "f.requester_id"
        val otherColumn = if (incoming) "f.requester_id" else "f.addressee_id"
        return connection.prepareStatement(
            """
            SELECT f.id, u.id AS user_id, p.display_name, p.player_code, p.avatar_url
            FROM friendships f JOIN users u ON u.id = $otherColumn JOIN profiles p ON p.user_id = u.id
            WHERE $ownColumn = ? AND f.status = 'PENDING' AND u.status = 'ACTIVE'
            ORDER BY f.created_at DESC
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(FriendRequestSnapshot(
                        requestId = result.getObject("id", UUID::class.java).toString(),
                        userId = result.getObject("user_id", UUID::class.java).toString(),
                        displayName = result.getString("display_name"),
                        playerCode = result.getString("player_code"),
                        avatarId = result.getString("avatar_url")
                    ))
                }
            }
        }
    }
}
