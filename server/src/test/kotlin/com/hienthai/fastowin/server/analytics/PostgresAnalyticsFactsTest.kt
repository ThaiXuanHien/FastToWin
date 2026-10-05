package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Instant
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

class PostgresAnalyticsFactsTest {
    // Missing registration instrumentation must fail these assertions, not silently skip PG.
    @Test fun guestUpgradeRecordsFridayOnceInsteadOfMondayIdentityCreation() = runTest { database {
        val guest = PostgresGuestIdentityRepository(source).resolveGuest("Guest", null, MONDAY)
        assertEquals(0, count("analytics_registrations"))
        val upgrade = GuestUpgrade(hashToken(guest.resumeToken), "upgrade@example.com", "hash", "android", session(FRIDAY))
        assertIs<GuestUpgradeResult.Success>(auth.upgradeGuest(upgrade))
        assertEquals(1, count("analytics_registrations"))
        assertEquals(FRIDAY, time("SELECT registered_at FROM analytics_registrations"))
        assertEquals(MONDAY, time("SELECT created_at FROM users"))
        assertEquals(GuestUpgradeResult.InvalidGuestSession, auth.upgradeGuest(upgrade))
        auth.replaceSessions(UUID.fromString(guest.playerId), "ios", session(FRIDAY + 1000))
        assertEquals(1, count("analytics_registrations"))
    } }

    @Test fun directRegistrationRetryAndLoginDoNotDuplicateFirstTransition() = runTest { database {
        val account = account()
        assertTrue(auth.createAccount(account))
        assertEquals(1, count("analytics_registrations"))
        assertFalse(auth.createAccount(account))
        auth.replaceSessions(account.userId, "ios", session(FRIDAY + 1000))
        assertEquals(1, count("analytics_registrations"))
        assertEquals(FRIDAY, time("SELECT registered_at FROM analytics_registrations"))
    } }

