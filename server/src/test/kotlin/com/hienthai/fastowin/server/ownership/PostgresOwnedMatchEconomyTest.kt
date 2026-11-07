package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.server.CompletedMatch
import com.hienthai.fastowin.server.CompletedMatchPlayer
import com.hienthai.fastowin.server.MatchOutcome
import com.hienthai.fastowin.server.PostgresGuestIdentityRepository
import com.hienthai.fastowin.server.PostgresMatchResultRepository
import com.hienthai.fastowin.server.PostgresPlayQuotaRepository
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.StoredActiveRoom
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresOwnedMatchEconomyTest {
    @Test
    fun `takeover after start and settlement preserves one debit and one reward`() = runBlocking {
        fixture { f ->
            val waiting = f.waitingRoom()
            val candidate = f.playing(waiting)
            assertIs<FencedResult.Committed>(f.economyA.start(waiting.fence, waiting.revision, f.hostSocket, candidate))
            val committed = assertNotNull(f.repoB.read(waiting.fence.roomId))
            assertTrue(f.repoA.release(committed.fence))
            val newOwner = assertNotNull(f.repoB.claim(waiting.fence.roomId, f.instanceB))
            assertEquals(candidate.matchId, newOwner.snapshot.matchId)
            assertIs<FencedResult.Stale>(f.economyA.start(committed.fence, newOwner.revision, f.hostSocket, candidate))
            assertIs<FencedResult.Unchanged>(f.economyB.start(newOwner.fence, newOwner.revision, f.hostSocket, candidate))
            assertEquals(2, f.receiptCount(candidate.matchId))
            assertEquals(listOf(1, 1), f.quotaCounts())

            val pending = f.finish(f.repoB, newOwner)
            assertIs<FencedResult.Stale>(f.economyA.settle(committed.fence, pending.revision))
            assertEquals(0, f.matchCount(candidate.matchId))
            assertIs<FencedResult.Committed>(f.economyB.settle(pending.fence, pending.revision))
            val settled = assertNotNull(f.repoA.read(waiting.fence.roomId))
            assertNull(settled.snapshot.pendingCompletedMatch)
            assertTrue(f.repoB.release(settled.fence))
            val recovered = assertNotNull(f.repoA.claim(waiting.fence.roomId, f.instanceA))
            assertIs<FencedResult.Unchanged>(f.economyA.settle(recovered.fence, recovered.revision))
            assertEquals(1, f.matchCount(candidate.matchId))
            assertEquals(listOf(1, 80, 24), f.hostReward(candidate.matchId))
            assertEquals(1, f.hostMatches())
        }
    }

    @Test
    fun `identical completion replays but different completion is rejected without new effects`() = runBlocking {
        fixture { f ->
            val started = f.start()
            val pending = f.finish(f.repoA, started)
            assertIs<FencedResult.Committed>(f.economyA.settle(pending.fence, pending.revision))
            val settled = assertNotNull(f.repoA.read(started.fence.roomId))
            val original = assertNotNull(pending.snapshot.pendingCompletedMatch)
            val identical = assertIs<FencedResult.Committed>(
                f.repoA.withFencedRoom(settled.fence, settled.revision, null) { _, state ->
                    RoomMutation.Change(state.snapshot.copy(
                        pendingCompletedMatch = original.copy(players = original.players.reversed())
                    ))
                }
            ).state
            val replay = assertIs<FencedResult.Committed>(f.economyA.settle(identical.fence, identical.revision)).state
            assertNull(replay.snapshot.pendingCompletedMatch)
            assertEquals(0L, f.economyA.integrityConflicts)

            val divergent = original.copy(endedAtMillis = original.endedAtMillis + 500)
            val requeued = assertIs<FencedResult.Committed>(
                f.repoA.withFencedRoom(replay.fence, replay.revision, null) { _, state ->
                    RoomMutation.Change(state.snapshot.copy(
                        finishedAtEpochMillis = divergent.endedAtMillis, pendingCompletedMatch = divergent
                    ))
                }
            ).state
            val result = assertIs<FencedResult.Rejected>(f.economyA.settle(requeued.fence, requeued.revision))
            assertEquals("MATCH_RESULT_CONFLICT", result.reason)
            assertEquals(1L, f.economyA.integrityConflicts)
            assertEquals(1, f.matchCount(original.matchId))
            assertEquals(listOf(1, 80, 24), f.hostReward(original.matchId))
            assertEquals(divergent, f.repoA.read(started.fence.roomId)?.snapshot?.pendingCompletedMatch)
        }
    }

    @Test
    fun `failed room write rolls back quota and settlement changes`() = runBlocking {
        fixture { f ->
            val waiting = f.waitingRoom()
            val candidate = f.playing(waiting)
            f.failRoomUpdates()
            assertIs<FencedResult.TemporaryFailure>(f.economyA.start(waiting.fence, waiting.revision, f.hostSocket, candidate))
            assertEquals(0, f.receiptCount(candidate.matchId))
            assertEquals(emptyList(), f.quotaCounts())
            assertEquals(RoomPhase.WAITING, f.repoB.read(waiting.fence.roomId)?.snapshot?.phase)
            f.allowRoomUpdates()
            val started = assertIs<FencedResult.Committed>(
                f.economyA.start(waiting.fence, waiting.revision, f.hostSocket, candidate)
            ).state
            val pending = f.finish(f.repoA, started)
            f.failRoomUpdates()
            assertIs<FencedResult.TemporaryFailure>(f.economyA.settle(pending.fence, pending.revision))
            assertEquals(0, f.matchCount(candidate.matchId))
            assertEquals(listOf(0, 0, 0), f.hostReward(candidate.matchId))
            assertEquals(0, f.hostMatches())
            assertNotNull(f.repoB.read(waiting.fence.roomId)?.snapshot?.pendingCompletedMatch)
            assertEquals(listOf(1, 1), f.quotaCounts())
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL fixture required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        val schema = "room_economy_test_" + UUID.randomUUID().toString().replace("-", "")
        fun pool(selectedSchema: String?) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
            if (selectedSchema != null) this.schema = selectedSchema
        })
        pool(null).use { control ->
            control.connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema).load().migrate()
                pool(schema).use { a ->
                    pool(schema).use { b ->
                        val identities = PostgresGuestIdentityRepository(a)
                        val host = identities.resolveGuest("Pilot host", null, NOW).playerId
                        val guest = identities.resolveGuest("Pilot guest", null, NOW).playerId
                        a.connection.use { c ->
                            c.createStatement().use {
                                it.executeUpdate("""
                                    UPDATE users
                                    SET account_type = 'REGISTERED',
                                        email_normalized = id::text || '@example.test',
                                        password_hash = 'fixture-only'
                                """.trimIndent())
                            }
                        }
                        block(Fixture(a, b, host, guest))
                    }
                }
            } finally {
                control.connection.use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private class Fixture(
        private val a: HikariDataSource,
        b: HikariDataSource,
        private val host: String,
        private val guest: String
    ) {
        val instanceA = UUID.randomUUID()
        val instanceB = UUID.randomUUID()
        val repoA = PostgresRoomOwnershipRepository(a)
        val repoB = PostgresRoomOwnershipRepository(b)
        val economyA = OwnedMatchEconomy(repoA, PostgresPlayQuotaRepository(a), PostgresMatchResultRepository(a))
        val economyB = OwnedMatchEconomy(repoB, PostgresPlayQuotaRepository(b), PostgresMatchResultRepository(b))
        lateinit var hostSocket: SocketFence
            private set

        suspend fun waitingRoom(): OwnedRoomState {
            hostSocket = repoA.bindSocket(UUID.fromString(host))
            val initial = StoredActiveRoom(
                roomId = UUID.randomUUID().toString(), roomName = "Pilot",
                host = StoredActivePlayer(host, "Pilot host", isAccount = true),
                passwordSalt = byteArrayOf(1), passwordHash = byteArrayOf(2),
                gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.WAITING
            )
            val room = assertIs<FencedResult.Committed>(repoA.createRoom(instanceA, hostSocket, initial)).state
            return assertIs<FencedResult.Committed>(
                repoA.withFencedRoom(room.fence, room.revision, hostSocket) { _, state ->
                    RoomMutation.Change(state.snapshot.copy(
                        guest = StoredActivePlayer(guest, "Pilot guest", isAccount = true),
                        readyPlayerIds = setOf(guest)
                    ))
                }
            ).state
        }

        fun playing(state: OwnedRoomState) = state.snapshot.copy(
            phase = RoomPhase.PLAYING, startedAtEpochMillis = NOW, numbers = (1..50).toList(),
            readyPlayerIds = emptySet()
        )

        suspend fun start(): OwnedRoomState {
            val waiting = waitingRoom()
            return assertIs<FencedResult.Committed>(
                economyA.start(waiting.fence, waiting.revision, hostSocket, playing(waiting))
            ).state
        }

        suspend fun finish(repo: RoomOwnershipRepository, state: OwnedRoomState): OwnedRoomState {
            val match = CompletedMatch(
                matchId = state.snapshot.matchId, roomId = state.snapshot.roomId,
                roomName = state.snapshot.roomName, gameMode = ProtocolGameMode.ORDER, matchType = MatchType.CASUAL,
                startedAtMillis = NOW, endedAtMillis = NOW + 1000, winnerPlayerId = host,
                players = listOf(
                    CompletedMatchPlayer(host, "Pilot host", 50, MatchOutcome.WIN),
                    CompletedMatchPlayer(guest, "Pilot guest", 20, MatchOutcome.LOSS)
                )
            )
            return assertIs<FencedResult.Committed>(
                repo.withFencedRoom(state.fence, state.revision, null) { _, room ->
                    RoomMutation.Change(room.snapshot.copy(
                        phase = RoomPhase.FINISHED, finishedAtEpochMillis = match.endedAtMillis,
                        pendingCompletedMatch = match
                    ))
                }
            ).state
        }

        fun failRoomUpdates() {
            execute("""
                CREATE OR REPLACE FUNCTION fail_pilot_room_write() RETURNS trigger LANGUAGE plpgsql AS
                'BEGIN RAISE EXCEPTION ''fixture failure after economy writes''; END;'
            """.trimIndent())
            execute("""
                CREATE TRIGGER fail_pilot_room_write BEFORE UPDATE OF snapshot ON room_ownership_pilot_rooms
                FOR EACH ROW EXECUTE FUNCTION fail_pilot_room_write()
            """.trimIndent())
        }

        fun allowRoomUpdates() = execute("DROP TRIGGER fail_pilot_room_write ON room_ownership_pilot_rooms")
        fun receiptCount(matchId: String) = ints(
            "SELECT count(*) FROM play_quota_match_consumptions WHERE match_id = ? AND reservation_state = 'FINALIZED'",
            UUID.fromString(matchId)
        ).single()
        fun quotaCounts() = ints("SELECT matches_consumed FROM daily_play_quotas ORDER BY user_id")
        fun matchCount(matchId: String) = ints("SELECT count(*) FROM matches WHERE id = ?", UUID.fromString(matchId)).single()
        fun hostMatches() = ints("SELECT COALESCE(max(total_matches), 0) FROM player_stats WHERE user_id = ?", UUID.fromString(host)).single()

        fun hostReward(matchId: String) = ints(
            """
            SELECT count(*), COALESCE(sum(gold_delta), 0), COALESCE(sum(xp_delta), 0)
            FROM wallet_transactions WHERE user_id = ? AND source_type = 'MATCH' AND source_id = ?
            """.trimIndent(), UUID.fromString(host), matchId
        )

        private fun execute(sql: String) = a.connection.use { c -> c.createStatement().use { it.execute(sql) } }

        private fun ints(sql: String, vararg args: Any): List<Int> = a.connection.use { c ->
            c.prepareStatement(sql).use { stmt ->
                args.forEachIndexed { index, value -> stmt.setObject(index + 1, value) }
                stmt.executeQuery().use { rows ->
                    buildList { while (rows.next()) for (i in 1..rows.metaData.columnCount) add(rows.getInt(i)) }
                }
            }
        }
    }

    private companion object {
        const val NOW = 1900000000000L
    }
}
