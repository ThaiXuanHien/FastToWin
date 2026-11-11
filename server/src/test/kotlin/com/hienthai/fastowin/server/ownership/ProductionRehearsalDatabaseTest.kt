package com.hienthai.fastowin.server.ownership

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ProductionRehearsalDatabaseTest {
    @Test
    fun `production workload requires exact database role accounts and an empty marker table`() = database { source, acceptance, admin ->
        source.connection.use { c ->
            verifyRoomAcceptanceDatabase(c, acceptance)
            assertFailsWith<IllegalStateException> {
                verifyRoomAcceptanceDatabase(c, acceptance.copy(accountIds = acceptance.accountIds.drop(1)))
            }
            assertFailsWith<IllegalStateException> {
                verifyRoomAcceptanceDatabase(c, acceptance.copy(fixtureId = UUID.randomUUID()))
            }
            c.prepareStatement("INSERT INTO room_ownership_pilot_fixture (fixture_id) VALUES (?)").use {
                it.setObject(1, UUID.randomUUID()); it.executeUpdate()
            }
            assertFailsWith<IllegalStateException> { verifyRoomAcceptanceDatabase(c, acceptance) }
            c.createStatement().use { it.executeUpdate("DELETE FROM room_ownership_pilot_fixture") }
            verifyRoomAcceptanceDatabase(c, acceptance)
        }
        admin.connection.use { c ->
            assertFailsWith<IllegalStateException> { verifyRoomAcceptanceDatabase(c, acceptance) }
        }
    }

    @Test
    fun `production latency probe commits one unchanged profile row without a fixture marker`() = database { source, settings, _ ->
        val before = profiles(source)
        val recorder = BaselineLatencyRecorder()
        measureBaselineDatabaseProbe(source, settings.fixtureId, recorder, settings)
        val after = profiles(source)
        assertEquals(before.mapValues { it.value.first }, after.mapValues { it.value.first })
        val probeAccount = settings.accountIds.first().toString()
        assertNotEquals(before.getValue(probeAccount).second, after.getValue(probeAccount).second)
        for (account in before.keys - probeAccount) assertEquals(before.getValue(account), after.getValue(account))
        for (stage in listOf(BaselineProbeStage.POOL, BaselineProbeStage.SELECT, BaselineProbeStage.UPDATE, BaselineProbeStage.COMMIT)) {
            assertEquals(1, recorder.summary(stage).count)
            assertEquals(0, recorder.summary(stage).failures)
        }
        assertEquals(0, recorder.summary(BaselineProbeStage.READY).count)
        for (wrong in listOf(settings.copy(accountIds = listOf(UUID.randomUUID())), settings.copy(fixtureId = UUID.randomUUID()))) {
            val rejected = BaselineLatencyRecorder()
            assertFailsWith<IllegalStateException> {
                measureBaselineDatabaseProbe(source, wrong.fixtureId, rejected, wrong)
            }
            assertEquals(after, profiles(source), "A rejected identity must not write or commit a profile version")
            assertEquals(0, rejected.summary(BaselineProbeStage.UPDATE).count)
            assertEquals(0, rejected.summary(BaselineProbeStage.COMMIT).count)
        }
    }

    private fun profiles(source: PGSimpleDataSource): Map<String, Pair<String, String>> = source.connection.use { c ->
        c.createStatement().use { statement ->
            statement.executeQuery("SELECT user_id::text, to_jsonb(p)::text, xmin::text FROM profiles p ORDER BY user_id").use { rows ->
                buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2) to rows.getString(3)) }
            }
        }
    }

    private fun database(block: (PGSimpleDataSource, RoomAcceptanceSettings, PGSimpleDataSource) -> Unit) {
        val raw = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!raw.isNullOrBlank(), "Isolated PostgreSQL is required")
        val uri = URI(requireNotNull(raw).removePrefix("jdbc:"))
        require(uri.host in setOf("localhost", "127.0.0.1") && uri.port in 1..65535)
        val seed = RoomOwnershipProductionSeedSettings.fromEnvironment(RoomOwnershipProductionSeedTest.values(UUID.randomUUID()))
        val name = seed.databaseName
        require(name.matches(Regex("room_rehearsal_[a-f0-9]{12}")))
        val control = PGSimpleDataSource().apply {
            setUrl(raw)
            user = System.getenv("TEST_DATABASE_USER")
            password = System.getenv("TEST_DATABASE_PASSWORD")
        }
        val targetUrl = "jdbc:postgresql://${uri.host}:${uri.port}/$name"
        val source = PGSimpleDataSource().apply {
            setUrl(targetUrl)
            user = name
            password = seed.database.password
        }
        val acceptance = RoomAcceptanceSettings(
            project = "room-prod-" + seed.runId.toString().replace("-", "").take(12),
            composeFiles = null, receipts = Path.of("receipts"), urls = emptyList(), proxyUrls = emptyList(),
            postgresPort = uri.port, suite = "Fault", backendCount = 2, durationSeconds = 600,
            roomCount = 20, seed = 73, fixtureId = seed.runId, accountIds = seed.accounts,
            databasePassword = seed.database.password, accountPassword = seed.password, productionLike = true
        )
        var roleCreated = false
        var databaseCreated = false
        try {
            control.connection.use { c -> c.createStatement().use {
                it.execute("CREATE ROLE $name LOGIN PASSWORD '${seed.database.password}'")
                roleCreated = true
                it.execute("CREATE DATABASE $name OWNER $name")
                databaseCreated = true
            } }
            seedRoomOwnershipProductionAccounts(source, seed)
            val admin = PGSimpleDataSource().apply {
                setUrl(targetUrl)
                user = System.getenv("TEST_DATABASE_USER")
                password = System.getenv("TEST_DATABASE_PASSWORD")
            }
            block(source, acceptance, admin)
        } finally {
            control.connection.use { c -> c.createStatement().use {
                if (databaseCreated) it.execute("DROP DATABASE $name WITH (FORCE)")
                if (roleCreated) it.execute("DROP ROLE $name")
            } }
        }
    }
}
