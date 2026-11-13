package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.DatabaseSettings
import com.hienthai.fastowin.server.StoredActivePlayer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class OwnedCreatePilotNettyTest {
    @Test fun `actual A and worker B retain committed descriptor through lost reply STOP_NEW and role rejection`() = runBlocking {
        withAdmissionFixture { f ->
            val localActor = UUID.fromString("10000000-0000-4000-8000-000000000003")
            f.execute("""INSERT INTO users(id,account_type,status,email_normalized,password_hash,email_verified_at,created_at,updated_at)
                VALUES('$localActor','REGISTERED','ACTIVE','local@example.test','isolated',clock_timestamp(),clock_timestamp(),clock_timestamp())""")
            f.execute("""INSERT INTO profiles(user_id,display_name,player_code,created_at,updated_at)
                SELECT id,'Owned fixture',right(replace(id::text,'-',''),12),clock_timestamp(),clock_timestamp() FROM users""")
            val players = f.players + (localActor to StoredActivePlayer(localActor.toString(), "Local", isAccount = true))
            val configA = admissionProductionConfig(accounts = players.keys.toList())
            val configB = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER, accounts = players.keys.toList())
            val policy = f.authority.bootstrap(assertNotNull(f.authority.observe()).generation, OwnedAdmissionBinding.from(configA))
            val settings = DatabaseSettings("${f.source.jdbcUrl}?currentSchema=${f.source.schema}", f.source.username, f.source.password, 5)
            OwnedWorkerDatabaseRuntime.open(settings).use { database ->
                val worker = database.openRoomOwnershipPilot(configB, candidateIdentity = { OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64)) })
                val workerServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) { ownedWorkerModule(worker) }.start()
                try {
                    val workerUri = URI("http://127.0.0.1:${workerServer.engine.resolvedConnectors().single().port}")
                    val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                    withTimeout(8_000) {
                        while (http.send(HttpRequest.newBuilder(workerUri.resolve("/ready")).GET().build(),
                                HttpResponse.BodyHandlers.discarding()).statusCode() != 200) delay(20)
                    }
                    val relays = AtomicInteger()
                    val dropReply = AtomicBoolean(true)
                    // This real HTTP hop drops only the response after B's committed reply is received.
                    val proxy = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                        routing { post(CREATE_PATH) {
                            relays.incrementAndGet()
                            val body = call.receiveText()
                            val forwarded = HttpRequest.newBuilder(workerUri.resolve(CREATE_PATH))
                                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                            OWNER_HEADERS.forEach { name -> forwarded.header(name, assertNotNull(call.request.headers[name])) }
                            val response = http.send(forwarded.build(), HttpResponse.BodyHandlers.ofByteArray())
                            if (dropReply.getAndSet(false)) call.respond(HttpStatusCode.ServiceUnavailable)
                            else {
                                OWNER_HEADERS.forEach { name -> response.headers().firstValue(name).ifPresent { call.response.headers.append(name, it) } }
                                call.respondBytes(response.body(), ContentType.Application.Json, HttpStatusCode.fromValue(response.statusCode()))
                            }
                        } }
                    }.start()
                    try {
                        val proxyUri = URI("http://127.0.0.1:${proxy.engine.resolvedConnectors().single().port}")
                        val routedA = object : RoomOwnershipRuntimeConfig by configA { override val peerUri = proxyUri }
                        val allowNew = AtomicBoolean(true)
                        val ingress = RoomOwnershipPilot.open(routedA, f.source, { players[it] }, wakeups = emptyFlow(),
                            newIntentAllowed = allowNew::get, candidateIdentity = { OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64)) })
                        val ingressServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                            routing { ingress.installRoutes(this) }
                        }.start()
                        try {
                            val host = f.rooms.bindSocket(ADMISSION_HOST)
                            val localSocket = f.rooms.bindSocket(localActor)
                            val command = ClientMessage.CreateRoom("Across A and B", "private-test-password", ProtocolGameMode.ORDER)
                            val remote = assertIs<OwnedCreatePreparation.Ready>(ingress.prepareOwnedCreate(host, command)).request
                            assertEquals(ADMISSION_NODE_B, remote.destinationNodeId)
                            assertIs<OwnedCreateResult.Unresolved>(ingress.createOwned(remote))
                            assertEquals(1, relays.get(), "An uncertain commit must not retry automatically")
                            assertEquals(1, f.count("owned_room_create_operations"))
                            assertEquals(1, f.count("room_ownership_pilot_rooms"), "Lost peer reply must not create a local fallback room")
                            val committed = assertNotNull(f.rooms.read(remote.roomId))
                            assertEquals(remote.roomId, f.rooms.lookupRoom(host.playerId))
                            val replay = assertIs<OwnedCreateResult.Created>(ingress.createOwned(remote))
                            assertEquals(committed.fence.instanceId, replay.proof.createdInstanceId)
                            assertEquals(true, replay.replayed)
                            assertIs<OwnedCreatePreparation.Existing>(ingress.prepareOwnedCreate(host, command))

                            val local = assertIs<OwnedCreatePreparation.Ready>(ingress.prepareOwnedCreate(localSocket, command)).request
                            assertEquals(ADMISSION_NODE_A, local.destinationNodeId)
                            val createdLocal = assertIs<OwnedCreateResult.Created>(ingress.createOwned(local))
                            assertFalse(createdLocal.replayed)
                            assertNotEquals(replay.proof.createdInstanceId, createdLocal.proof.createdInstanceId)
                            assertEquals(2, relays.get(), "Local placement must not enter the peer hop")
                            assertIs<OwnedCreatePreparation.Rejected>(worker.prepareOwnedCreate(host, command))
                            assertIs<OwnedCreateResult.AttemptRejected>(worker.createOwned(remote))

                            val ingressUri = URI("http://127.0.0.1:${ingressServer.engine.resolvedConnectors().single().port}")
                            val forgedWorker = OwnerPeerTransport(ADMISSION_NODE_B, ADMISSION_NODE_A, ingressUri, configA.peerSecret)
                            assertIs<OwnedCreateResult.AttemptRejected>(forgedWorker.forwardCreate(local))
                            assertIs<OwnedCreateResult.AttemptRejected>(ingress.createOwned(remote.copy(
                                policy = policy.copy(topologySha256 = "c".repeat(64)))))
                            assertIs<OwnedCreateResult.AttemptRejected>(ingress.createOwned(remote.copy(routeKey = localActor.toString())))

                            allowNew.set(false)
                            f.authority.stopNew(policy.generation)
                            val eventCount = f.count("room_ownership_pilot_events")
                            val revision = assertNotNull(f.rooms.read(remote.roomId)).revision
                            assertEquals(replay, ingress.createOwned(remote))
                            assertEquals(createdLocal.copy(replayed = true), ingress.createOwned(local))
                            assertEquals(revision, f.rooms.read(remote.roomId)?.revision)
                            assertEquals(eventCount, f.count("room_ownership_pilot_events"))
                            assertEquals(2, f.count("owned_room_create_operations"))
                            assertEquals(2, f.count("room_ownership_pilot_rooms"))
                            assertEquals(2, f.count("room_ownership_pilot_memberships"))
                        } finally { ingress.beginDrain(); ingressServer.stop(0, 1_000) }
                    } finally { proxy.stop(0, 1_000) }
                } finally { workerServer.stop(0, 5_000) }
            }
        }
    }

    private companion object {
        const val CREATE_PATH = "/internal/room-ownership/create"
        val OWNER_HEADERS = listOf("X-FastToWin-Owner-Sender", "X-FastToWin-Owner-Target", "X-FastToWin-Owner-Issued-At",
            "X-FastToWin-Owner-Nonce", "X-FastToWin-Owner-Signature")
    }
}
