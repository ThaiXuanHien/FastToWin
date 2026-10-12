package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresGiftRepositoryTest {
    private suspend fun database(block: suspend Fixture.() -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL absent: PostgreSQL coverage skipped")
        val schema = "gift_test_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 5
        }
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
        var now = System.currentTimeMillis()
        val admin = AdminService(PostgresAdminRepository(source), AdminConfiguration("http://localhost:8080", false), PasswordHasher(1000), { now })
        val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1000))
        val repository = PostgresGiftRepository(source)
        var gifts = GiftService(repository, admin, { now })
        lateinit var owner: GiftAdminContext
        lateinit var operator: GiftAdminContext
        lateinit var support: GiftAdminContext
        lateinit var player: String
        suspend fun initialize() {
            admin.bootstrapOwner("owner@example.com", "GoodPassword123!")
            owner = login("owner@example.com")
            for (role in listOf(AdminRole.OPERATOR, AdminRole.SUPPORT)) {
                admin.createAccount(owner.token, owner.csrfToken, owner.origin, CreateAdminRequest("${role.name.lowercase()}@example.com", "GoodPassword123!", role, "Fixture", role.name))
            }
            operator = login("operator@example.com"); support = login("support@example.com")
            player = register()
        }
        suspend fun register() = assertIs<AuthResult.Success>(auth.register("${UUID.randomUUID()}@example.com", "GoodPassword123!", "Player", "test")).session.userId
        suspend fun login(email: String): GiftAdminContext {
            val value = admin.login(email, "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
            return GiftAdminContext(value.token, value.response.csrfToken, "http://localhost:8080")
        }
        fun request() = GiftDraftRequest("Quà", "Gift", "Cảm ơn", "Thank you", "Fixture", 100, 30, availableAtEpochMillis = now, requestId = UUID.randomUUID().toString())
        suspend fun draft(request: GiftDraftRequest = request()) = gifts.create(owner, request).campaign
        suspend fun publish(input: GiftCampaign? = null): GiftCampaign {
            val campaign = input ?: draft()
            val preview = gifts.preview(owner, campaign.id, GiftPreviewRequest(campaign.version))
            return gifts.publish(owner, campaign.id, GiftPublishRequest(campaign.version, preview.previewToken, UUID.randomUUID().toString(), "Publish")).campaign
        }
        fun sql(sql: String) = source.connection.use { it.createStatement().use { s -> s.execute(sql) } }
        fun number(sql: String): Long = source.connection.use { it.createStatement().use { s -> s.executeQuery(sql).use { r -> r.next(); r.getLong(1) } } }
        suspend fun balance() = repository.transaction { balance(player) }
        suspend fun error(status: Int, block: suspend () -> Unit) { assertEquals(status, assertFailsWith<AdminException> { block() }.status) }
    }

    @Test fun `mixed gem claims serialize across connections and preserve one ledger entry after restart`() = runTest { database {
        val campaign = publish(draft(request().copy(gems = 13)))
        gifts.deliverDueBatch(); val before = balance()
        val receipts = coroutineScope { List(4) { async(Dispatchers.IO) { gifts.claim(player, campaign.id) } }.awaitAll() }
        assertTrue(receipts.all { it == receipts.first() })
        val result = receipts.first()
        assertEquals(13, result.awardedGems); assertEquals(before.gems + 13, result.gems)
        assertEquals(13, result.gift.gems); assertEquals(before.gold + 100, result.gold); assertEquals(before.xp + 30, result.xp)
        gifts = GiftService(PostgresGiftRepository(source), admin, { now })
        assertEquals(result, gifts.claim(player, campaign.id))
        assertEquals(before.gems + 13, balance().gems)
        assertEquals(1L, number("SELECT count(*) FROM wallet_transactions WHERE source_type='ADMIN_GIFT' AND source_id='${campaign.id}'"))
        assertEquals(13L, number("SELECT gems_delta FROM wallet_transactions WHERE source_id='${campaign.id}'"))
        assertEquals(13L, gifts.detail(owner, campaign.id).counters.claimedGems)
        assertEquals(0L, gifts.mailbox(player, PageRequest(0, 1)).availableCount)
    } }
    @Test fun `gem only campaign previews exact totals and pays real player stats`() = runTest { database {
        val campaign = draft(request().copy(gold = 0, xp = 0, gems = 23))
        assertEquals(23L, gifts.preview(owner, campaign.id, GiftPreviewRequest(1)).totalGems)
        publish(campaign); gifts.deliverDueBatch(); val before = balance()
        val result = gifts.claim(player, campaign.id)
        assertEquals(23, result.awardedGems); assertEquals(before.gems + 23, result.gems)
        assertEquals(before.gold, result.gold); assertEquals(before.xp, result.xp)
        assertEquals(23L, number("SELECT gems_delta FROM wallet_transactions WHERE source_id='${campaign.id}'"))
    } }
    @Test fun `gem overflow and ledger failure roll back every reward and claim marker`() = runTest { database {
        val campaign = publish(draft(request().copy(gems = 5))); gifts.deliverDueBatch(); balance()
        sql("UPDATE player_stats SET gold=11,experience_points=22,gems=2147483643 WHERE user_id='$player'")
        val before = balance()
        error(409) { gifts.claim(player, campaign.id) }
        assertEquals(before, balance()); assertEquals(0L, number("SELECT count(*) FROM wallet_transactions"))
        assertEquals(GiftState.AVAILABLE, gifts.mailbox(player, PageRequest()).items.single().state)
        sql("UPDATE player_stats SET gems=7 WHERE user_id='$player'")
        sql("ALTER TABLE wallet_transactions ADD CONSTRAINT reject_gems CHECK (gems_delta = 0)")
        error(503) { gifts.claim(player, campaign.id) }
        assertEquals(GiftBalance(11, 22, 7), balance())
        assertEquals(1L, gifts.mailbox(player, PageRequest()).availableCount)
        assertEquals(0L, number("SELECT count(*) FROM wallet_transactions"))
        sql("ALTER TABLE wallet_transactions DROP CONSTRAINT reject_gems")
        assertEquals(12, gifts.claim(player, campaign.id).gems)
    } }
    @Test fun `available count spans pages and uses exact time state ownership and delivery filters`() = runTest { database {
        publish(); publish()
        publish(draft(request().copy(expiresAtEpochMillis = now + 10)))
        val cancelled = publish(); val claimed = publish()
        val future = publish(draft(request().copy(availableAtEpochMillis = now + 100)))
        gifts.deliverDueBatch()
        gifts.cancel(owner, cancelled.id, GiftCancelRequest(2, "cancel-count", "Cancel"))
        gifts.claim(player, claimed.id)
        val pending = publish(); val unpublished = draft()
        sql("UPDATE admin_gift_recipients SET delivery='DELIVERED' WHERE campaign_id='${future.id}'")
        sql("INSERT INTO admin_gift_recipients(campaign_id,user_id,delivery) VALUES ('${unpublished.id}','$player','DELIVERED')")
        assertEquals(1L, number("SELECT count(*) FROM admin_gift_recipients WHERE campaign_id='${pending.id}' AND delivery='PENDING'"))
        now += 10
        for (page in listOf(0, 1, 99)) assertEquals(2L, gifts.mailbox(player, PageRequest(page, 1)).availableCount)
        val other = register()
        assertEquals(0L, gifts.mailbox(other, PageRequest()).availableCount)
    } }
    @Test fun `current roles csrf and origin guard campaign mutations`() = runTest { database {
        val campaign = draft(); val preview = gifts.preview(operator, campaign.id, GiftPreviewRequest(1))
        val publish = GiftPublishRequest(1, preview.previewToken, "pub", "Publish")
        error(401) { gifts.publish(GiftAdminContext("player-token", owner.csrfToken, owner.origin), campaign.id, publish) }
        error(403) { gifts.publish(operator, campaign.id, publish) }
        error(403) { gifts.create(support, request()) }
        error(403) { gifts.update(support, campaign.id, request().copy(expectedVersion = 1)) }
        error(403) { gifts.create(owner.copy(csrfToken = null), request()) }
        error(403) { gifts.create(owner.copy(origin = "http://evil.test"), request()) }
        admin.changeAccess(owner.token, owner.csrfToken, owner.origin, admin.session(operator.token).admin.id, ChangeAdminAccessRequest(AdminRole.SUPPORT, true, "Revoke", "revoke"))
        error(401) { gifts.preview(operator, campaign.id, GiftPreviewRequest(1)) }
    } }
    @Test fun `preview deduplicates and rejects nonexistent ids guests and changed audience`() = runTest { database {
        val missing = UUID.randomUUID().toString()
        val guest = run {
            // Real guest identity remains ineligible, even when explicitly targeted.
            val id = UUID.randomUUID().toString()
            sql("INSERT INTO users(id,account_type,status,created_at,updated_at) VALUES ('$id','GUEST','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
            id
        }
        val campaign = draft(request().copy(audience = GiftAudience.USER_IDS, userIds = listOf(player, player, missing, guest)))
        val p = gifts.preview(owner, campaign.id, GiftPreviewRequest(1))
        assertEquals(1, p.recipientCount); assertEquals(1, p.duplicateCount); assertEquals(setOf(missing, guest), p.invalidUserIds.toSet()); assertEquals(100, p.totalGold)
        error(400) { gifts.publish(owner, campaign.id, GiftPublishRequest(1, p.previewToken, "bad", "Publish")) }
        val all = draft(); val previous = gifts.preview(owner, all.id, GiftPreviewRequest(1)); register()
        error(409) { gifts.publish(owner, all.id, GiftPublishRequest(1, previous.previewToken, "changed", "Publish")) }
    } }
    @Test fun `versions freeze contents and request replay survives state changes and restart`() = runTest { database {
        val input = request(); val campaign = draft(input)
        assertEquals(campaign, draft(input)); error(409) { draft(input.copy(gold = 101)) }
        val p = gifts.preview(owner, campaign.id, GiftPreviewRequest(1))
        val updated = gifts.update(operator, campaign.id, input.copy(titleEn = "New", expectedVersion = 1, requestId = "edit")).campaign
        error(409) { gifts.update(owner, campaign.id, input.copy(expectedVersion = 1, requestId = "stale")) }
        error(409) { gifts.publish(owner, campaign.id, GiftPublishRequest(1, p.previewToken, "stale-publish", "Publish")) }
        val fresh = gifts.preview(owner, campaign.id, GiftPreviewRequest(updated.version))
        val pub = GiftPublishRequest(2, fresh.previewToken, "pub", "Publish")
        val published = gifts.publish(owner, campaign.id, pub)
        val cancel = GiftCancelRequest(3, "cancel", "Cancel")
        val cancelled = gifts.cancel(owner, campaign.id, cancel)
        gifts = GiftService(PostgresGiftRepository(source), admin, { now })
        assertEquals(published, gifts.publish(owner, campaign.id, pub)); assertEquals(cancelled, gifts.cancel(owner, campaign.id, cancel))
        error(409) { gifts.cancel(owner, campaign.id, cancel.copy(reason = "Changed")) }
        error(409) { gifts.update(owner, campaign.id, input.copy(expectedVersion = 4, requestId = "edit-after")) }
        assertEquals(1, number("SELECT count(*) FROM admin_audit WHERE action='GIFT_PUBLISH'"))
    } }
    @Test fun `partial delivery restart skips deleted accounts and freezes registration`() = runTest { database {
        val other = register(); val deleted = register(); val campaign = publish(); register()
        sql("DELETE FROM users WHERE id='$deleted'")
        assertEquals(1, gifts.deliverDueBatch(1))
        gifts = GiftService(PostgresGiftRepository(source), admin, { now })
        assertEquals(2, gifts.deliverDueBatch(100)); assertEquals(0, gifts.deliverDueBatch(100))
        val counters = gifts.detail(owner, campaign.id).counters
        assertEquals(3, counters.eligible); assertEquals(2, counters.delivered); assertEquals(1, counters.skipped); assertEquals(0, counters.pending)
        assertEquals(1, gifts.mailbox(player, PageRequest()).total); assertEquals(1, gifts.mailbox(other, PageRequest()).total)
        assertEquals(3, number("SELECT count(*) FROM admin_gift_recipients"))
    } }
    @Test fun `claims respect ownership and exact availability and expiry boundaries`() = runTest { database {
        val other = register(); val start = now + 1000
        val campaign = publish(draft(request().copy(audience = GiftAudience.USER_IDS, userIds = listOf(player), availableAtEpochMillis = start, expiresAtEpochMillis = start + 1000)))
        now = start - 1; assertEquals(0, gifts.deliverDueBatch()); assertEquals(0, gifts.mailbox(player, PageRequest()).total)
        error(404) { gifts.claim(player, campaign.id) }
        now = start; gifts.deliverDueBatch(); error(404) { gifts.claim(other, campaign.id) }
        assertEquals(GiftState.AVAILABLE, gifts.mailbox(player, PageRequest()).items.single().state)
        val result = gifts.claim(player, campaign.id); assertEquals(100, result.awardedGold); assertEquals(30, result.awardedXp)
        now = start + 1000; assertEquals(result.gift, gifts.claim(player, campaign.id).gift)
        val next = publish(draft(request().copy(expiresAtEpochMillis = now + 10))); gifts.deliverDueBatch(); now += 10
        error(409) { gifts.claim(player, next.id) }; assertEquals(GiftState.EXPIRED, gifts.mailbox(player, PageRequest()).items.first { it.campaignId == next.id }.state)
        val third = publish(draft(request().copy(expiresAtEpochMillis = now + 10))); gifts.deliverDueBatch(); now += 9
        assertEquals(GiftState.CLAIMED, gifts.claim(player, third.id).gift.state)
    } }
    @Test fun `two concurrent claims award one wallet transaction and no earned leaderboard income`() = runTest { database {
        val campaign = publish(); gifts.deliverDueBatch(); val before = balance()
        val results = coroutineScope { List(2) { async(Dispatchers.IO) { gifts.claim(player, campaign.id) } }.awaitAll() }
        assertEquals(results[0], results[1]); assertEquals(before.gold + 100, balance().gold); assertEquals(before.xp + 30, balance().xp)
        assertEquals(1, number("SELECT count(*) FROM wallet_transactions WHERE source_type='ADMIN_GIFT' AND source_id='${campaign.id}'"))
        assertEquals(0, number("SELECT lifetime_earned_gold FROM player_stats WHERE user_id='$player'"))
    } }
    @Test fun `claim cancel race is atomic and paid gifts remain claimed`() = runTest { database {
        val campaign = publish(); gifts.deliverDueBatch(); val before = balance()
        val outcomes = coroutineScope {
            listOf(async(Dispatchers.IO) { runCatching { gifts.claim(player, campaign.id) } }, async(Dispatchers.IO) { runCatching { gifts.cancel(owner, campaign.id, GiftCancelRequest(2, "cancel", "Stop")) } }).awaitAll()
        }
        assertTrue(outcomes[1].isSuccess)
        val paid = outcomes[0].isSuccess
        assertEquals(before.gold + if (paid) 100 else 0, balance().gold)
        assertEquals(if (paid) GiftState.CLAIMED else GiftState.CANCELLED, gifts.mailbox(player, PageRequest()).items.single().state)
        assertEquals(if (paid) 1 else 0, number("SELECT count(*) FROM wallet_transactions WHERE source_type='ADMIN_GIFT'"))
    } }
    @Test fun `received time survives waiting for campaign lock across expiry`() = runTest { database {
        val campaign = publish(draft(request().copy(expiresAtEpochMillis = now + 10))); gifts.deliverDueBatch()
        val received = now + 9
        source.connection.use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { it.execute("SELECT id FROM admin_gift_campaigns WHERE id='${campaign.id}' FOR UPDATE") }
            coroutineScope {
                val waiting = async(Dispatchers.IO) { gifts.claim(player, campaign.id, received) }
                delay(100); now += 100; connection.commit()
                assertEquals(GiftState.CLAIMED, waiting.await().gift.state)
            }
        }
    } }
    @Test fun `wallet failure and overflow roll back claim state balance and history`() = runTest { database {
        val campaign = publish(); gifts.deliverDueBatch(); val before = balance()
        sql("ALTER TABLE wallet_transactions ADD CONSTRAINT reject_gift CHECK (source_type <> 'ADMIN_GIFT')")
        error(503) { gifts.claim(player, campaign.id) }
        assertEquals(before, balance()); assertEquals(GiftState.AVAILABLE, gifts.mailbox(player, PageRequest()).items.single().state)
        sql("ALTER TABLE wallet_transactions DROP CONSTRAINT reject_gift")
        sql("UPDATE player_stats SET gold=2147483647 WHERE user_id='$player'")
        error(409) { gifts.claim(player, campaign.id) }
        assertEquals(2147483647, balance().gold); assertEquals(0, number("SELECT count(*) FROM wallet_transactions"))
        assertEquals(GiftState.AVAILABLE, gifts.mailbox(player, PageRequest()).items.single().state)
    } }
    @Test fun `audit failure rolls back publish frozen audience and cancellation`() = runTest { database {
        val campaign = draft(); val p = gifts.preview(owner, campaign.id, GiftPreviewRequest(1)); val pub = GiftPublishRequest(1, p.previewToken, "publish", "Publish")
        sql("ALTER TABLE admin_audit ADD CONSTRAINT reject_gift CHECK(action <> 'GIFT_PUBLISH')")
        error(503) { gifts.publish(owner, campaign.id, pub) }
        assertEquals(CampaignState.DRAFT, gifts.detail(owner, campaign.id).campaign.state); assertEquals(0, number("SELECT count(*) FROM admin_gift_recipients"))
        sql("ALTER TABLE admin_audit DROP CONSTRAINT reject_gift"); gifts.publish(owner, campaign.id, pub); gifts.deliverDueBatch()
        sql("ALTER TABLE admin_audit ADD CONSTRAINT reject_cancel CHECK(action <> 'GIFT_CANCEL')")
        error(503) { gifts.cancel(owner, campaign.id, GiftCancelRequest(2, "cancel", "Stop")) }
        assertEquals(GiftState.AVAILABLE, gifts.mailbox(player, PageRequest()).items.single().state)
    } }
    @Test fun `claim all is independent and notification deletion does not remove entitlement`() = runTest { database {
        val a = publish(); val b = publish(); gifts.deliverDueBatch()
        gifts.cancel(owner, b.id, GiftCancelRequest(2, "cancel", "Stop"))
        sql("INSERT INTO user_notifications(user_id,notification_id,kind,title,message,destination,created_at) VALUES ('$player','gift-reminder','MISSION','Gift','Reminder','PROFILE',CURRENT_TIMESTAMP)")
        sql("DELETE FROM user_notifications WHERE user_id='$player'")
        val results = gifts.claimAll(player, GiftClaimAllRequest(listOf(a.id, b.id, UUID.randomUUID().toString())))
        assertEquals(GiftState.CLAIMED, results.items[0].result!!.gift.state); assertEquals("CONFLICT", results.items[1].error!!.code); assertEquals("NOT_FOUND", results.items[2].error!!.code)
        assertEquals(2, gifts.mailbox(player, PageRequest()).total)
        error(400) { gifts.claimAll(player, GiftClaimAllRequest(List(101) { a.id })) }
    } }
    @Test fun `default expiry totals registered bounds validation and cancellation stop delivery`() = runTest { database {
        val input = request(); val campaign = draft(input)
        assertEquals(now + 30L * 24 * 60 * 60 * 1000, campaign.draft.expiresAtEpochMillis)
        for (bad in listOf(input.copy(gold = -1), input.copy(gold = 0, xp = 0), input.copy(titleEn = ""), input.copy(expiresAtEpochMillis = now), input.copy(audience = GiftAudience.REGISTERED_BETWEEN))) {
            error(400) { draft(bad.copy(requestId = UUID.randomUUID().toString())) }
        }
        val bound = number("SELECT (extract(epoch from created_at)*1000)::bigint FROM users WHERE id='$player'")
        val c = draft(request().copy(audience = GiftAudience.REGISTERED_BETWEEN, registeredFromEpochMillis = bound - 1, registeredUntilEpochMillis = bound + 1))
        assertEquals(1, gifts.preview(owner, c.id, GiftPreviewRequest(1)).recipientCount)
        val pub = publish(c); gifts.cancel(owner, pub.id, GiftCancelRequest(2, "cancel", "Stop")); assertEquals(0, gifts.deliverDueBatch())
        assertEquals(1, gifts.detail(owner, pub.id).counters.cancelled)
    } }
    @Test fun `delivery failure rolls back checkpoint and retries without duplicate entitlements`() = runTest { database {
        register(); val campaign=publish()
        sql("ALTER TABLE admin_gift_recipients ADD CONSTRAINT reject_delivery CHECK(delivery <> 'DELIVERED')")
        error(503) { gifts.deliverDueBatch() }
        assertEquals(2,gifts.detail(owner,campaign.id).counters.pending)
        assertEquals(0,gifts.mailbox(player,PageRequest()).total)
        sql("ALTER TABLE admin_gift_recipients DROP CONSTRAINT reject_delivery")
        gifts=GiftService(PostgresGiftRepository(source),admin,{now})
        assertEquals(2,gifts.deliverDueBatch()); assertEquals(0,gifts.deliverDueBatch())
        assertEquals(2,gifts.detail(owner,campaign.id).counters.delivered)
    } }
    @Test fun `late delivery retains expiry and upgraded guest never joins frozen audience`() = runTest { database {
        val guest=UUID.randomUUID().toString()
        sql("INSERT INTO users(id,account_type,status,created_at,updated_at) VALUES ('$guest','GUEST','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
        val campaign=publish(draft(request().copy(expiresAtEpochMillis=now+10)))
        sql("UPDATE users SET account_type='REGISTERED',email_normalized='upgraded@example.com',password_hash='fixture' WHERE id='$guest'")
        now+=11; gifts.deliverDueBatch()
        assertEquals(GiftState.EXPIRED,gifts.mailbox(player,PageRequest()).items.single().state)
        assertEquals(0,gifts.mailbox(guest,PageRequest()).total)
        error(409) { gifts.claim(player,campaign.id) }
        assertEquals(1,gifts.detail(owner,campaign.id).counters.expired)
    } }
    @Test fun `gift claim is visible in existing player profile and actual claimed totals`() = runTest { database {
        val campaign=publish(); gifts.deliverDueBatch(); gifts.claim(player,campaign.id)
        val profile=PostgresPlayerProfileRepository(source).findByPlayerId(player)!!
        assertEquals(100,profile.progression.gold); assertEquals(30,profile.progression.experiencePoints)
        val counters=gifts.detail(owner,campaign.id).counters
        assertEquals(1,counters.claimed); assertEquals(100,counters.claimedGold); assertEquals(30,counters.claimedXp)
    } }
    @Test fun `account deletion racing a claim does not deadlock or create orphan payouts`() = runTest { database {
        val campaign=publish(); gifts.deliverDueBatch()
        withContext(Dispatchers.Default) { withTimeout(10000) {
            coroutineScope {
                val claim=async(Dispatchers.IO) { runCatching { gifts.claim(player,campaign.id) } }
                val deletion=async(Dispatchers.IO) { sql("DELETE FROM users WHERE id='$player'") }
                deletion.await()
                claim.await().exceptionOrNull()?.let { assertEquals(401,assertIs<AdminException>(it).status) }
            }
        }
        }
        assertEquals(0,number("SELECT count(*) FROM wallet_transactions WHERE user_id='$player'"))
        assertEquals(1,number("SELECT count(*) FROM admin_gift_recipients WHERE user_id='$player'"))
    } }
    @Test fun `http authentication storage failure returns safe unavailable gift error`() = runTest { database {
        val account=assertIs<AuthResult.Success>(auth.register("outage@example.com","GoodPassword123!","Player","test")).session
        val actualAuth=auth
        val actualGifts=gifts
        // Keep the actual PostgreSQL authentication repository; make its session SELECT fail.
        sql("ALTER TABLE sessions RENAME TO sessions_temporarily_unavailable")
        testApplication {
            application { gameModule(authService=actualAuth,giftService=actualGifts) }
            for (prefix in listOf("/gifts", "/api/gifts")) {
                val response=client.get("$prefix/mailbox") { bearerAuth(account.accessToken) }
                assertEquals(HttpStatusCode.ServiceUnavailable,response.status)
                assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
                val body=response.bodyAsText()
                val error=Json.decodeFromString<GiftError>(body)
                assertEquals("STORAGE_UNAVAILABLE",error.code)
                assertEquals("Kho dữ liệu người chơi chưa sẵn sàng.",error.message)
                assertFalse(body.contains("sessions")); assertFalse(body.contains("SELECT")); assertFalse(body.contains("SQLException"))
            }
        }
    } }}
