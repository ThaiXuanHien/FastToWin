package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.DEFAULT_FEMALE_AVATAR_ID
import com.hienthai.fastowin.protocol.DEFAULT_MALE_AVATAR_ID
import com.hienthai.fastowin.protocol.PlayerGender
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

    override suspend fun findActiveAccountById(userId: UUID): AccountCredentials? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT u.id, u.password_hash, p.display_name
                    FROM users u
                    JOIN profiles p ON p.user_id = u.id
                    WHERE u.id = ? AND u.account_type = 'REGISTERED' AND u.status = 'ACTIVE'
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, userId)
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
                SELECT u.id, p.display_name, s.id AS session_id
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
                        displayName = result.getString("display_name"),
                        sessionId = result.getObject("session_id", UUID::class.java)
                    )
                }
            }
        }
    }

    override suspend fun listActiveSessions(
        userId: UUID,
        nowMillis: Long
    ): List<StoredAccountSession> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, device_platform, created_at, last_seen_at, expires_at
                FROM sessions
                WHERE user_id = ?
                  AND access_token_hash IS NOT NULL
                  AND refresh_token_hash IS NOT NULL
                  AND revoked_at IS NULL
                  AND expires_at > ?
                ORDER BY last_seen_at DESC, created_at DESC
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setTimestamp(2, nowMillis.toTimestamp())
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(StoredAccountSession(
                                sessionId = result.getObject("id", UUID::class.java),
                                devicePlatform = result.getString("device_platform"),
                                createdAtMillis = result.getTimestamp("created_at").time,
                                lastSeenAtMillis = result.getTimestamp("last_seen_at").time,
                                expiresAtMillis = result.getTimestamp("expires_at").time
                            ))
                        }
                    }
                }
            }
        }
    }

    override suspend fun revokeSessionById(
        userId: UUID,
        sessionId: UUID,
        nowMillis: Long
    ): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE sessions
                SET revoked_at = ?, last_seen_at = ?
                WHERE id = ? AND user_id = ?
                  AND access_token_hash IS NOT NULL
                  AND revoked_at IS NULL
                """.trimIndent()
            ).use { statement ->
                statement.setTimestamp(1, nowMillis.toTimestamp())
                statement.setTimestamp(2, nowMillis.toTimestamp())
                statement.setObject(3, sessionId)
                statement.setObject(4, userId)
                statement.executeUpdate() == 1
            }
        }
    }

    override suspend fun revokeAllSessions(userId: UUID, nowMillis: Long): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    UPDATE sessions
                    SET revoked_at = ?, last_seen_at = ?
                    WHERE user_id = ?
                      AND access_token_hash IS NOT NULL
                      AND revoked_at IS NULL
                    """.trimIndent()
                ).use { statement ->
                    statement.setTimestamp(1, nowMillis.toTimestamp())
                    statement.setTimestamp(2, nowMillis.toTimestamp())
                    statement.setObject(3, userId)
                    statement.executeUpdate()
                }
            }
        }

    override suspend fun upgradeGuest(upgrade: GuestUpgrade): GuestUpgradeResult =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val guest = findGuestForUpgrade(connection, upgrade)
                    if (guest == null) {
                        connection.rollback()
                        return@withContext GuestUpgradeResult.InvalidGuestSession
                    }

                    connection.prepareStatement(
                        """
                        UPDATE users
                        SET account_type = 'REGISTERED', email_normalized = ?, password_hash = ?, updated_at = ?
                        WHERE id = ? AND account_type = 'GUEST' AND status = 'ACTIVE'
                        """.trimIndent()
                    ).use { statement ->
                        statement.setString(1, upgrade.emailNormalized)
                        statement.setString(2, upgrade.passwordHash)
                        statement.setTimestamp(3, upgrade.session.nowMillis.toTimestamp())
                        statement.setObject(4, guest.userId)
                        if (statement.executeUpdate() != 1) {
                            connection.rollback()
                            return@withContext GuestUpgradeResult.InvalidGuestSession
                        }
                    }

                    connection.prepareStatement(
                        """
                        UPDATE sessions
                        SET revoked_at = ?, last_seen_at = ?
                        WHERE user_id = ? AND resume_token_hash IS NOT NULL AND revoked_at IS NULL
                        """.trimIndent()
                    ).use { statement ->
                        statement.setTimestamp(1, upgrade.session.nowMillis.toTimestamp())
                        statement.setTimestamp(2, upgrade.session.nowMillis.toTimestamp())
                        statement.setObject(3, guest.userId)
                        statement.executeUpdate()
                    }
                    insertSession(connection, guest.userId, upgrade.devicePlatform, upgrade.session)
                    connection.commit()
                    GuestUpgradeResult.Success(guest.userId, guest.displayName)
                } catch (error: SQLException) {
                    connection.rollback()
                    if (error.sqlState == UNIQUE_VIOLATION_SQL_STATE) {
                        GuestUpgradeResult.EmailAlreadyExists
                    } else {
                        throw error
                    }
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                }
            }
        }

    override suspend fun updatePasswordAndRevokeSessions(
        userId: UUID,
        passwordHash: String,
        nowMillis: Long
    ): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updated = connection.prepareStatement(
                    """
                    UPDATE users SET password_hash = ?, updated_at = ?
                    WHERE id = ? AND account_type = 'REGISTERED' AND status = 'ACTIVE'
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, passwordHash)
                    statement.setTimestamp(2, nowMillis.toTimestamp())
                    statement.setObject(3, userId)
                    statement.executeUpdate() == 1
                }
                if (updated) revokeAllSessions(connection, userId, nowMillis)
                connection.commit()
                updated
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun createPasswordReset(
        emailNormalized: String,
        reset: NewPasswordReset
    ): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = connection.prepareStatement(
                    """
                    SELECT id FROM users
                    WHERE email_normalized = ? AND account_type = 'REGISTERED' AND status = 'ACTIVE'
                    FOR UPDATE
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, emailNormalized)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.getObject("id", UUID::class.java) else null
                    }
                }
                if (userId == null) {
                    connection.rollback()
                    return@withContext false
                }
                connection.prepareStatement(
                    "DELETE FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL"
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO password_reset_tokens (id, user_id, token_hash, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, reset.id)
                    statement.setObject(2, userId)
                    statement.setString(3, reset.tokenHash)
                    statement.setTimestamp(4, reset.nowMillis.toTimestamp())
                    statement.setTimestamp(5, reset.expiresAtMillis.toTimestamp())
                    statement.executeUpdate()
                }
                connection.commit()
                true
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun consumePasswordReset(
        emailNormalized: String,
        tokenHash: String,
        passwordHash: String,
        nowMillis: Long
    ): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val reset = connection.prepareStatement(
                    """
                    SELECT pr.id, pr.user_id
                    FROM password_reset_tokens pr
                    JOIN users u ON u.id = pr.user_id
                    WHERE u.email_normalized = ? AND pr.token_hash = ?
                      AND pr.used_at IS NULL AND pr.expires_at > ?
                      AND u.account_type = 'REGISTERED' AND u.status = 'ACTIVE'
                    FOR UPDATE OF pr, u
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, emailNormalized)
                    statement.setString(2, tokenHash)
                    statement.setTimestamp(3, nowMillis.toTimestamp())
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else PasswordResetIdentity(
                            result.getObject("id", UUID::class.java),
                            result.getObject("user_id", UUID::class.java)
                        )
                    }
                }
                if (reset == null) {
                    connection.rollback()
                    return@withContext false
                }
                connection.prepareStatement(
                    "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?"
                ).use { statement ->
                    statement.setString(1, passwordHash)
                    statement.setTimestamp(2, nowMillis.toTimestamp())
                    statement.setObject(3, reset.userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "UPDATE password_reset_tokens SET used_at = ? WHERE id = ?"
                ).use { statement ->
                    statement.setTimestamp(1, nowMillis.toTimestamp())
                    statement.setObject(2, reset.id)
                    statement.executeUpdate()
                }
                revokeAllSessions(connection, reset.userId, nowMillis)
                connection.commit()
                true
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun deleteAccount(userId: UUID): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM users WHERE id = ? AND account_type = 'REGISTERED' AND status = 'ACTIVE'"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeUpdate() == 1
            }
        }
    }

    private fun revokeAllSessions(connection: Connection, userId: UUID, nowMillis: Long) {
        connection.prepareStatement(
            "UPDATE sessions SET revoked_at = ?, last_seen_at = ? WHERE user_id = ? AND revoked_at IS NULL"
        ).use { statement ->
            statement.setTimestamp(1, nowMillis.toTimestamp())
            statement.setTimestamp(2, nowMillis.toTimestamp())
            statement.setObject(3, userId)
            statement.executeUpdate()
        }
    }

    private fun findGuestForUpgrade(connection: Connection, upgrade: GuestUpgrade): UpgradeGuestIdentity? =
        connection.prepareStatement(
            """
            SELECT u.id, p.display_name
            FROM sessions s
            JOIN users u ON u.id = s.user_id
            JOIN profiles p ON p.user_id = u.id
            WHERE s.resume_token_hash = ?
              AND s.revoked_at IS NULL
              AND s.expires_at > ?
              AND u.account_type = 'GUEST'
              AND u.status = 'ACTIVE'
            FOR UPDATE OF s, u
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, upgrade.resumeTokenHash)
            statement.setTimestamp(2, upgrade.session.nowMillis.toTimestamp())
            statement.executeQuery().use { result ->
                if (!result.next()) null else UpgradeGuestIdentity(
                    userId = result.getObject("id", UUID::class.java),
                    displayName = result.getString("display_name")
                )
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
            INSERT INTO profiles (user_id, display_name, player_code, avatar_url, gender, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, account.userId)
            statement.setString(2, account.displayName)
            statement.setString(3, account.playerCode)
            statement.setString(
                4,
                if (account.gender == PlayerGender.FEMALE) DEFAULT_FEMALE_AVATAR_ID else DEFAULT_MALE_AVATAR_ID
            )
            statement.setString(5, account.gender.name)
            statement.setTimestamp(6, account.session.nowMillis.toTimestamp())
            statement.setTimestamp(7, account.session.nowMillis.toTimestamp())
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
    private data class UpgradeGuestIdentity(val userId: UUID, val displayName: String)
    private data class PasswordResetIdentity(val id: UUID, val userId: UUID)

    private companion object {
        const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }
}

private fun Long.toTimestamp(): Timestamp = Timestamp.from(Instant.ofEpochMilli(this))
