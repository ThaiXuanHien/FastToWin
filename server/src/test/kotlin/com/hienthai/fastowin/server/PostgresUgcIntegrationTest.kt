package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresUgcIntegrationTest {
    @Test fun `guest consent commits with identity and survives account upgrade`() = runTest { database { source ->
        val moderation=ModerationService(PostgresModerationRepository(source),enforcementEnabled={true})
        val engine=GameEngine(identityRepository=PostgresGuestIdentityRepository(source),moderationService=moderation)
        assertFails { engine.connectGuest("Missing consent",null) }
        val guest=engine.connectGuest("Guest",null,CONTENT_TERMS_VERSION)
        assertEquals(CONTENT_TERMS_VERSION,moderation.terms(guest.playerId).acceptedVersion)
        assertEquals(guest.playerId,engine.connectGuest("Guest",guest.resumeToken).playerId)
        val auth=AuthenticationService(PostgresAuthRepository(source),PasswordHasher(1000),moderationService=moderation)
        val upgraded=assertIs<AuthResult.Success>(auth.upgradeGuest(assertNotNull(guest.resumeToken),"${UUID.randomUUID()}@example.com","Password-123",null,CONTENT_TERMS_VERSION)).session
        assertEquals(guest.playerId,upgraded.userId)
        assertEquals(CONTENT_TERMS_VERSION,moderation.terms(upgraded.userId).acceptedVersion)
    }}
    @Test fun `deleting account transaction scrubs authored report identity evidence and acceptance`() = runTest { database { source ->
        val moderation=ModerationService(PostgresModerationRepository(source),enforcementEnabled={true})
        val auth=AuthenticationService(PostgresAuthRepository(source),PasswordHasher(1000),moderationService=moderation)
        val account=assertIs<AuthResult.Success>(auth.register("${UUID.randomUUID()}@example.com","Password-123","Reporter",null,acceptedContentTermsVersion=CONTENT_TERMS_VERSION)).session
        val target=PostgresGuestIdentityRepository(source).resolveGuest("Target",null,System.currentTimeMillis(),CONTENT_TERMS_VERSION)
        val receipt=moderation.report(account.userId,ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target.playerId,UgcReportReason.SPAM,"private note","delete-report"))
        assertIs<AccountActionResult.Success>(auth.deleteAccount(account.accessToken,"Password-123"))
        moderation.repository.transaction {
            assertNull(acceptedVersion(account.userId))
            val erased=assertNotNull(report(receipt.reportId))
            assertNull(erased.reporterId);assertEquals("",erased.note);assertEquals("",erased.evidenceText)
        }
    }}
    @Test fun `restriction revokes access refresh login and existing game actions`() = runTest { database { source ->
        val moderation=ModerationService(PostgresModerationRepository(source))
        val auth=AuthenticationService(PostgresAuthRepository(source),PasswordHasher(1000),moderationService=moderation)
        val email="${UUID.randomUUID()}@example.com"
        val session=assertIs<AuthResult.Success>(auth.register(email,"Password-123","Player",null)).session
        val engine=GameEngine(moderationService=moderation)
        val account=assertNotNull(auth.authenticateAccessToken(session.accessToken))
        engine.connectAccount(account)
        moderation.repository.transaction {putRestriction(ModerationRestrictionView(session.userId,1,true),System.currentTimeMillis())}
        assertNull(auth.authenticateAccessToken(session.accessToken))
        assertIs<AuthResult.Failure>(auth.login(email,"Password-123",null))
        assertIs<AuthResult.Failure>(auth.refresh(session.refreshToken))
        assertEquals("ACCOUNT_BLOCKED",assertIs<ServerMessage.Error>(engine.handle(session.userId,ClientMessage.ListRooms).single().message).code)
        assertFails { engine.connectAccount(account) }
    }}
    @Test fun `login cannot publish a session after account becomes inactive under row lock`() = runTest { database { source ->
        val repository=PostgresAuthRepository(source)
        val moderation=ModerationService(PostgresModerationRepository(source))
        val auth=AuthenticationService(repository,PasswordHasher(1000),moderationService=moderation)
        val email="${UUID.randomUUID()}@example.com"
        val session=assertIs<AuthResult.Success>(auth.register(email,"Password-123","Player",null)).session
        val observed=assertNotNull(repository.findActiveAccount(email))
        moderation.repository.transaction {putRestriction(ModerationRestrictionView(session.userId,1,true),System.currentTimeMillis())}
        val now=System.currentTimeMillis()
        assertFails {repository.replaceSessions(observed.userId,null,NewAuthSession(UUID.randomUUID(),hashToken("new-access"),hashToken("new-refresh"),now+60_000,now+120_000,now))}
        assertTrue(repository.listActiveSessions(observed.userId,now).isEmpty())
    }}
    @Test fun `missing resumed consent cannot change nickname before PostgreSQL check`() = runTest { database { source ->
        val guest=PostgresGuestIdentityRepository(source).resolveGuest("Original",null,System.currentTimeMillis())
        val moderation=ModerationService(PostgresModerationRepository(source),enforcementEnabled={true})
        val engine=GameEngine(identityRepository=PostgresGuestIdentityRepository(source),moderationService=moderation)
        assertFails {engine.connectGuest("Changed",guest.resumeToken)}
        source.connection.use {connection ->
            connection.prepareStatement("SELECT display_name FROM profiles WHERE user_id=?").use {statement ->
                statement.setObject(1,UUID.fromString(guest.playerId))
                statement.executeQuery().use {result ->assertTrue(result.next());assertEquals("Original",result.getString(1))}
            }
        }
    }}
    private suspend fun database(block:suspend(HikariDataSource)->Unit) {
        val url=System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(),"TEST_DATABASE_URL required for PostgreSQL UGC integration")
        val schemaName="ugc_integration_"+UUID.randomUUID().toString().replace("-","")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl=url;username=System.getenv("TEST_DATABASE_USER")?:"fasttowin"
            password=System.getenv("TEST_DATABASE_PASSWORD")?:"fasttowin";maximumPoolSize=3;schema=schemaName
        }).use {source ->
            Flyway.configure().dataSource(source).schemas(schemaName).defaultSchema(schemaName).locations("classpath:db/migration").load().migrate()
            try {block(source)} finally {
                check(schemaName.matches(Regex("ugc_integration_[a-f0-9]{32}")))
                source.connection.use {connection ->connection.createStatement().use {it.execute("DROP SCHEMA $schemaName CASCADE")}}
            }
        }
    }
}
