package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

@Serializable
internal data class OwnerCommandRequest(
    val roomId: String,
    val epoch: Long,
    val playerId: String,
    val generation: Long,
    val operation: OwnerOperation,
    val command: ClientMessage? = null,
    val ownerInstanceId: String
) {
    fun isSupported(): Boolean {
        val room = parsePilotUuid(roomId) ?: return false
        if (parsePilotUuid(playerId) == null || parsePilotUuid(ownerInstanceId) == null || epoch < 1 || generation < 1) {
            return false
        }
        if (operation != OwnerOperation.COMMAND) return command == null
        return command?.pilotRoomId()?.let(::parsePilotUuid) == room
    }
}

@Serializable
internal enum class OwnerOperation { COMMAND, RECONNECT, DISCONNECT }

internal fun Route.ownerCommandRoutes(
    transport: OwnerPeerTransport,
    dispatch: suspend (OwnerCommandRequest) -> OwnedRoomReply
) {
    post(OWNER_COMMAND_PATH) {
        val handled = withTimeoutOrNull(PEER_TIMEOUT_MILLIS) {
            transport.receive(call, dispatch)
            true
        }
        if (handled == null && !call.response.isCommitted) call.respond(HttpStatusCode.RequestTimeout)
    }
}

internal class OwnerPeerTransport(
    val instanceId: UUID,
    val peerId: UUID,
    peerUri: URI,
    secret: ByteArray,
    private val clock: Clock = Clock.systemUTC()
) {
    init {
        require(instanceId != peerId) { "Room ownership peer must be a different instance" }
        require(secret.size >= 32) { "Room ownership peer secret must be at least 32 bytes" }
        require(peerUri.scheme in setOf("http", "https") && peerUri.host != null &&
            peerUri.port in 1..65535 && peerUri.rawUserInfo == null && peerUri.rawQuery == null &&
            peerUri.rawFragment == null && peerUri.rawPath in setOf("", "/")) {
            "Room ownership peer must be an HTTP(S) origin with a private host and explicit port"
        }
    }

    private val key = SecretKeySpec(secret, HMAC_ALGORITHM)
    private val endpoint = peerUri.resolve(OWNER_COMMAND_PATH).toString().toHttpUrl()
    private val peerAddresses = resolvePrivatePeer(endpoint.host)
    private val nonces = PeerNonces(clock)
    private val client = OkHttpClient.Builder()
        .dns(Dns { host ->
            if (host != endpoint.host) throw UnknownHostException(host)
            peerAddresses
        })
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(PEER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun forward(request: OwnerCommandRequest): OwnedRoomReply? = withTimeoutOrNull(PEER_TIMEOUT_MILLIS) {
        if (!request.isSupported()) return@withTimeoutOrNull null
        val payload = Json.encodeToString(request).encodeToByteArray()
        if (payload.size > MAX_BODY_BYTES) return@withTimeoutOrNull null
        val nonce = UUID.randomUUID()
        val auth = sign(Direction.REQUEST, nonce, payload)
        val httpRequest = Request.Builder().url(endpoint).post(OneShotBody(payload))
        auth.headers().forEach { (name, value) -> httpRequest.header(name, value) }
        val response = client.newCall(httpRequest.build()).readReply() ?: return@withTimeoutOrNull null
        if (response.auth.nonce != nonce || !verify(Direction.REPLY, response.auth, response.payload)) {
            return@withTimeoutOrNull null
        }
        decode<OwnerCommandResponse>(response.payload)?.toReply(request)
    }

    internal suspend fun receive(call: ApplicationCall, dispatch: suspend (OwnerCommandRequest) -> OwnedRoomReply) {
        // The local connection point is the actual socket even when ForwardedHeaders is installed.
        if (call.request.local.remoteAddress.asSocketAddress() !in peerAddresses) {
            return call.respond(HttpStatusCode.Unauthorized)
        }
        val auth = PeerAuth.read(call.request.headers::getAll)
        if (auth == null || !accepts(auth)) return call.respond(HttpStatusCode.Unauthorized)
        if (!call.request.contentType().match(ContentType.Application.Json)) {
            return call.respond(HttpStatusCode.UnsupportedMediaType)
        }
        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_BODY_BYTES) {
            return call.respond(HttpStatusCode.PayloadTooLarge)
        }
        val payload = call.readOwnerPayload() ?: return call.respond(HttpStatusCode.PayloadTooLarge)
        if (!verify(Direction.REQUEST, auth, payload) || !nonces.remember(auth.nonce, auth.issuedAt)) {
            return call.respond(HttpStatusCode.Unauthorized)
        }
        val request = decode<OwnerCommandRequest>(payload)
        if (request == null || !request.isSupported()) return call.respond(HttpStatusCode.BadRequest)

        val reply = Json.encodeToString(OwnerCommandResponse.from(dispatch(request))).encodeToByteArray()
        if (reply.size > MAX_BODY_BYTES) return call.respond(HttpStatusCode.ServiceUnavailable)
        sign(Direction.REPLY, auth.nonce, reply).headers().forEach { (name, value) ->
            call.response.headers.append(name, value)
        }
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.respondBytes(reply, ContentType.Application.Json)
    }

    private fun accepts(auth: PeerAuth): Boolean =
        auth.sender == peerId && auth.target == instanceId && auth.issuedAt.isFreshAt(clock.millis())

    private fun verify(direction: Direction, auth: PeerAuth, payload: ByteArray): Boolean =
        accepts(auth) && MessageDigest.isEqual(auth.signature, signature(direction, auth, payload))

    private fun sign(direction: Direction, nonce: UUID, payload: ByteArray): PeerAuth {
        val unsigned = PeerAuth(instanceId, peerId, clock.millis(), nonce, ByteArray(0))
        return unsigned.copy(signature = signature(direction, unsigned, payload))
    }

    private fun signature(direction: Direction, auth: PeerAuth, payload: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(key)
        val fields = "fasttowin/room-owner/v1\n${direction.wireValue}\n${auth.sender}\n${auth.target}\n${auth.issuedAt}\n${auth.nonce}\n"
        mac.update(fields.encodeToByteArray())
        return mac.doFinal(payload)
    }
}

