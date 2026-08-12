package com.hienthai.fastowin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PostgresGuestIdentityRepository(
    private val dataSource: DataSource
) : GuestIdentityRepository {
    override suspend fun resolveGuest(
        displayName: String,
        resumeToken: String?,
        nowMillis: Long
    ): GuestIdentity = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val identity = resumeToken
                    ?.let { findAndRefresh(connection, displayName, it, nowMillis) }
                    ?: createGuest(connection, displayName, nowMillis)
                connection.commit()
                identity
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun markDisconnected(playerId: String, nowMillis: Long): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE sessions SET disconnected_at = ?, last_seen_at = ? WHERE user_id = ? AND revoked_at IS NULL"
            ).use { statement ->
                val timestamp = Timestamp.from(Instant.ofEpochMilli(nowMillis))
                statement.setTimestamp(1, timestamp)
                statement.setTimestamp(2, timestamp)
                statement.setObject(3, UUID.fromString(playerId))
                statement.executeUpdate()
            }
        }
    }

    private fun findAndRefresh(
        connection: Connection,
        displayName: String,
        resumeToken: String,
        nowMillis: Long
    ): GuestIdentity? {
        val tokenHash = hashToken(resumeToken)
        val playerId = connection.prepareStatement(
            """
            SELECT s.user_id
            FROM sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.resume_token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ? AND u.status = 'ACTIVE'
            FOR UPDATE
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, tokenHash)
            statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(nowMillis)))
            statement.executeQuery().use { result ->
                if (result.next()) result.getObject("user_id", UUID::class.java) else null
            }
        } ?: return null

        connection.prepareStatement(
            "UPDATE profiles SET display_name = ?, updated_at = ? WHERE user_id = ?"
        ).use { statement ->
            statement.setString(1, displayName)
            statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(nowMillis)))
            statement.setObject(3, playerId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "UPDATE sessions SET last_seen_at = ?, disconnected_at = NULL, expires_at = ? WHERE resume_token_hash = ?"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(nowMillis)))
            statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(nowMillis + SESSION_TTL_MILLIS)))
            statement.setString(3, tokenHash)
            statement.executeUpdate()
        }
        return GuestIdentity(playerId.toString(), resumeToken, displayName)
    }

    private fun createGuest(connection: Connection, displayName: String, nowMillis: Long): GuestIdentity {
        val playerId = UUID.randomUUID()
        val resumeToken = newResumeToken()
        val now = Timestamp.from(Instant.ofEpochMilli(nowMillis))

        connection.prepareStatement(
            "INSERT INTO users (id, account_type, status, created_at, updated_at) VALUES (?, 'GUEST', 'ACTIVE', ?, ?)"
        ).use { statement ->
            statement.setObject(1, playerId)
            statement.setTimestamp(2, now)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO profiles (user_id, display_name, player_code, created_at, updated_at) VALUES (?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, playerId)
            statement.setString(2, displayName)
            statement.setString(3, playerCode(playerId))
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            INSERT INTO sessions (id, user_id, resume_token_hash, created_at, last_seen_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, playerId)
            statement.setString(3, hashToken(resumeToken))
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(nowMillis + SESSION_TTL_MILLIS)))
            statement.executeUpdate()
        }
        return GuestIdentity(playerId.toString(), resumeToken, displayName)
    }

    private fun hashToken(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun playerCode(playerId: UUID): String = playerId.toString()
        .replace("-", "")
        .take(10)
        .uppercase()

    private companion object {
        const val SESSION_TTL_MILLIS = 30L * 24 * 60 * 60 * 1_000
    }
}
