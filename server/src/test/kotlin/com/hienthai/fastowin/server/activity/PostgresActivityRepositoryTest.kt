package com.hienthai.fastowin.server.activity

import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.AdminException
import com.hienthai.fastowin.server.analytics.CoverageStatus
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.hienthai.fastowin.server.analytics.AnalyticsFacts
import org.flywaydb.core.Flyway
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

class PostgresActivityRepositoryTest {
    @Test fun noFabricatedHistoryAndHealthyEmptyWindowProvesZero() = runTest { database {
        read { assertEquals(CoverageStatus.UNAVAILABLE, coverage(1000, 2000, 3000).status)
            assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", assertFailsWith<AdminException> { registeredRecipients(1000, 2000, 3000) }.code) }
        val tracker = ActivityTracker(repository, { 1000 }); tracker.checkpoint()
        ActivityTracker(repository, { 3000 }).checkpoint() // restart does not bridge even two seconds
        read { assertEquals(CoverageStatus.PARTIAL, coverage(1000, 3000, 3000).status) }
        var now = 4000L; val healthy = ActivityTracker(repository, { now }); healthy.checkpoint()
        now = 34_000; healthy.checkpoint()
        read { assertEquals(CoverageStatus.COMPLETE, coverage(4000, 34_000, now).status)
            assertEquals(0, activeSubjects(4000, 34_000, now)); assertEquals(emptyList(), registeredRecipients(4000, 34_000, now)) }
    } }

    @Test fun unionRequiresFullRangeAndCountsDistinctWithHalfOpenBoundaries() = runTest { database {
        val a = account(); val b = account(); var now = 1000L
        val tracker = ActivityTracker(repository, { now }); val first = UUID.randomUUID()
        tracker.connected(first, a); now = 2000; tracker.disconnected(first)
        val second = UUID.randomUUID(); tracker.connected(second, a)
        now = 3000; tracker.disconnected(second)
        val instant = UUID.randomUUID(); tracker.connected(instant, b); tracker.disconnected(instant)
        now = 4000; tracker.checkpoint()
        read { assertEquals(CoverageStatus.COMPLETE, coverage(1000, 4000, now).status)
            assertEquals(2, activeSubjects(1000, 4000, now)); assertEquals(1, activeSubjects(1000, 3000, now))
            assertEquals(1, activeSubjects(3000, 4000, now)); assertEquals(listOf(a, b).sorted(), registeredRecipients(1000, 4000, now)) }
        var next = 4000L; val restarted = ActivityTracker(repository, { next }); restarted.checkpoint()
        next = 5000; restarted.checkpoint()
        read { assertEquals(CoverageStatus.COMPLETE, coverage(1000, 5000, next).status) }
        next = 66_000; restarted.checkpoint()
        read { assertEquals(CoverageStatus.PARTIAL, coverage(1000, 66_000, next).status)
            assertEquals("ACTIVITY_RANGE_INCOMPLETE", assertFailsWith<AdminException> { registeredRecipients(1000, 66_000, next) }.code)
            assertEquals(CoverageStatus.PARTIAL, coverage(1000, 66_001, next).status) }
    } }

    @Test fun deletionKeepsAnonymousTotalsButCannotRestoreRecipientLink() = runTest { database {
        val a = account(); val connection = UUID.randomUUID(); var now = 1000L
        val tracker = ActivityTracker(repository, { now }); tracker.connected(connection, a)
        now = 2000; tracker.checkpoint()
        assertTrue(auth.deleteAccount(UUID.fromString(a)))
        now = 3000; tracker.checkpoint()
        read { assertEquals(1, activeSubjects(1000, 3000, now)); assertEquals(emptyList(), registeredRecipients(1000, 3000, now)) }
        tracker.connected(UUID.randomUUID(), a)
        assertEquals(0, number("SELECT count(*) FROM analytics_subject_links"))
    } }

    @Test fun failureAtCommitRollsBackFactsLinksAndCoverageTogether() = runTest { database {
        val a = account(); sql("DELETE FROM analytics_subject_links")
        sql("CREATE FUNCTION reject_activity() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'rejected'; END ${'$'}${'$'}")
        sql("CREATE CONSTRAINT TRIGGER reject_activity AFTER INSERT ON activity_coverage_windows DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_activity()")
        val observation = ActivityObservation(UUID.randomUUID(), UUID.randomUUID(), a, 1000, 2000)
        assertFailsWith<SQLException> { repository.checkpoint(ActivityCheckpoint(listOf(observation), ActivityWindow(UUID.randomUUID(), 1000, 2000), 2000, false)) }
        assertEquals(0, number("SELECT count(*) FROM activity_connection_observations"))
        assertEquals(0, number("SELECT count(*) FROM analytics_subject_links"))
        assertEquals(0, number("SELECT count(*) FROM activity_coverage_windows"))
    } }

    @Test fun retentionCutoffAppliesBeforeCleanupAndClipsLongIntervals() = runTest { database {
        val a = account(); val id = UUID.randomUUID(); val conn = UUID.randomUUID(); val window = UUID.randomUUID()
        repository.checkpoint(ActivityCheckpoint(listOf(ActivityObservation(id, conn, a, 1000, 5000)), ActivityWindow(window, 1000, 5000), 5000, false))
        val now = ACTIVITY_RETENTION_MILLIS + 3000
        read { assertEquals(0, activeSubjects(1000, 3000, now)); assertEquals(1, activeSubjects(3000, 5000, now))
            assertEquals(CoverageStatus.PARTIAL, coverage(1000, 5000, now).status)
            assertEquals(CoverageStatus.COMPLETE, coverage(3000, 5000, now).status) }
        repository.checkpoint(ActivityCheckpoint(emptyList(), ActivityWindow(UUID.randomUUID(), now, now), now, true))
        assertEquals(3000, number("SELECT extract(epoch FROM min(started_at))*1000 FROM activity_connection_observations"))
        assertEquals(3000, number("SELECT extract(epoch FROM min(started_at))*1000 FROM activity_coverage_windows"))
        val expiredNow = ACTIVITY_RETENTION_MILLIS + 5001
        repository.checkpoint(ActivityCheckpoint(emptyList(), ActivityWindow(UUID.randomUUID(), expiredNow, expiredNow), expiredNow, true))
        assertEquals(0, number("SELECT count(*) FROM activity_connection_observations"))
    } }

    @Test fun collectionWaitingOnDeletionCannotRecreateIdentityLink() = runTest { database {
        val a = account()
        coroutineScope {
            source.connection.use { deleting ->
                deleting.autoCommit = false
                deleting.createStatement().use { it.executeQuery("SELECT id FROM users WHERE id='$a' FOR UPDATE").close() }
                val writer = async(Dispatchers.IO) {
                    repository.checkpoint(ActivityCheckpoint(listOf(ActivityObservation(UUID.randomUUID(), UUID.randomUUID(), a, 1000, 2000)), ActivityWindow(UUID.randomUUID(), 1000, 2000), 2000, false))
                }
                val deadline = System.nanoTime() + 5_000_000_000L
                while (number("SELECT count(*) FROM pg_stat_activity WHERE application_name='${source.schema}' AND wait_event_type='Lock'") == 0L) {
                    check(System.nanoTime() < deadline) { "Activity writer did not wait on identity deletion" }
                    Thread.sleep(10)
                }
                AnalyticsFacts.eraseIdentityLink(deleting, a)
                deleting.createStatement().use { it.executeUpdate("DELETE FROM users WHERE id='$a'") }
                deleting.commit(); writer.await()
            }
        }
        assertEquals(0, number("SELECT count(*) FROM analytics_subject_links"))
        assertEquals(0, number("SELECT count(*) FROM activity_connection_observations"))
        assertEquals(1, number("SELECT count(*) FROM activity_coverage_windows"))
    } }

    @Test fun oppositeAccountOrdersCollectWithoutDeadlockAndReusePrivateSubjects() = runTest { database {
        val a = account(); val b = account()
        coroutineScope { listOf(listOf(a, b), listOf(b, a)).map { users -> async(Dispatchers.IO) {
            repository.checkpoint(ActivityCheckpoint(users.map { ActivityObservation(UUID.randomUUID(), UUID.randomUUID(), it, 1000, 2000) }, ActivityWindow(UUID.randomUUID(), 1000, 2000), 2000, false))
        } }.awaitAll() }
        assertEquals(4, number("SELECT count(*) FROM activity_connection_observations"))
        assertEquals(2, number("SELECT count(DISTINCT subject_key) FROM activity_connection_observations"))
        assertEquals(2, number("SELECT count(*) FROM analytics_subject_links"))
    } }
    @Test fun deletedStatusAndGuestsCannotRestoreSubjectsOrBecomeRecipients() = runTest { database {
        val a = account(); val b = account(); var now = 1000L
        val tracker = ActivityTracker(repository, { now }); tracker.connected(UUID.randomUUID(), a)
        sql("UPDATE users SET status='DELETED' WHERE id IN ('$a','$b')")
        sql("DELETE FROM analytics_subject_links WHERE user_id='$b'")
        tracker.connected(UUID.randomUUID(), b)
        val guest = PostgresGuestIdentityRepository(source).resolveGuest("Guest", null, 1000)
        tracker.connected(UUID.randomUUID(), guest.playerId)
        now = 2000; tracker.checkpoint()
        read { assertEquals(emptyList(), registeredRecipients(1000, 2000, now)) }
        assertEquals(1, number("SELECT count(*) FROM analytics_subject_links"))
        assertEquals(1, number("SELECT count(*) FROM activity_connection_observations"))
    } }
    @Test fun failedUpdateRollsBackPriorCoverageAndRecoveryStartsNewWindow() = runTest { database {
        val a = account(); var now = 1000L
        val tracker = ActivityTracker(repository, { now }); tracker.connected(UUID.randomUUID(), a)
        sql("CREATE FUNCTION reject_update() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'rejected'; END ${'$'}${'$'}")
        sql("CREATE CONSTRAINT TRIGGER reject_update AFTER UPDATE ON activity_coverage_windows DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_update()")
        now = 31_000; tracker.checkpoint() // safe failure isolation; the accepted game connection survives
        assertEquals(1000, number("SELECT extract(epoch FROM max(observed_until))*1000 FROM activity_connection_observations"))
        assertEquals(1000, number("SELECT extract(epoch FROM max(observed_until))*1000 FROM activity_coverage_windows"))
        sql("DROP TRIGGER reject_update ON activity_coverage_windows")
        now = 61_000; tracker.checkpoint(); now = 91_000; tracker.checkpoint()
        read { assertEquals(CoverageStatus.PARTIAL, coverage(1000, 91_000, now).status)
            assertEquals(CoverageStatus.COMPLETE, coverage(61_000, 91_000, now).status) }
        assertEquals(2, number("SELECT count(*) FROM activity_connection_observations"))
        assertEquals(2, number("SELECT count(*) FROM activity_coverage_windows"))
    } }

    @Test fun readerUsesCallerUncommittedSnapshotWithoutCommitting() = runTest { database {
        source.connection.use { transaction ->
            transaction.autoCommit = false
            transaction.createStatement().use { it.executeUpdate("INSERT INTO activity_coverage_windows VALUES ('${UUID.randomUUID()}',to_timestamp(1),to_timestamp(2))") }
            val reader = JdbcActivityReader(transaction)
            assertEquals(CoverageStatus.COMPLETE, reader.coverage(1000, 2000, 2000).status)
            assertEquals(emptyList(), reader.registeredRecipients(1000, 2000, 2000))
            assertFalse(transaction.autoCommit)
            assertEquals(0, number("SELECT count(*) FROM activity_coverage_windows"))
            transaction.rollback()
        }
        assertEquals(0, number("SELECT count(*) FROM activity_coverage_windows"))
    } }
    @Test fun concurrentCleanupCannotTurnValidatedCoverageIntoFalseEmptyAudience() = runTest { database {
        val a = account(); var now = 1000L
        val tracker = ActivityTracker(repository, { now }); tracker.connected(UUID.randomUUID(), a)
        now = 2000; tracker.checkpoint()
        source.connection.use { real ->
            real.autoCommit = false
            real.transactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
            var cleaned = false
            // Execute real JDBC SQL, then commit retention cleanup before the caller consumes its result.
            // A reader issuing coverage and recipients separately sees two different snapshots.
            val racing = java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection::class.java.classLoader,
                arrayOf(java.sql.Connection::class.java)) { _, method, arguments ->
                val result = method.invoke(real, *(arguments ?: emptyArray()))
                if (method.name == "prepareStatement") {
                    val sql = arguments!![0] as String
                    val statement = result as java.sql.PreparedStatement
                    java.lang.reflect.Proxy.newProxyInstance(java.sql.PreparedStatement::class.java.classLoader,
                        arrayOf(java.sql.PreparedStatement::class.java)) { _, operation, parameters ->
                        val response = operation.invoke(statement, *(parameters ?: emptyArray()))
                        if (operation.name == "executeQuery" && sql.contains("activity_coverage_windows") && !cleaned) {
                            source.connection.use { cleanup ->
                                cleanup.autoCommit = false
                                cleanup.createStatement().use { s ->
                                    s.executeUpdate("DELETE FROM activity_connection_observations")
                                    s.executeUpdate("DELETE FROM activity_coverage_windows")
                                }
                                cleanup.commit()
                            }
                            cleaned = true
                        }
                        response
                    }
                } else result
            } as java.sql.Connection
            assertEquals(listOf(a), JdbcActivityReader(racing).registeredRecipients(1000, 2000, now))
            assertTrue(cleaned)
            assertEquals(java.sql.Connection.TRANSACTION_READ_COMMITTED, real.transactionIsolation)
            real.rollback()
        }
        assertEquals(0, number("SELECT count(*) FROM activity_connection_observations"))
    } }
    @Test fun nestedOverlappingWindowsAndRangeEdgesAgreeForCoverageAndRecipients() = runTest { database {
        listOf(1 to 5, 2 to 3, 4 to 6).forEach { (start, end) ->
            sql("INSERT INTO activity_coverage_windows VALUES ('${UUID.randomUUID()}',to_timestamp($start),to_timestamp($end))")
        }
        read {
            assertEquals(CoverageStatus.COMPLETE, coverage(1000, 6000, 6000).status)
            assertEquals(emptyList(), registeredRecipients(1000, 6000, 6000))
            assertEquals("ACTIVITY_RANGE_INCOMPLETE", assertFailsWith<AdminException> { registeredRecipients(999, 6000, 6000) }.code)
            assertEquals("ACTIVITY_RANGE_INCOMPLETE", assertFailsWith<AdminException> { registeredRecipients(1000, 6001, 7000) }.code)
        }
        sql("DELETE FROM activity_coverage_windows WHERE started_at=to_timestamp(1)")
        read {
            assertEquals(CoverageStatus.PARTIAL, coverage(2000, 6000, 6000).status)
            assertEquals("ACTIVITY_RANGE_INCOMPLETE", assertFailsWith<AdminException> { registeredRecipients(2000, 6000, 6000) }.code)
        }
    } }
    private suspend fun database(block: suspend Fixture.() -> Unit) {
        val url = requireNotNull(System.getenv("TEST_DATABASE_URL")) { "Local PostgreSQL required" }
        require(url == "jdbc:postgresql://127.0.0.1:15432/admin_test")
        val schema = "activity_test_${UUID.randomUUID().toString().replace("-", "") }"
        fun config() = HikariConfig().apply { jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 4; addDataSourceProperty("ApplicationName", schema); connectionInitSql = "SET statement_timeout = '10s'" }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try { HikariDataSource(config().apply { this.schema = schema }).use { source ->
                Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                Fixture(source).block()
            } } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
    private class Fixture(val source: HikariDataSource) {
        val repository = PostgresActivityRepository(source); val auth = PostgresAuthRepository(source)
        fun sql(query: String) = source.connection.use { it.createStatement().use { s -> s.execute(query) } }
        fun number(query: String) = source.connection.use { it.createStatement().use { s -> s.executeQuery(query).use { r -> check(r.next()); r.getLong(1) } } }
        fun read(block: JdbcActivityReader.() -> Unit) = source.connection.use { JdbcActivityReader(it).block() }
        suspend fun account(): String {
            val id = UUID.randomUUID()
            assertTrue(auth.createAccount(NewAccount(id, "$id@example.com", "hash", "Player", id.toString().take(12), "android", NewAuthSession(UUID.randomUUID(), hashToken(UUID.randomUUID().toString()), hashToken(UUID.randomUUID().toString()), 100_000, 200_000, 1000))))
            return id.toString()
        }
    }
}
