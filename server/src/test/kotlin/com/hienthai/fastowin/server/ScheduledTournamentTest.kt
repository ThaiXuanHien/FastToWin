package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.decodeFromString
import java.util.UUID
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduledTournamentTest {
    @Test
    fun `invitation discloses actual schedule and fee before admission`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val tournament = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update()
        val invitation = fixture.engine.handle(fixture.ids.first(),
            ClientMessage.InviteTournamentPlayer(tournament.tournamentId, fixture.ids[1]))
            .map(Delivery::message).filterIsInstance<ServerMessage.TournamentInvitation>().single().invitation
        val json = ProtocolJson.encodeToString(TournamentInvitationSnapshot.serializer(), invitation)
        assertTrue(json.contains("\"schedule\""), "Invitees must see the actual schedule before admission")
        assertTrue(json.contains("\"entryFee\":100"), "Invitees must see the actual fee before admission")
        assertEquals(tournament.schedule, invitation.schedule)
        assertEquals(fixture.now, invitation.serverTimeEpochMillis)
        fixture.now += 1_000
        val refreshed = fixture.engine.handle(fixture.ids[1], ClientMessage.GetTournamentHub)
            .map(Delivery::message).filterIsInstance<ServerMessage.TournamentHubData>().single().hub.invitations.single()
        assertEquals(fixture.now, refreshed.serverTimeEpochMillis)
    }

    @Test
    fun `scheduled creation rejects invalid timezone before taking payment`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val result = fixture.engine.handle(fixture.ids.first(), fixture.creation(zone = "Invalid/Zone"))
        assertEquals("INVALID_TOURNAMENT_SCHEDULE", result.errorCode())
        assertEquals(1_000, fixture.wallet.gold.getValue(fixture.ids.first()))
        assertTrue(fixture.repository.loadActive().isEmpty())
    }

    @Test
    fun `registration locks at check in opening without waiting for a scheduler tick`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val tournament = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update()
        fixture.now = fixture.startsAt - 900_000
        assertEquals("TOURNAMENT_REGISTRATION_CLOSED", fixture.engine.handle(fixture.ids[1],
            ClientMessage.JoinPublicTournament(tournament.tournamentId)).errorCode())
        assertEquals(1_000, fixture.wallet.gold.getValue(fixture.ids[1]))
    }

    @Test
    fun `full checked in roster starts exactly once and preserves selected format`() = runTest {
        for (size in listOf(4, 8, 16)) {
            val fixture = Fixture(size)
            fixture.connect()
            val id = fixture.engine.handle(fixture.ids.first(), fixture.creation(size = size)).update().tournamentId
            fixture.ids.drop(1).take(size - 1).forEach { fixture.engine.handle(it, ClientMessage.JoinPublicTournament(id)) }
            fixture.now = fixture.startsAt - 900_000
            fixture.ids.take(size).forEach { fixture.engine.handle(it, ClientMessage.CheckInTournament(id)) }
            assertTrue(fixture.engine.advanceScheduledTournaments().isEmpty())
            fixture.now = fixture.startsAt
            val deliveries = fixture.engine.advanceScheduledTournaments()
            assertEquals(TournamentPhase.RUNNING, deliveries.update().phase)
            assertEquals(size, deliveries.update().maxPlayers)
            assertEquals(size / 2, deliveries.map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().size)
            assertEquals(size / 2, fixture.rooms.loadAll().size)
            assertTrue(fixture.engine.advanceScheduledTournaments().isEmpty())
        }
    }

    @Test
    fun `check in is self only idempotent and enforces both time boundaries`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        assertEquals("TOURNAMENT_CHECK_IN_CLOSED", fixture.engine.handle(fixture.ids.first(), ClientMessage.CheckInTournament(id)).errorCode())
        fixture.now = fixture.startsAt - 900_000
        assertEquals("TOURNAMENT_NOT_FOUND", fixture.engine.handle(fixture.ids[1], ClientMessage.CheckInTournament(id)).errorCode())
        repeat(2) { assertTrue(fixture.engine.handle(fixture.ids.first(), ClientMessage.CheckInTournament(id)).update().players.single().isCheckedIn) }
        assertEquals(900, fixture.wallet.gold.getValue(fixture.ids.first()))
        fixture.now = fixture.startsAt
        assertEquals("TOURNAMENT_CHECK_IN_CLOSED", fixture.engine.handle(fixture.ids.first(), ClientMessage.CheckInTournament(id)).errorCode())
    }

    @Test
    fun `insufficient check in cancels with all recorded fees including a leaver refunded once`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        fixture.ids.drop(1).take(3).forEach { fixture.engine.handle(it, ClientMessage.JoinPublicTournament(id)) }
        fixture.engine.handle(fixture.ids[3], ClientMessage.LeaveTournament(id))
        fixture.now = fixture.startsAt - 900_000
        fixture.ids.take(3).forEach { fixture.engine.handle(it, ClientMessage.CheckInTournament(id)) }
        fixture.now = fixture.startsAt
        val cancelled = fixture.engine.advanceScheduledTournaments().update()
        assertEquals(TournamentPhase.CANCELLED, cancelled.phase)
        assertEquals(fixture.ids.take(4).toSet(), cancelled.players.map { it.playerId }.toSet())
        fixture.ids.forEach { assertEquals(1_000, fixture.wallet.gold.getValue(it)) }
        assertTrue(fixture.rooms.loadAll().isEmpty())
        assertTrue(fixture.engine.advanceScheduledTournaments().isEmpty())
        fixture.repository.cancelForMaintenance(cancelled, fixture.wallet)
        fixture.ids.forEach { assertEquals(1_000, fixture.wallet.gold.getValue(it)) }
    }

    @Test
    fun `scheduled host cancellation refunds while manual cancellation keeps existing fee semantics`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        assertEquals("TOURNAMENT_AUTOMATIC_START", fixture.engine.handle(fixture.ids.first(), ClientMessage.StartTournament(id)).errorCode())
        assertEquals(TournamentPhase.CANCELLED, fixture.engine.handle(fixture.ids.first(), ClientMessage.LeaveTournament(id)).update().phase)
        assertEquals(1_000, fixture.wallet.gold.getValue(fixture.ids.first()))
        val manual = fixture.engine.handle(fixture.ids.first(), ClientMessage.CreateTournament("Manual cup", ProtocolGameMode.ORDER, 100)).update()
        fixture.engine.handle(fixture.ids.first(), ClientMessage.LeaveTournament(manual.tournamentId))
        assertEquals(900, fixture.wallet.gold.getValue(fixture.ids.first()))
    }

    @Test
    fun `late leave is denied and older clients can still create manual tournaments`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        fixture.engine.connectAccount(AuthenticatedAccount(UUID.fromString(fixture.ids[1]), "Old"), protocolVersion = 43)
        assertEquals("TOURNAMENT_CLIENT_UPDATE_REQUIRED", fixture.engine.handle(fixture.ids[1], fixture.creation()).errorCode())
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        assertEquals("TOURNAMENT_CLIENT_UPDATE_REQUIRED", fixture.engine.handle(fixture.ids[1], ClientMessage.JoinPublicTournament(id)).errorCode())
        fixture.engine.handle(fixture.ids[2], ClientMessage.JoinPublicTournament(id))
        fixture.now = fixture.startsAt - 900_000
        assertEquals("TOURNAMENT_REGISTRATION_CLOSED", fixture.engine.handle(fixture.ids[2], ClientMessage.LeaveTournament(id)).errorCode())
        assertNull(fixture.engine.handle(fixture.ids[1], ClientMessage.CreateTournament("Manual cup", ProtocolGameMode.ORDER)).update().schedule)
    }

    @Test
    fun `restart restores check in and overdue schedules use the same cancellation path`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        fixture.now = fixture.startsAt - 900_000
        fixture.engine.handle(fixture.ids.first(), ClientMessage.CheckInTournament(id))
        val restarted = GameEngine(playerProfileRepository = fixture.wallet, tournamentRepository = fixture.repository,
            activeRoomRepository = fixture.rooms, nowMillis = { fixture.now })
        restarted.restoreActiveRooms()
        assertTrue(fixture.repository.loadActive().single().players.single().isCheckedIn)
        fixture.now = fixture.startsAt
        assertEquals(TournamentPhase.CANCELLED, restarted.advanceScheduledTournaments().update().phase)
        assertEquals(1_000, fixture.wallet.gold.getValue(fixture.ids.first()))
    }

    @Test
    fun `maintenance retains overdue lobby until new intent reopens`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        fixture.engine.handle(fixture.ids.first(), fixture.creation())
        fixture.now = fixture.startsAt
        fixture.engine.publishMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(
            MaintenanceState.DRAINING))
        assertTrue(fixture.engine.advanceScheduledTournaments().isEmpty())
        assertEquals(TournamentPhase.LOBBY, fixture.repository.loadActive().single().phase)
        fixture.engine.publishMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(
            MaintenanceState.ACTIVE))
        assertEquals(TournamentPhase.CANCELLED, fixture.engine.advanceScheduledTournaments().update().phase)
    }

    @Test
    fun `schedule time range is inclusive and rejects past or excessive starts`() = runTest {
        for ((offset, allowed) in listOf(899_999L to false, 900_000L to true,
            31_536_000_000L to true, 31_536_000_001L to false, Long.MIN_VALUE to false)) {
            val fixture = Fixture()
            fixture.connect()
            val result = fixture.engine.handle(fixture.ids.first(), fixture.creation(start = fixture.now + offset))
            if (allowed) {
                val schedule = assertNotNull(result.update().schedule)
                assertEquals(fixture.now + offset - 900_000, schedule.registrationClosesAtEpochMillis)
                assertEquals(schedule.registrationClosesAtEpochMillis, schedule.checkInOpensAtEpochMillis)
                assertEquals(fixture.now, result.update().serverTimeEpochMillis)
                assertEquals(schedule, fixture.engine.handle(fixture.ids[1], ClientMessage.GetPublicTournaments())
                    .map(Delivery::message).filterIsInstance<ServerMessage.PublicTournamentsData>().single().tournaments.single().schedule)
            } else assertEquals("INVALID_TOURNAMENT_SCHEDULE", result.errorCode())
        }
    }

    @Test
    fun `scheduled invitations enforce client capability and exact registration deadline`() = runTest {
        val fixture = Fixture()
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        fun List<Delivery>.invitationId() = map(Delivery::message).filterIsInstance<ServerMessage.TournamentInvitation>().single().invitation.invitationId
        val invitation = fixture.engine.handle(fixture.ids.first(), ClientMessage.InviteTournamentPlayer(id, fixture.ids[1])).invitationId()
        fixture.engine.connectAccount(AuthenticatedAccount(UUID.fromString(fixture.ids[1]), "Old"), protocolVersion = 43)
        assertEquals("TOURNAMENT_CLIENT_UPDATE_REQUIRED", fixture.engine.handle(fixture.ids[1], ClientMessage.RespondTournamentInvitation(invitation, true)).errorCode())
        fixture.engine.connectAccount(AuthenticatedAccount(UUID.fromString(fixture.ids[1]), "New"))
        assertEquals(2, fixture.engine.handle(fixture.ids[1], ClientMessage.RespondTournamentInvitation(invitation, true)).update().players.size)
        fixture.now = fixture.startsAt - 900_001
        val late = fixture.engine.handle(fixture.ids.first(), ClientMessage.InviteTournamentPlayer(id, fixture.ids[2])).invitationId()
        fixture.now++
        assertEquals("TOURNAMENT_REGISTRATION_CLOSED", fixture.engine.handle(fixture.ids[2], ClientMessage.RespondTournamentInvitation(late, true)).errorCode())
        assertEquals(1_000, fixture.wallet.gold.getValue(fixture.ids[2]))
    }

    @Test
    fun `check in storage failure leaves live and durable participant unchecked`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        fixture.now = fixture.startsAt - 900_000
        store.failSave = true
        assertFailsWith<IllegalStateException> { fixture.engine.handle(fixture.ids.first(), ClientMessage.CheckInTournament(id)) }
        assertFalse(store.loadActive().single().players.single().isCheckedIn)
        assertFalse(fixture.engine.handle(fixture.ids.first(), ClientMessage.GetTournamentHub).active().players.single().isCheckedIn)
        store.failSave = false
        assertTrue(fixture.engine.handle(fixture.ids.first(), ClientMessage.CheckInTournament(id)).update().players.single().isCheckedIn)
    }

    @Test
    fun `failed scheduled start retains retryable lobby with no published or stored rooms`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        val id = fixture.prepareFullRoster()
        fixture.now = fixture.startsAt
        store.failProgression = true
        assertFailsWith<IllegalStateException> { fixture.engine.advanceScheduledTournaments() }
        assertEquals(TournamentPhase.LOBBY, fixture.engine.handle(fixture.ids.first(), ClientMessage.GetTournamentHub).active().phase)
        assertTrue(fixture.rooms.loadAll().isEmpty())
        store.failProgression = false
        assertEquals(TournamentPhase.RUNNING, fixture.engine.advanceScheduledTournaments().update().phase)
        assertEquals(2, fixture.rooms.loadAll().size)
        assertTrue(fixture.engine.advanceScheduledTournaments().isEmpty())
    }

    @Test
    fun `storage failure in cancellation retains paid lobby and retries full refund`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        store.failCancellation = true
        assertFailsWith<IllegalStateException> { fixture.engine.handle(fixture.ids.first(), ClientMessage.LeaveTournament(id)) }
        fixture.now = fixture.startsAt
        assertFailsWith<IllegalStateException> { fixture.engine.advanceScheduledTournaments() }
        assertEquals(900, fixture.wallet.gold.getValue(fixture.ids.first()))
        assertEquals(TournamentPhase.LOBBY, fixture.engine.handle(fixture.ids.first(), ClientMessage.GetTournamentHub).active().phase)
        store.failCancellation = false
        assertEquals(TournamentPhase.CANCELLED, fixture.engine.advanceScheduledTournaments().update().phase)
        assertEquals(1_000, fixture.wallet.gold.getValue(fixture.ids.first()))
    }

    @Test
    fun `disconnected checked in player prevents start and receives refund`() = runTest {
        val fixture = Fixture()
        fixture.prepareFullRoster()
        fixture.engine.markDisconnected(fixture.ids[2])
        fixture.now = fixture.startsAt
        assertEquals(TournamentPhase.CANCELLED, fixture.engine.advanceScheduledTournaments().update().phase)
        assertTrue(fixture.rooms.loadAll().isEmpty())
        fixture.ids.forEach { assertEquals(1_000, fixture.wallet.gold.getValue(it)) }
    }

    @Test
    fun `maintenance arriving during room commit rolls back unpublished start`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        fixture.prepareFullRoster()
        fixture.now = fixture.startsAt
        store.afterProgression = {
            fixture.engine.publishMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.DRAINING))
        }
        val deliveries = fixture.engine.advanceScheduledTournaments()
        assertEquals(TournamentPhase.LOBBY, deliveries.update().phase)
        assertTrue(deliveries.none { it.message is ServerMessage.GameStarted })
        assertTrue(fixture.rooms.loadAll().isEmpty())
        store.afterProgression = null
        fixture.engine.publishMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.ACTIVE))
        assertEquals(TournamentPhase.RUNNING, fixture.engine.advanceScheduledTournaments().update().phase)
    }

    @Test
    fun `full maintenance cancels a failed unpublished scheduled start and refunds it`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        fixture.prepareFullRoster()
        fixture.now = fixture.startsAt
        store.failProgression = true
        assertFailsWith<IllegalStateException> { fixture.engine.advanceScheduledTournaments() }
        store.failProgression = false
        fixture.engine.applyMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.MAINTENANCE))
        assertTrue(store.loadActive().isEmpty())
        fixture.ids.forEach { assertEquals(1_000, fixture.wallet.gold.getValue(it)) }
        assertTrue(fixture.rooms.loadAll().isEmpty())
    }

    @Test
    fun `check in and admission serialize snapshots instead of losing either participant`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        fixture.connect()
        val id = fixture.engine.handle(fixture.ids.first(), fixture.creation()).update().tournamentId
        fixture.now = fixture.startsAt - 900_001
        store.saveEntered = CompletableDeferred()
        store.saveRelease = CompletableDeferred()
        val joined = async { fixture.engine.handle(fixture.ids[1], ClientMessage.JoinPublicTournament(id)) }
        store.saveEntered!!.await()
        fixture.now++
        val checkedIn = async { fixture.engine.handle(fixture.ids.first(), ClientMessage.CheckInTournament(id)) }
        runCurrent()
        store.saveRelease!!.complete(Unit)
        joined.await()
        val snapshot = checkedIn.await().update()
        assertEquals(2, snapshot.players.size)
        assertTrue(snapshot.players.first().isCheckedIn)
        assertEquals(snapshot.players, store.loadActive().single().players)
    }

    @Test
    fun `one failed schedule does not discard another tournaments successful start deliveries`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(8, store)
        fixture.connect()
        val first = fixture.engine.handle(fixture.ids[0], fixture.creation()).update().tournamentId
        val second = fixture.engine.handle(fixture.ids[4], fixture.creation()).update().tournamentId
        fixture.ids.slice(1..3).forEach { fixture.engine.handle(it, ClientMessage.JoinPublicTournament(first)) }
        fixture.ids.slice(5..7).forEach { fixture.engine.handle(it, ClientMessage.JoinPublicTournament(second)) }
        fixture.now = fixture.startsAt - 900_000
        fixture.ids.take(4).forEach { fixture.engine.handle(it, ClientMessage.CheckInTournament(first)) }
        fixture.ids.slice(4..7).forEach { fixture.engine.handle(it, ClientMessage.CheckInTournament(second)) }
        fixture.now = fixture.startsAt
        store.failedTournamentId = second
        val deliveries = fixture.engine.advanceScheduledTournaments()
        assertEquals(first, deliveries.update().tournamentId)
        assertEquals(2, deliveries.map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().size)
        assertEquals(TournamentPhase.LOBBY, store.loadActive().single { it.tournamentId == second }.phase)
        store.failedTournamentId = null
        assertEquals(second, fixture.engine.advanceScheduledTournaments().update().tournamentId)
        assertTrue(fixture.engine.advanceScheduledTournaments().isEmpty())
    }

    @Test
    fun `late acknowledgment of timed activation stays unpublished and retry retimes the same rooms`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        fixture.prepareFullRoster(ProtocolGameMode.TIME_BONUS)
        fixture.now = fixture.startsAt
        store.afterProgression = { fixture.now += 1_000 }
        assertFailsWith<IllegalStateException> { fixture.engine.advanceScheduledTournaments() }
        assertEquals(TournamentPhase.LOBBY, fixture.engine.handle(fixture.ids.first(), ClientMessage.GetTournamentHub).active().phase)
        val priorIds = fixture.rooms.loadAll().map { it.roomId }.toSet()
        assertEquals(2, priorIds.size)
        store.afterProgression = null
        val deliveries = fixture.engine.advanceScheduledTournaments()
        assertEquals(TournamentPhase.RUNNING, deliveries.update().phase)
        assertEquals(fixture.now, deliveries.update().serverTimeEpochMillis)
        assertEquals(priorIds, fixture.rooms.loadAll().map { it.roomId }.toSet())
        assertTrue(fixture.rooms.loadAll().all { it.startedAtEpochMillis == fixture.now + 1_000 })
    }

    @Test
    fun `disconnect after durable room write rolls start back before cancellation`() = runTest {
        val store = FaultStore()
        val fixture = Fixture(repository = store)
        fixture.prepareFullRoster()
        fixture.now = fixture.startsAt
        store.afterProgression = { fixture.engine.markDisconnected(fixture.ids[2]) }
        val deliveries = fixture.engine.advanceScheduledTournaments()
        assertEquals(TournamentPhase.LOBBY, deliveries.update().phase)
        assertTrue(deliveries.none { it.message is ServerMessage.GameStarted })
        store.afterProgression = null
        assertEquals(TournamentPhase.CANCELLED, fixture.engine.advanceScheduledTournaments().update().phase)
        assertTrue(fixture.rooms.loadAll().isEmpty())
    }

    private class FaultStore : InMemoryTournamentRepository() {
        var failSave = false
        var failProgression = false
        var failedTournamentId: String? = null
        var failCancellation = false
        var afterProgression: (suspend () -> Unit)? = null
        var saveEntered: CompletableDeferred<Unit>? = null
        var saveRelease: CompletableDeferred<Unit>? = null
        override suspend fun save(tournament: TournamentSnapshot) {
            check(!failSave) { "Injected snapshot failure" }
            saveEntered?.complete(Unit)
            saveRelease?.await()
            super.save(tournament)
        }
        override suspend fun commitProgression(batch: TournamentProgression, rooms: ActiveRoomRepository, profiles: PlayerProfileRepository) {
            check(!failProgression && batch.tournament.tournamentId != failedTournamentId) { "Injected transaction failure" }
            super.commitProgression(batch, rooms, profiles)
            afterProgression?.invoke()
        }
        override suspend fun cancelForMaintenance(cancelled: TournamentSnapshot, profiles: PlayerProfileRepository) {
            check(!failCancellation) { "Injected refund transaction failure" }
            super.cancelForMaintenance(cancelled, profiles)
        }
    }

    private inner class Fixture(size: Int = 4, val repository: InMemoryTournamentRepository = InMemoryTournamentRepository()) {
        var now = 1_000_000L
        val startsAt = 3_000_000L
        val ids = List(size + 1) { UUID.randomUUID().toString() }
        val wallet = Wallet(ids)
        val rooms = InMemoryActiveRoomRepository()
        val engine = GameEngine(playerProfileRepository = wallet, tournamentRepository = repository, activeRoomRepository = rooms,
            friendRepository = object : FriendRepository by NoOpFriendRepository {
                override suspend fun areFriends(firstUserId: String, secondUserId: String) = firstUserId != secondUserId
            }, nowMillis = { now })
        suspend fun connect() { ids.forEach { engine.connectAccount(AuthenticatedAccount(UUID.fromString(it), it)) } }
        suspend fun prepareFullRoster(mode: ProtocolGameMode = ProtocolGameMode.ORDER): String {
            connect()
            val id = engine.handle(ids.first(), creation(mode = mode)).update().tournamentId
            ids.drop(1).take(3).forEach { engine.handle(it, ClientMessage.JoinPublicTournament(id)) }
            now = startsAt - 900_000
            ids.take(4).forEach { engine.handle(it, ClientMessage.CheckInTournament(id)) }
            return id
        }
        fun creation(zone: String = "Asia/Bangkok", start: Long = startsAt, size: Int = 4, mode: ProtocolGameMode = ProtocolGameMode.ORDER): ClientMessage.CreateTournament =
            ProtocolJson.decodeFromString<ClientMessage>("""{"type":"create_tournament","name":"Scheduled cup","gameMode":"${mode.name}","entryFee":100,"maxPlayers":$size,"visibility":"PUBLIC","schedule":{"startsAtEpochMillis":$start,"timeZoneId":"$zone"}}""") as ClientMessage.CreateTournament
    }

    private class Wallet(ids: List<String>) : PlayerProfileRepository by NoOpPlayerProfileRepository {
        val gold = ids.associateWith { 1_000 }.toMutableMap()
        private val ledger = mutableSetOf<Triple<String, String, String>>()
        override suspend fun findByPlayerId(playerId: String) = PlayerProfileSnapshot(playerId, "Player", "TEST",
            progression = PlayerProgressionSnapshot(level = 20, gold = gold.getValue(playerId)))
        override suspend fun applyWalletTransaction(playerId: String, sourceType: String, sourceId: String,
            goldDelta: Int, gemsDelta: Int, xpDelta: Int): WalletMutationStatus {
            val key = Triple(playerId, sourceType, sourceId)
            if (key in ledger) return WalletMutationStatus.DUPLICATE
            if (gold.getValue(playerId) + goldDelta < 0) return WalletMutationStatus.INSUFFICIENT_FUNDS
            ledger += key
            gold[playerId] = gold.getValue(playerId) + goldDelta
            return WalletMutationStatus.APPLIED
        }
    }

    private fun List<Delivery>.update() = map(Delivery::message).filterIsInstance<ServerMessage.TournamentUpdated>().single().tournament
    private fun List<Delivery>.errorCode() = map(Delivery::message).filterIsInstance<ServerMessage.Error>().singleOrNull()?.code
    private fun List<Delivery>.active() = map(Delivery::message).filterIsInstance<ServerMessage.TournamentHubData>().single().hub.activeTournament!!
}
