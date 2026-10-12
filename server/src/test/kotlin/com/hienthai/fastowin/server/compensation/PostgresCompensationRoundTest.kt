package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import com.hienthai.fastowin.server.events.*
import com.hienthai.fastowin.server.gifts.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.*

class PostgresCompensationRoundTest {
    private suspend fun database(block: suspend Fixture.() -> Unit) {
        val url = checkNotNull(System.getenv("TEST_DATABASE_URL")) { "Real PostgreSQL required; no skipped compensation coverage" }
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 5; connectionTimeout = 5000
        }
        // Same isolated UUID-schema cleanup policy as the accepted gift suite. No shared data changes.
        val schema = "compensation_round_test_${UUID.randomUUID().toString().replace("-", "")}"
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
        var rounds = CompensationRoundService(repository, admin, { now })
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
        fun roundRequest(number: Int = 1, audience: CompensationAudience = CompensationAudience.ALL_REGISTERED, users: List<String> = emptyList()) =
            CompensationRoundRequest(1, "Ảnh hưởng", "Affected", 100, 30, now, UUID.randomUUID().toString(), "Fixture", audience, users,
                expectedNextRoundNumber = number, supplementalReason = if (number > 1) "Explicit additional compensation" else null)
        suspend fun draft(i: CompensationIncident, number: Int = 1, audience: CompensationAudience = CompensationAudience.ALL_REGISTERED, users: List<String> = emptyList()) =
            rounds.create(owner, i.id, roundRequest(number, audience, users))
        suspend fun preview(i: CompensationIncident, c: CompensationRoundDetail) = rounds.preview(owner, i.id, c.campaign.id,
            CompensationRoundPreviewRequest(i.version, c.campaign.version, UUID.randomUUID().toString(), "Review recipients"))
        fun publishRequest(i: CompensationIncident, c: CompensationRoundDetail, p: CompensationPreview) =
            CompensationRoundPublishRequest(i.version, c.campaign.version, p.previewToken, UUID.randomUUID().toString(), "Approve compensation")
        suspend fun publish(i: CompensationIncident, c: CompensationRoundDetail): CompensationRoundDetail =
            rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, preview(i, c)))
        suspend fun cancel(i: CompensationIncident, c: CompensationRoundDetail) = rounds.cancel(owner, i.id, c.campaign.id,
            CompensationRoundCancelRequest(i.version, c.campaign.version, UUID.randomUUID().toString(), "Stop unclaimed compensation"))
        fun activityWindow(from: Long, until: Long) = sql("INSERT INTO activity_coverage_windows VALUES ('${UUID.randomUUID()}',to_timestamp($from/1000.0),to_timestamp($until/1000.0))")
        fun activity(user: String, from: Long, until: Long) = sql("INSERT INTO activity_connection_observations SELECT '${UUID.randomUUID()}','${UUID.randomUUID()}',subject_key,to_timestamp($from/1000.0),to_timestamp($until/1000.0) FROM analytics_subject_links WHERE user_id='$user'")
        fun sql(sql: String) = source.connection.use { it.createStatement().use { s -> s.execute(sql) } }
        fun number(sql: String): Long = source.connection.use { it.createStatement().use { s -> s.executeQuery(sql).use { r -> r.next(); r.getLong(1) } } }
        suspend fun error(status: Int, block: suspend () -> Unit) = assertEquals(status, assertFailsWith<AdminException> { block() }.status)
        suspend fun balance() = repository.transaction { balance(player) }
    }

    @Test fun `activity publication selects observed accounts freezes and replays after source loss`() = runTest { database {
        val inactive = register(); val deleted = register()
        val i = incidents.create(owner, request().copy(affectedFromEpochMillis = now-30_000, affectedUntilEpochMillis = now-1000))
        activityWindow(now-30_000, now)
        activity(player, now-29_000, now-2000); activity(deleted, now-29_000, now-2000)
        assertTrue(PostgresAuthRepository(source).deleteAccount(UUID.fromString(deleted)))
        val c = draft(i, audience = CompensationAudience.ACTIVITY)
        assertTrue(Json.encodeToString(c.round).contains("\"audience\":\"ACTIVITY\""))
        val p = preview(i, c); assertEquals(CompensationAudience.ACTIVITY, p.source)
        assertEquals("1", p.recipientCount); assertEquals(listOf(player), p.sampleUserIds)
        val publication = publishRequest(i, c, p); val published = rounds.publish(owner, i.id, c.campaign.id, publication)
        assertEquals("1", published.report.eligible)
        val late = register(); activity(late, now-29_000, now-2000)
        sql("DELETE FROM activity_coverage_windows")
        assertEquals(published, rounds.publish(owner, i.id, c.campaign.id, publication))
        gifts.deliverDueBatch(); assertTrue(gifts.mailbox(inactive, PageRequest()).items.isEmpty())
        assertTrue(gifts.mailbox(late, PageRequest()).items.isEmpty())
        val claimed = gifts.claim(player, c.campaign.id); assertEquals(claimed, gifts.claim(player, c.campaign.id))
        assertEquals(1, number("SELECT count(*) FROM wallet_transactions WHERE source_type='COMPENSATION'"))
        admin.logout(owner.token, owner.csrfToken, owner.origin)
        error(401) { rounds.publish(owner, i.id, c.campaign.id, publication) }
    } }
    @Test fun `activity audience changes deletion source and incident range invalidate preview`() = runTest { database {
        val i = incidents.create(owner, request().copy(affectedFromEpochMillis = now-30_000, affectedUntilEpochMillis = now-1000))
        activityWindow(now-30_000, now); activity(player, now-29_000, now-2000)
        val c = draft(i, audience = CompensationAudience.ACTIVITY); val p = preview(i, c)
        val extra = register(); activity(extra, now-29_000, now-2000)
        error(409) { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, p)) }
        val withExtra = preview(i, c); assertEquals("2", withExtra.recipientCount)
        assertTrue(PostgresAuthRepository(source).deleteAccount(UUID.fromString(extra)))
        error(409) { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, withExtra)) }
        val beforeGap = preview(i, c)
        sql("DELETE FROM activity_coverage_windows")
        assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", assertFailsWith<AdminException> { preview(i, c) }.code)
        assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", assertFailsWith<AdminException> { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, beforeGap)) }.code)
        activityWindow(now-20_000, now)
        assertEquals("ACTIVITY_RANGE_INCOMPLETE", assertFailsWith<AdminException> { preview(i, c) }.code)
        sql("DELETE FROM activity_coverage_windows"); activityWindow(now-30_000, now)
        val changed = incidents.update(owner, i.id, request().copy(affectedFromEpochMillis = now-31_000, affectedUntilEpochMillis = now-1000, expectedVersion = 1))
        error(409) { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, beforeGap)) }
        assertEquals("ACTIVITY_RANGE_INCOMPLETE", assertFailsWith<AdminException> { preview(changed, c) }.code)
        assertEquals(0, number("SELECT count(*) FROM admin_gift_recipients"))
    } }
    @Test fun `activity capability reflects freshness and creation requires complete incident retention`() = runTest { database {
        fun CompensationCapabilities.activity() = sources.single { it.source == CompensationAudience.ACTIVITY }
        assertFalse(incidents.capabilities(owner).activity().available)
        val i = incidents.create(owner, request().copy(affectedFromEpochMillis = now-30_000, affectedUntilEpochMillis = now-1000))
        assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", assertFailsWith<AdminException> { draft(i, audience = CompensationAudience.ACTIVITY) }.code)
        activityWindow(now-20_000, now)
        assertTrue(incidents.capabilities(owner).activity().available)
        sql("DELETE FROM activity_coverage_windows"); activityWindow(now-60_000, now-30_000)
        val lagged = incidents.capabilities(owner).activity()
        assertTrue(lagged.available); assertEquals(now-30_000, lagged.coverage?.freshness); assertEquals(90, lagged.retentionDays)
        sql("DELETE FROM activity_coverage_windows"); activityWindow(now-20_000, now)
        assertEquals("ACTIVITY_RANGE_INCOMPLETE", assertFailsWith<AdminException> { draft(i, audience = CompensationAudience.ACTIVITY) }.code)
        sql("DELETE FROM activity_coverage_windows"); activityWindow(now-120_000, now-61_000)
        assertFalse(incidents.capabilities(owner).activity().available)
        sql("DELETE FROM activity_coverage_windows"); activityWindow(0, 10)
        assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", assertFailsWith<AdminException> { draft(incidents.create(owner, request().copy(incidentKey = "expired")), audience = CompensationAudience.ACTIVITY) }.code)
    } }
    @Test fun `legacy round missing source infers saved explicit audience`() = runTest { database {
        val i = incident(); val c = draft(i, audience = CompensationAudience.USER_IDS, users = listOf(player))
        sql("UPDATE compensation_rounds SET payload=(payload::jsonb - 'audience')::text")
        val restored = rounds.detail(owner, i.id, c.campaign.id)
        assertTrue(Json.encodeToString(restored.round).contains("\"audience\":\"USER_IDS\""))
        assertEquals(CompensationAudience.USER_IDS, preview(i, restored).source)
        assertEquals("1", publish(i, restored).report.eligible)
    } }

    @Test fun `updating source binds preview and preserves saved source in detail`() = runTest { database {
        val inactive = register()
        val i = incidents.create(owner, request().copy(affectedFromEpochMillis = now-30_000, affectedUntilEpochMillis = now-1000))
        activityWindow(now-30_000, now); activity(player, now-29_000, now-2000)
        val c = draft(i); val all = preview(i, c); assertEquals("2", all.recipientCount)
        val update = roundRequest(audience = CompensationAudience.ACTIVITY).copy(expectedNextRoundNumber = null, expectedVersion = 1)
        val activity = rounds.update(owner, i.id, c.campaign.id, update)
        assertEquals(CompensationAudience.ACTIVITY, activity.round.audience)
        error(409) { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, all)) }
        val selected = preview(i, activity); assertEquals("1", selected.recipientCount)
        val reverted = rounds.update(owner, i.id, c.campaign.id, update.copy(audience = CompensationAudience.USER_IDS, userIds = listOf(inactive), expectedVersion = 2, requestId = "source-userids"))
        assertEquals(CompensationAudience.USER_IDS, reverted.round.audience)
        error(409) { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, activity, selected)) }
        assertEquals(listOf(inactive), preview(i, reverted).sampleUserIds)
        assertEquals(CompensationAudience.USER_IDS, rounds.detail(owner, i.id, c.campaign.id).round.audience)
    } }
    @Test fun `activity healthy empty interval freezes zero and expired preview cannot publish`() = runTest { database {
        val cutoff = now - 90L*86_400_000
        val i = incidents.create(owner, request().copy(affectedFromEpochMillis = cutoff+1000, affectedUntilEpochMillis = cutoff+2000))
        activityWindow(cutoff+1000, cutoff+2000)
        val c = draft(i, audience = CompensationAudience.ACTIVITY); val p = preview(i, c)
        assertEquals("0", p.recipientCount); assertTrue(p.sampleUserIds.isEmpty())
        rounds = CompensationRoundService(repository, admin, { now+3000 })
        assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", assertFailsWith<AdminException> { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, p)) }.code)
        assertEquals(0, number("SELECT count(*) FROM admin_gift_recipients"))
        rounds = CompensationRoundService(repository, admin, { now })
        assertEquals("0", rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, p)).report.eligible)
    } }

    @Test fun `activity HTTP capabilities preview publication headers roles and source errors`() = runTest { database {
        val i = incidents.create(owner, request().copy(affectedFromEpochMillis = now-30_000, affectedUntilEpochMillis = now-1000))
        activityWindow(now-30_000, now-1000); activity(player, now-29_000, now-2000)
        val c = draft(i, audience = CompensationAudience.ACTIVITY)
        val fixture = this
        testApplication {
            application { gameModule(adminService = fixture.admin, giftService = fixture.gifts,
                compensationIncidentService = fixture.incidents, compensationRoundService = fixture.rounds) }
            fun HttpRequestBuilder.context(ctx: GiftAdminContext = owner) {
                header(HttpHeaders.Origin, ctx.origin!!); header(HttpHeaders.Cookie, "$ADMIN_COOKIE=${ctx.token}")
                header(ADMIN_CSRF_HEADER, ctx.csrfToken!!); contentType(ContentType.Application.Json)
            }
            val capability = client.get("/admin/api/incidents/capabilities") { context() }
            assertEquals(HttpStatusCode.OK, capability.status)
            val source = Json.decodeFromString<CompensationCapabilities>(capability.bodyAsText()).sources.single { it.source == CompensationAudience.ACTIVITY }
            assertTrue(source.available); assertEquals(now-1000, source.coverage?.freshness)
            val path = "/admin/api/incidents/${i.id}/rounds/${c.campaign.id}"
            val preview = client.post("$path/preview") { context(operator); setBody(Json.encodeToString(CompensationRoundPreviewRequest(1, 1, "http-preview", "Review"))) }
            assertEquals(HttpStatusCode.OK, preview.status)
            assertEquals("no-store", preview.headers[HttpHeaders.CacheControl]); assertEquals("nosniff", preview.headers["X-Content-Type-Options"])
            val p = Json.decodeFromString<CompensationPreview>(preview.bodyAsText())
            assertEquals(CompensationAudience.ACTIVITY, p.source); assertEquals("1", p.recipientCount)
            val publication = publishRequest(i, c, p)
            assertEquals(HttpStatusCode.Forbidden, client.post("$path/publish") { context(operator); setBody(Json.encodeToString(publication)) }.status)
            sql("DELETE FROM activity_coverage_windows"); activityWindow(now-20_000, now-1000)
            val gap = client.post("$path/preview") { context(); setBody(Json.encodeToString(CompensationRoundPreviewRequest(1, 1, "http-gap", "Review"))) }
            assertEquals(HttpStatusCode.BadRequest, gap.status)
            assertEquals("ACTIVITY_RANGE_INCOMPLETE", Json.decodeFromString<AdminError>(gap.bodyAsText()).code)
            assertEquals("no-store", gap.headers[HttpHeaders.CacheControl])
            sql("DELETE FROM activity_coverage_windows"); activityWindow(now-30_000, now-1000)
            val published = client.post("$path/publish") { context(); setBody(Json.encodeToString(publication)) }
            assertEquals(HttpStatusCode.OK, published.status)
            assertEquals(CompensationAudience.ACTIVITY, Json.decodeFromString<CompensationRoundDetail>(published.bodyAsText()).round.audience)
        }
    } }

    @Test fun `multiple admins same next number create exactly one mapping`() = runTest { database {
        val i = incident(); val r = roundRequest()
        val results = coroutineScope { listOf(owner, operator).mapIndexed { n, ctx -> async(Dispatchers.IO) {
            runCatching { rounds.create(ctx, i.id, r.copy(requestId = "concurrent$n")) }
        } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess }); assertEquals(409, assertIs<AdminException>(results.single { it.isFailure }.exceptionOrNull()).status)
        assertEquals(1, number("SELECT count(*) FROM compensation_rounds"))
        val c = results.first { it.isSuccess }.getOrThrow(); error(409) { draft(i) }
        error(409) { draft(i, 2) }; cancel(i, c)
        error(400) { rounds.create(owner, i.id, roundRequest(2).copy(supplementalReason = null)) }
        error(409) { draft(i) }; val second = draft(i, 2)
        assertEquals(2, second.round.roundNumber); assertEquals(2, number("SELECT count(*) FROM compensation_rounds"))
    } }
    @Test fun `exact publish retry preserves original receipt after claims cancellation and restart`() = runTest { database {
        val i = incident(); val c = draft(i); val p = publishRequest(i, c, preview(i, c)); val published = rounds.publish(owner, i.id, c.campaign.id, p)
        gifts.deliverDueBatch(); val before = balance(); val receipt = gifts.claim(player, c.campaign.id)
        rounds = CompensationRoundService(PostgresGiftRepository(source), admin, { now }); gifts = GiftService(PostgresGiftRepository(source), admin, { now })
        assertEquals(receipt, gifts.claim(player, c.campaign.id)); val cancelled = cancel(i, published)
        assertEquals(published, rounds.publish(owner, i.id, c.campaign.id, p)); assertEquals("1", cancelled.report.claimed)
        assertEquals(1, number("SELECT count(*) FROM compensation_rounds")); assertEquals(1, number("SELECT count(*) FROM wallet_transactions WHERE source_type='COMPENSATION' AND source_id='${c.campaign.id}'"))
        assertEquals(before.gold + 100, balance().gold); assertEquals(before.xp + 30, balance().xp)
        error(409) { rounds.publish(owner, i.id, c.campaign.id, p.copy(reason = "Changed")) }
        admin.logout(owner.token, owner.csrfToken, owner.origin); error(401) { rounds.publish(owner, i.id, c.campaign.id, p) }
    } }
    @Test fun `publication freezes registered accounts and verified explicit ids`() = runTest { database {
        val i = incident(); val c = draft(i, audience = CompensationAudience.USER_IDS, users = listOf(player.uppercase(), player))
        val p = preview(i, c); assertEquals("1", p.recipientCount); assertEquals("1", p.duplicateCount); assertEquals(listOf(player), p.duplicateUserIds)
        val published = publish(i, c); val late = register(); gifts.deliverDueBatch()
        assertEquals("1", published.report.eligible); assertTrue(gifts.mailbox(late, PageRequest()).items.isEmpty()); error(404) { gifts.claim(late, c.campaign.id) }
        gifts.claim(player, c.campaign.id)
        val missingId = UUID.randomUUID().toString(); val invalid = draft(i, 2, CompensationAudience.USER_IDS, listOf(missingId))
        assertEquals(listOf(missingId), preview(i, invalid).invalidUserIds); error(400) { publish(i, invalid) }
        assertEquals(0, number("SELECT count(*) FROM admin_gift_recipients WHERE campaign_id='${invalid.campaign.id}'"))
    } }
    @Test fun `new registration and new prior claim each invalidate coherent preview`() = runTest { database {
        val i = incident(); val c = draft(i); val p = preview(i, c); register()
        error(409) { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, p)) }
        publish(i, c); gifts.deliverDueBatch()
        val second = draft(i, 2); val prior = preview(i, second); assertEquals("0", prior.previousClaimedUserCount)
        gifts.claim(player, c.campaign.id)
        error(409) { rounds.publish(owner, i.id, second.campaign.id, publishRequest(i, second, prior)) }
        val fresh = preview(i, second); assertEquals("1", fresh.previousClaimedUserCount); assertEquals("1", fresh.previousClaimedEntitlementCount); assertEquals("1", fresh.selectedPreviouslyClaimedUserCount)
        rounds.publish(owner, i.id, second.campaign.id, publishRequest(i, second, fresh))
    } }
    @Test fun `create publish audit and recipient freeze failures rollback all writes`() = runTest { database {
        val i = incident(); val r = roundRequest()
        sql("ALTER TABLE admin_audit ADD CONSTRAINT reject_create CHECK(action <> 'COMPENSATION_ROUND_CREATE')")
        error(503) { rounds.create(owner, i.id, r) }; assertEquals(0, number("SELECT count(*) FROM compensation_rounds")); assertEquals(0, number("SELECT count(*) FROM admin_gift_campaigns"))
        assertEquals(0, number("SELECT count(*) FROM compensation_replays WHERE request_id='${r.requestId}'"))
        sql("ALTER TABLE admin_audit DROP CONSTRAINT reject_create")
        val c = rounds.create(owner, i.id, r); val p = publishRequest(i, c, preview(i, c))
        sql("ALTER TABLE admin_gift_recipients ADD CONSTRAINT reject_freeze CHECK(user_id <> '$player'::uuid)")
        error(503) { rounds.publish(owner, i.id, c.campaign.id, p) }
        assertEquals(CampaignState.DRAFT, rounds.detail(owner, i.id, c.campaign.id).campaign.state); assertEquals(0, number("SELECT count(*) FROM admin_gift_recipients"))
        sql("ALTER TABLE admin_gift_recipients DROP CONSTRAINT reject_freeze")
        sql("ALTER TABLE admin_audit ADD CONSTRAINT reject_publish CHECK(action <> 'COMPENSATION_ROUND_PUBLISH')")
        error(503) { rounds.publish(owner, i.id, c.campaign.id, p) }
        assertEquals(0, number("SELECT count(*) FROM admin_gift_recipients")); assertEquals(0, number("SELECT count(*) FROM compensation_replays WHERE request_id='${p.requestId}'"))
        assertNull(rounds.detail(owner, i.id, c.campaign.id).round.publishedByAdminId)
        sql("ALTER TABLE admin_audit DROP CONSTRAINT reject_publish")
        val published = rounds.publish(owner, i.id, c.campaign.id, p); assertEquals(ownerId, published.round.publishedByAdminId)
        assertEquals(published, rounds.publish(owner, i.id, c.campaign.id, p)); assertEquals(1, number("SELECT count(*) FROM admin_audit WHERE action='COMPENSATION_ROUND_PUBLISH'"))
    } }
    @Test fun `claim cancellation race preserves assets and terminates unclaimed entitlements`() = runTest { database {
        val i = incident(); val published = publish(i, draft(i)); gifts.deliverDueBatch(); val before = balance()
        val result = coroutineScope {
            val claim = async(Dispatchers.IO) { runCatching { gifts.claim(player, published.campaign.id) } }
            val cancellation = async(Dispatchers.IO) { cancel(i, published) }
            claim.await() to cancellation.await()
        }
        val report = rounds.detail(owner, i.id, published.campaign.id).report
        val paid = if (result.first.isSuccess) 1 else 0
        if (paid == 0) assertEquals(409, assertIs<AdminException>(result.first.exceptionOrNull()).status)
        assertEquals(paid.toString(), report.claimed); assertEquals((1-paid).toString(), report.cancelled)
        assertEquals(paid.toLong(), number("SELECT count(*) FROM wallet_transactions WHERE source_type='COMPENSATION'"))
        assertEquals(before.gold + paid*100, balance().gold); assertEquals(before.xp + paid*30, balance().xp)
        if (paid == 1) gifts.claim(player, published.campaign.id) else error(409) { gifts.claim(player, published.campaign.id) }
    } }
    @Test fun `restart batches deleted skips and receipt reports survive paid account deletion`() = runTest { database {
        val deleted = register(); val i = incident(); val c = publish(i, draft(i))
        sql("DELETE FROM users WHERE id='$deleted'")
        assertEquals(1, gifts.deliverDueBatch(1)); gifts = GiftService(PostgresGiftRepository(source), admin, { now }); rounds = CompensationRoundService(PostgresGiftRepository(source), admin, { now })
        assertEquals(1, gifts.deliverDueBatch(1)); assertEquals(0, gifts.deliverDueBatch())
        val report = rounds.detail(owner, i.id, c.campaign.id).report
        assertEquals("2", report.eligible); assertEquals("1", report.delivered); assertEquals("1", report.skipped); assertEquals("0", report.pending)
        gifts.claim(player, c.campaign.id); sql("DELETE FROM users WHERE id='$player'")
        val paid = rounds.detail(owner, i.id, c.campaign.id).report
        assertEquals("1", paid.claimed); assertEquals("100", paid.claimedGold); assertEquals("30", paid.claimedXp)
        val next = draft(i, 2); val previous = preview(i, next)
        assertEquals("1", previous.previousClaimedUserCount); assertEquals("1", previous.previousClaimedEntitlementCount); assertEquals("0", previous.selectedPreviouslyClaimedUserCount)
    } }
    @Test fun `supplemental intentional repeat payments use new source ids and ignore x2 earned totals`() = runTest { database {
        val events = RewardEventService(PostgresRewardEventRepository(source), admin, { now }); val actor = RewardEventAdminContext(owner.token, owner.csrfToken, owner.origin)
        val event = events.create(actor, RewardEventDraftRequest(0, "event", "Fixture", RewardEventDraft("Thưởng", "Reward", "Thưởng", "Reward", "UTC", now-1, now+100000, 2, 2, MatchType.entries.toSet())))
        events.schedule(actor, event.id, RewardEventMutationRequest(1, "schedule", "Fixture", events.preview(actor, event.id).previewToken)); assertEquals(RewardEventState.ACTIVE, events.detail(actor, event.id).state)
        val i = incident(); val before = balance(); val first = publish(i, draft(i)); gifts.deliverDueBatch(); gifts.claim(player, first.campaign.id)
        val second = draft(i, 2); val p = preview(i, second)
        assertEquals("1", p.previousClaimedUserCount); assertEquals("1", p.previousClaimedEntitlementCount); assertEquals("1", p.selectedPreviouslyClaimedUserCount)
        val published = publish(i, second); gifts.deliverDueBatch()
        coroutineScope { List(2) { async(Dispatchers.IO) { gifts.claim(player, published.campaign.id) } }.awaitAll() }
        assertEquals(before.gold+200, balance().gold); assertEquals(before.xp+60, balance().xp)
        assertEquals(2, number("SELECT count(*) FROM wallet_transactions WHERE source_type='COMPENSATION'"))
        assertEquals(1, number("SELECT count(*) FROM wallet_transactions WHERE source_type='COMPENSATION' AND source_id='${first.campaign.id}'"))
        assertEquals(1, number("SELECT count(*) FROM wallet_transactions WHERE source_type='COMPENSATION' AND source_id='${published.campaign.id}'"))
        assertEquals(0, number("SELECT lifetime_earned_gold+lifetime_earned_gems FROM player_stats WHERE user_id='$player'"))
        val third = preview(i, draft(i, 3)); assertEquals("1", third.previousClaimedUserCount); assertEquals("2", third.previousClaimedEntitlementCount); assertEquals("1", third.selectedPreviouslyClaimedUserCount)
    } }
    @Test fun `current owner authorization after advisory lock rejects publication replay`() = runTest { database {
        val i = incident(); val c = draft(i); val p = publishRequest(i, c, preview(i, c)); rounds.publish(owner, i.id, c.campaign.id, p)
        source.connection.use { held ->
            held.autoCommit = false; held.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
            coroutineScope {
                val waiting = async(Dispatchers.IO) { runCatching { rounds.publish(owner, i.id, c.campaign.id, p) } }
                withContext(Dispatchers.IO) { withTimeout(10000) {
                    while (number("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%pg_advisory_xact_lock(7421092187349)%'") == 0L) delay(10)
                } }
                assertFalse(waiting.isCompleted)
                held.createStatement().use { it.execute("UPDATE admin_accounts SET role='OPERATOR' WHERE id='$ownerId'") }; held.commit()
                assertEquals(403, assertIs<AdminException>(waiting.await().exceptionOrNull()).status)
            }
        }
        assertEquals(1, number("SELECT count(*) FROM admin_audit WHERE action='COMPENSATION_ROUND_PUBLISH'"))
    } }
    @Test fun `incident revision invalidates preview then exact player content freezes forever`() = runTest { database {
        val i = incident(); val c = draft(i); val p = preview(i, c)
        val changed = incidents.update(owner, i.id, request().copy(titleEn = "Updated title", descriptionEn = "Updated safe copy", expectedVersion = 1))
        error(409) { rounds.publish(owner, i.id, c.campaign.id, publishRequest(i, c, p)) }
        val published = publish(changed, c); gifts.deliverDueBatch()
        val mailbox = gifts.mailbox(player, PageRequest()).items.single()
        assertEquals("Updated title", mailbox.titleEn); assertTrue(mailbox.bodyEn.contains("Updated safe copy"))
        assertTrue(mailbox.bodyEn.contains("[1970-01-01T00:00:00Z, 1970-01-01T00:00:00.010Z)"))
        for (private in listOf("PRIVATE_CAUSE", "PRIVATE_EVIDENCE", ownerId)) assertFalse(mailbox.bodyEn.contains(private))
        cancel(changed, published); error(409) { incidents.update(owner, i.id, request().copy(expectedVersion = 2)) }
        assertEquals(published.campaign.draft, rounds.detail(owner, i.id, c.campaign.id).campaign.draft)
    } }
}
