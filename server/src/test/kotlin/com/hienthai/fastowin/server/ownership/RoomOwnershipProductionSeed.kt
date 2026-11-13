package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.DEFAULT_MALE_AVATAR_ID
import com.hienthai.fastowin.server.DatabaseSettings
import com.hienthai.fastowin.server.PasswordHasher
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import java.net.InetAddress
import java.util.UUID
import javax.sql.DataSource

/** Configuration is bound to one disposable staging database before opening a connection. */
internal class RoomOwnershipProductionSeedSettings private constructor(
    val database: DatabaseSettings,
    val databaseName: String,
    val databaseHost: String,
    val accounts: List<UUID>,
    val password: String,
    val runId: UUID,
    val manifestSha256: String,
    val topologySocial: Boolean,
    val admissionBinding: OwnedAdmissionBinding
) {
    val scope = "room-ownership-production"

    fun dataSource(resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }): PGSimpleDataSource {
        val pinned = privateAddresses(databaseHost, resolve).sortedBy(InetAddress::getHostAddress).first()
        return PGSimpleDataSource().apply {
            setUrl("jdbc:postgresql://" + pinned.socketHost() + ":5432/" + databaseName)
            user = database.user
            password = database.password
            connectTimeout = 4
            socketTimeout = 15
        }
    }

    companion object {
        fun fromEnvironment(values: Map<String, String>): RoomOwnershipProductionSeedSettings {
            require(values["RAILWAY_PROJECT_ID"] == STAGING_PROJECT &&
                values["RAILWAY_ENVIRONMENT_ID"] == STAGING_ENVIRONMENT &&
                values["RAILWAY_ENVIRONMENT_NAME"] == "staging") { "Unexpected rehearsal environment" }
            require(values["FASTTOWIN_ENV"] == "prod" &&
                values["FTW_REHEARSAL_SCOPE"] == "room-ownership-production" &&
                values["FASTTOWIN_ROOM_OWNERSHIP_MODE"] == "production")
            require(values.keys.none {
                it.contains("FIXTURE", ignoreCase = true) || it.startsWith("PG") ||
                    it in setOf("RAILWAY_TOKEN", "RAILWAY_API_TOKEN", "FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE")
            }) { "Unexpected rehearsal settings" }
            val runId = requireNotNull(values["FTW_REHEARSAL_ID"]?.let(::parsePilotUuid))
            val suffix = runId.toString().replace("-", "").take(12)
            val prefix = values["FTW_REHEARSAL_PREFIX"] ?: "room-prod"
            require(prefix in setOf("room-prod", "room-pilot"))
            val run = prefix + "-" + suffix
            require(values["RAILWAY_SERVICE_NAME"] == run + "-runner")
            val host = run + "-pg.railway.internal"
            val database = "room_rehearsal_" + suffix
            require(values["DATABASE_URL"] == "jdbc:postgresql://" + host + ":5432/" + database &&
                values["DATABASE_USER"] == database) { "Expected a dedicated rehearsal database" }
            require(values["DATABASE_PASSWORD"]?.matches(Regex("[a-f0-9]{64}")) == true)
            val manifest = requireNotNull(values["FTW_REHEARSAL_MANIFEST_SHA256"])
            require(manifest.matches(Regex("[a-f0-9]{64}")))
            val accounts = requireNotNull(values["FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS"])
                .split(',').map { requireNotNull(parsePilotUuid(it)) }
            require(accounts.size in 2..256 && accounts.toSet().size == accounts.size)
            val topologySocial = values["FTW_REHEARSAL_PROFILE"] == "topology"
            if (topologySocial) require(accounts.size == 40)
            val password = requireNotNull(values["FASTTOWIN_ROOM_OWNERSHIP_REHEARSAL_PASSWORD"])
            require(password.matches(Regex("Rehearsal1![a-f0-9]{64}")))
            val policy = requireNotNull(RoomOwnershipProductionPolicy.fromEnvironment(values))
            require(policy.runtimeRole == RoomOwnershipRuntimeRole.INGRESS) { "Seeder requires the manifest ingress topology" }
            return RoomOwnershipProductionSeedSettings(
                requireNotNull(DatabaseSettings.fromEnvironment("prod", values)), database, host,
                accounts, password, runId, manifest, topologySocial, OwnedAdmissionBinding.from(policy)
            )
        }
    }
}

