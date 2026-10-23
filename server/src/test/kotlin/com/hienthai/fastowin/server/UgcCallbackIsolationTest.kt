package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.moderation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UgcCallbackIsolationTest {
    private class Fixture(val notifications:NotificationRepository=InMemoryNotificationRepository()) {
        val admins=InMemoryAdminRepository()
        val admin=AdminService(admins,AdminConfiguration("http://localhost:8080",false),PasswordHasher(1000))
        val moderation=ModerationService(InMemoryModerationRepository(admins),admin)
        lateinit var target:ConnectedPlayer
        lateinit var context:ModerationAdminContext
        lateinit var reportId:String
        val changes=mutableListOf<Pair<UgcTargetType,String>>()
        suspend fun setup(previousWarning:suspend(String,String)->Unit={_,_->}) {
            moderation.onContentRemoved=previousWarning
            val engine=GameEngine(moderationService=moderation,notificationRepository=notifications)
            admin.bootstrapOwner("owner@example.com","GoodPassword123!")
            val login=admin.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
            context=ModerationAdminContext(login.token,login.response.csrfToken,"http://localhost:8080")
            target=engine.connectGuest("Target",null)
            val reporter=engine.connectGuest("Reporter",null)
            reportId=assertNotNull(assertIs<ServerMessage.ContentReportResult>(engine.handle(reporter.playerId,
                ClientMessage.ReportContent(ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target.playerId,UgcReportReason.SPAM,requestId="callback-report"))).single().message).receipt).reportId
            moderation.onSuppressionChanged={type,id->changes+=type to id}
        }
        suspend fun remove()=moderation.resolve(context,reportId,ModerationResolveRequest(1,"callback-remove","Reviewed",ModerationStatus.RESOLVED,true))
    }
    @Test fun `prior warning listener failure does not prevent owner warning or live callback`()=runTest {
        val f=Fixture();f.setup {_,_->error("prior listener unavailable")}
        assertEquals(ModerationStatus.RESOLVED,f.remove().report.status)
        assertEquals("moderation:${f.reportId}",f.notifications.loadNotifications(f.target.playerId).single().id)
        assertEquals(listOf(UgcTargetType.PLAYER_PROFILE to f.target.playerId),f.changes)
    }
    @Test fun `warning storage failure cannot prevent committed response or live callback`()=runTest {
        val sink=object:NotificationRepository by InMemoryNotificationRepository() {
            override suspend fun createNotifications(userId:String,notifications:List<NotificationSnapshot>){error("notification unavailable")}
        }
        val f=Fixture(sink);f.setup()
        assertEquals(ModerationStatus.RESOLVED,f.remove().report.status)
        assertEquals(listOf(UgcTargetType.PLAYER_PROFILE to f.target.playerId),f.changes)
        assertTrue(f.moderation.isSuppressed(UgcTargetType.PLAYER_PROFILE,f.target.playerId))
    }
    @Test fun `live listener failure cannot turn committed removal into failed response`()=runTest {
        val f=Fixture();f.setup();f.moderation.onSuppressionChanged={_,_->error("live listener unavailable")}
        assertEquals(ModerationStatus.RESOLVED,f.remove().report.status)
        assertEquals(1,f.notifications.loadNotifications(f.target.playerId).size)
        assertTrue(f.moderation.isSuppressed(UgcTargetType.PLAYER_PROFILE,f.target.playerId))
    }
    @Test fun `cancellation still propagates after committed removal`()=runTest {
        val f=Fixture();f.setup {_,_->throw CancellationException("cancelled")}
        assertFailsWith<CancellationException>{f.remove()}
        assertTrue(f.changes.isEmpty())
        assertTrue(f.moderation.isSuppressed(UgcTargetType.PLAYER_PROFILE,f.target.playerId))
    }
}
