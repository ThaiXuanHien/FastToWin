package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.postgresql.ds.PGSimpleDataSource
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class RailwayBaselineLatencyTest {
    @Test
    fun `probe distributions report exact small sample percentiles without retaining payloads`() {
        var now = 0L
        val recorder = BaselineLatencyRecorder { now }
        repeat(20) { index ->
            val result = recorder.measure(BaselineProbeStage.SELECT) { now += (index + 1) * 1_000_000L; "private-result" }
            assertEquals("private-result", result)
        }
        assertEquals(BaselineLatencySummary(20, 10.5, 20.0, 19.0, 0), recorder.summary(BaselineProbeStage.SELECT))
        assertFalse("private-result" in ProtocolJson.encodeToString(recorder.receipt()))
    }

    @Test
    fun `database probe commits a marker-only update and leaves the fixture identity unchanged`() = database { source, fixtureId ->
        val before = marker(source)
        val recorder = BaselineLatencyRecorder()
        measureBaselineDatabaseProbe(source, fixtureId, recorder)
        val after = marker(source)
        assertEquals(fixtureId.toString(), after.first)
        assertNotEquals(before.second, after.second, "The WAL-producing marker update must commit rather than roll back")
        for (stage in listOf(BaselineProbeStage.POOL, BaselineProbeStage.SELECT, BaselineProbeStage.UPDATE, BaselineProbeStage.COMMIT)) {
            assertEquals(1, recorder.summary(stage).count)
        }
    }

    @Test
    fun `a mismatched marker is refused before the write or commit probe`() = database { source, fixtureId ->
        val before = marker(source)
        val recorder = BaselineLatencyRecorder()
        assertFailsWith<IllegalStateException> { measureBaselineDatabaseProbe(source, UUID.randomUUID(), recorder) }
        assertEquals(before, marker(source))
        assertEquals(fixtureId.toString(), before.first)
        assertEquals(0, recorder.summary(BaselineProbeStage.UPDATE).count)
        assertEquals(0, recorder.summary(BaselineProbeStage.COMMIT).count)
    }

    private fun marker(source: PGSimpleDataSource) = source.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT fixture_id::text,xmin::text FROM room_ownership_pilot_fixture").use { rows ->
                check(rows.next()); rows.getString(1) to rows.getString(2)
            }
        }
    }

    private fun database(block: (PGSimpleDataSource, UUID) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        fun source(schema: String? = null) = PGSimpleDataSource().apply {
            setURL(url); user = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD")
            if (schema != null) currentSchema = schema
        }
        val schema = "latency_probe_" + UUID.randomUUID().toString().replace("-", "")
        val fixtureId = UUID.randomUUID()
        source().connection.use { control ->
            control.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            try {
                val database = source(schema)
                database.connection.use { connection ->
                    connection.createStatement().use { it.execute("CREATE TABLE room_ownership_pilot_fixture(singleton boolean PRIMARY KEY DEFAULT TRUE CHECK(singleton), fixture_id uuid NOT NULL)") }
                    connection.prepareStatement("INSERT INTO room_ownership_pilot_fixture(fixture_id) VALUES (?)").use {
                        it.setObject(1, fixtureId); it.executeUpdate()
                    }
                }
                block(database, fixtureId)
            } finally { control.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}