private const val OWNER_COMMAND_PATH = "/internal/room-ownership/command"
private const val PEER_TIMEOUT_MILLIS = 2_000L
private const val MAX_BODY_BYTES = 64 * 1024
private const val HMAC_ALGORITHM = "HmacSHA256"
private const val MAX_AGE_MILLIS = 30_000L
private const val NONCE_CAPACITY = 4_096
private const val SENDER = "X-FastToWin-Owner-Sender"
private const val TARGET = "X-FastToWin-Owner-Target"
private const val ISSUED_AT = "X-FastToWin-Owner-Issued-At"
private const val NONCE = "X-FastToWin-Owner-Nonce"
private const val SIGNATURE = "X-FastToWin-Owner-Signature"

private enum class Direction(val wireValue: String) { REQUEST("request"), REPLY("reply") }

private data class PeerAuth(
    val sender: UUID,
    val target: UUID,
    val issuedAt: Long,
    val nonce: UUID,
    val signature: ByteArray
) {
    fun headers(): Map<String, String> = mapOf(
        SENDER to sender.toString(), TARGET to target.toString(), ISSUED_AT to issuedAt.toString(),
        NONCE to nonce.toString(), SIGNATURE to Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    )

    companion object {
        fun read(getAll: (String) -> List<String>?): PeerAuth? {
            val sender = getAll(SENDER)?.singleOrNull()?.asCanonicalUuid() ?: return null
            val target = getAll(TARGET)?.singleOrNull()?.asCanonicalUuid() ?: return null
            val timestamp = getAll(ISSUED_AT)?.singleOrNull() ?: return null
            val issuedAt = timestamp.toLongOrNull()?.takeIf { it.toString() == timestamp } ?: return null
            val nonce = getAll(NONCE)?.singleOrNull()?.asCanonicalUuid() ?: return null
            val encoded = getAll(SIGNATURE)?.singleOrNull()?.takeIf { it.length == 43 } ?: return null
            val signature = try {
                Base64.getUrlDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                return null
            }
            return PeerAuth(sender, target, issuedAt, nonce, signature)
        }
    }
}

private class PeerNonces(private val clock: Clock) {
    private val expiryByNonce = mutableMapOf<UUID, Long>()
    private var latestObservedAt = Long.MIN_VALUE

    @Synchronized
    fun remember(nonce: UUID, issuedAt: Long): Boolean {
        // A backward clock adjustment must never reopen an expired replay window.
        val now = maxOf(clock.millis(), latestObservedAt)
        latestObservedAt = now
        if (!issuedAt.isFreshAt(now)) return false
        expiryByNonce.values.removeIf { it < now }
        if (expiryByNonce.size >= NONCE_CAPACITY || nonce in expiryByNonce) return false
        expiryByNonce[nonce] = issuedAt + MAX_AGE_MILLIS
        return true
    }
}

