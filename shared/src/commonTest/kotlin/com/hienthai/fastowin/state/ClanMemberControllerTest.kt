package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClanMemberControllerTest {
    @Test fun `membership removal profile closes the stale clan view`() = runTest {
        val f = Fixture(this)
        try {
            f.server(ServerMessage.ProfileData(PlayerProfileSnapshot("owner", "Owner", "OWNER", clanId = "viewed")))
            f.server(ServerMessage.ClanInfoData(clan("viewed")))
            f.server(ServerMessage.ProfileData(PlayerProfileSnapshot("owner", "Owner", "OWNER")))
            assertNull(f.controller.uiState.value.currentClan)
            assertNull(f.controller.uiState.value.profile?.clanId)
        } finally { f.controller.close() }
    }

    @Test fun `opening clan refreshes social state before allowing friend actions`() = runTest {
        val f = Fixture(this)
        try {
            f.transport.sent.clear()
            f.controller.openClan(); runCurrent()
            assertTrue(ClientMessage.GetFriends in f.transport.sent)
            assertTrue(f.controller.uiState.value.isFriendsLoading)
            assertFalse(f.controller.uiState.value.isClanSocialReady)
            f.server(ServerMessage.FriendsData(FriendsSnapshot()))
            assertFalse(f.controller.uiState.value.isFriendsLoading)
            assertTrue(f.controller.uiState.value.isClanSocialReady)
        } finally { f.controller.close() }
    }

    @Test fun `presence refresh cannot replace a different viewed clan or change its roster`() = runTest {
        val f = Fixture(this)
        try {
            f.server(ServerMessage.ClanInfoData(clan("viewed")))
            f.server(ServerMessage.ClanInfoData(clan("other"), isPresenceRefresh = true))
            assertEquals("viewed", f.controller.uiState.value.currentClan?.id)
            val updated = clan("viewed").copy(members = listOf(member.copy(
                displayName = "stale name", presence = FriendPresence.PLAYING)))
            f.server(ServerMessage.ClanInfoData(updated, isPresenceRefresh = true))
            val visible = requireNotNull(f.controller.uiState.value.currentClan)
            assertEquals("Member", visible.members.single().displayName)
            assertEquals(FriendPresence.PLAYING, visible.members.single().presence)
            f.controller.leaveClan(); runCurrent()
            f.server(ServerMessage.ClanInfoData(updated, isPresenceRefresh = true))
            assertNull(f.controller.uiState.value.currentClan)
        } finally { f.controller.close() }
    }

    @Test fun `disconnect clears stale online state and reconnect refreshes viewed clan`() = runTest {
        val f = Fixture(this)
        try {
            f.controller.openClan(); runCurrent()
            f.server(ServerMessage.ClanInfoData(clan("viewed")))
            f.transport.session.incoming.close(); runCurrent()
            assertEquals(FriendPresence.OFFLINE, f.controller.uiState.value.currentClan?.members?.single()?.presence)
            f.transport.sent.clear()
            f.controller.retryConnection(); runCurrent()
            f.server(ServerMessage.SessionReady("owner"))
            assertTrue(ClientMessage.GetClanInfo("viewed") in f.transport.sent)
            assertTrue(ClientMessage.GetFriends in f.transport.sent)
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
        val sent = mutableListOf<ClientMessage>()
        override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
            session = Session(sent)
            try { block(session) } finally { session.close() }
        }
    }
    private class Session(val sent: MutableList<ClientMessage>) : SocketSession {
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        override suspend fun send(frame: Frame) { sent += ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText()) }
        fun server(message: ServerMessage) { check(incoming.trySend(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(message))).isSuccess) }
        override suspend fun close() { incoming.close() }
        override suspend fun closeReason(): String? = null
    }
    private companion object {
        val member = ClanMemberSnapshot("member", "Member", ClanRole.MEMBER, 1_000, presence = FriendPresence.ONLINE)
        fun clan(id: String) = ClanSnapshot(id, id, "", "owner", listOf(member), 0)
    }
}
