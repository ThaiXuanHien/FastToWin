package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

/** One-backend staging baseline uses the same load and acceptance gates as Docker local. */
internal fun runRailwayRoomBaseline(settings: RailwayRoomSmokeSettings) {
    var phase = "private-dns"
    var progress: BaselineLoadProgress? = null
    var receipts = Path.of("receipts")
    val latency = BaselineLatencyRecorder()
    var measuredFixture: RoomAcceptanceFixture? = null
    try {
        val acceptance = settings.acceptanceSettings()
        receipts = acceptance.receipts
        Files.createDirectories(acceptance.receipts)
        val receipt = runBlocking {
            withTimeout(900_000) {
                RoomAcceptanceFixture(acceptance).use { fixture ->
                    measuredFixture = fixture
                    phase = "fixture-marker-and-readiness"
                    fixture.verifyFixture()
                    fixture.awaitReady(0)
                    fixture.fundQuota()
                    withTimeout(45_000) { fixture.measureBaselineLatency(latency) }
                    fixture.resetDatabaseTimings()
                    phase = "load"
                    runRoomOwnershipLoad(fixture) { progress = it }
                }
            }
        }
        phase = "receipt"
        val bytes = ProtocolJson.encodeToString(receipt).toByteArray(Charsets.UTF_8)
        check(bytes.size <= 262_144) { "Receipt exceeds export limit" }
        Files.write(acceptance.receipts.resolve("load.json"), bytes)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        // Railway log lines are bounded. Export the complete numeric/metric receipt
        // in indexed chunks so the host can detect missing or corrupted lines.
        val chunks = Base64.getEncoder().encodeToString(bytes).chunked(2_048)
        chunks.forEachIndexed { index, chunk ->
            println("FTW_BASELINE_RECEIPT ${index + 1}/${chunks.size} $sha256 $chunk")
        }
        println("""{"baselineAccepted":true,"backendCount":1,"concurrentRooms":20,"configuredDurationSeconds":600,"selectionCount":${receipt.selectionCount},"actualSelectionsPerSecond":${receipt.actualSelectionsPerSecond},"fullAcceptanceProfile":${receipt.fullAcceptanceProfile},"receiptSha256":"$sha256"}""")
    } catch (failure: Exception) {
        val diagnostic = baselineFailureReceipt(phase, failure, receipts, progress)
        val bytes = ProtocolJson.encodeToString(diagnostic).toByteArray(Charsets.UTF_8)
        // A failed run must remain diagnosable even if its container disappears.
        runCatching { Files.createDirectories(receipts); Files.write(receipts.resolve("baseline-failure.json"), bytes) }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val chunks = Base64.getEncoder().encodeToString(bytes).chunked(2_048)
        chunks.forEachIndexed { index, chunk -> println("FTW_BASELINE_FAILURE ${index + 1}/${chunks.size} $hash $chunk") }
        // JDBC/client exceptions can contain fixture identifiers or connection
        // strings. Keep those outside provider logs and the exported receipt.
        throw IllegalStateException("Baseline failed during $phase")
    } finally {
        val bytes = ProtocolJson.encodeToString(latency.receipt(measuredFixture?.databaseTimingMetrics().orEmpty())).toByteArray(Charsets.UTF_8)
        runCatching { Files.createDirectories(receipts); Files.write(receipts.resolve("baseline-latency.json"), bytes) }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val chunks = Base64.getEncoder().encodeToString(bytes).chunked(2_048)
        chunks.forEachIndexed { index, chunk -> println("FTW_BASELINE_LATENCY ${index + 1}/${chunks.size} $hash $chunk") }
    }
}
