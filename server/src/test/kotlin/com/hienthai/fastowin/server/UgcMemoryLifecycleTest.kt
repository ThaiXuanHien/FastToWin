package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.moderation.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class UgcMemoryLifecycleTest {
    @Test fun `editing public profile refreshes moderation target before stale removal`() = runTest {
        val admins=InMemoryAdminRepository()
        val admin=AdminService(admins,AdminConfiguration("http://localhost:8080",false),PasswordHasher(1000))
        admin.bootstrapOwner("owner@example.com","GoodPassword123!")
        val login=admin.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
        val moderation=ModerationService(InMemoryModerationRepository(admins),admin)
        val targetId=UUID.randomUUID()
        var profile=PlayerProfileSnapshot(targetId.toString(),"Original","code")
        val profiles=object:PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun findByPlayerId(playerId:String)=profile.takeIf {it.userId==playerId}
            override suspend fun updateProfile(playerId:String,displayName:String,avatarId:String?):Boolean {
                if(playerId!=profile.userId)return false
                profile=profile.copy(displayName=displayName,avatarId=avatarId);return true
            }
        }
        val engine=GameEngine(playerProfileRepository=profiles,moderationService=moderation)
        engine.connectAccount(AuthenticatedAccount(targetId,"Original"))
        val reporter=engine.connectGuest("Reporter",null)
        val receipt=assertNotNull(assertIs<ServerMessage.ContentReportResult>(engine.handle(reporter.playerId,
            ClientMessage.ReportContent(ContentReportRequest(UgcTargetType.PLAYER_PROFILE,profile.userId,UgcReportReason.SPAM,requestId="report-old"))).single().message).receipt)
        engine.handle(profile.userId,ClientMessage.UpdateProfile("Changed",null))
        val context=ModerationAdminContext(login.token,login.response.csrfToken,"http://localhost:8080")
        assertTrue(moderation.detail(context,receipt.reportId).targetChanged)
        assertFailsWith<AdminException> {moderation.resolve(context,receipt.reportId,ModerationResolveRequest(1,"remove-old","Review",ModerationStatus.RESOLVED,true))}
        assertTrue(moderation.suppressions().isEmpty())
    }
    @Test fun `memory content removal warns owner once even when action response is retried`() = runTest {
        val admins=InMemoryAdminRepository()
        val admin=AdminService(admins,AdminConfiguration("http://localhost:8080",false),PasswordHasher(1000))
        admin.bootstrapOwner("owner@example.com","GoodPassword123!")
        val login=admin.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
        val moderation=ModerationService(InMemoryModerationRepository(admins),admin)
        val notifications=InMemoryNotificationRepository()
        val engine=GameEngine(moderationService=moderation,notificationRepository=notifications)
        val target=engine.connectGuest("Original",null)
        val reporter=engine.connectGuest("Reporter",null)
        val receipt=assertNotNull(assertIs<ServerMessage.ContentReportResult>(engine.handle(reporter.playerId,
            ClientMessage.ReportContent(ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target.playerId,UgcReportReason.SPAM,requestId="warning"))).single().message).receipt)
        val context=ModerationAdminContext(login.token,login.response.csrfToken,"http://localhost:8080")
        val action=ModerationResolveRequest(1,"remove-warning","Review",ModerationStatus.RESOLVED,true)
        moderation.resolve(context,receipt.reportId,action)
        moderation.resolve(context,receipt.reportId,action)
        val warning=notifications.loadNotifications(target.playerId).single()
        assertEquals("moderation:${receipt.reportId}",warning.id)
        assertEquals("UgcContentRemovedBody",warning.messageKey)
    }
    @Test fun `memory avatar replacement advances revision and bypasses old image suppression`() = runTest {
        val id=UUID.randomUUID().toString()
        var data:String?=null
        val profiles=object:PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun findByPlayerId(playerId:String)=PlayerProfileSnapshot(id,"Player","code")
            override suspend fun getAvatarData(playerId:String)=data
            override suspend fun updateAvatarData(playerId:String,base64:String):Boolean {data=base64;return true}
        }
        val repository=com.hienthai.fastowin.server.avatars.ProfileAvatarRepository(profiles)
        assertTrue(repository.writeLegacy(id,0,"first"))
        assertEquals(1L,repository.current(id)?.revision)
        assertFalse(repository.writeLegacy(id,0,"stale"))
        assertTrue(repository.writeLegacy(id,1,"replacement"))
        assertEquals(2L,repository.current(id)?.revision)
    }
}
