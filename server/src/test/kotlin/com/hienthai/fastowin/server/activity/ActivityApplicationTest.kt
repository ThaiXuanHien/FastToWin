package com.hienthai.fastowin.server.activity

import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.protocol.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class ActivityApplicationTest {
    @Test fun onlyAcceptedVerifiedAccountSocketIsObservedAndIdleDisconnectIsFlushed() = testApplication {
        val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val registered = assertIs<AuthResult.Success>(auth.register("activity@example.com", "StrongPassword123!", "Player", "android")).session
        val unverified = assertIs<AuthResult.Success>(auth.register("unverified@example.com", "StrongPassword123!", "Other", "android")).session
        val code = assertIs<AccountActionResult.Success>(auth.requestEmailVerification(registered.accessToken)).emailVerificationCode!!
        assertIs<AccountActionResult.Success>(auth.confirmEmailVerification(registered.accessToken, code))
        val observations = CopyOnWriteArrayList<ActivityObservation>()
        val tracker = ActivityTracker(object : ActivityRepository {
            override suspend fun checkpoint(checkpoint: ActivityCheckpoint) { observations.addAll(checkpoint.observations) }
        })
        application { gameModule(authService = auth, activityTracker = tracker) }
        val sockets = createClient { install(WebSockets) }
        suspend fun rejected(message: ClientMessage, code: String) {
            val socket = sockets.webSocketSession("/game")
            try { socket.send(Frame.Text(ProtocolJson.encodeToString(message)))
                withTimeout(5000) { while (true) {
                    val frame = socket.incoming.receive() as? Frame.Text ?: continue
                    val response = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                    if (response is ServerMessage.Error) { assertEquals(code, response.code); break }
                } }
            } finally { socket.close() }
        }
        rejected(ClientMessage.ConnectAccount("invalid"), "INVALID_ACCESS_TOKEN")
        rejected(ClientMessage.ConnectAccount(unverified.accessToken), "EMAIL_NOT_VERIFIED")
        rejected(ClientMessage.ConnectAccount(registered.accessToken, protocolVersion = 37), "PROTOCOL_MISMATCH")
        val guest = sockets.webSocketSession("/game")
        guest.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.ConnectGuest("Guest"))))
        withTimeout(5000) { while (ProtocolJson.decodeFromString<ServerMessage>((guest.incoming.receive() as Frame.Text).readText()) !is ServerMessage.SessionReady) {} }
        guest.close()
        assertTrue(observations.isEmpty())
        val accepted = sockets.webSocketSession("/game")
        accepted.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.ConnectAccount(registered.accessToken))))
        withTimeout(5000) { while (ProtocolJson.decodeFromString<ServerMessage>((accepted.incoming.receive() as Frame.Text).readText()) !is ServerMessage.SessionReady) {} }
        assertEquals(setOf(registered.userId), observations.map { it.userId }.toSet())
        val initial = observations.last(); delay(5)
        val replacement = sockets.webSocketSession("/game")
        replacement.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.ConnectAccount(registered.accessToken))))
        withTimeout(5000) { while (ProtocolJson.decodeFromString<ServerMessage>((replacement.incoming.receive() as Frame.Text).readText()) !is ServerMessage.SessionReady) {} }
        val second = observations.last { it.connectionId != initial.connectionId }
        withTimeout(5000) { accepted.closeReason.await() }
        delay(20)
        val afterOldClose = observations.size
        tracker.checkpoint()
        val next = observations.drop(afterOldClose)
        assertEquals(setOf(second.connectionId), next.map { it.connectionId }.toSet())
        delay(5); replacement.close()
        withTimeout(5000) { while (observations.none { it.connectionId == second.connectionId && it.until > next.last().until }) delay(10) }
        assertEquals(2, observations.map { it.connectionId }.distinct().size)
    }
}
