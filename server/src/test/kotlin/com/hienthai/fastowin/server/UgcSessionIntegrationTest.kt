package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.*
import com.hienthai.fastowin.server.avatars.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlin.test.*

class UgcSessionIntegrationTest {
    @Test fun `gateway report IP limit returns correlated retryable result`() = testApplication {
        val engine=GameEngine()
        val target=engine.connectGuest("Target",null)
        application {gameModule(engine=engine,rateLimiter=InMemoryRateLimiter(nowMillis={1000}),
            rateLimitPolicies=ServerRateLimitPolicies(reportPerIp=RateLimitPolicy(1,600_000)))}
        val socket=createClient {install(WebSockets)}.webSocketSession("/game")
        suspend fun send(message:ClientMessage){socket.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message)))}
        suspend fun next():ServerMessage=withTimeout(3000) {
            while(true){val frame=socket.incoming.receive();if(frame is Frame.Text)return@withTimeout ProtocolJson.decodeFromString<ServerMessage>(frame.readText())}
            error("unreachable")
        }
        try {
            send(ClientMessage.ConnectGuest("Reporter"))
            while(next() !is ServerMessage.RoomList){}
            fun request(id:String)=ClientMessage.ReportContent(ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target.playerId,UgcReportReason.SPAM,requestId=id))
            send(request("first"))
            val first=assertIs<ServerMessage.ContentReportResult>(next());assertNotNull(first.receipt)
            send(request("second"))
            val limited=assertIs<ServerMessage.ContentReportResult>(next())
            assertEquals("second",limited.requestId);assertEquals("REPORT_RATE_LIMITED",limited.errorCode);assertNull(limited.receipt)
        } finally {socket.close()}
    }

    @Test fun `reports always use active actor and cannot impersonate another identity`() = runTest {
        val repository = InMemoryModerationRepository()
        val engine = GameEngine(moderationService=ModerationService(repository))
        val actor=engine.connectGuest("Actor",null)
        val target=engine.connectGuest("Target",null)
        val request=ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target.playerId,UgcReportReason.SPAM,requestId="actor-report")
        val result=assertIs<ServerMessage.ContentReportResult>(engine.handle(actor.playerId,ClientMessage.ReportContent(request)).single().message)
        val report=repository.transaction { report(assertNotNull(result.receipt).reportId) }
        assertEquals(actor.playerId,assertNotNull(report).reporterId)
        assertEquals("SESSION_NOT_FOUND",assertIs<ServerMessage.Error>(engine.handle("spoof",ClientMessage.ReportContent(request)).single().message).code)
    }
    @Test fun `old persisted acceptance cannot authorize new public content`() = runTest {
        var enforced=false
        val repository=InMemoryModerationRepository()
        val engine=GameEngine(moderationService=ModerationService(repository,enforcementEnabled={enforced}))
        val player=engine.connectGuest("Player",null)
        repository.transaction { acceptTerms(player.playerId,"old-version",1000) }
        enforced=true
        val commands=listOf(ClientMessage.UpdateProfile("New",null),ClientMessage.CreateRoom("Room","",ProtocolGameMode.ORDER),
            ClientMessage.CreateClan("Clan","Description"),ClientMessage.CreateTournament("Cup",ProtocolGameMode.ORDER),
            ClientMessage.UpdateAvatar("bad"),ClientMessage.UpdateClanLogo("clan","logo"))
        commands.forEach { command -> assertEquals("CONTENT_TERMS_REQUIRED",assertIs<ServerMessage.Error>(engine.handle(player.playerId,command).single().message).code) }
    }
    @Test fun `suppressed avatar read checks exact revision and publication guard precedes normalization`() = runTest {
        var revision=7L
        val repository=object:AvatarRepository by ProfileAvatarRepository(NoOpPlayerProfileRepository) {
            override suspend fun current(playerId:String)=AvatarSnapshot(revision,"invalid-image",null)
        }
        val id="11111111-1111-1111-1111-111111111111"
        val service=AvatarService(repository,publicationAllowed={error("TERMS_REQUIRED")},contentSuppressed={_,value->value=="7"})
        assertIs<AvatarReadResult.NotFound>(service.read(id))
        revision=8
        assertIs<AvatarReadResult.Unavailable>(service.read(id))
        assertFails { service.upload(id,"invalid-image") }
    }
    @Test fun `blocked active websocket denies actions and blocked identity cannot reconnect`() = testApplication {
        val repository=InMemoryModerationRepository()
        val moderation=ModerationService(repository)
        val engine=GameEngine(moderationService=moderation)
        application { gameModule(engine=engine) }
        val socket=createClient { install(WebSockets) }.webSocketSession("/game")
        suspend fun send(message:ClientMessage) {socket.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message)))}
        suspend fun next():ServerMessage=withTimeout(3000) {
            while(true) { val frame=socket.incoming.receive(); if(frame is Frame.Text) return@withTimeout ProtocolJson.decodeFromString<ServerMessage>(frame.readText()) }
            error("unreachable")
        }
        try {
            send(ClientMessage.GetContentTerms)
            assertIs<ServerMessage.ContentTermsData>(next())
            send(ClientMessage.ConnectGuest("Guest"))
            val ready=assertIs<ServerMessage.SessionReady>(next())
            while(next() !is ServerMessage.RoomList) { }
            repository.transaction { putRestriction(ModerationRestrictionView(ready.playerId,1,true),1000) }
            send(ClientMessage.ListRooms)
            assertEquals("ACCOUNT_BLOCKED",assertIs<ServerMessage.Error>(next()).code)
            assertFails { engine.connectGuest("Guest",ready.resumeToken) }
        } finally {socket.close()}
    }
}
