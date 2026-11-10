package com.hienthai.fastowin.server.ownership

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Proxy
import java.util.UUID

internal fun interface ControlTransport : AutoCloseable {
    override fun close() {}
    suspend fun post(role: String, authorization: String, body: String, deadline: Long): Pair<Int, String>
}

/** Addresses were resolved and pinned by the exact marked rehearsal profile. */
internal class PrivateControlTransport private constructor(
    private val hosts: Map<String, String>, private val port: Int
) : ControlTransport {
    constructor(hosts: Map<String, String>) : this(hosts, 8181)

    companion object {
        internal fun loopbackForTest(port: Int): PrivateControlTransport {
            require(port in 1..65535)
            return PrivateControlTransport(mapOf("a" to "127.0.0.1"), port)
        }
    }

    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    override suspend fun post(role: String, authorization: String, body: String, deadline: Long): Pair<Int, String> {
        val request = Request.Builder().url("http://${hosts.getValue(role)}:$port/control")
            .header("Authorization", authorization)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val remaining = deadline - System.currentTimeMillis()
        check(remaining > 0) { "Expired control window" }
        val call = client.newCall(request)
        call.timeout().deadline(minOf(remaining, 5000L), TimeUnit.MILLISECONDS)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(IllegalStateException("Control transport unavailable")))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            val type = it.body.contentType()
                            check(it.code == 200 && type?.type == "application" && type.subtype == "json")
                            val bytes = it.body.byteStream().readNBytes(4097)
                            check(bytes.size <= 4096 && bytes.all { byte -> byte >= 0 })
                            it.code to bytes.toString(Charsets.US_ASCII)
                        }
                    }
                    if (result.isFailure) continuation.resumeWith(Result.failure(IllegalStateException("Invalid control reply")))
                    else continuation.resumeWith(result)
                }
            })
        }
    }

    override fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

@Serializable
private data class ControlRequest(val fixtureId: String, val requestId: String, val generation: Long,
    val operation: String, val deadlineUnixMillis: Long, val leaseMillis: Long)
@Serializable
private data class ControlReply(val requestId: String, val role: String, val generation: Long,
    val state: String, val verified: Boolean, val failed: Boolean, val errorCode: String)

internal class SupervisorRoomFaultControl(
    private val fixture: String, private val token: String, private val deadline: Long,
    private val transport: ControlTransport, private val now: () -> Long = System::currentTimeMillis
): RoomFaultControl {
    init { require(token.matches(Regex("[a-f0-9]{64}"))) }
    override fun close() = transport.close()
    override val processPauseStores = true
    private val checkedJson = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
    private val known = mutableMapOf<String, ControlReply>()
    private val applied = mutableListOf<Pair<String,String>>()
    fun verifiedMutations() = applied.toList()

    private suspend fun request(role: String, op: String, generation: Long, lease: Long): ControlReply {
        val end = minOf(deadline, now() + 5000)
        check(now() < end) { "Expired control window" }
        val id = UUID.randomUUID().toString()
        val body = Json.encodeToString(ControlRequest(fixture, id, generation, op, end, lease))
        val (status, raw) = transport.post(role, "Bearer $token", body, end)
        check(now() < end && status == 200 && raw.length <= 4096) { "Unacknowledged control request" }
        val reply = try {
            checkedJson.createParser(raw).use { parser ->
                require(parser.nextToken() == JsonToken.START_OBJECT)
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    val key = parser.currentName
                    val type = parser.nextToken()
                    require(when (key) {
                        "requestId","role","state","errorCode" -> type == JsonToken.VALUE_STRING
                        "generation" -> type == JsonToken.VALUE_NUMBER_INT
                        "verified","failed" -> type == JsonToken.VALUE_TRUE || type == JsonToken.VALUE_FALSE
                        else -> false
                    })
                }
                require(parser.currentToken == JsonToken.END_OBJECT && parser.nextToken() == null)
            }
            Json.decodeFromString<ControlReply>(raw)
        } catch (_: Exception) { error("Invalid control response") }
        check(reply.requestId == id && reply.role == role && reply.generation > 0 &&
            reply.verified && !reply.failed && reply.errorCode.isEmpty()) { "Invalid control proof" }
        return reply
    }

    private suspend fun status(role: String): ControlReply {
        val previous = known[role]
        val reply = request(role, "status", previous?.generation ?: 0, 0)
        check(previous == null || previous.generation == reply.generation && previous.state == reply.state) {
            "Unexpected supervisor process transition"
        }
        known[role] = reply
        return reply
    }

    private suspend fun transition(role: String, op: String, before: String, after: String) {
        val current = status(role)
        check(current.state == before) { "Required control precondition absent" }
        val reply = request(role, op, current.generation, if (before == "running") 60000 else 0)
        val expectedGeneration = if (op == "start") Math.addExact(current.generation, 1) else current.generation
        check(reply.state == after && reply.generation == expectedGeneration) { "Control operation was not proved" }
        known[role] = reply
        check(applied.size < 256)
        applied += role to op
    }
    private fun role(node: Int): String { require(node in 0..1); return if (node == 0) "a" else "b" }
    override suspend fun backend(node: Int, action: BackendAction) {
        val role = role(node)
        when (action) {
            BackendAction.KILL -> transition(role,"kill","running","stopped")
            BackendAction.START -> transition(role,"start","stopped","running")
            BackendAction.PAUSE -> transition(role,"pause","running","paused")
            BackendAction.RESUME -> transition(role,"resume","paused","running")
        }
    }
    override suspend fun store(role: StoreRole, unavailable: Boolean) {
        if (unavailable) transition(role.wire,"pause","running","paused")
        else transition(role.wire,"resume","paused","running")
    }
    override suspend fun partition(node: Int, blocked: Boolean) {
        val path = role(node)
        if (blocked) transition("proxy","block-$path","running","blocked-$path")
        else transition("proxy","unblock-$path","blocked-$path","running")
    }
    override suspend fun verifyHealthy() {
        for (role in listOf("a","b","pg","redis","proxy")) check(status(role).state == "running")
    }
}
