package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.BlockedPlayerSnapshot
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
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
                SELECT u.id, p.display_name, p.player_code, p.avatar_url,
                       COALESCE(s.equipped_frame_id, 'frame_default') AS frame_id
                FROM friendships f
                JOIN users u ON u.id = CASE WHEN f.requester_id = ? THEN f.addressee_id ELSE f.requester_id END
                JOIN profiles p ON p.user_id = u.id
                LEFT JOIN player_stats s ON s.user_id = u.id
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
                            avatarId = result.getString("avatar_url"),
                            frameId = result.getString("frame_id")
                        ))
                    }
                }
            }
            val incoming = loadRequests(connection, currentId, incoming = true)
            val outgoing = loadRequests(connection, currentId, incoming = false)
            val blockedPlayers = connection.prepareStatement(
                """
                SELECT u.id, p.display_name, p.player_code, p.avatar_url,
                       COALESCE(s.equipped_frame_id, 'frame_default') AS frame_id
                FROM player_blocks b
                JOIN users u ON u.id = b.blocked_id
                JOIN profiles p ON p.user_id = u.id
                LEFT JOIN player_stats s ON s.user_id = u.id
                WHERE b.blocker_id = ? AND u.status = 'ACTIVE'
                ORDER BY LOWER(p.display_name), p.player_code
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, currentId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(BlockedPlayerSnapshot(
                            userId = result.getObject("id", UUID::class.java).toString(),
                            displayName = result.getString("display_name"),
                            playerCode = result.getString("player_code"),
                            avatarId = result.getString("avatar_url"),
                            frameId = result.getString("frame_id")
                        ))
                    }
                }
            }
            val recentPlayers = connection.prepareStatement(
                """
                SELECT opponent.id, profile.display_name, profile.player_code, profile.avatar_url,
                       COALESCE(opponent_stats.equipped_frame_id, 'frame_default') AS frame_id,
                       MAX(m.ended_at) AS last_played_at, COUNT(*) AS matches_played
                FROM match_players current_player
                JOIN matches m ON m.id = current_player.match_id
                JOIN match_players opponent_player
                  ON opponent_player.match_id = current_player.match_id
                 AND opponent_player.user_id <> current_player.user_id
                JOIN users opponent ON opponent.id = opponent_player.user_id
                JOIN profiles profile ON profile.user_id = opponent.id
                LEFT JOIN player_stats opponent_stats ON opponent_stats.user_id = opponent.id
                WHERE current_player.user_id = ?
                  AND opponent.account_type = 'REGISTERED'
                  AND opponent.status = 'ACTIVE'
                GROUP BY opponent.id, profile.display_name, profile.player_code, profile.avatar_url,
                         opponent_stats.equipped_frame_id
                ORDER BY last_played_at DESC
                LIMIT 20
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, currentId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(RecentPlayerSnapshot(
                            userId = result.getObject("id", UUID::class.java).toString(),
                            displayName = result.getString("display_name"),
                            playerCode = result.getString("player_code"),
                            avatarId = result.getString("avatar_url"),
                            lastPlayedAtEpochMillis = result.getTimestamp("last_played_at").time,
                            matchesPlayed = result.getInt("matches_played"),
                            frameId = result.getString("frame_id")
                        ))
                    }
                }
            }
            StoredFriends(friends, incoming, outgoing, blockedPlayers, recentPlayers)
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
            if (isBlockedEitherWay(connection, requesterId, addresseeId)) {
                return@withContext FriendRequestResult.Blocked
            }
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

    override suspend fun cancelRequest(
        userId: String,
        requestId: String
    ): FriendCancellationResult = withContext(Dispatchers.IO) {
        val currentId = userId.toUuidOrNull() ?: return@withContext FriendCancellationResult.NotFound
        val friendshipId = requestId.toUuidOrNull() ?: return@withContext FriendCancellationResult.NotFound
        dataSource.connection.use { connection ->
            val recipientId = connection.prepareStatement(
                """
                DELETE FROM friendships
                WHERE id = ? AND requester_id = ? AND status = 'PENDING'
                RETURNING addressee_id
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, friendshipId)
                statement.setObject(2, currentId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getObject("addressee_id", UUID::class.java) else null
                }
            } ?: return@withContext FriendCancellationResult.NotFound
            FriendCancellationResult.Success(recipientId.toString())
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

    override suspend fun removeFriend(
        userId: String,
        friendUserId: String
    ): SocialMutationResult = withContext(Dispatchers.IO) {
        val currentId = userId.toUuidOrNull() ?: return@withContext SocialMutationResult.NotFound
        val friendId = friendUserId.toUuidOrNull() ?: return@withContext SocialMutationResult.NotFound
        if (currentId == friendId) return@withContext SocialMutationResult.SelfAction
        dataSource.connection.use { connection ->
            val removed = connection.prepareStatement(
                """
                DELETE FROM friendships
                WHERE ((requester_id = ? AND addressee_id = ?) OR
                       (requester_id = ? AND addressee_id = ?))
                  AND status = 'ACCEPTED'
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, currentId)
                statement.setObject(2, friendId)
                statement.setObject(3, friendId)
                statement.setObject(4, currentId)
                statement.executeUpdate() == 1
            }
            if (removed) SocialMutationResult.Success(friendId.toString())
            else SocialMutationResult.NotFound
        }
    }

    override suspend fun blockPlayer(
        userId: String,
        playerUserId: String,
        nowMillis: Long
    ): SocialMutationResult = withContext(Dispatchers.IO) {
        val blockerId = userId.toUuidOrNull() ?: return@withContext SocialMutationResult.NotFound
        val blockedId = playerUserId.toUuidOrNull() ?: return@withContext SocialMutationResult.NotFound
        if (blockerId == blockedId) return@withContext SocialMutationResult.SelfAction
        dataSource.connection.use { connection ->
            val targetExists = connection.prepareStatement(
                "SELECT 1 FROM users WHERE id = ? AND account_type = 'REGISTERED' AND status = 'ACTIVE'"
            ).use { statement ->
                statement.setObject(1, blockedId)
                statement.executeQuery().use { it.next() }
            }
            if (!targetExists) return@withContext SocialMutationResult.NotFound

            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO player_blocks (blocker_id, blocked_id, created_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (blocker_id, blocked_id) DO NOTHING
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, blockerId)
                    statement.setObject(2, blockedId)
                    statement.setTimestamp(3, java.sql.Timestamp(nowMillis))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    DELETE FROM friendships
                    WHERE (requester_id = ? AND addressee_id = ?) OR
                          (requester_id = ? AND addressee_id = ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, blockerId)
                    statement.setObject(2, blockedId)
                    statement.setObject(3, blockedId)
                    statement.setObject(4, blockerId)
                    statement.executeUpdate()
                }
                connection.commit()
                SocialMutationResult.Success(blockedId.toString())
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun unblockPlayer(
        userId: String,
        playerUserId: String
    ): SocialMutationResult = withContext(Dispatchers.IO) {
        val blockerId = userId.toUuidOrNull() ?: return@withContext SocialMutationResult.NotFound
        val blockedId = playerUserId.toUuidOrNull() ?: return@withContext SocialMutationResult.NotFound
        if (blockerId == blockedId) return@withContext SocialMutationResult.SelfAction
        dataSource.connection.use { connection ->
            val removed = connection.prepareStatement(
                "DELETE FROM player_blocks WHERE blocker_id = ? AND blocked_id = ?"
            ).use { statement ->
                statement.setObject(1, blockerId)
                statement.setObject(2, blockedId)
                statement.executeUpdate() == 1
            }
            if (removed) SocialMutationResult.Success(blockedId.toString())
            else SocialMutationResult.NotFound
        }
    }

    override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String): Boolean =
        withContext(Dispatchers.IO) {
            val first = firstUserId.toUuidOrNull() ?: return@withContext false
            val second = secondUserId.toUuidOrNull() ?: return@withContext false
            dataSource.connection.use { connection ->
                isBlockedEitherWay(connection, first, second)
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
            SELECT f.id, u.id AS user_id, p.display_name, p.player_code, p.avatar_url,
                   COALESCE(s.equipped_frame_id, 'frame_default') AS frame_id
            FROM friendships f
            JOIN users u ON u.id = $otherColumn
            JOIN profiles p ON p.user_id = u.id
            LEFT JOIN player_stats s ON s.user_id = u.id
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
                        avatarId = result.getString("avatar_url"),
                        frameId = result.getString("frame_id")
                    ))
                }
            }
        }
    }

    private fun isBlockedEitherWay(connection: Connection, first: UUID, second: UUID): Boolean =
        connection.prepareStatement(
            """
            SELECT 1 FROM player_blocks
            WHERE (blocker_id = ? AND blocked_id = ?) OR
                  (blocker_id = ? AND blocked_id = ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, first)
            statement.setObject(2, second)
            statement.setObject(3, second)
            statement.setObject(4, first)
            statement.executeQuery().use { it.next() }
        }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