private fun Long.isFreshAt(now: Long) = this in (now - MAX_AGE_MILLIS)..(now + MAX_AGE_MILLIS)

private fun String.asCanonicalUuid(): UUID? = parsePilotUuid(this)?.takeIf { it.toString() == this }

private fun resolvePrivatePeer(host: String): List<InetAddress> {
    val addresses = try {
        InetAddress.getAllByName(host).toList()
    } catch (failure: UnknownHostException) {
        throw IllegalArgumentException("Room ownership peer host could not be resolved", failure)
    }
    require(addresses.isNotEmpty() && addresses.all { address ->
        address.isLoopbackAddress || when (address) {
            is Inet4Address -> address.isSiteLocalAddress
            is Inet6Address -> (address.address[0].toInt() and 0xfe) == 0xfc // RFC 4193 unique local address.
            else -> false
        }
    }) { "Room ownership peer host must resolve exclusively to private addresses" }
    return addresses
}

private val IPV4_LITERAL = Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")

private fun String.asSocketAddress(): InetAddress? {
    val host = substringBefore('%')
    if (':' !in host && !IPV4_LITERAL.matches(host)) return null
    return try {
        InetAddress.getByName(host)
    } catch (_: UnknownHostException) {
        null
    }
}

private suspend fun ApplicationCall.readOwnerPayload(): ByteArray? {
    val channel = receiveChannel()
    val buffer = ByteArray(MAX_BODY_BYTES + 1)
    var size = 0
    while (size < buffer.size) {
        val count = channel.readAvailable(buffer, size, buffer.size - size)
        if (count < 0) return buffer.copyOf(size)
        size += count
    }
    return null
}

private inline fun <reified T> decode(payload: ByteArray): T? = try {
    Json.decodeFromString<T>(payload.decodeToString(throwOnInvalidSequence = true))
} catch (_: SerializationException) {
    null
} catch (_: CharacterCodingException) {
    null
}

private data class PeerReply(val auth: PeerAuth, val payload: ByteArray)

private suspend fun Call.readReply(): PeerReply? = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = continuation.resume(null)

        override fun onResponse(call: Call, response: Response) {
            val reply = try {
                response.use { it.readOwnerReply() }
            } catch (_: IOException) {
                null
            }
            continuation.resume(reply)
        }
    })
}

private fun Response.readOwnerReply(): PeerReply? {
    if (code != HttpStatusCode.OK.value) return null
    val auth = PeerAuth.read(headers::values) ?: return null
    if (body.contentLength() > MAX_BODY_BYTES) return null
    val payload = body.byteStream().readNBytes(MAX_BODY_BYTES + 1)
    return if (payload.size <= MAX_BODY_BYTES) PeerReply(auth, payload) else null
}

private class OneShotBody(payload: ByteArray) : RequestBody() {
    private val delegate = payload.toRequestBody("application/json".toMediaType())
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    override fun isOneShot() = true
}

@Serializable
private sealed interface OwnerCommandResponse {
    @Serializable @SerialName("applied")
    data class Applied(val message: ServerMessage, val roomId: String, val epoch: Long, val revision: Long, val replayed: Boolean) :
        OwnerCommandResponse

    @Serializable @SerialName("rejected") data class Rejected(val message: ServerMessage.Error) : OwnerCommandResponse
    @Serializable @SerialName("retryable") data class Retryable(val message: ServerMessage.Error) : OwnerCommandResponse
    @Serializable @SerialName("no_room") data object NoRoom : OwnerCommandResponse

    fun toReply(request: OwnerCommandRequest): OwnedRoomReply? = when (this) {
        is Applied -> parsePilotUuid(roomId)?.takeIf { it == parsePilotUuid(request.roomId) && epoch == request.epoch && revision > 0 }
            ?.let { OwnedRoomReply.Applied(message, it, epoch, revision, replayed) }
        is Rejected -> OwnedRoomReply.Rejected(message)
        is Retryable -> OwnedRoomReply.Retryable(message)
        NoRoom -> OwnedRoomReply.NoRoom
    }

    companion object {
        fun from(reply: OwnedRoomReply): OwnerCommandResponse = when (reply) {
            is OwnedRoomReply.Applied -> Applied(reply.publicMessage, reply.roomId.toString(), reply.epoch, reply.revision, reply.replayed)
            is OwnedRoomReply.Rejected -> Rejected(reply.publicMessage)
            is OwnedRoomReply.Retryable -> Retryable(reply.publicMessage)
            OwnedRoomReply.NoRoom -> NoRoom
        }
    }
}
