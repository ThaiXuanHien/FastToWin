package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.DEFAULT_MALE_AVATAR_ID
import com.hienthai.fastowin.server.DatabaseSettings
import com.hienthai.fastowin.server.PasswordHasher
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.system.exitProcess

private const val FIXTURE_DATABASE_URL = "jdbc:postgresql://postgres:5432/fasttowin_room_pilot"
private const val FIXTURE_DATABASE_USER = "fasttowin_room_pilot"

fun main() {
    try {
        val settings = RoomOwnershipFixtureSeedSettings.fromEnvironment(System.getenv())
        val dataSource = PGSimpleDataSource().apply {
            setUrl(settings.database.url)
            user = settings.database.user
            password = settings.database.password
            connectTimeout = 7
        }
        seedRoomOwnershipFixture(dataSource, settings.fixtureId, settings.accounts, settings.password)
    } catch (refusal: FixtureSeedRefused) {
        System.err.println(refusal.message)
        exitProcess(1)
    } catch (_: Exception) {
        System.err.println("Could not initialize the room ownership fixture database.")
        exitProcess(1)
    }
}

internal class RoomOwnershipFixtureSeedSettings private constructor(
    val database: DatabaseSettings,
    val fixtureId: UUID,
    val accounts: List<UUID>,
    val password: String
) {
    companion object {
        fun fromEnvironment(values: Map<String, String>): RoomOwnershipFixtureSeedSettings {
            requireFixture(values["FASTTOWIN_ENV"] == "dev", "Fixture initialization requires FASTTOWIN_ENV=dev.")
            requireFixture(values["DATABASE_URL"] == FIXTURE_DATABASE_URL && values["DATABASE_USER"] == FIXTURE_DATABASE_USER,
                "Fixture initialization requires the dedicated Compose database.")
            val database = checkNotNull(DatabaseSettings.fromEnvironment("dev", values))
            val fixtureId = values["FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID"]?.trim()?.let(::parsePilotUuid)
                ?: throw FixtureSeedRefused("A valid fixture ID is required.")
            val accounts = values["FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS"].orEmpty().split(',', limit = 257).map { value ->
                parsePilotUuid(value.trim()) ?: throw FixtureSeedRefused("A valid account ID is required for each fixture account.")
            }
            val password = values["FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD"].orEmpty()
            validateFixtureAccounts(accounts, password)
            return RoomOwnershipFixtureSeedSettings(database, fixtureId, accounts, password)
        }
    }
}

internal fun seedRoomOwnershipFixture(
    dataSource: DataSource,
    fixtureId: UUID,
    accounts: List<UUID>,
    password: String,
    schema: String = "public"
) {
    validateFixtureAccounts(accounts, password)
    requireFixture(schema.matches(Regex("[a-z][a-z0-9_]{0,62}")), "Invalid fixture schema.")
    dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            // Keep the preflight, migration, and seed serialized across fixture initializers.
            connection.createStatement().use { statement ->
                statement.execute("SELECT pg_catalog.pg_advisory_xact_lock(6075464190125381698)")
                statement.execute("SET LOCAL search_path TO \"$schema\"")
            }
            requireFixture(connection.schema == schema, "The fixture schema must already exist.")
            if (schema == "public") connection.requireDedicatedDatabase()

            val hasher = PasswordHasher()
            if (connection.fixtureMarkerTableExists()) {
                connection.lockFixtureTables()
                requireFixture(connection.readFixtureId() == fixtureId, "The database fixture marker does not match.")
                connection.verifyFixtureAccounts(accounts, password, hasher)
            } else {
                requireFixture(!connection.schemaHasObjects(schema), "The unmarked fixture database is not empty.")
                val hashes = accounts.map { hasher.hash(password) }
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load().migrate()
                connection.lockFixtureTables()
                requireFixture(connection.readFixtureId() == null && !connection.fixtureUsersExist(),
                    "The fixture database changed during initialization.")
                connection.insertFixtureAccounts(accounts, hashes)
                connection.prepareStatement("INSERT INTO room_ownership_pilot_fixture (fixture_id) VALUES (?)").use {
                    it.setObject(1, fixtureId)
                    it.executeUpdate()
                }
            }
            connection.commit()
        } catch (failure: Exception) {
            connection.rollback()
            throw failure
        }
    }
}

private fun validateFixtureAccounts(accounts: List<UUID>, password: String) {
    requireFixture(accounts.size in 2..256 && accounts.toSet().size == accounts.size,
        "The fixture requires 2 to 256 distinct account IDs.")
    requireFixture(password.length in 16..128 && password.none(Char::isISOControl) &&
        password.any(Char::isUpperCase) && password.any(Char::isLowerCase) && password.any(Char::isDigit) &&
        password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
        "The fixture password must be 16 to 128 characters with uppercase, lowercase, a digit, and punctuation.")
}

