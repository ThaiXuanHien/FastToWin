package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.DatabaseSettings
import com.hienthai.fastowin.protocol.DEFAULT_MALE_AVATAR_ID
import com.hienthai.fastowin.server.PasswordHasher
import org.flywaydb.core.Flyway
import java.net.URI
import java.util.UUID
import javax.sql.DataSource
import org.postgresql.ds.PGSimpleDataSource

internal class RoomOwnershipProductionSeedSettings private constructor(
    val database: DatabaseSettings,
    val accounts: List<UUID>,
    val password: String,
    val scope: String
) {
    companion object {
        fun fromEnvironment(values: Map<String, String>): RoomOwnershipProductionSeedSettings {
            require(values["FASTTOWIN_ENV"] == "prod")
            require(values["FTW_REHEARSAL_SCOPE"] == "room-ownership-production")
            require(values["FASTTOWIN_ROOM_OWNERSHIP_MODE"] == "production")
            require(values.keys.none { it.contains("FIXTURE", ignoreCase = true) })

            val url = values["DATABASE_URL"]?.trim().orEmpty()
            val parsed = runCatching { URI(url.removePrefix("jdbc:")) }.getOrNull()
            require(parsed?.host?.endsWith(".railway.internal") == true ||
                parsed?.host == "127.0.0.1" ||
                parsed?.host == "localhost")
            val database = requireNotNull(DatabaseSettings.fromEnvironment("prod", values))

            val accounts = values["FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS"]
                ?.split(',')
                ?.map { value -> UUID.fromString(value.trim()) }
                ?: error("Production rehearsal accounts are required")
            require(accounts.size in 2..256 && accounts.toSet().size == accounts.size)
            val password = values["FASTTOWIN_ROOM_OWNERSHIP_REHEARSAL_PASSWORD"]
                ?.takeIf { it.length in 16..128 }
                ?: error("Production rehearsal password is required")

            return RoomOwnershipProductionSeedSettings(
                database = database,
                accounts = accounts,
                password = password,
                scope = "room-ownership-production"
            )
        }
    }
}

internal fun seedRoomOwnershipProductionAccounts(
    dataSource: DataSource,
    accounts: List<UUID>,
    password: String
) {
    require(accounts.size in 2..256 && accounts.toSet().size == accounts.size)
    require(password.length in 16..128)
    dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            connection.createStatement().use {
                it.execute("SELECT pg_catalog.pg_advisory_xact_lock(6075464190125381698)")
            }
            Flyway.configure().dataSource(dataSource).defaultSchema("public")
                .locations("classpath:db/migration").load().migrate()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT EXISTS (SELECT FROM users)").use { rows ->
                    rows.next()
                    require(!rows.getBoolean(1)) { "Production rehearsal database already contains accounts" }
                }
            }
            val hashes = accounts.map { PasswordHasher().hash(password) }
            connection.prepareStatement(
                """
                INSERT INTO users
                    (id, account_type, status, email_normalized, password_hash, email_verified_at, created_at, updated_at)
                VALUES (?, 'REGISTERED', 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.trimIndent()
            ).use { statement ->
                accounts.forEachIndexed { index, account ->
                    statement.setObject(1, account)
                    statement.setString(2, "room-production-" + (index + 1) + "@example.test")
                    statement.setString(3, hashes[index])
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            connection.prepareStatement(
                """
                INSERT INTO profiles
                    (user_id, display_name, player_code, avatar_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.trimIndent()
            ).use { statement ->
                accounts.forEachIndexed { index, account ->
                    val number = index + 1
                    statement.setObject(1, account)
                    statement.setString(2, "Room Production " + number)
                    statement.setString(3, "RPROD" + number.toString().padStart(6, '0'))
                    statement.setString(4, DEFAULT_MALE_AVATAR_ID)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            connection.commit()
        } catch (failure: Exception) {
            connection.rollback()
            throw failure
        }
    }
}

fun main() {
    val settings = RoomOwnershipProductionSeedSettings.fromEnvironment(System.getenv())
    val dataSource = PGSimpleDataSource().apply {
        setUrl(settings.database.url)
        user = settings.database.user
        password = settings.database.password
        connectTimeout = 7
    }
    seedRoomOwnershipProductionAccounts(dataSource, settings.accounts, settings.password)
}
