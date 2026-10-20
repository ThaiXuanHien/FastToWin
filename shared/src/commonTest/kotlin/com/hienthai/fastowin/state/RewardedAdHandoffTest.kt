package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.platform.*
import com.hienthai.fastowin.protocol.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RewardedAdHandoffTest {
    @Test fun `HTTP granted while socket mailbox is absent settles after reconnect without restart`() = runTest {
        val f = Fixture(this)
        try {
            f.disconnect()
            f.grantThroughHttp()
            assertEquals("session_1", f.controller.uiState.value.claimingRewardedAdRequestId)
            assertTrue(f.transport.claims.isEmpty())
            f.reconnect()
            f.assertSettledAndNextWatchUsable()
            assertEquals(1, f.transport.claims.size)
        } finally { f.close() }
    }

    @Test fun `sent verified claim with lost reply is replayed idempotently after reconnect`() = runTest {
        val f = Fixture(this)
        try {
            f.transport.dropReply = true
            f.grantThroughHttp()
            assertEquals(1, f.transport.claims.size)
            assertEquals("session_1", f.controller.uiState.value.claimingRewardedAdRequestId)
            f.disconnect()
            f.transport.dropReply = false
            f.reconnect()
            f.assertSettledAndNextWatchUsable()
            assertEquals(2, f.transport.claims.size)
            assertEquals(f.transport.claims[0], f.transport.claims[1])
        } finally { f.close() }
    }

    @Test fun `pending verified handoff cannot replay into a different authenticated player`() = runTest {
        val f = Fixture(this)
        try {
            f.disconnect()
            f.grantThroughHttp()
            f.reconnect("other_owner")
            assertTrue(f.transport.claims.isEmpty())
            assertNull(f.controller.uiState.value.claimingRewardedAdRequestId)
            assertEquals(0, f.controller.uiState.value.playQuota?.bonusMatchesGranted)
        } finally { f.close() }
    }

    @Test fun `stale receipt result cannot settle a different pending session`() = runTest {
        val f = Fixture(this)
        try {
            f.transport.dropReply = true
            f.grantThroughHttp()
            f.transport.sessions.last().server(ServerMessage.RewardedAdBonusResult(
                "old_session", RewardedAdBonusStatus.ALREADY_GRANTED, exhausted))
            runCurrent()
            assertEquals("session_1", f.controller.uiState.value.claimingRewardedAdRequestId)
            assertEquals(0, f.controller.uiState.value.playQuota?.bonusMatchesGranted)
            f.transport.dropReply = false
            f.disconnect(); f.reconnect()
            f.assertSettledAndNextWatchUsable()
        } finally { f.close() }
    }

    @Test fun `disposed controller rejects a late verified receipt`() = runTest {
        val f = Fixture(this)
        f.close()
        assertFalse(f.controller.claimRewardedAdBonus(RewardedAdReceipt(
            "session_1", RewardedAdProvider.ADMOB_ANDROID, "verified_transaction", "session_1")))
    }

    private class Fixture(val test: TestScope) {
        val transport = Transport()
        val controller = GameController("ws://test", InMemoryResumeTokenStore(), transport,
            test.backgroundScope, "Owner", { "access" })
        val api = Api()
        val driver = Driver()
        val gateway = VerifiedRewardedAdGateway(api, driver, Store(), test.backgroundScope,
            nowMillis = { test.testScheduler.currentTime })
        init {
            test.runCurrent()
            test.backgroundScope.launch { gateway.receipts.collect { controller.claimRewardedAdBonus(it) } }
            transport.sessions.single().server(ServerMessage.SessionReady("owner"))
            test.runCurrent()
            gateway.configure(RewardedAdAvailability.MOBILE_PRODUCTION)
        }
        fun grantThroughHttp() {
            gateway.show("owner"); test.runCurrent()
            api.granted = true
            transport.authoritative = committed
            driver.outcome.complete(RewardedAdShowResult.EARNED)
            test.runCurrent()
            assertFalse(gateway.state.value.isPendingVerification)
        }
        fun disconnect() { transport.sessions.last().incoming.close(); test.runCurrent() }
        fun reconnect(owner: String = "owner") {
            controller.retryConnection(); test.runCurrent()
            transport.sessions.last().server(ServerMessage.SessionReady(owner)); test.runCurrent()
        }
        fun assertSettledAndNextWatchUsable() {
            assertNull(controller.uiState.value.claimingRewardedAdRequestId)
            assertEquals(2, controller.uiState.value.playQuota?.bonusMatchesGranted)
            assertEquals(2, controller.uiState.value.playQuota?.remainingMatches)
            // Authoritative consumption of the two committed matches reopens Watch in the same controller.
            transport.sessions.last().server(ServerMessage.PlayQuotaData(committed.copy(matchesConsumed = 12, remainingMatches = 0)))
            test.runCurrent()
            controller.showPlayQuotaDialog()
            assertTrue(controller.uiState.value.showPlayQuotaExhaustedDialog)
            assertTrue(gateway.state.value.isReady)
            assertFalse(gateway.state.value.isLoading)
            assertNull(controller.uiState.value.claimingRewardedAdRequestId)
            gateway.show("owner"); test.runCurrent()
            assertEquals(2, api.prepared)
            assertEquals(2, driver.shown)
            assertEquals(2, controller.uiState.value.playQuota?.bonusMatchesGranted)
        }
        fun close() { gateway.close(); controller.close() }
    }

    private class Store : PendingRewardedAdStore {
        private var session: String? = null
        override fun read(ownerId: String) = session
        override fun write(ownerId: String, sessionId: String?) { session = sessionId }
    }
    private class Api : RewardedAdSessionGateway {
        override val ownerId = "owner"
        var prepared = 0
        var granted = false
        override suspend fun prepare(requestId: String): RewardedAdSessionView {
            prepared++
            return pending.copy(sessionId = "session_$prepared")
        }
        override suspend fun status(sessionId: String) = if (granted && sessionId == "session_1")
            pending.copy(status = RewardedAdSessionStatus.GRANTED,
                providerTransactionId = "verified_transaction", quota = committed)
            else pending.copy(sessionId = sessionId)
        override fun close() = Unit
    }
    private class Driver : RewardedAdDriver {
        val outcome = CompletableDeferred<RewardedAdShowResult>()
        var shown = 0
        override suspend fun authorize() = RewardedAdAuthorization.ALLOWED
        override suspend fun load() = true
        override suspend fun show(session: RewardedAdSessionView): RewardedAdShowResult { shown++; return outcome.await() }
        override fun close() = Unit
    }
    private class Transport : SocketTransport {
        val sessions = mutableListOf<Session>()
        val claims = mutableListOf<ClientMessage.ClaimRewardedAdBonus>()
        var dropReply = false
        var authoritative = exhausted
        override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
            val session = Session(this)
            sessions += session
            try { block(session) } finally { session.close() }
        }
    }
    private class Session(val transport: Transport) : SocketSession {
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        var owner = "owner"
        override suspend fun send(frame: Frame) {
            when (val message = ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText())) {
                ClientMessage.GetPlayQuota -> server(ServerMessage.PlayQuotaData(if (owner == "owner") transport.authoritative else exhausted))
                is ClientMessage.ClaimRewardedAdBonus -> {
                    assertEquals("owner", owner)
                    assertEquals(ClientMessage.ClaimRewardedAdBonus("session_1", RewardedAdProvider.ADMOB_ANDROID,
                        "verified_transaction", "session_1"), message)
                    transport.claims += message
                    // SSV already committed exactly +2. Receipt replay only returns current truth.
                    if (!transport.dropReply) server(ServerMessage.RewardedAdBonusResult(
                        message.requestId, RewardedAdBonusStatus.ALREADY_GRANTED, committed))
                }
                else -> Unit
            }
        }
        fun server(message: ServerMessage) {
            if (message is ServerMessage.SessionReady) owner = message.playerId
            check(incoming.trySend(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(message))).isSuccess)
        }
        override suspend fun close() { incoming.close() }
        override suspend fun closeReason(): String? = null
    }
    private companion object {
        val exhausted = PlayQuotaSnapshot("2026-10-20", matchesConsumed = 10, remainingMatches = 0,
            nextResetAtEpochMillis = 1000000, rewardedAdAvailability = RewardedAdAvailability.MOBILE_PRODUCTION)
        val committed = exhausted.copy(bonusMatchesGranted = 2, remainingMatches = 2)
        val pending = RewardedAdSessionView("session_1", "opaque_nonce", "owned_unit", 1000000, RewardedAdSessionStatus.PENDING)
    }
}
