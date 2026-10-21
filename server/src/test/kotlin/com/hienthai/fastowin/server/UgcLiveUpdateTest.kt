package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.moderation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlin.test.*

class UgcLiveUpdateTest {
    @Test fun `committed removal invalidates aware client while legacy socket remains usable`() = testApplication {
        val admins=InMemoryAdminRepository()
        val admin=AdminService(admins,AdminConfiguration("http://localhost:8080",false),PasswordHasher(1000))
        admin.bootstrapOwner("owner@example.com","GoodPassword123!")
        val login=admin.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
        val moderation=ModerationService(InMemoryModerationRepository(admins),admin)
        val engine=GameEngine(moderationService=moderation)
        val auth=AuthenticationService(InMemoryAuthRepository(),PasswordHasher(1000),moderationService=moderation)
        val account=assertIs<AuthResult.Success>(auth.register("aware@example.com","Password-123","Aware",null)).session
        val verification=assertIs<AccountActionResult.Success>(auth.requestEmailVerification(account.accessToken))
        auth.confirmEmailVerification(account.accessToken,assertNotNull(verification.emailVerificationCode))
        application {gameModule(engine=engine,authService=auth)}
        val client=createClient {install(WebSockets)}
        val legacy=client.webSocketSession("/game")
        val aware=client.webSocketSession("/game")
        suspend fun DefaultClientWebSocketSession.command(value:ClientMessage){send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(value)))}
        suspend fun DefaultClientWebSocketSession.next():ServerMessage=withTimeout(3000) {
            while(true){val frame=incoming.receive();if(frame is Frame.Text)return@withTimeout ProtocolJson.decodeFromString<ServerMessage>(frame.readText())}
            error("unreachable")
        }
        try {
            legacy.command(ClientMessage.ConnectGuest("Legacy"))
            while(legacy.next() !is ServerMessage.RoomList){}
            aware.command(ClientMessage.ConnectAccount(account.accessToken))
            val session=assertIs<ServerMessage.SessionReady>(aware.next())
            while(aware.next() !is ServerMessage.RoomList){}
            aware.command(ClientMessage.CreateRoom("Unsafe room","",ProtocolGameMode.ORDER))
            var roomCreated=aware.next()
            while(roomCreated !is ServerMessage.RoomCreated){assertFalse(roomCreated is ServerMessage.Error);roomCreated=aware.next()}
            val room=roomCreated.game
            while(aware.next() !is ServerMessage.RoomList){}
            aware.command(ClientMessage.GetContentTerms)
            while(aware.next() !is ServerMessage.ContentTermsData){}
            val receipt=assertNotNull(assertIs<ServerMessage.ContentReportResult>(engine.handle(session.playerId,
                ClientMessage.ReportContent(ContentReportRequest(UgcTargetType.ROOM,room.roomId,UgcReportReason.SPAM,requestId="live-report"))).single().message).receipt)
            moderation.resolve(ModerationAdminContext(login.token,login.response.csrfToken,"http://localhost:8080"),receipt.reportId,
                ModerationResolveRequest(1,"live-remove","Review",ModerationStatus.RESOLVED,true))
            val invalidated=assertIs<ServerMessage.ContentModerationChanged>(aware.next())
            assertEquals(UgcTargetType.ROOM,invalidated.targetType);assertEquals(room.roomId,invalidated.targetId)
            val refreshed=assertIs<ServerMessage.RoomUpdated>(aware.next()).game
            assertEquals("Room",refreshed.roomName)
            assertEquals(room.sequence,refreshed.sequence)
            assertEquals(room.phase,refreshed.phase)
            legacy.command(ClientMessage.MeasureLatency(1000))
            var message=legacy.next()
            while(message !is ServerMessage.LatencyPong){assertFalse(message is ServerMessage.ContentModerationChanged);message=legacy.next()}
            assertTrue(legacy.isActive)
        } finally {legacy.close();aware.close()}
    }
}
