package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.sql.SQLException
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RailwayBaselineFailureTest {
    @Test
    fun `exports safe numeric diagnostics while rejecting unknown keys and metric labels`() {
        val directory = Files.createTempDirectory("baseline-failure")
        try {
            Files.writeString(directory.resolve("failure-counts.json"), """{"matches":423,"pendingSettlements":0,"settlements":423,"expiredLeases":0,"account-secret":999}""")
            Files.writeString(directory.resolve("failure-metrics-0.prom"), """
                fasttowin_room_ownership_pool_active_connections 10
                fasttowin_room_ownership_pool_max_connections 10
                fasttowin_room_ownership_oldest_queued_event_seconds 0.25
                fasttowin_room_ownership_settlement_failures_total 0
                fasttowin_jvm_memory_used_bytes NaN
                fasttowin_websocket_connections{player="account-secret"} 123
                arbitrary_secret 456
            """.trimIndent())
            Files.writeString(directory.resolve("failure-stack.txt"), "Password=Pilot1!secret; jdbc:postgresql://private/fixture; player=account-secret")
            val failure = baselineFailureReceipt("load", SQLException("Password=Pilot1!secret", "08006"), directory, null)
            assertEquals("database-connection", failure.category)
            assertEquals(mapOf("matches" to 423L, "pendingSettlements" to 0L, "settlements" to 423L, "expiredLeases" to 0L), failure.counts)
            assertEquals(4, failure.metrics.size)
            assertEquals(10.0, failure.metrics["fasttowin_room_ownership_pool_active_connections"])
            val exported = ProtocolJson.encodeToString(failure)
            listOf("Password", "Pilot1!", "jdbc:", "account-secret", "player", "arbitrary_secret", "NaN").forEach {
                assertFalse(it in exported, "Unsafe value in failure export: $it")
            }
        } finally {
            listOf("failure-counts.json", "failure-metrics-0.prom", "failure-stack.txt").forEach { directory.resolve(it).deleteIfExists() }
            directory.deleteIfExists()
        }
    }

    @Test
    fun `timeout includes the last measured room progress without exception text`() = runBlocking {
        val timeout = try {
            withTimeout(1) { delay(10_000) }
            error("Expected timeout")
        } catch (failure: TimeoutCancellationException) { failure }
        val progress = BaselineLoadProgress(630.0, List(20) { 1100L }, 440, 22_000)
        val directory = Files.createTempDirectory("baseline-failure")
        try {
            val receipt = baselineFailureReceipt("load", IllegalStateException("sensitive-wrapper", timeout), directory, progress)
            assertEquals("timeout", receipt.category)
            assertEquals(progress, receipt.progress)
            assertEquals(22_000L, receipt.progress!!.selectionsByRoomSlot.sum())
            assertTrue(receipt.counts.isEmpty())
            assertTrue(receipt.metrics.isEmpty())
            assertFalse("sensitive-wrapper" in ProtocolJson.encodeToString(receipt))
        } finally { directory.deleteIfExists() }
    }

    @Test
    fun `unknown phase exception messages malformed and oversized diagnostics stay private`() {
        val directory = Files.createTempDirectory("baseline-failure")
        try {
            Files.writeString(directory.resolve("failure-counts.json"), "{invalid-secret")
            Files.writeString(directory.resolve("failure-metrics-0.prom"), "private-secret".repeat(10_000))
            val receipt = baselineFailureReceipt("secret-phase", IllegalStateException("private-secret"), directory, null)
            assertEquals("unknown", receipt.phase)
            assertEquals("assertion", receipt.category)
            assertTrue(receipt.counts.isEmpty())
            assertTrue(receipt.metrics.isEmpty())
            assertFalse("secret" in ProtocolJson.encodeToString(receipt))
        } finally {
            directory.resolve("failure-counts.json").deleteIfExists()
            directory.resolve("failure-metrics-0.prom").deleteIfExists()
            directory.deleteIfExists()
        }
    }
}