private fun Connection.requireDedicatedDatabase() {
    createStatement().use { statement ->
        statement.executeQuery("""
            SELECT EXISTS (SELECT FROM pg_catalog.pg_namespace
                WHERE nspname NOT IN ('public', 'information_schema') AND nspname !~ '^pg_')
        """.trimIndent()).use { rows ->
            rows.next()
            requireFixture(!rows.getBoolean(1), "The fixture database contains another schema.")
        }
    }
}

private fun Connection.fixtureMarkerTableExists(): Boolean = createStatement().use { statement ->
    statement.executeQuery("SELECT pg_catalog.to_regclass('room_ownership_pilot_fixture') IS NOT NULL").use { rows ->
        rows.next()
        rows.getBoolean(1)
    }
}

private fun Connection.schemaHasObjects(schema: String): Boolean = prepareStatement("""
    SELECT EXISTS (
        SELECT FROM pg_catalog.pg_depend d JOIN pg_catalog.pg_namespace n ON n.oid = d.refobjid
        WHERE d.refclassid = 'pg_catalog.pg_namespace'::pg_catalog.regclass AND n.nspname = ?
    )
""".trimIndent()).use { statement ->
    statement.setString(1, schema)
    statement.executeQuery().use { rows ->
        rows.next()
        rows.getBoolean(1)
    }
}

private fun Connection.lockFixtureTables() {
    createStatement().use {
        it.execute("LOCK TABLE room_ownership_pilot_fixture, users, profiles IN SHARE ROW EXCLUSIVE MODE")
    }
}

private fun Connection.readFixtureId(): UUID? = createStatement().use { statement ->
    statement.executeQuery("SELECT fixture_id FROM room_ownership_pilot_fixture WHERE singleton").use { rows ->
        if (rows.next()) rows.getObject(1, UUID::class.java) else null
    }
}

private fun Connection.fixtureUsersExist(): Boolean = createStatement().use { statement ->
    statement.executeQuery("SELECT EXISTS (SELECT FROM users)").use { rows ->
        rows.next()
        rows.getBoolean(1)
    }
}

private fun Connection.verifyFixtureAccounts(accounts: List<UUID>, password: String, hasher: PasswordHasher) {
    val expected = accounts.withIndex().associate { (index, id) -> id to index + 1 }
    createStatement().use { statement ->
        statement.executeQuery("""
            SELECT u.id, u.email_normalized, u.password_hash, p.display_name,
                u.account_type = 'REGISTERED' AND u.status = 'ACTIVE' AND u.email_verified_at IS NOT NULL AS eligible
            FROM users u LEFT JOIN profiles p ON p.user_id = u.id
        """.trimIndent()).use { rows ->
            var matched = 0
            while (rows.next()) {
                val number = expected[rows.getObject("id", UUID::class.java)]
                    ?: throw FixtureSeedRefused("The database contains an unexpected fixture account.")
                val hash = rows.getString("password_hash")
                requireFixture(rows.getBoolean("eligible") && rows.getString("email_normalized") == fixtureEmail(number) &&
                    rows.getString("display_name") == fixtureDisplayName(number) && hash != null && hasher.verify(password, hash),
                    "An existing fixture account does not match the requested credentials or profile.")
                matched++
            }
            requireFixture(matched == accounts.size, "The database is missing fixture accounts.")
        }
    }
}

private fun Connection.insertFixtureAccounts(accounts: List<UUID>, hashes: List<String>) {
    prepareStatement("""
        INSERT INTO users (id, account_type, status, email_normalized, password_hash, email_verified_at, created_at, updated_at)
        VALUES (?, 'REGISTERED', 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    """.trimIndent()).use { statement ->
        accounts.forEachIndexed { index, id ->
            statement.setObject(1, id)
            statement.setString(2, fixtureEmail(index + 1))
            statement.setString(3, hashes[index])
            statement.addBatch()
        }
        statement.executeBatch()
    }
    prepareStatement("""
        INSERT INTO profiles (user_id, display_name, player_code, avatar_url, created_at, updated_at)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    """.trimIndent()).use { statement ->
        accounts.forEachIndexed { index, id ->
            val number = index + 1
            statement.setObject(1, id)
            statement.setString(2, fixtureDisplayName(number))
            statement.setString(3, "RP${number.toString().padStart(6, '0')}")
            statement.setString(4, DEFAULT_MALE_AVATAR_ID)
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun fixtureEmail(number: Int) = "room-pilot-$number@example.test"
private fun fixtureDisplayName(number: Int) = "Room Pilot $number"

private class FixtureSeedRefused(message: String) : IllegalArgumentException(message)

private fun requireFixture(condition: Boolean, message: String) {
    if (!condition) throw FixtureSeedRefused(message)
}
