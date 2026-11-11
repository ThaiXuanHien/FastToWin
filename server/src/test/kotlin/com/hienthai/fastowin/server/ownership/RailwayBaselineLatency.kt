package com.hienthai.fastowin.server.ownership

import kotlinx.serialization.Serializable
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource
import kotlin.math.ceil

internal enum class BaselineProbeStage { POOL, SELECT, UPDATE, COMMIT, READY }

@Serializable
internal data class BaselineLatencySummary(val count: Int, val meanMillis: Double, val maxMillis: Double, val p95Millis: Double?, val failures: Int)

@Serializable
internal data class BaselineLatencyReceipt(
    val scope: String = "preflight-fixture-probes-and-workload-timings",
    val expectedIterations: Int = 12,
    val complete: Boolean,
    val poolAcquire: BaselineLatencySummary,
    val selectRoundTrip: BaselineLatencySummary,
    // Legacy receipt label: production rehearsal measures an unchanged profile row instead.
    val markerUpdateRoundTrip: BaselineLatencySummary,
    val commitRoundTrip: BaselineLatencySummary,
    val readinessRoundTrip: BaselineLatencySummary,
    val backendBeforeLoadMetrics: Map<String, Double>,
    val fixtureDatabaseMetrics: Map<String, Double>
)

internal class BaselineLatencyRecorder(private val nanoTime: () -> Long = System::nanoTime) {
    private val samples = BaselineProbeStage.entries.associateWith { mutableListOf<Long>() }
    private val failures = BaselineProbeStage.entries.associateWith { 0 }.toMutableMap()
    var backendBeforeLoadMetrics: Map<String, Double> = emptyMap()
    fun <T> measure(stage: BaselineProbeStage, operation: () -> T): T {
        check(samples.getValue(stage).size < 32) { "Probe sample bound exceeded" }
        val started = nanoTime()
        try { return operation() }
        catch (error: Throwable) { failures[stage] = failures.getValue(stage) + 1; throw error }
        finally { samples.getValue(stage).add((nanoTime() - started).coerceAtLeast(0)) }
    }
    suspend fun <T> measureSuspending(stage: BaselineProbeStage, operation: suspend () -> T): T {
        check(samples.getValue(stage).size < 32) { "Probe sample bound exceeded" }
        val started = nanoTime()
        try { return operation() }
        catch (error: Throwable) { failures[stage] = failures.getValue(stage) + 1; throw error }
        finally { samples.getValue(stage).add((nanoTime() - started).coerceAtLeast(0)) }
    }
    fun summary(stage: BaselineProbeStage): BaselineLatencySummary {
        val values = samples.getValue(stage).map { it / 1_000_000.0 }.sorted()
        return BaselineLatencySummary(values.size, if (values.isEmpty()) 0.0 else values.average(), values.lastOrNull() ?: 0.0,
            values.takeIf { it.isNotEmpty() }?.get(ceil(values.size * 0.95).toInt() - 1), failures.getValue(stage))
    }
    fun receipt(fixtureMetrics: Map<String, Double> = emptyMap()) = BaselineLatencyReceipt(
        complete = BaselineProbeStage.entries.all { samples.getValue(it).size == 12 && failures.getValue(it) == 0 },
        poolAcquire = summary(BaselineProbeStage.POOL), selectRoundTrip = summary(BaselineProbeStage.SELECT),
        markerUpdateRoundTrip = summary(BaselineProbeStage.UPDATE), commitRoundTrip = summary(BaselineProbeStage.COMMIT),
        readinessRoundTrip = summary(BaselineProbeStage.READY), backendBeforeLoadMetrics = backendBeforeLoadMetrics,
        fixtureDatabaseMetrics = fixtureMetrics
    )
}

/** One WAL-producing transaction on an unchanged fixture marker or a verified production rehearsal profile. */
internal fun measureBaselineDatabaseProbe(
    source: DataSource,
    fixtureId: UUID,
    recorder: BaselineLatencyRecorder,
    productionSettings: RoomAcceptanceSettings? = null
) {
    recorder.measure(BaselineProbeStage.POOL) { source.connection }.use { connection ->
        val previousNetworkTimeout = connection.networkTimeout
        connection.setNetworkTimeout(Runnable::run, 2_500)
        connection.autoCommit = false
        try {
            if (productionSettings != null) {
                check(productionSettings.productionLike && productionSettings.fixtureId == fixtureId)
                verifyRoomAcceptanceDatabase(connection, productionSettings)
            } else {
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT fixture_id FROM room_ownership_pilot_fixture WHERE singleton").use { rows ->
                        check(rows.next() && rows.getObject(1, UUID::class.java) == fixtureId && !rows.next()) { "Probe fixture marker mismatch" }
                    }
                }
            }
            recorder.measure(BaselineProbeStage.SELECT) {
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT 1").use { rows -> check(rows.next() && rows.getInt(1) == 1) }
                }
            }
            recorder.measure(BaselineProbeStage.UPDATE) {
                if (productionSettings != null) {
                    connection.prepareStatement("UPDATE profiles SET avatar_url=avatar_url WHERE user_id=?").use { statement ->
                        statement.setObject(1, productionSettings.accountIds.first())
                        check(statement.executeUpdate() == 1) { "Probe production rehearsal profile missing" }
                    }
                } else {
                    connection.prepareStatement("UPDATE room_ownership_pilot_fixture SET fixture_id=fixture_id WHERE singleton AND fixture_id=?").use { statement ->
                        statement.setObject(1, fixtureId)
                        check(statement.executeUpdate() == 1) { "Probe fixture marker changed" }
                    }
                }
            }
            recorder.measure(BaselineProbeStage.COMMIT) { connection.commit() }
        } finally {
            if (!connection.isClosed) {
                runCatching { connection.rollback() }
                runCatching { connection.setNetworkTimeout(Runnable::run, previousNetworkTimeout) }
            }
        }
    }
}

internal suspend fun RoomAcceptanceFixture.measureBaselineLatency(recorder: BaselineLatencyRecorder, node: Int = 0) {
    verifyFixture() // Exact database identity/marker policy and synthetic account allowlist before writing.
    repeat(12) {
        withContext(Dispatchers.IO) {
            measureBaselineDatabaseProbe(source, settings.fixtureId, recorder, settings.takeIf { it.productionLike })
        }
        recorder.measureSuspending(BaselineProbeStage.READY) {
            val response = client.get(settings.urls[node] + "/ready")
            check(response.status == HttpStatusCode.OK && response.bodyAsText() == "READY") { "Probe readiness unavailable" }
        }
    }
    recorder.backendBeforeLoadMetrics = baselineSafeMetrics(metrics()[node])
}
