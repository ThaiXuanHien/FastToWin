package com.hienthai.fastowin.server.ownership

import kotlinx.serialization.Serializable
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.sql.SQLException

@Serializable
internal data class BaselineLoadProgress(
    val elapsedSeconds: Double,
    val selectionsByRoomSlot: List<Long>,
    val completedMatches: Long,
    val commandAttempts: Long
)

@Serializable
internal data class BaselineFailureReceipt(
    val phase: String,
    val category: String,
    val progress: BaselineLoadProgress?,
    val counts: Map<String, Long>,
    val metrics: Map<String, Double>
)

private val failurePhases = setOf("private-dns", "fixture-marker-and-readiness", "load", "receipt")
private val countNames = setOf("pendingSettlements", "matches", "expiredLeases", "settlements")
private val metricNames = setOf(
    "fasttowin_uptime_seconds", "fasttowin_websocket_connections", "fasttowin_websocket_connections_total",
    "fasttowin_websocket_messages_total", "fasttowin_websocket_rate_limited_total",
    "fasttowin_jvm_memory_used_bytes", "fasttowin_jvm_memory_max_bytes",
    "fasttowin_room_ownership_takeovers_total", "fasttowin_room_ownership_fence_rejections_total",
    "fasttowin_room_ownership_resyncs_total", "fasttowin_room_ownership_settlement_failures_total",
    "fasttowin_room_ownership_oldest_queued_event_seconds",
    "fasttowin_room_ownership_pool_active_connections", "fasttowin_room_ownership_pool_max_connections"
)

/** Never serialize exception text, stack traces, dynamic labels or unknown file keys. */
internal fun baselineFailureReceipt(phase: String, failure: Throwable, receipts: Path, progress: BaselineLoadProgress?): BaselineFailureReceipt {
    val causes = generateSequence(failure) { it.cause }.take(8).toList()
    val category = when {
        causes.any { it is TimeoutCancellationException || it is SocketTimeoutException } -> "timeout"
        causes.any { it is SQLException && it.sqlState?.startsWith("08") == true } -> "database-connection"
        causes.any { it is SQLException } -> "database-error"
        causes.any { it is ConnectException || it is UnknownHostException } -> "network"
        causes.any { it is AcceptanceRejected } -> "protocol-rejected"
        causes.any { it is IllegalArgumentException } -> "configuration"
        causes.any { it is IllegalStateException || it is AssertionError } -> "assertion"
        else -> "other"
    }
    fun boundedFile(name: String, maxBytes: Long): String? = runCatching {
        val path = receipts.resolve(name)
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maxBytes) null
        else Files.readString(path)
    }.getOrNull()
    val counts = runCatching {
        boundedFile("failure-counts.json", 4_096)?.let { ProtocolJson.parseToJsonElement(it).jsonObject }
            ?.entries?.mapNotNull { (key, value) ->
                if (key !in countNames) null else value.jsonPrimitive.content.toLongOrNull()?.takeIf { it >= 0 }?.let { key to it }
            }?.toMap().orEmpty()
    }.getOrDefault(emptyMap())
    val metrics = boundedFile("failure-metrics-0.prom", 65_536)?.lineSequence()?.mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size != 2 || parts[0] !in metricNames) null
        else parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { parts[0] to it }
    }?.toMap().orEmpty()
    val safeProgress = progress?.takeIf { it.elapsedSeconds.isFinite() && it.elapsedSeconds >= 0 &&
        it.selectionsByRoomSlot.size <= 100 && it.selectionsByRoomSlot.all { n -> n >= 0 } &&
        it.completedMatches >= 0 && it.commandAttempts >= 0 }
    return BaselineFailureReceipt(phase.takeIf { it in failurePhases } ?: "unknown", category, safeProgress, counts, metrics)
}
