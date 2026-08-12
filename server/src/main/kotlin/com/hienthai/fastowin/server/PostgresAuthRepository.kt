package com.hienthai.fastowin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PostgresAuthRepository(private val dataSource: DataSource) : AuthRepository {
    override suspend fun createAccount(account: NewAccount): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                insertUser(connection, account)
                insertProfile(connection, account)
                insertSession(connection, account.userId, account.devicePlatform, account.session)
                connection.commit()
                true
            } catch (error: SQLException) {
                connection.rollback()
                if (error.sqlState == UNIQUE_VIOLATION_SQL_STATE) false else throw error
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun findActiveAccount(emailNormalized: String): AccountCredentials? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT u.id, u.password_hash, p.display_name
                    FROM users u
                    JOIN profiles p ON p.user_id = u.id
                    WHERE u.email_normalized = ? AND u.account_type = 'REGISTERED' AND u.status = 'ACTIVE'
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, emailNormalized)
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else AccountCredentials(
                            userId = result.getObject("id", UUID::class.java),
                            passwordHash = result.getString("password_hash"),
                            displayName = result.getString("display_name")
                        )
                    }
                }
            }
        }

    override suspend fun createSession(userId: UUID, devicePlatform: String?, session: NewAuthSession): Unit =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection -> insertSession(connection, userId, devicePlatform, session) }
        }

    override suspend fun rotateSession(
        refreshTokenHash: String,
        replacement: NewAuthSession
    ): UUID? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val current = connection.prepareStatement(
                    """
                    SELECT s.id, s.user_id
                    FROM sessions s
                    JOIN users u ON u.id = s.user_id
                    WHERE s.refresh_token_hash = ?
                      AND s.revoked_at IS NULL
                      AND s.expires_at > ?
                      AND u.status = 'ACTIVE'
                    FOR UPDATE
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, refreshTokenHash)
                    statement.setTimestamp(2, replacement.nowMillis.toTimestamp())
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else CurrentSession(
                            result.getObject("id", UUID::class.java),
                            result.getObject("user_id", UUID::class.java)
                        )
                    }
                }
                if (current == null) {
                    connection.rollback()
                    return@withContext null
                }

                connection.prepareStatement(
                    """
                    UPDATE sessions
                    SET access_token_hash = ?, refresh_token_hash = ?, access_expires_at = ?,
                        expires_at = ?, refreshed_at = ?, last_seen_at = ?
                    WHERE id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, replacement.accessTokenHash)
                    statement.setString(2, replacement.refreshTokenHash)
                    statement.setTimestamp(3, replacement.accessExpiresAtMillis.toTimestamp())
                    statement.setTimestamp(4, replacement.refreshExpiresAtMillis.toTimestamp())
                    statement.setTimestamp(5, replacement.nowMillis.toTimestamp())
                    statement.setTimestamp(6, replacement.nowMillis.toTimestamp())
                    statement.setObject(7, current.sessionId)
                    statement.executeUpdate()
                }
                connection.commit()
                current.userId
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun revokeSession(refreshTokenHash: String, nowMillis: Long): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    UPDATE sessions
                    SET revoked_at = ?, last_seen_at = ?
                    WHERE refresh_token_hash = ? AND revoked_at IS NULL
                    """.trimIndent()
                ).use { statement ->
                    statement.setTimestamp(1, nowMillis.toTimestamp())
                    statement.setTimestamp(2, nowMillis.toTimestamp())
                    statement.setString(3, refreshTokenHash)
                    statement.executeUpdate() > 0
                }
            }
        }

    override suspend fun findActiveSession(
        accessTokenHash: String,
        nowMillis: Long
    ): AuthenticatedAccount? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT u.id, p.display_name
                FROM sessions s
                JOIN users u ON u.id = s.user_id
                JOIN profiles p ON p.user_id = u.id
                WHERE s.access_token_hash = ?
                  AND s.access_expires_at > ?
                  AND s.revoked_at IS NULL
                  AND u.account_type = 'REGISTERED'
                  AND u.status = 'ACTIVE'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, accessTokenHash)
                statement.setTimestamp(2, nowMillis.toTimestamp())
                statement.executeQuery().use { result ->
                    if (!result.next()) null else AuthenticatedAccount(
                        userId = result.getObject("id", UUID::class.java),
                        displayName = result.getString("display_name")
                    )
                }
            }
        }
    }

    private fun insertUser(connection: Connection, account: NewAccount) {
        connection.prepareStatement(
            """
            INSERT INTO users (
                id, account_type, status, email_normalized, password_hash, created_at, updated_at
            ) VALUES (?, 'REGISTERED', 'ACTIVE', ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, account.userId)
            statement.setString(2, account.emailNormalized)
            statement.setString(3, account.passwordHash)
            statement.setTimestamp(4, account.session.nowMillis.toTimestamp())
            statement.setTimestamp(5, account.session.nowMillis.toTimestamp())
            statement.executeUpdate()
        }
    }

    private fun insertProfile(connection: Connection, account: NewAccount) {
        connection.prepareStatement(
            """
            INSERT INTO profiles (user_id, display_name, player_code, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, account.userId)
            statement.setString(2, account.displayName)
            statement.setString(3, account.playerCode)
            statement.setTimestamp(4, account.session.nowMillis.toTimestamp())
            statement.setTimestamp(5, account.session.nowMillis.toTimestamp())
            statement.executeUpdate()
        }
    }

    private fun insertSession(
        connection: Connection,
        userId: UUID,
        devicePlatform: String?,
        session: NewAuthSession
    ) {
        connection.prepareStatement(
            """
            INSERT INTO sessions (
                id, user_id, access_token_hash, refresh_token_hash, device_platform,
                created_at, last_seen_at, access_expires_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, session.sessionId)
            statement.setObject(2, userId)
            statement.setString(3, session.accessTokenHash)
            statement.setString(4, session.refreshTokenHash)
            statement.setString(5, devicePlatform)
            statement.setTimestamp(6, session.nowMillis.toTimestamp())
            statement.setTimestamp(7, session.nowMillis.toTimestamp())
            statement.setTimestamp(8, session.accessExpiresAtMillis.toTimestamp())
            statement.setTimestamp(9, session.refreshExpiresAtMillis.toTimestamp())
            statement.executeUpdate()
        }
    }

    private data class CurrentSession(val sessionId: UUID, val userId: UUID)

    private companion object {
        const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }
}

private fun Long.toTimestamp(): Timestamp = Timestamp.from(Instant.ofEpochMilli(this))
