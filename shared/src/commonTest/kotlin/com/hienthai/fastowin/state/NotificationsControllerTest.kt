package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationsControllerTest {
    @Test fun `first socket read stays pending until authoritative inbox arrives`() = runTest {
        val f = Fixture(this)
        try {
            f.controller.openNotifications(); runCurrent()
            assertTrue(f.controller.uiState.value.isNotificationsLoading)
            assertFalse(f.controller.uiState.value.hasLoadedNotifications)
            f.server(ServerMessage.NotificationsData(emptyList()))
            assertFalse(f.controller.uiState.value.isNotificationsLoading)
            assertTrue(f.controller.uiState.value.hasLoadedNotifications)
            f.controller.openNotifications(); runCurrent()
            assertTrue(f.controller.uiState.value.hasLoadedNotifications)
            assertTrue(f.controller.uiState.value.isNotificationsLoading)
        } finally { f.controller.close() }
    }
    @Test fun `notification failure is scoped and explicit refresh clears it`() = runTest {
        val f = Fixture(this)
        try {
            f.server(ServerMessage.Error("NOT_NOTIFICATIONS_UNAVAILABLE", "Unavailable"))
            assertNotNull(f.controller.uiState.value.notificationsError)
            assertFalse(f.controller.uiState.value.hasLoadedNotifications)
            assertFalse(f.controller.uiState.value.isNotificationsLoading)
            f.server(ServerMessage.FriendsData(FriendsSnapshot()))
            assertNotNull(f.controller.uiState.value.notificationsError)
            f.controller.openNotifications(); runCurrent()
            assertNull(f.controller.uiState.value.notificationsError)
            f.server(ServerMessage.NotificationsData(emptyList()))
            assertTrue(f.controller.uiState.value.hasLoadedNotifications)
        } finally { f.controller.close() }
    }
    @Test fun `disconnect ends pending read and a different account cannot inherit loaded inbox`() = runTest {
        val f = Fixture(this)
        try {
            f.controller.openNotifications(); runCurrent()
            f.transport.session.incoming.close(); runCurrent()
            assertFalse(f.controller.uiState.value.isNotificationsLoading)
            assertNotNull(f.controller.uiState.value.notificationsError)
            f.controller.retryConnection(); runCurrent()
            f.server(ServerMessage.SessionReady("owner"))
            f.server(ServerMessage.NotificationsData(emptyList()))
            assertTrue(f.controller.uiState.value.hasLoadedNotifications)
            f.server(ServerMessage.SessionReady("other"))
            assertFalse(f.controller.uiState.value.hasLoadedNotifications)
        } finally { f.controller.close() }
    }
    private class Fixture(val test: TestScope) {
        val transport = Transport()
        val controller = GameController("ws://test", InMemoryResumeTokenStore(), transport,
            test.backgroundScope, "Owner", { "access" })
        init { test.runCurrent(); server(ServerMessage.SessionReady("owner")) }
        fun server(message: ServerMessage) { transport.session.server(message); test.runCurrent() }
    }
    private class Transport : SocketTransport {
        lateinit var session: Session
        override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
            session = Session(); try { block(session) } finally { session.close() }
        }
    }
    private class Session : SocketSession {
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        override suspend fun send(frame: Frame) { ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText()) }
        fun server(message: ServerMessage) { check(incoming.trySend(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(message))).isSuccess) }
        override suspend fun close() { incoming.close() }
        override suspend fun closeReason(): String? = null
    }
}
