package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementState
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PostgresAnnouncementDeliveryTest {
    private class PgFixture(val ds: HikariDataSource) {
        var now = 1_800_000_000_000L
        val admins = AdminService(PostgresAdminRepository(ds), AdminConfiguration("http://localhost:8080", false),
            PasswordHasher(iterations = 1_000), { now })
        val auth = AuthenticationService(PostgresAuthRepository(ds), PasswordHasher(iterations = 1_000), { now })
        val repository = PostgresNotificationRepository(ds)
        val service = AnnouncementService(repository.announcements, admins, nowMillis = { now })
        lateinit var owner: AnnouncementAdminContext
        lateinit var userId: String
        suspend fun setup() {
            admins.bootstrapOwner("owner@example.com", "GoodPassword123!")
            val login = admins.login("owner@example.com", "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
            owner = AnnouncementAdminContext(login.token, login.response.csrfToken, "http://localhost:8080")
            userId = assertIs<AuthResult.Success>(auth.register("announce-pg-${UUID.randomUUID()}@example.com",
                "password123", "Player", "android")).session.userId
        }
        suspend fun draft(pushEnabled: Boolean = false): AnnouncementAdminView = service.create(owner, AnnouncementDraftRequest(
            "Tiêu đề", "Title", "Nội dung", "Body", com.hienthai.fastowin.protocol.AdminAnnouncementCategory.GENERAL,
            startsAtEpochMillis = now + 1_000, endsAtEpochMillis = now + 10_000,
            pushEnabled = pushEnabled,
            requestId = UUID.randomUUID().toString(), reason = "Fixture"
        ))
        suspend fun publish(draft: AnnouncementAdminView, requestId: String = UUID.randomUUID().toString()): AnnouncementAdminView {
            val preview = service.preview(owner, draft.announcement.id)
            return service.publish(owner, draft.announcement.id,
                AnnouncementMutationRequest(draft.announcement.version, requestId, "Publish", preview.previewToken))
        }
        fun count(sql: String): Long = ds.connection.use { c -> c.createStatement().use { s ->
            s.executeQuery(sql).use { r -> r.next(); r.getLong(1) }
        } }
    }
    private suspend fun isolated(block: suspend PgFixture.() -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        val schema = "announce_task2_${UUID.randomUUID().toString().replace("-", "")}";
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 5
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schema") } }
            println("Task 2 owned PostgreSQL schema created: $schema")
            try {
                HikariDataSource(config().apply { this.schema = schema }).use { ds ->
                    Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema)
                        .locations("classpath:db/migration").load().migrate()
                    PgFixture(ds).apply { setup() }.block()
                }
            } finally {
                setup.connection.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA $schema CASCADE") } }
                println("Task 2 owned PostgreSQL schema dropped: $schema")
            }
        }
    }

    private fun waitForAdvisoryBlock(ds: HikariDataSource, holderPid: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val blocked = ds.connection.use { connection ->
                connection.prepareStatement("""SELECT EXISTS(SELECT 1 FROM pg_stat_activity a
                    WHERE ? = ANY(pg_blocking_pids(a.pid)) AND a.query LIKE '%pg_advisory_xact_lock%')""").use { statement ->
                    statement.setInt(1, holderPid)
                    statement.executeQuery().use { result -> result.next(); result.getBoolean(1) }
                }
            }
            if (blocked) return true
            Thread.sleep(20)
        }
        return false
    }


    @Test fun `round1 hide waiting on announcement does not hold user needed by delivery`() = runTest { isolated {
        val draft = service.create(owner, AnnouncementDraftRequest("Vi", "En", "Vi body", "En body",
            com.hienthai.fastowin.protocol.AdminAnnouncementCategory.GENERAL,
            startsAtEpochMillis = now, endsAtEpochMillis = now + 100_000, showBanner = true,
            requestId = UUID.randomUUID().toString(), reason = "Fixture"))
        val id = publish(draft).announcement.id
        service.deliverDueBatch()
        ds.connection.use { held ->
            held.autoCommit = false
            val tx = JdbcAnnouncementTransaction(held)
            tx.definition(id, true)
            val holderPid = held.createStatement().use { it.executeQuery("SELECT pg_backend_pid()").use { r -> r.next(); r.getInt(1) } }
            val hidden = CompletableFuture.supplyAsync { runBlocking { service.hideBanner(userId, id) } }
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var blocked = false
                while (!blocked && System.nanoTime() < deadline) {
                    blocked = ds.connection.use { c -> c.createStatement().use { st ->
                        st.executeQuery("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE $holderPid = ANY(pg_blocking_pids(pid)))").use { r -> r.next(); r.getBoolean(1) }
                    } }
                    if (!blocked) Thread.sleep(10)
                }
                assertTrue(blocked, "hide must be waiting on held announcement")
                held.createStatement().use { it.execute("SET LOCAL lock_timeout='500ms'") }
                val read = runCatching { tx.currentPlayer(userId) }
                assertTrue(read.isSuccess, "delivery must acquire user while hide waits on announcement: ${read.exceptionOrNull()?.javaClass?.simpleName}")
            } finally { held.rollback(); hidden.get(5, TimeUnit.SECONDS) }
        }
    } }


    @Test fun `round1 published event ordinary and compensation gift links preserve independent policies and assets`() = runTest { isolated {
        val actor = admins.session(owner.token).admin.id
        val events = com.hienthai.fastowin.server.events.PostgresRewardEventRepository(ds)
        val eventId = UUID.randomUUID().toString()
        events.transaction { put(com.hienthai.fastowin.server.events.RewardEventAdminView(
            eventId, 1, com.hienthai.fastowin.protocol.RewardEventState.ACTIVE,
            com.hienthai.fastowin.server.events.RewardEventDraft("Event", "Event", "Details", "Details", "UTC",
                now - 1, now + 100_000, 2, 3, setOf(com.hienthai.fastowin.protocol.MatchType.CASUAL)),
            actor, "Fixture", now, publishedAtEpochMillis = now)) }
        val gifts = com.hienthai.fastowin.server.gifts.PostgresGiftRepository(ds)
        val giftIds = List(2) { UUID.randomUUID().toString() }
        gifts.transaction {
            giftIds.forEachIndexed { index, id ->
                val request = com.hienthai.fastowin.server.gifts.GiftDraftRequest("Gift", "Gift", "Body", "Body", "Fixture", 5, 7,
                    availableAtEpochMillis = now, expiresAtEpochMillis = now + 100_000, requestId = UUID.randomUUID().toString())
                putCampaign(com.hienthai.fastowin.server.gifts.GiftCampaign(id, 1,
                    com.hienthai.fastowin.server.gifts.CampaignState.PUBLISHED, request, now, publishedAtEpochMillis = now,
                    compensation = if (index == 0) null else com.hienthai.fastowin.protocol.CompensationMailboxContext(
                        UUID.randomUUID().toString(), "OUTAGE_FIXTURE", 1, now - 1000, now, "Affected", "Affected")))
                freeze(id, listOf(userId))
                putRecipient(com.hienthai.fastowin.server.gifts.GiftRecipient(id, userId, "DELIVERED"))
            }
        }
        val maintenanceRepository = com.hienthai.fastowin.server.maintenance.PostgresMaintenanceRepository(ds)
        maintenanceRepository.transaction {
            val current = snapshot()
            putSnapshot(current.version, com.hienthai.fastowin.server.maintenance.MaintenanceSnapshot(
                version = current.version + 1, state = com.hienthai.fastowin.protocol.MaintenanceState.MAINTENANCE,
                mode = com.hienthai.fastowin.protocol.MaintenanceMode.EMERGENCY, messageVi = "Maintenance", messageEn = "Maintenance",
                startedAtEpochMillis = now, actorId = actor, reason = "Fixture"))
        }
        val maintenance = com.hienthai.fastowin.server.maintenance.MaintenanceService(maintenanceRepository, nowMillis = { now })
        val policyBefore = maintenance.refresh(0)
        val snapshotBefore = maintenanceRepository.transaction { snapshot() }
        val walletBefore = gifts.transaction { balance(userId) }
        val giftBefore = gifts.transaction { giftIds.map { campaign(it) to recipient(it, userId) } }
        val eventsBefore = events.publicRead { records() }
        for ((action, target) in listOf(com.hienthai.fastowin.protocol.AdminAnnouncementAction.REWARD_EVENT to eventId) +
            giftIds.map { com.hienthai.fastowin.protocol.AdminAnnouncementAction.GIFT_MAILBOX to it }) {
            val created = service.create(owner, AnnouncementDraftRequest("Notice", "Notice", "Copy", "Copy",
                com.hienthai.fastowin.protocol.AdminAnnouncementCategory.MAINTENANCE,
                startsAtEpochMillis = now, endsAtEpochMillis = now + 100_000, showBanner = true,
                action = action, targetId = target, requestId = UUID.randomUUID().toString(), reason = "Fixture"))
            val published = publish(created)
            assertEquals(target, published.announcement.targetId)
            service.deliverDueBatch()
            service.markRead(userId, published.announcement.id)
            service.hideBanner(userId, published.announcement.id)
            service.cancel(owner, published.announcement.id,
                AnnouncementMutationRequest(published.announcement.version, UUID.randomUUID().toString(), "Stop notice"))
            assertEquals(AdminAnnouncementState.CANCELLED, service.playerDetail(userId, published.announcement.id).announcement.state)
        }
        assertEquals(walletBefore, gifts.transaction { balance(userId) })
        assertEquals(giftBefore, gifts.transaction { giftIds.map { campaign(it) to recipient(it, userId) } })
        assertEquals(eventsBefore, events.publicRead { records() })
        assertEquals(snapshotBefore, maintenanceRepository.transaction { snapshot() })
        assertEquals(policyBefore, maintenance.refresh(0))
    } }

    @Test fun `revoked role after admin advisory wait cannot publish`() = runTest { isolated {
        val draft = draft()
        val preview = service.preview(owner, draft.announcement.id)
        val request = AnnouncementMutationRequest(1, "waited-publish", "Publish", preview.previewToken)
        val actorId = admins.session(owner.token).admin.id
        ds.connection.use { held ->
            held.autoCommit = false
            held.prepareStatement("SELECT pg_advisory_xact_lock(7421092187349)").use { it.execute() }
            val holderPid = held.prepareStatement("SELECT pg_backend_pid()").use { statement ->
                statement.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
            val pending = CompletableFuture.supplyAsync {
                runBlocking { runCatching { service.publish(owner, draft.announcement.id, request) }.exceptionOrNull() }
            }
            try {
                assertTrue(waitForAdvisoryBlock(ds, holderPid), "publisher must wait on the admin advisory lock")
                assertTrue(!pending.isDone)
                held.prepareStatement("UPDATE admin_accounts SET role='SUPPORT' WHERE id=?").use { statement ->
                    statement.setObject(1, UUID.fromString(actorId))
                    statement.executeUpdate()
                }
            } finally { held.commit() }
            val error = pending.get(10, TimeUnit.SECONDS)
            assertEquals(403, (error as AdminException).status)
        }
        repository.announcements.transaction {
            assertEquals(AnnouncementStatus.DRAFT, definition(draft.announcement.id)?.status)
            assertEquals(0L, recipientCounts(draft.announcement.id).frozen)
        }
    } }

    @Test fun `publication race freezes one audience and failed transaction rolls back facts`() = runTest { isolated {
        val draft = draft()
        val preview = service.preview(owner, draft.announcement.id)
        val firstRequest = AnnouncementMutationRequest(1, "first", "Publish", preview.previewToken)
        assertFailsWith<IllegalStateException> {
            repository.announcements.transaction(admin = true) {
                val value = definition(draft.announcement.id, lock = true)!!
                putDefinition(value.copy(version = 2, status = AnnouncementStatus.PUBLISHED,
                    publishedBy = value.createdBy, publishedAtEpochMillis = now, updatedAtEpochMillis = now))
                putRecipient(AnnouncementRecipient(value.id, userId))
                admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), value.createdBy,
                    "ANNOUNCEMENT_PUBLISH", value.id, "failed", "Fixture", null, null, now))
                error("rollback")
            }
        }
        repository.announcements.transaction {
            assertEquals(AnnouncementStatus.DRAFT, definition(draft.announcement.id)?.status)
            assertEquals(0L, recipientCounts(draft.announcement.id).frozen)
        }
        assertEquals(0L, count("SELECT count(*) FROM admin_audit WHERE action='ANNOUNCEMENT_PUBLISH'"))
        val a = CompletableFuture.supplyAsync { runBlocking { runCatching { service.publish(owner, draft.announcement.id, firstRequest) } } }
        val b = CompletableFuture.supplyAsync { runBlocking { runCatching { service.publish(owner, draft.announcement.id,
            firstRequest.copy(requestId = "second")) } } }
        val results = listOf(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { (it.exceptionOrNull() as? AdminException)?.status == 409 })
        repository.announcements.transaction { assertEquals(1L, recipientCounts(draft.announcement.id).frozen) }
        assertEquals(1L, count("SELECT count(*) FROM admin_audit WHERE action='ANNOUNCEMENT_PUBLISH'"))
    } }

    @Test fun `deleted frozen recipient becomes skipped without notification`() = runTest { isolated {
        val draft = draft()
        val published = publish(draft)
        ds.connection.use { c -> c.prepareStatement("DELETE FROM users WHERE id=?").use { statement ->
            statement.setObject(1, UUID.fromString(userId)); statement.executeUpdate()
        } }
        now += 1_000
        assertEquals(1, service.deliverDueBatch())
        repository.announcements.transaction {
            assertEquals(1L, recipientCounts(published.announcement.id).frozen)
            assertEquals(1L, recipientCounts(published.announcement.id).skipped)
            assertEquals(0L, recipientCounts(published.announcement.id).stored)
        }
    } }

    @Test fun `exact start creates one row and exact end expires while scheduled cancellation stays empty`() = runTest { isolated {
        val start = now + 1_000
        val draft = draft()
        val published = publish(draft)
        val cancelledDraft = draft()
        val cancelled = publish(cancelledDraft)
        service.cancel(owner, cancelled.announcement.id,
            AnnouncementMutationRequest(cancelled.announcement.version, "cancel-future", "Stop"))
        assertEquals(0, service.deliverDueBatch())
        now = start
        assertEquals(1, service.deliverDueBatch())
        assertEquals(0, service.deliverDueBatch())
        assertEquals("1", service.inbox(userId).total)
        assertEquals(AdminAnnouncementState.ACTIVE, service.playerDetail(userId, published.announcement.id).announcement.state)
        now = published.announcement.endsAtEpochMillis
        assertEquals(AdminAnnouncementState.EXPIRED, service.playerDetail(userId, published.announcement.id).announcement.state)
        assertTrue(service.banners(userId).items.isEmpty())
        repository.announcements.transaction {
            assertEquals(0L, recipientCounts(cancelled.announcement.id).stored)
            assertEquals(1L, recipientCounts(published.announcement.id).stored)
        }
    } }

    @Test fun `cancellation stops new postgres push claims while committed claim may settle`() = runTest { isolated {
        val second = assertIs<AuthResult.Success>(auth.register("announce-pg-${UUID.randomUUID()}@example.com",
            "password123", "Second", "android")).session.userId
        val published = publish(draft(pushEnabled = true))
        val players = listOf(userId, second)
        val profiles = PostgresPlayerProfileRepository(ds)
        players.forEachIndexed { index, id ->
            assertTrue(profiles.updateFcmToken(id, "fake-token-$index", "vi"))
            repository.announcements.transaction { putPreference(AnnouncementPreference(id, 1, adminEnabled = true)) }
        }
        now += 1_000
        assertEquals(2, service.deliverDueBatch())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fake = object : PushNotificationService {
            override val announcementConfigured = true
            override suspend fun sendNotification(fcmToken: String, language: AppLanguage, content: LocalizedPushContent,
                                                  destinationPath: String) = PushDeliveryStatus.FAILED
            override suspend fun sendAnnouncement(fcmToken: String, languageTag: String, titleVi: String, titleEn: String,
                                                  bodyVi: String, bodyEn: String, destinationPath: String): AnnouncementPushStatus {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                return AnnouncementPushStatus.ACCEPTED
            }
        }
        val delivery = AnnouncementPushDelivery(repository.announcements, fake, { now })
        val worker = CompletableFuture.supplyAsync { runBlocking { delivery.deliverDueBatch(limit = 1) } }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            repository.announcements.transaction {
                assertEquals(1, players.count { pushAttempt(published.announcement.id, it)?.outcome == AnnouncementPushOutcome.ATTEMPTING })
                assertEquals(2L, recipientCounts(published.announcement.id).stored)
            }
            service.cancel(owner, published.announcement.id,
                AnnouncementMutationRequest(published.announcement.version, "cancel-during-claim", "Stop"))
        } finally { release.countDown() }
        assertEquals(1, worker.get(10, TimeUnit.SECONDS))
        assertEquals(0, delivery.deliverDueBatch())
        repository.announcements.transaction {
            val attempts = players.mapNotNull { pushAttempt(published.announcement.id, it) }
            assertEquals(1, attempts.size)
            assertEquals(AnnouncementPushOutcome.ACCEPTED, attempts.single().outcome)
            assertEquals(2L, recipientCounts(published.announcement.id).stored)
        }
    } }
    @Test fun `restart catch-up stores expired history once without banner or push`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 3
        }).use { ds ->
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            val auth = AuthenticationService(PostgresAuthRepository(ds), PasswordHasher(iterations = 1_000),
                nowMillis = { 1_800_000_000_000L })
            val user = assertIs<AuthResult.Success>(auth.register("announce-delivery-${UUID.randomUUID()}@example.com",
                "password123", "Player", "android")).session
            val id = UUID.randomUUID().toString()
            var now = 1_800_000_000_000L
            val definition = AnnouncementRepositoryTest().fixtureDefinition(id).copy(
                startsAtEpochMillis = now + 1_000, endsAtEpochMillis = now + 2_000,
                pushEnabled = true, showBanner = true
            )
            val notifications = PostgresNotificationRepository(ds)
            try {
                notifications.announcements.transaction {
                    putDefinition(definition)
                    putRecipient(AnnouncementRecipient(id, user.userId))
                }
                val before = AnnouncementService(notifications.announcements, null, nowMillis = { now })
                assertEquals(0, before.deliverDueBatch())
                assertTrue(notifications.loadNotifications(user.userId).isEmpty())

                now += 3_000
                val restarted = AnnouncementService(PostgresNotificationRepository(ds).announcements, null, nowMillis = { now })
                assertEquals(1, restarted.deliverDueBatch())
                assertEquals(0, restarted.deliverDueBatch())
                val history = restarted.inbox(user.userId)
                assertEquals("1", history.total)
                assertEquals(AdminAnnouncementState.EXPIRED, history.items.single().announcement.state)
                assertTrue(restarted.banners(user.userId).items.isEmpty())
                notifications.announcements.transaction {
                    assertEquals(1L, recipientCounts(id).stored)
                    assertEquals(1L, this.notifications.count(user.userId, includeDismissed = true, announcementOnly = true))
                }
            } finally {
                ds.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id=?").use { statement ->
                        statement.setObject(1, UUID.fromString(user.userId)); statement.executeUpdate()
                    }
                }
                ds.connection.use { connection ->
                    connection.autoCommit = false
                    try {
                        connection.prepareStatement("DELETE FROM admin_announcement_recipients WHERE announcement_id=?").use { statement ->
                            statement.setObject(1, UUID.fromString(id)); statement.executeUpdate()
                        }
                        connection.prepareStatement("DELETE FROM admin_announcements WHERE id=?").use { statement ->
                            statement.setObject(1, UUID.fromString(id)); statement.executeUpdate()
                        }
                        connection.commit()
                    } catch (error: Throwable) { connection.rollback(); throw error }
                }
            }
        }
    }
}
