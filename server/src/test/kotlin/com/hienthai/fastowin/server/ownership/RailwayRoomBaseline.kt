package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64

/** One-backend staging baseline uses the same load and acceptance gates as Docker local. */
internal fun runRailwayRoomBaseline(settings: RailwayRoomSmokeSettings) {
    var phase = "private-dns"
    try {
        val acceptance = settings.acceptanceSettings()
        Files.createDirectories(acceptance.receipts)
        val receipt = runBlocking {
            withTimeout(900_000) {
                RoomAcceptanceFixture(acceptance).use { fixture ->
                    phase = "fixture-marker-and-readiness"
                    fixture.verifyFixture()
                    fixture.awaitReady(0)
                    fixture.fundQuota()
                    phase = "load"
                    runRoomOwnershipLoad(fixture)
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
    } catch (_: Exception) {
        // JDBC/client exceptions can contain fixture identifiers or connection
        // strings. Keep those outside provider logs and the exported receipt.
        throw IllegalStateException("Baseline failed during $phase")
    }
}
