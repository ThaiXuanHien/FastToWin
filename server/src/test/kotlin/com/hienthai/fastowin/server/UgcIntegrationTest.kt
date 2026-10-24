package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UgcIntegrationTest {
    @Test fun `memory restriction denies refresh login new connection and revokes stored sessions`() = runTest {
        val repository=InMemoryAuthRepository()
        val moderationRepository=InMemoryModerationRepository()
        val moderation=ModerationService(moderationRepository)
        val auth=AuthenticationService(repository,PasswordHasher(1000),moderationService=moderation)
        val session=assertIs<AuthResult.Success>(auth.register("blocked@example.com","Password-123","Blocked",null)).session
        val identity=assertNotNull(auth.authenticateAccessToken(session.accessToken))
        val engine=GameEngine(moderationService=moderation)
        engine.connectAccount(identity)
        moderationRepository.transaction { putRestriction(ModerationRestrictionView(session.userId,1,true),System.currentTimeMillis()) }
        assertEquals("ACCOUNT_BLOCKED",assertIs<AuthResult.Failure>(auth.refresh(session.refreshToken)).code)
        assertEquals("ACCOUNT_BLOCKED",assertIs<AuthResult.Failure>(auth.login("blocked@example.com","Password-123",null)).code)
        assertNull(auth.authenticateAccessToken(session.accessToken))
        assertTrue(repository.listActiveSessions(identity.userId,System.currentTimeMillis()).isEmpty())
        assertFails {engine.connectAccount(identity)}
        assertEquals("ACCOUNT_BLOCKED",assertIs<ServerMessage.Error>(engine.handle(session.userId,ClientMessage.ListRooms).single().message).code)
    }

    @Test fun `existing account can login and delete without accepting new content terms`() = runTest {
        var enforced=false
        val moderation=ModerationService(InMemoryModerationRepository(),enforcementEnabled={enforced})
        val auth=AuthenticationService(InMemoryAuthRepository(),PasswordHasher(1000),moderationService=moderation)
        assertIs<AuthResult.Success>(auth.register("existing@example.com","Password-123","Existing",null))
        enforced=true
        val login=assertIs<AuthResult.Success>(auth.login("existing@example.com","Password-123",null)).session
        assertNull(moderation.terms(login.userId).acceptedVersion)
        assertIs<AccountActionResult.Success>(auth.deleteAccount(login.accessToken,"Password-123"))
    }

    @Test fun `accepted guest resumes without resending local acceptance`() = runTest {
        val moderation=ModerationService(InMemoryModerationRepository(),enforcementEnabled={true})
        val engine=GameEngine(moderationService=moderation)
        val original=engine.connectGuest("Guest",null,CONTENT_TERMS_VERSION)
        val resumed=engine.connectGuest("Guest",original.resumeToken)
        assertEquals(original.playerId,resumed.playerId)
        assertEquals(CONTENT_TERMS_VERSION,moderation.terms(resumed.playerId).acceptedVersion)
    }
    @Test fun `unaccepted resumed guest receives consent error`() = runTest {
        var enforced=false
        val identities=InMemoryGuestIdentityRepository()
        val moderation=ModerationService(InMemoryModerationRepository(),enforcementEnabled={enforced})
        val engine=GameEngine(identityRepository=identities,moderationService=moderation)
        val original=engine.connectGuest("Original",null)
        enforced=true
        val failure=assertFailsWith<ModerationException> {engine.connectGuest("Changed",original.resumeToken)}
        assertEquals("CONTENT_TERMS_REQUIRED",failure.code)
    }

    @Test fun `guest cannot publish name without current terms`() = runTest {
        val moderation = ModerationService(InMemoryModerationRepository(), enforcementEnabled = { true })
        val engine = GameEngine(moderationService = moderation)
        assertFails { engine.connectGuest("Public name", null) }
        val accepted = engine.connectGuest("Public name", null, CONTENT_TERMS_VERSION)
        assertEquals(CONTENT_TERMS_VERSION, moderation.terms(accepted.playerId).acceptedVersion)
    }
    @Test fun `unaccepted existing identity may read terms but cannot edit UGC`() = runTest {
        var enforced = false
        val moderation = ModerationService(InMemoryModerationRepository(), enforcementEnabled = { enforced })
        val engine = GameEngine(moderationService = moderation)
        val player = engine.connectGuest("Existing", null)
        enforced = true
        val terms = engine.handle(player.playerId, ClientMessage.GetContentTerms).single().message
        assertIs<ServerMessage.ContentTermsData>(terms)
        for (command in listOf(ClientMessage.CreateClan("New", "description"), ClientMessage.UpdateAvatar("image"))) {
            assertEquals("CONTENT_TERMS_REQUIRED", assertIs<ServerMessage.Error>(engine.handle(player.playerId, command).single().message).code)
        }
    }
    @Test fun `unauthenticated identity cannot submit reports or accept terms`() = runTest {
        val moderation = ModerationService(InMemoryModerationRepository())
        val engine = GameEngine(moderationService = moderation)
        assertEquals("SESSION_NOT_FOUND", assertIs<ServerMessage.Error>(engine.handle("spoofed", ClientMessage.AcceptContentTerms(CONTENT_TERMS_VERSION)).single().message).code)
    }
    @Test fun `signup requires current consent before creating account and persists acceptance`() = runTest {
        val moderation = ModerationService(InMemoryModerationRepository(), enforcementEnabled = { true })
        val auth = AuthenticationService(InMemoryAuthRepository(), moderationService = moderation)
        assertEquals("CONTENT_TERMS_REQUIRED", assertIs<AuthResult.Failure>(auth.register("a@example.com", "password123", "Name", null)).code)
        val session = assertIs<AuthResult.Success>(auth.register("a@example.com", "password123", "Name", null, acceptedContentTermsVersion = CONTENT_TERMS_VERSION)).session
        assertEquals(CONTENT_TERMS_VERSION, moderation.terms(session.userId).acceptedVersion)
        val login = assertIs<AuthResult.Success>(auth.login("a@example.com", "password123", null)).session
        assertIs<AccountActionResult.Success>(auth.deleteAccount(login.accessToken, "password123"))
        assertNull(moderation.repository.transaction { acceptedVersion(session.userId) })
    }
}
