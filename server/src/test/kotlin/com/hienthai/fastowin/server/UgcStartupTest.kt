package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.moderation.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UgcStartupTest {
    @Test fun `memory startup owner session authorizes moderation mutation`() = runTest {
        val (configuredAdmin, moderation) = createModerationRuntime(null, AdminConfiguration("http://localhost:8080", false))
        val admin = assertNotNull(configuredAdmin)
        admin.bootstrapOwner("owner@example.com", "GoodPassword123!")
        val login = admin.login("owner@example.com", "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
        val engine = GameEngine(moderationService = moderation)
        val owner = engine.connectGuest("Reported name", null)
        val reporter = engine.connectGuest("Reporter", null)
        val receipt = assertNotNull(assertIs<ServerMessage.ContentReportResult>(engine.handle(reporter.playerId,
            ClientMessage.ReportContent(ContentReportRequest(UgcTargetType.PLAYER_PROFILE, owner.playerId,
                UgcReportReason.HARASSMENT, requestId = "startup-report"))).single().message).receipt)
        val context = ModerationAdminContext(login.token, login.response.csrfToken, "http://localhost:8080")

        val resolved = moderation.resolve(context, receipt.reportId,
            ModerationResolveRequest(1, "startup-remove", "Reviewed", ModerationStatus.RESOLVED, true))

        assertEquals(ModerationStatus.RESOLVED, resolved.report.status)
        assertEquals(owner.playerId, resolved.targetOwnerId)
        assertTrue(moderation.isSuppressed(UgcTargetType.PLAYER_PROFILE, owner.playerId, contentRevision("Reported name")))
        assertEquals(resolved, moderation.detail(context, receipt.reportId))
    }
}