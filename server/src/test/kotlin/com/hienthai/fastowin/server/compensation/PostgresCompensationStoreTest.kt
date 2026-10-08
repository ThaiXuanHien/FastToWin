package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.events.*
import com.hienthai.fastowin.server.gifts.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.*

class PostgresCompensationStoreTest {
    private suspend fun database(block: suspend Fixture.() -> Unit) {
        val url = checkNotNull(System.getenv("TEST_DATABASE_URL")) { "Real PostgreSQL required; no skipped compensation coverage" }
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 5; connectionTimeout = 5000
        }
        // Same isolated UUID-schema cleanup policy as the accepted gift suite. No shared data changes.
        val schema = "compensation_test_${UUID.randomUUID().toString().replace("-", "")}"
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    Fixture(source).apply { initialize() }.block()
                }
            } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
    private class Fixture(val source: HikariDataSource) {
        val now = System.currentTimeMillis()
        val admin = AdminService(PostgresAdminRepository(source), AdminConfiguration("http://localhost:8080", false), PasswordHasher(1000), { now })
        val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1000))
        val repository = PostgresGiftRepository(source)
        var gifts = GiftService(repository, admin, { now })
        var incidents = CompensationIncidentService(repository, admin, { now })
        lateinit var owner: GiftAdminContext
        lateinit var operator: GiftAdminContext
        lateinit var operatorId: String
        lateinit var ownerId: String
        lateinit var player: String
        suspend fun initialize() {
            ownerId = admin.bootstrapOwner("owner@example.com", "GoodPassword123!").id
            owner = login("owner@example.com")
            operatorId = admin.createAccount(owner.token, owner.csrfToken, owner.origin, CreateAdminRequest("operator@example.com", "GoodPassword123!", AdminRole.OPERATOR, "Fixture", "operator")).id
            operator = login("operator@example.com"); player = register()
        }
        suspend fun register() = assertIs<AuthResult.Success>(auth.register("${UUID.randomUUID()}@example.com", "GoodPassword123!", "Player", "test")).session.userId
        suspend fun login(email: String): GiftAdminContext {
            val login = admin.login(email, "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
            return GiftAdminContext(login.token, login.response.csrfToken, "http://localhost:8080")
        }
        fun request() = CompensationIncidentRequest(" outage_1 ", "Sự cố", "Outage", "Thông tin an toàn", "Safe explanation",
            0, 10, "PRIVATE_CAUSE", "PRIVATE_EVIDENCE", ownerId, UUID.randomUUID().toString(), "Fixture")
        suspend fun incident() = incidents.create(owner, request())
        // Pure persisted fixture; production round creation/publication is Task2 authority.
        suspend fun campaign(incident: CompensationIncident, roundNumber: Int = 1, users: List<String> = listOf(player), state: CampaignState = CampaignState.PUBLISHED): GiftCampaign = repository.transaction(true) {
            val draft = GiftDraftRequest(incident.titleVi, incident.titleEn, incident.descriptionVi, incident.descriptionEn, "Fixture", 100, 30,
                availableAtEpochMillis = now, expiresAtEpochMillis = now + 100000, requestId = UUID.randomUUID().toString())
            val context = CompensationMailboxContext(incident.id, incident.incidentKey, roundNumber, 0, 10, "Ảnh hưởng", "Affected")
            val c = GiftCampaign(UUID.randomUUID().toString(), 1, state, draft, now, if (state == CampaignState.PUBLISHED) now else null, compensation = context)
            putCampaign(c); compensation.putRound(CompensationRound(c.id, incident.id, roundNumber, if (roundNumber > 1) "Additional" else null, "Ảnh hưởng", "Affected"))
            freeze(c.id, users); c
        }
        fun sql(sql: String) = source.connection.use { it.createStatement().use { s -> s.execute(sql) } }
        fun number(sql: String): Long = source.connection.use { it.createStatement().use { s -> s.executeQuery(sql).use { r -> r.next(); r.getLong(1) } } }
        suspend fun error(status: Int, block: suspend () -> Unit) = assertEquals(status, assertFailsWith<AdminException> { block() }.status)
        suspend fun balance() = repository.transaction { balance(player) }
    }
    @Test fun `compensation concurrent claim retry exact wallet and earned totals ignore active x2 event`() = runTest { database {
        val events = RewardEventService(PostgresRewardEventRepository(source), admin, { now })
        val actor = RewardEventAdminContext(owner.token, owner.csrfToken, owner.origin)
        val event = events.create(actor, RewardEventDraftRequest(0, "event", "Fixture", RewardEventDraft("Thưởng", "Reward", "Thưởng", "Reward", "UTC", now - 1, now + 100000, 2, 2, MatchType.entries.toSet())))
        events.schedule(actor, event.id, RewardEventMutationRequest(1, "schedule", "Fixture", events.preview(actor, event.id).previewToken))
        assertEquals(RewardEventState.ACTIVE, events.detail(actor, event.id).state)
        val c = campaign(incident()); gifts.deliverDueBatch(); val before = balance()
        val receipts = coroutineScope { List(2) { async(Dispatchers.IO) { gifts.claim(player, c.id) } }.awaitAll() }
        gifts = GiftService(PostgresGiftRepository(source), admin, { now })
        assertEquals(receipts[0], receipts[1]); assertEquals(receipts[0], gifts.claim(player, c.id))
        assertEquals(before.gold + 100, balance().gold); assertEquals(before.xp + 30, balance().xp)
        assertEquals(1, number("SELECT count(*) FROM wallet_transactions WHERE source_type='COMPENSATION' AND source_id='${c.id}'"))
        assertEquals(0, number("SELECT lifetime_earned_gold+lifetime_earned_gems FROM player_stats WHERE user_id='$player'"))
        assertEquals(1, number("SELECT count(*) FROM admin_gift_campaigns WHERE source_type='COMPENSATION'"))
    } }
    @Test fun `compensation wallet rejection rolls back balance receipt and wallet atomically`() = runTest { database {
        val c = campaign(incident()); gifts.deliverDueBatch(); val before = balance()
        sql("ALTER TABLE wallet_transactions ADD CONSTRAINT reject_compensation CHECK(source_type <> 'COMPENSATION')")
        error(503) { gifts.claim(player, c.id) }
        assertEquals(before, balance()); assertEquals(0, number("SELECT count(*) FROM wallet_transactions"))
        assertEquals(GiftState.AVAILABLE, gifts.mailbox(player, PageRequest()).items.single().state)
        sql("ALTER TABLE wallet_transactions DROP CONSTRAINT reject_compensation")
        gifts.claim(player, c.id); assertEquals(before.gold + 100, balance().gold)
    } }
    @Test fun `incident audit rejection rolls back incident and replay then retry commits once`() = runTest { database {
        val r = request(); sql("ALTER TABLE admin_audit ADD CONSTRAINT reject_incident CHECK(action <> 'COMPENSATION_INCIDENT_CREATE')")
        error(503) { incidents.create(owner, r) }
        assertEquals(0, number("SELECT count(*) FROM compensation_incidents")); assertEquals(0, number("SELECT count(*) FROM compensation_replays"))
        sql("ALTER TABLE admin_audit DROP CONSTRAINT reject_incident")
        val c = incidents.create(owner, r); incidents = CompensationIncidentService(PostgresGiftRepository(source), admin, { now })
        assertEquals(c, incidents.create(owner, r)); assertEquals(1, number("SELECT count(*) FROM admin_audit WHERE action='COMPENSATION_INCIDENT_CREATE'"))
        error(409) { incidents.create(owner, r.copy(incidentKey = "OUTAGE_1", requestId = "duplicate")) }
        error(409) { incidents.create(owner, r.copy(titleEn = "Changed")) }
    } }
    @Test fun `current operator revocation while waiting admin lock cannot replay`() = runTest { database {
        val r = request(); incidents.create(operator, r)
        source.connection.use { held ->
            held.autoCommit = false
            held.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
            coroutineScope {
                val waiting = async(Dispatchers.IO) { runCatching { incidents.create(operator, r) } }
                withContext(Dispatchers.IO) { withTimeout(10000) {
                    while (number("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%pg_advisory_xact_lock(7421092187349)%'") == 0L) delay(10)
                } }
                assertFalse(waiting.isCompleted)
                held.createStatement().use { it.execute("UPDATE admin_accounts SET role='SUPPORT' WHERE id='$operatorId'") }
                held.commit()
                assertEquals(403, assertIs<AdminException>(waiting.await().exceptionOrNull()).status)
            }
        }
        assertEquals(1, number("SELECT count(*) FROM compensation_incidents")); assertEquals(1, number("SELECT count(*) FROM compensation_replays"))
        assertEquals(1, number("SELECT count(*) FROM admin_audit WHERE action='COMPENSATION_INCIDENT_CREATE'"))
    } }
    @Test fun `unique round identity one draft max published freeze and immutable incident key`() = runTest { database {
        val i = incident(); val c = campaign(i, state = CampaignState.DRAFT)
        repository.transaction { assertEquals(1, compensation.maximumRoundNumber(i.id)); assertTrue(compensation.hasDraftRound(i.id)); assertFalse(compensation.hasPublishedRound(i.id)) }
        error(409) { campaign(i, 2, state = CampaignState.DRAFT) }
        error(409) { campaign(i, 1) }
        error(409) { incidents.update(owner, i.id, request().copy(incidentKey = "OTHER", expectedVersion = 1)) }
        repository.transaction(true) { putCampaign(c.copy(state = CampaignState.PUBLISHED, publishedAtEpochMillis = now)) }
        repository.transaction { assertFalse(compensation.hasDraftRound(i.id)); assertTrue(compensation.hasPublishedRound(i.id)); assertEquals(c.id, compensation.rounds(i.id, PageRequest()).items.single().campaignId) }
        error(409) { incidents.update(owner, i.id, request().copy(expectedVersion = 1)) }
        repository.transaction(true) { putCampaign(c.copy(state = CampaignState.CANCELLED, publishedAtEpochMillis = now)) }
        error(409) { incidents.update(owner, i.id, request().copy(expectedVersion = 1)) }
        assertEquals(1, number("SELECT count(*) FROM compensation_rounds"))
    } }
    @Test fun `one receipt query distinguishes users entitlements and selected overlap after deletion`() = runTest { database {
        val other = register(); val i = incident(); val a = campaign(i, 1, listOf(player, other)); val b = campaign(i, 2)
        gifts.deliverDueBatch(); gifts.claim(player, a.id); gifts.claim(other, a.id); gifts.claim(player, b.id)
        sql("DELETE FROM users WHERE id='$other'")
        repository.transaction { assertEquals(CompensationPreviousClaims("2", "3", "1"), compensation.previousClaims(i.id, 3, listOf(player))) }
    } }
    @Test fun `compensation delivery checkpoint reload deleted skips and private projection`() = runTest { database {
        val deleted = register(); val i = incident(); val c = campaign(i, users = listOf(player, deleted))
        sql("DELETE FROM users WHERE id='$deleted'")
        assertEquals(1, gifts.deliverDueBatch(1)); gifts = GiftService(PostgresGiftRepository(source), admin, { now })
        assertEquals(1, gifts.deliverDueBatch(1)); assertEquals(0, gifts.deliverDueBatch())
        repository.transaction { val counts = counters(c, now); assertEquals(1, counts.delivered); assertEquals(1, counts.skipped); assertEquals(0, counts.pending) }
        val json = ProtocolJson.encodeToString(GiftMailboxPage.serializer(), gifts.mailbox(player, PageRequest()))
        for (secret in listOf("PRIVATE_CAUSE", "PRIVATE_EVIDENCE", ownerId, "causeCode", "responsibleAdminId", "evidence")) assertFalse(json.contains(secret))
        assertEquals(c.compensation, gifts.mailbox(player, PageRequest()).items.single().compensation)
        assertEquals(0, gifts.list(owner, PageRequest()).total); error(404) { gifts.detail(owner, c.id) }
        error(404) { gifts.update(owner, c.id, c.draft.copy(expectedVersion = 1, requestId = "edit")) }
        error(404) { gifts.preview(owner, c.id, GiftPreviewRequest(1)) }
        error(404) { gifts.publish(owner, c.id, GiftPublishRequest(1, "forged", "pub", "Fixture")) }
        error(404) { gifts.cancel(owner, c.id, GiftCancelRequest(1, "cancel", "Fixture")) }
    } }
    @Test fun `legacy persisted payload without context keeps admin gift source and earned totals`() = runTest { database {
        val c = gifts.create(owner, GiftDraftRequest("Quà", "Gift", "Cảm ơn", "Thanks", "Fixture", 100, 30, availableAtEpochMillis = now, requestId = "legacy")).campaign
        val p = gifts.preview(owner, c.id, GiftPreviewRequest(1)); gifts.publish(owner, c.id, GiftPublishRequest(1, p.previewToken, "publish", "Fixture"))
        // Default-null metadata is omitted by Json encoding, exactly matching legacy storage.
        assertEquals(0, number("SELECT count(*) FROM admin_gift_campaigns WHERE payload::jsonb ? 'compensation'"))
        gifts.deliverDueBatch(); gifts.claim(player, c.id)
        assertEquals(1, number("SELECT count(*) FROM wallet_transactions WHERE source_type='ADMIN_GIFT'"))
        assertEquals(0, number("SELECT lifetime_earned_gold+lifetime_earned_gems FROM player_stats WHERE user_id='$player'"))
        assertNull(gifts.mailbox(player, PageRequest()).items.single().compensation)
        val incident = incident()
        error(409) { repository.transaction(true) { compensation.putRound(CompensationRound(c.id, incident.id, 1, null, "Affected", "Affected")) } }
        val compensated = campaign(incident)
        error(409) { repository.transaction(true) { compensation.putRound(CompensationRound(compensated.id, incident.id, 2, "Extra", "Affected", "Affected")) } }
    } }
}
