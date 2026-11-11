package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.measureTime

class OwnerCreateRoutesTest {
    private val ingress = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val key = ByteArray(32) { it.toByte() }
    private val clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)
    private val request = OwnedCreateRequest(UUID.randomUUID(), UUID.randomUUID(), SocketFence(ADMISSION_HOST, 1),
        ADMISSION_HOST.toString(), owner, OwnedAdmissionPolicy(7, OwnedAdmissionState.ACTIVE, "a".repeat(64), "b".repeat(64)),
        ClientMessage.CreateRoom("Private create", "private-test-password", ProtocolGameMode.ORDER))
    private val proof = OwnedCreateProof(request.operationId, request.roomId, owner, UUID.randomUUID(), 1, 1)

    @Test fun `fresh transport nonce replays the same operation using create domain only`() {
        val creates = AtomicInteger()
        val commands = AtomicInteger()
        val receiver = OwnerPeerTransport(owner, ingress, URI("http://127.0.0.1:1"), key, clock)
        endpoint({
            ownerCreateRoutes(receiver) { received ->
                assertEquals(request, received)
                OwnedCreateResult.Created(proof, creates.incrementAndGet() > 1)
            }
            ownerCommandRoutes(receiver) { commands.incrementAndGet(); OwnedRoomReply.NoRoom }
        }) { uri ->
            val peer = OwnerPeerTransport(ingress, owner, uri, key, clock)
            assertEquals(OwnedCreateResult.Created(proof, false), peer.forwardCreate(request))
            assertEquals(OwnedCreateResult.Created(proof, true), peer.forwardCreate(request))
            assertEquals(2, creates.get())
            assertEquals(0, commands.get())
            val body = Json.encodeToString(OwnedCreateWireRequest.from(request))
            assertEquals(401, post(uri, CREATE_PATH, body, sign(body, domain = "fasttowin/room-owner/v1")))
            assertEquals(401, post(uri, COMMAND_PATH, body, sign(body)))
            assertEquals(2, creates.get())
        }
    }

    @Test fun `create nonce freshness tampering and chunked body cap reject before dispatch`() {
        val calls = AtomicInteger()
        val receiver = OwnerPeerTransport(owner, ingress, URI("http://127.0.0.1:1"), key, clock)
        endpoint({ ownerCreateRoutes(receiver) { calls.incrementAndGet(); OwnedCreateResult.Created(proof, true) } }) { uri ->
            val body = Json.encodeToString(OwnedCreateWireRequest.from(request))
            val auth = sign(body)
            assertEquals(401, post(uri, CREATE_PATH, "$body ", auth))
            assertEquals(401, post(uri, CREATE_PATH, body, sign(body, timestamp = clock.millis() - 120_000)))
            assertEquals(401, post(uri, CREATE_PATH, body, sign(body, timestamp = clock.millis() + 120_000)))
            assertEquals(200, post(uri, CREATE_PATH, body, auth))
            assertEquals(401, post(uri, CREATE_PATH, body, auth))
            val oversized = body.padEnd(65_537)
            assertEquals(413, post(uri, CREATE_PATH, oversized, sign(oversized), chunked = true))
            assertEquals(1, calls.get())
        }
    }

    @Test fun `wrong signed create proof identities remain unresolved`() {
        val wrong = listOf(proof.copy(operationId = UUID.randomUUID()), proof.copy(roomId = UUID.randomUUID()),
            proof.copy(destinationNodeId = UUID.randomUUID()), proof.copy(createdEpoch = 0), proof.copy(createdRevision = 0))
        for (result in wrong) {
            val receiver = OwnerPeerTransport(owner, ingress, URI("http://127.0.0.1:1"), key, clock)
            endpoint({ ownerCreateRoutes(receiver) { OwnedCreateResult.Created(result, true) } }) { uri ->
                assertIs<OwnedCreateResult.Unresolved>(OwnerPeerTransport(ingress, owner, uri, key, clock).forwardCreate(request))
            }
        }
    }

    @Test fun `redirect and timeout never retry or change descriptor`() {
        val calls = AtomicInteger()
        val redirects = AtomicInteger()
        endpoint({
            post(CREATE_PATH) { calls.incrementAndGet(); call.response.headers.append(HttpHeaders.Location, "/redirected"); call.respond(HttpStatusCode.TemporaryRedirect) }
            post("/redirected") { redirects.incrementAndGet(); call.respond(HttpStatusCode.BadRequest) }
        }) { uri ->
            assertIs<OwnedCreateResult.Unresolved>(OwnerPeerTransport(ingress, owner, uri, key, clock).forwardCreate(request))
            assertEquals(1, calls.get()); assertEquals(0, redirects.get())
        }
        endpoint({ post(CREATE_PATH) { calls.incrementAndGet(); delay(10_000); call.respond(HttpStatusCode.ServiceUnavailable) } }) { uri ->
            val elapsed = measureTime {
                assertIs<OwnedCreateResult.Unresolved>(OwnerPeerTransport(ingress, owner, uri, key, clock).forwardCreate(request))
            }
            assertTrue(elapsed.inWholeMilliseconds < 3_000)
            assertEquals(2, calls.get())
        }
    }

    @Test fun `signed attempt rejection is nondurable and every failure envelope stays descriptor bound`() {
        for (outcome in listOf("attempt_rejected", "unresolved")) {
            val matching = """{"version":1,"operationId":"${request.operationId}","roomId":"${request.roomId}","destinationNodeId":"$owner","outcome":"$outcome","reason":"ATTEMPT_ONLY"}"""
            endpoint({ post(CREATE_PATH) {
                val nonce = UUID.fromString(call.request.headers["X-FastToWin-Owner-Nonce"])
                sign(matching, sender = owner, target = ingress, direction = "reply", nonce = nonce)
                    .forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respondText(matching, ContentType.Application.Json)
            } }) { uri ->
                val result = OwnerPeerTransport(ingress, owner, uri, key, clock).forwardCreate(request)
                if (outcome == "attempt_rejected") assertEquals(OwnedCreateResult.AttemptRejected("ATTEMPT_ONLY"), result)
                else assertEquals(OwnedCreateResult.Unresolved("ATTEMPT_ONLY"), result)
            }
            for (wrong in listOf(matching.replace(request.operationId.toString(), UUID.randomUUID().toString()),
                matching.replace(request.roomId.toString(), UUID.randomUUID().toString()), matching.replace(owner.toString(), UUID.randomUUID().toString()))) {
                endpoint({ post(CREATE_PATH) {
                    val nonce = UUID.fromString(call.request.headers["X-FastToWin-Owner-Nonce"])
                    sign(wrong, sender = owner, target = ingress, direction = "reply", nonce = nonce)
                        .forEach { (name, value) -> call.response.headers.append(name, value) }
                    call.respondText(wrong, ContentType.Application.Json)
                } }) { uri -> assertIs<OwnedCreateResult.Unresolved>(OwnerPeerTransport(ingress, owner, uri, key, clock).forwardCreate(request)) }
            }
        }
    }

    @Test fun `bad create reply nonce domain tampering and command schema never resolve an operation`() {
        val body = """{"version":1,"operationId":"${request.operationId}","roomId":"${request.roomId}","destinationNodeId":"$owner","outcome":"attempt_rejected","reason":"ATTEMPT_ONLY"}"""
        for (fault in listOf("nonce", "domain", "tampered", "command_schema")) {
            endpoint({ post(CREATE_PATH) {
                val incomingNonce = UUID.fromString(call.request.headers["X-FastToWin-Owner-Nonce"])
                val reply = if (fault == "command_schema") """{"type":"no_room"}""" else body
                sign(reply, sender = owner, target = ingress, direction = "reply",
                    nonce = if (fault == "nonce") UUID.randomUUID() else incomingNonce,
                    domain = if (fault == "domain") "fasttowin/room-owner/v1" else "fasttowin/room-create/v1")
                    .forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respondText(if (fault == "tampered") "$reply " else reply, ContentType.Application.Json)
            } }) { uri -> assertIs<OwnedCreateResult.Unresolved>(OwnerPeerTransport(ingress, owner, uri, key, clock).forwardCreate(request)) }
        }
    }

    private fun endpoint(routes: Route.() -> Unit, block: suspend (URI) -> Unit) = runBlocking {
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing(routes) }.start(wait = false)
        try { block(URI("http://127.0.0.1:${server.engine.resolvedConnectors().single().port}")) }
        finally { server.stop(0, 1000) }
    }

    private fun post(uri: URI, path: String, body: String, headers: Map<String, String>, chunked: Boolean = false): Int {
        val publisher = if (chunked) HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(body.toByteArray()) }
            else HttpRequest.BodyPublishers.ofString(body)
        val builder = HttpRequest.newBuilder(uri.resolve(path)).header("Content-Type", ContentType.Application.Json.toString()).POST(publisher)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun sign(body: String, domain: String = "fasttowin/room-create/v1", timestamp: Long = clock.millis(),
        sender: UUID = ingress, target: UUID = owner, direction: String = "request", nonce: UUID = UUID.randomUUID()
    ): Map<String, String> {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        mac.update("$domain\n$direction\n$sender\n$target\n$timestamp\n$nonce\n".toByteArray())
        return mapOf("X-FastToWin-Owner-Sender" to sender.toString(), "X-FastToWin-Owner-Target" to target.toString(),
            "X-FastToWin-Owner-Issued-At" to timestamp.toString(), "X-FastToWin-Owner-Nonce" to nonce.toString(),
            "X-FastToWin-Owner-Signature" to Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.toByteArray())))
    }

    private companion object {
        const val CREATE_PATH = "/internal/room-ownership/create"
        const val COMMAND_PATH = "/internal/room-ownership/command"
    }
}