private fun seedTopologySocial(c: java.sql.Connection, settings: RoomOwnershipProductionSeedSettings) {
    val players = settings.accounts.take(4)
    val friendViewer = settings.accounts[4]
    val clanViewer = settings.accounts[5]
    val clan = UUID.randomUUID()
    c.prepareStatement("""
        INSERT INTO friendships(id,requester_id,addressee_id,status,created_at,updated_at)
        VALUES(?,?,?,'ACCEPTED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
    """.trimIndent()).use { s ->
        for (player in players) {
            s.setObject(1, UUID.randomUUID()); s.setObject(2, player); s.setObject(3, friendViewer)
            check(s.executeUpdate() == 1)
        }
    }
    c.prepareStatement("INSERT INTO clans(id,name,owner_id,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP)").use { s ->
        s.setObject(1, clan); s.setString(2, "Topology " + settings.runId.toString().take(12)); s.setObject(3, clanViewer)
        check(s.executeUpdate() == 1)
    }
    val roles = players.associateWith { "MEMBER" } + (clanViewer to "LEADER")
    c.prepareStatement("INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES(?,?,?,CURRENT_TIMESTAMP)").use { s ->
        for ((user, role) in roles) {
            s.setObject(1, clan); s.setObject(2, user); s.setString(3, role); check(s.executeUpdate() == 1)
        }
    }
    c.createStatement().use { s ->
        s.executeQuery("SELECT count(*) FROM profiles").use { r -> check(r.next() && r.getInt(1) == 40) }
        s.executeQuery("SELECT requester_id,addressee_id,status FROM friendships").use { r ->
            val observed = mutableListOf<UUID>()
            while (r.next()) {
                check(r.getObject(2) == friendViewer && r.getString(3) == "ACCEPTED")
                observed += r.getObject(1, UUID::class.java)
            }
            check(observed.size == 4 && observed.toSet() == players.toSet())
        }
        s.executeQuery("SELECT c.id,c.owner_id,m.user_id,m.role FROM clans c JOIN clan_members m ON c.id=m.clan_id").use { r ->
            val actual = mutableMapOf<UUID,String>()
            while (r.next()) {
                check(r.getObject(1) == clan && r.getObject(2) == clanViewer)
                check(actual.put(r.getObject(3, UUID::class.java), r.getString(4)) == null)
            }
            check(actual == roles)
        }
    }
}

/** Holds the initializer lock through preflight, migration and account insertion. */
internal fun seedRoomOwnershipProductionAccounts(
    dataSource: DataSource,
    settings: RoomOwnershipProductionSeedSettings
): OwnedAdmissionPolicy {
    dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                statement.execute("SELECT pg_catalog.pg_advisory_xact_lock(6075464190125381698)")
                statement.executeQuery("SELECT current_database(), current_user").use { rows ->
                    check(rows.next())
                    require(rows.getString(1) == settings.databaseName &&
                        rows.getString(2) == settings.database.user) { "Unexpected rehearsal database identity" }
                }
                statement.executeQuery("""
                    SELECT EXISTS (SELECT FROM pg_catalog.pg_namespace
                        WHERE nspname NOT IN ('public', 'information_schema') AND nspname !~ '^pg_')
                        OR EXISTS (
                            SELECT FROM pg_catalog.pg_depend d
                            JOIN pg_catalog.pg_namespace n ON n.oid = d.refobjid
                            WHERE d.refclassid = 'pg_catalog.pg_namespace'::pg_catalog.regclass
                                AND n.nspname = 'public'
                        )
                """.trimIndent()).use { rows ->
                    check(rows.next())
                    require(!rows.getBoolean(1)) { "Rehearsal database must be empty before migration" }
                }
            }
            // Flyway uses its own committed transaction after the dedicated-target checks.
            Flyway.configure().dataSource(dataSource).defaultSchema("public")
                .locations("classpath:db/migration").load().migrate()
            val hashes = settings.accounts.map { PasswordHasher().hash(settings.password) }
            connection.prepareStatement("""
                INSERT INTO users
                    (id, account_type, status, email_normalized, password_hash, email_verified_at, created_at, updated_at)
                VALUES (?, 'REGISTERED', 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent()).use { statement ->
                settings.accounts.forEachIndexed { index, account ->
                    statement.setObject(1, account)
                    statement.setString(2, "room-production-" + (index + 1) + "@example.test")
                    statement.setString(3, hashes[index])
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            connection.prepareStatement("""
                INSERT INTO profiles
                    (user_id, display_name, player_code, avatar_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent()).use { statement ->
                settings.accounts.forEachIndexed { index, account ->
                    val number = index + 1
                    statement.setObject(1, account)
                    statement.setString(2, "Room Production " + number)
                    statement.setString(3, "RPROD" + number.toString().padStart(6, '0'))
                    statement.setString(4, DEFAULT_MALE_AVATAR_ID)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            if (settings.topologySocial) seedTopologySocial(connection, settings)
            // Accounts, profiles and ACTIVE binding commit atomically on this existing seed connection.
            // Flyway has already committed the initial unbound STOP_NEW row; failure here leaves it stopped.
            val authority = PostgresOwnedAdmissionRepository(dataSource)
            val initial = requireNotNull(authority.observeOn(connection))
            val active = authority.bootstrapOn(connection, initial.generation, settings.admissionBinding)
            connection.commit()
            return active
        } catch (failure: Exception) {
            connection.rollback()
            throw failure
        }
    }
}
