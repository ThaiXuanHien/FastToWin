package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

class OwnerCommandRoutesTest {
    private val owner = UUID.randomUUID()
    private val ingress = UUID.randomUUID()
    private val ownerInstanceId = UUID.randomUUID().toString()
    private val room = UUID.randomUUID().toString()
    private val player = UUID.randomUUID().toString()
    private val key = ByteArray(32) { it.toByte() }
    private val now = Instant.parse("2026-11-07T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    private val body = """{"roomId":"$room","epoch":3,"playerId":"$player","generation":4,"ownerInstanceId":"$ownerInstanceId","operation":"DISCONNECT"}"""
    private val disconnect = OwnerCommandRequest(room, 3, player, 4, OwnerOperation.DISCONNECT, ownerInstanceId = ownerInstanceId)

    @Test fun `real peers authenticate a disconnect acknowledgement`() = withOwner { uri, dispatched ->
        val peer = OwnerPeerTransport(ingress, owner, uri, key, clock)
        val reply = peer.forward(disconnect)
        assertEquals(OwnedRoomReply.NoRoom, reply)
        assertEquals(1, dispatched.get())
    }

    @Test fun `only the exact fresh request is accepted once`() = withOwner { uri, dispatched ->
        val headers = sign(body)
        assertEquals(401, post(uri, body.replace("\"epoch\":3", "\"epoch\":4"), headers))
        assertEquals(401, post(uri, body, sign(body, issuedAt = now.minusSeconds(120).toEpochMilli())))
        assertEquals(401, post(uri, body, sign(body, sender = UUID.randomUUID())))
        assertEquals(401, post(uri, body, sign(body, target = UUID.randomUUID())))
        assertEquals(200, post(uri, body, headers))
        assertEquals(401, post(uri, body, headers))
        assertEquals(1, dispatched.get())
    }

    @Test fun `forwarded addresses cannot replace the socket address`() = withOwner("127.0.0.2") { uri, dispatched ->
        val headers = sign(body) + mapOf("X-Forwarded-For" to "127.0.0.2", "Forwarded" to "for=127.0.0.2")
        assertEquals(401, post(uri, body, headers))
        assertEquals(0, dispatched.get())
    }

    @Test fun `chunked request body honors the 64 KiB boundary`() = withOwner { uri, dispatched ->
        val atLimit = body.padEnd(65_536)
        val oversized = "$atLimit "
        assertEquals(200, post(uri, atLimit, sign(atLimit), chunked = true))
        assertEquals(413, post(uri, oversized, sign(oversized), chunked = true))
        assertEquals(1, dispatched.get())
    }

    @Test fun `signed create and mismatched command room never dispatch`() = withOwner { uri, dispatched ->
        val create = """{"type":"create_room","roomName":"Forbidden","password":"private","gameMode":"ORDER"}"""
        val leave = """{"type":"leave_room","roomId":"${UUID.randomUUID()}"}"""
        for (command in listOf(create, leave)) {
            val commandBody = body.replace("\"DISCONNECT\"}", "\"COMMAND\",\"command\":$command}")
            assertEquals(400, post(uri, commandBody, sign(commandBody)))
        }
        assertEquals(0, dispatched.get())
    }

    @Test fun `owner instance identity must be a UUID`() = withOwner { uri, dispatched ->
        val invalid = body.replace(ownerInstanceId, "unknown")
        assertEquals(400, post(uri, invalid, sign(invalid)))
        assertEquals(0, dispatched.get())
    }

    @Test fun `credentials never cross the private hop`() {
        val requests = AtomicInteger()
        withEndpoint({
            post(PATH) {
                requests.incrementAndGet()
                call.respond(HttpStatusCode.BadRequest)
            }
        }) { uri ->
            val peer = OwnerPeerTransport(ingress, owner, uri, key, clock)
            val request = disconnect.copy(operation = OwnerOperation.COMMAND, command = ClientMessage.ConnectAccount("secret"))
            assertNull(peer.forward(request))
            assertEquals(0, requests.get())
        }
    }

    @Test fun `tampered signed replies do not acknowledge the command`() {
        val reply = """{"type":"no_room"}"""
        withEndpoint({
            post(PATH) {
                val nonce = UUID.fromString(call.request.headers["X-FastToWin-Owner-Nonce"])
                sign(reply, sender = owner, target = ingress, direction = "reply", nonce = nonce)
                    .forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respondText("$reply ", ContentType.Application.Json)
            }
        }) { uri ->
            val peer = OwnerPeerTransport(ingress, owner, uri, key, clock)
            assertNull(peer.forward(disconnect))
        }
    }

    @Test fun `peer redirects and retry responses never produce a second request`() {
        val requests = AtomicInteger()
        val redirects = AtomicInteger()
        withEndpoint({
            post(PATH) {
                if (requests.incrementAndGet() == 1) {
                    call.response.headers.append(HttpHeaders.Location, "/redirected")
                    call.respond(HttpStatusCode.TemporaryRedirect)
                } else {
                    call.response.headers.append(HttpHeaders.RetryAfter, "0")
                    call.respond(HttpStatusCode.ServiceUnavailable)
                }
            }
            post("/redirected") {
                redirects.incrementAndGet()
                call.respond(HttpStatusCode.BadRequest)
            }
        }) { uri ->
            val peer = OwnerPeerTransport(ingress, owner, uri, key, clock)
            assertNull(peer.forward(disconnect))
            assertEquals(1, requests.get())
            assertEquals(0, redirects.get())
            assertNull(peer.forward(disconnect))
            assertEquals(2, requests.get())
        }
    }

    @Test fun `a stalled peer hop expires`() = withEndpoint({
        post(PATH) {
            delay(10_000)
            call.respond(HttpStatusCode.ServiceUnavailable)
        }
    }) { uri ->
        val peer = OwnerPeerTransport(ingress, owner, uri, key, clock)
        val elapsed = measureTime { assertNull(peer.forward(disconnect)) }
        assertTrue(elapsed.inWholeMilliseconds < 3_000, "Private hop took $elapsed")
    }

    @Test fun `public destinations are rejected before any request`() {
        assertFailsWith<IllegalArgumentException> {
            OwnerPeerTransport(ingress, owner, URI("http://8.8.8.8:8090"), key, clock)
        }
    }

    private fun withOwner(
        peerAddress: String = "127.0.0.1",
        block: suspend (URI, AtomicInteger) -> Unit
    ) {
        val dispatched = AtomicInteger()
        val receiver = OwnerPeerTransport(owner, ingress, URI("http://$peerAddress:1"), key, clock)
        withEndpoint({
            ownerCommandRoutes(receiver) { request ->
                check(request.operation == OwnerOperation.DISCONNECT)
                check(request.ownerInstanceId == ownerInstanceId)
                dispatched.incrementAndGet()
                OwnedRoomReply.NoRoom
            }
        }) { uri -> block(uri, dispatched) }
    }

    private fun withEndpoint(routes: Route.() -> Unit, block: suspend (URI) -> Unit) = runBlocking {
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            install(ForwardedHeaders)
            install(XForwardedHeaders)
            routing(routes)
        }.start(wait = false)
        try {
            block(URI("http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"))
        } finally {
            server.stop(0, 1000)
        }
    }

    private fun post(uri: URI, body: String, headers: Map<String, String>, chunked: Boolean = false): Int {
        val publisher = if (chunked) HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(body.toByteArray()) }
        else HttpRequest.BodyPublishers.ofString(body)
        val builder = HttpRequest.newBuilder(uri.resolve(PATH))
            .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json").POST(publisher)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return http.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun sign(
        body: String,
        sender: UUID = ingress,
        target: UUID = owner,
        issuedAt: Long = now.toEpochMilli(),
        direction: String = "request",
        nonce: UUID = UUID.randomUUID()
    ): Map<String, String> {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update("fasttowin/room-owner/v1\n$direction\n$sender\n$target\n$issuedAt\n$nonce\n".toByteArray())
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.toByteArray()))
        return mapOf(
            "X-FastToWin-Owner-Sender" to sender.toString(),
            "X-FastToWin-Owner-Target" to target.toString(),
            "X-FastToWin-Owner-Issued-At" to issuedAt.toString(),
            "X-FastToWin-Owner-Nonce" to nonce.toString(),
            "X-FastToWin-Owner-Signature" to signature
        )
    }

    private companion object {
        const val PATH = "/internal/room-ownership/command"
    }
}
