package com.hienthai.fastowin.server.ownership

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

class RehearsalReceiptTest {
    private val result = RoomOwnershipFaultCaseReceipt("kill-after-committed-selection", roomId = "private-room",
        matchId = "private-match", beforeEpoch = 1, afterEpoch = 2, beforeRevision = 3, afterRevision = 4,
        recoveryMillis = 9000, evidence = mapOf("acknowledgedSelectionPreserved" to "true", "quotaDebits" to "2",
            "neverExport" to "SELECT secret"))
    private val ops = listOf("a" to "kill", "a" to "start")
    @Test fun caseProjectionRequiresVerifiedFaultAndRestore() {
        val safe = faultCaseProjection(result, ops)
        assertFalse(safe.toString().contains("private"))
        assertFalse(safe.toString().contains("secret"))
        assertEquals("backend-kill", safe.getValue("mechanism").jsonPrimitive.content)
        for (bad in listOf(emptyList(), ops.take(1), listOf("a" to "kill","b" to "start"), listOf("a" to "pause","a" to "resume")))
            assertFails { faultCaseProjection(result,bad) }
        assertFails { faultCaseProjection(result.copy(passed=false),ops) }
        assertFails { faultCaseProjection(result.copy(evidence=emptyMap()),ops) }
    }
    @Test fun onlyRealPausesAndSafeBackendMetricsEnterLoadProjection() {
        val scrape = "fasttowin_room_ownership_pool_max_connections 20\nprivate_account_id 123\nraw_sql SELECT"
        val load = LoadReceipt(seed=73, backendCount=2, concurrentRooms=20, configuredDurationSeconds=600,
            configuredSelectionsPerRoomPerSecond=5, fixtureRateLimits="private-setting", roomSetupSeconds=1.0,
            elapsedSeconds=600.0, selectionCount=60000, selectionsByRoomSlot=List(20) { 3000L },
            actualSelectionsPerSecond=100.0, completedMatches=1200, commandP95Millis=1.0, commandMaxMillis=2.0,
            commandAttempts=60000, commandRetries=0, minimumSelectionSpacingMillis=55, maxScheduledLatenessMillis=1.0,
            scheduledToAckP95Millis=1.0, recoveryMillis=listOf(9000,9000,9000), recoveryP95Millis=9000.0,
            maxQueuedEventSeconds=0.0, maxPostgresPoolSaturation=0.2, queuedEventP95Seconds=0.0,
            postgresPoolP95Saturation=0.2, resourceSamples=listOf(LoadResourceSample(600.0,0.0,0.2)), resourceSampleCount=1,
            fullAcceptanceProfile=true, finalBackendMetrics=listOf(scrape,scrape))
        val events = listOf("a","b","a").flatMap { listOf(it to "pause", it to "resume") }
        val payload = rehearsalLoadProjection(load, events)
        assertFalse("private" in payload.toString() || "SELECT" in payload.toString())
        assertEquals(2,payload.getValue("finalBackendMetrics").jsonArray.size)
        assertFails { rehearsalLoadProjection(load,events.dropLast(2)) }
        assertFails { rehearsalLoadProjection(load.copy(finalBackendMetrics=listOf(scrape)),events) }
    }

    @Test fun chunksPreserveDigestAndRejectOversize() {
        val data = buildJsonObject { put("samples", JsonArray(List(1200) { JsonPrimitive(it) })) }
        val lines = emitRehearsalReceipt("c".repeat(64),"fault","latency",data)
        assertTrue(lines.size > 1)
        val bytes = Base64.getDecoder().decode(lines.joinToString("") { it.substringAfterLast(' ') })
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        lines.forEachIndexed { index, line -> assertTrue(line.startsWith("FTW_REHEARSAL_LATENCY ${index+1}/${lines.size} $digest ")) }
        val decoded = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertEquals(data,decoded["payload"])
        assertFails { emitRehearsalReceipt("c".repeat(64),"fault","latency",buildJsonObject { put("s","x".repeat(262145)) }) }
    }
}