    // Missing/mismatched debit instrumentation must fail event/amount/distinct checks.
    @Test fun twoSuccessfulDebitsRetainSameWalletEventsAndOneDistinctBuyer() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account)); fund(account.userId)
        assertTrue(profiles.buyCosmetic(account.userId.toString(), "card_back_test", "CARD_BACK", 100))
        assertTrue(profiles.buyCosmetic(account.userId.toString(), "board_skin_test", "BOARD_SKIN", 250))
        assertEquals(2, count("analytics_item_purchases"))
        assertEquals(350, number("SELECT sum(amount) FROM analytics_item_purchases WHERE currency='GOLD'"))
        assertEquals(1, number("SELECT count(DISTINCT subject_key) FROM analytics_item_purchases"))
        assertEquals(2, number("""SELECT count(*) FROM analytics_item_purchases f JOIN wallet_transactions w
            ON f.event_id=w.id WHERE f.amount=-w.gold_delta AND f.purchased_at=w.created_at
            AND f.item_id=w.source_id AND w.source_type='COSMETIC_PURCHASE'"""))
        assertEquals(650, number("SELECT gold FROM player_stats"))
    } }

    @Test fun registrationFactFailureRollsBackIdentityProfileSessionAndPrivateLink() = runTest { database {
        rejectInsert("analytics_registrations", atCommit = true)
        assertFailsWith<SQLException> { auth.createAccount(account()) }
        listOf("users", "profiles", "sessions", "analytics_subject_links", "analytics_registrations").forEach {
            assertEquals(0, count(it), it)
        }
        sql("DROP TRIGGER reject_fact ON analytics_registrations")
        assertTrue(auth.createAccount(account()))
        assertEquals(1, count("analytics_registrations"))
    } }

    @Test fun upgradeFactFailureLeavesGuestAndOriginalSessionRetryable() = runTest { database {
        val guest = PostgresGuestIdentityRepository(source).resolveGuest("Guest", null, MONDAY)
        val upgrade = GuestUpgrade(hashToken(guest.resumeToken), "retry@example.com", "hash", "android", session(FRIDAY))
        rejectInsert("analytics_registrations")
        assertFailsWith<SQLException> { auth.upgradeGuest(upgrade) }
        assertEquals(1, number("SELECT count(*) FROM users WHERE account_type='GUEST'"))
        assertEquals(1, number("SELECT count(*) FROM sessions WHERE revoked_at IS NULL"))
        assertEquals(0, count("analytics_subject_links"))
        assertEquals(0, count("analytics_registrations"))
        sql("DROP TRIGGER reject_fact ON analytics_registrations")
        assertIs<GuestUpgradeResult.Success>(auth.upgradeGuest(upgrade))
        assertEquals(FRIDAY, time("SELECT registered_at FROM analytics_registrations"))
    } }

    @Test fun itemFactFailureRollsBackWalletOwnershipAndNewSubjectLink() = runTest { database {
        val guest = PostgresGuestIdentityRepository(source).resolveGuest("Guest", null, MONDAY)
        fund(UUID.fromString(guest.playerId))
        rejectInsert("analytics_item_purchases")
        assertFalse(profiles.buyCosmetic(guest.playerId, "card_back_test", "CARD_BACK", 100))
        assertEquals(1000, number("SELECT gold FROM player_stats"))
        listOf("wallet_transactions", "player_cosmetics", "analytics_subject_links", "analytics_item_purchases").forEach {
            assertEquals(0, count(it), it)
        }
        sql("DROP TRIGGER reject_fact ON analytics_item_purchases")
        assertTrue(profiles.buyCosmetic(guest.playerId, "card_back_test", "CARD_BACK", 100))
        assertEquals(900, number("SELECT gold FROM player_stats"))
        assertEquals(1, count("analytics_item_purchases"))
    } }

    @Test fun concurrentDuplicatePurchaseCommitsOneDebitOwnershipAndFact() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account)); fund(account.userId)
        val results = coroutineScope {
            List(2) { async(Dispatchers.IO) { profiles.buyCosmetic(account.userId.toString(), "card_back_test", "CARD_BACK", 100) } }.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(900, number("SELECT gold FROM player_stats"))
        listOf("player_cosmetics", "wallet_transactions", "analytics_item_purchases").forEach { assertEquals(1, count(it), it) }
    } }

    @Test fun grantsDefaultsEquipAndFailedPurchasesProduceNoPurchaseFacts() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account)); fund(account.userId)
        val id = account.userId
        assertNotNull(profiles.findByPlayerId(id.toString()))
        sql("INSERT INTO player_cosmetics(user_id,cosmetic_id,cosmetic_type,acquired_at) VALUES ('$id','gift_back','CARD_BACK',CURRENT_TIMESTAMP)")
        assertTrue(profiles.equipCosmetic(id.toString(), "gift_back", "CARD_BACK"))
        assertFalse(profiles.buyCosmetic(id.toString(), "gift_back", "CARD_BACK", 100))
        assertFalse(profiles.buyCosmetic(id.toString(), "expensive_back", "CARD_BACK", 2000))
        assertFalse(profiles.buyCosmetic(id.toString(), "free_back", "CARD_BACK", 0))
        assertFalse(profiles.buyCosmetic(id.toString(), "negative_back", "CARD_BACK", -100))
        assertEquals(1000, number("SELECT gold FROM player_stats"))
        assertEquals(0, count("analytics_item_purchases"))
        assertEquals(0, count("wallet_transactions"))
    } }

    @Test fun deletionUnlinksBeforeDeletingIdentityAndRetainsAnonymousFacts() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account)); fund(account.userId)
        assertTrue(profiles.buyCosmetic(account.userId.toString(), "card_back_test", "CARD_BACK", 100))
        // A scrub guard models an identity-deletion hook that must never observe an account link.
        sql("""CREATE FUNCTION require_unlinked() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN IF EXISTS(SELECT 1 FROM analytics_subject_links WHERE user_id=OLD.id) THEN
            RAISE EXCEPTION 'identity link not scrubbed'; END IF; RETURN OLD; END ${'$'}${'$'}""")
        sql("CREATE TRIGGER scrub_guard BEFORE DELETE ON users FOR EACH ROW EXECUTE FUNCTION require_unlinked()")
        assertTrue(auth.deleteAccount(account.userId))
        assertFalse(auth.deleteAccount(account.userId))
        assertEquals(0, count("users"))
        assertEquals(0, count("analytics_subject_links"))
        assertEquals(1, count("analytics_registrations"))
        assertEquals(100, number("SELECT sum(amount) FROM analytics_item_purchases"))
        assertEquals(1, number("SELECT count(DISTINCT subject_key) FROM analytics_item_purchases"))
        source.connection.use { connection ->
            connection.autoCommit = false
            assertFailsWith<IllegalStateException> { AnalyticsFacts.subjectKey(connection, account.userId.toString()) }
            connection.rollback()
        }
        assertEquals(0, count("analytics_subject_links"))
    } }

    @Test fun failedDeletionRollsBackScrubAndRetainsAccountSessionAndFacts() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account))
        sql("CREATE FUNCTION reject_delete() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'delete rejected'; END ${'$'}${'$'}")
        sql("CREATE TRIGGER reject_delete BEFORE DELETE ON users FOR EACH ROW EXECUTE FUNCTION reject_delete()")
        assertFailsWith<SQLException> { auth.deleteAccount(account.userId) }
        listOf("users", "sessions", "analytics_subject_links", "analytics_registrations").forEach { assertEquals(1, count(it), it) }
        sql("DROP TRIGGER reject_delete ON users")
        assertTrue(auth.deleteAccount(account.userId))
        assertEquals(0, count("analytics_subject_links"))
        assertEquals(1, count("analytics_registrations"))
    } }

    @Test fun writerWaitingOnDeletionCannotRecreatePrivateSubjectLink() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account))
        val writerPid = CompletableDeferred<Int>()
        coroutineScope {
            source.connection.use { deleting ->
                deleting.autoCommit = false
                deleting.createStatement().use { it.executeQuery("SELECT id FROM users WHERE id='${account.userId}' FOR UPDATE").close() }
                val writer = async(Dispatchers.IO) {
                    source.connection.use { connection ->
                        connection.autoCommit = false
                        connection.createStatement().use { it.execute("SET LOCAL statement_timeout='10s'") }
                        connection.createStatement().use { statement ->
                            statement.executeQuery("SELECT pg_backend_pid()").use { result ->
                                result.next(); writerPid.complete(result.getInt(1))
                            }
                        }
                        try {
                            AnalyticsFacts.recordRegistration(connection, account.userId.toString(), FRIDAY)
                            connection.commit()
                            false
                        } catch (_: IllegalStateException) { connection.rollback(); true }
                    }
                }
                val pid = writerPid.await()
                val deadline = System.nanoTime() + 5_000_000_000L
                while (number("SELECT count(*) FROM pg_stat_activity WHERE pid=$pid AND wait_event_type='Lock'") == 0L) {
                    check(System.nanoTime() < deadline) { "Writer did not wait on the identity deletion lock" }
                    Thread.sleep(10)
                }
                AnalyticsFacts.eraseIdentityLink(deleting, account.userId.toString())
                deleting.createStatement().use { it.executeUpdate("DELETE FROM users WHERE id='${account.userId}'") }
                deleting.commit()
                assertTrue(writer.await())
            }
        }
        assertEquals(0, count("analytics_subject_links"))
        assertEquals(1, count("analytics_registrations"))
    } }

    @Test fun sharedSubjectAndEventRetryKeepCurrenciesSeparateAndExact() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account))
        val goldEvent = UUID.randomUUID(); val gemsEvent = UUID.randomUUID()
        source.connection.use { connection ->
            connection.autoCommit = false
            AnalyticsFacts.recordRegistration(connection, account.userId.toString(), FRIDAY + 1000)
            AnalyticsFacts.recordItemPurchase(connection, goldEvent, account.userId.toString(), "gold_item", "GOLD", 9007199254740993, FRIDAY)
            AnalyticsFacts.recordItemPurchase(connection, goldEvent, account.userId.toString(), "gold_item", "GOLD", 9007199254740993, FRIDAY)
            AnalyticsFacts.recordItemPurchase(connection, gemsEvent, account.userId.toString(), "gem_fixture", "GEMS", 7, FRIDAY)
            connection.commit()
        }
        assertEquals(2, count("analytics_item_purchases"))
        assertEquals(9007199254740993, number("SELECT sum(amount) FROM analytics_item_purchases WHERE currency='GOLD'"))
        assertEquals(7, number("SELECT sum(amount) FROM analytics_item_purchases WHERE currency='GEMS'"))
        assertEquals(1, number("SELECT count(DISTINCT subject_key) FROM (SELECT subject_key FROM analytics_item_purchases UNION ALL SELECT subject_key FROM analytics_registrations) subjects"))
        assertEquals(FRIDAY, time("SELECT registered_at FROM analytics_registrations"))
    } }

    @Test fun collectionCoverageStartsAtMigrationWithoutInventedHistoricalFacts() = runTest { database(target = "51") {
        val id = UUID.randomUUID()
        sql("INSERT INTO users(id,account_type,status,email_normalized,password_hash,created_at,updated_at) VALUES ('$id','REGISTERED','ACTIVE','historical@example.com','hash','2020-01-01Z','2025-01-01Z')")
        sql("INSERT INTO wallet_transactions(id,user_id,source_type,source_id,gold_delta,gems_delta,xp_delta,created_at) VALUES ('${UUID.randomUUID()}','$id','COSMETIC_PURCHASE','unknown',-100,0,0,'2020-01-01Z')")
        val before = System.currentTimeMillis()
        migrate(source)
        val after = System.currentTimeMillis()
        listOf("analytics_registrations", "analytics_item_purchases", "analytics_financial_events", "analytics_subject_links").forEach { assertEquals(0, count(it), it) }
        assertEquals(2, number("SELECT count(*) FROM analytics_source_coverage WHERE source='SERVER' AND status='COMPLETE' AND freshness IS NOT NULL"))
        assertTrue(time("SELECT min(coverage_start) FROM analytics_source_coverage WHERE source='SERVER'") in before..after)
        assertEquals(2, number("SELECT count(*) FROM analytics_source_coverage WHERE metric='FINANCIAL' AND environment='LIVE' AND status='UNAVAILABLE' AND coverage_start IS NULL AND freshness IS NULL"))
    } }

    @Test fun laterCommitFromEarlierTransactionDoesNotRegressFreshness() = runTest { database {
        val first = PostgresGuestIdentityRepository(source).resolveGuest("First", null, MONDAY)
        val second = PostgresGuestIdentityRepository(source).resolveGuest("Second", null, MONDAY)
        source.connection.use { earlier ->
            earlier.autoCommit = false
            earlier.createStatement().use { it.executeQuery("SELECT CURRENT_TIMESTAMP").close() }
            source.connection.use { later ->
                later.autoCommit = false
                AnalyticsFacts.recordRegistration(later, second.playerId, FRIDAY)
                later.commit()
            }
            val latest = time("SELECT freshness FROM analytics_source_coverage WHERE metric='REGISTRATIONS'")
            AnalyticsFacts.recordRegistration(earlier, first.playerId, FRIDAY)
            earlier.commit()
            assertTrue(time("SELECT freshness FROM analytics_source_coverage WHERE metric='REGISTRATIONS'") >= latest,
                "Committed analytics freshness must not move backwards")
        }
    } }

    @Test fun financialFoundationRetainsExactAnonymousEventsWithSourceScopedUniqueness() = runTest { database {
        val account = account(); assertTrue(auth.createAccount(account))
        val subject = text("SELECT subject_key::text FROM analytics_subject_links")
        fun event(source: String, environment: String, eventId: String, type: String, amount: String) =
            sql("""INSERT INTO analytics_financial_events(source,environment,event_id,event_type,transaction_id,
                product_id,subject_key,amount_minor,currency,currency_exponent,occurred_at)
                VALUES ('$source','$environment','$eventId','$type','transaction','product','$subject',$amount,'THB',2,'2026-10-02T05:00:00Z')""")
        event("GOOGLE_PLAY", "LIVE", "payment", "PAYMENT", "9007199254740993")
        assertFailsWith<SQLException> { event("GOOGLE_PLAY", "LIVE", "payment", "PAYMENT", "9007199254740993") }
        assertFailsWith<SQLException> { event("GOOGLE_PLAY", "LIVE", "other-payment-id", "PAYMENT", "1") }
        event("GOOGLE_PLAY", "LIVE", "refund", "REFUND", "1")
        event("GOOGLE_PLAY", "SANDBOX", "payment", "PAYMENT", "10")
        event("APP_STORE", "LIVE", "payment", "PAYMENT", "20")
        assertEquals("9007199254740993", text("SELECT amount_minor::text FROM analytics_financial_events WHERE source='GOOGLE_PLAY' AND environment='LIVE' AND event_type='PAYMENT'"))
        assertTrue(auth.deleteAccount(account.userId))
        assertEquals(0, count("analytics_subject_links"))
        assertEquals(4, count("analytics_financial_events"))
        assertEquals(1, number("SELECT count(DISTINCT subject_key) FROM analytics_financial_events"))
        assertEquals(2, number("SELECT count(*) FROM analytics_source_coverage WHERE metric='FINANCIAL' AND status='UNAVAILABLE'"))
    } }

    private fun migrate(source: HikariDataSource, target: String? = null) {
        val configuration = Flyway.configure().dataSource(source).schemas(source.schema).defaultSchema(source.schema)
            .locations("classpath:db/migration")
        if (target != null) configuration.target(target)
        configuration.load().migrate()
    }

    private suspend fun database(target: String? = null, block: suspend Fixture.() -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL required for PostgreSQL behavior coverage")
        val schema = "analytics_test_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER")
            password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 5
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema }).use { source ->
                    migrate(source, target)
                    Fixture(source).block()
                }
            } finally {
                setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private class Fixture(val source: HikariDataSource) {
        val auth = PostgresAuthRepository(source)
        val profiles = PostgresPlayerProfileRepository(source)
        fun sql(text: String) = source.connection.use { it.createStatement().use { s -> s.execute(text) } }
        fun number(text: String): Long = source.connection.use { it.createStatement().use { s ->
            s.executeQuery(text).use { r -> check(r.next()); r.getLong(1) }
        } }
        fun count(table: String): Long {
            val exists = source.connection.use { it.prepareStatement("SELECT to_regclass(?) IS NOT NULL").use { s ->
                s.setString(1, table); s.executeQuery().use { r -> r.next(); r.getBoolean(1) }
            } }
            return if (exists) number("SELECT count(*) FROM $table") else 0
        }
        fun time(text: String): Long = source.connection.use { it.createStatement().use { s ->
            s.executeQuery(text).use { r -> check(r.next()); r.getTimestamp(1).time }
        } }
        fun text(query: String): String = source.connection.use { it.createStatement().use { statement ->
            statement.executeQuery(query).use { result -> check(result.next()); result.getString(1) }
        } }
        fun fund(id: UUID) = sql("INSERT INTO player_stats(user_id,gold,updated_at) VALUES ('$id',1000,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO UPDATE SET gold=1000")
        fun rejectInsert(table: String, atCommit: Boolean = false) {
            sql("CREATE FUNCTION reject_fact() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'fact rejected'; END ${'$'}${'$'}")
            if (atCommit) sql("CREATE CONSTRAINT TRIGGER reject_fact AFTER INSERT ON $table DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_fact()")
            else sql("CREATE TRIGGER reject_fact BEFORE INSERT ON $table FOR EACH ROW EXECUTE FUNCTION reject_fact()")
        }
    }

    companion object {
        val MONDAY = Instant.parse("2026-09-28T05:00:00Z").toEpochMilli()
        val FRIDAY = Instant.parse("2026-10-02T05:00:00Z").toEpochMilli()
        fun session(now: Long) = NewAuthSession(UUID.randomUUID(), hashToken(UUID.randomUUID().toString()),
            hashToken(UUID.randomUUID().toString()), now + 3_600_000, now + 30 * 86_400_000L, now)
        fun account() = NewAccount(UUID.randomUUID(), "${UUID.randomUUID()}@example.com", "hash", "Player",
            UUID.randomUUID().toString().replace("-", "").take(12), "android", session(FRIDAY))
    }
}
