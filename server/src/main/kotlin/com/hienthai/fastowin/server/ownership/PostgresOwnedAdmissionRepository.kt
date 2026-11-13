package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.withGameplayReadTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/** One cluster authority. Construction and observation never initialize or activate it. */
internal class PostgresOwnedAdmissionRepository(private val source: DataSource) {
    suspend fun observe(): OwnedAdmissionPolicy? = withContext(Dispatchers.IO) {
        source.connection.use { connection -> connection.withGameplayReadTimeout { observeOn(connection) } }
    }

    fun observeOn(connection: Connection): OwnedAdmissionPolicy? = connection.createStatement().use { statement ->
            statement.queryTimeout = 2
            statement.executeQuery("SELECT generation, state, topology_sha256, policy_sha256 FROM owned_admission_policy WHERE singleton")
                .use { rows -> if (rows.next()) rows.ownedAdmissionPolicy() else null }
    }

    suspend fun bootstrap(expectedGeneration: Long, binding: OwnedAdmissionBinding): OwnedAdmissionPolicy =
        transaction { bootstrapOn(this, expectedGeneration, binding) }

    /** Trusted initializer only: retains the caller's account/profile transaction and commit decision. */
    fun bootstrapOn(connection: Connection, expectedGeneration: Long, binding: OwnedAdmissionBinding): OwnedAdmissionPolicy {
        val previous = lock(connection, update = true)
            ?: throw OwnedAdmissionRejected("Missing owned admission authority")
        if (previous.generation != expectedGeneration || previous.state != OwnedAdmissionState.STOP_NEW ||
            previous.topologySha256 != null || previous.policySha256 != null
        ) throw OwnedAdmissionRejected("Owned admission authority was already initialized or changed")
        return replace(connection, OwnedAdmissionState.ACTIVE, binding)
    }

    suspend fun resume(expectedGeneration: Long, binding: OwnedAdmissionBinding): OwnedAdmissionPolicy = transaction {
        val previous = lock(this, update = true) ?: throw OwnedAdmissionRejected("Missing owned admission authority")
        if (previous.generation != expectedGeneration || previous.state != OwnedAdmissionState.STOP_NEW ||
            previous.topologySha256 != binding.topologySha256 || previous.policySha256 != binding.policySha256
        ) throw OwnedAdmissionRejected("Owned admission resume is stale or configuration does not match")
        replace(this, OwnedAdmissionState.ACTIVE, binding)
    }

    suspend fun stopNew(expectedGeneration: Long): OwnedAdmissionPolicy = transaction {
        val previous = lock(this, update = true) ?: throw OwnedAdmissionRejected("Missing owned admission authority")
        if (previous.generation != expectedGeneration ||
            previous.state !in setOf(OwnedAdmissionState.ACTIVE, OwnedAdmissionState.STOP_NEW)
        ) throw OwnedAdmissionRejected("Owned admission stop is stale or in an incompatible state")
        if (previous.state == OwnedAdmissionState.STOP_NEW) previous
        else replace(this, OwnedAdmissionState.STOP_NEW,
            OwnedAdmissionBinding(requireNotNull(previous.topologySha256), requireNotNull(previous.policySha256)))
    }

    /** Advance only from the exact committed phase, while holding its lock across the global zero sample. */
    suspend fun advanceDrain(expected: OwnedAdmissionPolicy, binding: OwnedAdmissionBinding,
        next: OwnedAdmissionState): OwnedAdmissionPolicy = transaction {
        val actual = lock(this, update = true)
        if (actual != expected || actual.topologySha256 != binding.topologySha256 ||
            actual.policySha256 != binding.policySha256 ||
            !((actual.state == OwnedAdmissionState.STOP_NEW && next == OwnedAdmissionState.QUIESCING) ||
                (actual.state == OwnedAdmissionState.QUIESCING && next == OwnedAdmissionState.DISABLED))
        ) throw OwnedAdmissionRejected("Owned drain phase or configuration changed")
        val empty = createStatement().use { statement ->
            statement.executeQuery("""
                SELECT NOT EXISTS (SELECT 1 FROM room_ownership_pilot_rooms
                    WHERE closed_at IS NULL OR
                        (snapshot->'pendingCompletedMatch' IS NOT NULL AND snapshot->'pendingCompletedMatch' <> 'null'::jsonb))
            """.trimIndent()).use { rows -> check(rows.next()); rows.getBoolean(1) }
        }
        if (!empty) throw OwnedAdmissionRejected("Owned drain still has durable work")
        replace(this, next, binding)
    }
    /** Explicit recovery only: retain the phase lock while checking zero and allocating ACTIVE. */
    suspend fun resumeDrained(expected: OwnedAdmissionPolicy, binding: OwnedAdmissionBinding): OwnedAdmissionPolicy = transaction {
        val actual = lock(this, update = true)
        if (actual != expected || actual.state !in setOf(OwnedAdmissionState.QUIESCING, OwnedAdmissionState.DISABLED) ||
            actual.topologySha256 != binding.topologySha256 || actual.policySha256 != binding.policySha256
        ) throw OwnedAdmissionRejected("Drain recovery authority changed")
        val empty = createStatement().use { statement ->
            statement.executeQuery("""
                SELECT NOT EXISTS (SELECT 1 FROM room_ownership_pilot_rooms WHERE closed_at IS NULL OR
                    (snapshot->'pendingCompletedMatch' IS NOT NULL AND snapshot->'pendingCompletedMatch' <> 'null'::jsonb))
            """.trimIndent()).use { rows -> check(rows.next()); rows.getBoolean(1) }
        }
        if (!empty) throw OwnedAdmissionRejected("Drain recovery still has durable work")
        replace(this, OwnedAdmissionState.ACTIVE, binding)
    }

    /** Revalidate an already disabled authority and global work in one locked transaction. */
    suspend fun verifyDisabledZero(expected: OwnedAdmissionPolicy, binding: OwnedAdmissionBinding): RoomOwnershipDurableSnapshot? = transaction {
        val actual = lock(this, update = false)
        if (actual != expected || actual.state != OwnedAdmissionState.DISABLED ||
            actual.topologySha256 != binding.topologySha256 || actual.policySha256 != binding.policySha256
        ) throw OwnedAdmissionRejected("Disabled drain authority changed")
        createStatement().use { statement ->
            statement.executeQuery("""
                SELECT count(*) FILTER (WHERE closed_at IS NULL AND lease_until > statement_timestamp()),
                    count(*) FILTER (WHERE closed_at IS NULL),
                    count(*) FILTER (WHERE snapshot->'pendingCompletedMatch' IS NOT NULL AND snapshot->'pendingCompletedMatch' <> 'null'::jsonb)
                FROM room_ownership_pilot_rooms
            """.trimIndent()).use { rows ->
                check(rows.next())
                RoomOwnershipDurableSnapshot(rows.getInt(1), rows.getInt(2), rows.getInt(3))
            }
        }
    }

    /** Sample only after the caller has acknowledged a committed stop. Hold its generation while counting. */
    suspend fun maintenanceWorkCount(expected: OwnedAdmissionPolicy, binding: OwnedAdmissionBinding): Int = transaction {
        val actual = lock(this, update = false)
        if (actual != expected || actual.state == OwnedAdmissionState.ACTIVE ||
            actual.topologySha256 != binding.topologySha256 || actual.policySha256 != binding.policySha256
        ) throw OwnedAdmissionRejected("Owned maintenance sample requires a current stopped policy")
        createStatement().use { statement ->
            statement.executeQuery("""
                SELECT count(*) FROM room_ownership_pilot_rooms
                WHERE closed_at IS NULL OR
                    (snapshot->'pendingCompletedMatch' IS NOT NULL AND snapshot->'pendingCompletedMatch' <> 'null'::jsonb)
            """.trimIndent()).use { rows -> check(rows.next()); rows.getInt(1) }
        }
    }

    fun requireNewIntent(connection: Connection, expected: OwnedAdmissionPolicy, binding: OwnedAdmissionBinding) {
        requireOwnedNewIntent(lock(connection, update = false), expected, binding)
    }

    private fun lock(connection: Connection, update: Boolean): OwnedAdmissionPolicy? {
        require(!connection.autoCommit) { "Owned admission guard requires the caller's transaction" }
        return connection.createStatement().use { statement ->
            statement.executeQuery("SELECT generation, state, topology_sha256, policy_sha256 FROM owned_admission_policy " +
                "WHERE singleton FOR ${if (update) "UPDATE" else "SHARE"}").use { rows ->
                if (rows.next()) rows.ownedAdmissionPolicy() else null
            }
        }
    }

    private fun replace(connection: Connection, state: OwnedAdmissionState, binding: OwnedAdmissionBinding): OwnedAdmissionPolicy =
        connection.prepareStatement("""
            UPDATE owned_admission_policy SET generation=nextval('owned_admission_generation_seq'),
                state=?, topology_sha256=?, policy_sha256=?, updated_at=clock_timestamp()
            WHERE singleton RETURNING generation, state, topology_sha256, policy_sha256
        """.trimIndent()).use { statement ->
            statement.setString(1, state.name)
            statement.setString(2, binding.topologySha256)
            statement.setString(3, binding.policySha256)
            statement.executeQuery().use { rows -> check(rows.next()); rows.ownedAdmissionPolicy() }
        }

    private suspend fun <T> transaction(block: Connection.() -> T): T = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.withGameplayReadTimeout {
            connection.autoCommit = false
            try {
                connection.createStatement().use { it.execute("SET LOCAL lock_timeout='1s'; SET LOCAL statement_timeout='2s'") }
                val result = connection.block()
                connection.commit()
                result
            } catch (failure: Throwable) {
                runCatching { connection.rollback() }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
            }
        }
    }
}

internal fun ResultSet.ownedAdmissionPolicy() = OwnedAdmissionPolicy(
    getLong("generation"), OwnedAdmissionState.valueOf(getString("state")),
    getString("topology_sha256"), getString("policy_sha256")
)
